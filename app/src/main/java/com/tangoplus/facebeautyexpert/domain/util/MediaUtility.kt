package com.tangoplus.facebeautyexpert.domain.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.orbbec.obsensor.ColorFrame
import com.orbbec.obsensor.Frame
import com.orbbec.obsensor.VideoStreamProfile
import com.orbbec.obsensor.types.CameraIntrinsic
import com.orbbec.obsensor.types.Format
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkResult
import com.tangoplus.facebeautyexpert.orbbec.vo.Point3D
import com.tangoplus.facebeautyexpert.orbbec.vo.ScaledIntrinsic
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.atan
import kotlin.math.cos

object MediaUtility {
	fun Float.roundToOneDecimal(): Float {
		return BigDecimal(this.toDouble()).setScale(1, RoundingMode.HALF_UP).toFloat()
	}

	fun extractVideoCoordinates(jsonData: JSONObject): List<List<Pair<Float, Float>>> {
		val faceLandmarks = jsonData.getJSONArray("face_landmark")

		return List(faceLandmarks.length()) { i ->
			val frameLandmarks = faceLandmarks.getJSONArray(i)

			List(frameLandmarks.length()) { j ->
				val landmark = frameLandmarks.getJSONArray(j)
				Pair(
					landmark.getDouble(1).toFloat(),  // x
					landmark.getDouble(2).toFloat()   // y
				)
			}
		}
	}

	fun calculateScreenX(olW: Float, xx: Float): Float {
		val scaleFactor = olW / 1920
		val offsetX = ((olW - 1920 * scaleFactor) / 2)
		val x = xx * olW / scaleFactor + offsetX
		return x
	}

	fun calculateScreenY(olH: Float, yy: Float): Float {
		val scaleFactor = olH / 1080
		val offsetY = (olH - 1080 * scaleFactor) / 2
		val y = yy * olH / scaleFactor + offsetY
		return y
	}

	fun colorFrameToMPImageRaw(colorFrame: Frame): MPImage? {
		val profile = colorFrame.streamProfile
		val (w, h) = if (profile is VideoStreamProfile) {
			profile.width to profile.height
		} else 1280 to 720 // fallback

		// 1) Frame → Bitmap (이미 가지고 계신 함수 재사용)
		val srcBitmap = colorFrameToBitmap(colorFrame, w, h) ?: return null

		// 2) MediaPipe는 ARGB_8888 권장. 아닐 경우 복사
		val bmp = if (srcBitmap.config != Bitmap.Config.ARGB_8888) {
			srcBitmap.copy(Bitmap.Config.ARGB_8888, /* mutable= */ false)
		} else srcBitmap

		// 3) 그대로 MPImage 래핑 (보정/회전/미러 적용 안함)
		return BitmapImageBuilder(bmp).build()
	}

