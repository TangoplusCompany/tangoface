package com.tangoplus.tangoface_compose.domain.util

import android.graphics.Bitmap
import android.util.Log
import com.tangoplus.tangoface_compose.domain.util.LandmarkUtility.convertDepthPixelTo3D
import com.tangoplus.tangoface_compose.domain.util.LandmarkUtility.findNearbyDepth
import com.tangoplus.tangoface_compose.domain.vision.face.FaceLandmarkResult
import com.tangoplus.tangoface_compose.orbbec.vo.Point3D
import com.tangoplus.tangoface_compose.orbbec.vo.ScaledIntrinsic
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

object HairLineUtility {
	data class HairlineResult(
		val hairlinePoints: List<HairlinePoint>,
		val hairlineCenterPoint: PointF,
		val hairlineY: Float,
		val confidence: Float,
		val skinColor: Scalar,
		val hairColor: Scalar,
		val searchRangeStart: Float,
		val searchRangeEnd: Float
	)
	data class HairlinePoint(
		val landmarkIndex: Int,    // 원본 랜드마크 인덱스 (54,103,67,109,10,338,297,332,284)
		val point: PointF,         // 헤어라인 위치
		val confidence: Float
	)
	data class PointF(val x: Float, val y: Float)
	private val HAIRLINE_LANDMARK_INDICES = listOf(103, 67, 109, 10, 338, 297, 332)
	/**
	 * 메인 함수: 헤어라인 찾기
	 */
	data class HairlinePoint3D(
		val landmarkIndex: Int,
		val point2D: PointF,      // 화면 좌표
		val point3D: Point3D,     // 실제 3D 좌표 (mm)
		val confidence: Float
	)
	fun convertHairlineTo3D(
		hairlinePoints: List<HairlinePoint>,
		depthData: ShortArray,
		depthWidth: Int,  // 1280
		depthHeight: Int, // 720
		intrinsic: ScaledIntrinsic // 640x360 기준 파라미터
	): List<HairlinePoint3D> {
		val result = mutableListOf<HairlinePoint3D>()

		// Orbbec SDK의 Intrinsic 원본 해상도
		val intrinsicWidth = 640f
		val intrinsicHeight = 360f

		for (point in hairlinePoints) {
			// 1. 원본 1920x1080(FHD) 좌표
			val fhdX = point.point.x
			val fhdY = point.point.y

			// 2. Depth Map(1280x720) 배열 인덱스 참조용 (Z값 추출용)
			val depthX = (fhdX * (depthWidth.toFloat() / 1920f)).toInt()
			val depthY = (fhdY * (depthHeight.toFloat() / 1080f)).toInt()

			// 3. 3D 투영을 위한 Intrinsic(640x360) 스케일링 좌표 (X, Y값 계산용)
			val calcX = fhdX * (intrinsicWidth / 1920f)
			val calcY = fhdY * (intrinsicHeight / 1080f)

			if (depthX in 0 until depthWidth && depthY in 0 until depthHeight) {
				val idx = depthY * depthWidth + depthX
				val rawDepth = depthData[idx].toInt() and 0xFFFF
				val depthM = rawDepth / 1000f

				val point3D = if (depthM > 0) {
					// ★ 핵심: fhdX 대신 calcX(640 기준)를 넣습니다.
					convertDepthPixelTo3D(intrinsic, calcX, calcY, depthM)
				} else {
					val nearbyDepth = findNearbyDepth(depthData, depthX, depthY, depthWidth, depthHeight)
					if (nearbyDepth > 0) {
						convertDepthPixelTo3D(intrinsic, calcX, calcY, nearbyDepth / 1000f)
					} else {
						Point3D(0f, 0f, 0f)
					}
				}
				result.add(HairlinePoint3D(point.landmarkIndex, point.point, point3D, point.confidence))
			} else {
				result.add(HairlinePoint3D(point.landmarkIndex, point.point, Point3D(0f, 0f, 0f), point.confidence))
			}
		}
		return result
	}
	fun findHairline(
		bitmap: Bitmap,
		faceLandmarks: FaceLandmarkResult,
		// ★ 튜닝 파라미터 - 조정 가능
		skinHairLabThreshold: Double = 25.0,  // 피부→머리 색변화 임계값 (낮을수록 민감)
		sustainLength: Int = 5                // 연속으로 threshold 넘어야 하는 픽셀 수
	): HairlineResult? {

		if (faceLandmarks.landmarks.size < 339) {
			Log.e("헤어라인E", "landmarks 부족: ${faceLandmarks.landmarks.size}")
			return null
		}

		val matRgb = Mat()
		Utils.bitmapToMat(bitmap, matRgb)
		Imgproc.cvtColor(matRgb, matRgb, Imgproc.COLOR_RGBA2RGB)

		val matLab = Mat()
		Imgproc.cvtColor(matRgb, matLab, Imgproc.COLOR_RGB2Lab)

		val matGray = Mat()
		Imgproc.cvtColor(matRgb, matGray, Imgproc.COLOR_RGB2GRAY)

		try {
			val point10 = faceLandmarks.landmarks[10]
			val point9  = faceLandmarks.landmarks[9]
			val foreheadHeight = abs(point10.y - point9.y)

			// ★ 수정: searchStart 항상 0, 머리카락 잘려도 탐색 가능
			val searchStart = 0
			val searchEnd   = (point10.y - foreheadHeight * 0.3f).toInt().coerceAtLeast(0)

			// ★ 이마 전체 피부색 샘플링 (눈썹 위 이마 영역)
			// 기존 5개 → 이마 전체 영역으로 확장
			val foreheadSampleIndices = listOf(9, 6, 1, 50, 280, 108, 337, 66, 296, 107, 336)
			val skinColorLab = getSkinColorLabBrightPreferred(
				matLab, faceLandmarks, foreheadSampleIndices, radius = 10
			) ?: return null

			Log.v("피부색Lab", "L=${skinColorLab.`val`[0]}, A=${skinColorLab.`val`[1]}, B=${skinColorLab.`val`[2]}")

			val hairlinePoints = mutableListOf<HairlinePoint>()

			for (landmarkIdx in HAIRLINE_LANDMARK_INDICES) {
				val lm = faceLandmarks.landmarks[landmarkIdx]
				val sampleX = lm.x.toInt().coerceIn(0, matRgb.cols() - 1)

				// ★ 단순화된 탐색: 위로 올라가면서 Lab 거리가 threshold 넘으면 머리카락
				val foundY = findHairColorTransition(
					matLab    = matLab,
					matGray = matGray,
					centerX   = sampleX,
					startY    = searchEnd,   // 랜드마크 바로 위부터
					endY      = searchStart, // 화면 최상단까지
					skinLab   = skinColorLab,
					colorThreshold = skinHairLabThreshold,
					textureThreshold = 15.0,
					sustainLen = sustainLength
				)

				if (foundY != null) {
					val hairLabArr = matLab.get(foundY, sampleX) ?: continue
					val hairColorLab = Scalar(hairLabArr[0], hairLabArr[1], hairLabArr[2])
					val conf = calculateConfidenceLab(skinColorLab, hairColorLab)

					Log.v("헤어라인탐색", "idx=$landmarkIdx x=$sampleX foundY=$foundY conf=$conf labDist=${
						labDistance(hairLabArr[0], hairLabArr[1], hairLabArr[2],
							skinColorLab.`val`[0], skinColorLab.`val`[1], skinColorLab.`val`[2])
					}")

					hairlinePoints.add(
						HairlinePoint(
							landmarkIndex = landmarkIdx,
							point         = PointF(sampleX.toFloat(), foundY.toFloat()),
							confidence    = conf
						)
					)
				} else {
					Log.w("헤어라인W", "랜드마크 $landmarkIdx 탐색 실패, landmark Y 사용")
					hairlinePoints.add(
						HairlinePoint(
							landmarkIndex = landmarkIdx,
							point         = PointF(lm.x, lm.y),
							confidence    = 0f
						)
					)
				}
			}

			val correctedPoints = correctHairlineWithCurvature(hairlinePoints, faceLandmarks)
			val centerPoint = correctedPoints.find { it.landmarkIndex == 10 }
				?: correctedPoints[correctedPoints.size / 2]

			val cx = centerPoint.point.x.toInt().coerceIn(0, matRgb.cols() - 1)
			val cy = centerPoint.point.y.toInt().coerceIn(0, matRgb.rows() - 1)
			val hairRgbArr = matRgb.get(cy, cx) ?: return null
			val hairColorRgb = Scalar(hairRgbArr[0], hairRgbArr[1], hairRgbArr[2])

			return HairlineResult(
				hairlinePoints      = correctedPoints,
				hairlineCenterPoint = centerPoint.point,
				hairlineY           = centerPoint.point.y,
				confidence          = centerPoint.confidence,
				skinColor           = skinColorLab,
				hairColor           = hairColorRgb,
				searchRangeStart    = searchStart.toFloat(),
				searchRangeEnd      = searchEnd.toFloat()
			)

		} finally {
			matRgb.release()
			matLab.release()
		}
	}

