package com.tangoplus.facebeautyexpert.domain.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import com.orbbec.obsensor.ColorFrame
import com.orbbec.obsensor.types.CameraDistortion
import com.orbbec.obsensor.types.CameraIntrinsic
import com.orbbec.obsensor.types.Extrinsic
import com.orbbec.obsensor.types.Format
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkResult
import com.tangoplus.facebeautyexpert.domain.vision.pose.PoseLandmarkResult
import com.tangoplus.facebeautyexpert.orbbec.vo.Point3D
import com.tangoplus.facebeautyexpert.orbbec.vo.ScaledIntrinsic
import org.json.JSONObject
import kotlin.Pair
import kotlin.math.atan
import kotlin.math.cos

object LandmarkUtility {

	fun generateGradientLayer(
		filledDepth: FloatArray,
		depthW: Int,
		depthH: Int,
		minDepth: Int,
		maxDepth: Int,
		alpha: Int = 160   // 배경 투명도, 0~255
	): Bitmap {
		val pixels = IntArray(depthW * depthH)
		for (i in pixels.indices) {
			val d = filledDepth[i]
			if (d <= 0f) {
				pixels[i] = Color.argb(0, 0, 0, 0)
			} else {
				val normalized = ((d - minDepth) / (maxDepth - minDepth)).coerceIn(0f, 1f)
				val hue = normalized * 270f
				val rgb = Color.HSVToColor(floatArrayOf(hue, 1f, 0.9f))
				pixels[i] = Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
			}
		}
		val bmp = Bitmap.createBitmap(depthW, depthH, Bitmap.Config.ARGB_8888)
		bmp.setPixels(pixels, 0, depthW, 0, 0, depthW, depthH)
		return bmp
	}


	fun CameraIntrinsic.toScaledIntrinsic(): ScaledIntrinsic {
		return ScaledIntrinsic(
			fx = this.fx,
			fy = this.fy,
			cx = this.cx,
			cy = this.cy,
			width = this.width.toInt(),
			height = this.height.toInt()
		)
	}


	fun buildFaceMaskPathFromPoints(points: List<Pair<Float, Float>>, outlineIndexes: List<Int>): Path {
		val path = Path()
		outlineIndexes.forEachIndexed { i, idx ->
			val (x, y) = points[idx]
			if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
		}
		path.close()
		return path
	}

	fun Extrinsic.rotArray(): FloatArray {
		val field = this.javaClass.getDeclaredField("mRot")
		field.isAccessible = true
		return field.get(this) as FloatArray
	}

	fun Extrinsic.transArray(): FloatArray {
		val field = this.javaClass.getDeclaredField("mTrans")
		field.isAccessible = true
		return field.get(this) as FloatArray
	}
	fun CameraDistortion.k1(): Float = getPrivateFloat("mK1")
	fun CameraDistortion.k2(): Float = getPrivateFloat("mK2")
	fun CameraDistortion.k3(): Float = getPrivateFloat("mK3")
	fun CameraDistortion.k4(): Float = getPrivateFloat("mK4")
	fun CameraDistortion.k5(): Float = getPrivateFloat("mK5")
	fun CameraDistortion.k6(): Float = getPrivateFloat("mK6")
	fun CameraDistortion.p1(): Float = getPrivateFloat("mP1")
	fun CameraDistortion.p2(): Float = getPrivateFloat("mP2")

