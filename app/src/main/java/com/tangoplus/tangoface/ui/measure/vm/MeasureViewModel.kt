package com.tangoplus.tangoface.ui.measure.vm

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.JsonWriter
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.video.Recording
import androidx.compose.runtime.key
import androidx.core.graphics.scale
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tangoplus.tangoface.domain.util.ClassifyUtility.calculateVerticalRatio
import com.tangoplus.tangoface.domain.util.ClassifyUtility.classifyFaceShape
import com.tangoplus.tangoface.domain.util.ClassifyUtility.classifyFacialAsymmetry
import com.tangoplus.tangoface.domain.util.ClassifyUtility.classifyFacialAxis
import com.tangoplus.tangoface.domain.util.ClassifyUtility.classifySaggingDepth
import com.tangoplus.tangoface.domain.util.FileUtility.getFileName
import com.tangoplus.tangoface.domain.util.FileUtility.writeJsonValue
import com.tangoplus.tangoface.domain.util.HairLineUtility.convertHairlineTo3D
import com.tangoplus.tangoface.domain.util.HairLineUtility.findHairline
import com.tangoplus.tangoface.domain.util.LandmarkUtility.calculateScreenX
import com.tangoplus.tangoface.domain.util.LandmarkUtility.calculateScreenY
import com.tangoplus.tangoface.domain.util.LandmarkUtility.convertDepthPixelTo3D
import com.tangoplus.tangoface.domain.util.LandmarkUtility.findNearbyDepth
import com.tangoplus.tangoface.domain.util.MathHelpers.calculateAngle
import com.tangoplus.tangoface.domain.util.MathHelpers.calculateAreaWithScaling
import com.tangoplus.tangoface.domain.util.MathHelpers.calculatePolygonArea
import com.tangoplus.tangoface.domain.util.MathHelpers.calculateSlope
import com.tangoplus.tangoface.domain.util.MathHelpers.correctingValue
import com.tangoplus.tangoface.domain.util.MathHelpers.get3DDistance
import com.tangoplus.tangoface.domain.util.MathHelpers.get3DFloorDistance
import com.tangoplus.tangoface.domain.vision.face.FaceLandmarkResult
import com.tangoplus.tangoface.domain.vision.face.FaceLandmarkerHelper
import com.tangoplus.tangoface.domain.vision.pose.PoseLandmarkerHelper
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.orbbec.vo.Point3D
import com.tangoplus.tangoface.ui.view.OverlayView
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
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
import com.orbbec.obsensor.DepthFrame
import com.orbbec.obsensor.Device
import com.orbbec.obsensor.Frame
import com.orbbec.obsensor.HoleFillingFilter
import com.orbbec.obsensor.Pipeline
import com.orbbec.obsensor.types.CameraDistortion
import com.orbbec.obsensor.types.FrameType
import com.tangoplus.tangoface.domain.opencv.HsvColorTracker
import com.tangoplus.tangoface.domain.util.BitmapUtility.faceOutLineIndexes
import com.tangoplus.tangoface.domain.util.FileUtility.getMoireFileName
import com.tangoplus.tangoface.domain.util.LandmarkUtility.buildFaceMaskPathFromPoints
import com.tangoplus.tangoface.domain.util.LandmarkUtility.colorFrameToBitmapSafe
import com.tangoplus.tangoface.domain.util.LandmarkUtility.convertFaceLandmarksTo3D
import com.tangoplus.tangoface.domain.util.LandmarkUtility.generateGradientLayer
import com.tangoplus.tangoface.domain.util.LandmarkUtility.toRotated90ScaledIntrinsic
import com.tangoplus.tangoface.domain.util.LandmarkUtility.toScaledIntrinsic
import com.tangoplus.tangoface.domain.util.LandmarkUtility.undistortLandmarks
import com.tangoplus.tangoface.domain.util.MathHelpers.getNormalizedDistance
import com.tangoplus.tangoface.domain.util.MathHelpers.getScaleFactor
import com.tangoplus.tangoface.domain.util.MathHelpers.safeRound
import com.tangoplus.tangoface.domain.util.MathHelpers.toFloatTarget
import com.tangoplus.tangoface.domain.util.MeasureUtility.shiftDepthUp
import com.tangoplus.tangoface.domain.util.PreferenceUtility
import com.tangoplus.tangoface.domain.util.copyFrom
import com.tangoplus.tangoface.domain.vision.face.FaceLandmarkAdapter
import com.tangoplus.tangoface.domain.vo.MeasureType
import com.tangoplus.tangoface.orbbec.vo.ScaledIntrinsic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.collections.listOf
import kotlin.io.use
import kotlin.math.roundToInt
import kotlin.ranges.contains
import kotlin.text.replace

class MeasureViewModel : ViewModel() {
	private val allSeqMap = mapOf(
		MeasureType.NORMAL to listOf(Sequence.COMFORT, Sequence.NECK_EXTENTION),
		MeasureType.EXPERT to Sequence.entries.filter { it != Sequence.BOW },
		MeasureType.BOW to listOf(Sequence.BOW),
		MeasureType.IMAGE to listOf(Sequence.COMFORT)
	)
	private val progressSeqMap = mapOf(
		MeasureType.NORMAL to listOf(Sequence.COMFORT, Sequence.NECK_EXTENTION),
		MeasureType.EXPERT to Sequence.entries.filter { it != Sequence.BOW && it != Sequence.JAW_OPENING && it != Sequence.OCCLUSAL },
		MeasureType.BOW to listOf(Sequence.BOW),
		MeasureType.IMAGE to listOf(Sequence.COMFORT),
	)
	var measureType: MeasureType? = null

	val allSeqs: List<Sequence>
		get() = allSeqMap[measureType] ?: emptyList()
	val progressSeqs: List<Sequence>
		get() = progressSeqMap[measureType] ?: emptyList()
	var currentSeq = Sequence.COMFORT

	fun moveToNextSeq(): Boolean {
		val currentIndex = progressSeqs.indexOf(currentSeq)
		Log.v("progressSeqs", "$progressSeqs")
		return if (currentIndex != -1 && currentIndex + 1 < progressSeqs.size) {
			currentSeq = progressSeqs[currentIndex + 1]
			true
		} else {
			false
		}
	}

	fun moveToPreviousSeq(): Boolean {
		val currentIndex = progressSeqs.indexOf(currentSeq)
		return if (currentIndex > 0) {
			currentSeq = progressSeqs[currentIndex - 1]
			true
		} else {
			false
		}
	}

	fun getSeqIndex(): Int {
		return progressSeqs.indexOf(currentSeq)
	}

	// ----------# flag들 #-----------

	// 🍿🍿🍿🍿🍿 results들 🍿🍿🍿🍿🍿
	var face3DResult: List<Point3D> = listOf()
	var face2DResult: FaceLandmarkResult? = null
	// 🍿🍿🍿🍿🍿  🍿🍿🍿🍿🍿


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
	// 측정값들 담기 🪷🪷🪷entity column datas 🪷🪷🪷
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
	// ----------------# 보우 #-----------------
	var currentTarget: List<HsvColorTracker.Target> = listOf() // 페이스 보우의 3가지 빨간 점이 tracking되는 상황
	val resultTargetFs = JSONArray()
//	val hsvColorJo = JSONObject()
//	val hsvFLCoordinatesJA = JSONArray() // 484개의 얼굴좌표 담는 곳
//

	var videoTempCoordinatesJA = JSONArray()
	// ----------------# 헤어라인 #---------------
	var latestDepthData: ShortArray? = null
	var latestDepthWidth: Int = 0
	var latestDepthHeight: Int = 0
	var latestBitmap: Bitmap? = null
	var latestIntrinsic : ScaledIntrinsic? = null
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
	var isOcclusalSaved = false