	// ★ 새로운 단순 탐색 함수
	private fun findHairColorTransition(
		matLab: Mat,
		matGray: Mat, // Imgproc.COLOR_RGB2GRAY로 미리 변환된 흑백 Mat 전달
		centerX: Int,
		startY: Int,
		endY: Int,
		skinLab: Scalar,
		colorThreshold: Double = 20.0,
		textureThreshold: Double = 15.0, // 질감 변화 임계값
		sustainLen: Int = 3
	): Int? {
		val height = startY - endY + 1
		if (height <= 0) return null

		// 1. JNI 호출 최소화: 필요한 열(Column) 데이터 한 번에 추출
		val colLab = Mat()
		val colGray = Mat()
		matLab.submat(endY, startY + 1, centerX, centerX + 1).copyTo(colLab)
		matGray.submat(endY, startY + 1, centerX, centerX + 1).copyTo(colGray)

		val labData = ByteArray(height * 3)
		val grayData = ByteArray(height)
		colLab.get(0, 0, labData)
		colGray.get(0, 0, grayData)
		colLab.release(); colGray.release()

		val skinL = skinLab.`val`[0]
		val skinA = skinLab.`val`[1]
		val skinB = skinLab.`val`[2]

		var sustainCount = 0
		var firstHairY = -1

		// 2. Y를 거꾸로(아래에서 위로) 탐색 (배열 인덱스는 역순)
		for (i in height - 1 downTo 1) {
			val y = endY + i

			// 색상 거리 계산
			val idx = i * 3
			val l = labData[idx].toInt() and 0xFF
			val a = labData[idx + 1].toInt() and 0xFF
			val b = labData[idx + 2].toInt() and 0xFF
			val colorDist = labDistance(l.toDouble(), a.toDouble(), b.toDouble(), skinL, skinA, skinB)

			// 텍스처 계산 (간단한 수직 1D Gradient - 머리카락 경계는 수직 변화율이 큼)
			val p1 = grayData[i].toInt() and 0xFF
			val p2 = grayData[i - 1].toInt() and 0xFF
			val textureGrad = abs(p1 - p2).toDouble()

			// 3. 색상이 임계치를 넘거나, 질감(경계선)이 확연히 나타날 때 카운트
			if (colorDist > colorThreshold || textureGrad > textureThreshold) {
				if (sustainCount == 0) firstHairY = y
				sustainCount++

				if (sustainCount >= sustainLen) {
					return firstHairY
				}
			} else {
				sustainCount = 0
				firstHairY = -1
			}
		}
		return null
	}

