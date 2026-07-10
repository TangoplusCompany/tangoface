package com.tangoplus.facebeautyexpert.domain.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.icu.text.DecimalFormat
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.tangoplus.facebeautyexpert.ui.info.vo.DrawLine
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkResult.Companion.fromFaceCoordinates
import com.tangoplus.facebeautyexpert.domain.vo.FaceResult
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.getPathFromContentUri
import com.tangoplus.facebeautyexpert.domain.vision.pose.PoseLandmarkResult.Companion.fromPoseCoordinates
import com.tangoplus.facebeautyexpert.ui.info.vm.InformationViewModel
import kotlinx.coroutines.Dispatchers
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.extractBowCoordinates
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.extractFace3DCoordinates
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.extractFaceCoordinates
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.extractHairLine3DCoordinate
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.extractHairLineCoordinate
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.extractPose3DCoordinates
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.extractPoseCoordinates
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.scaleHsvToFHD
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.scaleLandmarksToFHD
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkResult
import com.tangoplus.facebeautyexpert.domain.vo.BitmapFlag
import com.tangoplus.facebeautyexpert.domain.vo.Landmarks
import com.tangoplus.facebeautyexpert.domain.vo.Sequence
import com.tangoplus.facebeautyexpert.ui.info.vo.LineType
import com.tangoplus.facebeautyexpert.ui.view.TextBoxOverlayView
import kotlinx.coroutines.withContext

object BitmapUtility {
	data class DrawImageUiModel(
		val fragment: Fragment,
		val originalBitmap: Bitmap,
		val faceResult: FaceResult,
		val setedLine: MutableSet<DrawLine>,
		val seq: Sequence,
		val lms : Landmarks,
		val flags: BitmapFlag,
	)

	suspend fun getCombineBitmap(
		fragment: Fragment,
		faceResult: FaceResult,
		seq: Sequence,
		ivm: InformationViewModel,
	): Triple<Bitmap?, List<TextBoxData>, TextBoxOverlayView.FaceRect?> = withContext(Dispatchers.Default) {
		try {
			val seqs = faceResult.seqs
			val seqIndex = seqs.indexOf(seq)

			if (seqIndex == -1) {
				return@withContext Triple(createInfoBitmap(fragment.context, 0), listOf(), null)
			}
			val imageUri = faceResult.mediaUri.getOrNull(seqIndex) ?:
			return@withContext Triple(createInfoBitmap(fragment.context, 0), listOf(), null)


			val path = getPathFromContentUri(fragment.requireContext(), imageUri)

			val bitmap = BitmapFactory.decodeStream(
				fragment.requireContext().contentResolver.openInputStream(imageUri)
			)?.let { originalBitmap ->
				// ⭐ 원본 크기 저장
				val originalWidth = originalBitmap.width
				val originalHeight = originalBitmap.height
				Log.d("BitmapSize", "Original bitmap size: ${originalWidth}x${originalHeight}")

				val processedBitmap = if (originalWidth <= 1280 && originalHeight <= 720) {
					originalBitmap.scale(1920, 1080)
				} else {
					originalBitmap
				}

				// ⭐ 원본 크기와 처리된 비트맵을 Pair로 반환
				Pair(processedBitmap, Pair(originalWidth, originalHeight))
			} ?: return@withContext Triple(createInfoBitmap(fragment.context, 0), listOf(), null)

			val processedBitmap = bitmap.first
			val (originalWidth, originalHeight) = bitmap.second
			val jsonData = faceResult.results.getJSONObject(seqIndex)
			val faceCoordinates = extractFaceCoordinates(jsonData)
			val face3DCoordinates = extractFace3DCoordinates(jsonData)
			val poseCoordinates = extractPoseCoordinates(jsonData)
			val pose3DCoordinates = extractPose3DCoordinates(jsonData)
			val bowCoordinates = extractBowCoordinates(jsonData)
			val hairLineCoordinate = extractHairLineCoordinate(jsonData)
			val hairLine3DCoordinate = extractHairLine3DCoordinate(jsonData)
			val faceLandmarkResult = fromFaceCoordinates(faceCoordinates)
			val poseLandmarkResult = fromPoseCoordinates(poseCoordinates)

			val (scaledFLs, scaledPLs, scaledHairline) = scaleLandmarksToFHD(
				faceLandmarkResult,
				poseLandmarkResult,
				hairLineCoordinate,
				originalWidth,
				originalHeight
			)
			val lms = Landmarks(
				scaledFLs!!,
				face3DCoordinates,
				scaledPLs!!,
				pose3DCoordinates,
				scaleHsvToFHD(bowCoordinates),
				scaledHairline,
				hairLine3DCoordinate
			)
			val isLineOn = ivm.getLineOn()
			val isGridLineOn = ivm.getGridLineOn()
			val isMoireOn = ivm.getMoireOn()
			val flags = BitmapFlag(
				isLineOn = isLineOn,
				isGridLineOn = isGridLineOn,
				isMoireOn = isMoireOn
			)
			val (combinedBitmap, textBoxes, faceRect) = combineImageAndOverlay(
				DrawImageUiModel(
					fragment,
					processedBitmap,
					faceResult,
					setedLine = ivm.currentCheckedLines,
					seq = seq,
					lms = lms,
					flags = flags
				)
			)

			val isPortrait =
				fragment.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
			val upscaledBitmap = upscaleImage(combinedBitmap, 2.25f)
			val finalBitmap = if (isPortrait) {
				val width = upscaledBitmap.width
				val height = upscaledBitmap.height

				if (width > height) {
					val targetWidth = (height * 3f / 4f).toInt()
					val x = (width - targetWidth) / 2
					Log.d("BitmapCrop", "Cropping from ${width}x${height} to ${targetWidth}x${height}")
					Bitmap.createBitmap(upscaledBitmap, x, 0, targetWidth, height)
				} else {
					upscaledBitmap
				}
			} else {
				upscaledBitmap
			}
			val targetWidth = upscaledBitmap.width // 1080
			val targetHeight = (targetWidth * 4) / 3 // 1440
			val startY = (upscaledBitmap.height - targetHeight) / 2
			val centerCroppedBitmap = Bitmap.createBitmap(upscaledBitmap, 0, startY, targetWidth, targetHeight)

			Triple(centerCroppedBitmap.copy(centerCroppedBitmap.config ?: Bitmap.Config.ARGB_8888, true), textBoxes, faceRect)

		} catch (e: Exception) {
			Log.e("getCombineBitmap", "Error: ${e.message}", e)
			Triple(null, listOf(), null)
		}
	}

	fun setSSIV(bt: Bitmap, ssiv: SubsamplingScaleImageView) {
		ssiv.setImage(ImageSource.bitmap(bt))
		ssiv.maxScale = 3.5f
		ssiv.minScale = 1f
		ssiv.setOnImageEventListener(object : SubsamplingScaleImageView.OnImageEventListener {
			override fun onReady() {  }
			override fun onImageLoaded() {
			}
			override fun onPreviewLoadError(e: Exception?) {
				Log.e("setSSIV", "Preview 에러", e)
			}
			override fun onImageLoadError(e: Exception?) {
				Log.e("setSSIV", "Image 에러", e)
			}
			override fun onTileLoadError(e: Exception?) {}
			override fun onPreviewReleased() {}
		})
	}

	private val strokeWidthh = 1f
	val leftCheekIndex = listOf(234, 196, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93)
	val rightCheekIndex = listOf(454, 419, 152, 377, 400, 378, 379, 365, 397, 288, 361, 323) // 오른쪽
	val leftCheekBoneIndex = listOf(346, 347, 330, 425, 411, 352)
	val rightCheekBoneIndex = listOf(117, 118, 101, 205, 187, 123)
	val faceOutLineIndexes = listOf(
		10, 109, 67, 103, 54, 21, 162, 127, 234, 93, 132, 58, 172, 136, 150, 149, 176, 148,
		152, 377, 378, 379, 365, 397, 288, 361, 323, 454, 356, 389, 251, 284, 332, 297, 338, 10
	)
	val whiteAlpha15Paint = Paint().apply {
		color = "#25FFFFFF".toColorInt()
		strokeWidth = strokeWidthh
		strokeCap = Paint.Cap.ROUND
		style = Paint.Style.STROKE
	}

