package com.tangoplus.facebeautyexpert.ui.measure.vm

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.JsonWriter
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.orbbec.obsensor.types.CameraIntrinsic
import com.tangoplus.facebeautyexpert.domain.util.ClassifyUtility.calculateVerticalRatio
import com.tangoplus.facebeautyexpert.domain.util.ClassifyUtility.classifyFaceShape
import com.tangoplus.facebeautyexpert.domain.util.ClassifyUtility.classifyFacialAsymmetry
import com.tangoplus.facebeautyexpert.domain.util.ClassifyUtility.classifyFacialAxis
import com.tangoplus.facebeautyexpert.domain.util.ClassifyUtility.classifySaggingDepth
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.getFileName
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.writeJsonValue
import com.tangoplus.facebeautyexpert.domain.util.HairLineUtility.convertHairlineTo3D
import com.tangoplus.facebeautyexpert.domain.util.HairLineUtility.findHairline
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.calculateScreenX
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.calculateScreenY
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.convertDepthPixelTo3D
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.findNearbyDepth
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.calculateAngle
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.calculateAreaWithScaling
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.calculatePolygonArea
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.calculateSlope
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.correctingValue
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.get3DDistance
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.get3DFloorDistance
import com.tangoplus.facebeautyexpert.domain.util.MeasureUtility
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkResult
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkerHelper
import com.tangoplus.facebeautyexpert.domain.vision.pose.PoseLandmarkerHelper
import com.tangoplus.facebeautyexpert.domain.vo.Sequence
import com.tangoplus.facebeautyexpert.orbbec.vo.Point3D
import com.tangoplus.facebeautyexpert.ui.view.OverlayView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.use
import androidx.core.net.toUri
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.getScaleFactor
import com.tangoplus.facebeautyexpert.domain.util.PreferenceUtility
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkAdapter
import kotlin.math.abs
import kotlin.text.replace

class MeasureViewModel : ViewModel() {
	// 측정할 것들만 가져오기
	var allSeqs = Sequence.entries.toMutableList()
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

	// ----------# flag들 #-----------

	// 🍿🍿🍿🍿🍿 results들 🍿🍿🍿🍿🍿
	var face3DResult: List<Point3D> = listOf()
	var face2DResult: FaceLandmarkResult? = null
	// 🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿
	// detect Face Pose Landmarker
//	var poseLandmarker: PoseLandmarkerHelper? = null
//	var flh: FaceLandmarkerHelper? = null
	// 🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿🍿

	// 측정 초기화
	var initMeasure = MutableLiveData(true)

//	val resultTargetFs = JSONArray()
//	val hsvColorJo = JSONObject()
//	val hsvFLCoordinatesJA = JSONArray() // 484개의 얼굴좌표 담는 곳
//	val hsvPLCoordinatesJA = JSONArray()
//	var hsvMediaFileName = ""

	// 결과값 임시로 담을 곳 (영상 녹화가 끝나면 초기화)
	var tempStaticJo: JSONObject? = null

	// 중간 임시 저장 좌표들 ( seq 1개씩만 )
	var tempCoordinateJA = JSONArray()
	var tempHairLineJA = JSONArray()
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
	val current3DPlrCoordinate = mutableListOf<Triple<Float, Float, Float>>() // 절대 좌표
	val currentHairLine3DCoordinate = mutableListOf<Triple<Float, Float, Float>>()
	val currentHairLine2DCoordinate = mutableListOf<Pair<Float, Float>>()
	// ----------------# 헤어라인 #---------------
	var latestDepthData: ShortArray? = null
	var latestDepthWidth: Int = 0
	var latestDepthHeight: Int = 0
	var latestBitmap: Bitmap? = null
	var latestIntrinsic : CameraIntrinsic? = null
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

	private val leftCheeks = listOf(197, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152)
	private val rightCheeks = listOf(197, 234, 93, 132, 58, 172, 136, 150, 149, 176, 148, 152)

	//    private val leftCheeks = listOf(197, 34, 227, 137, 177, 215, 138, 135, 169, 170, 140, 171, 175)
//    private val rightCheeks = listOf(197, 264, 447, 366, 401, 435, 367, 364, 394, 395, 369, 396, 175)
	private val leftCheekBone = listOf(346, 347, 330, 425, 411, 352)
	private val rightCheekBone = listOf(117, 118, 101, 205, 187, 123)
	var isDepthCameraMeasure = true

