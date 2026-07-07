package com.tangoplus.facebeautyexpert.domain.opencv

import android.graphics.Bitmap
import androidx.annotation.WorkerThread
import com.google.mediapipe.framework.image.MPImage
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

class HsvColorTracker(
	private var hsvRange: HsvRange = HsvRange.exampleRedDual().second,
	private val minBlobAreaPx: Int = 400, // reject tiny noise blobs
	private val useOpenMorphology: Boolean = true, // open/close to clean mask
	private val smoothFactor: Float = 0.25f // EMA smoothing for track point
) {
	data class HsvRange(
		val low: Scalar, // Scalar(h, s, v)
		val high: Scalar // Scalar(h, s, v)
	) {
		companion object {
//			fun fromCenter(center: Scalar, dh: Int = 10, ds: Int = 60, dv: Int = 60): HsvRange {
//				val h = center.`val`[0];
//				val s = center.`val`[1];
//				val v = center.`val`[2]
//				val loH = max(0.0, h - dh).toInt()
//				val hiH = min(179.0, h + dh).toInt()
//				val loS = max(0.0, s - ds).toInt()
//				val hiS = min(255.0, s + ds).toInt()
//				val loV = max(0.0, v - dv).toInt()
//				val hiV = min(255.0, v + dv).toInt()
//				return HsvRange(
//					Scalar(loH.toDouble(), loS.toDouble(), loV.toDouble()),
//					Scalar(hiH.toDouble(), hiS.toDouble(), hiV.toDouble())
//				)
//			}

			//            fun exampleBlue() = HsvRange(Scalar(95.0, 80.0, 80.0), Scalar(130.0, 255.0, 255.0))
//            fun exampleGreen() = HsvRange(Scalar(40.0, 60.0, 60.0), Scalar(85.0, 255.0, 255.0))
			fun exampleRedDual(): Pair<HsvRange, HsvRange> =
				HsvRange(
					Scalar(0.0, 90.0, 100.0),
					Scalar(10.0, 255.0, 255.0)
				) to
					HsvRange(
						Scalar(170.0, 100.0, 90.0),
						Scalar(180.0, 255.0, 255.0)
					)
		}
	}
	// rgb bgr

	data class Target(
		val bbox: Rect,
		val center: Point,
		val area: Double
	)

	data class MultiTrackResult(
		val targets: List<Target>,
		val maskDebug: Bitmap? = null
	)

	private val lastCenters: MutableList<Point?> = mutableListOf()

	// ---------# contour할 갯수 #----------
	private var maxTargets: Int = 3

	@WorkerThread
	fun process(mpImage: MPImage, makeDebugMask: Boolean = false): MultiTrackResult {
		val bmp: Bitmap = mpImageToBitmap(mpImage)
		return process(bmp, makeDebugMask)
	}

	@WorkerThread
	fun process(bitmap: Bitmap, makeDebugMask: Boolean = false): MultiTrackResult {
		if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
			android.util.Log.e("HSV", "Invalid bitmap")
			return MultiTrackResult(emptyList(), null)
		}

		val srcBmp: Bitmap = if (bitmap.config != Bitmap.Config.ARGB_8888 || !bitmap.isMutable) {
			bitmap.copy(Bitmap.Config.ARGB_8888, false)
		} else bitmap

		val rgba = Mat()
		try {
			Utils.bitmapToMat(srcBmp, rgba)
			if (rgba.empty()) return MultiTrackResult(emptyList(), null)

			// RGBA → HSV
			val hsv = Mat()
			Imgproc.cvtColor(rgba, hsv, Imgproc.COLOR_RGB2HSV)
			if (hsv.empty()) return MultiTrackResult(emptyList(), null)

			// inRange
			val mask = Mat()
			val (redLo, redHi) = HsvRange.exampleRedDual()
			if (hsvRange == redLo || hsvRange == redHi) {
				val m1 = Mat();
				val m2 = Mat()
				Core.inRange(hsv, redLo.low, redLo.high, m1)
				Core.inRange(hsv, redHi.low, redHi.high, m2)
				Core.bitwise_or(m1, m2, mask)
				m1.release(); m2.release()
			} else {
				Core.inRange(hsv, hsvRange.low, hsvRange.high, mask)
			}

			if (useOpenMorphology) {
				val k = Imgproc.getStructuringElement(
					Imgproc.MORPH_ELLIPSE,
					Size(7.0, 7.0) // 노이즈 억제 강화
				)
				Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, k)
				Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, k)
			}

			// 컨투어
			val contours = mutableListOf<MatOfPoint>()
			val hierarchy = Mat()
			Imgproc.findContours(
				mask, contours, hierarchy,
				Imgproc.RETR_EXTERNAL,
				Imgproc.CHAIN_APPROX_SIMPLE
			)
			hierarchy.release()

			// 면적 필터 + 정렬
			/* 둘중에 큰 값을 쓰기 때문에 "mask.cols()" 를 곱하는 Double을 조정해야함
			 * ex) 1920 * 1080 = 2073600 의 * 0.000055 = 114.048
			 */

			val minAreaAbs = max(minBlobAreaPx.toDouble(), 0.000055 * mask.cols() * mask.rows())
			val candidates = contours
				.map { c -> c to Imgproc.contourArea(c) }
				.filter { it.second >= minAreaAbs }
				.sortedByDescending { it.second }
				.take(maxTargets)

			// 타깃들의 (bbox, centroid) 계산
			val detectedCenters = ArrayList<Point>()
			val detectedBBoxes = ArrayList<Rect>()
			val detectedAreas = ArrayList<Double>()

			for ((contour, area) in candidates) {
				val bbox = Imgproc.boundingRect(contour)
				val m = Imgproc.moments(contour)
				val center = if (m.m00 != 0.0)
					Point(m.m10 / m.m00, m.m01 / m.m00)
				else
					Point(bbox.x + bbox.width / 2.0, bbox.y + bbox.height / 2.0)

				detectedCenters.add(center)
				detectedBBoxes.add(bbox)
				detectedAreas.add(area)
			}

			// 프레임 간 매칭(최근접) + 스무딩
			val smoothedTargets = smoothAndAssign(detectedCenters, detectedBBoxes, detectedAreas)

			// 디버그 마스크
			val debugBmp = if (makeDebugMask) {
				val preview = Mat()
				Imgproc.resize(
					mask, preview,
					Size(mask.cols() * 0.33, mask.rows() * 0.33)
				)
				val dbg = Bitmap.createBitmap(preview.cols(), preview.rows(), Bitmap.Config.ARGB_8888)
				Imgproc.cvtColor(preview, preview, Imgproc.COLOR_GRAY2RGBA)
				Utils.matToBitmap(preview, dbg)
				preview.release()
				dbg
			} else null

			// 정리
			hsv.release(); mask.release()
			contours.forEach { it.release() }
			rgba.release()

			return MultiTrackResult(smoothedTargets, debugBmp)

		} catch (t: Throwable) {
			android.util.Log.e("HSV", "process() exception: ${t.message}", t)
			rgba.release()
			return MultiTrackResult(emptyList(), null)
		}
	}

	private fun smoothAndAssign(
		detectedCenters: List<Point>,
		detectedBBoxes: List<Rect>,
		detectedAreas: List<Double>
	): List<Target> {
		// lastCenters 크기 보정
		if (lastCenters.size < maxTargets) {
			repeat(maxTargets - lastCenters.size) { lastCenters.add(null) }
		}

		// 매칭: 기존 lastCenters ↔ 새 centers (그리디 최근접)
		val remainingDet = detectedCenters.indices.toMutableSet()
		val assignment = IntArray(maxTargets) { -1 } // index into detectedCenters or -1

		for (i in 0 until maxTargets) {
			val prev = lastCenters[i] ?: continue
			// i번 트랙에 가장 가까운 새 중심 찾기
			var bestJ = -1
			var bestD = Double.MAX_VALUE
			for (j in remainingDet) {
				val d = dist2(prev, detectedCenters[j])
				if (d < bestD) {
					bestD = d; bestJ = j
				}
			}
			if (bestJ >= 0) {
				assignment[i] = bestJ
				remainingDet.remove(bestJ)
			}
		}

		// 남은 검출들을 빈 슬롯에 채우기
		for (i in 0 until maxTargets) {
			if (assignment[i] == -1 && remainingDet.isNotEmpty()) {
				val j = remainingDet.first()
				assignment[i] = j
				remainingDet.remove(j)
			}
		}

		// 스무딩 + 결과 생성
		val out = mutableListOf<Target>()
		for (i in 0 until maxTargets) {
			val j = assignment[i]
			if (j == -1) {
				// 이번 프레임에서 못 찾음 → 유지/지움
				lastCenters[i] = null
				continue
			}
			val raw = detectedCenters[j]
			val smoothed = lastCenters[i]?.let { p ->
				Point(
					p.x + (raw.x - p.x) * smoothFactor,
					p.y + (raw.y - p.y) * smoothFactor
				)
			} ?: raw
			lastCenters[i] = smoothed

			out.add(Target(detectedBBoxes[j], smoothed, detectedAreas[j]))
		}

		// 면적 큰 순으로 재정렬(옵션)
		return out.sortedByDescending { it.area }
	}

	private fun dist2(a: Point, b: Point): Double {
		val dx = a.x - b.x
		val dy = a.y - b.y
		return dx * dx + dy * dy
	}


	private fun mpImageToBitmap(mpImage: MPImage): Bitmap {
// Many apps create MPImage from Bitmap; if so, try to extract it via reflection fallback.
// If not available, we ask callers to supply Bitmap directly to process().
		try {
			val field = MPImage::class.java.getDeclaredField("bitmap")
			field.isAccessible = true
			val bmp = field.get(mpImage) as? Bitmap
			if (bmp != null) return bmp
		} catch (_: Throwable) {
		}


// Fallback: if MPImage contains RGBA ByteBuffer with width/height metadata
		try {
			val getWidth = MPImage::class.java.getDeclaredMethod("getWidth")
			val getHeight = MPImage::class.java.getDeclaredMethod("getHeight")
			val width = getWidth.invoke(mpImage) as Int
			val height = getHeight.invoke(mpImage) as Int
			val rgbaMethod = MPImage::class.java.getDeclaredMethod("getRgbaBuffer")
			val buf = rgbaMethod.invoke(mpImage) as java.nio.ByteBuffer
			val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
			buf.rewind()
			bmp.copyPixelsFromBuffer(buf)
			return bmp
		} catch (_: Throwable) {
		}


		throw IllegalArgumentException("Unsupported MPImage backing type. Pass a Bitmap to process().")
	}

}
