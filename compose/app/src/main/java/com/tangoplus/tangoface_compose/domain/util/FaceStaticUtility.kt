package com.tangoplus.tangoface_compose.domain.util

import android.util.Log
import com.tangoplus.tangoface_compose.ui.info.vm.InformationViewModel
import com.tangoplus.tangoface_compose.domain.vo.FaceStaticItem
import com.tangoplus.tangoface_compose.domain.vo.Sequence
import org.json.JSONArray
import java.text.DecimalFormat
import kotlin.math.abs

object FaceStaticUtility {
	val horizontalKeyword = listOf(
		"양 눈", "입술 끝", "턱 끝", "양 어깨", "양 귀", "양 입술"
	)
	val verticalKeyword = listOf(
		"입 각도", "목 수직 각도", "얼굴 수직 각도",
	)

	// 단위값 판단하는 list
	val distanceKeyword = listOf(
		"왼쪽 중간 턱", "오른쪽 중간 턱", "입 높이"
	)
	val extentKeyword = listOf(
		"좌우 볼 너비",
		"좌우 광대 너비",
		"개구: 좌우 볼 너비",
	)
	const val noseShoulderKeyword = "어깨-코 각도"
	const val earShoulderKeyword = "어깨-귀 각도"
//    val standardKeyword = "양 눈"
//    val seqStrings = listOf(
//        "편한 상태",
//        "정면 상태",
//        "교합 상태",
//        "턱 가측 이동(왼쪽)",
//        "턱 가측 이동(오른쪽)",
//        "개구 상태",
//        "목폄 상태",
//        "왼쪽 상태",
//        "오른쪽 상태",
//        "페이스 보우"
//    )