	val whitePaint = Paint().apply {
		color = "#FFFFFF".toColorInt()
		strokeWidth = strokeWidthh
		strokeCap = Paint.Cap.ROUND
		style = Paint.Style.STROKE
	}
	val orangeAlphaPaint = Paint().apply {
		color = "#1AFFAE00".toColorInt()
		strokeWidth = strokeWidthh
		strokeCap = Paint.Cap.ROUND
		style = Paint.Style.FILL
	}
	val orangePaint = Paint().apply {
		color = "#D9FFAE00".toColorInt()
		strokeWidth = strokeWidthh
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
	}
	val yellowPaint = Paint().apply {
		color = "#FFEA00".toColorInt()
		strokeWidth = strokeWidthh
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
	}
	val bluePaint = Paint().apply {
		color = "#0051FF".toColorInt()
		strokeWidth = strokeWidthh
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
	}
	val redPaint = Paint().apply {
		color = "#FF4242".toColorInt()
		strokeWidth = strokeWidthh
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
	}
	val greenPaint = Paint().apply {
		color = "#00FF48".toColorInt()
		strokeWidth = strokeWidthh
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
	}
	fun combineImageAndOverlay(
		dim: DrawImageUiModel
	): Triple<Bitmap, List<TextBoxData>, TextBoxOverlayView.FaceRect> {

		val matrix = Matrix().apply {
			preScale(-1f, 1f)
		}
		val flippedBitmap = Bitmap.createBitmap(
			dim.originalBitmap,
			0,
			0,
			dim.originalBitmap.width,
			dim.originalBitmap.height,
			matrix,
			true
		)
		val resultBitmap = flippedBitmap.copy(Bitmap.Config.ARGB_8888, true)
		val canvas = Canvas(resultBitmap)
		// ----------# 선택한 항목이 없을 때 기본 축 #------------
		val dimPLS = dim.lms.poseLandmarks
		val dimFLS = dim.lms.faceLandmarks
		if (dim.flags.isGridLineOn) {
			val gridSize = 25f  // 그리드 간격 (픽셀)

			// 세로 선 그리기
			var x = 0f
			while (x <= canvas.width) {
				canvas.drawLine(x, 0f, x, canvas.height.toFloat(), whiteAlpha15Paint)
				x += gridSize
			}

			// 가로 선 그리기
			var y = 0f
			while (y <= canvas.height) {
				canvas.drawLine(0f, y, canvas.width.toFloat(), y, whiteAlpha15Paint)
				y += gridSize
			}
		}

		if (dim.flags.isLineOn && dimFLS.landmarks.isNotEmpty()) {
			if (dim.setedLine.contains(DrawLine.A_SAGGING_FACE)) {
				val leftEarFlap = dimFLS.landmarks[234]
				val rightEarFlap = dimFLS.landmarks[454]
				val leftMandibularBone = dimFLS.landmarks[58]
				val rightMandibularBone = dimFLS.landmarks[288]
				val midChin = dimFLS.landmarks[152]
				val leftCheeks = listOf(93, 132, 58, 172, 136,150 ,149, 176, 148)
				val rightCheeks = listOf(323, 361, 288, 397, 365, 379, 378, 400, 377)

				val leftCheeksLm = leftCheeks.map { dimFLS.landmarks[it] }
				val rightCheeksLm = rightCheeks.map { dimFLS.landmarks[it] }
				drawSmoothCurve(canvas, leftCheeksLm, redPaint)
				drawSmoothCurve(canvas, rightCheeksLm, redPaint)
				val x00 = leftEarFlap.x
				val y00 = leftEarFlap.y
				val x01 = rightEarFlap.x
				val y01 = rightEarFlap.y
				val x10 = leftMandibularBone.x
				val y10 = leftMandibularBone.y
				val x11 = rightMandibularBone.x
				val y11 = rightMandibularBone.y
				val x12 = midChin.x
				val y12 = midChin.y

				drawExtendedLine(canvas, x00, y00, x10, y10, 0f, 0f, orangePaint)
				drawExtendedLine(canvas, x10, y10, x12, y12, 0f, 0f, orangePaint)
				drawExtendedLine(canvas, x12, y12, x11, y11, 0f, 0f, orangePaint)
				drawExtendedLine(canvas, x11, y11, x01, y01, 0f, 0f, orangePaint)
			}

			if (dim.setedLine.contains(DrawLine.A_SHOULDER)) {
				val topLm0 = dimPLS.landmarks[11]
				val bottomLm0 = dimPLS.landmarks[12]
				val x00 = topLm0.x
				val y00 = topLm0.y
				val x01 = bottomLm0.x
				val y01 = bottomLm0.y
				drawExtendedLine(canvas, x00, y00, x01, y01, 0f, 0f, whitePaint, )
			}

			if (dim.setedLine.contains(DrawLine.NECK)) {
				val topLm0 = dimPLS.landmarks[11]
				val bottomLm0 = dimPLS.landmarks[12]
				val middleShoulderX = (topLm0.x + bottomLm0.x) / 2
				val middleShoulderY = (topLm0.y + bottomLm0.y) / 2
				val nose = dimFLS.landmarks[1]
				val x00 = middleShoulderX
				val y00 = middleShoulderY
				val x01 = nose.x
				val y01 = nose.y
				drawExtendedLine(
					canvas,
					x00,
					y00,
					x01,
					y01,
					0f,
					100f,
					whitePaint,
				)
			}

			if (dim.setedLine.contains(DrawLine.FACE)) {
				// height
				val topLm0 = dimFLS.landmarks[152]
				val bottomLm0 = dimFLS.landmarks[10]
				val x00 = topLm0.x
				val y00 = topLm0.y
				val x01 = bottomLm0.x
				val y01 = bottomLm0.y
				drawExtendedLine(
					canvas,
					x00,
					y00,
					x01,
					y01,
					150f,
					150f,
					redPaint,
				)


				// width
				val leftIndex = listOf(162, 127,234, 93, 132, 58, 172, 136)
				val rightIndex=  listOf(389, 356, 454, 323, 361, 288, 397, 365)
				val smallestLeft = leftIndex.map { dimFLS.landmarks[it] }.minBy { it.x }
				val largestRight = rightIndex.map { dimFLS.landmarks[it] }.maxBy { it.x }

				val x10 = smallestLeft.x
				val y10 = smallestLeft.y
				val x11 = largestRight.x
				val y11 = largestRight.y
				drawExtendedLine(
					canvas,
					x10,
					(y10 + y11) / 2,
					x11,
					(y10 + y11) / 2,
					0f,
					0f,
					yellowPaint,
					lineType = LineType.LENGTH
				)
			}

			if (dim.setedLine.contains(DrawLine.A_HORIZON)) {
				val leftLm0 = dimFLS.landmarks[33]
				val rightLm0 = dimFLS.landmarks[263]
				val x00 = leftLm0.x
				val y00 = leftLm0.y
				val x01 = rightLm0.x
				val y01 = rightLm0.y
				drawExtendedLine(
					canvas,
					x00,
					y00,
					x01,
					y01,
					100f,
					100f,
					whitePaint,
				)

				val leftLm1 = dimFLS.landmarks[234]
				val rightLm1 = dimFLS.landmarks[454]
				val x10 = leftLm1.x
				val y10 = leftLm1.y
				val x11 = rightLm1.x
				val y11 = rightLm1.y
				drawExtendedLine(
					canvas,
					x10,
					y10,
					x11,
					y11,
					100f,
					100f,
					whitePaint,
					lineType = LineType.ANGLE
				)

				val leftLm2 = dimFLS.landmarks[61]
				val rightLm2 = dimFLS.landmarks[291]
				val x20 = leftLm2.x
				val y20 = leftLm2.y
				val x21 = rightLm2.x
				val y21 = rightLm2.y

				val centerX = (leftLm2.x + rightLm2.x) / 2
				val centerY = (leftLm2.y + rightLm2.y) / 2

				drawExtendedLine(
					canvas,
					centerX,
					centerY,
					x20,
					y20,
					100f,
					100f,
					whitePaint,
				)
				drawExtendedLine(canvas, centerX, centerY, x21, y21, 100f, 100f, whitePaint)


				val topLm0 = dimFLS.landmarks[152]
				val bottomLm0 = dimFLS.landmarks[152]
				val x30 = topLm0.x
				val y30 = topLm0.y
				val x31 = bottomLm0.x + 1
				val y31 = bottomLm0.y
				drawExtendedLine(canvas, x30, y30, x31, y31, 100f, 100f, whitePaint, )
			}

			// ✅✅✅✅✅✅
			if (dim.setedLine.contains(DrawLine.EYE)) {
				val leftLm1 = dimFLS.landmarks[133]
				val rightLm1 = dimFLS.landmarks[33]
				val x10 = leftLm1.x
				val y10 = leftLm1.y
				val x11 = rightLm1.x
				val y11 = rightLm1.y
				val leftLm2 = dimFLS.landmarks[263]
				val rightLm2 = dimFLS.landmarks[362]
				val x20 = leftLm2.x
				val y20 = leftLm2.y
				val x21 = rightLm2.x
				val y21 = rightLm2.y
				drawExtendedLine(canvas, x10, y10, x11, y11, 0f, 0f, redPaint, lineType = LineType.ANGLE)
				drawExtendedLine(canvas, x20, y20, x21, y21, 0f, 0f, redPaint, lineType = LineType.ANGLE)

				// 왼쪽 눈꺼풀들
				val leftLm3 = dimFLS.landmarks[33]
				val rightLm3 = dimFLS.landmarks[130]
				val x30 = leftLm3.x
				val y30 = leftLm3.y
				val x31 = rightLm3.x
				val y31 = rightLm3.y
				val leftLm4 = dimFLS.landmarks[243]
				val rightLm4 = dimFLS.landmarks[133]
				val x40 = leftLm4.x
				val y40 = leftLm4.y
				val x41 = rightLm4.x
				val y41 = rightLm4.y
				drawExtendedLine(canvas, x30, y30, x31, y31, 0f, 10f, orangePaint, lineType = LineType.ANGLE)
				drawExtendedLine(canvas, x40, y40, x41, y41, 0f, 10f, orangePaint, lineType = LineType.ANGLE)

				// 오른쪽 눈꺼풀들
				val leftLm5 = dimFLS.landmarks[362]
				val rightLm5 = dimFLS.landmarks[463]
				val x50 = leftLm5.x
				val y50 = leftLm5.y
				val x51 = rightLm5.x
				val y51 = rightLm5.y
				val leftLm6 = dimFLS.landmarks[359]
				val rightLm6 = dimFLS.landmarks[263]
				val x60 = leftLm6.x
				val y60 = leftLm6.y
				val x61 = rightLm6.x
				val y61 = rightLm6.y
				drawExtendedLine(canvas, x50, y50, x51, y51, 0f, 10f, orangePaint, lineType = LineType.ANGLE)
				drawExtendedLine(canvas, x60, y60, x61, y61, 0f, 10f, orangePaint, lineType = LineType.ANGLE)

				// length
				val leftLm7 = dimFLS.landmarks[133]
				val rightLm7 = dimFLS.landmarks[33]
				val x70 = leftLm7.x
				val y70 = leftLm7.y
				val x71 = rightLm7.x
				val y71 = rightLm7.y
				drawExtendedLine(canvas, x70, ((y70 + y71) / 2) - 5, x71, ((y70 + y71) / 2) - 5, 0f, 0f, yellowPaint, lineType = LineType.LENGTH)
//			drawExtendedLine(canvas, (x10 + x11) / 2, y10, (x10 + x11) / 2, y11, 0f, 0f, yellowPaint, lineType = LineType.LENGTH)
				val leftLm8 = dimFLS.landmarks[159]
				val rightLm8 = dimFLS.landmarks[145]
				val x80 = leftLm8.x
				val y80 = leftLm8.y
				val x81 = rightLm8.x
				val y81 = rightLm8.y
//			drawExtendedLine(canvas, x20, (y20 + y21) / 2, x21, (y20 + y21) / 2, 0f, 0f, yellowPaint, lineType = LineType.LENGTH)
				drawExtendedLine(canvas, (x80 + x81) / 2, y80, (x80 + x81) / 2, y81, 0f, 0f, yellowPaint, lineType = LineType.LENGTH)

				val leftLm9 = dimFLS.landmarks[362]
				val rightLm9 = dimFLS.landmarks[263]
				val x90 = leftLm3.x
				val y90 = leftLm3.y
				val x91 = rightLm3.x
				val y91 = rightLm3.y
				drawExtendedLine(canvas, x90, ((y90 + y91) / 2) - 5, x91, ((y90 + y91) / 2) -5 , 0f, 0f, yellowPaint, lineType = LineType.LENGTH)
//			drawExtendedLine(canvas, (x30 + x31) / 2, y30, (x30 + x31) / 2, y31, 0f, 0f, yellowPaint, lineType = LineType.LENGTH)
				val leftLm10 = dimFLS.landmarks[386]
				val rightLm10 = dimFLS.landmarks[374]
				val x100 = leftLm10.x
				val y100 = leftLm10.y
				val x101 = rightLm10.x
				val y101 = rightLm10.y
//			drawExtendedLine(canvas, x40, (y40 + y41) / 2, x41, (y40 + y41) / 2, 0f, 0f, yellowPaint, lineType = LineType.LENGTH)
				drawExtendedLine(canvas, (x100 + x101) / 2, y100, (x100 + x101) / 2, y101, 0f, 0f, yellowPaint, lineType = LineType.LENGTH)


			}
			if (dim.setedLine.contains(DrawLine.L_CORNEA)) {
				val leftLm0 = dimFLS.landmarks[470]
				val rightLm0 = dimFLS.landmarks[472]
				val x00 = leftLm0.x
				val y00 = leftLm0.y
				val x01 = rightLm0.x
				val y01 = rightLm0.y
				val leftLm1 = dimFLS.landmarks[475]
				val rightLm1 = dimFLS.landmarks[477]
				val x10 = leftLm1.x
				val y10 = leftLm1.y
				val x11 = rightLm1.x
				val y11 = rightLm1.y
				drawExtendedLine(
					canvas,
					x00 + 5,
					y00,
					x01 + 5 ,
					y01,
					0f,
					0f,
					redPaint,
					lineType = LineType.LENGTH
				)
				drawExtendedLine(
					canvas,
					x10,
					y10,
					x11,
					y11,
					0f,
					0f,
					redPaint,
					lineType = LineType.LENGTH
				)
			}

			if (dim.setedLine.contains(DrawLine.EYEBROW)) {
				val leftLm00 = dimFLS.landmarks[55]
				val leftLm01 = dimFLS.landmarks[107]
				val leftLm10 = dimFLS.landmarks[52]
				val leftLm11 = dimFLS.landmarks[105]

				val x000 = leftLm00.x
				val y000 = leftLm00.y
				val x001 = leftLm01.x
				val y001 = leftLm01.y

				val x010 = leftLm10.x
				val y010 = leftLm10.y
				val x011 = leftLm11.x
				val y011 = leftLm11.y
				val centerX00 = (x000 + x001) / 2
				val centerY00 = (y000 + y001) / 2
				val centerX01 = (x010 + x011) / 2
				val centerY01 = (y010 + y011) / 2
				drawExtendedLine(
					canvas,
					centerX00,
					centerY00,
					centerX01,
					centerY01,
					0f,
					0f,
					redPaint,
					lineType = LineType.ANGLE
				)

				val rightLm00 = dimFLS.landmarks[285]
				val rightLm01 = dimFLS.landmarks[336]
				val rightLm10 = dimFLS.landmarks[282]
				val rightLm11 = dimFLS.landmarks[334]

				val x100 = rightLm00.x
				val y100 = rightLm00.y
				val x101 = rightLm01.x
				val y101 = rightLm01.y
				val x110 = rightLm10.x
				val y110 = rightLm10.y
				val x111 = rightLm11.x
				val y111 = rightLm11.y
				val centerX10 = (x100 + x101) / 2
				val centerY10 = (y100 + y101) / 2
				val centerX11 = (x110 + x111) / 2
				val centerY11 = (y110 + y111) / 2

				drawExtendedLine(canvas,
					centerX10,
					centerY10,
					centerX11,
					centerY11,
					0f,
					0f,
					redPaint,
					lineType = LineType.ANGLE
				)


				val leftLm0 = dimFLS.landmarks[55]
				val rightLm0 = dimFLS.landmarks[107]
				val x00 = leftLm0.x
				val y00 = leftLm0.y
				val x01 = rightLm0.x
				val y01 = rightLm0.y
				val leftLm1 = dimFLS.landmarks[52]
				val rightLm1 = dimFLS.landmarks[105]
				val x10 = leftLm1.x
				val y10 = leftLm1.y
				val x11 = rightLm1.x
				val y11 = rightLm1.y
				val leftLm2 = dimFLS.landmarks[46]
				val rightLm2 = dimFLS.landmarks[70]
				val x20 = leftLm2.x
				val y20 = leftLm2.y
				val x21 = rightLm2.x
				val y21 = rightLm2.y

				drawExtendedLine(
					canvas,
					x00,
					y00,
					x01,
					y01,
					0f,
					0f,
					yellowPaint,
					lineType = LineType.LENGTH
				)
				drawExtendedLine(
					canvas,
					x10,
					y10,
					x11,
					y11,
					0f,
					0f,
					yellowPaint,
					lineType = LineType.LENGTH
				)
				drawExtendedLine(
					canvas,
					x20,
					y20,
					x21,
					y21,
					0f,
					0f,
					yellowPaint,
					lineType = LineType.LENGTH
				)

				//👉👉👉👉👉👉👉👉👉👉👉👉👉👉👉👉
				val leftLm3 = dimFLS.landmarks[285]
				val rightLm3 = dimFLS.landmarks[336]
				val x30 = leftLm3.x
				val y30 = leftLm3.y
				val x31 = rightLm3.x
				val y31 = rightLm3.y
				val leftLm4 = dimFLS.landmarks[282]
				val rightLm4 = dimFLS.landmarks[334]
				val x40 = leftLm4.x
				val y40 = leftLm4.y
				val x41 = rightLm4.x
				val y41 = rightLm4.y
				val leftLm5 = dimFLS.landmarks[276]
				val rightLm5 = dimFLS.landmarks[300]
				val x50 = leftLm5.x
				val y50 = leftLm5.y
				val x51 = rightLm5.x
				val y51 = rightLm5.y

				drawExtendedLine(
					canvas,
					x30,
					y30,
					x31,
					y31,
					0f,
					0f,
					yellowPaint,
					lineType = LineType.LENGTH
				)
				drawExtendedLine(
					canvas,
					x40,
					y40,
					x41,
					y41,
					0f,
					0f,
					yellowPaint,
					lineType = LineType.LENGTH
				)
				drawExtendedLine(
					canvas,
					x50,
					y50,
					x51,
					y51,
					0f,
					0f,
					yellowPaint,
					lineType = LineType.LENGTH
				)
			}


			if (dim.setedLine.contains(DrawLine.NOSE)) {
				val leftLm0 = dimFLS.landmarks[220]
				val rightLm0 = dimFLS.landmarks[48]
				val x00 = leftLm0.x
				val y00 = leftLm0.y
				val x01 = rightLm0.x
				val y01 = rightLm0.y
				val leftLm1 = dimFLS.landmarks[278]
				val rightLm1 = dimFLS.landmarks[440]
				val x10 = leftLm1.x
				val y10 = leftLm1.y
				val x11 = rightLm1.x
				val y11 = leftLm1.y
				val leftLm2 = dimFLS.landmarks[440]
				val rightLm2 = dimFLS.landmarks[220]
				val x20 = leftLm2.x
				val y20 = leftLm2.y
				val x21 = rightLm2.x
				val y21 = rightLm2.y

				drawExtendedLine(
					canvas,
					x00,
					((y20+ y21) / 2) + 50,
					x01,
					((y20+ y21) / 2) + 50,
					0f,
					0f,
					redPaint,
					lineType = LineType.ANGLE
				)
				drawExtendedLine(
					canvas,
					x10,
					((y20+ y21) / 2) + 50,
					x11,
					((y20+ y21) / 2) + 50,
					0f,
					0f,
					redPaint,
					lineType = LineType.ANGLE
				)
				drawExtendedLine(
					canvas,
					x20,
					((y20+ y21) / 2) + 30,
					x21,
					((y20+ y21) / 2) + 30,
					0f,
					0f,
					redPaint,
					lineType = LineType.ANGLE
				)

				val leftLm3 = dimFLS.landmarks[168]
				val rightLm3 = dimFLS.landmarks[2]
				val x30 = leftLm3.x
				val y30 = leftLm3.y
				val x31 = rightLm3.x
				val y31 = rightLm3.y
				val leftLm4 = dimFLS.landmarks[278]
				val rightLm4 = dimFLS.landmarks[48]
				val x40 = leftLm4.x
				val y40 = leftLm4.y
				val x41 = rightLm4.x
				val y41 = rightLm4.y
				drawExtendedLine(
					canvas,
					x30,
					y30,
					x31,
					y31,
					0f,
					0f,
					yellowPaint,
					lineType = LineType.LENGTH
				)
				drawExtendedLine(
					canvas,
					x40,
					y40,
					x41,
					y41,
					0f,
					0f,
					yellowPaint,
					lineType = LineType.LENGTH
				)
			}

			if (dim.setedLine.contains(DrawLine.A_CANTHUS_ORAL)) {
				val topLm0 = dimFLS.landmarks[33]
				val bottomLm0 = dimFLS.landmarks[61]
				val x00 = topLm0.x
				val y00 = topLm0.y
				val x01 = bottomLm0.x + 1
				val y01 = bottomLm0.y
				drawExtendedLine(canvas, x00, y00, x01, y01, 0f, 100f, whitePaint)

				val topLm1 = dimFLS.landmarks[263]
				val bottomLm1 = dimFLS.landmarks[291]
				val x10 = topLm1.x
				val y10 = topLm1.y
				val x11 = bottomLm1.x
				val y11 = bottomLm1.y
				drawExtendedLine(canvas, x10, y10, x11, y11, 0f, 100f, whitePaint)
			}
			if (dim.setedLine.contains(DrawLine.A_ALAR_ORAL)) {
				val topLm0 = dimFLS.landmarks[64]
				val bottomLm0 = dimFLS.landmarks[61]
				val x00 = topLm0.x
				val y00 = topLm0.y
				val x01 = bottomLm0.x + 1
				val y01 = bottomLm0.y
				drawExtendedLine(canvas, x00, y00, x01, y01, 0f, 0f, redPaint, lineType = LineType.ANGLE)

				val topLm1 = dimFLS.landmarks[294]
				val bottomLm1 = dimFLS.landmarks[291]
				val x10 = topLm1.x
				val y10 = topLm1.y
				val x11 = bottomLm1.x
				val y11 = bottomLm1.y
				drawExtendedLine(canvas, x10, y10, x11, y11, 0f, 0f, redPaint, lineType = LineType.ANGLE)
			}
			if (dim.setedLine.contains(DrawLine.A_SIDE_HEAD)) {
				val leftTop = dimFLS.landmarks[156]
				val leftMid = dimFLS.landmarks[143]
				val leftBottom = dimFLS.landmarks[111]
				val x00 = leftTop.x
				val y00 = leftTop.y
				val x01 = leftMid.x
				val y01 = leftMid.y
				val x02 = leftBottom.x
				val y02 = leftBottom.y
				drawExtendedLine(canvas, x00, y00, x01, y01, 0f, 0f, whitePaint)
				drawExtendedLine(canvas, x01, y01, x02, y02, 0f, 0f, whitePaint)

				val rightTop = dimFLS.landmarks[383]
				val rightMid = dimFLS.landmarks[372]
				val rightBottom = dimFLS.landmarks[340]
				val x10 = rightTop.x
				val y10 = rightTop.y
				val x11 = rightMid.x
				val y11 = rightMid.y
				val x22 = rightBottom.x
				val y22 = rightBottom.y
				drawExtendedLine(canvas, x10, y10, x11, y11, 0f, 0f, whitePaint)
				drawExtendedLine(canvas, x11, y11, x22, y22, 0f, 0f, whitePaint)
			}

			if (dim.setedLine.contains(DrawLine.E_CHEEKS)) {
				val cheekPath0 = Path()
				leftCheekIndex.forEachIndexed { i, landmarkIndex ->
					val landmark = dimFLS.landmarks[landmarkIndex]
					val x = landmark.x
					val y = landmark.y

					if (i == 0) {
						cheekPath0.moveTo(x, y) // 시작점
					} else {
						cheekPath0.lineTo(x, y) // 선 연결
					}
				}
				canvas.drawPath(cheekPath0, orangeAlphaPaint)


				val cheekPath1 = Path()
				rightCheekIndex.forEachIndexed { i, landmarkIndex ->
					val landmark = dimFLS.landmarks[landmarkIndex]
					val x = landmark.x
					val y = landmark.y

					if (i == 0) {
						cheekPath1.moveTo(x, y) // 시작점
					} else {
						cheekPath1.lineTo(x, y) // 선 연결
					}
				}
				canvas.drawPath(cheekPath1, orangeAlphaPaint)
			}
			if (dim.setedLine.contains(DrawLine.E_CHEEKBONE)) {
				val cheekBonePath0 = Path()
				leftCheekBoneIndex.forEachIndexed { i, landmarkIndex ->
					val landmark = dimFLS.landmarks[landmarkIndex]
					val x = landmark.x
					val y = landmark.y

					if (i == 0) {
						cheekBonePath0.moveTo(x, y) // 시작점
					} else {
						cheekBonePath0.lineTo(x, y) // 선 연결
					}
				}
				canvas.drawPath(cheekBonePath0, orangeAlphaPaint)

				val cheekBonePath1 = Path()
				rightCheekBoneIndex.forEachIndexed { i, landmarkIndex ->
					val landmark = dimFLS.landmarks[landmarkIndex]
					val x = landmark.x
					val y = landmark.y

					if (i == 0) {
						cheekBonePath1.moveTo(x, y) // 시작점
					} else {
						cheekBonePath1.lineTo(x, y) // 선 연결
					}
				}
				canvas.drawPath(cheekBonePath1, orangeAlphaPaint)
			}

			if (dim.setedLine.contains(DrawLine.MOUSE)) {
				// 입꼬리 각도
				val midLm = dimFLS.landmarks[13]
				val topLm0 = dimFLS.landmarks[61]
				val bottomLm0 = dimFLS.landmarks[291]
				val x0 = midLm.x
				val y0 = midLm.y
				val x00 = topLm0.x
				val y00 = topLm0.y
				val x01 = bottomLm0.x
				val y01 = bottomLm0.y
				drawExtendedLine(
					canvas,
					x0,
					y0,
					x00,
					y00,
					0f,
					25f,
					redPaint,
					lineType = LineType.ANGLE
				)
				drawExtendedLine(
					canvas,
					x0,
					y0,
					x01,
					y01,
					0f,
					25f,
					redPaint,
					lineType = LineType.ANGLE
				)

				// 입술 너비
				drawExtendedLine(
					canvas,
					x00,
					y00 - 10,
					x01,
					y01 - 10,
					0f,
					0f,
					yellowPaint,
					lineType = LineType.LENGTH
				)

				val topLm2 = dimFLS.landmarks[2]
				val bottomLm2 = dimFLS.landmarks[0]
				val x20 = topLm2.x
				val y20 = topLm2.y
				val x21 = bottomLm2.x
				val y21 = bottomLm2.y
				drawExtendedLine(
					canvas,
					x20,
					y20,
					x21,
					y21,
					0f,
					0f,
					yellowPaint,
					lineType = LineType.LENGTH
				)

				// 윗입술 아랫입술 높이
				val topLm3 = dimFLS.landmarks[0]
				val bottomLm3 = dimFLS.landmarks[13]
				val x30 = topLm3.x
				val y30 = topLm3.y
				val x31 = bottomLm3.x
				val y31 = bottomLm3.y
				drawExtendedLine(
					canvas,
					x30 + 10,
					y30,
					x30 + 10,
					y31,
					0f,
					0f,
					yellowPaint,
					lineType = LineType.LENGTH
				)

				// 윗입술 아랫입술 높이
				val topLm4 = dimFLS.landmarks[14]
				val bottomLm4 = dimFLS.landmarks[17]
				val x40 = topLm4.x
				val y40 = topLm4.y
				val x41 = bottomLm4.x
				val y41 = bottomLm4.y
				drawExtendedLine(
					canvas,
					x40 + 10,
					y40,
					x40 + 10,
					y41,
					0f,
					0f,
					yellowPaint,
					lineType = LineType.LENGTH
				)

			}

			// ----------------------------------# 목 폄 상태 #--------------------------------------
			if (dim.setedLine.contains(DrawLine.A_FACIAL_AXIS)) {
				val lm0 = dimFLS.landmarks[168]
				val lm1= dimFLS.landmarks[2]
				val lm2 = dimFLS.landmarks[152]
				val x0 = lm0.x
				val y0 = lm0.y
				val x1 = lm1.x
				val y1 = lm1.y
				val x2 = lm2.x
				val y2 = lm2.y

				drawExtendedLine(
					canvas,
					x0,
					y0,
					x1,
					y1,
					0f,
					0f,
					greenPaint,
					lineType = LineType.ANGLE
				)
				drawExtendedLine(
					canvas,
					x1,
					y1,
					x2,
					y2,
					0f,
					0f,
					greenPaint,
					lineType = LineType.ANGLE
				)
			}

			// ----------------------------------# 비율 계산기 #------------------------------------
			if (dim.setedLine.contains(DrawLine.A_RATIO_VERTI)) {

				val leftFace = listOf(21, 162, 127, 234, 93, 132, 58)
				val rightFace = listOf(251, 390, 356, 454, 323, 361)

				val minLeftIndex = leftFace.maxByOrNull { index -> dimFLS.landmarks[index].x }
				val minRightIndex = rightFace.minByOrNull { index -> dimFLS.landmarks[index].x }
				val leftLm = minLeftIndex?.let { dimFLS.landmarks[it] }
				val rightLm = minRightIndex?.let { dimFLS.landmarks[it] }
				val x0 = leftLm?.x ?: 0f
				val y0 = leftLm?.y ?: 0f

				val x1 = rightLm?.x ?: 0f
				val y1 = rightLm?.y ?: 0f
				val leftEyeStart = dimFLS.landmarks[130]
				val leftEyeEnd = dimFLS.landmarks[133]

				val rightEyeStart = dimFLS.landmarks[362]
				val rightEyeEnd = dimFLS.landmarks[359]

				val x2 = leftEyeStart.x
				val y2 = leftEyeStart.y
				val x3 = leftEyeEnd.x
				val y3 = leftEyeEnd.y
				val x4 = rightEyeStart.x
				val y4 = rightEyeStart.y
				val x5 = rightEyeEnd.x
				val y5 = rightEyeEnd.y

				drawExtendedLine(canvas, x0, y2, x0, y2 + 1, 400f, 400f, greenPaint)
				drawExtendedLine(canvas, x1, y2, x1, y2 + 1, 400f, 400f, greenPaint)
				drawExtendedLine(canvas, x2, y2, x2, y2 + 1, 400f, 400f, greenPaint)
				drawExtendedLine(canvas, x3, y2, x3, y2 + 1, 400f, 400f, greenPaint)
				drawExtendedLine(canvas, x4, y2, x4, y2 + 1, 400f, 400f, greenPaint)
				drawExtendedLine(canvas, x5, y2, x5, y2 + 1, 400f, 400f, greenPaint)
			}


			if (dim.setedLine.contains(DrawLine.A_RATIO_HORIZON)) {

				val glabella = dimFLS.landmarks[8]
				val subNazale = dimFLS.landmarks[2]
//					val centerTopLips = dimFLS.landmarks[13]
				val menton = dimFLS.landmarks[152]

				val x0 = glabella.x
				val y0 = glabella.y

				val x1 = subNazale.x
				val y1 = subNazale.y

//					val x2 = centerTopLips.x
//					val y2 = centerTopLips.y

				val x3 = menton.x
				val y4 = menton.y

				val hairLine = dim.lms.hairLineLandmarks
				Log.v("hairLine", "notExtract: ${hairLine}")
				if (hairLine != null && hairLine.size == 7) {
					drawExtendedLine(canvas, x1, hairLine[3].second, x1 + 1, hairLine[3].second, 300f, 300f, redPaint)
					hairLine.forEach { point ->
						canvas.drawCircle(point.first, point.second, 1f, yellowPaint)
					}
				}
				drawExtendedLine(canvas, x1, y0, x1 + 1, y0, 300f, 300f, redPaint)
				drawExtendedLine(canvas, x1, y1, x1 + 1, y1, 300f, 300f, redPaint)
				drawExtendedLine(canvas, x1, y4, x1 + 1, y4, 300f, 300f, redPaint)
			}
		}

		if (dim.flags.isMoireOn && dim.faceResult.moireUri.isNotEmpty()) {
			val originalMoireBitmap = BitmapFactory.decodeStream(
				dim.fragment.requireContext().contentResolver.openInputStream(dim.faceResult.moireUri.first()!!)
			)

			if (originalMoireBitmap != null) {
				Log.d("BitmapSize", "Original moire size: ${originalMoireBitmap.width}x${originalMoireBitmap.height}")

				val targetWidth = 1920
				val targetHeight = 1080
				val matrix = Matrix().apply {
					// x축을 -1로 설정하여 좌우를 뒤집고, 1920x1080 비율에 맞게 스케일을 계산합니다.
					postScale(1f * (targetWidth.toFloat() / originalMoireBitmap.width), // -1f
						targetHeight.toFloat() / originalMoireBitmap.height)

					// 뒤집힌 이미지가 화면 밖으로 나가지 않도록 가로 크기만큼 보정 이동해 줍니다.
					postTranslate(targetWidth.toFloat(), 0f)
				}
				val scaledMoire = Bitmap.createBitmap(
					originalMoireBitmap,
					0, 0,
					originalMoireBitmap.width,
					originalMoireBitmap.height,
					matrix,
					true
				)
				canvas.drawBitmap(scaledMoire, 0f, 0f, null)
				if (scaledMoire != originalMoireBitmap) {
					originalMoireBitmap.recycle()
				}
				scaledMoire.recycle()
			} else {
				Log.e("BitmapSize", "모아레 이미지를 로드하지 못했습니다.")
			}
		}
// ------------------------------# 선택에 따라 바로 그리기 #----------------------------------
		val (textBoxInfos, faceRect) = createRawDataBox(dim)
		return Triple(drawDirectionUIOnBitmap(resultBitmap), textBoxInfos, faceRect)
	}