	fun resultBundleToJson(
//		resultBundle: FaceLandmarkResult,
//		flr3D: List<Point3D> = listOf(),
		seq: Sequence
	) {
		Log.v("3d좌표?", "${face3DResult.size}")
		if (face2DResult?.landmarks?.isNotEmpty() == true) {
			val flr = face2DResult?.landmarks!!
			Log.v("3d좌표?", "현재seq: ${flr}")

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
			currentCoordinate.clear()
			relativeCoordinate.clear()
			current3DCoordinate.clear()

			flr.forEachIndexed { index, faceLandmark ->
				val scaledX = calculateScreenX(1f - faceLandmark.x)
				val scaledY = calculateScreenY( faceLandmark.y)
				val x = face3DResult.getOrNull(index)?.x ?: 0f
				val y = face3DResult.getOrNull(index)?.y ?: 0f
				val z = face3DResult.getOrNull(index)?.z ?: 0f

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
				current3DCoordinate.add(Triple(x, y, z))
			}
			// seq 1개의 좌표를 담고 tempCoordinates는 초기화
			coordinatesJA.put(tempCoordinateJA)
			// 각도나 여러가지 계산할 때는 거울 모드가 아님. 그렇기 때문에 랜드마크 사진에서 오른쪽에 있는 landmark 265번은 좌측 눈매 끝의 피부 점인거임.
			// 그렇기 때문에 각도 계산할 때 left에다가 second를 넣고, right에 first를 넣음.


			// ---------# 편한 상태 #----------
			val vmFlr = relativeCoordinate
			val flr3D = current3DCoordinate
			if (seq == Sequence.COMFORT) {
					// 🎀🎀🎀🎀🎀 상안부 🎀🎀🎀🎀🎀
					// 468: 실제 오른쪽 눈  473: 실제 왼쪽 눈
				val corneaAngle =
					calculateSlope(vmFlr[468].first, vmFlr[468].second, vmFlr[473].first, vmFlr[473].second)
				val eyeAngles = Pair(
					calculateSlope(vmFlr[133].first, vmFlr[133].second, vmFlr[33].first, vmFlr[33].second),
					calculateSlope(vmFlr[362].first, vmFlr[362].second, vmFlr[263].first, vmFlr[263].second)
				)
				// 왼쪽 내안각, 외안각
				val leftCanthuses = Pair(
					calculateSlope(vmFlr[243].first, vmFlr[243].second, vmFlr[133].first, vmFlr[133].second),
					calculateSlope(vmFlr[33].first, vmFlr[33].second, vmFlr[130].first, vmFlr[130].second)
				)
				val rightCanthuses = Pair(
					calculateSlope(vmFlr[362].first, vmFlr[362].second, vmFlr[463].first, vmFlr[463].second),
					calculateSlope(vmFlr[359].first, vmFlr[359].second, vmFlr[263].first, vmFlr[263].second)
				)

				val leftEyeTopPoint = listOf(160,159,158)
					.map { flr3D[it] }
					.minByOrNull { it.second } ?: flr3D[159]
				val leftEyeBottomPoint = listOf(144,145,153)
					.map { flr3D[it] }
					.maxByOrNull { it.second } ?: flr3D[145]
				val rightEyeTopPoint = listOf(385,386,387)
					.map { flr3D[it] }
					.minByOrNull { it.second } ?: flr3D[386]
				val rightEyeBottomPoint = listOf(380,374,373)
					.map { flr3D[it] }
					.maxByOrNull { it.second } ?: flr3D[374]
				// width x height
				val leftEyeSizes = Pair(
					get3DDistance(flr3D[133], flr3D[33]),
					get3DDistance(leftEyeTopPoint, leftEyeBottomPoint)
				)
				val rightEyeSizes = Pair(
					get3DDistance(flr3D[133], flr3D[33]),
					get3DDistance(rightEyeTopPoint, rightEyeBottomPoint)
				)
				val corneaLengths = Pair(
					get3DDistance(flr3D[469], flr3D[471]),
					get3DDistance(flr3D[474], flr3D[476]),
				)

				val leftEyeBrowTopPoint = listOf(65, 52, 53)
					.map { vmFlr[it] }
					.minByOrNull { it.second } ?: vmFlr[65]
				val rightEyeBrowTopPoint = listOf(295, 282, 283)
					.map { vmFlr[it] }
					.minByOrNull { it.second } ?: vmFlr[295]
				val eyeBrowHorizonAngle =
					calculateSlope(vmFlr[107].first, vmFlr[107].second, vmFlr[336].first, vmFlr[336].second)
				val eyeBrowAngles = Pair(
					calculateSlope(vmFlr[55].first, vmFlr[55].second, leftEyeBrowTopPoint.first, leftEyeBrowTopPoint.second),
					calculateSlope(vmFlr[285].first, vmFlr[285].second, rightEyeBrowTopPoint.first, rightEyeBrowTopPoint.second)
				)
				val leftEyeBrowLengths = Triple(
					get3DDistance(flr3D[55], flr3D[107]),
					get3DDistance(flr3D[52], flr3D[105]),
					get3DDistance(flr3D[70], flr3D[46]),
				)
				val rightEyeBrowLengths = Triple(
					get3DDistance(flr3D[285], flr3D[336]),
					get3DDistance(flr3D[282], flr3D[334]),
					get3DDistance(flr3D[276], flr3D[300]),
				)

				// 🎀🎀🎀🎀🎀 중안부 🎀🎀🎀🎀🎀
				val correctionValue = 180f - corneaAngle // 보정값 180

				val leftAlaStartPoint = listOf(209, 49, 64)
					.map { flr3D[it] }
					.minByOrNull { it.first } ?: flr3D[49]
				val rightAlaEndPoint = listOf(429, 279, 294)
					.map { flr3D[it] }
					.maxByOrNull { it.first } ?: flr3D[279]
				val noseHeight = get3DDistance(flr3D[168], flr3D[2])
				val noseWidth = get3DDistance(leftAlaStartPoint, rightAlaEndPoint)
				val alaLengths = Pair(
					get3DFloorDistance(leftAlaStartPoint, flr3D[2]),
					get3DFloorDistance(flr3D[2], rightAlaEndPoint),
				)
				val earFlapAngle = calculateSlope(vmFlr[61].first, vmFlr[61].second, vmFlr[291].first, vmFlr[291].second)

				val leftAla2DPoint = listOf(209, 49, 64)
					.map { flr[it] }
					.minByOrNull { it.x } ?: flr[49]
				val rightAla2DPoint = listOf(429, 279, 294)
					.map { flr[it] }
					.maxByOrNull { it.x } ?: flr[279]
				val alaAngle = calculateSlope(leftAla2DPoint.x, leftAla2DPoint.y, rightAla2DPoint.x, rightAla2DPoint.y)

				// 🎀🎀🎀🎀🎀 하안부 🎀🎀🎀🎀🎀

				// 입술끝 왼: 291 오: 61
				val tipOfLipsAngle =
					calculateSlope(vmFlr[61].first, vmFlr[61].second, vmFlr[291].first, vmFlr[291].second)
				val correctTipOfLipsAngle = correctingValue(tipOfLipsAngle, correctionValue)
				// 턱
				val tipOfChinsAngle =
					calculateSlope(vmFlr[148].first, vmFlr[148].second, vmFlr[377].first, vmFlr[377].second)
				val correctTipOfChinsAngle = correctingValue(tipOfChinsAngle, correctionValue)

				Log.v("각도보기", "$corneaAngle $correctionValue $tipOfLipsAngle -> $correctTipOfLipsAngle")

				val canthusOralAngle = Pair(
					calculateSlope(vmFlr[33].first, vmFlr[33].second, vmFlr[61].first, vmFlr[61].second),
					calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[263].first, vmFlr[263].second)
				)
				val nasalWingLipsAngle = Pair(
					calculateSlope(vmFlr[64].first, vmFlr[64].second, vmFlr[61].first, vmFlr[61].second),
					calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[294].first, vmFlr[294].second)
				)

				val cheeksExtents = if (isDepthCameraMeasure) {
//					val leftCheeksPoint = leftCheeks.map { current3DCoordinate[it] }
//					val rightCheeksPoint = rightCheeks.map { current3DCoordinate[it] }
//						Log.v("뎁스카메라", "5-Cheeks: $leftCheeksPoint, $rightCheeksPoint")
					Pair(
						calculateAreaWithScaling(leftCheeks, currentCoordinate, current3DCoordinate),
						calculateAreaWithScaling(rightCheeks, currentCoordinate, current3DCoordinate)
					)
				} else {
					val leftCheeksPoint = leftCheeks.map { currentCoordinate[it] }
					val rightCheeksPoint = rightCheeks.map { currentCoordinate[it] }
//						Log.v("뎁스카메라", "0-Cheeks: $leftCheeksPoint, $rightCheeksPoint")

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
//						Log.v("뎁스카메라", "0-CheekBone: $leftCheekBonePoint, $rightCheekBonePoint")
					Pair(
						calculateAreaWithScaling(leftCheekBone, currentCoordinate, current3DCoordinate),
						calculateAreaWithScaling(rightCheekBone, currentCoordinate, current3DCoordinate)
					)
				} else {
					val leftCheekBonePoint = leftCheekBone.map { currentCoordinate[it] }
					val rightCheekBonePoint = rightCheekBone.map { currentCoordinate[it] }
//						Log.v("뎁스카메라", "0-CheekBone: $leftCheekBonePoint, $rightCheekBonePoint")
					Pair(
						calculatePolygonArea(leftCheekBonePoint),
						calculatePolygonArea(rightCheekBonePoint)
					)
				}
				val mouseCornerAngle = Pair(
					calculateSlope(vmFlr[61].first, vmFlr[61].second, vmFlr[13].first, vmFlr[13].second),
					calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[13].first, vmFlr[13].second)
				)
				val philtrumLength = get3DDistance(flr3D[2], flr3D[0])
				val mouseWidth = get3DDistance(flr3D[61], flr3D[291])
				val lipsLengths = Pair(
					get3DDistance(flr3D[0], flr3D[13]),
					get3DDistance(flr3D[14], flr3D[17])
				)
				// 🎀🎀🎀🎀🎀 얼굴 전체 🎀🎀🎀🎀🎀
//					Log.v("뎁스카메라", "isSDKInit: ${isDepthCameraMeasure}, $cheekboneExtents")
				val plr = currentPlrCoordinate
				val plr3D = current3DPlrCoordinate
				val midShoulder =
					Pair((plr[11].first + plr[12].first) / 2, (plr[11].second + plr[12].second) / 2)
				val shoulderHorizon = calculateSlope(plr[11].first,  plr[12].first, plr[11].second , plr[12].second)