	fun addCommentInFCI(fci: FaceStaticItem, isComparison: Boolean): FaceStaticItem {
		val newFci = fci
		val label = fci.label
		val value00 = fci.value00
		val value01 = fci.value01
		val value10 = fci.value10
		val value11 = fci.value11
		val df = DecimalFormat("#.##")
		val notation = when (label) {
			in distanceKeyword -> "cm"
			in extentKeyword -> "㎠"
			else -> "˚"
		}
		val comment = when {
			value00 != null && value01 != null && value10 != null && value11 != null -> {
				val leftSeqDiff = abs(abs(value00) - abs(value01))
				val rightSeqDiff = abs(abs(value10) - abs(value11))
				val seqDiff = abs(leftSeqDiff - rightSeqDiff)

				val (big, small) = if (leftSeqDiff < rightSeqDiff) {
					if (isComparison) Pair("오른쪽 측정", "왼쪽 측정")
					else Pair("교합", "정면")
				} else {
					if (isComparison) Pair("왼쪽 측정", "오른쪽 측정")
					else Pair("정면", "교합")
				}
				"$big 데이터가 $small 데이터보다 ${df.format(abs(seqDiff))}${notation}만큼 큽니다."

			}

			value00 != null && value01 == null && value10 != null && value11 == null -> {
				val seqDiff = abs(value00) - abs(value10)
				val (seqL, seqR) = if (fci.label in listOf("양 눈", "입술 끝", "턱 끝")) Pair(1, 2) else Pair(7, 8)
				val (big, small) = if (seqDiff < 0) {
					if (isComparison) Pair("오른쪽 측정", "왼쪽 측정")
					else if (seqL == 1 && seqR == 2) Pair("교합", "정면") else Pair("왼쪽 얼굴", "오른쪽 얼굴")
				} else {
					if (isComparison) Pair("왼쪽 측정", "오른쪽 측정")
					else if (seqL == 1 && seqR == 2) Pair("정면", "교합") else Pair("오른쪽 얼굴", "왼쪽 얼굴")
				}
				"$big 데이터가 $small 데이터보다 기준값이 ${df.format(abs(seqDiff))}${notation}만큼 큽니다."


			}

			value00 != null && value01 != null && value10 == null && value11 == null -> {
				val seqDiff = abs(value00) - abs(value01)
				if (isComparison) {
					if (seqDiff < 0) "오른쪽 측정 값이 왼쪽 측정 값보다 더 큽니다"
					else "왼쪽 측정 값이 오른쪽 측정 값보다 더 큽니다"
				} else {
					if (seqDiff < 0) "오른쪽 볼 면적이 왼쪽 볼 면적보다 더 큽니다"
					else "왼쪽 볼 면적이 오른쪽 볼 면적보다 더 큽니다"
				}
			}

			value00 != null && value01 == null && value10 == null && value11 == null -> {
				"-"
			}

			value00 == null && value01 != null && value10 == null && value11 == null -> {
				"-"
			}

			else -> "-"
		}

		newFci.comment = comment
		return newFci
	}

//	fun setLinesInImage(
//		ivm: InformationViewModel,
//		mvm: MainViewModel,
//		switchedOn: Boolean,
//		callback: () -> Unit
//	) {
//		val seq = ivm.getSeq()
//
//		// 각 seqIndex별 기본 라인 정의
//		val detailLines = mapOf(
//			0 to listOf(
//				DrawLine.A_CANTHUS, DrawLine.A_TIP_OF_LIPS, DrawLine.A_CHIN,
//				DrawLine.A_CANTHUS_ORAL, DrawLine.A_NASALWINGS_ORAL, DrawLine.A_SIDE_HEAD,
//				DrawLine.E_CHEEKS_REST, DrawLine.E_CHEEKBONE,
//				DrawLine.A_NECK, DrawLine.A_FACE
//			),
//			1 to listOf(
//				DrawLine.A_CANTHUS, DrawLine.A_TIP_OF_LIPS, DrawLine.A_CHIN,
//				DrawLine.A_CANTHUS_ORAL, DrawLine.A_NASALWINGS_ORAL, DrawLine.A_SIDE_HEAD,
//				DrawLine.E_CHEEKS_REST, DrawLine.E_CHEEKBONE
//			),
//			2 to listOf(
//				DrawLine.A_GLABELLA_NOSE, DrawLine.A_NOSE_JAW
//			),
//			3 to listOf(
//				DrawLine.A_BELOW_LIPS, DrawLine.E_CHEEKS_OPENING,
//				DrawLine.A_SHOULDER, DrawLine.A_EAR, DrawLine.A_NECK,
//				DrawLine.A_FACE, DrawLine.A_MANDIBULAR_CHIN
//			),
//			4 to listOf(
//				DrawLine.A_SHOULDER_EAR,
//				DrawLine.A_SHOULDER_NOSE
//			),
//			5 to listOf(
//				DrawLine.A_CANTHUS, DrawLine.A_CHIN
//			)
//		)
//
//		// comparison일 때 사용하는 라인 정의
//		val comparisonLines = mapOf(
//			0 to listOf( // seqIndex 0,1,2 공통
//				DrawLine.A_CANTHUS, DrawLine.A_TIP_OF_LIPS, DrawLine.A_CHIN,
//				DrawLine.A_CANTHUS_ORAL, DrawLine.A_NASALWINGS_ORAL, DrawLine.A_SIDE_HEAD,
//				DrawLine.E_CHEEKS_REST, DrawLine.E_CHEEKBONE
//			),
//			5 to listOf(
//				DrawLine.A_BELOW_LIPS, DrawLine.E_CHEEKS_OPENING
//			),
//			6 to listOf(
//				DrawLine.A_SHOULDER, DrawLine.A_EAR, DrawLine.A_NECK,
//				DrawLine.A_FACE, DrawLine.A_MANDIBULAR_CHIN
//			),
//			7 to listOf(
//				DrawLine.A_SHOULDER_EAR,
//				DrawLine.A_SHOULDER_NOSE
//			),
//			8 to listOf(
//				DrawLine.A_SHOULDER_EAR,
//				DrawLine.A_SHOULDER_NOSE
//			),
//			9 to listOf(
//				DrawLine.A_CANTHUS, DrawLine.A_CHIN
//			)
//
//		)
//
//		ivm.currentCheckedLines.clear()
//
//
//		callback()
//	}


//	fun getBtnMatchedSeq(index: Int, isComparison: Boolean): Pair<Sequence, Sequence> {
//		val (leftSeq, rightSeq) = if (!isComparison) {
//			when (index) {
//				0 -> Sequence.COMFORT to Sequence.COMFORT
//				2 -> Sequence.JAW_LEFT_TILT to Sequence.JAW_RIGHT_TILT
//				3 -> Sequence.JAW_OPENING to Sequence.NECK_EXTENTION
//				4 -> Sequence.SIDE_LEFT to Sequence.SIDE_RIGHT
//				5 -> Sequence.BOW to Sequence.BOW
//				else -> Sequence.RESTING to Sequence.OCCLUSAL
//			}
//		} else {
//			when (index) {
//				0 -> Sequence.COMFORT to Sequence.COMFORT
//				2 -> Sequence.OCCLUSAL to Sequence.OCCLUSAL
//				3 -> Sequence.JAW_LEFT_TILT to Sequence.JAW_LEFT_TILT
//				4 -> Sequence.JAW_RIGHT_TILT to Sequence.JAW_RIGHT_TILT
//				5 -> Sequence.JAW_OPENING to Sequence.JAW_OPENING
//				6 -> Sequence.NECK_EXTENTION to Sequence.NECK_EXTENTION
//				7 -> Sequence.SIDE_LEFT to Sequence.SIDE_LEFT
//				8 -> Sequence.SIDE_RIGHT to Sequence.SIDE_RIGHT
//				9 -> Sequence.BOW to Sequence.BOW
//				else -> Sequence.RESTING to Sequence.RESTING
//			}
//		}
//		return Pair(leftSeq, rightSeq)
//	}