	private fun correctHairlineWithCurvature(
		hairlinePoints: List<HairlinePoint>,
		faceLandmarks: FaceLandmarkResult
	): List<HairlinePoint> {
		if (hairlinePoints.size < 3) return hairlinePoints

		// 랜드마크 상단선 곡률 계산 (103-67-109-10-338-297-332)
		val topLandmarkIndices = listOf(103, 67, 109, 10, 338, 297, 332)
		val topLandmarkYs = topLandmarkIndices.map { faceLandmarks.landmarks[it].y }

		// 랜드마크 기준 상대적 Y 오프셋 (곡률 패턴)
		val centerY = topLandmarkYs[3]  // 10번 랜드마크 (중앙)
		val curvatureOffsets = topLandmarkYs.map { it - centerY }

		// 헤어라인 중앙점 Y
		val hairlineCenterY = hairlinePoints.find { it.landmarkIndex == 10 }?.point?.y
			?: hairlinePoints[hairlinePoints.size / 2].point.y

		// 예상 헤어라인 Y (곡률 패턴 적용)
		val expectedYs = curvatureOffsets.map { offset -> hairlineCenterY + offset }

		// 각 포인트 보정
		val corrected = hairlinePoints.mapIndexed { i, point ->
			val expectedY = expectedYs[i]
			val diff = abs(point.point.y - expectedY)

			// 예상값과 너무 차이나면 (튄 경우) → 예상값으로 대체
			val threshold = 30f  // px 기준, 튜닝 필요
			if (diff > threshold) {
				Log.w("헤어라인보정", "랜드마크 ${point.landmarkIndex}: ${point.point.y} → $expectedY (diff=$diff)")
				HairlinePoint(
					landmarkIndex = point.landmarkIndex,
					point = PointF(point.point.x, expectedY),
					confidence = point.confidence * 0.5f  // 보정된 점은 confidence 낮춤
				)
			} else {
				point
			}
		}
		return corrected
	}
	private fun getSkinColorLabBrightPreferred(
		matLab: Mat,
		faceLandmarks: FaceLandmarkResult,
		indices: List<Int>,
		radius: Int = 4,
		brightPercent: Double = 0.50
	): Scalar? {
		val Ls = mutableListOf<Double>()
		val As = mutableListOf<Double>()
		val Bs = mutableListOf<Double>()

		for (idx in indices) {
			if (idx >= faceLandmarks.landmarks.size) continue
			val p = faceLandmarks.landmarks[idx]
			val x = p.x.toInt().coerceIn(0, matLab.cols() - 1)
			val y = p.y.toInt().coerceIn(0, matLab.rows() - 1)

			// ROI 픽셀들 수집
			for (dy in -radius..radius) for (dx in -radius..radius) {
				val yy = (y + dy).coerceIn(0, matLab.rows() - 1)
				val xx = (x + dx).coerceIn(0, matLab.cols() - 1)
				val v = matLab.get(yy, xx) ?: continue
				Ls.add(v[0]); As.add(v[1]); Bs.add(v[2])
			}
		}
		if (Ls.size < 20) return null

		// 밝은 상위 brightPercent만 선택
		val order = Ls.indices.sortedByDescending { Ls[it] }
		val keep = (Ls.size * brightPercent).toInt().coerceAtLeast(10)
		val pickIdx = order.take(keep)

		fun median(vals: List<Double>): Double {
			val s = vals.sorted()
			return s[s.size / 2]
		}

		val Lpick = pickIdx.map { Ls[it] }
		val Apick = pickIdx.map { As[it] }
		val Bpick = pickIdx.map { Bs[it] }

		return Scalar(
			median(Lpick),
			median(Apick),
			median(Bpick)
		)
	}

