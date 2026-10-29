package com.tangoplus.tangoface.ui.measure.model

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.min

class OrtSession(context: Context) {

	private var ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
	private var ortSession: OrtSession? = null
	init {
		setupOrtSession(context)
	}
	private fun setupOrtSession(context: Context) {
		if (ortSession == null) {
			val modelBytes = context.assets.open("resnet18_model.onnx").readBytes()
			ortSession = ortEnv.createSession(modelBytes)
		}
	}
	suspend fun runSegmentationFromModNet(context: Context, uri: Uri) : Bitmap =
		withContext(Dispatchers.IO) {
			val inputImage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				val source = ImageDecoder.createSource(context.contentResolver, uri)
				ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
					decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
					decoder.isMutableRequired = true
				}
			} else {
				@Suppress("DEPRECATION")
				MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
			}
			val modelInputSize = 512

			// --- [STEP 1: 전처리 - 비율 유지 및 패딩 추가] ---

			// 원본 비율을 유지하며 512 크기에 맞추기 위한 스케일 계산
			val scale = min(
				modelInputSize.toFloat() / inputImage.width,
				modelInputSize.toFloat() / inputImage.height
			)
			val scaledWidth = (inputImage.width * scale).toInt()
			val scaledHeight = (inputImage.height * scale).toInt()

			// 512x512 정사각형 내에서 이미지가 중앙에 위치할 좌표(패딩) 계산
			val left = (modelInputSize - scaledWidth) / 2
			val top = (modelInputSize - scaledHeight) / 2

			// 모델 입력용 512x512 검은색 배경 비트맵 생성
			val inputBitmap = Bitmap.createBitmap(modelInputSize, modelInputSize, Bitmap.Config.ARGB_8888)
			val canvas = Canvas(inputBitmap)
			canvas.drawColor(Color.BLACK) // 배경은 검정색

			// 원본 이미지를 비율에 맞춰 줄인 후 중앙에 그리기
			val scaledBitmap = Bitmap.createScaledBitmap(inputImage, scaledWidth, scaledHeight, true)
			canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), null)

			// --- [STEP 2: FloatBuffer 변환 (MODNet 포맷)] ---

			// [1, 3, 512, 512] 형태의 FloatBuffer 생성 (RGB 평면 분리 방식)
			val floatBuffer = FloatBuffer.allocate(1 * 3 * modelInputSize * modelInputSize)
			floatBuffer.rewind()

			val pixels = IntArray(modelInputSize * modelInputSize)
			inputBitmap.getPixels(pixels, 0, modelInputSize, 0, 0, modelInputSize, modelInputSize)

			for (i in 0 until modelInputSize * modelInputSize) {
				val pixel = pixels[i]
				// R, G, B 값을 0~1 사이로 정규화하여 각 채널 평면에 배치
				floatBuffer.put(i, android.graphics.Color.red(pixel) / 255.0f)
				floatBuffer.put(i + modelInputSize * modelInputSize, android.graphics.Color.green(pixel) / 255.0f)
				floatBuffer.put(i + 2 * modelInputSize * modelInputSize, android.graphics.Color.blue(pixel) / 255.0f)
			}
			floatBuffer.rewind()

			// --- [STEP 3: ONNX Runtime 추론 실행] ---

			val inputName = ortSession?.inputNames?.first() ?: "input"
			val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, longArrayOf(1, 3, modelInputSize.toLong(), modelInputSize.toLong()))

			// 모델 실행
			val result = ortSession?.run(java.util.Collections.singletonMap(inputName, inputTensor))

			// 결과값 추출 (MODNet 출력 형식: [1, 1, 512, 512])
			val outputRaw = result?.get(0)?.value as Array<Array<Array<FloatArray>>>
			// --- [STEP 4: 후처리 - 마스크에서 패딩 제거(Crop)] ---

			// 512x512 결과물 중에서 실제 이미지가 위치한 [scaledWidth x scaledHeight] 영역만 추출
			val croppedMaskBuffer = FloatBuffer.allocate(scaledWidth * scaledHeight)
			for (y in 0 until scaledHeight) {
				for (x in 0 until scaledWidth) {
					// 모델 결과(512x512)에서 앞서 계산한 top, left 패딩만큼 건너뛰고 값을 읽음
					val maskValue = outputRaw[0][0][top + y][left + x]
					croppedMaskBuffer.put(maskValue)
				}
			}
			croppedMaskBuffer.rewind()

			// --- [STEP 5: UI 업데이트 및 결과 전달] ---
			val centerX = scaledWidth / 2
			val centerY = scaledHeight / 2

			// 1차원 버퍼 인덱스로 환산
			val bufferIndex = (centerY * scaledWidth) + centerX
			// 해당 좌표의 마스크 값 읽기 (0.0 ~ 1.0)
			val confidenceValue = croppedMaskBuffer.get(bufferIndex)

			Log.d("MODNet", "중앙 좌표($centerX, $centerY)의 마스크 값: $confidenceValue")

			// 목 두께 측정을 위한 간단한 로직 예시 (특정 행 분석)
			// 예: 이미지 높이의 70% 지점(목 근처)의 너비 계산
			val neckRowY = (scaledHeight * 0.7).toInt()
			var personPixelCount = 0

			for (x in 0 until scaledWidth) {
				val index = (neckRowY * scaledWidth) + x
				// 신뢰도가 0.5 이상이면 사람으로 판단
				if (croppedMaskBuffer.get(index) > 0.5f) {
					personPixelCount++
				}
			}

			Log.d("MODNet", "$neckRowY 행에서의 인물 픽셀 너비: $personPixelCount")
			val origW = inputImage.width   // 1080
			val origH = inputImage.height  // 1920