	fun getIdxFromSeq(
		isComparison: Boolean,
		seqs0: Set<Sequence>?,
		seqs1: Set<Sequence>?,
		leftSeq: Sequence,
		rightSeq: Sequence
	): Pair<Int, Int> {
		val (leftSeqIdx, rightSeqIdx) = if (!isComparison) {
			// seqs0만 사용
			val orderedSeqs = seqs0?.toList()?.sorted() ?: emptyList()
			val leftIdx = orderedSeqs.indexOf(leftSeq)
			val rightIdx = orderedSeqs.indexOf(rightSeq)
			Pair(leftIdx, rightIdx)
		} else {
			// seqs0과 seqs1 각각 사용
			val orderedSeqs0 = seqs0?.toList()?.sorted() ?: emptyList()
			val orderedSeqs1 = seqs1?.toList()?.sorted() ?: emptyList()
			val leftIdx = orderedSeqs0.indexOf(leftSeq)
			val rightIdx = orderedSeqs1.indexOf(rightSeq)
			Pair(leftIdx, rightIdx)
		}
		return Pair(leftSeqIdx, rightSeqIdx)
	}

	fun buildFaceStaticItemList(
		ivm: InformationViewModel,
		isComparison: Boolean,
		leftJA: JSONArray?, rightJA: JSONArray?,
		seqs0: Set<Sequence>?, seqs1: Set<Sequence>?
	): List<FaceStaticItem>? {
		Log.v("buildItem", "buildFaceStaticItemList started")
//		val (leftSeq, rightSeq) = getBtnMatchedSeq(btnIndex, isComparison)
//		val (leftSeqIdx, rightSeqIdx) = if (!isComparison) {
			// seqs0만 사용
//			val orderedSeqs = seqs0?.toList()?.sorted() ?: emptyList()
//			val leftIdx = orderedSeqs.indexOf(leftSeq)
//			val rightIdx = orderedSeqs.indexOf(rightSeq)
//			Pair(leftIdx, rightIdx)
//		} else {
			// seqs0과 seqs1 각각 사용
//			val orderedSeqs0 = seqs0?.toList()?.sorted() ?: emptyList()
//			val orderedSeqs1 = seqs1?.toList()?.sorted() ?: emptyList()
//			val leftIdx = orderedSeqs0.indexOf(leftSeq)
//			val rightIdx = orderedSeqs1.indexOf(rightSeq)
//			Pair(leftIdx, rightIdx)
//		}

//		if (leftJA != null && rightJA != null) {
//			val leftValue = if (!isComparison) {
//				when (ivm.getBtnIndex()) {
//					2 -> leftJA.getJSONArray(leftSeqIdx)?.optJSONObject(0)
//					else -> leftJA.optJSONObject(leftSeqIdx)?.optJSONObject("data")
//				}
//			} else {
//				when (ivm.getBtnIndex()) {
//					3, 4 -> leftJA.optJSONObject(leftSeqIdx)?.optJSONObject("data")
//					else -> leftJA.optJSONObject(leftSeqIdx)?.optJSONObject("data")
//				}
//			}
//			val rightValue = if (!isComparison) {
//				when (ivm.getBtnIndex()) {
//					2 -> rightJA.getJSONArray(rightSeqIdx)?.optJSONObject(0)
//					else -> rightJA.optJSONObject(rightSeqIdx)?.optJSONObject("data")
//
//				}
//			} else {
//				when (ivm.getBtnIndex()) {
//					3, 4 -> rightJA.optJSONObject(rightSeqIdx)?.optJSONObject("data")
//					else -> rightJA.optJSONObject(rightSeqIdx)?.optJSONObject("data")
//				}
//			}
//
//			return if (!isComparison) {
//				when (ivm.getBtnIndex()) {
//					0 -> { // 짝수 index는 왼쪽, 홀수 index는 오른쪽
//						listOf(
//							FaceStaticItem(
//								"양 눈",
//								leftValue?.optDouble("comfort_eye_horizontal_angle")?.toFloat(),
//							),
//							FaceStaticItem(
//								"입술 끝",
//								leftValue?.optDouble("comfort_tip_of_lips_horizontal_angle")?.toFloat(),
//							),
//							FaceStaticItem(
//								"턱 끝",
//								leftValue?.optDouble("comfort_tip_of_chin_horizontal_angle")?.toFloat(),
//							),
//
//							FaceStaticItem(
//								"좌우 눈매-입술 끝",
//								leftValue?.optDouble("comfort_canthus_oral_left_vertical_angle")?.toFloat(),
//								rightValue?.optDouble("comfort_canthus_oral_right_vertical_angle")?.toFloat(),
//							),
//
//							FaceStaticItem(
//								"좌우 코끝-입술 끝",
//								leftValue?.optDouble("comfort_nasal_wing_tip_of_lips_left_vertical_angle")
//									?.toFloat(),
//								rightValue?.optDouble("comfort_nasal_wing_tip_of_lips_right_vertical_angle")
//									?.toFloat(),
//							),
//							FaceStaticItem(
//								"좌우 측두 각도",
//								leftValue?.optDouble("comfort_side_head_left_angle")?.toFloat(),
//								rightValue?.optDouble("comfort_side_head_right_angle")?.toFloat(),
//							),
//							FaceStaticItem(
//								"좌우 볼 너비",
//								leftValue?.optDouble("comfort_left_cheeks_extent")?.toFloat(),
//								rightValue?.optDouble("comfort_right_cheeks_extent")?.toFloat(),
//							),
//							FaceStaticItem(
//								"좌우 광대 너비",
//								leftValue?.optDouble("comfort_left_cheekbone_extent")?.toFloat(),
//								rightValue?.optDouble("comfort_right_cheekbone_extent")?.toFloat(),
//							),
//							FaceStaticItem(
//								"목 수직 각도",
//								leftValue?.optDouble("comfort_neck_vertical_angle")?.toFloat(),
//							),
//							FaceStaticItem(
//								"얼굴 수직 각도",
//								leftValue?.optDouble("comfort_face_vertical_angle")?.toFloat(),
//							),
//						)
//					}
//
//					1 -> { // 짝수 index는 왼쪽, 홀수 index는 오른쪽
//						listOf(
//							FaceStaticItem(
//								"양 눈",
//								value00 = leftValue?.optDouble("resting_eye_horizontal_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("occlusal_eye_horizontal_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"입술 끝",
//								value00 = leftValue?.optDouble("resting_tip_of_lips_horizontal_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("occlusal_tip_of_lips_horizontal_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"턱 끝",
//								value00 = leftValue?.optDouble("resting_tip_of_chin_horizontal_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("occlusal_tip_of_chin_horizontal_angle")?.toFloat()
//							),
//
//							FaceStaticItem(
//								"좌우 눈매-입술 끝",
//								leftValue?.optDouble("resting_canthus_oral_left_vertical_angle")?.toFloat(),
//								leftValue?.optDouble("resting_canthus_oral_right_vertical_angle")?.toFloat(),
//								rightValue?.optDouble("occlusal_canthus_oral_left_vertical_angle")?.toFloat(),
//								rightValue?.optDouble("occlusal_canthus_oral_right_vertical_angle")?.toFloat()
//							),
//
//							FaceStaticItem(
//								"좌우 코끝-입술 끝",
//								leftValue?.optDouble("resting_nasal_wing_tip_of_lips_left_vertical_angle")
//									?.toFloat(),
//								leftValue?.optDouble("resting_nasal_wing_tip_of_lips_right_vertical_angle")
//									?.toFloat(),
//								rightValue?.optDouble("occlusal_nasal_wing_tip_of_lips_left_vertical_angle")
//									?.toFloat(),
//								rightValue?.optDouble("occlusal_nasal_wing_tip_of_lips_right_vertical_angle")
//									?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 측두 각도",
//								leftValue?.optDouble("resting_side_head_left_angle")?.toFloat(),
//								leftValue?.optDouble("resting_side_head_right_angle")?.toFloat(),
//								rightValue?.optDouble("occlusal_side_head_left_angle")?.toFloat(),
//								rightValue?.optDouble("occlusal_side_head_right_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 볼 너비",
//								leftValue?.optDouble("resting_left_cheeks_extent")?.toFloat(),
//								leftValue?.optDouble("resting_right_cheeks_extent")?.toFloat(),
//								rightValue?.optDouble("occlusal_left_cheeks_extent")?.toFloat(),
//								rightValue?.optDouble("occlusal_right_cheeks_extent")?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 광대 너비",
//								leftValue?.optDouble("resting_left_cheekbone_extent")?.toFloat(),
//								leftValue?.optDouble("resting_right_cheekbone_extent")?.toFloat(),
//								rightValue?.optDouble("occlusal_left_cheekbone_extent")?.toFloat(),
//								rightValue?.optDouble("occlusal_right_cheekbone_extent")?.toFloat()
//							),
//						)
//					}
//
//					3 -> {
//						listOf(
//							FaceStaticItem("입 높이", leftValue?.optDouble("jaw_opening_lips_distance")?.toFloat()),
//							FaceStaticItem(
//								"입 각도",
//								leftValue?.optDouble("jaw_opening_lips_vertical_angle")?.toFloat()
//							),
//
//							FaceStaticItem(
//								"개구: 좌우 볼 너비",
//								leftValue?.optDouble("jaw_opening_left_cheeks_extent")?.toFloat(),
//								leftValue?.optDouble("jaw_opening_right_cheeks_extent")?.toFloat()
//							),
//							FaceStaticItem(
//								"양 어깨",
//								value10 = rightValue?.optDouble("neck_extention_shoulder_horizontal_angle")
//									?.toFloat(),
//							),
//							FaceStaticItem(
//								"양 귀",
//								value10 = rightValue?.optDouble("neck_extention_ear_horizontal_angle")?.toFloat(),
//							),
//							FaceStaticItem(
//								"목 수직 각도",
//								value10 = rightValue?.optDouble("neck_extention_neck_vertical_angle")?.toFloat(),
//							),
//							FaceStaticItem(
//								"얼굴 수직 각도",
//								value10 = rightValue?.optDouble("neck_extention_face_vertical_angle")?.toFloat(),
//							),
//
//							FaceStaticItem(
//								"왼쪽 사각턱 각도",
//								value10 = rightValue?.optDouble("neck_extention_left_mandibular_chin_angle")
//									?.toFloat(),
//							),
//							FaceStaticItem(
//								"오른쪽 사각턱 각도",
//								value10 = rightValue?.optDouble("neck_extention_right_mandibular_chin_angle")
//									?.toFloat(),
//							),
//							FaceStaticItem(
//								"중앙 턱 각도",
//								value10 = rightValue?.optDouble("neck_extention_middle_mandibular_chin_angle")
//									?.toFloat(),
//							),
//						)
//					}
//
//					4 -> {
//						listOf(
//							FaceStaticItem(
//								"어깨-코 각도",
//								value00 = leftValue?.optDouble("side_left_shoulder_nose_vertical_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("side_right_shoulder_nose_vertical_angle")
//									?.toFloat()
//							),
//							FaceStaticItem(
//								"어깨-귀 각도",
//								value00 = leftValue?.optDouble("side_left_shoulder_ear_vertical_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("side_right_shoulder_ear_vertical_angle")?.toFloat()
//							),
//						)
//					}
//
//					5 -> {
//						listOf(
//							FaceStaticItem("양 눈", leftValue?.optDouble("bow_eye_horizontal_angle")?.toFloat()),
//							FaceStaticItem(
//								"입술 끝",
//								leftValue?.optDouble("bow_tip_of_lips_horizontal_angle")?.toFloat(),
//							),
//							FaceStaticItem(
//								"턱 끝",
//								leftValue?.optDouble("bow_tip_of_chin_horizontal_angle")?.toFloat(),
//							),
//						)
//					}
//
//					else -> listOf()
//				}
//			} else {
//				// "비교" 임
//				when (ivm.getBtnIndex()) {
//					0 -> {
//						listOf(
//							FaceStaticItem(
//								"양 눈",
//								value00 = leftValue?.optDouble("comfort_eye_horizontal_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("comfort_eye_horizontal_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"양 입술",
//								value00 = leftValue?.optDouble("comfort_tip_of_lips_horizontal_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("comfort_tip_of_lips_horizontal_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"턱 끝",
//								value00 = leftValue?.optDouble("comfort_tip_of_chin_horizontal_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("comfort_tip_of_chin_horizontal_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 눈매-입술 끝",
//								leftValue?.optDouble("comfort_canthus_oral_left_vertical_angle")?.toFloat(),
//								leftValue?.optDouble("comfort_canthus_oral_right_vertical_angle")?.toFloat(),
//								rightValue?.optDouble("comfort_canthus_oral_left_vertical_angle")?.toFloat(),
//								rightValue?.optDouble("comfort_canthus_oral_right_vertical_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 코끝-입술 끝",
//								leftValue?.optDouble("comfort_nasal_wing_tip_of_lips_left_vertical_angle")
//									?.toFloat(),
//								leftValue?.optDouble("comfort_nasal_wing_tip_of_lips_right_vertical_angle")
//									?.toFloat(),
//								rightValue?.optDouble("comfort_nasal_wing_tip_of_lips_left_vertical_angle")
//									?.toFloat(),
//								rightValue?.optDouble("comfort_nasal_wing_tip_of_lips_right_vertical_angle")
//									?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 측두",
//								leftValue?.optDouble("comfort_side_head_left_angle")?.toFloat(),
//								leftValue?.optDouble("comfort_side_head_right_angle")?.toFloat(),
//								rightValue?.optDouble("comfort_side_head_left_angle")?.toFloat(),
//								rightValue?.optDouble("comfort_side_head_right_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 볼 너비",
//								leftValue?.optDouble("comfort_left_cheeks_extent")?.toFloat(),
//								leftValue?.optDouble("comfort_right_cheeks_extent")?.toFloat(),
//								rightValue?.optDouble("comfort_left_cheeks_extent")?.toFloat(),
//								rightValue?.optDouble("comfort_right_cheeks_extent")?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 광대 너비",
//								leftValue?.optDouble("comfort_left_cheekbone_extent")?.toFloat(),
//								leftValue?.optDouble("comfort_right_cheekbone_extent")?.toFloat(),
//								rightValue?.optDouble("comfort_left_cheekbone_extent")?.toFloat(),
//								rightValue?.optDouble("comfort_right_cheekbone_extent")?.toFloat()
//							),
//							FaceStaticItem(
//								"목 수직 각도",
//								value00 = leftValue?.optDouble("comfort_neck_vertical_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("comfort_neck_vertical_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"얼굴 수직 각도",
//								value00 = leftValue?.optDouble("comfort_face_vertical_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("comfort_face_vertical_angle")?.toFloat()
//							),
//
//							)
//					}
//
//					1 -> {
//						listOf(
//							FaceStaticItem(
//								"양 눈",
//								value00 = leftValue?.optDouble("resting_eye_horizontal_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("resting_eye_horizontal_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"양 입술",
//								value00 = leftValue?.optDouble("resting_tip_of_lips_horizontal_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("resting_tip_of_lips_horizontal_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"턱 끝",
//								value00 = leftValue?.optDouble("resting_tip_of_chin_horizontal_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("resting_tip_of_chin_horizontal_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 눈매-입술 끝",
//								leftValue?.optDouble("resting_canthus_oral_left_vertical_angle")?.toFloat(),
//								leftValue?.optDouble("resting_canthus_oral_right_vertical_angle")?.toFloat(),
//								rightValue?.optDouble("resting_canthus_oral_left_vertical_angle")?.toFloat(),
//								rightValue?.optDouble("resting_canthus_oral_right_vertical_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 코끝-입술 끝",
//								leftValue?.optDouble("resting_nasal_wing_tip_of_lips_left_vertical_angle")
//									?.toFloat(),
//								leftValue?.optDouble("resting_nasal_wing_tip_of_lips_right_vertical_angle")
//									?.toFloat(),
//								rightValue?.optDouble("resting_nasal_wing_tip_of_lips_left_vertical_angle")
//									?.toFloat(),
//								rightValue?.optDouble("resting_nasal_wing_tip_of_lips_right_vertical_angle")
//									?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 측두",
//								leftValue?.optDouble("resting_side_head_left_angle")?.toFloat(),
//								leftValue?.optDouble("resting_side_head_right_angle")?.toFloat(),
//								rightValue?.optDouble("resting_side_head_left_angle")?.toFloat(),
//								rightValue?.optDouble("resting_side_head_right_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 볼 너비",
//								leftValue?.optDouble("resting_left_cheeks_extent")?.toFloat(),
//								leftValue?.optDouble("resting_right_cheeks_extent")?.toFloat(),
//								rightValue?.optDouble("resting_left_cheeks_extent")?.toFloat(),
//								rightValue?.optDouble("resting_right_cheeks_extent")?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 광대 너비",
//								leftValue?.optDouble("resting_left_cheekbone_extent")?.toFloat(),
//								leftValue?.optDouble("resting_right_cheekbone_extent")?.toFloat(),
//								rightValue?.optDouble("resting_left_cheekbone_extent")?.toFloat(),
//								rightValue?.optDouble("resting_right_cheekbone_extent")?.toFloat()
//							),
//						)
//					}
//
//					2 -> {
//						listOf(
//							FaceStaticItem(
//								"양 눈",
//								value00 = leftValue?.optDouble("occlusal_eye_horizontal_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("occlusal_eye_horizontal_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"양 입술",
//								value00 = leftValue?.optDouble("occlusal_tip_of_lips_horizontal_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("occlusal_tip_of_lips_horizontal_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"턱 끝",
//								value00 = leftValue?.optDouble("occlusal_tip_of_chin_horizontal_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("occlusal_tip_of_chin_horizontal_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 눈매-입술 끝",
//								leftValue?.optDouble("occlusal_canthus_oral_left_vertical_angle")?.toFloat(),
//								leftValue?.optDouble("occlusal_canthus_oral_right_vertical_angle")?.toFloat(),
//								rightValue?.optDouble("occlusal_canthus_oral_left_vertical_angle")?.toFloat(),
//								rightValue?.optDouble("occlusal_canthus_oral_right_vertical_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 코끝-입술 끝",
//								leftValue?.optDouble("occlusal_nasal_wing_tip_of_lips_left_vertical_angle")
//									?.toFloat(),
//								leftValue?.optDouble("occlusal_nasal_wing_tip_of_lips_right_vertical_angle")
//									?.toFloat(),
//								rightValue?.optDouble("occlusal_nasal_wing_tip_of_lips_left_vertical_angle")
//									?.toFloat(),
//								rightValue?.optDouble("occlusal_nasal_wing_tip_of_lips_right_vertical_angle")
//									?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 측두",
//								leftValue?.optDouble("occlusal_side_head_left_angle")?.toFloat(),
//								leftValue?.optDouble("occlusal_side_head_right_angle")?.toFloat(),
//								rightValue?.optDouble("occlusal_side_head_left_angle")?.toFloat(),
//								rightValue?.optDouble("occlusal_side_head_right_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 볼 너비",
//								leftValue?.optDouble("occlusal_left_cheeks_extent")?.toFloat(),
//								leftValue?.optDouble("occlusal_right_cheeks_extent")?.toFloat(),
//								rightValue?.optDouble("occlusal_left_cheeks_extent")?.toFloat(),
//								rightValue?.optDouble("occlusal_right_cheeks_extent")?.toFloat()
//							),
//							FaceStaticItem(
//								"좌우 광대 너비",
//								leftValue?.optDouble("occlusal_left_cheekbone_extent")?.toFloat(),
//								leftValue?.optDouble("occlusal_right_cheekbone_extent")?.toFloat(),
//								rightValue?.optDouble("occlusal_left_cheekbone_extent")?.toFloat(),
//								rightValue?.optDouble("occlusal_right_cheekbone_extent")?.toFloat()
//							),
//						)
//					}
//
//					5 -> {
//						listOf(
//							FaceStaticItem(
//								"입 높이",
//								value00 = leftValue?.optDouble("jaw_opening_lips_distance")?.toFloat(),
//								value10 = rightValue?.optDouble("jaw_opening_lips_distance")?.toFloat()
//							),
//							FaceStaticItem(
//								"입 각도",
//								value00 = leftValue?.optDouble("jaw_opening_lips_vertical_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("jaw_opening_lips_vertical_angle")?.toFloat()
//							),
//
//							FaceStaticItem(
//								"개구: 좌우 볼 너비",
//								leftValue?.optDouble("jaw_opening_left_cheeks_extent")?.toFloat(),
//								leftValue?.optDouble("jaw_opening_right_cheeks_extent")?.toFloat(),
//								rightValue?.optDouble("jaw_opening_left_cheeks_extent")?.toFloat(),
//								rightValue?.optDouble("jaw_opening_right_cheeks_extent")?.toFloat()
//							),
//						)
//					}
//
//					6 -> {
//						listOf(
//							FaceStaticItem(
//								"양 어깨",
//								value00 = leftValue?.optDouble("neck_extention_shoulder_horizontal_angle")
//									?.toFloat(),
//								value10 = rightValue?.optDouble("neck_extention_shoulder_horizontal_angle")
//									?.toFloat()
//							),
//
//							FaceStaticItem(
//								"양 귀",
//								value00 = leftValue?.optDouble("neck_extention_ear_horizontal_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("neck_extention_ear_horizontal_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"목 수직 각도",
//								value00 = leftValue?.optDouble("neck_extention_neck_vertical_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("neck_extention_neck_vertical_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"얼굴 수직 각도",
//								value00 = leftValue?.optDouble("neck_extention_face_vertical_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("neck_extention_face_vertical_angle")?.toFloat(),
//							),
//
//							FaceStaticItem(
//								"왼쪽 사각턱 각도",
//								value00 = rightValue?.optDouble("neck_extention_left_mandibular_chin_angle")
//									?.toFloat(),
//								value10 = rightValue?.optDouble("neck_extention_left_mandibular_chin_angle")
//									?.toFloat()
//							),
//
//							FaceStaticItem(
//								"오른쪽 사각턱 각도",
//								value00 = rightValue?.optDouble("neck_extention_right_mandibular_chin_angle")
//									?.toFloat(),
//								value10 = rightValue?.optDouble("neck_extention_right_mandibular_chin_angle")
//									?.toFloat()
//							),
//							FaceStaticItem(
//								"중앙 턱 각도",
//								value00 = rightValue?.optDouble("neck_extention_middle_mandibular_chin_angle")
//									?.toFloat(),
//								value10 = rightValue?.optDouble("neck_extention_middle_mandibular_chin_angle")
//									?.toFloat()
//							),
//						)
//					}
//
//					7 -> {
//						listOf(
//							FaceStaticItem(
//								"왼쪽 어깨-코 각도",
//								value00 = leftValue?.optDouble("side_left_shoulder_nose_vertical_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("side_left_shoulder_nose_vertical_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"왼쪽 어깨-귀 각도",
//								value00 = leftValue?.optDouble("side_left_shoulder_ear_vertical_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("side_left_shoulder_ear_vertical_angle")?.toFloat()
//							),
//						)
//					}
//
//					8 -> {
//						listOf(
//							FaceStaticItem(
//								"오른쪽 어깨-코 각도",
//								value00 = leftValue?.optDouble("side_right_shoulder_nose_vertical_angle")
//									?.toFloat(),
//								value10 = rightValue?.optDouble("side_right_shoulder_nose_vertical_angle")
//									?.toFloat()
//							),
//							FaceStaticItem(
//								"오른쪽 어깨-귀 각도",
//								value00 = leftValue?.optDouble("side_right_shoulder_ear_vertical_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("side_right_shoulder_ear_vertical_angle")?.toFloat()
//							),
//						)
//					}
//
//					9 -> {
//						listOf(
//							FaceStaticItem(
//								"양 눈",
//								value00 = leftValue?.optDouble("bow_eye_horizontal_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("bow_eye_horizontal_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"양 입술",
//								value00 = leftValue?.optDouble("bow_tip_of_lips_horizontal_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("bow_tip_of_lips_horizontal_angle")?.toFloat()
//							),
//							FaceStaticItem(
//								"턱 끝",
//								value00 = leftValue?.optDouble("bow_tip_of_chin_horizontal_angle")?.toFloat(),
//								value10 = rightValue?.optDouble("bow_tip_of_chin_horizontal_angle")?.toFloat()
//							),
//						)
//					}
//
//					else -> listOf()
//				}
//			}
//		}
		return null
	}


}