package com.tangoplus.facebeautyexpert.domain.util

import android.graphics.PointF
import java.lang.Math.toDegrees
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

object MathHelpers {
	private var SCALE_FACTOR = 1f

	fun setScaleFactor(scale: Float) {
		SCALE_FACTOR = scale
	}

	fun getScaleFactor(): String {
		return "%.1f".format(SCALE_FACTOR)
	}

	// ------# 기울기 계산 #------
	fun calculateSlope(x1: Float, y1: Float, x2: Float, y2: Float): Float {
		val radians = atan2(y1 - y2, x1 - x2)
		val degrees = toDegrees(radians.toDouble()).toFloat()
		val result = if (degrees > 180) degrees % 180 else degrees
		return "%.4f".format(result).toFloat()
	}

	// ------# 점 3개의 각도 계산 #------
	fun calculateAngle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float): Float {
		val v1x = x1 - x2
		val v1y = y1 - y2
		val v2x = x3 - x2
		val v2y = y3 - y2
		val dotProduct = v1x * v2x + v1y * v2y
		val magnitude1 = sqrt(v1x * v1x + v1y * v1y)
		val magnitude2 = sqrt(v2x * v2x + v2y * v2y)

		val cosTheta = dotProduct / (magnitude1 * magnitude2)
		val angleRadians = acos(cosTheta)
		return toDegrees(angleRadians.toDouble()).toFloat()
	}

	// 얼굴형 계산 목적/input이 Pair
	fun calculateAngle(
		p1: Pair<Float, Float>,
		p2: Pair<Float, Float>, // 꼭짓점
		p3: Pair<Float, Float>
	): Float {
		val v1x = p1.first - p2.first
		val v1y = p1.second - p2.second
		val v2x = p3.first - p2.first
		val v2y = p3.second - p2.second

		val dot = v1x * v2x + v1y * v2y
		val cross = v1x * v2y - v1y * v2x
		val angle = Math.toDegrees(atan2(cross.toDouble(), dot.toDouble())).toFloat()
		return abs(angle)
	}
	fun calculatePolygonArea(points: List<Pair<Float, Float>>): Float {
		if (points.size < 3) return 0.0f  // 다각형이 안 됨

		var sum = 0.0f
		val n = points.size

		for (i in 0 until n) {
			val (x1, y1) = points[i]
			val (x2, y2) = points[(i + 1) % n]  // 마지막 점은 첫 번째와 연결
			sum += (x1 * y2) - (x2 * y1)
		}
		return (abs(sum) / 2.0f) / 1000f
	}

	// ------# 보정 값 계산 #-------
	fun correctingValue(OGValue: Float, correctionValue: Float): Float {
		return (OGValue + correctionValue + 360f) % 360f
	}

	// ------! 선과 점의 X 각도 !------