// 원본 픽셀 배열
			val origPixels = IntArray(origW * origH)
			inputImage.getPixels(origPixels, 0, origW, 0, 0, origW, origH)

// 결과 비트맵 (ARGB_8888 - 투명도 지원)
			val resultBitmap = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)
			val resultPixels = IntArray(origW * origH)

			for (y in 0 until origH) {
				for (x in 0 until origW) {
					// 원본 좌표 → croppedMask 좌표로 변환
					val maskX = (x * scaledWidth.toFloat() / origW).toInt().coerceIn(0, scaledWidth - 1)
					val maskY = (y * scaledHeight.toFloat() / origH).toInt().coerceIn(0, scaledHeight - 1)
					val maskIndex = maskY * scaledWidth + maskX
					val maskValue = croppedMaskBuffer.get(maskIndex)

					val origPixel = origPixels[y * origW + x]
					resultPixels[y * origW + x] = if (maskValue > 0.5f) {
						// 인물 → 원본 색상 유지, alpha=255
						Color.argb(
							255,
							Color.red(origPixel),
							Color.green(origPixel),
							Color.blue(origPixel)
						)
					} else {
						// 배경 → 투명
						Color.TRANSPARENT
					}
				}
			}

			resultBitmap.setPixels(resultPixels, 0, origW, 0, 0, origW, origH)

			// 메모리 정리
			inputBitmap.recycle()
			scaledBitmap.recycle()
			inputTensor.close()
			result.close()

			resultBitmap
		}

	// --------------------- 🔥🔥🔥RESNET🔥🔥🔥 ---------------------
	private fun runSegmentationByResNet(context: Context, uri: Uri) {

		// 1. Uri → Bitmap
		val sourceBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			val source = ImageDecoder.createSource(context.contentResolver, uri)
			ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
				decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
				decoder.isMutableRequired = true
			}
		} else {
			@Suppress("DEPRECATION")
			MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
		}

		// 2. 512×512 리사이즈 + ARGB_8888 보장
		val MODEL_SIZE = 512
		val scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, MODEL_SIZE, MODEL_SIZE, true)
			.copy(Bitmap.Config.ARGB_8888, false)

		// 3. Normalize → float array [1, 3, 512, 512] (BCHW)
		val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
		val std  = floatArrayOf(0.229f, 0.224f, 0.225f)
		val pixels = IntArray(MODEL_SIZE * MODEL_SIZE)
		scaledBitmap.getPixels(pixels, 0, MODEL_SIZE, 0, 0, MODEL_SIZE, MODEL_SIZE)

		val inputTensor = FloatArray(1 * 3 * MODEL_SIZE * MODEL_SIZE)
		val rOffset = 0
		val gOffset = MODEL_SIZE * MODEL_SIZE
		val bOffset = MODEL_SIZE * MODEL_SIZE * 2

		for (i in pixels.indices) {
			val pixel = pixels[i]
			inputTensor[rOffset + i] = (((pixel shr 16) and 0xFF) / 255f - mean[0]) / std[0]
			inputTensor[gOffset + i] = (((pixel shr 8)  and 0xFF) / 255f - mean[1]) / std[1]
			inputTensor[bOffset + i] = ((pixel           and 0xFF) / 255f - mean[2]) / std[2]
		}

		val inputName = ortSession!!.inputNames.iterator().next()
		val shape = longArrayOf(1, 3, MODEL_SIZE.toLong(), MODEL_SIZE.toLong())
		val inputOnnxTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(inputTensor), shape)

		val output = ortSession!!.run(mapOf(inputName to inputOnnxTensor))
		// 출력: [1, 19, 512, 512]
		val rawOutput = (output[0].value as Array<Array<Array<FloatArray>>>)[0] // [19][512][512]

		// 5. Argmax → segmentation mask (각 픽셀의 클래스 번호)
		val maskArray = Array(MODEL_SIZE) { y ->
			IntArray(MODEL_SIZE) { x ->
				var maxClass = 0
				var maxVal = rawOutput[0][y][x]
				for (c in 1 until 19) {
					if (rawOutput[c][y][x] > maxVal) {
						maxVal = rawOutput[c][y][x]
						maxClass = c
					}
				}
				maxClass
			}
		}

		// 6. 원하는 클래스만 필터링
		// 헤어(17) 제외, 얼굴(헤어라인~턱) + 목
		// 0=background, 1=skin, 2=l_brow, 3=r_brow, 4=l_eye, 5=r_eye,
		// 6=eye_g(glasses), 7=l_ear, 8=r_ear, 9=ear_r, 10=nose,
		// 11=mouth, 12=u_lip, 13=l_lip, 14=neck, 15=neck_l, 16=cloth, 17=hair, 18=hat
		val faceAndNeckClasses = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)

		// 7. 원본 이미지 크기에 맞는 마스크 비트맵 생성
		val origW = sourceBitmap.width
		val origH = sourceBitmap.height
		val maskBitmap = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)

		val scaleX = MODEL_SIZE.toFloat() / origW
		val scaleY = MODEL_SIZE.toFloat() / origH

		for (y in 0 until origH) {
			for (x in 0 until origW) {
				val maskY = (y * scaleY).toInt().coerceIn(0, MODEL_SIZE - 1)
				val maskX = (x * scaleX).toInt().coerceIn(0, MODEL_SIZE - 1)
				val cls = maskArray[maskY][maskX]
				if (cls in faceAndNeckClasses) {
					maskBitmap.setPixel(x, y, Color.argb(180, 0, 200, 100)) // 반투명 초록
				} else {
					maskBitmap.setPixel(x, y, Color.TRANSPARENT)
				}
			}
		}

		// 8. 원본 위에 마스크 오버레이
		val resultBitmap = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(resultBitmap)
		canvas.drawBitmap(sourceBitmap, 0f, 0f, null)
		canvas.drawBitmap(maskBitmap, 0f, 0f, null)
		inputOnnxTensor.close()
		output.close()
	}

}