	private fun detectHairlineTransitionLab(
		matLab: Mat,
		matRgb: Mat,
		centerX: Int,
		startY: Int,
		endY: Int,
		skinLab: Scalar
	): Pair<Int, Double>? {

		if (startY >= endY) return null

		// window 파라미터
		val halfW = 6            // 13px
		val halfH = 1            // 3px
		val smoothK = 5          // y smoothing
		val sustainLen = 4       // high 상태 유지 길이 (기존 6보다 짧게)

		// 피부 밝기에 따른 히스테리시스 임계값(두 개)
		val skinL = skinLab.`val`[0]

		val lowTh = when {
			skinL < 90 -> 0.35
			skinL < 140 -> 0.38
			else -> 0.40
		}
		val highTh = when {
			skinL < 90 -> 0.58
			skinL < 140 -> 0.62
			else -> 0.66
		}

		// y별 hairScore
		val scores = ArrayList<Double>(endY - startY + 1)
		val ys = ArrayList<Int>(endY - startY + 1)

		for (y in endY downTo startY) {
			val s = hairScoreWindow(matLab, matRgb, centerX, y, skinLab, halfW, halfH)
			scores.add(s)
			ys.add(y)
		}
		if (scores.size < smoothK + 6) return null

		// smoothing
		val sm = DoubleArray(scores.size)
		for (i in scores.indices) {
			var sum = 0.0
			var cnt = 0
			val a = (i - smoothK / 2).coerceAtLeast(0)
			val b = (i + smoothK / 2).coerceAtMost(scores.size - 1)
			for (j in a..b) { sum += scores[j]; cnt++ }
			sm[i] = sum / cnt
		}

		// ✅ 1) 먼저 "피부 구간"을 확보: lowTh 이하가 일정 길이 존재해야 함
		// (정상 케이스에서 헤어라인을 아래로 잡는 것을 막는 핵심 장치)
		val skinRunNeed = 4
		var skinRun = 0
		var skinConfirmedIdx: Int? = null

		for (i in sm.indices) {
			if (sm[i] <= lowTh) {
				skinRun++
				if (skinRun >= skinRunNeed) {
					skinConfirmedIdx = i
					break
				}
			} else {
				skinRun = 0
			}
		}

		// 피부 구간이 없다 = 이마가 거의 안 보인다(가림 심함) 가능성
		// -> 기존 sustain 방식 fallback (가림 케이스 유지)
		if (skinConfirmedIdx == null) {
			return fallbackSustain(sm, ys, highTh, sustainLen)
		}
		// ✅ 2) 피부가 확인된 지점 이후부터 위로 올라가며 highTh 교차점 탐색
		var highRun = 0
		var crossIdx: Int? = null

		for (i in (skinConfirmedIdx + 1) until sm.size) {
			if (sm[i] >= highTh) {
				highRun++
				if (highRun >= sustainLen) {
					// high가 지속되기 시작한 첫 지점이 경계에 더 가깝다
					crossIdx = i - sustainLen + 1
					break
				}
			} else {
				highRun = 0
			}
		}

		if (crossIdx == null) {
			// 교차가 안 잡히면 fallback
			return fallbackSustain(sm, ys, highTh, sustainLen)
		}
		// ------------------------# 앞머리 유무, 피부 색, 헤어에 대해 판단하는 곳 #-------------------------
		val midTh = maxOf((lowTh + highTh) / 2.0, 0.7)
		val a = skinConfirmedIdx
		val b = crossIdx
		val boundaryTh = (lowTh + (highTh - lowTh) * 0.80).coerceAtMost(highTh - 0.01)
		var boundaryIdx: Int? = null
		for (i in b - 1 downTo a + 1) {
			if (sm[i] < boundaryTh && sm[i + 1] >= boundaryTh) {
				boundaryIdx = i + 1
				break
			}
		}
		if (boundaryIdx == null) {
			var idx = b
			while (idx > a && sm[idx] >= boundaryTh) idx--
			boundaryIdx = (idx + 1).coerceAtMost(b)
		}

		val finalIdx = boundaryIdx
		val yFound = ys[finalIdx]

		Log.v("메타데이타", "0: $lowTh, $highTh, $midTh")
		Log.v("메타데이타", "1: $skinConfirmedIdx, $crossIdx, $finalIdx")
		Log.v("메타데이타", "2: ${ys[skinConfirmedIdx]}, ${ys[crossIdx]}, ${ys[finalIdx]}")
		// confidence는 midTh 대비 정도로(너무 낮게 나오지 않게)
		val conf = ((sm[finalIdx] - boundaryTh) / (1.0 - boundaryTh)).coerceIn(0.0, 1.0)
		return Pair(yFound, conf)
	}