	fun colorFrameToBitmapSafe(frame: ColorFrame, rotationDegrees: Int = 0): Bitmap? {
		val w = frame.getWidth()
		val h = frame.getHeight()
		if (w <= 0 || h <= 0) {
			Log.e("convertError", "Invalid frame size: ${w}x${h}")
			return null
		}
//        Log.d("FrameInfo", "w=${frame.getWidth()}, h=${frame.getHeight()}, fmt=${frame.getFormat()}, size=${frame.getDataSize()}")
		val dataSize = frame.getDataSize()
		val data = ByteArray(dataSize)
		frame.getData(data)

		// 프레임 포맷 확인(예: RGB888, BGRA, YUYV 등)
		val format = frame.getFormat()

		var mat1: org.opencv.core.Mat? = null
		var mat2: org.opencv.core.Mat? = null
		return try {
			val baseBitmap = when (format) {
				// 3바이트/픽셀
				Format.RGB -> {
					if (data.size != w * h * 3) {
						Log.e("convertError", "RGB888 size mismatch: got=${data.size}, expected=${w * h * 3}")
						return null
					}
					// ARGB_8888 Bitmap으로 직접 포장
					val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
					val pixels = IntArray(w * h)
					var src = 0
					for (i in pixels.indices) {
						val r = data[src++].toInt() and 0xFF
						val g = data[src++].toInt() and 0xFF
						val b = data[src++].toInt() and 0xFF
						pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
					}
					bmp.setPixels(pixels, 0, w, 0, 0, w, h)
					bmp
				}

				// 4바이트/픽셀
				Format.BGRA -> {
					if (data.size != w * h * 4) {
						Log.e("convertError", "BGRA size mismatch: got=${data.size}, expected=${w * h * 4}")
						return null
					}
					mat1 = org.opencv.core.Mat(h, w, org.opencv.core.CvType.CV_8UC4)
					mat1.put(0, 0, data)

					mat2 = org.opencv.core.Mat()
					org.opencv.imgproc.Imgproc.cvtColor(
						mat1,
						mat2,
						org.opencv.imgproc.Imgproc.COLOR_BGRA2RGBA
					)

					val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
					org.opencv.android.Utils.matToBitmap(mat2, bmp)
					bmp
				}

				// 2바이트/픽셀
				Format.YUYV -> {
					if (data.size != w * h * 2) {
						Log.e("convertError", "YUYV size mismatch: got=${data.size}, expected=${w * h * 2}")
						return null
					}
					mat1 = org.opencv.core.Mat(h, w, org.opencv.core.CvType.CV_8UC2)
					mat1.put(0, 0, data)

					mat2 = org.opencv.core.Mat()
					org.opencv.imgproc.Imgproc.cvtColor(
						mat1,
						mat2,
						org.opencv.imgproc.Imgproc.COLOR_YUV2RGBA_YUY2
					)

					val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
					org.opencv.android.Utils.matToBitmap(mat2, bmp)
					bmp
				}

				else -> {
					Log.e("convertError", "Unsupported format: $format")
					null
				}
			}

			if (baseBitmap != null && rotationDegrees != 0) {
				rotateBitmap(baseBitmap, rotationDegrees)
			} else {
				baseBitmap
			}

		} catch (e: Throwable) {
			Log.e("convertError", "Exception: ${e.message}")
			null
		} finally {
			// ✅ OpenCV Mat 반드시 해제
			mat1?.release()
			mat2?.release()
		}
	}

	private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
		val matrix = Matrix()
		matrix.postRotate(degrees.toFloat())

		val rotated = Bitmap.createBitmap(
			source,
			0,
			0,
			source.width,
			source.height,
			matrix,
			true
		)

		// 원본 Bitmap 해제 (메모리 절약)
		if (rotated != source) {
			source.recycle()
		}

