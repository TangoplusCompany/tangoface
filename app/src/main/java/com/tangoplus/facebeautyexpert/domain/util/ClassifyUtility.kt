package com.tangoplus.facebeautyexpert.domain.util

import android.util.Log
import com.tangoplus.facebeautyexpert.data.db.FaceShape
import com.tangoplus.facebeautyexpert.data.db.FacialAxisShape
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.calculateRatios
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkResult
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

object ClassifyUtility {
	fun getIntFromFaceType(faceType: FaceShape?): Int {
		return when (faceType) {
			FaceShape.EGG -> 0
			FaceShape.RHOMBUS -> 1
			FaceShape.HEART -> 2
			FaceShape.PEANUT -> 3
			FaceShape.HEXAGON -> 4
			FaceShape.CIRCLE -> 5
			FaceShape.DEFAULT -> -1
			null -> -1
		}
	}

	fun getFaceTypeFromInt(faceIndex: Int?): FaceShape? {
		return when (faceIndex) {
			0 -> FaceShape.EGG
			1 -> FaceShape.RHOMBUS
			2 -> FaceShape.HEART
			3 -> FaceShape.PEANUT
			4 -> FaceShape.HEXAGON
			5 -> FaceShape.CIRCLE
			else -> null
		}
	}
	data class FaceShapeResult(
		val shape: FaceShape,
		val ratios: Map<String, Float>  // 디버깅용 비율값
	)

	fun classifyFaceShape(
		faceLandmarks: MutableList<Pair<Float, Float>>,
		hairlinePoints: List<Triple<Float, Float, Float>>
	): FaceShapeResult {

		val lm = faceLandmarks

		// 핵심 width 측정
		val cheekWidth    = abs(lm[454].first - lm[234].first)   // 광대 width (가장 넓은 부분)
		val foreheadWidth = abs(lm[332].first - lm[103].first)   // 이마 width
		val jawWidth      = abs(lm[397].first - lm[172].first)   // 턱 width
		val midFaceWidth  = abs(lm[361].first - lm[132].first)   // 볼 중간 width (광대 아래)

		// 헤어라인 Y
		val hairlineY = hairlinePoints.getOrNull(3)?.second ?: lm[10].second
		val chinY     = lm[152].second
		val faceHeight = abs(chinY - hairlineY)

		// 비율들
		val cheekToForehead = cheekWidth / foreheadWidth   // 광대/이마 (>1 = 광대가 넓음)
		val cheekToJaw      = cheekWidth / jawWidth        // 광대/턱  (>1 = 광대가 넓음)
		val foreheadToJaw   = foreheadWidth / jawWidth     // 이마/턱  (>1 = 이마가 넓음)
		val widthToHeight   = cheekWidth / faceHeight      // 가로/세로 비율

		// 볼살 굴곡: 볼 중간이 광대보다 얼마나 좁은지
		// 값이 클수록 볼이 패임 (땅콩형)
		// 값이 작을수록 볼살이 많음 (둥근형)
		val cheekHollowRatio = midFaceWidth / cheekWidth   // 1에 가까울수록 볼살 많음, 작을수록 패임

		val ratios = mapOf(
			"cheekToForehead" to cheekToForehead,
			"cheekToJaw"      to cheekToJaw,
			"foreheadToJaw"   to foreheadToJaw,
			"widthToHeight"   to widthToHeight,
			"cheekHollow"     to cheekHollowRatio
		)

		Log.v("얼굴형비율", """
        광대/이마: ${"%.3f".format(cheekToForehead)}
        광대/턱:   ${"%.3f".format(cheekToJaw)}
        이마/턱:   ${"%.3f".format(foreheadToJaw)}
        가로/세로: ${"%.3f".format(widthToHeight)}
        볼굴곡:    ${"%.3f".format(cheekHollowRatio)}
    """.trimIndent())

		val shape = when {

			// 마름모형: 광대 큼, 이마 좁음, 턱 좁음
			// cheekToForehead 크고, cheekToJaw 크고, foreheadToJaw ≈ 1
			cheekToForehead > 1.15f
				&& cheekToJaw > 1.25f
				&& foreheadToJaw in 0.85f..1.15f
				&& cheekHollowRatio > 0.85f -> FaceShape.RHOMBUS

			// 땅콩형: 광대 큼, 턱 큼, 볼이 패임
			// cheekToForehead 크고, cheekToJaw 작음 (턱이 광대만큼 넓음), 볼 패임
			cheekToForehead > 1.1f
				&& cheekToJaw < 1.15f
				&& cheekHollowRatio < 0.82f -> FaceShape.PEANUT

			// 하트형: 광대 큼, 이마 넓음, 턱 중간
			// cheekToForehead 작고(이마도 넓음), cheekToJaw 중간
			cheekToForehead in 0.95f..1.15f
				&& cheekToJaw in 1.15f..1.35f
				&& foreheadToJaw > 1.1f -> FaceShape.HEART

			// 육각형: 광대 작음, 턱 큼
			// cheekToJaw 작음 (턱이 광대만큼 넓거나 더 넓음)
			cheekToJaw < 1.05f
				&& cheekToForehead < 1.1f
				&& foreheadToJaw < 0.95f -> FaceShape.HEXAGON

			// 둥근형: 광대 작음, 턱 작음, 볼살 많음
			// 가로/세로 비율 크고, 볼 굴곡 작음 (볼살 많음)
			widthToHeight > 0.80f
				&& cheekToJaw < 1.2f
				&& cheekToForehead < 1.15f
				&& cheekHollowRatio > 0.88f -> FaceShape.CIRCLE

			// 계란형: 광대 작음, 이마>턱, 아래로 갈수록 좁아짐
			foreheadToJaw > 1.1f
				&& cheekToForehead in 0.9f..1.15f
				&& cheekToJaw in 1.1f..1.35f
				&& widthToHeight < 0.80f -> FaceShape.EGG

			// fallback
			else -> FaceShape.EGG
		}

		Log.v("얼굴형결과", shape.displayName)
		return FaceShapeResult(shape, ratios)
	}