				val neckVerticalAngle =
					calculateSlope(midShoulder.first, midShoulder.second, vmFlr[10].first, vmFlr[10].second)
				val faceVerticalAngle =
					calculateSlope(vmFlr[152].first, vmFlr[152].second, vmFlr[10].first, vmFlr[10].second)
				val midShoulder3D =
					Triple((plr3D[11].first + plr3D[12].first) / 2, (plr3D[11].second + plr3D[12].second) / 2, (plr3D[11].third + plr3D[12].third) / 2)
				val neckHeight = get3DFloorDistance(midShoulder3D, flr3D[152])
				val faceHeight = if (isDepthCameraMeasure) get3DFloorDistance(flr3D[152], currentHairLine3DCoordinate[3]) else 0f
//					val neckWidth = get3DDistance(midShoulder3D, flr3D[152])

				val leftIndices = listOf(162, 127, 234, 93, 132, 58)
				val rightIndices = listOf(389, 356, 454, 323, 401, 435)

				val widths = leftIndices.indices.map { i ->
					val left = flr3D[leftIndices[i]]
					val right = flr3D[rightIndices[i]]
					get3DFloorDistance(left, right)
				}

				// 그중 가장 넓은 지점의 거리를 faceWidth로 결정합니다.
				val maxHorizontalWidth = widths.maxOrNull() ?: 0f
				val faceWidth = maxHorizontalWidth
				Log.v("너비들", "face: $faceWidth, $faceHeight | ${neckHeight}")
				val fs = classifyFaceShape(currentCoordinate, currentHairLine3DCoordinate)
				val faceShape = fs.primaryShape
				val faceShapeSub = fs.secondaryShape
				val facialAxisShape = classifyFacialAxis(vmFlr)
				val verticalRatio = calculateVerticalRatio(vmFlr)
				val seggingDepths = classifySaggingDepth(vmFlr)
				val asymmetrys = classifyFacialAsymmetry(vmFlr)
				// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6