	fun resultBundleToJson(
		seq: Sequence,
	) {
		Log.v("seq확인", "$seq => if문 들어갈 수 있나요?: ${face2DResult?.landmarks?.size}")
		if (face2DResult?.landmarks?.isNullOrEmpty() == true) return

		val flr = face2DResult?.landmarks!!
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
		when (seq) {
			Sequence.JAW_LEFT_TILT, Sequence.JAW_RIGHT_TILT -> {
				flr.forEachIndexed { index, faceLandmark ->
					val scaledX = calculateScreenX(1f - faceLandmark.x)
					val scaledY = calculateScreenY(faceLandmark.y)
					val x = (face3DResult.getOrNull(index)?.x ?: 0f).safe()
					val y = (face3DResult.getOrNull(index)?.y ?: 0f).safe()
					val z = (face3DResult.getOrNull(index)?.z ?: 0f).safe()

					val roundedX = safeRound(scaledX, 1)
					val roundedY = safeRound(scaledY, 1)
					val rounded3DX = safeRound(x, 4)
					val rounded3DY = safeRound(y, 4)
					val rounded3DZ = safeRound(z, 4)

					val ja = JSONArray().apply {
						put(roundedX)
						put(roundedY)
						put(rounded3DX)
						put(rounded3DY)
						put(rounded3DZ)
					}
					tempCoordinateJA.put(ja)

					// 상대좌표
					relativeCoordinate.add(Pair(1f - faceLandmark.x, faceLandmark.y))
				}
				// seq 1개의 좌표를 담고 tempCoordinates는 초기화
				videoTempCoordinatesJA.put(tempCoordinateJA)
			}

			else -> {
				currentCoordinate.clear()
				relativeCoordinate.clear()
				current3DCoordinate.clear()

				flr.forEachIndexed { index, faceLandmark ->
					val scaledX = calculateScreenX(1f - faceLandmark.x)
					val scaledY = calculateScreenY(faceLandmark.y)

					val x = (face3DResult.getOrNull(index)?.x ?: 0f).safe()
					val y = (face3DResult.getOrNull(index)?.y ?: 0f).safe()
					val z = (face3DResult.getOrNull(index)?.z ?: 0f).safe()

					if (currentSeq == Sequence.JAW_OPENING && index in listOf(13, 14)) {
						Log.v("xyz보기", "$index: ($x $y $z), ${face3DResult.size}")
					}

					// 이미 NaN이 0f로 정제되었으므로 안전하게 반올림 연산 수행
					val roundedX = safeRound(scaledX, 1)
					val roundedY = safeRound(scaledY, 1)
					val rounded3DX = safeRound(x, 4)
					val rounded3DY = safeRound(y, 4)
					val rounded3DZ = safeRound(z, 4)

					val ja = JSONArray().apply {
						put(roundedX)
						put(roundedY)
						put(rounded3DX)
						put(rounded3DY)
						put(rounded3DZ)
					}
					tempCoordinateJA.put(ja)

					// 절대좌표
					currentCoordinate.add(Pair(scaledX, scaledY))
					// 상대좌표
					relativeCoordinate.add(Pair(1f - faceLandmark.x, faceLandmark.y))

					// 💡 이제 안전하게 정제된 Float 값(x, y, z)이 들어가므로,
					// 이를 활용한 모든 2차 연산(거리, 면적 등)에서 NaN이 완전히 박멸됩니다.
					if (isDepthCameraMeasure) {
						current3DCoordinate.add(Triple(x, y, z))
					}
				}
				// seq 1개의 좌표를 담고 tempCoordinates는 초기화
				coordinatesJA.put(tempCoordinateJA)
				Log.v("좌표들", "$seq => ${coordinatesJA.length()}")
			}
		}

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

			val upperEyelid3D =  if (isDepthCameraMeasure) {
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

				JSONObject().apply {
					put("comfort_left_eye_width", leftEyeSizes.first)
					put("comfort_left_eye_height", leftEyeSizes.second)
					put("comfort_right_eye_width", rightEyeSizes.first)
					put("comfort_right_eye_height", rightEyeSizes.second)
					put("comfort_left_cornea_length", corneaLengths.first)
					put("comfort_right_cornea_length", corneaLengths.second)


					put("comfort_left_eyebrow_start_length", leftEyeBrowLengths.first)
					put("comfort_left_eyebrow_arch_length", leftEyeBrowLengths.second)
					put("comfort_left_eyebrow_end_length", leftEyeBrowLengths.third)
					put("comfort_right_eyebrow_start_length", rightEyeBrowLengths.first)
					put("comfort_right_eyebrow_arch_length", rightEyeBrowLengths.second)
					put("comfort_right_eyebrow_end_length", rightEyeBrowLengths.third)
				}
			} else JSONObject()

			// 🎀🎀🎀🎀🎀 중안부 🎀🎀🎀🎀🎀
			val correctionValue = 180f - corneaAngle // 보정값 180
			val leftAla2DPoint = listOf(209, 49, 64)
				.map { flr[it] }
				.minByOrNull { it.x } ?: flr[49]
			val rightAla2DPoint = listOf(429, 279, 294)
				.map { flr[it] }
				.maxByOrNull { it.x } ?: flr[279]
			val alaAngle = calculateSlope(leftAla2DPoint.x, leftAla2DPoint.y, rightAla2DPoint.x, rightAla2DPoint.y)

			val midFace3D = if (isDepthCameraMeasure) {
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


				JSONObject().apply {
					put("comfort_nose_width", noseWidth)
					put("comfort_nose_height", noseHeight)
					put("comfort_left_alar_length", alaLengths.first)
					put("comfort_right_alar_length", alaLengths.second)

				}
			} else JSONObject()
			val earFlapAngle = calculateSlope(vmFlr[61].first, vmFlr[61].second, vmFlr[291].first, vmFlr[291].second)

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
				Pair(
					calculateAreaWithScaling(leftCheeks, currentCoordinate, current3DCoordinate),
					calculateAreaWithScaling(rightCheeks, currentCoordinate, current3DCoordinate)
				)
			} else {
				val leftCheeksPoint = leftCheeks.map { currentCoordinate[it] }
				val rightCheeksPoint = rightCheeks.map { currentCoordinate[it] }
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

			val lowerLips3D =  if (isDepthCameraMeasure) {
				val philtrumLength = get3DDistance(flr3D[2], flr3D[0])
				val mouseWidth = get3DDistance(flr3D[61], flr3D[291])
				val lipsLengths = Pair(
					get3DDistance(flr3D[0], flr3D[13]),
					get3DDistance(flr3D[14], flr3D[17])
				)

				JSONObject().apply {
					put("comfort_philtrum_length", philtrumLength)
					put("comfort_mouse_width", mouseWidth)
					put("comfort_upper_lips_height", lipsLengths.first)
					put("comfort_lower_lips_height", lipsLengths.second)
				}
			} else JSONObject()

			// 🎀🎀🎀🎀🎀 얼굴 전체 🎀🎀🎀🎀🎀

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
			val fs = classifyFaceShape(currentCoordinate, currentHairLine3DCoordinate)
			val faceShape = fs.primaryShape
			val faceShapeSub = fs.secondaryShape

			// ⚠️⚠️⚠️⚠️ 얼굴 전체 3D 전용 parameter ⚠️⚠️⚠️⚠️
			val wholeFace3D = if (isDepthCameraMeasure) {

				val neckHeight = get3DFloorDistance(midShoulder3D, flr3D[152])
				// 💡 1. 코 끝(Index 1)을 기준으로 삼아 안전한 얼굴 거리(Base Depth)를 도출합니다.
				val baseDepth = flr3D.getOrNull(1)?.third?.takeIf { it in 0.2f..1.0f }
					?: flr3D.getOrNull(4)?.third?.takeIf { it in 0.2f..1.0f }
					?: 0.5f

				val sanitizedFlr3D = flr3D.map { p ->
					val zVal = p.third
					if (zVal <= 0f || zVal > 1.0f || zVal > (baseDepth + 0.25f)) {
						val scale = baseDepth / if (zVal <= 0f) 0.35f else zVal
						Triple(p.first * scale, p.second * scale, baseDepth)
					} else {
						p
					}
				}
				val sanitizedHairLine3D = currentHairLine3DCoordinate.map { t ->
					val zVal = t.third
					val scale = if (zVal <= 0f || zVal > 1.0f || zVal > (baseDepth + 0.25f)) {
						baseDepth / if (zVal <= 0f) 0.35f else zVal
					} else {
						1.0f
					}
					Triple(t.first * scale, t.second * scale, if (scale == 1.0f) zVal else baseDepth)
				}
				val faceHeight = if (isDepthCameraMeasure && sanitizedHairLine3D.size > 3) {
					get3DFloorDistance(sanitizedFlr3D[152], sanitizedHairLine3D[3])
				} else {
					0f
				}
				val leftIndices = listOf(162, 127, 234, 93, 132, 58)
				val rightIndices = listOf(389, 356, 454, 323, 401, 435)

				val widths = leftIndices.indices.map { i ->
					val left = sanitizedFlr3D[leftIndices[i]]
					val right = sanitizedFlr3D[rightIndices[i]]
					get3DFloorDistance(left, right)
				}

				val maxHorizontalWidth = widths.maxOrNull() ?: 0f
				val faceWidth = maxHorizontalWidth
				Log.v("너비들", "face: $faceWidth, $faceHeight | ${neckHeight}")

				JSONObject().apply {
					put("comfort_face_height", faceHeight)
					put("comfort_face_width", faceWidth)
					put("comfort_neck_height", neckHeight)
				}
			} else JSONObject()

			Log.v("faceLandmarks", "${vmFlr.size}")

			val facialAxisShape = classifyFacialAxis(vmFlr)
			val verticalRatio = calculateVerticalRatio(vmFlr)
			val seggingDepths = classifySaggingDepth(vmFlr)
			val asymmetrys = classifyFacialAsymmetry(vmFlr)
			val tempStatic = JSONObject().apply {
				upperEyelid3D.keys().forEach { key ->
					put(key, upperEyelid3D.get(key))
				}

				midFace3D.keys().forEach { key ->
					put(key, midFace3D.get(key))
				}
				lowerLips3D.keys().forEach { key ->
					put(key, lowerLips3D.get(key))
				}

				wholeFace3D.keys().forEach { key ->
					put(key, wholeFace3D.get(key))
				}

				put("comfort_left_mouse_corner_angle", mouseCornerAngle.first)
				put("comfort_right_mouse_corner_angle", mouseCornerAngle.second)
				put("comfort_eye_horizontal_angle", corneaAngle)
				put("comfort_left_eye_horizontal_angle", eyeAngles.first)
				put("comfort_right_eye_horizontal_angle", eyeAngles.second)
				put("comfort_left_medial_canthus_angle", leftCanthuses.first)
				put("comfort_right_medial_canthus_angle", leftCanthuses.second)
				put("comfort_left_lateral_canthus_angle", rightCanthuses.first)
				put("comfort_right_lateral_canthus_angle", rightCanthuses.second)
				put("comfort_eyebrow_horizontal_angle", eyeBrowHorizonAngle)
				put("comfort_left_eyebrow_horizontal_angle", eyeBrowAngles.first)
				put("comfort_right_eyebrow_horizontal_angle", eyeBrowAngles.second)
				put("vertical_ratio", verticalRatio)
				put("comfort_side_head_left_angle", sideHeadAngle.first)
				put("comfort_side_head_right_angle", sideHeadAngle.second)
				put("comfort_left_cheeks_extent", cheeksExtents.first)
				put("comfort_right_cheeks_extent", cheeksExtents.second)
				put("comfort_left_cheekbone_extent", cheekboneExtents.first)
				put("comfort_right_cheekbone_extent", cheekboneExtents.second)

				put("comfort_face_vertical_angle", faceVerticalAngle)
				put("comfort_neck_vertical_angle", neckVerticalAngle)
				put("comfort_shoulder_horizontal_angle", shoulderHorizon)
				put("comfort_face_shape", faceShape)
				put("comfort_face_shape_sub", faceShapeSub)
				put("comfort_left_sagging_scale", seggingDepths.first)
				put("comfort_right_sagging_scale", seggingDepths.second)
				put("comfort_length_symmetry_factor", asymmetrys.first)
				put("comfort_angle_symmetry_factor", asymmetrys.second)
				put("comfort_facial_axis_shape", facialAxisShape.curve)

				put("comfort_alar_horizontal_angle", alaAngle)
				put("comfort_earflaps_horizontal_angle", earFlapAngle)

				put("comfort_tip_of_lips_horizontal_angle", correctTipOfLipsAngle)
				put("comfort_tip_of_chin_horizontal_angle", correctTipOfChinsAngle)
				put("comfort_canthus_oral_left_vertical_angle", canthusOralAngle.first)
				put("comfort_canthus_oral_right_vertical_angle", canthusOralAngle.second)
				put("comfort_alar_tip_of_lips_left_vertical_angle", nasalWingLipsAngle.first)
				put("comfort_alar_tip_of_lips_right_vertical_angle", nasalWingLipsAngle.second)

			}

			staticJA.put(tempStatic)
			Log.v("스태틱저장", "0: 스태틱 저장 ${staticJA.length()}")
		}