	fun drawExtendedLine(
		canvas: Canvas,
		x0: Float,
		y0: Float,
		x1: Float,
		y1: Float,
		startExtension: Float,
		endExtension: Float,
		paint: Paint,
		lineType: LineType = LineType.LENGTH
	) {
		val dx = x1 - x0
		val dy = y1 - y0
		val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()

		if (length == 0f) return

		val nx = dx / length
		val ny = dy / length

		val extendedStartX = x0 - nx * startExtension
		val extendedStartY = y0 - ny * startExtension
		val extendedEndX = x1 + nx * endExtension
		val extendedEndY = y1 + ny * endExtension

		// 점선 효과
		val dashedPaint = Paint(paint).apply {
			pathEffect = DashPathEffect(floatArrayOf(2f, 4f), 0f)
		}
		canvas.drawLine(extendedStartX, extendedStartY, extendedEndX, extendedEndY, dashedPaint)

		// 끝점 마커
		when (lineType) {
			LineType.ANGLE -> {
				// ◆ 다이아몬드
				drawDiamond(canvas, extendedStartX, extendedStartY, paint)
				drawDiamond(canvas, extendedEndX, extendedEndY, paint)

			}
			LineType.LENGTH -> {
				// ▼ ▲ 삼각형 화살표
				drawTriangleArrow(canvas, extendedStartX, extendedStartY, nx, ny, paint, isStart = true)
				drawTriangleArrow(canvas, extendedEndX, extendedEndY, nx, ny, paint, isStart = false)
			}
		}
	}
	private fun drawTriangleArrow(
		canvas: Canvas,
		x: Float,
		y: Float,
		nx: Float,
		ny: Float,
		paint: Paint,
		isStart: Boolean
	) {
		val arrowSize = 4f
		// isStart면 ▲ (선 방향 반대), isStart 아니면 ▼ (선 방향)
		val dirX = if (isStart) nx else -nx
		val dirY = if (isStart) ny else -ny

		// 수직 방향
		val perpX = -dirY
		val perpY = dirX

		val path = Path().apply {
			moveTo(x + dirX * arrowSize, y + dirY * arrowSize)           // 꼭짓점
			lineTo(x - perpX * arrowSize / 2f, y - perpY * arrowSize / 2f) // 왼쪽
			lineTo(x + perpX * arrowSize / 2f, y + perpY * arrowSize / 2f) // 오른쪽
			close()
		}
		canvas.drawPath(path, Paint(paint).apply { style = Paint.Style.FILL })
	}