				val tempStatic = JSONObject().apply {

					put("vertical_ratio", verticalRatio)
					put("comfort_face_shape", faceShape)
					put("comfort_face_shape_sub", faceShapeSub)
					put("comfort_left_sagging_scale", seggingDepths.first)
					put("comfort_right_sagging_scale", seggingDepths.second)
					put("comfort_length_symmetry_factor", asymmetrys.first)
					put("comfort_angle_symmetry_factor", asymmetrys.second)
					put("comfort_facial_axis_shape", facialAxisShape.curve)
					put("comfort_face_vertical_angle", faceVerticalAngle)
					put("comfort_neck_vertical_angle", neckVerticalAngle)
					put("comfort_shoulder_horizontal_angle", shoulderHorizon)
					put("comfort_face_height", faceHeight)
					put("comfort_face_width", faceWidth)
					put("comfort_neck_height", neckHeight)
					put("comfort_left_mouse_corner_angle", mouseCornerAngle.first)
					put("comfort_right_mouse_corner_angle", mouseCornerAngle.second)
					put("comfort_philtrum_length", philtrumLength)
					put("comfort_mouse_width", mouseWidth)
					put("comfort_upper_lips_height", lipsLengths.first)
					put("comfort_lower_lips_height", lipsLengths.second)

					put("comfort_eye_horizontal_angle", corneaAngle)
					put("comfort_left_eye_horizontal_angle", eyeAngles.first)
					put("comfort_right_eye_horizontal_angle", eyeAngles.second)
					put("comfort_left_medial_canthus_angle", leftCanthuses.first)
					put("comfort_right_medial_canthus_angle", leftCanthuses.second)
					put("comfort_left_lateral_canthus_angle", rightCanthuses.first)
					put("comfort_right_lateral_canthus_angle", rightCanthuses.second)

					put("comfort_left_eye_width", leftEyeSizes.first)
					put("comfort_left_eye_height", leftEyeSizes.second)
					put("comfort_right_eye_width", rightEyeSizes.first)
					put("comfort_right_eye_height", rightEyeSizes.second)
					put("comfort_left_cornea_length", corneaLengths.first)
					put("comfort_right_cornea_length", corneaLengths.second)

					put("comfort_eyebrow_horizontal_angle", eyeBrowHorizonAngle)
					put("comfort_left_eyebrow_horizontal_angle", eyeBrowAngles.first)
					put("comfort_right_eyebrow_horizontal_angle", eyeBrowAngles.second)
					put("comfort_left_eyebrow_start_length", leftEyeBrowLengths.first)
					put("comfort_left_eyebrow_arch_length", leftEyeBrowLengths.second)
					put("comfort_left_eyebrow_end_length", leftEyeBrowLengths.third)
					put("comfort_right_eyebrow_start_length", rightEyeBrowLengths.first)
					put("comfort_right_eyebrow_arch_length", rightEyeBrowLengths.second)
					put("comfort_right_eyebrow_end_length", rightEyeBrowLengths.third)

					put("comfort_nose_width", noseWidth)
					put("comfort_nose_height", noseHeight)
					put("comfort_left_alar_length", alaLengths.first)
					put("comfort_right_alar_length", alaLengths.second)
					put("comfort_earflaps_horizontal_angle", earFlapAngle)
					put("comfort_alar_horizontal_angle", alaAngle)

					put("comfort_tip_of_lips_horizontal_angle", correctTipOfLipsAngle)
					put("comfort_tip_of_chin_horizontal_angle", correctTipOfChinsAngle)
					put("comfort_canthus_oral_left_vertical_angle", canthusOralAngle.first)
					put("comfort_canthus_oral_right_vertical_angle", canthusOralAngle.second)
					put("comfort_alar_tip_of_lips_left_vertical_angle", nasalWingLipsAngle.first)
					put("comfort_alar_tip_of_lips_right_vertical_angle", nasalWingLipsAngle.second)
					put("comfort_side_head_left_angle", sideHeadAngle.first)
					put("comfort_side_head_right_angle", sideHeadAngle.second)
					put("comfort_left_cheeks_extent", cheeksExtents.first)
					put("comfort_right_cheeks_extent", cheeksExtents.second)
					put("comfort_left_cheekbone_extent", cheekboneExtents.first)
					put("comfort_right_cheekbone_extent", cheekboneExtents.second)
				}
				staticJA.put(tempStatic)
				Log.v("스태틱저장", "0: 스태틱 저장 ${staticJA.length()}")
			}


		}

		if (seq == Sequence.NECK_EXTENTION) {
			val plr = currentPlrCoordinate
			Log.v("plr봐보기", "$plr")
			val shoulderHorizontalAngle =
				calculateSlope(plr[11].first, plr[11].second, plr[12].first, plr[12].second)
			val earHorizontalAngle =
				calculateSlope(plr[7].first, plr[7].second, plr[8].first, plr[8].second)

			val midShoulder =
				Pair((plr[11].first + plr[12].first) / 2, (plr[11].second + plr[12].second) / 2)
			val neckVerticalAngle =
				calculateSlope(midShoulder.first, midShoulder.second, plr[0].first, plr[0].second)
			val midEye = Pair((plr[1].first + plr[4].first) / 2, (plr[1].second + plr[4].second) / 2)
			val faceVerticalAngle =
				calculateSlope(midEye.first, midEye.second, plr[0].first, plr[0].second)
//				val leftChinAngle = calculateAngle(
//					vmFlr[234].first,
//					vmFlr[234].second,
//					vmFlr[58].first,
//					vmFlr[58].second,
//					vmFlr[152].first,
//					vmFlr[152].second
//				)
//				val rightChinAngle = calculateAngle(
//					vmFlr[454].first,
//					vmFlr[454].second,
//					vmFlr[288].first,
//					vmFlr[288].second,
//					vmFlr[152].first,
//					vmFlr[152].second
//				)
//				val middleChinAngle = calculateAngle(
//					vmFlr[58].first,
//					vmFlr[58].second,
//					vmFlr[152].first,
//					vmFlr[152].second,
//					vmFlr[288].first,
//					vmFlr[288].second
//				)

			// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6
			val tempStatic = JSONObject().apply {
				put("neck_extention_shoulder_horizontal_angle", shoulderHorizontalAngle)
				put("neck_extention_ear_horizontal_angle", earHorizontalAngle)
				put("neck_extention_neck_vertical_angle", neckVerticalAngle)
				put("neck_extention_face_vertical_angle", faceVerticalAngle)
//					put("neck_extention_left_mandibular_chin_angle", leftChinAngle)
//					put("neck_extention_right_mandibular_chin_angle", rightChinAngle)
//					put("neck_extention_middle_mandibular_chin_angle", middleChinAngle)
			}
			staticJA.put(tempStatic)
			Log.v(
				"고개 젖힘",
				"shoulderHorizontalAngle: $shoulderHorizontalAngle earHorizontalAngle: $earHorizontalAngle  neckVerticalAngle: $neckVerticalAngle"
			)
			Log.v("스태틱저장", "1: 스태틱 저장 ${staticJA.length()}")
		}

	}



	// 🔶🔶🔶🔶🔶🔶🔶🔶🔶🔶🔶🔶🔶 json, media 파일 저장 🔶🔶🔶🔶🔶🔶🔶🔶🔶🔶🔶🔶🔶
	fun saveJsonToStorage(activity: AppCompatActivity, inputJson: JSONObject, fileName: String): String? {

		MeasureUtility.show(activity)
		val resolver = activity.contentResolver
		val contentValues = ContentValues().apply {
			put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.json")
			put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
			put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Documents/TangoPlus")
		}

		return try {
			val uri: Uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
				?: run {
					Log.e("JSON_SAVE", "insert returned null uri")
					return null
				}

			resolver.openOutputStream(uri)?.use { os ->
				OutputStreamWriter(os, Charsets.UTF_8).use { osw ->
					JsonWriter(osw).use { w ->
						// 보기 좋게 들여쓰기(원치 않으면 제거)
						w.setIndent("  ")
						// ✅ JSONObject/JSONArray를 toString() 없이 직접 기록
						writeJsonValue(w, inputJson)
					}
				}
			} ?: run {
				Log.e("JSON_SAVE", "openOutputStream returned null")
				return null
			}

			Log.d("JSON_SAVE", "saved(JsonWriter): $fileName.json, uri=$uri")
			MeasureUtility.dismiss(activity)
			"$fileName.json"
		} catch (e: Exception) {
			Log.e("JSON_SAVE", "save failed(JsonWriter)", e)
			null
		}
	}

	suspend fun saveMediaToStorage(
		context: Context,
		uri: Uri,
		fileName: String,
		flh: FaceLandmarkerHelper,
		poseLandmarker: PoseLandmarkerHelper): Boolean {
		return withContext(Dispatchers.IO) {
			try {
				val extension = ".webp"
//			val file = File(context.cacheDir, "$fileName$extension")

				val contentValues = ContentValues().apply {
					put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName$extension")
					put(MediaStore.MediaColumns.MIME_TYPE, "image/webp")
					put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TangoPlus")
				}
				val inputStream = context.contentResolver.openInputStream(uri)
				val tempFile = File.createTempFile("tempImage", null, context.cacheDir)
				inputStream?.use { input ->
					tempFile.outputStream().use { output ->
						input.copyTo(output)
					}
				}

				// EXIF 데이터 읽기
				val exif = ExifInterface(tempFile.absolutePath)
				val orientation =
					exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
//                Log.d("ExifDebug", "Exif Orientation: $orientation")
				// 비트맵 디코딩
				val options = BitmapFactory.Options().apply {
					inJustDecodeBounds = true
				}
				BitmapFactory.decodeFile(tempFile.absolutePath, options)

				val sourceWidth = options.outWidth
				val sourceHeight = options.outHeight

				val targetWidth = 1920
				val targetHeight = 1080

				// 이미지 스케일 계산
				val widthRatio = sourceWidth.toFloat() / targetWidth
				val heightRatio = sourceHeight.toFloat() / targetHeight
				val scale = maxOf(widthRatio, heightRatio)

				options.inJustDecodeBounds = false
				options.inSampleSize = scale.toInt()

				var bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, options)
				val matrix = Matrix()

				// 회전 적용
				when (orientation) {
					ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)   // 시계 방향 90도
					ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f) // 시계 방향 180도
					ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f) // 시계 방향 270도
				}

				// ★★★ 좌우반전 ★★★