	fun Any.getPrivateFloat(fieldName: String): Float {
		val field = this.javaClass.getDeclaredField(fieldName)
		field.isAccessible = true
		return field.get(this) as Float
	}
	fun undistortLandmarks(
		points: List<Pair<Float, Float>>,   // (px, py) 픽셀 좌표 리스트
		intrinsic: ScaledIntrinsic,
		distortion: CameraDistortion
	): List<Pair<Float, Float>> {
		val cameraMatrix = org.opencv.core.Mat(3, 3, org.opencv.core.CvType.CV_64F)
		cameraMatrix.put(0, 0, intrinsic.fx.toDouble())
		cameraMatrix.put(0, 1, 0.0)
		cameraMatrix.put(0, 2, intrinsic.cx.toDouble())
		cameraMatrix.put(1, 0, 0.0)
		cameraMatrix.put(1, 1, intrinsic.fy.toDouble())
		cameraMatrix.put(1, 2, intrinsic.cy.toDouble())
		cameraMatrix.put(2, 0, 0.0)
		cameraMatrix.put(2, 1, 0.0)
		cameraMatrix.put(2, 2, 1.0)

		val distCoeffs = org.opencv.core.MatOfDouble(
			distortion.k1().toDouble(), distortion.k2().toDouble(),
			distortion.p1().toDouble(), distortion.p2().toDouble(),
			distortion.k3().toDouble(), distortion.k4().toDouble(),
			distortion.k5().toDouble(), distortion.k6().toDouble()
		)

		val srcMat = org.opencv.core.MatOfPoint2f()
		srcMat.fromList(points.map { org.opencv.core.Point(it.first.toDouble(), it.second.toDouble()) })

		val dstMat = org.opencv.core.MatOfPoint2f()
		// P(새 카메라 행렬)를 cameraMatrix와 동일하게 넘기면 결과가 다시 픽셀 좌표계로 나옴
		org.opencv.calib3d.Calib3d.undistortPoints(srcMat, dstMat, cameraMatrix, distCoeffs, org.opencv.core.Mat(), cameraMatrix)

		val result = dstMat.toList().map { Pair(it.x.toFloat(), it.y.toFloat()) }

		cameraMatrix.release(); distCoeffs.release(); srcMat.release(); dstMat.release()
		return result
	}

	fun undistortSinglePoint(
		x: Float, y: Float,
		intrinsic: CameraIntrinsic,
		distortion: CameraDistortion
	): Pair<Float, Float> {
		val cameraMatrix = org.opencv.core.Mat(3, 3, org.opencv.core.CvType.CV_64F)
		cameraMatrix.put(0, 0, intrinsic.fx.toDouble())
		cameraMatrix.put(0, 1, 0.0)
		cameraMatrix.put(0, 2, intrinsic.cx.toDouble())
		cameraMatrix.put(1, 0, 0.0)
		cameraMatrix.put(1, 1, intrinsic.fy.toDouble())
		cameraMatrix.put(1, 2, intrinsic.cy.toDouble())
		cameraMatrix.put(2, 0, 0.0)
		cameraMatrix.put(2, 1, 0.0)
		cameraMatrix.put(2, 2, 1.0)

		val distCoeffs = org.opencv.core.MatOfDouble(
			distortion.k1().toDouble(), distortion.k2().toDouble(),
			distortion.p1().toDouble(), distortion.p2().toDouble(),
			distortion.k3().toDouble(), distortion.k4().toDouble(),
			distortion.k5().toDouble(), distortion.k6().toDouble()
		)

		val srcMat = org.opencv.core.MatOfPoint2f(org.opencv.core.Point(x.toDouble(), y.toDouble()))
		val dstMat = org.opencv.core.MatOfPoint2f()
		org.opencv.calib3d.Calib3d.undistortPoints(srcMat, dstMat, cameraMatrix, distCoeffs, org.opencv.core.Mat(), cameraMatrix)

		val result = dstMat.toArray()[0]
		cameraMatrix.release(); distCoeffs.release(); srcMat.release(); dstMat.release()
		return Pair(result.x.toFloat(), result.y.toFloat())
	}



	fun buildFaceMaskPath(
		landmarks: List<FaceLandmarkResult.FaceLandmark>,   // mvm.face2DResult.landmarks
		outlineIndexes: List<Int>,
		width: Int,
		height: Int,
		flipX: Boolean = false
	): Path {
		val path = Path()
		outlineIndexes.forEachIndexed { i, idx ->
			val lm = landmarks[idx]
			val x = if (flipX) (1.0f - lm.x) * width else lm.x * width
			val y = lm.y * height
			if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
		}
		path.close()
		return path
	}