	data class FacialAxisResult(
		val curve: FacialAxisShape,
		val upperAngle: Float,  // 미간→코끝 각도 (양수=오른쪽, 음수=왼쪽)
		val lowerAngle: Float   // 코끝→턱끝 각도
	)

	fun classifyFacialAxis(faceLandmarks: MutableList<Pair<Float, Float>>): FacialAxisResult {
		val lm = faceLandmarks

		val glabella = lm[168]  // 미간
		val noseTip  = lm[4]    // 코끝
		val chin     = lm[152]  // 턱끝

		// 미간→코끝 각도 (수직 기준)
		val upperDx = noseTip.first - glabella.first
		val upperDy = noseTip.second - glabella.second
		val upperAngle = Math.toDegrees(atan2(upperDx.toDouble(), upperDy.toDouble())).toFloat()

		// 코끝→턱끝 각도 (수직 기준)
		val lowerDx = chin.first - noseTip.first
		val lowerDy = chin.second - noseTip.second
		val lowerAngle = Math.toDegrees(atan2(lowerDx.toDouble(), lowerDy.toDouble())).toFloat()

		val threshold = 1.5f  // 1.5도 이상이면 틀어진 것으로 판단

		val upperTilted = abs(upperAngle) > threshold
		val lowerTilted = abs(lowerAngle) > threshold

		val curve = when {
			// 정상: 둘 다 1.5도 이내
			!upperTilted && !lowerTilted -> FacialAxisShape.NORMAL

			// C커브: 위는 정상, 아래만 틀어짐
			!upperTilted && lowerTilted -> FacialAxisShape.C_ASYMMETRY

			// S커브: 둘 다 틀어지고 방향이 반대
			upperTilted && lowerTilted
				&& (upperAngle * lowerAngle < 0) -> FacialAxisShape.S_ASYMMETRY

			// 중심축 이탈: 둘 다 틀어지고 방향이 같음
			upperTilted && lowerTilted
				&& (upperAngle * lowerAngle > 0) -> FacialAxisShape.OFF_CENTER_AXIS

			// 위만 틀어짐 (C커브 반대)
			else -> FacialAxisShape.C_ASYMMETRY
		}

		Log.v("얼굴축", "upper: $upperAngle°, lower: $lowerAngle°, 결과: ${curve.displayName}")

		return FacialAxisResult(curve, upperAngle, lowerAngle)
	}

