package com.tangoplus.tangoface.domain.util

import android.util.Log
import com.tangoplus.tangoface.data.db.FaceShape
import com.tangoplus.tangoface.data.db.FacialAxisShape
import com.tangoplus.tangoface.domain.util.MathHelpers.calculateAngle
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

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

		// --- 1. 기본 골격 데이터 추출 ---
		val cheekWidth    = abs(lm[454].first - lm[234].first)
		val foreheadWidth = abs(lm[332].first - lm[103].first)
		val jawWidth      = abs(lm[397].first - lm[172].first)
		val midFaceWidth  = abs(lm[361].first - lm[132].first)

		val hairlineY = hairlinePoints.getOrNull(3)?.second ?: lm[10].second
		val chinY     = lm[152].second
		val faceHeight = abs(chinY - hairlineY)

		val widthToHeight   = cheekWidth / faceHeight
		val cheekToJaw      = cheekWidth / jawWidth
		val foreheadToJaw   = foreheadWidth / jawWidth
		val cheekToForehead = cheekWidth / foreheadWidth

		val chinAngle = calculateAngle(lm[132], lm[152], lm[361])

		// --- 2. ✨ NEW: 상/하단 볼살(Flesh) 여백 추출 ---
		// [상단 여백] 눈 끝 ~ 얼굴 외곽 (광대 부각 및 상단 볼살 확인)
		val upperFleshWidth = abs(lm[454].first - lm[263].first) + abs(lm[33].first - lm[234].first)
		val upperFleshRatio = upperFleshWidth / cheekWidth

		// [하단 여백] 입꼬리 ~ 얼굴 외곽 (심술보, 하단 볼살 두께 확인)
		// 좌측: 외곽(58) - 입꼬리(61) / 우측: 외곽(288) - 입꼬리(291)
		val lowerFleshWidth = abs(lm[61].first - lm[58].first) + abs(lm[288].first - lm[291].first)
		val lowerFleshRatio = lowerFleshWidth / cheekWidth

		// --- 1. 핵심 판별 플래그 (Celebrity 데이터 기반 최적화) ---
// 김정은(0.45), 봉준호(0.44) 등 '머리 너비'가 넓은 타입
		val isUpperWide = upperFleshRatio > 0.42f
// 김정은(0.64) 등 하단 볼살이 비정상적으로 많은 타입
		val isChubby = upperFleshRatio > 0.43f && lowerFleshRatio > 0.60f
// 정호연(0.37), 한효주(0.35) 등 뼈가 도드라지고 하단은 매끈한 타입
		val isBoneProminent = upperFleshRatio > 0.33f && lowerFleshRatio <= 0.54f

