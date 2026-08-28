//package com.tangoplus.tangoface.domain.util
//
//import android.content.Context
//import android.graphics.Bitmap
//import android.graphics.Canvas
//import android.graphics.Color
//import android.graphics.Paint
//import android.graphics.Rect
//import com.tangoplus.tangoface.ui.view.TextBoxOverlayView
//import org.tensorflow.lite.Interpreter
//import org.tensorflow.lite.gpu.CompatibilityList
//import org.tensorflow.lite.gpu.GpuDelegate
//import java.io.FileInputStream
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//import java.nio.channels.FileChannel
//
//class TFLiteSuperResolution(context: Context, modelPath: String = "esrgan.tflite") {
//
//	private val interpreter: Interpreter
//	private val inputShape: IntArray
//	private val outputShape: IntArray
//
//	init {
//		val options = Interpreter.Options().apply {
//			if (CompatibilityList().isDelegateSupportedOnThisDevice) {
//				addDelegate(GpuDelegate())
//			} else {
//				setNumThreads(4)
//			}
//		}
//		interpreter = Interpreter(loadModelFile(context, modelPath), options)
//
//		// 모델이 요구하는 입력/출력 해상도 자동 추출 (예: [1, 128, 128, 3])
//		inputShape = interpreter.getInputTensor(0).shape()
//		outputShape = interpreter.getOutputTensor(0).shape()
//	}
//
//	private fun loadModelFile(context: Context, modelPath: String): ByteBuffer {
//		val fileDescriptor = context.assets.openFd(modelPath)
//		val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
//		val fileChannel = inputStream.channel
//		return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
//	}
//
//	fun upscale(srcBitmap: Bitmap): Bitmap {
//		val inputH = inputShape[1] // 128
//		val inputW = inputShape[2] // 128
//		val outputH = outputShape[1]
//		val outputW = outputShape[2]
//
//		// 1. 모델 규격(128x128)으로 리사이즈
//		val resizedInput = Bitmap.createScaledBitmap(srcBitmap, inputW, inputH, true)
//
//		// 2. 정확한 196,608 bytes 입력 버퍼 생성
//		val inputBuffer = ByteBuffer.allocateDirect(1 * inputH * inputW * 3 * 4).apply {
//			order(ByteOrder.nativeOrder())
//			val pixels = IntArray(inputW * inputH)
//			resizedInput.getPixels(pixels, 0, inputW, 0, 0, inputW, inputH)
//			for (pixel in pixels) {
//				putFloat(Color.red(pixel).toFloat())
//				putFloat(Color.green(pixel).toFloat())
//				putFloat(Color.blue(pixel).toFloat())
//			}
//		}
//
//		// 3. 모델 규격 출력 버퍼 생성
//		val outputBuffer = ByteBuffer.allocateDirect(1 * outputH * outputW * 3 * 4).apply {
//			order(ByteOrder.nativeOrder())
//		}
//
//		// 4. 추론 실행
//		interpreter.run(inputBuffer, outputBuffer)
//		outputBuffer.rewind()
//
//		// 5. 결과 비트맵 생성
//		val outBitmap = Bitmap.createBitmap(outputW, outputH, Bitmap.Config.ARGB_8888)
//		val outPixels = IntArray(outputW * outputH)
//		for (i in outPixels.indices) {
//			val r = outputBuffer.float.coerceIn(0f, 255f).toInt()
//			val g = outputBuffer.float.coerceIn(0f, 255f).toInt()
//			val b = outputBuffer.float.coerceIn(0f, 255f).toInt()
//			outPixels[i] = Color.rgb(r, g, b)
//		}
//		outBitmap.setPixels(outPixels, 0, outputW, 0, 0, outputW, outputH)
//
//		if (resizedInput != srcBitmap) resizedInput.recycle()
//		return outBitmap
//	}
//
//	fun enhanceFaceRegion(
//		baseBitmap: Bitmap,
//		faceRect: TextBoxOverlayView.FaceRect,
//		srModel: TFLiteSuperResolution
//	): Bitmap {
//		val srcW = baseBitmap.width
//		val srcH = baseBitmap.height
//
//		// 1. 얼굴 주변 20% 안전 여백(Padding) 추가
//		val marginX = (faceRect.right - faceRect.left) * 0.2f
//		val marginY = (faceRect.bottom - faceRect.top) * 0.2f
//
//		val cropLeft = (faceRect.left - marginX).toInt().coerceIn(0, srcW)
//		val cropTop = (faceRect.top - marginY).toInt().coerceIn(0, srcH)
//		val cropRight = (faceRect.right + marginX).toInt().coerceIn(0, srcW)
//		val cropBottom = (faceRect.bottom + marginY).toInt().coerceIn(0, srcH)
//
//		val cropW = cropRight - cropLeft
//		val cropH = cropBottom - cropTop
//
//		if (cropW <= 0 || cropH <= 0) return baseBitmap
//
//		// 2. 얼굴 영역 패치만 크롭
//		val facePatch = Bitmap.createBitmap(baseBitmap, cropLeft, cropTop, cropW, cropH)
//
//		// 3. TFLite 초해상도 AI 추론 실행 (얼굴만 빠르게 복원)
//		val enhancedPatch = srModel.upscale(facePatch)
//
//		// 4. 결과 비트맵 캔버스에 복원된 얼굴 영역 덮어쓰기
//		val resultBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
//		val canvas = Canvas(resultBitmap)
//		val dstRect = Rect(cropLeft, cropTop, cropRight, cropBottom)
//		val paint = Paint(Paint.FILTER_BITMAP_FLAG) // 부드러운 합성 필터
//
//		canvas.drawBitmap(enhancedPatch, null, dstRect, paint)
//
//		// 메모리 해제
//		if (facePatch != enhancedPatch) facePatch.recycle()
//		enhancedPatch.recycle()
//
//		return resultBitmap
//	}
//}