	/** 피부 구간이 없거나 교차 실패 시: 기존 sustain 방식 fallback */
	private fun fallbackSustain(
		sm: DoubleArray,
		ys: List<Int>,
		highTh: Double,
		sustainLen: Int
	): Pair<Int, Double>? {
		var run = 0
		for (i in sm.indices) {
			if (sm[i] >= highTh) {
				run++
				if (run >= sustainLen) {
					val idx = i - sustainLen + 1
					val yFound = ys[idx]
					val conf = ((sm[idx] - highTh) / (1.0 - highTh)).coerceIn(0.0, 1.0)
					return Pair(yFound, conf)
				}
			} else run = 0
		}
		return null
	}

	private fun hairScoreWindow(
		matLab: Mat,
		matRgb: Mat,
		x: Int,
		y: Int,
		skinLab: Scalar,
		halfW: Int,
		halfH: Int
	): Double {

		val x0 = (x - halfW).coerceIn(0, matLab.cols() - 1)
		val x1 = (x + halfW).coerceIn(0, matLab.cols() - 1)
		val y0 = (y - halfH).coerceIn(0, matLab.rows() - 1)
		val y1 = (y + halfH).coerceIn(0, matLab.rows() - 1)

		var distSum = 0.0
		var distCnt = 0

		val skinL = skinLab.`val`[0]
		val skinA = skinLab.`val`[1]
		val skinB = skinLab.`val`[2]
		for (yy in y0..y1) {
			for (xx in x0..x1) {
				val p = matLab.get(yy, xx) ?: continue
				val d = labDistance(p[0], p[1], p[2], skinL, skinA, skinB)
				distSum += d
				distCnt++
			}
		}
		if (distCnt == 0) return 0.0

		val distAvg = distSum / distCnt

		// texture는 해당 y에서의 ROI를 한 번만 계산하는게 비용 좋지만,
		// 일단 단순 적용: 작은 ROI의 Laplacian variance
		val tex = calcTextureScoreLaplacianVar(matRgb, x, y)

		// dist를 0~1로 정규화 (현장에 맞춰 스케일 조정)
		// distAvg: 피부와 많이 다르면 hair (예: 15~45 사이가 자주 나옴)
		val dist01 = ((distAvg - 12.0) / 30.0).coerceIn(0.0, 1.0)
		val tex01  = ((tex - 20.0) / 60.0).coerceIn(0.0, 1.0)
		val wDist = if (skinL > 140) 0.55 else 0.40
		val wTex  = 1.0 - wDist

		val score = dist01 * wDist + tex01 * wTex

		return score
	}