	fun classifySaggingDepth(
		faceLandmarks: MutableList<Pair<Float, Float>>
	): Pair<Float, Float> {
		val lm = faceLandmarks

		val leftJaw  = lm[136]
		val rightJaw = lm[365]
		val chin     = lm[152]

		// 왼쪽 기준선: leftJaw → chin
		val leftLineX = chin.first  - leftJaw.first
		val leftLineY = chin.second - leftJaw.second
		val leftLineLen = hypot(leftLineX.toDouble(), leftLineY.toDouble()).toFloat()

		// 오른쪽 기준선: rightJaw → chin
		val rightLineX = chin.first  - rightJaw.first
		val rightLineY = chin.second - rightJaw.second
		val rightLineLen = hypot(rightLineX.toDouble(), rightLineY.toDouble()).toFloat()

		if (leftLineLen == 0f || rightLineLen == 0f) return Pair(0f, 0f)

		fun distanceFromLine(
			px: Float, py: Float,
			x0: Float, y0: Float,
			lineX: Float, lineY: Float,
			lineLen: Float
		): Float {
			val cross = (px - x0) * lineY - (py - y0) * lineX
			return cross / lineLen
		}

		// 턱선 라인만 (132, 58 제거)
		val leftCheekIndices  = listOf(136, 150, 149, 176, 148)
		val rightCheekIndices = listOf(365, 379, 378, 400, 377)

		// 왼쪽은 부호 반전 (왼쪽 기준선은 처짐이 음수로 나옴)
		val leftMaxD = leftCheekIndices
			.map { idx ->
				-distanceFromLine(
					lm[idx].first, lm[idx].second,
					leftJaw.first, leftJaw.second,
					leftLineX, leftLineY, leftLineLen
				)
			}
			.maxOrNull() ?: 0f

		// 오른쪽은 양수 그대로
		val rightMaxD = rightCheekIndices
			.map { idx ->
				distanceFromLine(
					lm[idx].first, lm[idx].second,
					rightJaw.first, rightJaw.second,
					rightLineX, rightLineY, rightLineLen
				)
			}
			.maxOrNull() ?: 0f

		val leftCI  = ((leftMaxD  / leftLineLen)  * 100f).coerceAtLeast(0f)
		val rightCI = ((rightMaxD / rightLineLen) * 100f).coerceAtLeast(0f)

		Log.v("처짐CI", "왼쪽 d: ${"%.4f".format(leftMaxD)} L: ${"%.4f".format(leftLineLen)} CI: ${"%.2f".format(leftCI)}")
		Log.v("처짐CI", "오른쪽 d: ${"%.4f".format(rightMaxD)} L: ${"%.4f".format(rightLineLen)} CI: ${"%.2f".format(rightCI)}")

		return Pair(leftCI, rightCI)
	}

