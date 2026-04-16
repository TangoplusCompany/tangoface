package com.tangoplus.facebeautyexpert.domain.util

import android.util.Log
import com.tangoplus.facebeautyexpert.data.db.FaceShape
import com.tangoplus.facebeautyexpert.data.db.FacialAxisShape
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.calculateAngle
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
		val primaryShape: FaceShape,      // 1순위 얼굴형
		val secondaryShape: FaceShape?,   // 2순위 얼굴형 (단독일 경우 null)
		val ratios: Map<String, Float>    // 분석 수치들
	)

	fun classifyFaceShape(
		faceLandmarks: MutableList<Pair<Float, Float>>,
		hairlinePoints: List<Triple<Float, Float, Float>>
	): FaceShapeResult {

		val lm = faceLandmarks

		// --- 1. 데이터 추출 ---
		val cheekWidth    = abs(lm[454].first - lm[234].first)
		val foreheadWidth = abs(lm[332].first - lm[103].first)
		val jawWidth      = abs(lm[397].first - lm[172].first)
		val midFaceWidth  = abs(lm[361].first - lm[132].first)

		val hairlineY = hairlinePoints.getOrNull(3)?.second ?: lm[10].second
		val chinY     = lm[152].second
		val faceHeight = abs(chinY - hairlineY)

		val rightEyeOuter = lm[33].first
		val leftEyeOuter = lm[263].first
		val eyeToEyeWidth = abs(leftEyeOuter - rightEyeOuter)
		val sideFleshWidth = cheekWidth - eyeToEyeWidth
		val sideFleshRatio = sideFleshWidth / cheekWidth

		val chinAngle = calculateAngle(lm[132], lm[152], lm[361])

		val cheekToForehead = cheekWidth / foreheadWidth
		val cheekToJaw      = cheekWidth / jawWidth
		val foreheadToJaw   = foreheadWidth / jawWidth
		val widthToHeight   = cheekWidth / faceHeight
		val cheekHollowRatio = midFaceWidth / cheekWidth

		val ratios = mapOf(
			"cheekToForehead" to cheekToForehead,
			"cheekToJaw"      to cheekToJaw,
			"foreheadToJaw"   to foreheadToJaw,
			"widthToHeight"   to widthToHeight,
			"cheekHollow"     to cheekHollowRatio,
			"sideFleshRatio"  to sideFleshRatio,
			"chinAngle"       to chinAngle
		)

		// --- 2. 얼굴형 점수판 (Scoreboard) 세팅 ---
		val scores = mutableMapOf(
			FaceShape.CIRCLE to 0f,
			FaceShape.RHOMBUS to 0f,
			FaceShape.EGG to 0f,
			FaceShape.HEXAGON to 0f,
			FaceShape.PEANUT to 0f,
			FaceShape.HEART to 0f
		)
		fun add(shape: FaceShape, points: Float) { scores[shape] = scores[shape]!! + points }

		// --- 3. 특징별 채점 시작 ---

		// [둥근형 특성] - 김정은, 봉준호, 트럼프
		if (sideFleshRatio > 0.38f) add(FaceShape.CIRCLE, 60f) // 볼 여백이 많으면 압도적 1위 후보
		if (sideFleshRatio > 0.42f) add(FaceShape.CIRCLE, 40f) // 초고도비만형 가중치
		if (chinAngle > 125f) add(FaceShape.CIRCLE, 30f)      // 턱이 둥글면 추가
		if (cheekHollowRatio > 0.92f) add(FaceShape.CIRCLE, 20f) // 볼패임이 전혀 없으면 둥근형

		// [마름모형 특성] - 카리나
		if (cheekToForehead > 1.12f && sideFleshRatio < 0.37f) {
			add(FaceShape.RHOMBUS, 40f)
			if (chinAngle < 118f) add(FaceShape.RHOMBUS, 20f) // 턱이 뾰족해야만 진짜 마름모
		} else if (sideFleshRatio >= 0.38f) {
			add(FaceShape.RHOMBUS, -50f) // ❌ 살집이 있으면 마름모일 확률 마이너스
		}

		// [계란형 특성] - 밸런스 지표
		if (widthToHeight in 0.75f..0.82f) add(FaceShape.EGG, 30f)
		if (chinAngle in 115f..125f) add(FaceShape.EGG, 20f)
		if (cheekHollowRatio > 0.85f) add(FaceShape.EGG, 20f)
		if (cheekToForehead in 0.95f..1.10f) add(FaceShape.EGG, 20f)

		// [육각형 특성] - 유해진 님이 육각형으로 빠지는 것 방지 (조건을 더 엄격하게 뼈대로 한정)
		if (cheekToJaw <= 1.08f) add(FaceShape.HEXAGON, 40f) // 턱이 광대만큼 '확실하게' 넓어야 함
		if (chinAngle > 122f && sideFleshRatio <= 0.35f) add(FaceShape.HEXAGON, 30f) // 살이 아닌 진짜 골격으로 각진 경우

		// [땅콩형 특성] - 안젤리나 졸리
		if (cheekHollowRatio < 0.83f) add(FaceShape.PEANUT, 50f) // 볼이 쏙 패임 (핵심)
		if (cheekToJaw < 1.15f) add(FaceShape.PEANUT, 30f)
		if (chinAngle > 115f) add(FaceShape.PEANUT, 20f)

		// [하트형 특성] - 극단적 V라인뿐만 아니라 '자연스럽게 좁아지는 하관' 포함 (유해진, 봉준호 타겟)
		if (foreheadToJaw > 1.12f) add(FaceShape.HEART, 30f) // 이마/상단이 하관보다 확실히 넓음
		if (cheekToJaw in 1.12f..1.28f) add(FaceShape.HEART, 40f) // 💡 광대>턱이지만 마름모처럼 극단적으로 좁지 않음 (핵심)
		if (chinAngle < 118f) add(FaceShape.HEART, 30f) // 극단적 V라인이 아니어도 어느 정도 갸름하게 떨어지면 점수 부여


		// --- 4. 순위 매기기 ---
		val sortedScores = scores.entries.sortedByDescending { it.value }
		val firstPlace = sortedScores[0]
		val secondPlace = sortedScores[1]

		val primaryShape = firstPlace.key
		var secondaryShape: FaceShape? = null

		// 💡 2순위 결정 로직:
		// 1. 2순위 점수가 의미 있는 수준(예: 30점 이상)이고,
		// 2. 1순위가 압도적이지 않은 경우(예: 김정은처럼 둥근형이 100점이고 2순위가 10점이면 서브 얼굴형 없음)
		if (secondPlace.value >= 30f && secondPlace.value >= firstPlace.value * 0.4f) {
			secondaryShape = secondPlace.key
		}

		Log.v("얼굴형비율", "광대/이마:${"%.2f".format(cheekToForehead)} | 광대/턱:${"%.2f".format(cheekToJaw)} | 가로세로:${"%.2f".format(widthToHeight)} | 여백:${"%.2f".format(sideFleshRatio)} | 턱각도:${"%.1f".format(chinAngle)}")
		Log.v("얼굴형채점", "1위: ${firstPlace.key.name}(${firstPlace.value}점) | 2위: ${secondPlace.key.name}(${secondPlace.value}점)")

		val displaySecondary = if(secondaryShape != null) " + ${secondaryShape.displayName}" else " (단독)"
		Log.v("얼굴형최종결과", "${primaryShape.displayName}$displaySecondary")

		return FaceShapeResult(primaryShape, secondaryShape, ratios)
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

		val leftJaw  = lm[58]
		val rightJaw = lm[288]
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

		val leftCI  = ((leftMaxD  / leftLineLen)  * 10f).coerceAtLeast(0f)
		val rightCI = ((rightMaxD / rightLineLen) * 10f).coerceAtLeast(0f)

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