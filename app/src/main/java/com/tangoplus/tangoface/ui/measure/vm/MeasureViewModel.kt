package com.tangoplus.tangoface.ui.measure.vm

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.tangoplus.tangoface.domain.opencv.HsvColorTracker
import com.tangoplus.tangoface.domain.util.MathHelpers.calculateAngle
import com.tangoplus.tangoface.domain.util.MathHelpers.calculateAreaWithScaling
import com.tangoplus.tangoface.domain.util.MathHelpers.calculatePolygonArea
import com.tangoplus.tangoface.domain.util.MathHelpers.calculateSlope
import com.tangoplus.tangoface.domain.util.MathHelpers.correctingValue
import com.tangoplus.tangoface.domain.util.MathHelpers.get3DDistance
import com.tangoplus.tangoface.domain.util.MathHelpers.getNormalizedDistance
import com.tangoplus.tangoface.domain.util.MathHelpers.normalizeAngle90
import com.tangoplus.tangoface.domain.util.MediaUtility.calculateScreenX
import com.tangoplus.tangoface.domain.util.MediaUtility.calculateScreenY
import com.tangoplus.tangoface.domain.vision.face.FaceLandmarkResult
import com.tangoplus.tangoface.domain.vision.face.FaceLandmarkerHelper
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.orbbec.vo.Point3D
import com.tangoplus.tangoface.ui.view.OverlayView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MeasureViewModel : ViewModel() {
	// 측정할 것들만 가져오기
	var allSeqs = mutableListOf<Sequence>()
	var currentSeq = Sequence.COMFORT

	fun moveToNextSeq(): Boolean {
		val currentIndex = allSeqs.indexOf(currentSeq)
		return if (currentIndex != -1 && currentIndex + 1 < allSeqs.size) {
			currentSeq = allSeqs[currentIndex + 1]
			true
		} else {
			false
		}
	}

	fun moveToPreviousSeq(): Boolean {
		val currentIndex = allSeqs.indexOf(currentSeq)
		return if (currentIndex > 0) {
			currentSeq = allSeqs[currentIndex - 1]
			true
		} else {
			false
		}
	}

	fun getSeqIndex(): Int {
		return allSeqs.indexOf(currentSeq)
	}

	fun isFirstSeq(): Boolean = allSeqs.indexOf(currentSeq) == 0
	fun isLastSeq(): Boolean = allSeqs.indexOf(currentSeq) == allSeqs.size - 1

	// ----------# flag들 #-----------


	// 측정 초기화
	var initMeasure = MutableLiveData(true)

	var currentTarget: List<HsvColorTracker.Target> = listOf()
	val resultTargetFs = JSONArray()
	val hsvColorJo = JSONObject()
	val hsvFLCoordinatesJA = JSONArray() // 484개의 얼굴좌표 담는 곳
	val hsvPLCoordinatesJA = JSONArray()
	var hsvMediaFileName = ""

	// 결과값 임시로 담을 곳 (영상 녹화가 끝나면 초기화)
	var tempStaticJo: JSONObject? = null

	// 중간 임시 저장 좌표들 ( seq 1개씩만 )
	var tempCoordinateJA = JSONArray()
	var videoTempCoordinatesJA = JSONArray()
	var tempHairLineJO = JSONObject()

	// ------------------------# 측정 1개당 값들이 들어가는 곳 전부 index == 6 #------------------------
	// 측정값들 담기
	val staticJA = JSONArray()

	// 파일로 만들기 전 6단계 전부 들어가는 곳
	var coordinatesJA = JSONArray()

	// static 데이터 + 좌표값 통합된 JSON
	val mediaFileNames = mutableListOf<String>()

	// 포즈랜드마크 전부
	val plrJA = JSONArray()

	// 그 외 추가할 것 헤어라인 등
	var hairLineJA = JSONArray()
	var etcJO = JSONObject()

	// 현재 얼굴 좌표들 전부 담는 곳 (형식은 coordinatesJA와 같음)
	var currentCoordinate = mutableListOf<Pair<Float, Float>>() // 절대 좌표
	var relativeCoordinate = mutableListOf<Pair<Float, Float>>() // 상대좌표
	var current3DCoordinate = mutableListOf<Triple<Float, Float, Float>>() // 절대 좌표

	// ---------------------------- # 저장 전 #-------------------------
	val mergedJA = JSONArray()
	var currentUserSn = -1
	var currentUUID = ""

	// -------# pose landmark #-------
	var tempPlrJA = JSONArray()
	val currentPlrCoordinate = mutableListOf<Pair<Float, Float>>()
	fun getCurrentDateTime(): String =
		LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

	var scaleFactorX: Float? = null
	var scaleFactorY: Float? = null

	var inputWidth: Int = 0
	var inputHeight: Int = 0
	var overlayW: Float = 0f
	var overlayH: Float = 0f
	fun initScaleFactor(ol: OverlayView) {
		if (scaleFactorX == null && scaleFactorY == null) {
			if (inputWidth != 0 && inputHeight != 0) {
				scaleFactorX = (ol.width / inputWidth).toFloat()
				scaleFactorY = (ol.height / inputHeight).toFloat()
			} else {
				scaleFactorX = 1f
				scaleFactorY = 1f
			}
		}
	}

	fun initOverlaySize(ol: OverlayView) {
		if (ol.width != 0 && ol.height != 0 && overlayW == 0f && overlayH == 0f) {
			overlayW = ol.width.toFloat()
			overlayH = ol.height.toFloat()
		}
	}

	fun initWidthHeight(resultBundle: FaceLandmarkerHelper.ResultBundle) {
		if (inputWidth == 0 && inputHeight == 0) {
			inputWidth = resultBundle.inputImageWidth
			inputHeight = resultBundle.inputImageHeight
		}
	}

	@UnstableApi
	private fun flipVideo(
		context: Context,
		inputPath: String,
		outputPath: String,
		onSuccess: (File) -> Unit,
		onError: (String) -> Unit
	) {
		// 입력 비디오 URI 생성
		val inputUri = Uri.fromFile(File(inputPath))
		val mediaItem = MediaItem.fromUri(inputUri)

		val flipEffect = ScaleAndRotateTransformation.Builder()
			.setScale(-1f, 1f)
			.build()
		val effects = Effects(
			listOf(ChannelMixingAudioProcessor()),
			listOf(flipEffect)
		)
		val editedMediaItem = EditedMediaItem.Builder(mediaItem)
			.setEffects(effects)
			.build()

		val transformer = Transformer.Builder(context)
			.setVideoMimeType(MimeTypes.VIDEO_H264)
			.addListener(object : Transformer.Listener {
				override fun onCompleted(composition: Composition, exportResult: ExportResult) {
					super.onCompleted(composition, exportResult)
					onSuccess(File(outputPath))
				}

				override fun onError(
					composition: Composition,
					exportResult: ExportResult,
					exportException: ExportException
				) {
					super.onError(composition, exportResult, exportException)
					onError(exportException.message ?: "Unknown error")
				}
			}).build()
		transformer.start(editedMediaItem, outputPath)
	}

	@OptIn(UnstableApi::class)
	fun flipVideoHorizontally(
		context: Context,
		inputPath: String,
		outputPath: String,
		callback: (Boolean) -> Unit
	) {
		CoroutineScope(Dispatchers.Main).launch {
			flipVideo(
				context = context,
				inputPath = inputPath,
				outputPath = outputPath,
				onSuccess = { file ->
					callback(true)
					println("비디오 플립 성공: ${file.path}")
				},
				onError = { errorMessage ->
					callback(false)
					println("비디오 크롭 실패: $errorMessage")
				},
			)
		}
	}


	private val leftCheeks = listOf(197, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152)
	private val rightCheeks = listOf(197, 234, 93, 132, 58, 172, 136, 150, 149, 176, 148, 152)

	//    private val leftCheeks = listOf(197, 34, 227, 137, 177, 215, 138, 135, 169, 170, 140, 171, 175)
//    private val rightCheeks = listOf(197, 264, 447, 366, 401, 435, 367, 364, 394, 395, 369, 396, 175)
	private val leftCheekBone = listOf(346, 347, 330, 425, 411, 352)
	private val rightCheekBone = listOf(117, 118, 101, 205, 187, 123)
	var isDepthCameraMeasure = true

	fun resultBundleToJson(
		resultBundle: FaceLandmarkResult,
		flr3D: List<Point3D> = listOf(),
		seq: Sequence
	) {
		Log.v("3d좌표?", "${flr3D.size}")
		if (resultBundle.landmarks.isNotEmpty()) {
			val flr = resultBundle.landmarks

			/* 1. 키오스크 -> pose좌표까지 좌우 반전 -> 8이 왼쪽 어깨
			*  2. 일치시키기 위해 모바일 pose좌표 -> 동일
			*  3. 실제 기울기를 계산하는 곳만 반대로 들어감 -> 7 이 왼쪽 어깨
			*  4. 이유는 기울기와 거리 계산하는 곳의 모든 인자를 변경해줘야 하기 때문에.
			*  5. 값 계산에서는 왼쪽의 기울기는 second 혹은 1번 index 임.
			*  6. 이를 일치 시키려면? -> mvm에 들어가는 인자들을 거울모드로 변경 -> 값들은 원상복귀
			* */
//            Log.v("현재렌즈위치", "$cameraFacing == 정면${CameraSelector.LENS_FACING_FRONT}, 후면${CameraSelector.LENS_FACING_BACK}")
			// 비우기
			tempCoordinateJA = JSONArray()
			when (currentSeq) {
				Sequence.JAW_LEFT_TILT, Sequence.JAW_RIGHT_TILT -> {
					flr.forEachIndexed { index, faceLandmark ->
						val scaledX = calculateScreenX(
							overlayW,
							if (isDepthCameraMeasure) (1f - faceLandmark.x) else faceLandmark.x
						)
						val scaledY = calculateScreenY(overlayH, faceLandmark.y)
						val x = flr3D.getOrNull(index)?.x ?: 0f
						val y = flr3D.getOrNull(index)?.y ?: 0f
						val z = flr3D.getOrNull(index)?.z ?: 0f

						val ja = JSONArray().apply {
							put(index)
							put(scaledX.toBigDecimal().setScale(1, RoundingMode.HALF_UP))
							put(scaledY.toBigDecimal().setScale(1, RoundingMode.HALF_UP))
							put(x.toBigDecimal().setScale(4, RoundingMode.HALF_UP))
							put(y.toBigDecimal().setScale(4, RoundingMode.HALF_UP))
							put(z.toBigDecimal().setScale(4, RoundingMode.HALF_UP))
						}
						// 이 tempCoordinateJA는 영상 녹화가 종료됐을 때, timer가 끝나는 곳에서 저장후 초기화
						tempCoordinateJA.put(ja)
//                        // 절대좌표
//                        currentCoordinate.add(Pair(scaledX, scaledY))
						// 상대좌표
						relativeCoordinate.add(
							Pair(
								1f - faceLandmark.x,
								faceLandmark.y
							)
						)
					}
					// seq 1개의 좌표를 담고 tempCoordinates는 초기화
					videoTempCoordinatesJA.put(tempCoordinateJA)
				}

				else -> {
					currentCoordinate.clear()
					relativeCoordinate.clear()
					current3DCoordinate.clear()

					flr.forEachIndexed { index, faceLandmark ->
						val scaledX = calculateScreenX(
							overlayW,
							if (isDepthCameraMeasure) (1f - faceLandmark.x) else faceLandmark.x
						)
						val scaledY = calculateScreenY(overlayH, faceLandmark.y)
						val x = flr3D.getOrNull(index)?.x ?: 0f
						val y = flr3D.getOrNull(index)?.y ?: 0f
						val z = flr3D.getOrNull(index)?.z ?: 0f
						if (currentSeq == Sequence.JAW_OPENING && index in listOf(13, 14)) Log.v(
							"xyz보기",
							"$index: ($x $y $z), ${flr3D.size}"
						)
						val ja = JSONArray().apply {
							put(index)
							put(scaledX.toBigDecimal().setScale(1, RoundingMode.HALF_UP))
							put(scaledY.toBigDecimal().setScale(1, RoundingMode.HALF_UP))
							put(x.toBigDecimal().setScale(4, RoundingMode.HALF_UP))
							put(y.toBigDecimal().setScale(4, RoundingMode.HALF_UP))
							put(z.toBigDecimal().setScale(4, RoundingMode.HALF_UP))
						}
						tempCoordinateJA.put(ja)
						// 절대좌표
						currentCoordinate.add(Pair(scaledX, scaledY))
						// 상대좌표
						relativeCoordinate.add(
							Pair(
								faceLandmark.x,
								faceLandmark.y
							)
						)
						// 3d 변환 좌표
						if (isDepthCameraMeasure) {
							current3DCoordinate.add(Triple(x, y, z))
						}
					}
					// seq 1개의 좌표를 담고 tempCoordinates는 초기화
					coordinatesJA.put(tempCoordinateJA)
				}
			}
			// 각도나 여러가지 계산할 때는 거울 모드가 아님. 그렇기 때문에 랜드마크 사진에서 오른쪽에 있는 landmark 265번은 좌측 눈매 끝의 피부 점인거임.
			// 그렇기 때문에 각도 계산할 때 left에다가 second를 넣고, right에 first를 넣음.


			// ---------# 편한 상태 #----------
			val vmFlr = relativeCoordinate
			when (seq) {
				Sequence.COMFORT -> {
					val eyeBrowAngle =
						calculateSlope(vmFlr[107].first, vmFlr[107].second, vmFlr[336].first, vmFlr[336].second)

					// 468: 실제 오른쪽 눈  473: 실제 왼쪽 눈
					val eyeAngle =
						calculateSlope(vmFlr[468].first, vmFlr[468].second, vmFlr[473].first, vmFlr[473].second)

					val correctionValue = 180f - eyeAngle // 180
					// 입술끝 왼: 291 오: 61
					val tipOfLipsAngle =
						calculateSlope(vmFlr[61].first, vmFlr[61].second, vmFlr[291].first, vmFlr[291].second)
					val correctTipOfLipsAngle = correctingValue(tipOfLipsAngle, correctionValue)
					// 턱
					val tipOfChinsAngle =
						calculateSlope(vmFlr[148].first, vmFlr[148].second, vmFlr[377].first, vmFlr[377].second)
					val correctTipOfChinsAngle = correctingValue(tipOfChinsAngle, correctionValue)

					Log.v("각도보기", "$eyeAngle $correctionValue $tipOfLipsAngle -> $correctTipOfLipsAngle")

					val canthusOralAngle = Pair(
						calculateSlope(vmFlr[33].first, vmFlr[33].second, vmFlr[61].first, vmFlr[61].second),
						calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[263].first, vmFlr[263].second)
					)
					val nasalWingLipsAngle = Pair(
						calculateSlope(vmFlr[64].first, vmFlr[64].second, vmFlr[61].first, vmFlr[61].second),
						calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[294].first, vmFlr[294].second)
					)

					val cheeksExtents = if (isDepthCameraMeasure) {
						val leftCheeksPoint = leftCheeks.map { current3DCoordinate[it] }
						val rightCheeksPoint = rightCheeks.map { current3DCoordinate[it] }
						Log.v("뎁스카메라", "5-Cheeks: $leftCheeksPoint, $rightCheeksPoint")
						Pair(
							calculateAreaWithScaling(leftCheeks, currentCoordinate, current3DCoordinate),
							calculateAreaWithScaling(rightCheeks, currentCoordinate, current3DCoordinate)
						)
					} else {
						val leftCheeksPoint = leftCheeks.map { currentCoordinate[it] }
						val rightCheeksPoint = rightCheeks.map { currentCoordinate[it] }
						Log.v("뎁스카메라", "0-Cheeks: $leftCheeksPoint, $rightCheeksPoint")

						Pair(
							calculatePolygonArea(leftCheeksPoint),
							calculatePolygonArea(rightCheeksPoint)
						)
					}

					val sideHeadAngle = Pair(
						calculateAngle(
							vmFlr[156].first,
							vmFlr[156].second,
							vmFlr[143].first,
							vmFlr[143].second,
							vmFlr[111].first,
							vmFlr[111].second
						),
						calculateAngle(
							vmFlr[383].first,
							vmFlr[383].second,
							vmFlr[372].first,
							vmFlr[372].second,
							vmFlr[340].first,
							vmFlr[340].second
						)
					)

					val cheekboneExtents = if (isDepthCameraMeasure) {
						val leftCheekBonePoint = leftCheekBone.map { currentCoordinate[it] }
						val rightCheekBonePoint = rightCheekBone.map { currentCoordinate[it] }
						Log.v("뎁스카메라", "0-CheekBone: $leftCheekBonePoint, $rightCheekBonePoint")
						Pair(
							calculateAreaWithScaling(leftCheekBone, currentCoordinate, current3DCoordinate),
							calculateAreaWithScaling(rightCheekBone, currentCoordinate, current3DCoordinate)
						)
					} else {
						val leftCheekBonePoint = leftCheekBone.map { currentCoordinate[it] }
						val rightCheekBonePoint = rightCheekBone.map { currentCoordinate[it] }
						Log.v("뎁스카메라", "0-CheekBone: $leftCheekBonePoint, $rightCheekBonePoint")
						Pair(
							calculatePolygonArea(leftCheekBonePoint),
							calculatePolygonArea(rightCheekBonePoint)
						)
					}
					Log.v("뎁스카메라", "isSDKInit: ${isDepthCameraMeasure}, $cheekboneExtents")
					val plr = currentPlrCoordinate
					val midShoulder =
						Pair((plr[11].first + plr[12].first) / 2, (plr[11].second + plr[12].second) / 2)

					val neckVerticalAngle =
						calculateSlope(midShoulder.first, midShoulder.second, vmFlr[1].first, vmFlr[1].second)
					val faceVerticalAngle =
						calculateSlope(vmFlr[152].first, vmFlr[152].second, vmFlr[10].first, vmFlr[10].second)

					// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6
					val tempStatic = JSONObject().apply {
						put("comfort_eye_horizontal_angle", eyeAngle)
						put("comfort_eyebrow_horizontal_angle", eyeBrowAngle)
						put("comfort_tip_of_lips_horizontal_angle", correctTipOfLipsAngle)
						put("comfort_tip_of_chin_horizontal_angle", correctTipOfChinsAngle)
						put("comfort_canthus_oral_left_vertical_angle", canthusOralAngle.second)
						put("comfort_canthus_oral_right_vertical_angle", canthusOralAngle.first)
						put("comfort_nasal_wing_tip_of_lips_left_vertical_angle", nasalWingLipsAngle.second)
						put("comfort_nasal_wing_tip_of_lips_right_vertical_angle", nasalWingLipsAngle.first)
						put("comfort_side_head_left_angle", sideHeadAngle.second)
						put("comfort_side_head_right_angle", sideHeadAngle.first)
						put("comfort_left_cheeks_extent", cheeksExtents.second)
						put("comfort_right_cheeks_extent", cheeksExtents.first)
						put("comfort_left_cheekbone_extent", cheekboneExtents.second)
						put("comfort_right_cheekbone_extent", cheekboneExtents.first)
						put("comfort_neck_vertical_angle", neckVerticalAngle)
						put("comfort_face_vertical_angle", faceVerticalAngle)
					}
					staticJA.put(tempStatic)
				}

				Sequence.RESTING -> {
					val eyeBrowAngle =
						calculateSlope(vmFlr[107].first, vmFlr[107].second, vmFlr[336].first, vmFlr[336].second)

					// 468: 실제 오른쪽 눈  473: 실제 왼쪽 눈
					val eyeAngle =
						calculateSlope(vmFlr[468].first, vmFlr[468].second, vmFlr[473].first, vmFlr[473].second)
					val correctionValue = 180f - eyeAngle
					// 입술끝 왼: 291 오: 61
					val tipOfLipsAngle =
						calculateSlope(vmFlr[61].first, vmFlr[61].second, vmFlr[291].first, vmFlr[291].second)
					val correctTipOfLipsAngle = correctingValue(tipOfLipsAngle, correctionValue)
					// 턱
					val tipOfChinsAngle =
						calculateSlope(vmFlr[148].first, vmFlr[148].second, vmFlr[377].first, vmFlr[377].second)
					val correctTipOfChinsAngle = correctingValue(tipOfChinsAngle, correctionValue)

					val canthusOralAngle = Pair(
						calculateSlope(vmFlr[33].first, vmFlr[33].second, vmFlr[61].first, vmFlr[61].second),
						calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[263].first, vmFlr[263].second)
					)
					val nasalWingLipsAngle = Pair(
						calculateSlope(vmFlr[64].first, vmFlr[64].second, vmFlr[61].first, vmFlr[61].second),
						calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[294].first, vmFlr[294].second)
					)

					val cheeksExtents = if (isDepthCameraMeasure) {
						Pair(
							calculateAreaWithScaling(leftCheeks, currentCoordinate, current3DCoordinate),
							calculateAreaWithScaling(rightCheeks, currentCoordinate, current3DCoordinate),
						)
					} else {
						val leftCheeksPoint = leftCheeks.map { currentCoordinate[it] }
						val rightCheeksPoint = rightCheeks.map { currentCoordinate[it] }
						Log.v("뎁스카메라", "1-Cheeks: $leftCheeksPoint, $rightCheeksPoint")
						Pair(
							calculatePolygonArea(leftCheeksPoint),
							calculatePolygonArea(rightCheeksPoint)
						)
					}

					val sideHeadAngle = Pair(
						calculateAngle(
							vmFlr[156].first,
							vmFlr[156].second,
							vmFlr[143].first,
							vmFlr[143].second,
							vmFlr[111].first,
							vmFlr[111].second
						),
						calculateAngle(
							vmFlr[383].first,
							vmFlr[383].second,
							vmFlr[372].first,
							vmFlr[372].second,
							vmFlr[340].first,
							vmFlr[340].second
						)
					)

					val cheekboneExtents = if (isDepthCameraMeasure) {
						Pair(
							calculateAreaWithScaling(leftCheekBone, currentCoordinate, current3DCoordinate),
							calculateAreaWithScaling(rightCheekBone, currentCoordinate, current3DCoordinate)
						)
					} else {
						val leftCheekBonePoint = leftCheekBone.map { currentCoordinate[it] }
						val rightCheekBonePoint = rightCheekBone.map { currentCoordinate[it] }
						Log.v("뎁스카메라", "1-CheekBone: $leftCheekBonePoint, $rightCheekBonePoint")
						Pair(
							calculatePolygonArea(leftCheekBonePoint),
							calculatePolygonArea(rightCheekBonePoint)
						)
					}

					// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6
					val tempStatic = JSONObject().apply {
						put("resting_eye_horizontal_angle", eyeAngle)
						put("resting_eyebrow_horizontal_angle", eyeBrowAngle)
						put("resting_tip_of_lips_horizontal_angle", correctTipOfLipsAngle)
						put("resting_tip_of_chin_horizontal_angle", correctTipOfChinsAngle)
						put("resting_canthus_oral_left_vertical_angle", canthusOralAngle.second)
						put("resting_canthus_oral_right_vertical_angle", canthusOralAngle.first)
						put("resting_nasal_wing_tip_of_lips_left_vertical_angle", nasalWingLipsAngle.second)
						put("resting_nasal_wing_tip_of_lips_right_vertical_angle", nasalWingLipsAngle.first)
						put("resting_side_head_left_angle", sideHeadAngle.second)
						put("resting_side_head_right_angle", sideHeadAngle.first)
						put("resting_left_cheeks_extent", cheeksExtents.second)
						put("resting_right_cheeks_extent", cheeksExtents.first)
						put("resting_left_cheekbone_extent", cheekboneExtents.second)
						put("resting_right_cheekbone_extent", cheekboneExtents.first)
					}
					staticJA.put(tempStatic)
					Log.v(
						"정면 각도들",
						"cheeksExtent: $cheeksExtents, eyeAngle: $eyeAngle eyebrowAngle: $eyeBrowAngle tipOfLipsAngle: $tipOfLipsAngle chinAngle: $correctTipOfChinsAngle canthusOralAngle: $canthusOralAngle nasalWingLipsAngle:$nasalWingLipsAngle, "
					)
//                    Log.v("제이슨", "${}")
				}

				Sequence.OCCLUSAL -> {
					val eyeBrowAngle =
						calculateSlope(vmFlr[107].first, vmFlr[107].second, vmFlr[336].first, vmFlr[336].second)

					// 468: 실제 오른쪽 눈  473: 실제 왼쪽 눈
					val eyeAngle =
						calculateSlope(vmFlr[468].first, vmFlr[468].second, vmFlr[473].first, vmFlr[473].second)
					val correctionValue = 180f - eyeAngle
					// 입술끝 왼: 291 오: 61
					val tipOfLipsAngle =
						calculateSlope(vmFlr[61].first, vmFlr[61].second, vmFlr[291].first, vmFlr[291].second)
					val correctTipOfLipsAngle = correctingValue(tipOfLipsAngle, correctionValue)
					// 턱
					val tipOfChinsAngle =
						calculateSlope(vmFlr[148].first, vmFlr[148].second, vmFlr[377].first, vmFlr[377].second)
					val correctTipOfChinsAngle = correctingValue(tipOfChinsAngle, correctionValue)
					val canthusOralAngle = Pair(
						calculateSlope(vmFlr[33].first, vmFlr[33].second, vmFlr[61].first, vmFlr[61].second),
						calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[263].first, vmFlr[263].second)
					)
					val nasalWingLipsAngle = Pair(
						calculateSlope(vmFlr[64].first, vmFlr[64].second, vmFlr[61].first, vmFlr[61].second),
						calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[294].first, vmFlr[294].second)
					)

					val cheeksExtents = if (isDepthCameraMeasure) {
						Pair(
							calculateAreaWithScaling(leftCheeks, currentCoordinate, current3DCoordinate),
							calculateAreaWithScaling(rightCheeks, currentCoordinate, current3DCoordinate)
						)
					} else {
						val leftCheeksPoint = leftCheeks.map { currentCoordinate[it] }
						val rightCheeksPoint = rightCheeks.map { currentCoordinate[it] }
						Log.v("뎁스카메라", "2-Cheeks: $leftCheeksPoint, $rightCheeksPoint")
						Pair(
							calculatePolygonArea(leftCheeksPoint),
							calculatePolygonArea(rightCheeksPoint)
						)
					}

					val sideHeadAngle = Pair(
						calculateAngle(
							vmFlr[156].first,
							vmFlr[156].second,
							vmFlr[143].first,
							vmFlr[143].second,
							vmFlr[111].first,
							vmFlr[111].second
						),
						calculateAngle(
							vmFlr[383].first,
							vmFlr[383].second,
							vmFlr[372].first,
							vmFlr[372].second,
							vmFlr[340].first,
							vmFlr[340].second
						)
					)

					val cheekboneExtents = if (isDepthCameraMeasure) {
						Pair(
							calculateAreaWithScaling(leftCheekBone, currentCoordinate, current3DCoordinate),
							calculateAreaWithScaling(rightCheekBone, currentCoordinate, current3DCoordinate)
						)
					} else {
						val leftCheekBonePoint = leftCheekBone.map { currentCoordinate[it] }
						val rightCheekBonePoint = rightCheekBone.map { currentCoordinate[it] }
						Log.v("뎁스카메라", "2-CheekBone: $leftCheekBonePoint, $rightCheekBonePoint")
						Pair(
							calculatePolygonArea(leftCheekBonePoint),
							calculatePolygonArea(rightCheekBonePoint)
						)
					}

					// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6
					val tempStatic = JSONObject().apply {
						put("occlusal_eye_horizontal_angle", eyeAngle)
						put("occlusal_eyebrow_horizontal_angle", eyeBrowAngle)
						put("occlusal_tip_of_lips_horizontal_angle", correctTipOfLipsAngle)
						put("occlusal_tip_of_chin_horizontal_angle", correctTipOfChinsAngle)
						put("occlusal_canthus_oral_left_vertical_angle", canthusOralAngle.second)
						put("occlusal_canthus_oral_right_vertical_angle", canthusOralAngle.first)
						put("occlusal_nasal_wing_tip_of_lips_left_vertical_angle", nasalWingLipsAngle.second)
						put("occlusal_nasal_wing_tip_of_lips_right_vertical_angle", nasalWingLipsAngle.first)
						put("occlusal_side_head_left_angle", sideHeadAngle.second)
						put("occlusal_side_head_right_angle", sideHeadAngle.first)
						put("occlusal_left_cheeks_extent", cheeksExtents.second)
						put("occlusal_right_cheeks_extent", cheeksExtents.first)
						put("occlusal_left_cheekbone_extent", cheekboneExtents.second)
						put("occlusal_right_cheekbone_extent", cheekboneExtents.first)
					}
					staticJA.put(tempStatic)
					Log.v(
						"교합 각도들",
						"eyeAngle: $eyeAngle eyebrowAngle: $eyeBrowAngle tipOfLipsAngle: $tipOfLipsAngle chinAngle: $correctTipOfChinsAngle canthusOralAngle: $canthusOralAngle nasalWingLipsAngle:$nasalWingLipsAngle, cheeksExtent: $cheeksExtents"
					)
				}

				Sequence.JAW_LEFT_TILT -> {
					if (tempStaticJo == null) {
						val tiltNoseChinAngle =
							calculateSlope(vmFlr[1].first, vmFlr[1].second, vmFlr[152].first, vmFlr[152].second)
						val tiltTipOfLipsAngle =
							calculateSlope(vmFlr[61].first, vmFlr[61].second, vmFlr[291].first, vmFlr[291].second)
						val mandibularDistance = Pair(
							getNormalizedDistance(
								Pair(vmFlr[13].first, vmFlr[13].second),
								Pair(vmFlr[58].first, vmFlr[58].second)
							),
							getNormalizedDistance(
								Pair(vmFlr[13].first, vmFlr[13].second),
								Pair(vmFlr[288].first, vmFlr[288].second)
							)
						)

						// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6
						val tempStatic = JSONObject().apply {
							put("jaw_left_tilt_nose_chin_vertical_angle", tiltNoseChinAngle)
							put("jaw_left_tilt_tip_of_lips_horizontal_angle", tiltTipOfLipsAngle)
							put("jaw_left_tilt_left_mandibular_distance", mandibularDistance.second)
							put("jaw_left_tilt_right_mandibular_distance", mandibularDistance.first)
						}
						// 측정 값 초기화
						tempStaticJo = tempStatic
						staticJA.put(tempStaticJo)
						Log.v(
							"왼쪽 턱 쏠림 각도들",
							"noseChinAngle: $tiltNoseChinAngle tipOfLipsAngle: $tiltTipOfLipsAngle  mandibularDistance: $mandibularDistance"
						)
					}
				}

				Sequence.JAW_RIGHT_TILT -> {
					if (tempStaticJo == null) {
						val tiltNoseChinAngle =
							calculateSlope(vmFlr[1].first, vmFlr[1].second, vmFlr[152].first, vmFlr[152].second)
						val tiltTipOfLipsAngle =
							calculateSlope(vmFlr[61].first, vmFlr[61].second, vmFlr[291].first, vmFlr[291].second)
						val mandibularDistance = Pair(
							getNormalizedDistance(
								Pair(vmFlr[13].first, vmFlr[13].second),
								Pair(vmFlr[58].first, vmFlr[58].second)
							),
							getNormalizedDistance(
								Pair(vmFlr[13].first, vmFlr[13].second),
								Pair(vmFlr[288].first, vmFlr[288].second)
							)
						)

						// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6
						val tempStatic = JSONObject().apply {
							put("jaw_right_tilt_nose_chin_vertical_angle", tiltNoseChinAngle)
							put("jaw_right_tilt_tip_of_lips_horizontal_angle", tiltTipOfLipsAngle)
							put("jaw_right_tilt_left_mandibular_distance", mandibularDistance.second)
							put("jaw_right_tilt_right_mandibular_distance", mandibularDistance.first)

						}
						tempStaticJo = tempStatic
						staticJA.put(tempStaticJo)
						Log.v(
							"왼쪽 턱 쏠림 각도들",
							"noseChinAngle: $tiltNoseChinAngle tipOfLipsAngle: $tiltTipOfLipsAngle  mandibularDistance: $mandibularDistance"
						)
					}
				}

				Sequence.JAW_OPENING -> {
					val openingLipsDistance = if (isDepthCameraMeasure) {
						Log.v(
							"뎁스카메라",
							"5-OpeningLips: 13-${current3DCoordinate[13]}, 14-${current3DCoordinate[14]}, ${
								get3DDistance(
									current3DCoordinate[13],
									current3DCoordinate[14]
								)
							}"
						)
						get3DDistance(current3DCoordinate[13], current3DCoordinate[14])
					} else {
						getNormalizedDistance(
							Pair(vmFlr[13].first, vmFlr[13].second),
							Pair(vmFlr[14].first, vmFlr[14].second)
						)
					}
					val openingLipsAngle = calculateSlope(
						vmFlr[13].first,
						vmFlr[13].second,
						vmFlr[14].first,
						vmFlr[14].second
					) % 180f

					val cheeksExtents = if (isDepthCameraMeasure) {

						Pair(
							calculateAreaWithScaling(leftCheeks, currentCoordinate, current3DCoordinate),
							calculateAreaWithScaling(rightCheeks, currentCoordinate, current3DCoordinate),
						)
					} else {
						val leftCheeksPoint = leftCheeks.map { currentCoordinate[it] }
						val rightCheeksPoint = rightCheeks.map { currentCoordinate[it] }
						Pair(
							calculatePolygonArea(leftCheeksPoint),
							calculatePolygonArea(rightCheeksPoint)
						)
					}

					// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6
					val tempStatic = JSONObject().apply {
						put("jaw_opening_lips_distance", openingLipsDistance)
						put("jaw_opening_lips_vertical_angle", openingLipsAngle)
						put("jaw_opening_left_cheeks_extent", cheeksExtents.first)
						put("jaw_opening_right_cheeks_extent", cheeksExtents.second)
					}
					staticJA.put(tempStatic)
					Log.v(
						"입 벌림 각도들",
						"openingLipsDistance: $openingLipsDistance openingLipsAngle: $openingLipsAngle cheeksExtents: $cheeksExtents"
					)

				}

				Sequence.NECK_EXTENTION -> {
					val plr = currentPlrCoordinate
					Log.v("plr봐보기", "$plr")
					val shoulderHorizontalAngle =
						calculateSlope(plr[11].first, plr[11].second, plr[12].first, plr[12].second)
					val earHorizontalAngle =
						calculateSlope(plr[7].first, plr[7].second, plr[8].first, plr[8].second)

					val midShoulder =
						Pair((plr[11].first + plr[12].first) / 2, (plr[11].second + plr[12].second) / 2)
					val neckVerticalAngle =
						calculateSlope(midShoulder.first, midShoulder.second, vmFlr[1].first, vmFlr[1].second)
					val faceVerticalAngle =
						calculateSlope(vmFlr[152].first, vmFlr[152].second, vmFlr[10].first, vmFlr[10].second)
					val leftChinAngle = calculateAngle(
						vmFlr[234].first,
						vmFlr[234].second,
						vmFlr[58].first,
						vmFlr[58].second,
						vmFlr[152].first,
						vmFlr[152].second
					)
					val rightChinAngle = calculateAngle(
						vmFlr[454].first,
						vmFlr[454].second,
						vmFlr[288].first,
						vmFlr[288].second,
						vmFlr[152].first,
						vmFlr[152].second
					)
					val middleChinAngle = calculateAngle(
						vmFlr[58].first,
						vmFlr[58].second,
						vmFlr[152].first,
						vmFlr[152].second,
						vmFlr[288].first,
						vmFlr[288].second
					)

					// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6
					val tempStatic = JSONObject().apply {
						put("neck_extention_shoulder_horizontal_angle", shoulderHorizontalAngle)
						put("neck_extention_ear_horizontal_angle", earHorizontalAngle)
						put("neck_extention_neck_vertical_angle", neckVerticalAngle)
						put("neck_extention_face_vertical_angle", faceVerticalAngle)
						put("neck_extention_left_mandibular_chin_angle", leftChinAngle)
						put("neck_extention_right_mandibular_chin_angle", rightChinAngle)
						put("neck_extention_middle_mandibular_chin_angle", middleChinAngle)

					}
					staticJA.put(tempStatic)
					Log.v(
						"고개 젖힘",
						"shoulderHorizontalAngle: $shoulderHorizontalAngle earHorizontalAngle: $earHorizontalAngle  neckVerticalAngle: $neckVerticalAngle"
					)
				}

				Sequence.SIDE_LEFT -> {
					val plr = currentPlrCoordinate

					val noseShoulderAngle = normalizeAngle90(
						calculateSlope(
							plr[0].first,
							plr[0].second,
							plr[11].first,
							plr[11].second
						)
					) % 180
					val earShoulderAngle = normalizeAngle90(
						calculateSlope(
							plr[7].first,
							plr[7].second,
							plr[11].first,
							plr[11].second
						)
					) % 180

					val tempStatic = JSONObject().apply {
						put("side_left_shoulder_nose_vertical_angle", noseShoulderAngle)
						put("side_left_shoulder_ear_vertical_angle", earShoulderAngle)
					}
					staticJA.put(tempStatic)
					Log.v("좌측", "noseShoulderAngle: $noseShoulderAngle earShoulderAngle: $earShoulderAngle")
				}

				Sequence.SIDE_RIGHT -> {
					val plr = currentPlrCoordinate

					val noseShoulderAngle = normalizeAngle90(
						calculateSlope(
							plr[0].first,
							plr[0].second,
							plr[12].first,
							plr[12].second
						)
					) % 180
					val earShoulderAngle = normalizeAngle90(
						calculateSlope(
							plr[8].first,
							plr[8].second,
							plr[12].first,
							plr[12].second
						)
					) % 180

					val tempStatic = JSONObject().apply {
						put("side_right_shoulder_nose_vertical_angle", noseShoulderAngle)
						put("side_right_shoulder_ear_vertical_angle", earShoulderAngle)
					}
					staticJA.put(tempStatic)
					Log.v(
						"우측",
						"staticJACount: ${staticJA.length()}/ noseShoulderAngle: $noseShoulderAngle earShoulderAngle: $earShoulderAngle"
					)
				}

				Sequence.BOW -> {}
			}
		}
	}
}