	fun scaleHsvToFHD(
		originalHsv: List<Pair<Float, Float>>?,
		originalWidth: Int = 720,
		originalHeight: Int = 1280,
		targetWidth: Int = 1080,
		targetHeight: Int = 1920
	): List<Pair<Float, Float>> {
		val scaleX = targetWidth.toFloat() / originalWidth
		val scaleY = targetHeight.toFloat() / originalHeight

		return originalHsv?.map { hsv ->
			Pair(
				hsv.first * scaleX,
				hsv.second * scaleY
			)
		} ?: emptyList()
	}

	fun scaleLandmarksToFHD(
		faceLandmarks: FaceLandmarkResult?,
		poseLandmarks: PoseLandmarkResult?,
		hairLineLandmarks: List<Pair<Float, Float>>?,
		originalWidth: Int = 720,
		originalHeight: Int = 1280,
		targetWidth: Int = 1080,
		targetHeight: Int = 1920
	): Triple<FaceLandmarkResult?, PoseLandmarkResult?, List<Pair<Float,Float>>?> {
		val scaleX = targetWidth.toFloat() / originalWidth
		val scaleY = targetHeight.toFloat() / originalHeight

		val scaledFace = faceLandmarks?.let { result ->
			FaceLandmarkResult(
				result.landmarks.map { l ->
					FaceLandmarkResult.FaceLandmark(
						x = l.x * scaleX,
						y = l.y * scaleY
					)
				}
			)
		}

		val scaledPose = poseLandmarks?.let { result ->
			PoseLandmarkResult(
				result.landmarks.map { l ->
					PoseLandmarkResult.PoseLandmark(
						x = l.x * scaleX,
						y = l.y * scaleY
					)
				}
			)
		}
		val scaledHairline = hairLineLandmarks?.let { result ->
			result.map { l ->
			Pair(
				l.first * scaleX,
				l.second * scaleY
			)
		}
		}
		return Triple(scaledFace, scaledPose, scaledHairline)
	}


	fun extractFaceCoordinates(jsonData: JSONObject?): List<Pair<Float, Float>>? {
		val poseData = jsonData?.optJSONArray("face_landmark")
		return if (poseData != null) {
			List(poseData.length()) { i ->
				val landmark = poseData.getJSONArray(i)
				Pair(
					landmark.optDouble(1).toFloat(),
					landmark.optDouble(2).toFloat()
				)
			}
		} else null
	}


	fun extractFace3DCoordinates(jsonData: JSONObject?): List<Triple<Float, Float, Float>>? {
		val faceData = jsonData?.optJSONArray("face_landmark")
		return if (faceData != null) {
			List(faceData.length()) { i ->
				val landmark = faceData.getJSONArray(i)
				Triple(
					landmark.optDouble(3).toFloat(),
					landmark.optDouble(4).toFloat(),
					landmark.optDouble(5).toFloat()
				)
			}
		} else null
	}
	fun extractPoseCoordinates(jsonData: JSONObject?): List<Pair<Float , Float>>? {
		val poseData = jsonData?.optJSONArray("pose_landmark")
		return if (poseData != null) {
			List(poseData.length()) { i ->
				val landmark = poseData.getJSONArray(i)
				Pair(
					landmark.optDouble(1).toFloat(),
					landmark.optDouble(2).toFloat()
				)
			}
		} else null
	}
	fun extractPose3DCoordinates(jsonData: JSONObject?): List<Triple<Float, Float, Float>>? {
		val poseData = jsonData?.optJSONArray("pose_landmark")
		return if (poseData != null) {
			List(poseData.length()) { i ->
				val landmark = poseData.getJSONArray(i)
				Triple(
					landmark.optDouble(3).toFloat(),
					landmark.optDouble(4).toFloat(),
					landmark.optDouble(5).toFloat()
				)
			}
		} else null
	}
	fun extractBowCoordinates(jsonData: JSONObject?): List<Pair<Float, Float>>? {
		val data = jsonData?.optJSONArray("bow_landmark")
		return if (data != null) {
			List(data.length()) { i ->
				val landmark = data.getJSONObject(i)
				Pair(
					landmark.optDouble("cx").toFloat(),
					landmark.optDouble("cy").toFloat()
				)
			}
		} else null
	}