		return rotated
	}
	fun colorFrameToBitmap(colorFrame: Frame, width: Int, height: Int): Bitmap? {
		return try {
			val data = ByteArray(colorFrame.dataSize)
			colorFrame.getData(data)

			// RealSense Color Frame은 보통 RGB8 형식
			val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
			val pixels = IntArray(width * height)

			// RGB 데이터를 ARGB로 변환
			for (i in pixels.indices) {
				val pixelIndex = i * 3
				if (pixelIndex + 2 < data.size) {
					val r = data[pixelIndex].toInt() and 0xFF
					val g = data[pixelIndex + 1].toInt() and 0xFF
					val b = data[pixelIndex + 2].toInt() and 0xFF
					pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
				}
			}

			bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
			bitmap

		} catch (e: Exception) {
			Log.e("convertError", "Error converting colorFrame to bitmap: ${e.message}")
			null
		}
	}

	fun convertDepthPixelTo3D(
		intr: CameraIntrinsic,
		pixelX: Float,
		pixelY: Float,
		depth: Float
	): Point3D {
		val Z = depth

		// 시야각 기반 보정
		val thetaX = atan((pixelX - intr.cx) / intr.fx)
		val thetaY = atan((pixelY - intr.cy) / intr.fy)

		// cos(θ)로 보정: 중심에서 벗어날수록 평면 확장 과대가 줄어듦
		val correctedX = (pixelX - intr.cx) / intr.fx * Z * cos(thetaX)
		val correctedY = (pixelY - intr.cy) / intr.fy * Z * cos(thetaY)

		return Point3D(correctedX, correctedY, Z)
	}

	fun convertFaceLandmarksTo3D(
		landmarks2D: FaceLandmarkResult,
		depthData: ShortArray,
		depthWidth: Int,
		depthHeight: Int,
		intrinsic: CameraIntrinsic
	): List<Point3D> {
		val landmarks3D = mutableListOf<Point3D>()

		// Orbbec SDK의 Intrinsic 원본 해상도
		val intrinsicWidth = 640f
		val intrinsicHeight = 360f

		for (landmark in landmarks2D.landmarks) {
			// landmark.x, y가 0.0 ~ 1.0 사이의 정규화된 값이라고 가정할 때:

			// 1. Depth Map(1280x720) 배열 인덱스 참조용 좌표
			val depthX = (landmark.x * depthWidth).toInt()
			val depthY = (landmark.y * depthHeight).toInt()

			// 2. 3D 투영을 위한 Intrinsic(640x360) 스케일링 좌표 (X, Y 계산용)
			val calcX = landmark.x * intrinsicWidth
			val calcY = landmark.y * intrinsicHeight

			if (depthX in 0 until depthWidth && depthY in 0 until depthHeight) {
				val idx = depthY * depthWidth + depthX
				val depthMm = depthData[idx].toInt() and 0xFFFF
				val depthM = depthMm / 1000f // mm를 m로 변환

				if (depthM > 0) {
					val point3D = convertDepthPixelTo3D(
						intrinsic,
						calcX,  // ★ 1280 좌표가 아닌 640 스케일 좌표 사용
						calcY,  // ★ 720 좌표가 아닌 360 스케일 좌표 사용
						depthM,
					)
					landmarks3D.add(point3D)
				} else {
					// Depth 값이 0인 경우 주변에서 찾기
					val nearbyDepth = findNearbyDepth(depthData, depthX, depthY, depthWidth, depthHeight)
					if (nearbyDepth > 0) {
						val point3D = convertDepthPixelTo3D(
							intrinsic,
							calcX,  // ★ 여기도 동일하게 적용
							calcY,
							nearbyDepth / 1000f,
						)
						landmarks3D.add(point3D)
					} else {
						landmarks3D.add(Point3D(0f, 0f, 0f))
					}
				}
			} else {
				landmarks3D.add(Point3D(0f, 0f, 0f))
			}
		}
		return landmarks3D
	}


	// 3. 주변 픽셀에서 유효한 depth 찾기
	fun findNearbyDepth(
		depthData: ShortArray,
		centerX: Int,
		centerY: Int,
		width: Int,
		height: Int,
		radius: Int = 5
	): Int {
		for (r in 1..radius) {
			for (dy in -r..r) {
				for (dx in -r..r) {
					val x = centerX + dx
					val y = centerY + dy

					if (x in 0 until width && y in 0 until height) {
						val idx = y * width + x
						val depth = depthData[idx].toInt() and 0xFFFF
						if (depth > 0) return depth
					}
				}
			}
		}
		return 0
	}


	fun scaleAndCropIntrinsicCenterCrop(
		fx: Float, fy: Float, cx: Float, cy: Float,
		baseWidth: Int, baseHeight: Int,
		targetWidth: Int, targetHeight: Int
	): ScaledIntrinsic {
		val bw = baseWidth.toFloat()
		val bh = baseHeight.toFloat()
		val tw = targetWidth.toFloat()
		val th = targetHeight.toFloat()

		// 1) 동일 배율 s 로 스케일 (가로 기준으로 꽉 채움: CENTER_CROP)
		val sx = tw / bw
		val sy = th / bh
		// CENTER_CROP 은 더 큰 배율을 써서 한쪽을 크롭
		val s = maxOf(sx, sy)

		val scaledW = bw * s          // = max(tw, tw) 보통 tw 이상
		val scaledH = bh * s          // 보통 th 이상

		// 2) 남는 쪽 크롭량 (좌우/상하 반반 크롭)
		val cropX = (scaledW - tw) * 0.5f   // 좌/우 각각 cropX 만큼 잘림
		val cropY = (scaledH - th) * 0.5f   // 상/하 각각 cropY 만큼 잘림

		// 3) Intrinsic 스케일 + 크롭 보정
		val fx2 = fx * s
		val fy2 = fy * s
		val cx2 = cx * s - cropX
		val cy2 = cy * s - cropY

		return ScaledIntrinsic(
			fx = fx2,
			fy = fy2,
			cx = cx2,
			cy = cy2,
			width = targetWidth,
			height = targetHeight
		)
	}

}