	// ◆ 다이아몬드
	private fun drawDiamond(
		canvas: Canvas,
		x: Float,
		y: Float,
		paint: Paint
	) {
		val size = 2.5f
		val path = Path().apply {
			moveTo(x, y - size)  // 위
			lineTo(x + size, y)  // 오른쪽
			lineTo(x, y + size)  // 아래
			lineTo(x - size, y)  // 왼쪽
			close()
		}
		canvas.drawPath(path, Paint(paint).apply { style = Paint.Style.FILL })
	}

	// L, R 써클 그리기
	fun drawDirectionUIOnBitmap(
		bitmap: Bitmap,

		): Bitmap {
		val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
		val canvas = Canvas(resultBitmap)
		val bWidth = bitmap.width
//        val bHeight = bitmap.height
		val leftCircleX = bWidth * 0.125f
		val leftCircleY = bWidth * 0.07f
		val circleRadius = bitmap.width * 0.0225f
		val innerRadius = bWidth * 0.007f

		val textPaint = Paint().apply {
			color = "#FFFFFF".toColorInt()
			textSize = circleRadius * 1.1f
			isAntiAlias = true
			textAlign = Paint.Align.CENTER
		}

		val outerCirclePaint = Paint().apply {
			color = "#41000000".toColorInt()
			style = Paint.Style.FILL
		}
		val innerCirclePaint = Paint().apply {
			color = "#FFFFFF".toColorInt()
			style = Paint.Style.FILL
		}

		canvas.drawCircle(leftCircleX, leftCircleY, circleRadius, outerCirclePaint)
//        canvas.drawCircle(leftCircleX, leftCircleY, circleRadius - innerRadius, innerCirclePaint)
		canvas.drawText("L", leftCircleX, leftCircleY + textPaint.textSize / 3, textPaint)

		canvas.drawCircle(resultBitmap.width - leftCircleX, leftCircleY, circleRadius, outerCirclePaint)
//        canvas.drawCircle(resultBitmap.width - leftCircleX, leftCircleY, circleRadius - innerRadius, innerCirclePaint)
		canvas.drawText(
			"R",
			resultBitmap.width - leftCircleX,
			leftCircleY + textPaint.textSize / 3,
			textPaint
		)

		return resultBitmap
	}