	fun classifyFacialAsymmetry(
		faceLandmarks: MutableList<Pair<Float, Float>>
	): Pair<Float, Float> {  // (길이 비대칭 지수, 각도 비대칭 지수)

		// 왼쪽 눈끝(바깥): 33, 입끝: 61
		// 오른쪽 눈끝(바깥): 263, 입끝: 291
		val leftEyeCorner   = faceLandmarks[33]
		val leftMouthCorner = faceLandmarks[61]
		val rightEyeCorner  = faceLandmarks[263]
		val rightMouthCorner = faceLandmarks[291]

		// 정규화용 얼굴 width (광대 기준 234↔454)
		val faceWidth = abs(faceLandmarks[454].first - faceLandmarks[234].first)
		if (faceWidth == 0f) return Pair(0f, 0f)

		// 1. 왼쪽/오른쪽 눈끝-입끝 길이
		val leftLength = hypot(
			(leftMouthCorner.first  - leftEyeCorner.first).toDouble(),
			(leftMouthCorner.second - leftEyeCorner.second).toDouble()
		).toFloat()

		val rightLength = hypot(
			(rightMouthCorner.first  - rightEyeCorner.first).toDouble(),
			(rightMouthCorner.second - rightEyeCorner.second).toDouble()
		).toFloat()

		// 2. 왼쪽/오른쪽 기울기 (도)
		val leftAngle = Math.toDegrees(
			atan2(
				(leftMouthCorner.second - leftEyeCorner.second).toDouble(),
				(leftMouthCorner.first  - leftEyeCorner.first).toDouble()
			)
		).toFloat()

		val rightAngle = Math.toDegrees(
			atan2(
				(rightMouthCorner.second - rightEyeCorner.second).toDouble(),
				(rightMouthCorner.first  - rightEyeCorner.first).toDouble()
			)
		).toFloat()

		// 3. 길이 비대칭 지수 (얼굴 width로 정규화)
		val lengthAsymmetry = abs(leftLength - rightLength) / faceWidth

		// 4. 각도 비대칭 지수 (0~1로 정규화, 180도 기준)
		val angleAsymmetry = abs(leftAngle - abs(rightAngle)) / 180f

//		Log.v("안면비대칭", """
//        왼쪽 길이: $leftLength, 오른쪽 길이: $rightLength
//        왼쪽 각도: ${"%.2f".format(leftAngle)}°, 오른쪽 각도: ${"%.2f".format(rightAngle)}°
//        길이 비대칭: ${"%.4f".format(lengthAsymmetry)}
//        각도 비대칭: ${"%.4f".format(angleAsymmetry)}
//    """.trimIndent())

		return Pair(lengthAsymmetry, angleAsymmetry)
	}

	// 가로 비율 (상:중:하 총합 3)
	fun calculateHorizontalRatio(
		faceLandmarks: MutableList<Pair<Float, Float>>,
		hairLine3DCoordinates: MutableList<Triple<Float, Float, Float>>
	): String {
		val hairlineY = hairLine3DCoordinates[3].second
		val eyesY = (faceLandmarks[55].second + faceLandmarks[285].second) / 2f
		val noseY = faceLandmarks[2].second
		val chinY = faceLandmarks[152].second

		val upper = eyesY - hairlineY
		val mid = noseY - eyesY
		val lower = chinY - noseY

		val total = upper + mid + lower
		if (total == 0f) return "0:0:0"

		// 전체 중 차지하는 비율 * 3
		val rUpper = (upper / total) * 3f
		val rMid = (mid / total) * 3f
		val rLower = (lower / total) * 3f

		return "%.2f:%.2f:%.2f".format(rUpper, rMid, rLower)
	}

	// 세로 비율 (5구간 총합 5)
	fun calculateVerticalRatio(
		faceLandmarks: MutableList<Pair<Float, Float>>
	): String {
		val leftEarX = faceLandmarks[234].first
		val leftEyeOutX = faceLandmarks[33].first
		val leftEyeInX = faceLandmarks[133].first
		val rightEyeInX = faceLandmarks[362].first
		val rightEyeOutX = faceLandmarks[263].first
		val rightEarX = faceLandmarks[454].first

		val m1 = leftEyeOutX - leftEarX
		val m2 = leftEyeInX - leftEyeOutX
		val m3 = rightEyeInX - leftEyeInX
		val m4 = rightEyeOutX - rightEyeInX
		val m5 = rightEarX - rightEyeOutX

		val total = m1 + m2 + m3 + m4 + m5
		if (total == 0f) return "0:0:0:0:0"

		// 전체 중 차지하는 비율 * 5
		val r1 = (m1 / total) * 5f
		val r2 = (m2 / total) * 5f
		val r3 = (m3 / total) * 5f
		val r4 = (m4 / total) * 5f
		val r5 = (m5 / total) * 5f

		return "%.2f:%.2f:%.2f:%.2f:%.2f".format(r1, r2, r3, r4, r5)
	}


}