		// ℹ️ℹ️ EXPERT ️ℹ️ℹ️
		if (seq == Sequence.OCCLUSAL) {
			Log.v("occlusal", "occlusal 조건문")
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
			// 원복 후 그냥 tilt에 관해서 측정
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
			// 측정 값 초기화
			tempStaticJo = tempStatic
			staticJA.put(tempStaticJo)
			Log.v("occlusal", "occlusal 저장 끝 크기 ${staticJA.length()}")

			Log.v(
				"교합상태",
				"eyeAngle: $eyeAngle correctTipOfLipsAngle: $correctTipOfLipsAngle  correctTipOfChinsAngle: $correctTipOfChinsAngle"
			)

		}

		// ℹ️ℹ️ TILT ️ℹ️ℹ️
		if (seq == Sequence.JAW_LEFT_TILT) {
			if (tempStaticJo == null) {
				// tilt
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

		if (seq == Sequence.JAW_RIGHT_TILT) {
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
					"오른쪽 턱 쏠림 각도들",
					"noseChinAngle: $tiltNoseChinAngle tipOfLipsAngle: $tiltTipOfLipsAngle  mandibularDistance: $mandibularDistance"
				)
				Log.v(
					"오른쪽 턱 쏠림 각도들",
					"staticJA크기: ${staticJA.length()}"
				)
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

			// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6
			val tempStatic = JSONObject().apply {
				put("neck_extention_shoulder_horizontal_angle", shoulderHorizontalAngle)
				put("neck_extention_ear_horizontal_angle", earHorizontalAngle)
				put("neck_extention_neck_vertical_angle", neckVerticalAngle)
				put("neck_extention_face_vertical_angle", faceVerticalAngle)
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
	fun saveJsonToStorage(activity: FragmentActivity, inputJson: JSONObject, fileName: String): String? {

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
		poseLandmarker: PoseLandmarkerHelper,
		isImage: Boolean,
		isFromCamra: Boolean = true
	): Boolean {
		return withContext(Dispatchers.IO) {
			try {
				Log.v("미디어스토리지", "start ${currentSeq} fileName: $fileName | 이미지임?: ${isImage} : 랜드마크들: $flh $poseLandmarker")
				val extension = if (isImage) ".webp" else ".mp4"
//			val file = File(context.cacheDir, "$fileName$extension")

				if (isImage) {
					val contentValues = ContentValues().apply {
						put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName$extension")
						put(MediaStore.MediaColumns.MIME_TYPE, "image/webp")
						put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TangoPlus")
					}

					val tempFile = File.createTempFile("tempImage", null, context.cacheDir)
					context.contentResolver.openInputStream(uri)?.use { input ->
						tempFile.outputStream().use { output -> input.copyTo(output) }
					}

					// 1. 크기 확인
					val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
					BitmapFactory.decodeFile(tempFile.absolutePath, options)
					val srcW = options.outWidth
					val srcH = options.outHeight

					// 2. 회전 각도 결정 (카메라 vs 갤러리 분기)
					val rotationDegrees = if (isFromCamra) {
						if (srcW > srcH) 90f else 0f
					} else {
						0f
					}

					// 3. 샘플링 및 비트맵 디코딩
					val isSwapped = rotationDegrees == 90f || rotationDegrees == 270f
					val rotW = if (isSwapped) srcH else srcW
					val rotH = if (isSwapped) srcW else srcH
					val targetWidth = 1080
					val targetHeight = 1920

					val scale = maxOf(rotW.toFloat() / targetWidth, rotH.toFloat() / targetHeight)
					options.inJustDecodeBounds = false
					options.inSampleSize = if (scale.toInt() < 1) 1 else scale.toInt()

					var bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, options)

					// 4. 회전 적용
					if (rotationDegrees != 0f) {
						val matrix = Matrix().apply { postRotate(rotationDegrees) }
						val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
						if (rotated != bitmap) bitmap.recycle()
						bitmap = rotated
					}

					// 5. 1080x1920 Center Crop 맞춤
					if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
						val scaleFactor = maxOf(targetWidth.toFloat() / bitmap.width, targetHeight.toFloat() / bitmap.height)
						val scaledW = (bitmap.width * scaleFactor).toInt()
						val scaledH = (bitmap.height * scaleFactor).toInt()
						val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)

						val xOffset = ((scaledW - targetWidth) / 2).coerceAtLeast(0)
						val yOffset = ((scaledH - targetHeight) / 2).coerceAtLeast(0)
						val croppedBitmap = Bitmap.createBitmap(scaledBitmap, xOffset, yOffset, targetWidth, targetHeight)

						if (scaledBitmap != bitmap && scaledBitmap != croppedBitmap) scaledBitmap.recycle()
						if (bitmap != croppedBitmap) bitmap.recycle()
						bitmap = croppedBitmap
					}

					latestBitmap = bitmap

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
						val cacheDir = context.cacheDir
						val imageFile = File(cacheDir, "$fileName.png")
						FileOutputStream(imageFile).use { out ->
							// PNG 포맷으로 저장 (손실 없음)
							bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
						}
						Log.d("BitmapCache", "이미지 저장 성공: ${imageFile.absolutePath}")
						face2DResult = flResultBundle?.result?.let { FaceLandmarkAdapter.toCustomFaceLandmarkResult(it) }
						Log.v("2D랜드마크", "${flResultBundle?.result?.faceLandmarks()?.size}")
					}
					if (currentSeq == Sequence.COMFORT) {
						if (face2DResult == null) {
							Log.w("saveMedia", "얼굴 랜드마크 검출 실패. 저장을 중단합니다.")
							return@withContext false // ❌ 중단
						}
						val absoluteLandmarks = face2DResult!!.landmarks.map { landmark ->
							FaceLandmarkResult.FaceLandmark(
								x = calculateScreenX(1- landmark.x),
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
									val roundedX = safeRound(hp.point2D.x, 1)
									val roundedY = safeRound(hp.point2D.y, 1)
									val rounded3DX = safeRound(hp.point3D.x, 4)
									val rounded3DY = safeRound(hp.point3D.y, 4)
									val rounded3DZ = safeRound(hp.point3D.z, 4)

									val pointJA = JSONArray().apply {
										put(roundedX)
										put(roundedY)
										put(rounded3DX)
										put(rounded3DY)
										put(rounded3DZ)
									}
									tempHairLineJA.put(pointJA)
									currentHairLine2DCoordinate.add(Pair(hp.point2D.x, hp.point2D.y))
									currentHairLine3DCoordinate.add(Triple(hp.point3D.x, hp.point3D.y, hp.point3D.z))
							}
						} else {
							// 깊이 데이터가 없을 경우 z값을 0.0으로 빠르게 채움 (불필요한 수학 연산 제거)
							hairResult.hairlinePoints.forEachIndexed { index, hp2d ->

								val roundedX = safeRound(hp2d.point.x, 1)
								val roundedY = safeRound(hp2d.point.y, 1)
								val rounded3DX = 0.0
								val rounded3DY = 0.0
								val rounded3DZ = 0.0
								val pointJA = JSONArray().apply {
									put(roundedX)
									put(roundedY)
									put(rounded3DX)
									put(rounded3DY)
									put(rounded3DZ)
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
					Log.v("포즈검출", "${resultBundle?.results?.first()?.landmarks()?.size}")
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
							} else {
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

							val roundedX = safeRound(scaledX, 1)
							val roundedY = safeRound(scaledY, 1)
							val rounded3DX = safeRound(point3D.x, 4)
							val rounded3DY = safeRound(point3D.y, 4)
							val rounded3DZ = safeRound(point3D.z, 4)


							val ja = JSONArray().apply {
								// 💡 [Index 삭제] 용량 다이어트를 위해 낱개 인덱스 기록을 과감히 제거합니다.
								put(roundedX)
								put(roundedY)
								put(rounded3DX)
								put(rounded3DY)
								put(rounded3DZ)
							}
							tempPlrJA.put(ja)
							currentPlrCoordinate.add(Pair(targetLandmark.x(), targetLandmark.y()))
							current3DPlrCoordinate.add(Triple(point3D.x, point3D.y, point3D.z))
						}
						Log.v("포즈랜드마크", "사이즈: ${currentPlrCoordinate.size}")
						plrJA.put(tempPlrJA)
					} else {
						withContext(Dispatchers.Main) {
							Toast.makeText(context, "poselandmark를 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
						}
						plrJA.put(tempPlrJA)
						Log.e("saveMedia", "poselandmark 검출 실패. 저장을 중단합니다.")
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
				} else {
					val videoCollection =
						MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
					val contentValues = ContentValues().apply {
						put(MediaStore.Video.Media.DISPLAY_NAME, fileName)  // ex: 4-20251020145123.mp4
						put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
						put(
							MediaStore.Video.Media.RELATIVE_PATH,
							"${Environment.DIRECTORY_MOVIES}/TangoPlus"
						) // Movies/TangoPlus/
						put(MediaStore.Video.Media.IS_PENDING, 1)
					}

					val videoUri = context.contentResolver.insert(videoCollection, contentValues)

					if (videoUri != null) {
						context.contentResolver.openInputStream(uri)?.use { input ->
							context.contentResolver.openOutputStream(videoUri)?.use { output ->
								input.copyTo(output)

								contentValues.clear()
								contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
								context.contentResolver.update(videoUri, contentValues, null, null)
								Log.v("영상작업완료 후 plr넣기", "$fileName$extension")

								mediaFileNames.add("$fileName$extension")
								coordinatesJA.put(videoTempCoordinatesJA)
								videoTempCoordinatesJA = JSONArray()
								// 빈 jsonArray를 넣어서 index 갯수 맞추기
								plrJA.put(JSONArray())
								Log.v("영상작업완료 후 plr넣기", "${plrJA.length()}")
							}
						}
					}
					return@withContext true
				}
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
	fun inpaintDepth(depth: FloatArray, width: Int, height: Int, iterations: Int = 15): FloatArray {
		var current = depth.copyOf()

		repeat(iterations) {
			val next = current.copyOf()
			for (y in 1 until height - 1) {
				for (x in 1 until width - 1) {
					val idx = y * width + x
					if (current[idx] > 0f) continue

					var sum = 0f
					var count = 0
					for (dy in -1..1) {
						for (dx in -1..1) {
							val v = current[(y + dy) * width + (x + dx)]
							if (v > 0f) { sum += v; count++ }
						}
					}
					if (count > 0) {
						next[idx] = sum / count
					}
				}
			}
			current = next
		}
		return current
	}

	fun smoothDepthByResample(
		depthData: ShortArray, width: Int, height: Int,
		downscaleFactor: Int = 3
	): FloatArray {
		val smallW = (width / downscaleFactor).coerceAtLeast(1)
		val smallH = (height / downscaleFactor).coerceAtLeast(1)

		val small = FloatArray(smallW * smallH)
		for (sy in 0 until smallH) {
			for (sx in 0 until smallW) {
				var sum = 0f
				var count = 0
				val startX = sx * downscaleFactor
				val startY = sy * downscaleFactor
				for (yy in startY until minOf(startY + downscaleFactor, height)) {
					for (xx in startX until minOf(startX + downscaleFactor, width)) {
						val d = depthData[yy * width + xx].toInt() and 0xFFFF
						if (d > 0) { sum += d; count++ }
					}
				}
				small[sy * smallW + sx] = if (count > 0) sum / count else 0f
			}
		}

		val result = FloatArray(width * height)
		for (y in 0 until height) {
			for (x in 0 until width) {
				val fx = (x.toFloat() / downscaleFactor).coerceIn(0f, (smallW - 1).toFloat())
				val fy = (y.toFloat() / downscaleFactor).coerceIn(0f, (smallH - 1).toFloat())
				val x0 = fx.toInt(); val x1 = (x0 + 1).coerceAtMost(smallW - 1)
				val y0 = fy.toInt(); val y1 = (y0 + 1).coerceAtMost(smallH - 1)
				val tx = fx - x0; val ty = fy - y0

				val v00 = small[y0 * smallW + x0]
				val v10 = small[y0 * smallW + x1]
				val v01 = small[y1 * smallW + x0]
				val v11 = small[y1 * smallW + x1]

				val top = v00 + (v10 - v00) * tx
				val bottom = v01 + (v11 - v01) * tx
				result[y * width + x] = top + (bottom - top) * ty
			}
		}
		return result
	}


	data class LinePoint(val x: Float, val y: Float)

	fun interpolate(x1: Float, y1: Float, v1: Float, x2: Float, y2: Float, v2: Float, level: Float): LinePoint {
		val t = if (v2 != v1) (level - v1) / (v2 - v1) else 0.5f
		return LinePoint(x1 + t * (x2 - x1), y1 + t * (y2 - y1))
	}

	fun marchingSquares(
		depth: FloatArray, width: Int, height: Int, level: Float
	): List<Pair<LinePoint, LinePoint>> {
		val segments = mutableListOf<Pair<LinePoint, LinePoint>>()

		for (y in 0 until height - 1) {
			for (x in 0 until width - 1) {
				val tl = depth[y * width + x]
				val tr = depth[y * width + x + 1]
				val bl = depth[(y + 1) * width + x]
				val br = depth[(y + 1) * width + x + 1]

				if (tl <= 0f || tr <= 0f || bl <= 0f || br <= 0f) continue

				var caseIdx = 0
				if (tl > level) caseIdx = caseIdx or 8
				if (tr > level) caseIdx = caseIdx or 4
				if (br > level) caseIdx = caseIdx or 2
				if (bl > level) caseIdx = caseIdx or 1

				val xf = x.toFloat(); val yf = y.toFloat()
				val top = interpolate(xf, yf, tl, xf + 1, yf, tr, level)
				val right = interpolate(xf + 1, yf, tr, xf + 1, yf + 1, br, level)
				val bottom = interpolate(xf, yf + 1, bl, xf + 1, yf + 1, br, level)
				val left = interpolate(xf, yf, tl, xf, yf + 1, bl, level)

				when (caseIdx) {
					1, 14 -> segments.add(left to bottom)
					2, 13 -> segments.add(bottom to right)
					3, 12 -> segments.add(left to right)
					4, 11 -> segments.add(top to right)
					6, 9 -> segments.add(top to bottom)
					7, 8 -> segments.add(left to top)
					5 -> { segments.add(left to top); segments.add(bottom to right) }
					10 -> { segments.add(top to right); segments.add(left to bottom) }
				}
			}
		}
		return segments
	}


	fun drawContourLevel(
		canvas: Canvas, segments: List<Pair<LinePoint, LinePoint>>, color: Int, strokeWidth: Float = 2f
	) {
		val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			this.color = color
			style = Paint.Style.STROKE
			this.strokeWidth = strokeWidth
			strokeCap = Paint.Cap.ROUND
		}
		for ((p1, p2) in segments) {
			canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
		}
	}
	fun computeFaceDepthRange(
		depthData: ShortArray, depthW: Int, depthH: Int, faceMaskPath: Path
	): Pair<Int, Int> {
		val bounds = RectF()
		faceMaskPath.computeBounds(bounds, true)

		val region = Region()
		region.setPath(
			faceMaskPath,
			Region(0, 0, depthW, depthH)
		)

		val depths = mutableListOf<Int>()
		for (y in bounds.top.toInt().coerceIn(0, depthH - 1)..bounds.bottom.toInt().coerceIn(0, depthH - 1)) {
			for (x in bounds.left.toInt().coerceIn(0, depthW - 1)..bounds.right.toInt().coerceIn(0, depthW - 1)) {
				if (!region.contains(x, y)) continue   // ✅ 폴리곤 내부만
				val d = depthData[y * depthW + x].toInt() and 0xFFFF
				if (d > 0) depths.add(d)
			}
		}

		if (depths.isEmpty()) return Pair(0, 0)

		// ✅ 퍼센타일 기반으로 outlier 방어까지 같이 적용
		depths.sort()
//		val minDepth = depths[(depths.size * 0.05).toInt().coerceIn(0, depths.size - 1)]
		val minDepth = depths[(depths.size * 0.01).toInt().coerceIn(0, depths.size - 1)]
		val maxDepth = depths[(depths.size * 0.95).toInt().coerceIn(0, depths.size - 1)]

		return Pair(minDepth, maxDepth)
	}

	// ===== 3) 무아레 등고선 생성 (band 비교 방식) =====
	fun generateMoireBitmap(
		rawDepthData: ShortArray,
		depthW: Int,
		depthH: Int,
		faceMaskPath: Path?,
		minDepth: Int,
		maxDepth: Int,
		intervalMm: Float = 2.5f,
		strokeWidth: Float = 2f
	): Bitmap {
		val smoothed = smoothDepthByResample(rawDepthData, depthW, depthH, downscaleFactor = 2)
		val filled = inpaintDepth(smoothed, depthW, depthH, iterations = 15)

		val bitmap = Bitmap.createBitmap(depthW, depthH, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(bitmap)
		if (faceMaskPath != null) {
			canvas.clipPath(faceMaskPath)
		}
		val gradientLayer = generateGradientLayer(filled, depthW, depthH, minDepth, maxDepth)
		canvas.drawBitmap(gradientLayer, 0f, 0f, null)
		var level = minDepth.toFloat()
		while (level <= maxDepth) {
			val segments = marchingSquares(filled, depthW, depthH, level)

			val normalized = ((level - minDepth) / (maxDepth - minDepth)).coerceIn(0f, 1f)
			val hue = normalized * 270f
			val color = Color.HSVToColor(floatArrayOf(hue, 1f, 0.9f))

			val lineColor = Color.argb(100, 255, 255, 255)  // 흰색, 반투명, alpha 조절 가능
			drawContourLevel(canvas, segments, lineColor, strokeWidth = 0.5f)
			level += intervalMm
		}

		return bitmap
	}
	suspend fun saveMoireToStorage (
		context: Context,
		moireBitmap: Bitmap,
		fileName: String
	) {
		return withContext(Dispatchers.IO) {
			try {
				val contentValues = ContentValues().apply {
					put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.webp")
					put(MediaStore.MediaColumns.MIME_TYPE, "image/webp")
					put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TangoPlus")
				}
				val imageUri = context.contentResolver.insert(
					MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
					contentValues
				)
				imageUri?.let { uri ->
					context.contentResolver.openOutputStream(uri)?.use { os ->
						moireBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, os)
						os.flush()
					}
					Log.d("SaveMoire", "무아레 저장 완료: $fileName.webp")
					true
				} ?: false
			} catch (e: Exception) {
				Log.e("SaveMoire", "무아레 저장 실패: ${e.message}")
				false
			}
		}
	}
	fun rotateDepth90CW(src: ShortArray, w: Int, h: Int): ShortArray {
		val dest = ShortArray(w * h)
		for (y in 0 until h) {
			for (x in 0 until w) {
				val destX = h - 1 - y
				val destY = x
				dest[destY * h + destX] = src[y * w + x]
			}
		}
		return dest
	}

	suspend fun scannedImage(
		activity: AppCompatActivity,
		imagePath : String?,
		flh: FaceLandmarkerHelper,
		plh: PoseLandmarkerHelper
	): Boolean {
		// 1. 파일명 생성
		val name = getFileName(Sequence.COMFORT)
		val contentResolver = activity.contentResolver


		if (imagePath.isNullOrEmpty()) {
			Log.e("scannedImage", "편한자세에 저장된 이미지 경로가 없습니다.")
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
				val success = saveMediaToStorage(activity, tempUri, name, flh, plh, true, false)

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
      Sequence.OCCLUSAL -> 1
			Sequence.JAW_LEFT_TILT -> 2
			Sequence.JAW_RIGHT_TILT -> 3
			Sequence.JAW_OPENING -> 4
			Sequence.BOW -> 5
    }
		val seqIndex = allSeqs.indexOf(seq)
		val mediaFileName = mediaFileNames[seqIndex]
		val measureTypeNum = when (measureType) {
			MeasureType.NORMAL -> 0
			MeasureType.EXPERT -> 1
			MeasureType.BOW -> 2
			else -> 0
		}
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
			put("measure_type", measureTypeNum)
		}
	}

	// depthCamera에서 가져오는 stream pipeline/mDevice/mStreamRunnable
	var mPipeline: Pipeline? = null
	var mStreamThread: Thread? = null

	var last3DCalculateTime = 0L
	val calculationInterval = 200L // 5fps

	var mIsStreamRunning = false
	@Volatile var mIsDeviceDetached = false
	var mDevice: Device? = null
	var mHoleFillingFilter: HoleFillingFilter? = null

	var scaledIntrinsic: ScaledIntrinsic? = null
	var colorDistortion: CameraDistortion? = null
	var depthDistortion: CameraDistortion? = null
	var flh: FaceLandmarkerHelper? = null
	var plh : PoseLandmarkerHelper? = null

	suspend fun captureImage(seq: Sequence, activity: FragmentActivity): Boolean {
		val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
		val name = getFileName(seq, time)
		val moireName = getMoireFileName(seq, time)
		return withContext(Dispatchers.IO) {
			try {
				mPipeline?.waitForFrameSet(1000)?.use { frameSet ->
					val colorFrame = frameSet.getColorFrame() ?: return@use false
					val originalDepthFrame = frameSet.getDepthFrame()
					val dataSize = colorFrame.getDataSize()
					Log.d("captureImage", "✅ 데이터 읽을 크기: $dataSize bytes")

					val byteBuffer = ByteBuffer.allocateDirect(dataSize)
					colorFrame.getData(byteBuffer)
					byteBuffer.rewind() // 읽기 위치를 처음으로

					val width = colorFrame.width
					val height = colorFrame.height
					Log.d("captureImage", "✅ 크기: ${width}x${height}")

					val pixels = IntArray(width * height)
					for (i in pixels.indices) {
						if (byteBuffer.remaining() >= 3) {
							val r = byteBuffer.get().toInt() and 0xFF
							val g = byteBuffer.get().toInt() and 0xFF
							val b = byteBuffer.get().toInt() and 0xFF

							pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
						}
					}
					colorFrame.close()

					// depth 프레임 가져오기
					var depthData: ShortArray? = null
					var depthW = 0
					var depthH = 0
					val face2D = face2DResult
					val params = mDevice?.calibrationCameraParamList


					if (originalDepthFrame != null) {
						var filteredFrame: Frame? = null
						var finalDepthFrame: DepthFrame? = null

						try {
							// 1. 홀필링 필터 처리
							filteredFrame = mHoleFillingFilter?.process(originalDepthFrame)
							finalDepthFrame = filteredFrame?.`as`(FrameType.DEPTH) as? DepthFrame

							// 2. 필터링 성공하면 필터본 사용, 실패하면 원본 사용
							val df = finalDepthFrame ?: originalDepthFrame

							// 3. 실제 데이터 추출 진행
							depthW = df.width
							depthH = df.height
							val byteData = ByteArray(depthW * depthH * 2)
							df.getData(byteData)

							val rawDepthData = ShortArray(depthW * depthH) { i ->
								val lo = byteData[i * 2].toInt() and 0xFF
								val hi = byteData[i * 2 + 1].toInt() and 0xFF
								(hi shl 8 or lo).toShort()
							}

							val rotatedDepth = rotateDepth90CW(rawDepthData, depthW, depthH)
							val tempW = depthH
							val tempH = depthW
							depthW = tempW
							depthH = tempH

							val shiftPixels = 40
							depthData = shiftDepthUp(rotatedDepth, depthW, depthH, shiftPixels)

							latestDepthData = depthData
							latestDepthWidth = depthW
							latestDepthHeight = depthH

						} catch (e: Exception) {
							Log.e("captureImage", "❌ Depth processing failed: ${e.message}")
						} finally {
							// 💡 [Double Free 방지] 자원이 겹치지 않게 안전하게 해제
							try {
								// 1. finalDepthFrame과 filteredFrame이 같은 네이티브 객체를 바라보고 있다면 하나만 해제
								if (finalDepthFrame != null) {
									finalDepthFrame.close()
								} else {
									filteredFrame?.close()
								}
							} catch (e: Exception) {
								Log.e("captureImage", "Error closing filtered frame: ${e.message}")
							}

							try {
								// 2. 원본 프레임 해제
								originalDepthFrame.close()
							} catch (e: Exception) {
								Log.e("captureImage", "Error closing original frame: ${e.message}")
							}
						}
					}

					val undistortedPoints: List<Pair<Float, Float>>? = if (face2D != null && params != null) {
						val paramsUnit = params.getCameraParam(0)
						val rawPoints = face2D.landmarks.map { Pair(it.x * depthW, it.y * depthH) }

						// 💡 [변경] 수동으로 완벽히 축을 정렬한 90도 회전 ScaledIntrinsic을 바로 뽑아냅니다.
						val depthIntrc = paramsUnit.depthIntrinsic.toRotated90ScaledIntrinsic()

						undistortLandmarks(rawPoints, depthIntrc, depthDistortion!!)
					} else null

					if (face2D != null && params != null && depthData != null) {
						val paramsUnit = params.getCameraParam(0)
						val depthIntrc = paramsUnit.depthIntrinsic.toRotated90ScaledIntrinsic()
						latestIntrinsic = depthIntrc

						val landmarks3D = convertFaceLandmarksTo3D(
							face2D,
							depthData,
							depthW, // 720
							depthH, // 1280
							depthIntrc,
							depthDistortion!!
						)

						face3DResult = landmarks3D
						Log.d("captureImage", "✅ 3D landmark 계산 완료: ${landmarks3D.size}개")
					} else {
						Log.w("captureImage", "⚠️ face2D 또는 params 없음, 3D 계산 스킵")
					}

					Log.d("captureImage", "color=${width}x${height}  depth=${depthW}x${depthH}")

					val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
					bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

					face2D?.let { result ->
						val noseIndexes = listOf(1, 4, 5)
						noseIndexes.forEach { idx ->
							val lm = result.landmarks[idx]
							val px = (lm.x * depthW).toInt().coerceIn(0, depthW - 1)
							val py = (lm.y * depthH).toInt().coerceIn(0, depthH - 1)

							val sb = StringBuilder()
							for (dy in -2..2) {
								for (dx in -2..2) {
									val xx = (px + dx).coerceIn(0, depthW - 1)
									val yy = (py + dy).coerceIn(0, depthH - 1)
									val d = depthData?.get(yy * depthW + xx)?.toInt()?.and(0xFFFF) ?: -1
									sb.append("$d ")
								}
								sb.append("| ")
							}
						}
					}
					val faceMaskPath = undistortedPoints?.let { pts ->
						buildFaceMaskPathFromPoints(pts, faceOutLineIndexes)
					}
					Log.d("faceMaskPath", "undistortedPoints ${undistortedPoints} | faceMaskPath=${faceMaskPath}")

					val tempFile = File.createTempFile("color_capture", ".webp", activity.cacheDir)
					FileOutputStream(tempFile).use { fos ->
						bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 100, fos)
					}
					val tempUri = Uri.fromFile(tempFile)
					Log.d("captureImage", "flh=${flh}  plh=${plh}")

					if (flh != null && plh!= null) {
						val success = saveMediaToStorage(activity, tempUri, name, flh!!, plh!!, true)
						Log.v("saveMedia", "$success")
						if (seq == Sequence.COMFORT) {
							val (minDepth, maxDepth) = computeFaceDepthRange(depthData!!, depthW, depthH, faceMaskPath!!)
							val tempMoireBitmap = generateMoireBitmap(depthData, depthW, depthH, faceMaskPath, minDepth, maxDepth)
							saveMoireToStorage(activity, tempMoireBitmap, moireName)
						}
						Log.v("saveMedia", "afterMoire $success")
					}
					return@withContext true
				} ?: false
			} catch (e: Exception) {
				Log.e("captureImage", "❌ Frame capture failed: ${e.message}", e)
				false
			}
		}
	}
	fun resultHsvToJson(
		resultBundle: FaceLandmarkResult,
		targets: List<HsvColorTracker.Target>,
		flr3D: List<Point3D> = listOf()
	) {
		if (resultBundle.landmarks.isNotEmpty()) {
			val flr = resultBundle.landmarks

			val targetFs = targets.map { toFloatTarget(it, baseW = 1080, baseH = 1920, mirrorX = true) }
				.sortedBy { it.cx }
			val ja = JSONArray()
			for (t in targetFs) {
				val obj = JSONObject()
					.put("cx", t.cx)
					.put("cy", t.cy)
				ja.put(obj)
			}
			resultTargetFs.copyFrom(ja)
//            Log.v("좌표까봐-결과", "resultTargetFs: ${mvm.resultTargetFs}")

			currentCoordinate.clear()
			relativeCoordinate.clear()
			current3DCoordinate.clear()

			flr.forEachIndexed { index, faceLandmark ->


				val scaledX = calculateScreenX( 1f - faceLandmark.x)
				val scaledY = calculateScreenY( faceLandmark.y)
				val x = (flr3D.getOrNull(index)?.x ?: 0f).safe()
				val y = (flr3D.getOrNull(index)?.y ?: 0f).safe()
				val z = (flr3D.getOrNull(index)?.z ?: 0f).safe()

				val roundedX = safeRound(scaledX, 1)
				val roundedY = safeRound(scaledY, 1)
				val rounded3DX = safeRound(x, 4)
				val rounded3DY = safeRound(y, 4)
				val rounded3DZ = safeRound(z, 4)
				val ja = JSONArray().apply {
					put(roundedX)
					put(roundedY)
					put(rounded3DX)
					put(rounded3DY)
					put(rounded3DZ)
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
			}
			coordinatesJA.put(tempCoordinateJA)
			val vmFlr = relativeCoordinate
			if (targetFs.size >= 3) {
				val eyeAngle =
					calculateSlope(vmFlr[468].first, vmFlr[468].second, vmFlr[473].first, vmFlr[473].second)
				val lipsAngle =
					calculateSlope(targetFs[0].cx, targetFs[0].cy, targetFs[2].cx, targetFs[2].cy)
				val chinAngle =
					calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[61].first, vmFlr[61].second)

				val tempHsvColorJo = JSONObject()
				tempHsvColorJo.apply {
					put("bow_eye_horizontal_angle", eyeAngle)
					put("bow_tip_of_lips_horizontal_angle", lipsAngle)
					put("bow_tip_of_chin_horizontal_angle", chinAngle)
				}

				staticJA.put(tempHsvColorJo)
			}
		}
	}

	//  🎥🎥🎥🎥🎥🎥🎥 video 🎥🎥🎥🎥🎥🎥🎥
	private var maxJawOpening = -1f
	private var maxJawOpeningIndex = -1
	var maxJawOpeningBitmap: Bitmap? = null
	var maxJawOpeningDepthData: ShortArray? = null
	var maxJawOpeningDepthW = 0
	var maxJawOpeningDepthH = 0
	var maxJawOpeningFace2D: FaceLandmarkResult? = null
	var maxJawOpening3D: List<Point3D>? = null
	private var recordingJob: Job? = null
	private var recording: Recording? = null
	var startRecording = false
	private var isRecording = false
	private var videoEncoder: MediaCodec? = null
	private var mediaMuxer: MediaMuxer? = null
	private var videoTrackIndex = -1
	var recordingStartTime: Long? = null
	private var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
	@SuppressLint("SuspiciousIndentation")
  fun stopVideoRecording(activity: FragmentActivity) {
		recordingJob?.cancel()
		isRecording = false
		cleanup()
		releaseVideoRecording(activity)
	}

	private fun startRecordingMaxTimer(activity: FragmentActivity) {
		activity.lifecycleScope.launch(Dispatchers.Main) {
			delay(6000) // 6초가 max 영상 길이
			isRecording = false
		}
	}

	fun startVideoRecording(activity: FragmentActivity, callback: () -> Unit) {
		startDepthCameraRecording(activity, callback)
	}

	@SuppressLint("SuspiciousIndentation")
	private fun releaseVideoRecording(activity: FragmentActivity) {
		// 1) 타이머/잡 정리 (이미 있는 변수만 사용)
		try {
			startRecording = false
			recordingJob?.cancel()
		} catch (_: Throwable) {
		}
		recordingJob = null
		isRecording = false
		startRecording = false
		recordingStartTime = null

		// 2) 메인 루퍼에 걸어둔 지연 콜백들 제거
		//    (Start에서 Handler(Looper.getMainLooper()).postDelayed(...) 사용)
		try {
			Handler(Looper.getMainLooper()).removeCallbacksAndMessages(null)
		} catch (_: Throwable) {
		}

		// 3) CameraX Recording 자원 해제
		recording?.let { r ->
			runCatching { r.stop() }
			runCatching { r.close() }
		}
		recording = null

		runCatching { cleanup() }

		// 5) 변환 중 남은 임시 파일들 중 ‘빈 파일/부분 파일’만 정리 (안전 범위)
		activity.runCatching {
			cacheDir?.listFiles()
				?.filter { it.isFile && it.extension.equals("mp4", ignoreCase = true) }
				// 0바이트 또는 아주 작은 부분 파일(예: 64KB 미만)만 제거
				?.filter { it.length() in 0..65535 }
				?.forEach { runCatching { it.delete() } }
		}
	}


	private fun cleanup() {
		try {
			videoEncoder?.let { encoder ->
				try {
					encoder.stop()
				} catch (e: Exception) {
					Log.e("cleanup", "Error stopping encoder: ${e.message}")
				}
				encoder.release()
				videoEncoder = null
			}

			if (::inputSurface.isInitialized) {
				try {
					inputSurface.release()
				} catch (e: Exception) {
					Log.e("cleanup", "Error releasing surface: ${e.message}")
				}
			}

			mediaMuxer?.let { muxer ->
				try {
					muxer.stop()
				} catch (e: Exception) {
					Log.e("cleanup", "Error stopping muxer: ${e.message}")
				}
				muxer.release()
				mediaMuxer = null
			}

			videoTrackIndex = -1
			isRecording = false

			Log.d("cleanup", "Video recording cleanup completed")
		} catch (e: Exception) {
			Log.e("cleanup", "Cleanup failed: ${e.message}")
		}
	}

	private lateinit var inputSurface: Surface

	private fun startDepthCameraRecording(activity: FragmentActivity, callback: () -> Unit) {
		recordingJob = CoroutineScope(Dispatchers.IO).launch {
			try {
				timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
				val outputFile = File(activity.cacheDir, "$timestamp.mp4")
				Log.d("startRecording", "🔍 [RECORD_DEBUG] 녹화 시작 - 출력 파일: ${outputFile.absolutePath}")

				val outWidth = 1080
				val outHeight = 1920
				val camWidth = 1920
				val camHeight = 1080
				val fps = 30
				val bitRate = 6_000_000

				val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outWidth, outHeight).apply {
					setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
					setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
					setInteger(MediaFormat.KEY_FRAME_RATE, fps)
					setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
				}

				videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
				videoEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
				inputSurface = videoEncoder!!.createInputSurface()

				mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
				videoEncoder?.start()

				delay(100)
				isRecording = true

				withContext(Dispatchers.Main) {
					startRecording = true
					startRecordingMaxTimer(activity)
				}

				val frameInterval = 1000L / fps
				var frameCount = 0
				var encodedFrameCount = 0 // 실제 인코딩되어 파일에 써진 프레임 수 추적
				val bufferInfo = MediaCodec.BufferInfo()
				var trackIndex = -1
				var muxerStarted = false
				val canvasPaint = Paint().apply { isFilterBitmap = true }

//				val pixels = IntArray(camWidth * camHeight)
				val bitmap = Bitmap.createBitmap(camWidth, camHeight, Bitmap.Config.ARGB_8888)
				val rotateMatrix = Matrix().apply {
					postRotate(90f)
					postTranslate(camHeight.toFloat(), 0f)
				}


				// JAW OPENING을 위한 함수
				// while 문 바로 직전 위치에 선언
				var hasMaxJawOpeningData = false
				val maxRawDepthW = 1280
				val maxRawDepthH = 720

				// 매번 할당하지 않고 재사용할 프리미티브 배열들
				val jawDepthByteData = ByteArray(maxRawDepthW * maxRawDepthH * 2)
				val jawRawDepthData = ShortArray(maxRawDepthW * maxRawDepthH)

				// 회전 가공하지 않은 원본 스냅샷 저장용 비트맵 및 캔버스
				val jawRawBitmap = Bitmap.createBitmap(camWidth, camHeight, Bitmap.Config.ARGB_8888)
				val jawCanvas = Canvas(jawRawBitmap)

				while (isRecording) {
					isStopProcessing = false

					val frameStart = System.currentTimeMillis()

					// 1️⃣ 프레임 획득 단계 로그
					var frameAcquireTime = 0L
					mPipeline?.waitForFrameSet(200).let { frameSet ->
						frameAcquireTime = System.currentTimeMillis() - frameStart

						if (frameSet == null) {
							return@let
						}

						frameSet.use { fs ->
							val colorFrame = fs.getColorFrame()
							if (colorFrame == null) {
								return@use
							}

							// 2️⃣ 비트맵 변환 소요 시간 측정
							val bitmapStart = System.currentTimeMillis()
							val rawBitmap = colorFrameToBitmapSafe(colorFrame, 0)
							if (rawBitmap != null) {
								val canvas = Canvas(bitmap)
								canvas.drawBitmap(rawBitmap, 0f, 0f, null)
								rawBitmap.recycle()
							} else {
								return@use
							}
							val bitmapTime = System.currentTimeMillis() - bitmapStart

							// 3️⃣ 알고리즘 연산 및 스냅샷 저장 단계 (Occlusal)
							if (!isOcclusalSaved) {
								fs.getDepthFrame()?.use { df ->
									isOcclusalSaved = true
									// (생략 가능하나 흐름 추적용 로그 추가)
									val rawW = df.width
									val rawH = df.height
									val byteData = ByteArray(rawW * rawH * 2)
									df.getData(byteData)
									val rawDepthData = ShortArray(rawW * rawH) { i ->
										val lo = byteData[i * 2].toInt() and 0xFF
										val hi = byteData[i * 2 + 1].toInt() and 0xFF
										(hi shl 8 or lo).toShort()
									}
									val rotatedDepth = rotateDepth90CW(rawDepthData, rawW, rawH)
									val finalDepthData = shiftDepthUp(rotatedDepth, rawH, rawW, 40)
									val face2DCopy = face2DResult?.copy()
									val bitmapCopy = Bitmap.createBitmap(bitmap, 0, 0, camWidth, camHeight, rotateMatrix, true)
									Log.v("개구좌표들", "${face2DCopy?.landmarks?.size}")

									CoroutineScope(Dispatchers.IO).launch {
										try {
											processAndSaveFrameData(Sequence.OCCLUSAL, activity, bitmapCopy, finalDepthData, rawH, rawW, face2DCopy)
											resultBundleToJson(Sequence.OCCLUSAL)
										} finally {
											bitmapCopy.recycle()
										}
									}
								}
							}

							// 개구 상태 체크
							if (currentSeq == Sequence.JAW_LEFT_TILT) {
								val topLips = face2DResult?.landmarks?.getOrNull(13)?.y ?: 0f
								val bottomLips = face2DResult?.landmarks?.getOrNull(14)?.y ?: 0f
								val currentOpening = bottomLips - topLips

								if (currentOpening > maxJawOpening) {
									maxJawOpening = currentOpening
									maxJawOpeningIndex = frameCount
									hasMaxJawOpeningData = true

									jawCanvas.drawBitmap(bitmap, 0f, 0f, null)
									maxJawOpeningFace2D = face2DResult?.copy()
									maxJawOpening3D = face3DResult.map { it.copy() }
									Log.v("조오프닝좌표들", "${maxJawOpeningFace2D?.landmarks?.size}, ${maxJawOpening3D?.size}")
									frameSet.getDepthFrame()?.use { df ->
										// 💡 [최적화] 배열 새로 안 만들고 기존 바이트 배열 재사용
										df.getData(jawDepthByteData)

										// 가로형 원본 상태 그대로 Short 배열 변환만 빠르게 수행
										for (i in 0 until (df.width * df.height)) {
											val lo = jawDepthByteData[i * 2].toInt() and 0xFF
											val hi = jawDepthByteData[i * 2 + 1].toInt() and 0xFF
											jawRawDepthData[i] = (hi shl 8 or lo).toShort()
										}
									}
								}
							}
							if (!isRecording || !::inputSurface.isInitialized) return@use

							// 4️⃣ 인코더 Surface에 그리기 소요 시간 측정
							val drawStart = System.currentTimeMillis()
							try {
								val canvas = inputSurface.lockCanvas(null)
								synchronized(bitmap) {
									canvas.drawBitmap(bitmap, rotateMatrix, canvasPaint)
								}
								inputSurface.unlockCanvasAndPost(canvas)
							} catch (e: Exception) {
								Log.e("startRecording", "🔍 [RECORD_DEBUG] ❌ Surface 그리기 실패: ${e.message}")
								isRecording = false
								return@use
							}

							if (frameCount % 3 == 0) {
								resultBundleToJson(currentSeq)
							}

							val drawTime = System.currentTimeMillis() - drawStart

							Log.d("startRecording", "🔍 [RECORD_DEBUG] 🟢 [프레임 #$frameCount] 획득:${frameAcquireTime}ms, 비트맵변환:${bitmapTime}ms, 서피스드로우:${drawTime}ms")
						}
					}

					// 5️⃣ 하드웨어 인코더 배출(Drain) 및 파일 쓰기 단계 로그
					val encoder = videoEncoder
					if (encoder == null || !isRecording) break

					val drainStart = System.currentTimeMillis()
					try {
						var outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)

						if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
							if (!muxerStarted) {
								trackIndex = mediaMuxer!!.addTrack(encoder.outputFormat)
								mediaMuxer!!.start()
								muxerStarted = true
								Log.d("startRecording", "🔍 [RECORD_DEBUG] 🎬 MediaMuxer가 정상적으로 Start 되었습니다.")
							}
							outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
						}

						while (outputBufferIndex >= 0) {
							val encodedData = encoder.getOutputBuffer(outputBufferIndex)
							if (encodedData != null) {
								if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
									bufferInfo.size = 0
								}

								if (bufferInfo.size != 0 && muxerStarted) {
									encodedData.position(bufferInfo.offset)
									encodedData.limit(bufferInfo.offset + bufferInfo.size)
									mediaMuxer?.writeSampleData(trackIndex, encodedData, bufferInfo)
									encodedFrameCount++ // 파일에 기록 성공한 프레임 카운트 증가
								}
							}
							encoder.releaseOutputBuffer(outputBufferIndex, false)
							outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
						}
					} catch (e: Exception) {
						Log.e("startRecording", "🔍 [RECORD_DEBUG] ❌ 인코더 배출 중 에러: ${e.message}")
					}
					val drainTime = System.currentTimeMillis() - drainStart
					if (drainTime > 10) {
						Log.w("startRecording", "🔍 [RECORD_DEBUG] ⚠️ 인코더 배출 지연 발생: ${drainTime}ms")
					}

					frameCount++
					val elapsed = System.currentTimeMillis() - frameStart
					val delay = frameInterval - elapsed
					if (delay > 0) delay(delay)
				}
				if (hasMaxJawOpeningData) {
					Log.d("startRecording", "🔄 녹화 종료 후 Max Jaw Opening 최종 프레임 회전 가공 시작")

					maxJawOpeningBitmap = Bitmap.createBitmap(jawRawBitmap, 0, 0, camWidth, camHeight, rotateMatrix, true)
					val rotatedDepth = rotateDepth90CW(jawRawDepthData, maxRawDepthW, maxRawDepthH)
					maxJawOpeningDepthW = maxRawDepthH // 720
					maxJawOpeningDepthH = maxRawDepthW // 1280
					maxJawOpeningDepthData = shiftDepthUp(rotatedDepth, maxJawOpeningDepthW, maxJawOpeningDepthH, 40)
				}
				jawRawBitmap.recycle()
				Log.d("startRecording", "🔍 [RECORD_DEBUG] 루프 종료 - 총 루프 카운트: $frameCount, 실제 인코딩 완료 프레임: $encodedFrameCount")
				stopDepthCameraRecording(activity, outputFile,  callback)

			} catch (e: Exception) {
				Log.e("startRecording", "❌ Recording failed", e)
				withContext(Dispatchers.Main) {
					isRecording = false
					startRecording = false
				}
			}
		}
	}

	private var isStopProcessing = false
	private fun stopDepthCameraRecording(activity: FragmentActivity, outputFile: File, callback: () -> Unit) {
		// 💡 [핵심 안전장치] 동기화 블록을 통해 중복 호출을 완벽하게 차단합니다.
		synchronized(this) {
			if (isStopProcessing) {
				Log.w("stopRecording", "⚠️ 이미 종료 처리가 진행 중입니다. 중복 호출을 무시합니다.")
				return
			}
			isStopProcessing = true
		}

		isRecording = false
		startRecording = false
		Log.v("stopDepthRecording", "stopDepthCameraRecording - 최초 1회 종료 프로세스 가동")

		try {
			// ✅ 순서 중요: encoder -> surface -> muxer
			videoEncoder?.let { encoder ->
				try { encoder.stop() } catch (e: Exception) { Log.e("stopRecording", "Error stopping encoder: ${e.message}") }
				encoder.release()
				videoEncoder = null
			}

			if (::inputSurface.isInitialized) {
				try { inputSurface.release() } catch (e: Exception) { Log.e("stopRecording", "Error releasing surface: ${e.message}") }
			}

			mediaMuxer?.let { muxer ->
				try { muxer.stop() } catch (e: Exception) { Log.e("stopRecording", "Error stopping muxer: ${e.message}") }
				muxer.release()
				mediaMuxer = null
			}

			Log.v("startRecording", "finish Recording")

			// 💡 Media3 Flip 작업은 파일 시스템 플러시 대기 및 내부 구조상 반드시 Main 스레드에서 출발해야 합니다.
			activity.lifecycleScope.launch(Dispatchers.Main) {
				delay(100) // 디스크 파일 락 해제 대기 마진

				if (!outputFile.exists() || outputFile.length().toInt() == 0) {
					Log.e("stopRecording", "❌ 디스크에 유효한 원본 mp4 파일이 생성되지 않았습니다.")
					callback()
					return@launch
				}

				val inputPath = outputFile.absolutePath
				val outputPath = File(activity.cacheDir, "mirrored_video.mp4").absolutePath

				Log.d("stopRecording", "🎬 Media3 좌우반전(Flip) 엔진 구동 시작")
				flipVideoHorizontally(activity, inputPath, outputPath) { success ->
					if (success) {
						// 저장 및 파일 정리는 IO 스레드에서 무겁지 않게 처리
						activity.lifecycleScope.launch(Dispatchers.IO) {
							saveMediaToStorage(
								activity,
								Uri.fromFile(File(outputPath)),
								getFileName(currentSeq),
								flh!!,
								plh!!,
								false
							)

							// 사용이 끝난 캐시 내부 임시 파일 정리
							try {
								outputFile.delete()
								File(outputPath).delete()
							} catch(e: Exception) {}

							withContext(Dispatchers.Main) {
								callback()
							}
						}
					} else {
						Log.e("flip", "Failed to apply mirror effect to video.")
						callback()
					}
				}
			}

		} catch (e: Exception) {
			Log.e("stopRecording", "Error stopping recording: ${e.message}", e)
			callback()
		}
	}

	suspend fun processAndSaveFrameData(
		seq: Sequence,
		activity: FragmentActivity,
		bitmap: Bitmap,
		depthData: ShortArray?,
		depthW: Int,
		depthH: Int,
		face2D: FaceLandmarkResult? // 갱신된 시점의 랜드마크를 받음
	): Boolean {
		val name = getFileName(seq)
		val params = mDevice?.calibrationCameraParamList

		// 1. 3D Landmark 계산
		val undistortedPoints: List<Pair<Float, Float>>? = if (face2D != null && params != null) {
			val paramsUnit = params.getCameraParam(0)
			val rawPoints = face2D.landmarks.map { Pair(it.x * depthW, it.y * depthH) }
			val rotatedIntrinsic = paramsUnit.depthIntrinsic.createRotated90()
			val depthIntrc = rotatedIntrinsic.toScaledIntrinsic()
			undistortLandmarks(rawPoints, depthIntrc, depthDistortion!!)
		} else null
		if (face2D != null && params != null && depthData != null) {
			val paramsUnit = params.getCameraParam(0)
			val depthIntrc = paramsUnit.depthIntrinsic.toRotated90ScaledIntrinsic()
			latestIntrinsic = depthIntrc
			val landmarks3D = convertFaceLandmarksTo3D(
				face2D,
				depthData,
				depthW,
				depthH,
				depthIntrc, // Intrinsic 재배열 후 넣기
				depthDistortion!!
			)

			face3DResult = landmarks3D
			Log.d("processSaveFrame", "✅ ${seq} 3D landmark 계산 완료: ${landmarks3D.size}개")
		} else {
			Log.w("processSaveFrame", "⚠️ face2D 또는 params 없음, 3D 계산 스킵")
		}

		// D2C align 확인용 로그 (한 번 찍어보세요)
		val width = bitmap.width
		val height = bitmap.height
		Log.d("processSaveFrame", "✅ 크기: ${width}x${height}")

		face2D?.let { result ->
			val noseIndexes = listOf(1, 4, 5)
			noseIndexes.forEach { idx ->
				val lm = result.landmarks[idx]
				val px = (lm.x * depthW).toInt().coerceIn(0, depthW - 1)
				val py = (lm.y * depthH).toInt().coerceIn(0, depthH - 1)

				val sb = StringBuilder()
				for (dy in -2..2) {
					for (dx in -2..2) {
						val xx = (px + dx).coerceIn(0, depthW - 1)
						val yy = (py + dy).coerceIn(0, depthH - 1)
						val d = depthData?.get(yy * depthW + xx)?.toInt()?.and(0xFFFF) ?: -1
						sb.append("$d ")
					}
					sb.append("| ")
				}
			}
		}
		val faceMaskPath = undistortedPoints?.let { pts ->
			buildFaceMaskPathFromPoints(pts, faceOutLineIndexes)
		}
		Log.d("faceMaskPath", "undistortedPoints ${undistortedPoints} | faceMaskPath=${faceMaskPath}")

		val tempFile = File.createTempFile("color_capture", ".webp", activity.cacheDir)
		FileOutputStream(tempFile).use { fos ->
			bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 100, fos)
		}
		val tempUri = Uri.fromFile(tempFile)
		Log.d("captureImage", "flh=${flh}  plh=${plh}")

		if (flh != null && plh!= null) {
			val success = saveMediaToStorage(activity, tempUri, name, flh!!, plh!!, true)
			Log.v("saveMedia", "afterMoire $success")
		}

		Log.v("순서확인", "${mediaFileNames} | ${coordinatesJA.length()}, ${plrJA.length()}")

		return false
	}

	fun resultBundleToJson(
		seq: Sequence,
		face2D: FaceLandmarkResult?,
		face3D: List<Point3D>?
	) {
		Log.v("개구상태?", "$seq : ${face3D?.size ?: 0}")
		if (face2D?.landmarks?.isNotEmpty() == true) {
			val flr = face2D.landmarks

			// 초기화
			tempCoordinateJA = JSONArray()
			currentCoordinate.clear()
			relativeCoordinate.clear()
			current3DCoordinate.clear()

			flr.forEachIndexed { index, faceLandmark ->
				val scaledX = calculateScreenX(1f - faceLandmark.x)
				val scaledY = calculateScreenY(faceLandmark.y)

				val x = (face3D?.getOrNull(index)?.x ?: 0f).safe()
				val y = (face3D?.getOrNull(index)?.y ?: 0f).safe()
				val z = (face3D?.getOrNull(index)?.z ?: 0f).safe()

				if (seq == Sequence.JAW_OPENING && index in listOf(13, 14)) {
					Log.v("xyz보기", "$index: ($x $y $z), ${face3D?.size}")
				}

				// 이미 NaN이 0f로 정제되었으므로 안전하게 반올림 연산 수행
				val roundedX = safeRound(scaledX, 1)
				val roundedY = safeRound(scaledY, 1)
				val rounded3DX = safeRound(x, 4)
				val rounded3DY = safeRound(y, 4)
				val rounded3DZ = safeRound(z, 4)

				val ja = JSONArray().apply {
					put(roundedX)
					put(roundedY)
					put(rounded3DX)
					put(rounded3DY)
					put(rounded3DZ)
				}
				tempCoordinateJA.put(ja)

				// 절대좌표
				currentCoordinate.add(Pair(scaledX, scaledY))
				// 상대좌표
				relativeCoordinate.add(Pair(1f - faceLandmark.x, faceLandmark.y))

				// 💡 이제 안전하게 정제된 Float 값(x, y, z)이 들어가므로,
				// 이를 활용한 모든 2차 연산(거리, 면적 등)에서 NaN이 완전히 박멸됩니다.
				if (isDepthCameraMeasure) {
					current3DCoordinate.add(Triple(x, y, z))
				}
			}
			// seq 1개의 좌표를 담고 tempCoordinates는 초기화
			coordinatesJA.put(tempCoordinateJA)

			// ---------# 편한 상태 #----------
			val vmFlr = relativeCoordinate
			val flr3D = current3DCoordinate
			// ℹ️ℹ️ OPENING ️ℹ️ℹ️
			if (seq == Sequence.JAW_OPENING) {
				val openingLipsDistance = if (isDepthCameraMeasure) {
//					Log.v(
//						"뎁스카메라",
//						"5-OpeningLips: 13-${current3DCoordinate[13]}, 14-${current3DCoordinate[14]}, ${
//							get3DDistance(
//								current3DCoordinate[13],
//								current3DCoordinate[14]
//							)
//						}"
//					)
					get3DDistance(flr3D[13], flr3D[14])
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
						calculateAreaWithScaling(leftCheeks, currentCoordinate, flr3D),
						calculateAreaWithScaling(rightCheeks, currentCoordinate, flr3D),
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
			Log.d("saveJson", "✅ [$seq] 랜드마크 JSON 구조화 완료 coordinatesJA 길이: ${coordinatesJA.length()}")
		}
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
//		val cropEffect = Crop(-0.421875f, 0.421875f, -1f, 1f)
//		val presentation = Presentation.createForWidthAndHeight(
//			540, 720, Presentation.LAYOUT_SCALE_TO_FIT
//		)
		val flipEffect = ScaleAndRotateTransformation.Builder()
			.setScale(-1f, 1f)
			.build()
		val effects = Effects(
			listOf(ChannelMixingAudioProcessor()),
			listOf( flipEffect ) // cropEffect, presentation
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
	fun Float.safe(): Float = if (this.isNaN() || this.isInfinite()) 0f else this
}