	private fun upscaleImage(originalBitmap: Bitmap, scaleFactor: Float): Bitmap {
		val paint = Paint().apply {
			isAntiAlias = true
			isFilterBitmap = true
			isDither = true
		}

		val width = (originalBitmap.width * scaleFactor).toInt()
		val height = (originalBitmap.height * scaleFactor).toInt()

		val scaledBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(scaledBitmap)

		val srcRect = Rect(0, 0, originalBitmap.width, originalBitmap.height)
		val dstRect = Rect(0, 0, width, height)

		canvas.drawBitmap(originalBitmap, srcRect, dstRect, paint)

		return scaledBitmap
	}

	fun calculateFaceScale(
		leftFaceCoords: List<Pair<Float, Float>>,
		rightFaceCoords: List<Pair<Float, Float>>,
	): Pair<Boolean, Float> { // isLeftBigger

		// MediaPipe 귀 근처 랜드마크 인덱스
		val LEFT_EAR_INDEX = 234
		val RIGHT_EAR_INDEX = 454

		try {
			// 왼쪽 얼굴 너비 계산
			val leftEarLeft = leftFaceCoords[LEFT_EAR_INDEX]
			val leftEarRight = leftFaceCoords[RIGHT_EAR_INDEX]
			val leftFaceWidth = sqrt(
				(leftEarRight.first - leftEarLeft.first).pow(2) +
					(leftEarRight.second - leftEarLeft.second).pow(2)
			)

			// 오른쪽 얼굴 너비 계산
			val rightEarLeft = rightFaceCoords[LEFT_EAR_INDEX]
			val rightEarRight = rightFaceCoords[RIGHT_EAR_INDEX]
			val rightFaceWidth = sqrt(
				(rightEarRight.first - rightEarLeft.first).pow(2) +
					(rightEarRight.second - rightEarLeft.second).pow(2)
			)

			// 큰 얼굴 기준으로 작은 얼굴 확대
			return if (leftFaceWidth > rightFaceWidth) {
				Pair(true, leftFaceWidth / rightFaceWidth)
			} else {
				Pair(false, rightFaceWidth / leftFaceWidth)
			}

		} catch (e: Exception) {
			Log.e("FaceScale", "Error calculating face scale: ${e.message}")
			return Pair(true, 1f)
		}
	}

	fun scaleAndCropBitmap(
		bitmap: Bitmap,
		scale: Float,
		targetWidth: Int,
		targetHeight: Int
	): Bitmap {
		// 1. 스케일링
		val scaledBitmap = bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())

		// 2. 크롭 시작 좌표 (중앙 기준으로 잘라내기 예시)
		val startX = max(0, (scaledBitmap.width - targetWidth) / 2)
		val startY = max(0, (scaledBitmap.height - targetHeight) / 2)