	private fun labDistance(L: Double, A: Double, B: Double, l2: Double, a2: Double, b2: Double): Double {
		val dL = L - l2
		val dA = A - a2
		val dB = B - b2
		return kotlin.math.sqrt(dL * dL + dA * dA + dB * dB)
	}
	private fun calcTextureScoreLaplacianVar(matRgb: Mat, x: Int, y0: Int): Double {
		val w = 14
		val h = 18

		val x0 = (x - w / 2).coerceIn(0, matRgb.cols() - 1)
		val y1 = (y0 - h).coerceIn(0, matRgb.rows() - 1)
		val x1 = (x0 + w).coerceIn(0, matRgb.cols())
		val y2 = (y0 - 2).coerceIn(0, matRgb.rows())

		if (x1 <= x0 || y2 <= y1) return 0.0

		val roi = matRgb.submat(y1, y2, x0, x1)
		val gray = Mat()
		Imgproc.cvtColor(roi, gray, Imgproc.COLOR_RGB2GRAY)

		val lap = Mat()
		Imgproc.Laplacian(gray, lap, CvType.CV_64F)

		val mean = MatOfDouble()
		val std = MatOfDouble()
		Core.meanStdDev(lap, mean, std)

		val variance = std.toArray()[0] * std.toArray()[0]

		roi.release()
		gray.release()
		lap.release()
		mean.release()
		std.release()

		return variance
	}

	private fun calculateConfidenceLab(skinLab: Scalar, hairLab: Scalar): Float {
		val d = labDistance(
			hairLab.`val`[0], hairLab.`val`[1], hairLab.`val`[2],
			skinLab.`val`[0], skinLab.`val`[1], skinLab.`val`[2]
		)

		// Lab 거리 기준으로 0~1 스케일링 (현장 데이터로 스케일은 조정 가능)
		return (d / 60.0).coerceIn(0.0, 1.0).toFloat()
	}
}