	fun extractHairLineCoordinate(jsonData: JSONObject?): List<Pair<Float, Float>>? {
		val data = jsonData?.optJSONArray("hairline_landmark")
		return if (data != null) {
			List(data.length()) { i ->
				val landmark = data.getJSONArray(i)
				Pair(
					landmark.optDouble(1).toFloat(),
					landmark.optDouble(2).toFloat()
				)
			}
		} else null
	}
	fun extractHairLine3DCoordinate(jsonData: JSONObject?): List<Triple<Float, Float, Float>>? {
		val hairData = jsonData?.optJSONArray("hairline_landmark")
		return if (hairData != null) {
			List(hairData.length()) { i ->
				val landmark = hairData.getJSONArray(i)
				Triple(
					landmark.optDouble(3).toFloat(),
					landmark.optDouble(4).toFloat(),
					landmark.optDouble(5).toFloat()
				)
			}
		} else null
	}



//	fun calculateScreenX(olW: Float, xx: Float): Float {
//		val scaleFactor = olW / 1920
//		val offsetX = ((olW - 1920 * scaleFactor) / 2)
//		val x = xx * olW / scaleFactor + offsetX
//		return x
//	}
//
//	fun calculateScreenY(olH: Float, yy: Float): Float {
//		val scaleFactor = olH / 1080
//		val offsetY = (olH - 1080 * scaleFactor) / 2
//		val y = yy * olH / scaleFactor + offsetY
//		return y
//	}
	// X 좌표: 0.0 ~ 1.0 값을 1920 픽셀 비율로 변환
	fun calculateScreenX(xx: Float): Float = xx * 1080f

	// Y 좌표: 0.0 ~ 1.0 값을 1080 픽셀 비율로 변환
	fun calculateScreenY(yy: Float): Float = yy * 1920f

	fun colorFrameToBitmapSafe(frame: ColorFrame, rotationDegrees: Int = 0): Bitmap? {
		val w = frame.getWidth()
		val h = frame.getHeight()
		if (w <= 0 || h <= 0) {
			Log.e("convertError", "Invalid frame size: ${w}x${h}")
			return null
		}
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
		intrinsic: CameraIntrinsic,
		distortion: CameraDistortion
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
			val undistorted = undistortSinglePoint(calcX, calcY, intrinsic, distortion)
			val ucalcX = undistorted.first
			val ucalcY = undistorted.second

//			Log.d("Undistort", "raw=($calcX,$calcY) -> undist=($ucalcX,$ucalcY)")
			if (depthX in 0 until depthWidth && depthY in 0 until depthHeight) {
				val idx = depthY * depthWidth + depthX
				val depthMm = depthData[idx].toInt() and 0xFFFF
				val depthM = depthMm / 1000f // mm를 m로 변환

				if (depthM > 0) {
					val point3D = convertDepthPixelTo3D(
						intrinsic,
						ucalcX,  // ★ 1280 좌표가 아닌 640 스케일 좌표 사용
						ucalcY,  // ★ 720 좌표가 아닌 360 스케일 좌표 사용
						depthM,
					)
					landmarks3D.add(point3D)
				} else {
					// Depth 값이 0인 경우 주변에서 찾기
					val nearbyDepth = findNearbyDepth(depthData, depthX, depthY, depthWidth, depthHeight)
					if (nearbyDepth > 0) {
						val point3D = convertDepthPixelTo3D(
							intrinsic,
							ucalcX,  // ★ 여기도 동일하게 적용
							ucalcY,
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