		// 3. 크롭
		val croppedBitmap = Bitmap.createBitmap(
			scaledBitmap,
			startX,
			startY,
			min(targetWidth, scaledBitmap.width - startX),
			min(targetHeight, scaledBitmap.height - startY)
		)
		return croppedBitmap
	}

	fun Activity.getScreenOrientation(): String {
		val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
		val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
		val rotation = display?.rotation
		val orientation = resources.configuration.orientation

		return when (orientation) {
			Configuration.ORIENTATION_PORTRAIT -> {
				when (rotation) {
					Surface.ROTATION_0 -> "portrait"
					Surface.ROTATION_180 -> "reverse portrait"
					else -> "portrait (unknown rotation)"
				}
			}

			Configuration.ORIENTATION_LANDSCAPE -> {
				when (rotation) {
					Surface.ROTATION_90 -> "landscape"
					Surface.ROTATION_270 -> "Reverse landscape"
					else -> "landscape (unknown rotation)"
				}
			}

			else -> "undefined"
		}
	}

	val df = DecimalFormat("#.##")
	// TODO 전부 COMFORT 분기 처리
	private fun extractRawData(dim: DrawImageUiModel, dl: DrawLine): List<String> {
		val fr = dim.faceResult

		val seq = dim.seq
		val seqss = fr.seqs // 0 1 2 3 4 5 6 7 8 9
		val seqIndex = seqss.indexOf(dim.seq)

		val keys = when (dl) {
			DrawLine.A_SAGGING_FACE -> when (seq) {
				Sequence.COMFORT -> listOf("comfort_left_sagging_scale", "comfort_right_sagging_scale")
				else -> listOf()
			}
			DrawLine.A_SHOULDER -> when (seq) {
				Sequence.COMFORT -> listOf("comfort_shoulder_horizontal_angle")
				else -> listOf("neck_extention_shoulder_horizontal_angle")
			}
			DrawLine.NECK -> when (seq) {
				Sequence.COMFORT -> listOf("comfort_neck_vertical_angle", "comfort_neck_length")
				else -> listOf("neck_extention_neck_vertical_angle")
			}
			DrawLine.FACE -> when (seq) {
				Sequence.COMFORT -> listOf("comfort_face_vertical_angle", "comfort_face_width", "comfort_face_height")
				else -> listOf("neck_extention_face_vertical_angle")
			}
			DrawLine.A_HORIZON -> when (seq) {
				Sequence.COMFORT -> listOf(
					"comfort_eye_horizontal_angle",
					"comfort_earflaps_horizontal_angle",
					"comfort_tip_of_lips_horizontal_angle",
					"comfort_tip_of_chin_horizontal_angle"
				)
				else -> listOf("neck_extention_ear_horizontal_angle", "neck_extention_shoulder_horizontal_angle")
			}
			DrawLine.EYE ->
				when (seq) {
					Sequence.COMFORT -> listOf(
						"comfort_left_eye_horizontal_angle",
						"comfort_right_eye_horizontal_angle",
						"comfort_left_medial_canthus_angle",
						"comfort_right_medial_canthus_angle",
						"comfort_left_lateral_canthus_angle",
						"comfort_right_lateral_canthus_angle",
						"comfort_left_eye_width",
						"comfort_right_eye_width",
						"comfort_left_eye_height",
						"comfort_right_eye_height"
					)
					else -> listOf()
				}
			DrawLine.L_CORNEA -> when (seq) {
				Sequence.COMFORT -> listOf(
					"comfort_left_cornea_length",
					"comfort_right_cornea_length",
				)
				else -> listOf()
			}
			DrawLine.EYEBROW -> when (seq) {
				Sequence.COMFORT -> listOf(
					"comfort_left_eyebrow_horizontal_angle",
					"comfort_right_eyebrow_horizontal_angle",
					"comfort_left_eyebrow_start_length",
					"comfort_left_eyebrow_arch_length",
					"comfort_left_eyebrow_end_length",
					"comfort_right_eyebrow_start_length",
					"comfort_right_eyebrow_arch_length",
					"comfort_right_eyebrow_end_length",
				)
				else -> listOf()
			}
			DrawLine.NOSE -> when (seq) {
				Sequence.COMFORT -> listOf(
					"comfort_nose_height",
					"comfort_nose_width",
					"comfort_alar_horizontal_angle",
					"comfort_left_alar_length",
					"comfort_right_alar_length"
				)
				else -> listOf()
			}
			DrawLine.A_CANTHUS_ORAL -> when (seq) {
				Sequence.COMFORT -> listOf(
					"comfort_canthus_oral_left_vertical_angle",
					"comfort_canthus_oral_right_vertical_angle"
				)
				else -> listOf()
			}
			DrawLine.A_ALAR_ORAL -> when (seq) {
				Sequence.COMFORT -> listOf("comfort_alar_tip_of_lips_left_vertical_angle", "comfort_alar_tip_of_lips_right_vertical_angle")
				else -> listOf()
			}
			DrawLine.A_SIDE_HEAD -> when (seq) {
				Sequence.COMFORT -> listOf("comfort_side_head_left_angle", "comfort_side_head_right_angle")
				else -> listOf()
			}
			DrawLine.E_CHEEKS -> when (seq) {
				Sequence.COMFORT -> listOf("comfort_left_cheeks_extent", "comfort_right_cheeks_extent")
				else -> listOf()
			}
			DrawLine.E_CHEEKBONE -> when (seq) {
				Sequence.COMFORT -> listOf("comfort_left_cheekbone_extent","comfort_right_cheekbone_extent")
				else -> listOf()
			}
			DrawLine.MOUSE -> when (seq) {
				Sequence.COMFORT -> listOf(
					"comfort_left_mouse_corner_angle",
					"comfort_right_mouse_corner_angle",
					"comfort_philtrum_length",
					"comfort_mouse_width",
					"comfort_upper_lips_height",
					"comfort_lower_lips_height",
				)
				else -> listOf()
			}


			DrawLine.A_FACIAL_AXIS -> when (seq) {
				Sequence.COMFORT -> listOf("comfort_length_symmetry_factor", "comfort_angle_symmetry_factor")
				else -> listOf()
			}

			DrawLine.A_RATIO_VERTI -> when (seq) {
				Sequence.COMFORT -> listOf("vertical_ratio")
				else -> listOf()
			}
			DrawLine.A_RATIO_HORIZON -> when (seq) {
				Sequence.COMFORT -> listOf("horizontal_ratio")
				else -> listOf()
			}
		}

		val jsonObject = fr.results.getJSONObject(seqIndex)
		val dataObject = jsonObject.getJSONObject("data")
		return keys.map { key ->
			when (key) {
				// String 타입으로 저장된 비율 데이터인 경우
				"vertical_ratio", "horizontal_ratio" -> {
					dataObject.optString(key, "") // 포맷팅 없이 문자열 그대로 반환 (기본값 "")
				}
				// 그 외의 숫자 데이터인 경우 (기존 로직 유지)
				else -> {
					val numValue = dataObject.optDouble(key, 0.0) // 기본값 0.0 지정
					df.format(numValue)
				}
			}
		}
	}
	private fun createInfoBitmap(context: Context?, case: Int): Bitmap {
		return createBitmap(1280, 720).apply {
			val canvas = Canvas(this)

			// 1. 배경을 흰색으로 채우기
			canvas.drawColor(Color.WHITE)

			// 2. Paint 설정 (텍스트 스타일 지정)
			val paint = Paint().apply {
				// color.xml의 색상 불러오기 (context가 필요함)
				color = context?.let { ContextCompat.getColor(it, R.color.subColor700) } ?: Color.BLACK
				isAntiAlias = true          // 테두리 부드럽게
				textSize = 40f              // 텍스트 크기
				textAlign = Paint.Align.CENTER // 중앙 정렬 설정
			}

			/* case
			* 0: 텍스트 없음
			* 1: 선택해주세요
			* */
			when (case) {
				0 -> {}
				1 -> {
					val text = "단계를 선택해주세요"
					val xPos = (canvas.width / 2).toFloat()        // 가로 중앙
					val yPos = (canvas.height - 60).toFloat()     // 하단에서 60px 위로
					canvas.drawText(text, xPos, yPos, paint)
				}
			}
		}
	}

	private fun drawSmoothCurve(canvas: Canvas, points:  List<FaceLandmarkResult.FaceLandmark>, paint: Paint) {
		if (points.size < 2) return

		val path = Path()
		path.moveTo(points[0].x, points[0].y)

		for (i in 0 until points.size - 1) {
			val p0 = if (i > 0) points[i - 1] else points[i]
			val p1 = points[i]
			val p2 = points[i + 1]
			val p3 = if (i + 2 < points.size) points[i + 2] else p2

			val cp1x = p1.x + (p2.x - p0.x) / 6f
			val cp1y = p1.y + (p2.y - p0.y) / 6f
			val cp2x = p2.x - (p3.x - p1.x) / 6f
			val cp2y = p2.y - (p3.y - p1.y) / 6f

			path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
		}
		val dashedPaint = Paint(paint).apply {
			pathEffect = DashPathEffect(floatArrayOf(2f, 4f), 0f)
		}
		canvas.drawPath(path, dashedPaint)
	}
