package com.tangoplus.facebeautyexpert.domain.util

import android.util.Log
import com.tangoplus.facebeautyexpert.data.db.FaceShape
import com.tangoplus.facebeautyexpert.data.db.FacialAxisShape
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
		faceLandmarks: MutableList<Pair<Float , Float>>,
		hairlinePoints: List<Triple<Float, Float, Float>>
	): FaceShapeResult {

		val lm = faceLandmarks

		// 핵심 측정값
		val cheekWidth   = abs(lm[454].first - lm[234].first)  // 광대 width
		val foreheadWidth = abs(lm[332].first - lm[103].first) // 이마 width
		val jawWidth     = abs(lm[397].first - lm[172].first)  // 턱 width

		// 헤어라인 Y (있으면 사용, 없으면 랜드마크 10번)
		val hairlineY = hairlinePoints
			.getOrNull(10)?.second
			?: lm[10].second
		val chinY     = lm[152].second
		val faceHeight = abs(chinY - hairlineY)

		// 비율
		val widthHeightRatio  = cheekWidth / faceHeight      // 넓이/높이
		val foreheadJawRatio  = foreheadWidth / jawWidth     // 이마/턱
		val cheekForeheadRatio = cheekWidth / foreheadWidth  // 광대/이마
		val cheekJawRatio     = cheekWidth / jawWidth        // 광대/턱

		// 광대 Y 위치 비율 (얼굴에서 광대가 어느 위치인지)
		val cheekYRatio = abs(lm[234].second - hairlineY) / faceHeight

		val ratios = mapOf(
			"widthHeight"    to widthHeightRatio,
			"foreheadJaw"   to foreheadJawRatio,
			"cheekForehead" to cheekForeheadRatio,
			"cheekJaw"      to cheekJawRatio,
			"cheekYRatio"   to cheekYRatio
		)

		Log.v("얼굴형", ratios.toString())

		val shape = when {
			// 둥근형: 넓이≈높이, 광대가 가장 넓고 전체적으로 둥글
			widthHeightRatio > 0.85f
				&& cheekForeheadRatio < 1.2f
				&& cheekJawRatio < 1.2f -> FaceShape.CIRCLE

			// 계란형: 이마>턱, 광대가 가장 넓고 아래로 갈수록 좁아짐
			foreheadJawRatio in 1.1f..1.4f
				&& cheekForeheadRatio in 0.9f..1.15f
				&& widthHeightRatio < 0.85f -> FaceShape.EGG

			// 하트형: 이마>>턱, 광대가 넓고 턱이 많이 좁음
			foreheadJawRatio > 1.4f
				&& cheekJawRatio > 1.4f -> FaceShape.HEART

			// 마름모형: 이마<광대>턱, 광대가 유독 넓음
			cheekForeheadRatio > 1.2f
				&& cheekJawRatio > 1.2f -> FaceShape.RHOMBUS

			// 땅콩형: 이마≈턱>광대 (광대가 상대적으로 좁음)
			cheekForeheadRatio < 0.9f
				&& cheekJawRatio < 0.9f -> FaceShape.PEANUT

			// 육각형: 이마≈턱≈광대, 전체적으로 각짐
			foreheadJawRatio in 0.9f..1.1f
				&& cheekForeheadRatio in 0.9f..1.1f -> FaceShape.HEXAGON

			// fallback
			else -> FaceShape.EGG
		}

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

		// 기준선: 왼쪽 하악각 → 오른쪽 하악각
		val leftJaw  = lm[172]
		val rightJaw = lm[397]

		// 얼굴 width (광대 기준) - 정규화용
		val faceWidth = abs(lm[454].first - lm[234].first)
		if (faceWidth == 0f) return Pair(0f, 0f)

		// 기준선 벡터
		val lineX = rightJaw.first - leftJaw.first
		val lineY = rightJaw.second - leftJaw.second
		val lineLen = hypot(lineX.toDouble(), lineY.toDouble()).toFloat()

		if (lineLen == 0f) return Pair(0f, 0f)

		// 점 → 기준선 수직거리 (양수 = 아래로 처짐)
		fun distanceFromBaseline(px: Float, py: Float): Float {
			val cross = (px - leftJaw.first) * lineY - (py - leftJaw.second) * lineX
			return cross / lineLen
		}

		// 왼쪽/오른쪽 볼살 후보
		val leftCheekIndices  = listOf(136, 150, 149, 176, 148)
		val rightCheekIndices = listOf(365, 379, 378, 400, 377)

		val leftSagging = leftCheekIndices
			.map { idx -> distanceFromBaseline(lm[idx].first, lm[idx].second) }
			.maxOrNull() ?: 0f

		val rightSagging = rightCheekIndices
			.map { idx -> distanceFromBaseline(lm[idx].first, lm[idx].second) }
			.maxOrNull() ?: 0f

		// 얼굴 width로 정규화 (0.0 ~ 1.0 사이, 클수록 많이 처짐)
		val normalizedLeft  = (leftSagging / faceWidth).coerceAtLeast(0f)
		val normalizedRight = (rightSagging / faceWidth).coerceAtLeast(0f)

		Log.v("처짐", "왼쪽: ${"%.4f".format(normalizedLeft)}, 오른쪽: ${"%.4f".format(normalizedRight)}, faceWidth: $faceWidth")

		return Pair(normalizedLeft, normalizedRight)
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

		Log.v("안면비대칭", """
        왼쪽 길이: $leftLength, 오른쪽 길이: $rightLength
        왼쪽 각도: ${"%.2f".format(leftAngle)}°, 오른쪽 각도: ${"%.2f".format(rightAngle)}°
        길이 비대칭: ${"%.4f".format(lengthAsymmetry)}
        각도 비대칭: ${"%.4f".format(angleAsymmetry)}
    """.trimIndent())

		return Pair(lengthAsymmetry, angleAsymmetry)
	}

	fun calculateHorizontalRatio(
		faceLandmarks: MutableList<Pair<Float, Float>>
	): String {
		val earLeft    = faceLandmarks[234]
		val eyeLeftOuter = faceLandmarks[33]
		val eyeLeftInner = faceLandmarks[133]
		val eyeRightInner = faceLandmarks[362]
		val eyeRightOuter = faceLandmarks[263]
		val earRight   = faceLandmarks[454]

		val seg1 = abs(eyeLeftOuter.first  - earLeft.first)        // 귓바퀴 → 왼눈끝
		val seg2 = abs(eyeLeftInner.first  - eyeLeftOuter.first)   // 왼눈끝 → 왼눈시작
		val seg3 = abs(eyeRightInner.first - eyeLeftInner.first)   // 왼눈시작 → 오른눈시작 (미간)
		val seg4 = abs(eyeRightOuter.first - eyeRightInner.first)  // 오른눈시작 → 오른눈끝
		val seg5 = abs(earRight.first      - eyeRightOuter.first)  // 오른눈끝 → 오른귓바퀴

		val minSeg = listOf(seg1, seg2, seg3, seg4, seg5).min()
		if (minSeg == 0f) return "0:0:0:0:0"

		// 가장 작은 세그먼트 기준으로 정규화
		return listOf(seg1, seg2, seg3, seg4, seg5)
			.joinToString(":") { seg ->
				"%.2f".format(seg / minSeg)
			}
	}
	fun calculateVerticalRatio(
		faceLandmarks: MutableList<Pair<Float, Float>>,
		hairLine3DCoordinates: MutableList<Triple<Float, Float, Float>>  // 7개
	): String {
		// 헤어라인 중앙 (10번 인덱스 = hairLine3DCoordinates[3])
		val hairlineCenterY = hairLine3DCoordinates[3].second

		// 세로 기준점 Y좌표
		val eyeLineY  = (faceLandmarks[33].second + faceLandmarks[263].second) / 2f  // 양눈끝 중간
		val noseTipY  = faceLandmarks[4].second    // 코끝
		val mouthY    = faceLandmarks[17].second   // 아랫입술 끝
		val chinY     = faceLandmarks[152].second  // 턱끝

		val seg1 = abs(eyeLineY  - hairlineCenterY)  // 헤어라인 → 눈
		val seg2 = abs(noseTipY  - eyeLineY)         // 눈 → 코끝
		val seg3 = abs(mouthY    - noseTipY)         // 코끝 → 입술끝
		val seg4 = abs(chinY     - mouthY)           // 입술끝 → 턱끝

		val minSeg = listOf(seg1, seg2, seg3, seg4).min()
		if (minSeg == 0f) return "0:0:0:0"

		return listOf(seg1, seg2, seg3, seg4)
			.joinToString(":") { seg ->
				"%.2f".format(seg / minSeg)
			}
	}



}