// --- 2. 얼굴형 점수판 세팅 ---
		val scores = mutableMapOf(
			FaceShape.EGG to 0f,
			FaceShape.HEART to 0f,
			FaceShape.HEXAGON to 0f,
			FaceShape.CIRCLE to 0f,
			FaceShape.RHOMBUS to 0f
		)
		fun add(shape: FaceShape, points: Float) { scores[shape] = (scores[shape] ?: 0f) + points }

		// --- 3. 특징별 채점 시작 ---

		// [A. 둥근형(CIRCLE)] - 김정은, 봉준호 타겟
		if (widthToHeight > 0.83f) add(FaceShape.CIRCLE, 40f)
		if (isChubby) add(FaceShape.CIRCLE, 60f) // 고도비만형
		if (isUpperWide) add(FaceShape.CIRCLE, 50f) // 상단 너비가 넓은 넙데데형

		// [B. 골격형 판별 (마름모 vs 육각형)] - 정호연, 한효주 타겟
		if (isBoneProminent) {
			if (cheekToJaw > 1.28f) {
				// 💎 정호연 타입 (턱이 극단적으로 좁은 마름모/다이아몬드)
				add(FaceShape.RHOMBUS, 80f)
				add(FaceShape.HEART, 40f)
				if (chinAngle < 100f) add(FaceShape.RHOMBUS, 20f)
			} else {
				// 📐 한효주 타입 (턱 골격이 우아하게 살아있는 육각형)
				add(FaceShape.HEXAGON, 90f)
				add(FaceShape.HEART, 20f)
				if (widthToHeight in 0.70f..0.80f) add(FaceShape.HEXAGON, 20f)
			}
		} else if (cheekToJaw < 1.10f && !isChubby) {
			// 일반 사각턱 타입 육각형
			add(FaceShape.HEXAGON, 60f)
		}

		// [C. 계란형(EGG)] - 고윤정 타겟
		// 💡 계란형은 뼈 부각, 살집, 넓은 상단이 모두 없을 때만 도달 가능
		if (!isChubby && !isUpperWide && !isBoneProminent) {
			if (chinAngle < 116f) add(FaceShape.EGG, 60f)
			if (cheekToJaw > 1.25f) add(FaceShape.EGG, 30f)
			if (widthToHeight <= 0.82f) add(FaceShape.EGG, 10f)
		} else {
			// 특징이 하나라도 튀면 계란형에서 강력 제외
			add(FaceShape.EGG, -100f)
		}

		// [D. 하트형(HEART)] - 역삼각형 실루엣 보정
		if (!isChubby) {
			// 턱이 뾰족하면 하트형 점수를 주되, 뼈 부각 시 육각형/마름모보다 낮게 설정
			if (chinAngle < 105f) {
				// 턱이 1.28보다 좁을 때만 하트형 점수 비중을 높임
				val heartBonus = if (cheekToJaw > 1.28f) 50f else 20f
				add(FaceShape.HEART, heartBonus)
			}
			if (foreheadToJaw > 1.15f) add(FaceShape.HEART, 20f)
		}

		// --- 5. 순위 매기기 ---
		val sortedScores = scores.entries.sortedByDescending { it.value }
		val firstPlace = sortedScores[0]
		val secondPlace = sortedScores[1]

		val primaryShape = firstPlace.key
		var secondaryShape: FaceShape? = null

		// 2순위 결정 (1순위가 압도적이지 않고, 2순위가 유의미한 점수일 때)
		if (secondPlace.value >= 30f && secondPlace.value >= firstPlace.value * 0.5f) {
			secondaryShape = secondPlace.key
		}
		Log.v("얼굴여백분석", "상단볼살(Upper): ${"%.3f".format(upperFleshRatio)} | 하단볼살(Lower): ${"%.3f".format(lowerFleshRatio)}")
		Log.v("얼굴살판별", "비만형(Chubby): $isChubby | 뼈부각(Bone): $isBoneProminent")
		Log.v("얼굴형비율", "광대/턱: ${"%.2f".format(cheekToJaw)} | 가로/세로: ${"%.2f".format(widthToHeight)} | 턱각도: ${"%.1f".format(chinAngle)}")
		Log.v("얼굴형최종결과", "1위: ${primaryShape.name}(${firstPlace.value}점) | 2위: ${secondaryShape?.name ?: "없음"}")
		val ratios = mapOf(
			"widthToHeight"   to widthToHeight,
			"cheekToJaw"      to cheekToJaw,
			"upperFlesh"      to upperFleshRatio,
			"lowerFlesh"      to lowerFleshRatio,
			"chinAngle"       to chinAngle
		)
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

		// 💡 [수정] 외적값에 abs()를 취해 항상 양수 거리만 반환하도록 보장
		fun distanceFromLine(
			px: Float, py: Float,
			x0: Float, y0: Float,
			lineX: Float, lineY: Float,
			lineLen: Float
		): Float {
			val cross = (px - x0) * lineY - (py - y0) * lineX
			return abs(cross) / lineLen // 👈 abs() 추가로 방향 무관 절대 거리 산출
		}

		// 턱선 라인만 (132, 58 제거)
		val leftCheekIndices  = listOf(136, 150, 149, 176, 148)
		val rightCheekIndices = listOf(365, 379, 378, 400, 377)

		// 💡 [수정] 부호 반전 없이 순수 절대 거리의 최댓값만 수집
		val leftMaxD = leftCheekIndices
			.map { idx ->
				distanceFromLine(
					lm[idx].first, lm[idx].second,
					leftJaw.first, leftJaw.second,
					leftLineX, leftLineY, leftLineLen
				)
			}
			.maxOrNull() ?: 0f

		val rightMaxD = rightCheekIndices
			.map { idx ->
				distanceFromLine(
					lm[idx].first, lm[idx].second,
					rightJaw.first, rightJaw.second,
					rightLineX, rightLineY, rightLineLen
				)
			}
			.maxOrNull() ?: 0f

		// 💡 [수정] 기준값 1.0에서 시작하여 최대 2.0으로 수렴시키는 보정 수식
		// d/L 비율(보통 0.0 ~ 0.1 사이)에 10을 곱해 더해줍니다.

		/* $0.0\text{cm}$1.00처짐 없음 (칼날 같은 V라인)
		* $1.4\text{cm}$1.29미세 처짐
		* $2.8\text{cm}$1.57보통 처짐
		* $4.2\text{cm}$1.86심한 처짐
		* $4.9\text{cm}$2.00 (Max)극심한 처짐 (Max 기준점)
		* */
		val scaleFactor = 2.86f

		val leftCI  = (1.0f + (leftMaxD  / leftLineLen)  * scaleFactor).coerceIn(1.0f, 2.0f)
		val rightCI = (1.0f + (rightMaxD / rightLineLen) * scaleFactor).coerceIn(1.0f, 2.0f)

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
		faceLandmarks: List<Pair<Float, Float>>,
		hairLine2DCoordinates: List<Pair<Float, Float>>
	): String {
		// 7개 포인트 중 중앙 지점(인덱스 3) 사용
		if (faceLandmarks.isNotEmpty() && hairLine2DCoordinates.size > 3) {
			val hairlineY = hairLine2DCoordinates[3].second
			val eyesY = (faceLandmarks[55].second + faceLandmarks[285].second) / 2f // 눈썹 라인
			val noseY = faceLandmarks[2].second   // 코끝
			val chinY = faceLandmarks[152].second // 턱끝

			val upper = eyesY - hairlineY
			val mid = noseY - eyesY
			val lower = chinY - noseY

			val total = upper + mid + lower
			if (total <= 0f) return "0:0:0"

			val pUpper = (upper / total * 100).roundToInt()
			val pMid = (mid / total * 100).roundToInt()
			val pLower = (lower / total * 100).roundToInt()

			return "$pUpper:$pMid:$pLower"
		}
		return "0:0:0"
	}

	fun calculateVerticalRatio(
		faceLandmarks: MutableList<Pair<Float, Float>>
	): String {
		if (faceLandmarks.isEmpty()) return "0:0:0:0:0"

		val leftEarX = faceLandmarks[234].first
		val leftEyeOutX = faceLandmarks[33].first
		val leftEyeInX = faceLandmarks[133].first
		val rightEyeInX = faceLandmarks[362].first
		val rightEyeOutX = faceLandmarks[263].first
		val rightEarX = faceLandmarks[454].first

		// 💡 절댓값으로 계산해 좌우 반전 및 음수 방지
		val m1 = abs(leftEyeOutX - leftEarX)
		val m2 = abs(leftEyeInX - leftEyeOutX)
		val m3 = abs(rightEyeInX - leftEyeInX)
		val m4 = abs(rightEyeOutX - rightEyeInX)
		val m5 = abs(rightEarX - rightEyeOutX)

		val total = m1 + m2 + m3 + m4 + m5
		if (total <= 0f) return "0:0:0:0:0"

		val p1 = (m1 / total * 100).roundToInt()
		val p2 = (m2 / total * 100).roundToInt()
		val p3 = (m3 / total * 100).roundToInt()
		val p4 = (m4 / total * 100).roundToInt()
		val p5 = (m5 / total * 100).roundToInt()

		return "$p1:$p2:$p3:$p4:$p5"
	}

}