/* 0 1 2 3 4
*  5 6 7 8 9
*  10 11 12 13 14
*  15 16 17 18 19
*  20 21 22 23 24
*  25 26 27 28 29
*  30 31 32 33 34 35
* */

 // 🔥🔥🔥🔥🔥🔥🔥 rawData 박스 만들기 🔥🔥🔥🔥🔥🔥🔥
 data class TextBoxData(
	 val anchorX: Float,        // 최종 비트맵의 픽셀 좌표 (x)
	 val anchorY: Float,        // 최종 비트맵의 픽셀 좌표 (y)
	 val targetGridIndex: Int,  // 0 ~ 11 사이의 그리드 번호
	 val info: TextDrawInfo
 )
 data class TextDrawInfo(
	 val title: String,
	 val items: List<TextItem>,
 )
	data class TextItem(
		val label: String,    // "눈:", "귀:", "입술:" 등
		val value: String     // "178.1°", "178.2°" 등
	)
	/**
	 * FHD(1920x1080) 절대 좌표를 최종 가공된 비트맵(2.25배 확대 + 3:4 크롭)의 픽셀 좌표로 변환
	 */
	private fun calculateFinalPixel(
		rawX: Float,
		rawY: Float,
		isPortrait: Boolean = true
	): PointF {
		val upscaleFactor = 2.25f

		// 1. 2.25배 업스케일 적용
		val upscaledX = rawX * upscaleFactor
		val upscaledY = rawY * upscaleFactor

		// 2. Portrait 모드에서의 크롭 보정
		// Logic: 가로(4320)가 세로(2430)보다 크면 3:4 비율로 가운데를 남기고 좌우를 자름
		val upscaledW = 1080f * upscaleFactor // 4320
		val upscaledH = 1920f * upscaleFactor // 2430

		var finalX = upscaledX
		var finalY = upscaledY

		if (isPortrait && upscaledW > upscaledH) {
			val targetWidth = (upscaledH * 0.75f).toInt() // 3:4 비율 너비
			val cropXOffset = (upscaledW - targetWidth) / 2f

			finalX -= cropXOffset // 잘려나간 왼쪽만큼 좌표를 왼쪽으로 이동
		}

		return PointF(finalX, finalY)
	}

	private fun createRawDataBox(dim: DrawImageUiModel) : Pair<List<TextBoxData>, TextBoxOverlayView.FaceRect> {
		val dl = dim.setedLine
		val flm = dim.lms.faceLandmarks.landmarks
		val plm = dim.lms.poseLandmarks.landmarks
		val textBoxes = mutableListOf<TextBoxData>()

		if (flm.isNotEmpty()) {
			if (dl.contains(DrawLine.A_SAGGING_FACE)) {
				val boxLocation = flm[152]
				val finalPos = calculateFinalPixel(boxLocation.x, boxLocation.y)
				val rawData = extractRawData(dim, DrawLine.A_SAGGING_FACE)
				if (rawData.isNotEmpty()) {
					textBoxes.add(
						TextBoxData(
							anchorX = finalPos.x,
							anchorY = finalPos.y,
							targetGridIndex = 26,
							info = TextDrawInfo(
								title = "처짐 지수",
								items = listOf(
									TextItem("왼쪽:", rawData.getOrNull(0) ?: ""),
									TextItem("오른쪽:", rawData.getOrNull(1) ?: ""),
								)
							)
						)
					)
				}
			}

			if (dl.contains(DrawLine.A_SHOULDER)) {
				val rawX = (plm[11].x + plm[12].x) / 2
				val rawY = (plm[11].y + plm[12].y) / 2
				val finalPos = calculateFinalPixel(rawX, rawY)
				val rawData = extractRawData(dim, DrawLine.A_SHOULDER)
				if (rawData.isNotEmpty()) {
					textBoxes.add(TextBoxData(
						anchorX = finalPos.x, anchorY = finalPos.y,
						targetGridIndex = 31, // 중앙 하단부
						info = TextDrawInfo("어깨 각도", listOf(TextItem("너비:", "${rawData.getOrNull(0) ?: ""}º")))
					))
				}
			}

			if (dl.contains(DrawLine.NECK)) {
				val rawX = (plm[11].x + plm[12].x) / 2
				val rawY = (plm[11].y + plm[12].y) / 2
				val finalPos = calculateFinalPixel(rawX, rawY)
				val rawData = extractRawData(dim, DrawLine.NECK)
				if (rawData.isNotEmpty()) {
					textBoxes.add(TextBoxData(
						anchorX = finalPos.x, anchorY = finalPos.y,
						targetGridIndex = 32, // 목 근처 왼쪽
						info = TextDrawInfo("목 각도", listOf(TextItem("", "${rawData.getOrNull(0) ?: ""}º")))
					))
				}
			}
			if (dl.contains(DrawLine.FACE)) {
				val boxLocation = flm[234]
				val finalPos = calculateFinalPixel(boxLocation.x, boxLocation.y)
				val rawData = extractRawData(dim, DrawLine.FACE)
				if (rawData.isNotEmpty()) {
					val v10 = rawData.getOrNull(1)
					val v20 = rawData.getOrNull(2)
					val v1 = if (v10 == null || v10 == "0") "🚫" else v10 + "cm"
					val v2 = if (v20 == null || v20 == "0") "🚫" else v20 + "cm"
					textBoxes.add(TextBoxData(
						anchorX = finalPos.x, anchorY = finalPos.y,
						targetGridIndex = 15, // 얼굴 왼쪽 구역
						info = TextDrawInfo("얼굴 정보", if (dim.seq == Sequence.COMFORT) {
							listOf(
								TextItem("너비:", v1),
								TextItem("높이:", v2),
								TextItem("각도:", "${rawData.getOrNull(0) ?: ""}º")
							)
						} else  listOf(
							TextItem("각도:", "${rawData.getOrNull(0) ?: ""}º")
						))
					))
				}
			}

			if (dl.contains(DrawLine.A_HORIZON)) {
				val boxLocation = flm[454]
				val finalPos = calculateFinalPixel(boxLocation.x, boxLocation.y)
				val rawData = extractRawData(dim, DrawLine.A_HORIZON)
				if (rawData.isNotEmpty()) {
					textBoxes.add(TextBoxData(
						anchorX = finalPos.x, anchorY = finalPos.y,
						targetGridIndex = 19, // 얼굴 오른쪽 구역
						info = TextDrawInfo("수평 각도",  if (dim.seq == Sequence.COMFORT) {
							listOf(
								TextItem("눈:", "${rawData.getOrNull(0) ?: ""}º"),
								TextItem("귀:", "${rawData.getOrNull(1) ?: ""}º"),
								TextItem("입술:", "${rawData.getOrNull(2) ?: ""}º"),
								TextItem("턱:", "${rawData.getOrNull(3) ?: ""}º")
							)
						} else  listOf(
							TextItem("눈:", "${rawData.getOrNull(0) ?: ""}º"),
							TextItem("귀:", "${rawData.getOrNull(1) ?: ""}º"),
						))

					))
				}
			}
			if (dl.contains(DrawLine.EYE)) {
				val rawData = extractRawData(dim, DrawLine.EYE)
				if (rawData.isNotEmpty()) {
					// 왼쪽 눈 (233번)
					val leftEyePos = calculateFinalPixel(flm[233].x, flm[233].y,)

					val v1 = listOf(
						rawData.getOrNull(6),
						rawData.getOrNull(8),
					).joinToString(" | ") { if (it == null || it == "0") "🚫" else "${it}cm" }

					textBoxes.add(TextBoxData(
						anchorX = leftEyePos.x, anchorY = leftEyePos.y,
						targetGridIndex = 16, // 좌측 상단
						info = TextDrawInfo("왼쪽 눈", listOf(
							TextItem("눈매:", "${rawData.getOrNull(0)}º"),
							TextItem("너비 | 높이:", v1),
							TextItem("눈꼬리 | 눈앞머리", "${rawData.getOrNull(4)}º | ${rawData.getOrNull(2)}º")
						))
					))

					val v2 = listOf(
						rawData.getOrNull(7),
						rawData.getOrNull(9),
					).joinToString(" | ") { if (it == null || it == "0") "🚫" else "${it}cm" }

					// 오른쪽 눈 (453번)
					val rightEyePos = calculateFinalPixel(flm[453].x, flm[453].y,)
					textBoxes.add(TextBoxData(
						anchorX = rightEyePos.x, anchorY = rightEyePos.y,
						targetGridIndex = 18, // 우측 상단
						info = TextDrawInfo("오른쪽 눈", listOf(
							TextItem("눈매:", "${rawData.getOrNull(1)}º"),
							TextItem("너비 | 높이:", v2),
							TextItem("눈앞머리 | 눈꼬리", "${rawData.getOrNull(3)} | ${rawData.getOrNull(5)}º")
						))
					))
				}
			}

			if (dl.contains(DrawLine.EYEBROW)) {
				val rawData = extractRawData(dim, DrawLine.EYEBROW)
				if (rawData.isNotEmpty()) {
					// 왼쪽 눈 (233번)
					val leftEyePos = calculateFinalPixel(flm[66].x, flm[66].y,)

					val v1 = listOf(
						rawData.getOrNull(4),
						rawData.getOrNull(3),
						rawData.getOrNull(2)
					).joinToString(" | ") { if (it == null || it == "0") "🚫" else "${it}cm" }

					textBoxes.add(TextBoxData(
						anchorX = leftEyePos.x, anchorY = leftEyePos.y,
						targetGridIndex = 6, // 좌측 상단
						info = TextDrawInfo("왼쪽 눈썹", listOf(
							TextItem("각도: ", "${rawData.getOrNull(0)}º"),
							TextItem("두께: ", v1)
						))
					))

					val v2 = listOf(
						rawData.getOrNull(5),
						rawData.getOrNull(6),
						rawData.getOrNull(7)
					).joinToString(" | ") { if (it == null || it == "0") "🚫" else "${it}cm" }

					// 오른쪽 눈 (453번)
					val rightEyePos = calculateFinalPixel(flm[296].x, flm[296].y,)
					textBoxes.add(TextBoxData(
						anchorX = rightEyePos.x, anchorY = rightEyePos.y,
						targetGridIndex = 8, // 우측 상단
						info = TextDrawInfo("오른쪽 눈썹", listOf(
							TextItem("각도: ", "${rawData.getOrNull(1)}º"),
							TextItem("두께: ", v2)
						))
					))
				}
			}

			if (dl.contains(DrawLine.NOSE)) {
				val noseCenter = calculateFinalPixel(flm[164].x, flm[164].y)
				val rawData = extractRawData(dim, DrawLine.NOSE)
				if (rawData.isNotEmpty()) {

					val v1 = listOf(
						rawData.getOrNull(0),
						rawData.getOrNull(1),
					).joinToString(" | ") { if (it == null || it == "0") "🚫" else "${it}cm" }

					textBoxes.add(TextBoxData(
						anchorX = noseCenter.x, anchorY = noseCenter.y,
						targetGridIndex = 18, // 중앙 상단 (얼굴 중앙)
						info = TextDrawInfo("코 정보", listOf(
							TextItem("길이 | 너비:", v1),
							TextItem("콧날개 각:", "${rawData.getOrNull(2)}º")
						))
					))
				}

			}
//
//		if (dl.contains(DrawLine.E_CHEEKBONE)) {
//
//		}
			if (dl.contains(DrawLine.A_ALAR_ORAL)) {
				val boxLocation = flm[357] // 코 하단 중앙
				val finalPos = calculateFinalPixel(boxLocation.x, boxLocation.y)
				val rawData = extractRawData(dim, DrawLine.A_ALAR_ORAL)
				if (rawData.isNotEmpty()) {
					textBoxes.add(TextBoxData(
						anchorX = finalPos.x, anchorY = finalPos.y,
						targetGridIndex = 27, // 중앙 하단부
						info = TextDrawInfo("콧날개-입술끝 각도", listOf(
							TextItem("좌측:", "${rawData.getOrNull(0) ?: ""}º"),
							TextItem("우측:", "${rawData.getOrNull(1) ?: ""}º")
						))
					))
				}
			}
			if (dl.contains(DrawLine.A_SIDE_HEAD)) {
				val rawData = extractRawData(dim, DrawLine.A_SIDE_HEAD)
				if (rawData.isNotEmpty()) {
					// 좌측 측두 (251번)
					val leftPos = calculateFinalPixel(flm[251].x, flm[251].y)
					textBoxes.add(TextBoxData(
						anchorX = leftPos.x, anchorY = leftPos.y,
						targetGridIndex = 10, // 왼쪽 중간
						info = TextDrawInfo("측두 각도(좌)", listOf(TextItem("", "${rawData.getOrNull(0) ?: ""}º")))
					))

					// 우측 측두 (21번)
					val rightPos = calculateFinalPixel(flm[21].x, flm[21].y)
					textBoxes.add(TextBoxData(
						anchorX = rightPos.x, anchorY = rightPos.y,
						targetGridIndex = 14, // 오른쪽 중간
						info = TextDrawInfo("측두 각도(우)", listOf(TextItem("", "${rawData.getOrNull(1) ?: ""}º")))
					))
				}
			}
			if (dl.contains(DrawLine.A_CANTHUS_ORAL)) {
				val boxLocation = flm[18] // 하안면부 중앙
				val finalPos = calculateFinalPixel(boxLocation.x, boxLocation.y)
				val rawData = extractRawData(dim, DrawLine.A_CANTHUS_ORAL)
				if (rawData.isNotEmpty()) {
					textBoxes.add(TextBoxData(
						anchorX = finalPos.x, anchorY = finalPos.y,
						targetGridIndex = 32, // 하단 중앙
						info = TextDrawInfo("눈매끝-입꼬리 각도", listOf(
							TextItem("좌측:", "${rawData.getOrNull(0) ?: ""}º"),
							TextItem("우측:", "${rawData.getOrNull(1) ?: ""}º")
						))
					))
				}
			}
			if (dl.contains(DrawLine.E_CHEEKS)) {
				val rawData = extractRawData(dim, DrawLine.E_CHEEKS)

				// 좌측 볼 (132번)
				if (rawData.isNotEmpty()) {

					val v10 = rawData.getOrNull(0)
					val v1 = if (v10 == null || v10 == "0") "🚫" else v10 + "㎠"

					val leftPos = calculateFinalPixel(flm[132].x, flm[132].y,)
					textBoxes.add(TextBoxData(
						anchorX = leftPos.x, anchorY = leftPos.y,
						targetGridIndex = 20, // 왼쪽 하단부
						info = TextDrawInfo("볼 너비(좌)", listOf(TextItem("", v1)))
					))

					val v20 = rawData.getOrNull(1)
					val v2 = if (v20 == null || v20 == "0") "🚫" else v20 + "㎠"
					// 우측 볼 (361번)
					val rightPos = calculateFinalPixel(flm[361].x, flm[361].y,)
					textBoxes.add(TextBoxData(
						anchorX = rightPos.x, anchorY = rightPos.y,
						targetGridIndex = 24, // 오른쪽 하단부
						info = TextDrawInfo("볼 너비(우)", listOf(TextItem("", v2)))
					))
				}
			}
			if (dl.contains(DrawLine.MOUSE)) {
				val mousePos = calculateFinalPixel(flm[17].x, flm[17].y,)
				val rawData = extractRawData(dim, DrawLine.MOUSE)
				if (rawData.isNotEmpty()) {

					val v10 = rawData.getOrNull(2)
					val v20 = rawData.getOrNull(3)
					val v30 = rawData.getOrNull(4)
					val v40 = rawData.getOrNull(5)

					val v1 = if (v10 == null || v10 == "0") "🚫" else v10 + "cm"
					val v2 = if (v20 == null || v20 == "0") "🚫" else v20 + "cm"
					val v3 = if (v30 == null || v10 == "0") "🚫" else v30 + "cm"
					val v4 = if (v40 == null || v20 == "0") "🚫" else v40 + "cm"




					textBoxes.add(TextBoxData(
						anchorX = mousePos.x, anchorY = mousePos.y,
						targetGridIndex = 27, // 하단 중앙
						info = TextDrawInfo("입 정보", listOf(
							TextItem("인중 길이:", v1),
							TextItem("입 길이", v2),
							TextItem("입꼬리 각도:", "${rawData.getOrNull(0)}º / ${rawData.getOrNull(1)}º"),
							TextItem("윗입술 두께", v3),
							TextItem("아래입술 두께", v4)
						))
					))
				}
			}
			if (dl.contains(DrawLine.A_FACIAL_AXIS)) {
				val mousePos = calculateFinalPixel(flm[17].x, flm[17].y,)
				val rawData = extractRawData(dim, DrawLine.A_FACIAL_AXIS)
				if (rawData.isNotEmpty()) {
					textBoxes.add(TextBoxData(
						anchorX = mousePos.x, anchorY = mousePos.y,
						targetGridIndex = 33, // 하단 중앙
						info = TextDrawInfo("하관 비대칭", listOf(
							TextItem("길이 비대칭 지수", "${rawData.getOrNull(0)}"),
							TextItem("각도 비대칭 지수", "${rawData.getOrNull(1)}"),
						))
					))
				}

			}
			if (dl.contains(DrawLine.A_RATIO_VERTI)) {
				val rawData = extractRawData(dim, DrawLine.A_RATIO_VERTI)

				val verticalRatio = rawData[0].split(":")
				if (rawData.isNotEmpty()) {
					textBoxes.add(TextBoxData(
						anchorX = 0f, anchorY = 0f,
						targetGridIndex = 30, // 하단 중앙
						info = TextDrawInfo("", listOf(
							TextItem("", "${verticalRatio[0]}"),
						))
					))
					textBoxes.add(TextBoxData(
						anchorX = 0f, anchorY = 0f,
						targetGridIndex = 31, // 하단 중앙
						info = TextDrawInfo("", listOf(
							TextItem("", "${verticalRatio[1]}%"),
						))
					))
					textBoxes.add(TextBoxData(
						anchorX = 0f, anchorY = 0f,
						targetGridIndex = 32, // 하단 중앙
						info = TextDrawInfo("", listOf(
							TextItem("", "${verticalRatio[2]}%"),
						))
					))
					textBoxes.add(TextBoxData(
						anchorX = 0f, anchorY = 0f,
						targetGridIndex = 33, // 하단 중앙
						info = TextDrawInfo("", listOf(
							TextItem("", "${verticalRatio[3]}%"),
						))
					))
					textBoxes.add(TextBoxData(
						anchorX = 0f, anchorY = 0f,
						targetGridIndex = 34, // 하단 중앙
						info = TextDrawInfo("", listOf(
							TextItem("", "${verticalRatio[4]}%"),
						))
					))
				}
			}
			if (dl.contains(DrawLine.A_RATIO_HORIZON)) {
				val rawData = extractRawData(dim, DrawLine.A_RATIO_HORIZON)
				val verticalRatio = rawData[0].split(":")
				if (rawData.isNotEmpty()) {
					textBoxes.add(TextBoxData(
						anchorX = 0f, anchorY = 0f,
						targetGridIndex = 9, // 하단 중앙
						info = TextDrawInfo("", listOf(
							TextItem("", "${verticalRatio[0]}%"),
						))
					))
					textBoxes.add(TextBoxData(
						anchorX = 0f, anchorY = 0f,
						targetGridIndex = 19, // 하단 중앙
						info = TextDrawInfo("", listOf(
							TextItem("", "${verticalRatio[1]}%"),
						))
					))
					textBoxes.add(TextBoxData(
						anchorX = 0f, anchorY = 0f,
						targetGridIndex = 29, // 하단 중앙
						info = TextDrawInfo("", listOf(
							TextItem("", "${verticalRatio[2]}%"),
						))
					))
				}
			}
		}

		val faceXList = flm.map { it.x }
		val faceYList = flm.map { it.y }

		val minX = faceXList.minOrNull() ?: 0f
		val maxX = faceXList.maxOrNull() ?: 0f
		val minY = faceYList.minOrNull() ?: 0f
		val maxY = faceYList.maxOrNull() ?: 0f

// 2. 아까 만든 calculateFinalPixel 함수를 이용해 최종 비트맵 픽셀 좌표로 변환
		val topLeft = calculateFinalPixel(minX, minY)
		val bottomRight = calculateFinalPixel(maxX, maxY)

// 3. FaceRect 객체 생성 (이걸 Fragment에서 OverlayView로 전달해야 함)
		val faceRect = TextBoxOverlayView.FaceRect(
			left = topLeft.x,
			top = topLeft.y,
			right = bottomRight.x,
			bottom = bottomRight.y
		)
		return Pair(textBoxes, faceRect)
	}