//    fun calculateAngleBySlope(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float): Float {
//        val x4 = (x1 + x2) / 2
//        val y4 = (y1 + y2) / 2
//        // 벡터1: (x1, y1) -> (x4, y4)
//        val dx1 = x4 - x1
//        val dy1 = y4 - y1
//        // 벡터2: (x4, y4) -> (x3, y3)
//        val dx2 = x3 - x4
//        val dy2 = y3 - y4
//        // 내적 계산
//        val dotProduct = dx1 * dx2 + dy1 * dy2
//        // 벡터 크기 계산
//        val magnitude1 = sqrt(dx1.pow(2) + dy1.pow(2))
//        val magnitude2 = sqrt(dx2.pow(2) + dy2.pow(2))
//        // 코사인 값 계산
//        val cosTheta = dotProduct / (magnitude1 * magnitude2)
//        // 라디안 -> 도 변환
//        val angleRadians = acos(cosTheta)
//        return toDegrees(angleRadians.toDouble()).toFloat()
//    }



	fun normalizeAngle90(angle: Float): Float {
		val absAngle = abs(angle % 180)
		return if (absAngle > 90) 180 - absAngle else absAngle
	}

	// 두 점의 거리 계산
	fun getNormalizedDistance(
		point1: Pair<Float, Float>,
		point2: Pair<Float, Float>,
		isScaled: Boolean = false
	): Float {
		val deltaX = point2.first - point1.first
		val deltaY = point2.second - point1.second
		return sqrt(deltaX.pow(2) + deltaY.pow(2)) * if (!isScaled) SCALE_FACTOR else 1f
	}

	fun calculateScaleFromPart(relativeDistance: Float, realPupilLength: Float = 6.5f): Float {
		if (relativeDistance <= 0f) return 0f // 잘못된 값 방어
		// 6.5cm 눈 사이 거리
		return realPupilLength / relativeDistance
	}

	fun calculateRatios(
		points: List<Pair<Float, Float>>,
		isVertical: Boolean = true
	): List<Float> {
		if (points.size < 2) return emptyList()

		// 1. 각 구간의 거리 계산
		val distances = mutableListOf<Float>()
		for (i in 0 until points.size - 1) {
			val distance = if (!isVertical) {
				abs(points[i + 1].second - points[i].second) // Y 좌표 차이
			} else {
				abs(points[i + 1].first - points[i].first)   // X 좌표 차이
			}
			distances.add(distance)
		}

		// 2. 가장 작은 거리를 1로 기준 설정
		val minDistance = distances.minOrNull() ?: 1f
		if (minDistance == 0f) return distances.map { 1f } // 모든 점이 같은 위치면 1로 반환

		// 3. 비율 계산 (가장 작은 값을 1로)
		return distances.map { it / minDistance }
	}

	fun getRealWorldDistance(
		point1: PointF,
		point2: PointF,
		scaleFactor: Float,
		screenWidth: Int = 1280,
		screenHeight: Int = 720,
	): Float {
		val relX1 = revertScreenX(point1.x, screenWidth)
		val relY1 = revertScreenY(point1.y, screenHeight)
		val relX2 = revertScreenX(point2.x, screenWidth)
		val relY2 = revertScreenY(point2.y, screenHeight)

		val dx = relX2 - relX1
		val dy = relY2 - relY1

		val pixelDistance = sqrt(dx.pow(2) + dy.pow(2))
		return pixelDistance * scaleFactor
	}

	fun revertScreenX(x: Float, screenWidth: Int): Float {
		val scaleFactor = screenWidth / 1280f
		val offsetX = (screenWidth - 1280 * scaleFactor) / 2
		return (x - offsetX) * 1280f / screenWidth
	}

	fun revertScreenY(y: Float, screenHeight: Int): Float {
		val scaleFactor = screenHeight / 720f
		val offsetY = (screenHeight - 720 * scaleFactor) / 2
		return (y - offsetY) * 720f / screenHeight
	}


	// 3d 좌표
	fun get3DDistance(p1: Triple<Float, Float, Float>, p2: Triple<Float, Float, Float>): Float {
		val dx = p2.first - p1.first
		val dy = p2.second - p1.second
		val dz = p2.third - p1.third
		return (sqrt(dx.pow(2) + dy.pow(2) + dz.pow(2))) * 100
	}
	fun get3DFloorDistance(p1: Triple<Float, Float, Float>, p2: Triple<Float, Float, Float>): Float {
		val dx = p2.first - p1.first
		val dy = p2.second - p1.second
		val distance = sqrt(dx.pow(2) + dy.pow(2))
		return distance * 100
	}
	fun calculate3DAngle(
		p1: Triple<Float, Float, Float>,
		p2: Triple<Float, Float, Float>,
		p3: Triple<Float, Float, Float>
	): Float {
		val v1 = Triple(p1.first - p2.first, p1.second - p2.second, p1.third - p2.third)
		val v2 = Triple(p3.first - p2.first, p3.second - p2.second, p3.third - p2.third)

		val dot = v1.first * v2.first + v1.second * v2.second + v1.third * v2.third
		val mag1 = sqrt(v1.first.pow(2) + v1.second.pow(2) + v1.third.pow(2))
		val mag2 = sqrt(v2.first.pow(2) + v2.second.pow(2) + v2.third.pow(2))

		val cosTheta = (dot / (mag1 * mag2)).coerceIn(-1f, 1f)
		return toDegrees(acos(cosTheta).toDouble()).toFloat()
	}

	// ===== Main: area with robust outlier removal =====
	fun calculate3DPolygonArea(points: List<Triple<Float, Float, Float>>): Float {
		if (points.size < 3) return 0f

		var totalArea = 0f
		val center = Triple(
			points.map { it.first }.average().toFloat(),
			points.map { it.second }.average().toFloat(),
			points.map { it.third }.average().toFloat()
		)

		// 중심점에서 각 변으로 삼각형을 만들어 면적 합산
		for (i in points.indices) {
			val p1 = points[i]
			val p2 = points[(i + 1) % points.size]
			totalArea += triangleArea3D(center, p1, p2)
		}

		return totalArea
	}

	fun triangleArea3D(
		p1: Triple<Float, Float, Float>,
		p2: Triple<Float, Float, Float>,
		p3: Triple<Float, Float, Float>
	): Float {
		// 벡터 외적(cross product)을 이용한 삼각형 넓이
		val v1x = p2.first - p1.first
		val v1y = p2.second - p1.second
		val v1z = p2.third - p1.third

		val v2x = p3.first - p1.first
		val v2y = p3.second - p1.second
		val v2z = p3.third - p1.third

		// 외적 계산
		val crossX = v1y * v2z - v1z * v2y
		val crossY = v1z * v2x - v1x * v2z
		val crossZ = v1x * v2y - v1y * v2x

		// 외적의 크기 / 2 = 삼각형 넓이
		val magnitude = sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ)
		return magnitude / 2f
	}

	fun calculate3DRatios(points: List<Triple<Float, Float, Float>>): List<Float> {
		if (points.size < 2) return emptyList()
		val distances = mutableListOf<Float>()
		for (i in 0 until points.size - 1) {
			distances.add(get3DDistance(points[i], points[i + 1]))
		}
		val minDistance = distances.minOrNull() ?: 1f
		return distances.map { if (minDistance == 0f) 1f else it / minDistance }
	}

	private fun dist2DPx(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
		val dx = a.first - b.first
		val dy = a.second - b.second
		return sqrt(dx * dx + dy * dy)
	}


	private fun projFactorXY(
		p1: Triple<Float, Float, Float>,
		p2: Triple<Float, Float, Float>
	): Float {
		val vx = p2.first - p1.first
		val vy = p2.second - p1.second
		val vz = p2.third - p1.third
		val num = sqrt(vx * vx + vy * vy)
		val den = sqrt(vx * vx + vy * vy + vz * vz)
		return if (den > 0f) (num / den) else 1f
	}

	private fun medianOf(list: MutableList<Float>): Float {
		list.sort()
		return list[list.size / 2]
	}

	fun calculateAreaWithScaling(
		polygonIndices: List<Int>,
		points2Dpx: List<Pair<Float, Float>>,
		points3D: List<Triple<Float, Float, Float>>,
		correctForeshortening: Boolean = true,
		areaUnitK: Float = 1000f
	): Float {
		if (polygonIndices.size < 3) return 0f
		if (points2Dpx.isEmpty() || points3D.isEmpty()) return 0f

		// 1️⃣ polygonIndices 기반으로 polygon2D 구성
		val polygon2D = polygonIndices.mapNotNull { idx ->
			if (idx in points2Dpx.indices) points2Dpx[idx] else null
		}
		if (polygon2D.size < 3) return 0f

		// 2️⃣ 기존 2D 면적 계산 (현재 px²/1000 반환)
		val areaPx2_div1000 = calculatePolygonArea(polygon2D)
		if (areaPx2_div1000 <= 0f) return 0f

		// 2) 당신이 제공한 기준선 인덱스들 (cm/px 산출용)
		val baselines = listOf(
			55 to 286,  // eyeBrowDistance
			48 to 278,  // noseDistance
			33 to 133,  // leftEyeDistance
			263 to 362  // rightEyeDistance
		)
		val ratios = ArrayList<Float>(baselines.size) // 각 기준선의 cm/px
		for ((i, j) in baselines) {
			if (i !in points2Dpx.indices || j !in points2Dpx.indices) continue
			if (i !in points3D.indices || j !in points3D.indices) continue

			var d2Dpx = dist2DPx(points2Dpx[i], points2Dpx[j]) // px
			if (d2Dpx <= 0f) continue

			if (correctForeshortening) {
				val pf = projFactorXY(points3D[i], points3D[j]).coerceIn(0.3f, 1f)
				d2Dpx /= pf // foreshortening 보정
			}

			val d3Dcm = get3DDistance(points3D[i], points3D[j]) // cm (당신 함수)
			if (d3Dcm <= 0f) continue

			ratios.add(d3Dcm / d2Dpx) // cm/px
		}
		if (ratios.isEmpty()) return 0f

		// 3) 다중 기준선의 중앙값(cm/px) 사용 → 튐에 강함
		val cmPerPx = medianOf(ratios)

		// 4) 면적 스케일링: (cm/px)^2 * (px²)
		//    areaPx2_div1000 * areaUnitK  ==  실제 px²(당신 함수 보정)
		val areaPx2 = areaPx2_div1000 * areaUnitK
		return areaPx2 * (cmPerPx * cmPerPx) // cm²
	}
}