//					matrix.postScale(1f, 1f, bitmap.width / 2f, bitmap.height / 2f)

				// 모든 변환을 한번에 적용
				bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

				// 스케일 조정
				bitmap.scale(targetWidth, targetHeight).also { bitmap = it }

				// Pictures/TangoBeauty에 저장
				val imageUri = context.contentResolver.insert(
					MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
					contentValues
				)
				latestBitmap = bitmap
				// --------------------------# poselandmark를 잡기 위한 detectAsync #-------------------------
				// ⚠️⚠️⚠️⚠️⚠️⚠️ MeasureImage일 때만 flh image로 init 한다음 detectImage하기  ⚠️⚠️⚠️⚠️⚠️⚠️
				if (!isDepthCameraMeasure) {
					val flResultBundle = flh.detectImage(bitmap)
					face2DResult = flResultBundle?.result?.let { FaceLandmarkAdapter.toCustomFaceLandmarkResult(it) }
					Log.v("2d얼굴들어감", "$face2DResult")
				}

				if (currentSeq == Sequence.COMFORT) {
					if (face2DResult == null) {
						Log.w("saveMedia", "얼굴 랜드마크 검출 실패. 저장을 중단합니다.")
						return@withContext false // ❌ 중단
					}
					val absoluteLandmarks = face2DResult!!.landmarks.map { landmark ->
						FaceLandmarkResult.FaceLandmark(
							x = calculateScreenX(landmark.x),
							y = calculateScreenY(landmark.y)
						)
					}
					val absoluteResult = FaceLandmarkResult(absoluteLandmarks)
					val hairResult = findHairline(bitmap, absoluteResult)
					if (hairResult == null || hairResult.hairlinePoints.isEmpty()) {
						Log.w("saveMedia", "헤어라인 검출 실패. 빈 배열 생성을 막고 저장을 중단합니다.")
						return@withContext false
					}
					// 1. 깊이 데이터가 유효한지 안전하게 검사
					val isDepthValid = latestDepthData != null && latestDepthWidth > 0 && latestDepthHeight > 0 && latestIntrinsic != null

					if (isDepthValid) {
						val hairline3D = convertHairlineTo3D(
							hairlinePoints = hairResult.hairlinePoints,
							depthData      = latestDepthData!!,
							depthWidth     = latestDepthWidth,
							depthHeight    = latestDepthHeight,
							intrinsic      = latestIntrinsic!!,
						)

						hairline3D.forEach { hp ->
							val pointJA = JSONArray().apply {
								put(hp.landmarkIndex)
								put(hp.point2D.x.toBigDecimal().setScale(1, RoundingMode.HALF_UP))
								put(hp.point2D.y.toBigDecimal().setScale(1, RoundingMode.HALF_UP))
								put(hp.point3D.x.toBigDecimal().setScale(4, RoundingMode.HALF_UP))
								put(hp.point3D.y.toBigDecimal().setScale(4, RoundingMode.HALF_UP))
								put(hp.point3D.z.toBigDecimal().setScale(4, RoundingMode.HALF_UP))
							}
							tempHairLineJA.put(pointJA)
							currentHairLine3DCoordinate.add(Triple(hp.point3D.x, hp.point3D.y, hp.point3D.z))
						}
					} else {
						// 깊이 데이터가 없을 경우 z값을 0f로 채우는 기존 로직
						hairResult.hairlinePoints.forEachIndexed { index, hp2d ->
							val pointJA = JSONArray().apply {
								put(index)
								put(hp2d.point.x.toBigDecimal().setScale(1, RoundingMode.HALF_UP))
								put(hp2d.point.y.toBigDecimal().setScale(1, RoundingMode.HALF_UP))
								put(0f.toBigDecimal().setScale(4, RoundingMode.HALF_UP))
								put(0f.toBigDecimal().setScale(4, RoundingMode.HALF_UP))
								put(0f.toBigDecimal().setScale(4, RoundingMode.HALF_UP))
							}
							tempHairLineJA.put(pointJA)
							currentHairLine2DCoordinate.add(Pair(hp2d.point.x, hp2d.point.y))
						}
					}

					hairLineJA.put(tempHairLineJA)
					Log.v("mvmhairLineJA", "${hairLineJA}")
					tempHairLineJA = JSONArray()
				}

				// --------------------------# poselandmark를 잡기 위한 detectAsync #-------------------------
				val resultBundle = poseLandmarker.detectImage(bitmap)
				if (resultBundle?.results?.first()?.landmarks()?.isNotEmpty() == true) {
					val plr = resultBundle.results.first().landmarks()[0]

					tempPlrJA = JSONArray()
					plr.forEachIndexed { index, _ ->
						val swapIndex = if (index >= 7 && index % 2 == 0) index - 1
						else if (index >= 7) index + 1
						else index
						val targetLandmark = plr[swapIndex]

						val scaledX = calculateScreenX(1 - targetLandmark.x())
						val scaledY = calculateScreenY(targetLandmark.y())

						// pose 랜드마크 3D 변환
						val point3D = if (latestDepthWidth <= 0 || latestDepthHeight <= 0) {
							Point3D(0f, 0f, 0f)
							// point3D를 사용하는 이후 로직 수행
						} else {
							// 2. 유효한 경우에만 기존 변환 로직을 실행합니다.
							val px = (targetLandmark.x() * latestDepthWidth).toInt().coerceIn(0, latestDepthWidth - 1)
							val py = (targetLandmark.y() * latestDepthHeight).toInt().coerceIn(0, latestDepthHeight - 1)
							val idx = py * latestDepthWidth + px
							val depthMm = latestDepthData?.get(idx)?.toInt()?.and(0xFFFF) ?: 0
							val depthM = depthMm / 1000f

							if (depthM > 0) {
								convertDepthPixelTo3D(latestIntrinsic!!, px.toFloat(), py.toFloat(), depthM)
							} else {
								val nearbyDepth = findNearbyDepth(latestDepthData!!, px, py, latestDepthWidth, latestDepthHeight)
								if (nearbyDepth > 0) convertDepthPixelTo3D(latestIntrinsic!!, px.toFloat(), py.toFloat(), nearbyDepth / 1000f)
								else Point3D(0f, 0f, 0f)
							}
						}

						val ja = JSONArray().apply {
							put(index)
							put(scaledX.toBigDecimal().setScale(1, RoundingMode.HALF_UP))
							put(scaledY.toBigDecimal().setScale(1, RoundingMode.HALF_UP))
							put(point3D.x.toBigDecimal().setScale(4, RoundingMode.HALF_UP))
							put(point3D.y.toBigDecimal().setScale(4, RoundingMode.HALF_UP))
							put(point3D.z.toBigDecimal().setScale(4, RoundingMode.HALF_UP))
						}
						tempPlrJA.put(ja)
						currentPlrCoordinate.add(Pair(targetLandmark.x(), targetLandmark.y()))
						current3DPlrCoordinate.add(Triple(point3D.x, point3D.y, point3D.z))
					}
					plrJA.put(tempPlrJA)
				} else {
					withContext(Dispatchers.Main) {
						Toast.makeText(context, "poselandmark를 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
					}
					Log.e("saveMedia", "poselandmark 검출 실패. 저장을 중단합니다.")
					return@withContext false // ❌ 중단
				}
				// -------------------------# 목젖 위치를 잡기 위한 detectAsync 끝 #-----------------------
				imageUri?.let { imageURI ->
					context.contentResolver.openOutputStream(imageURI)?.use { outputStream ->
						bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 100, outputStream)
						outputStream.flush()
					}
					Log.d("SaveMedia", "Image saved to Pictures: ${currentSeq} ->  $fileName$extension")
				} ?: run {
					Log.e("SaveMedia", "Failed to create image URI")
				}
				mediaFileNames.add("$fileName$extension")

				Log.d("SaveMedia", "seqStep: ${currentSeq} vmFileName: ${mediaFileNames}")

				// 임시 파일과 비트맵 정리
				tempFile.delete()
				bitmap.recycle()
				return@withContext true
			} catch (e: IndexOutOfBoundsException) {
				Log.e("SaveMediaIndex", "${e.message}")
				return@withContext false
			} catch (e: IllegalArgumentException) {
				Log.e("SaveMedia", "IllegalArgument: ${e.printStackTrace()}")
				return@withContext false
			} catch (e: IllegalStateException) {
				Log.e("SaveMedia", "IllegalState: ${e.printStackTrace()}")
				return@withContext false
			} catch (e: NullPointerException) {
				Log.e("SaveMediaNull", "${e.printStackTrace()}")
				return@withContext false
			} catch (e: java.lang.Exception) {
				Log.e("SaveMediaException", "${e.printStackTrace()}")
				return@withContext false
			}
		}
	}

	suspend fun scannedImage(
		activity: AppCompatActivity,
		imagePaths : List<String?>,
		seq: Sequence,
		flh: FaceLandmarkerHelper,
		plh: PoseLandmarkerHelper
	): Boolean {
		// 1. 파일명 생성
		val name = getFileName(seq)
		val contentResolver = activity.contentResolver
		val imagePath = imagePaths.getOrNull(seq.ordinal)

		if (imagePath.isNullOrEmpty()) {
			Log.e("captureImage", "❌ 해당 순번($seq)에 저장된 이미지 경로가 없습니다.")
			return false
		}

		return withContext(Dispatchers.IO) {
			try {
				// 3. (핵심 수정) 경로 타입에 따라 ImageDecoder.Source 다르게 생성
				val source = if (imagePath.startsWith("content://")) {
					ImageDecoder.createSource(contentResolver, Uri.parse(imagePath))
				} else {
					// 내부/외부 저장소의 실제 절대 경로인 경우 File 객체로 직접 접근
					ImageDecoder.createSource(File(imagePath))
				}

				val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
					decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
				}

				// 4. 임시 파일 생성 및 저장
				val tempFile = File.createTempFile("captured_process", ".webp", activity.cacheDir)
				FileOutputStream(tempFile).use { fos ->
					bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 100, fos)
				}

				// 5. 최종 저장 로직 호출
				val tempUri = Uri.fromFile(tempFile)
				val success = saveMediaToStorage(activity, tempUri, name, flh, plh)

				Log.d("scannedImage", "✅ 이미지 저장 완료?: $success: $name")
				success
			} catch (e: Exception) {
				Log.e("scannedImage", "❌ 이미지 로드/저장 실패: ${e.message}", e)
				false
			}
		}
	}

	fun setJo(originJo: JSONObject, seq: Sequence, ivm: InputViewModel, prefsUtil: PreferenceUtility, isDepthResult: Boolean): JSONObject {
		val seqInt = when (seq) {
			Sequence.COMFORT -> 0
			Sequence.NECK_EXTENTION -> 6
		}
		val seqIndex = allSeqs.indexOf(seq)
		val mediaFileName = mediaFileNames[seqIndex]

		return originJo.apply {
			put("temp_server_sn", prefsUtil.getLastTempServerSn())
			put("media_file_name", mediaFileName)
			put("json_file_name", mediaFileName.replace(".webp", ".json").replace(".mp4", ".json"))
			put("seq", seqInt)
			put("uuid", currentUUID)
			put("name", ivm.nameValue.value)
			put("mobile", ivm.mobileValue.value)
			put("birthday", ivm.birthdayValue.value)
			put("gender", ivm.genderValue.value!!)
			put("nationality", ivm.nationalityValue.value)


			put("scale_factor", getScaleFactor())
			put("depth_result", isDepthResult)
		}
	}
}