//
//	private fun calculateBoxHeight(info: TextDrawInfo): Float {
//		val padding = 12f
//		val lineSpacing = 8f
//
//		val titleBounds = Rect()
//		textPaint.getTextBounds(info.title, 0, info.title.length, titleBounds)
//
//		val itemsHeight = info.items.size * (titleBounds.height() + lineSpacing)
//
//		return titleBounds.height() + lineSpacing + itemsHeight + padding * 2
//	}
//
//	private fun drawTextBoxWithBackground(canvas: Canvas, info: TextDrawInfo, offsetY: Float = 0f) {
//		val padding = 12f
//		val lineSpacing = 8f
//		val labelValueSpacing = 8f
//
//		// 타이틀 bounds
//		val titleBounds = Rect()
//		textPaint.getTextBounds(info.title, 0, info.title.length, titleBounds)
//
//		// 각 아이템의 bounds 계산
//		val itemBounds = info.items.map { item ->
//			val labelBounds = Rect()
//			val valueBounds = Rect()
//			if (item.label.isNotEmpty()) {
//				textPaint.getTextBounds(item.label, 0, item.label.length, labelBounds)
//			}
//			textPaint.getTextBounds(item.value, 0, item.value.length, valueBounds)
//			Pair(labelBounds, valueBounds)
//		}
//
//		// 전체 박스 크기 계산
//		val maxItemWidth = itemBounds.maxOfOrNull { (labelBounds, valueBounds) ->
//			val labelW = labelBounds.width()
//			val valueW = valueBounds.width()
//			if (labelW > 0) {
//				labelW + labelValueSpacing.toInt() + valueW
//			} else {
//				valueW
//			}
//		} ?: 0
//		val boxWidth = maxOf(titleBounds.width(), maxItemWidth).toFloat()
//
//		val titleHeight = titleBounds.height()
//		val itemsHeight = itemBounds.sumOf { maxOf(it.first.height(), it.second.height()) } +
//			(info.items.size - 1) * lineSpacing
//		val totalHeight = titleHeight + lineSpacing + itemsHeight
//
//		// Anchor에 따른 시작 위치 (기준점 기준으로 박스가 어느 방향에 위치하는지)
//		val (boxLeft, boxTop) = when (info.anchor) {
//			EdgePosition.TOP -> {
//				// 기준점이 박스 위쪽 = 박스가 아래에 위치
//				Pair(
//					info.baseX - boxWidth / 2f + info.marginX,
//					info.baseY + info.marginY + offsetY
//				)
//			}
//			EdgePosition.BOTTOM -> {
//				// 기준점이 박스 아래쪽 = 박스가 위에 위치
//				Pair(
//					info.baseX - boxWidth / 2f + info.marginX,
//					info.baseY - totalHeight - padding * 2 - info.marginY - offsetY
//				)
//			}
//			EdgePosition.LEFT -> {
//				// 기준점이 박스 왼쪽 = 박스가 오른쪽에 위치
//				Pair(
//					info.baseX + info.marginX + offsetY,
//					info.baseY - totalHeight / 2f - padding + info.marginY
//				)
//			}
//			EdgePosition.RIGHT -> {
//				// 기준점이 박스 오른쪽 = 박스가 왼쪽에 위치
//				Pair(
//					info.baseX - boxWidth - padding * 2 - info.marginX - offsetY,
//					info.baseY - totalHeight / 2f - padding + info.marginY
//				)
//			}
//		}
//
//		// 박스 그리기
//		val boxRight = boxLeft + boxWidth + padding * 2
//		val boxBottom = boxTop + totalHeight + padding * 2
//		val radius = 8f
//
//		canvas.drawRoundRect(boxLeft, boxTop, boxRight, boxBottom, radius, radius, bgPaint)
//
//		// 타이틀 그리기 (중앙 정렬)
//		val titleX = boxLeft + (boxWidth - titleBounds.width()) / 2f + padding
//		val titleY = boxTop + padding + titleBounds.height()
//		canvas.drawText(info.title, titleX, titleY, textPaint)
//
//		// 각 아이템 그리기
//		var currentY = titleY + lineSpacing
//		info.items.forEachIndexed { index, item ->
//			val (labelBounds, valueBounds) = itemBounds[index]
//
//			if (item.label.isNotEmpty()) {
//				// 레이블이 있는 경우 (왼쪽 정렬)
//				val labelX = boxLeft + padding
//				val labelY = currentY + labelBounds.height()
//				canvas.drawText(item.label, labelX, labelY, textPaint)
//
//				// 값 (오른쪽 정렬)
//				val valueX = boxRight - padding - valueBounds.width()
//				val valueY = currentY + valueBounds.height()
//				canvas.drawText(item.value, valueX, valueY, textPaint)
//			} else {
//				// 레이블이 없는 경우 (중앙 정렬)
//				val valueX = boxLeft + (boxWidth - valueBounds.width()) / 2f + padding
//				val valueY = currentY + valueBounds.height()
//				canvas.drawText(item.value, valueX, valueY, textPaint)
//			}
//
//			currentY += maxOf(labelBounds.height(), valueBounds.height()) + lineSpacing
//		}
//	}
}