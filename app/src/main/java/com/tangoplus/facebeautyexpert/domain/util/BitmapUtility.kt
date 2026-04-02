package com.tangoplus.facebeautyexpert.domain.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.icu.text.DecimalFormat
import android.util.Log
import android.view.Display
import android.view.Surface
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
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.extractFace3DCoordinates
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.extractFaceCoordinates
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.extractHairLine3DCoordinate
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.extractHairLineCoordinate
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.extractPose3DCoordinates
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.extractPoseCoordinates
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.scaleLandmarksToFHD
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkResult
import com.tangoplus.facebeautyexpert.domain.vo.BitmapFlag
import com.tangoplus.facebeautyexpert.domain.vo.Landmarks
import com.tangoplus.facebeautyexpert.domain.vo.Sequence
import com.tangoplus.facebeautyexpert.ui.info.vo.LineType
import kotlinx.coroutines.withContext

object BitmapUtility {
	data class DrawImageUiModel(
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
	): Bitmap? = withContext(Dispatchers.Default) {
		try {
			val seqs = faceResult.seqs
			val seqIndex = seqs.indexOf(seq)
			if (seqIndex == -1) {
				return@withContext createInfoBitmap(fragment.context, 0)
			}
			val imageUri = faceResult.mediaUri.getOrNull(seqIndex) ?: return@withContext createInfoBitmap(
				fragment.context,
				0
			)


			val path = getPathFromContentUri(fragment.requireContext(), imageUri)
			Log.v("imageUri", "$path")

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
			} ?: return@withContext createInfoBitmap(fragment.context, 0)

			val processedBitmap = bitmap.first
			val (originalWidth, originalHeight) = bitmap.second
			val jsonData = faceResult.results.getJSONObject(seqIndex)
			val faceCoordinates = extractFaceCoordinates(jsonData)
			val face3DCoordinates = extractFace3DCoordinates(jsonData)
			val poseCoordinates = extractPoseCoordinates(jsonData)
			val pose3DCoordinates = extractPose3DCoordinates(jsonData)
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
				scaledHairline,
				hairLine3DCoordinate
			)
			val isLineOn = ivm.getLineOn()
			val isGridLineOn = ivm.getGridLineOn()
			val flags = BitmapFlag(
				isLineOn = isLineOn,
				isGridLineOn = isGridLineOn
			)
			val combinedBitmap = combineImageAndOverlay(
				DrawImageUiModel(
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

			finalBitmap.copy(finalBitmap.config ?: Bitmap.Config.ARGB_8888, true)

		} catch (e: Exception) {
			Log.e("getCombineBitmap", "Error: ${e.message}", e)
			null
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
		color = "#32FFAE00".toColorInt()
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
	): Bitmap {

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

//        if (!dim.face3DLandmarks.isNullOrEmpty()) {
//            val startTime = System.currentTimeMillis()
//            Log.d("FaceContour", "=== 시작 ===")
//
//            // 1. 2D 좌표 추출 (이미 FHD로 스케일링된 절대 좌표)
//            val ptsHD = extractLandmarkPoints(faceLandmarks)
//            if (ptsHD.isEmpty()) {
//                Log.e("FaceContour", "포인트가 비어있음")
//
//            }
//
//            // 스케일링 없이 그대로 사용
//            val pts = ptsHD
//
//            Log.d("FaceContour", "포인트 개수: ${pts.size}")
//            Log.d("FaceContour", "첫 포인트: ${pts.firstOrNull()}")
//
//            // 2. Z값 검증 및 정규화
//            if (face3DLandmarks.size != pts.size) {
//                Log.e("FaceContour", "2D와 3D 포인트 개수 불일치: ${pts.size} vs ${face3DLandmarks.size}")
//
//            }
//
//            val zValues = face3DLandmarks.map { it.third }
//            val minZ = zValues.minOrNull() ?: 0f
//            val maxZ = zValues.maxOrNull() ?: 1f
//            val zRange = maxZ - minZ
//
//            Log.d("FaceContour", "Z 범위: $minZ ~ $maxZ (range: $zRange)")
//
//            if (zRange < 0.001f) {
//                Log.e("FaceContour", "Z값 범위가 너무 작음")
//
//            }

//            val heights = face3DLandmarks.map { (it.third - minZ) / zRange }
//
//            // 3. 얼굴 외곽 Path
//            val facePath = Path()
//            if (faceOutLineIndexes.isNotEmpty()) {
//                facePath.moveTo(pts[faceOutLineIndexes[0]].x, pts[faceOutLineIndexes[0]].y)
//                for (i in 1 until faceOutLineIndexes.size) {
//                    val idx = faceOutLineIndexes[i]
//                    if (idx < pts.size) {
//                        facePath.lineTo(pts[idx].x, pts[idx].y)
//                    }
//                }
//                facePath.close()
//            }
//
//            Log.d("FaceContour", "외곽선 포인트 개수: ${faceOutLineIndexes.size}")
//            Log.d("FaceContour", "첫 외곽 포인트: ${pts.getOrNull(faceOutLineIndexes.firstOrNull() ?: 0)}")
//
//            // 4. BBox 계산
//            val bbox = calculateBBox(pts, faceOutLineIndexes)
//            Log.d("FaceContour", "BBox: ${bbox.width()} x ${bbox.height()}")
//            Log.d("FaceContour", "BBox 좌표: (${bbox.left}, ${bbox.top}) ~ (${bbox.right}, ${bbox.bottom})")
//            Log.d("FaceContour", "비트맵 크기: ${resultBitmap.width} x ${resultBitmap.height}")
//
//            // 5. 그리드 필드 생성 (해상도 줄임)
//            val cellPx = 10f // 15 → 10으로 감소 (더 촘촘하게)
//            val fieldW = max(2, min(80, (bbox.width() / cellPx).toInt())) // 50 → 80
//            val fieldH = max(2, min(80, (bbox.height() / cellPx).toInt()))
//
//            Log.d("FaceContour", "그리드 크기: $fieldW x $fieldH")
//
//            val field = Array(fieldH + 1) { FloatArray(fieldW + 1) }
//
//            // 6. IDW 보간 (최적화)
//            val kNearest = 5 // 8 → 5로 감소
//            for (gy in 0..fieldH) {
//                val py = bbox.top + gy * cellPx
//                for (gx in 0..fieldW) {
//                    val px = bbox.left + gx * cellPx
//
//                    // 거리 계산 (제곱근 생략 버전)
//                    val distList = pts.indices.map { i ->
//                        val dx = pts[i].x - px
//                        val dy = pts[i].y - py
//                        i to (dx * dx + dy * dy) // 제곱근 생략
//                    }.sortedBy { it.second }.take(kNearest)
//
//                    var sum = 0f
//                    var weightSum = 0f
//                    for ((i, distSq) in distList) {
//                        val weight = 1f / (sqrt(distSq) + 1f)
//                        sum += heights[i] * weight
//                        weightSum += weight
//                    }
//
//                    field[gy][gx] = if (weightSum > 0) sum / weightSum else 0f
//                }
//            }
//
//            Log.d("FaceContour", "보간 완료 (${System.currentTimeMillis() - startTime}ms)")
//
//            // 필드 값 범위 확인
//            var fieldMin = Float.POSITIVE_INFINITY
//            var fieldMax = Float.NEGATIVE_INFINITY
//            for (row in field) {
//                for (v in row) {
//                    if (v < fieldMin) fieldMin = v
//                    if (v > fieldMax) fieldMax = v
//                }
//            }
//            Log.d("FaceContour", "필드 값 범위: $fieldMin ~ $fieldMax")
//
//            // 7. 등고선 레벨 (전체 범위를 균등하게 커버)
//            val numLevels = 20 // 12 → 20으로 증가
//            val levels = (0 until numLevels).map { i ->
//                fieldMin + (fieldMax - fieldMin) * (i / (numLevels - 1f))
//            }
//            Log.d("FaceContour", "등고선 레벨: ${levels.take(3)}... ~ ${levels.takeLast(2)}")
//
//            // 8. 등고선 그리기
//            canvas.save()
//            canvas.clipPath(facePath) // clip 다시 활성화
//
//            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//                style = Paint.Style.STROKE
//                strokeWidth = 2.5f
//            }
//
//            var drawnLines = 0
//            for (level in levels) {
//                paint.color = getContourColor(level)
//                paint.alpha = 160
//
//                val paths = marchingSquares(field, bbox, cellPx, level)
//                paths.forEach {
//                    canvas.drawPath(it, paint)
//                    drawnLines++
//                }
//            }
//
//            canvas.restore()
//
//            Log.d("FaceContour", "완료! 총 ${drawnLines}개 선분 그림 (${System.currentTimeMillis() - startTime}ms)")
//
//
//            val ptsHD = dim.faceLandmarks.landmarks.map { _Pt2(it.x, it.y) }
//            val (sx, sy) = _inferScaleToBitmap(ptsHD, resultBitmap.width, resultBitmap.height)
//            val pts = ptsHD.map { _Pt2(it.x * sx, it.y * sy) }
//
//            // 2) 외곽점으로 "얼굴 기준 평면" z = a x + b y + c 피팅 (z>1 이상치는 제외)
//            val planeSamples = faceOutLineIndexes.mapNotNull { idx ->
//                val z = dim.face3DLandmarks.getOrNull(idx)?.third ?: return@mapNotNull null
//                if (z > 1f) null else Triple(pts[idx].x, pts[idx].y, z)
//            }
//            if (planeSamples.size >= 3) {
//
//                val (a, b, c) = _fitPlane2D(planeSamples)
//
//                // 3) 잔차 r = z - (a x + b y + c) (코/중앙이 +로 커짐)
//                val residual = FloatArray(dim.face3DLandmarks.size) { i ->
//                    val z = dim.face3DLandmarks[i].third
//                    (z - (a * pts[i].x + b * pts[i].y + c))
//                }
//
//                // 4) 얼굴 내부만 그리도록 Path로 clip
//                val facePath = Path().apply {
//                    moveTo(pts[faceOutLineIndexes.first()].x, pts[faceOutLineIndexes.first()].y)
//                    for (k in 1 until faceOutLineIndexes.size) {
//                        val idx = faceOutLineIndexes[k]
//                        lineTo(pts[idx].x, pts[idx].y)
//                    }
//                    close()
//                }
//
//                // 5) 얼굴 bbox (약간 패딩)
//                val bbox = run {
//                    var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
//                    var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
//                    for (idx in faceOutLineIndexes) {
//                        val p = pts[idx]
//                        if (p.x < minX) minX = p.x
//                        if (p.y < minY) minY = p.y
//                        if (p.x > maxX) maxX = p.x
//                        if (p.y > maxY) maxY = p.y
//                    }
//                    val pad = 10f
//                    RectF(
//                        max(0f, minX - pad), max(0f, minY - pad),
//                        min(resultBitmap.width.toFloat(),  maxX + pad),
//                        min(resultBitmap.height.toFloat(), maxY + pad)
//                    )
//                }
//
//                // 6) IDW(1/d)로 잔차 필드 보간 (그리드 해상도: cellPx)
//                val cellPx = 6                // 더 촘촘히(얼굴 중심 디테일 위해)
//                val fieldW = max(2, (bbox.width()  / cellPx).roundToInt())
//                val fieldH = max(2, (bbox.height() / cellPx).roundToInt())
//                val field = Array(fieldH + 1) { FloatArray(fieldW + 1) }
//
//                val kNearest = 15
//                for (gy in 0..fieldH) {
//                    val py = bbox.top + gy * cellPx
//                    for (gx in 0..fieldW) {
//                        val px = bbox.left + gx * cellPx
//                        val nearest = pts.indices.asSequence()
//                            .map { i -> i to hypot((pts[i].x - px).toDouble(), (pts[i].y - py).toDouble()) }
//                            .sortedBy { it.second }
//                            .take(kNearest)
//                            .toList()
//                        var num = 0.0; var den = 0.0; val eps = 1e-4
//                        for ((i, d) in nearest) {
//                            val w = 1.0 / max(d, eps)
//                            num += w * residual[i]
//                            den += w
//                        }
//                        field[gy][gx] = if (den == 0.0) 0f else (num / den).toFloat()
//                    }
//                }
//
//                // 6-1) 가벼운 스무딩(엣지 톱니 제거)
//                _smoothField3x3(field)
//
//                // 7) 레벨 자동 생성: 데이터 실제 범위 기반(절삭으로 이상치 완화)
//                val targetLines = 30             // 원하는 선 개수
//                val winsor = 0.1f               // 5% 절삭(선택)
//                var fmin = Float.POSITIVE_INFINITY
//                var fmax = Float.NEGATIVE_INFINITY
//                for (row in field) for (v in row) { if (v < fmin) fmin = v; if (v > fmax) fmax = v }
//
//                run {
//                    val flat = FloatArray(field.size * field[0].size)
//                    var t = 0
//                    for (row in field) for (v in row) flat[t++] = v
//                    flat.sort()
//                    val loIdx = ((flat.size - 1) * winsor).toInt().coerceIn(0, flat.size - 1)
//                    val hiIdx = ((flat.size - 1) * (1f - winsor)).toInt().coerceIn(0, flat.size - 1)
//                    fmin = max(fmin, flat[loIdx])
//                    fmax = min(fmax, flat[hiIdx])
//                }
//                if (fmax - fmin < 1e-6f) { fmin -= 0.001f; fmax += 0.001f }
//                val amp = max(abs(fmin), abs(fmax))
//                val levels = (0 until targetLines).map { i ->
//                    val t = i / (targetLines - 1f)
//                    -amp + (2f * amp) * t
//                }
//
//                // 8) 등고선 그리기 (얼굴 내부 clip)
//                canvas.save()
//                canvas.clipPath(facePath)
//
//                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//                    style = Paint.Style.STROKE
//                    strokeWidth = 1.8f
//                }
//
//                for (iso in levels) {
//                    paint.color = _contourColor(iso, amp / max(1f, targetLines / 2f), targetLines / 2).apply {
//                        // 투명도
//                    }
//                    paint.alpha = 127
//                    val paths = _marchingSquares(field, bbox, cellPx, iso)
//                    paths.forEach { canvas.drawPath(it, paint) }
//                }
//                canvas.restore()
//            }
//        }

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

		if (dim.flags.isLineOn) {
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
				val topLm0 = dimFLS.landmarks[11]
				val bottomLm0 = dimFLS.landmarks[12]
				val rawData = extractRawData(dim, DrawLine.A_SHOULDER)
				val nullCheckRawData = if (rawData.isNotEmpty()) rawData[0] else ""
				val x00 = topLm0.x
				val y00 = topLm0.y
				val x01 = bottomLm0.x
				val y01 = bottomLm0.y
				drawExtendedLine(canvas, x00, y00, x01, y01, 50f, 50f, whitePaint, )
			}

			if (dim.setedLine.contains(DrawLine.NECK)) {
				val topLm0 = dimPLS.landmarks[11]
				val bottomLm0 = dimPLS.landmarks[12]
				val rawData = extractRawData(dim, DrawLine.NECK)
				val nullCheckRawData = if (rawData.isNotEmpty()) rawData[0] else ""

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
				) // neck에 대해서 끝 위치
			}

			if (dim.setedLine.contains(DrawLine.FACE)) {
				// height
				val topLm0 = dimFLS.landmarks[152]
				val bottomLm0 = dimFLS.landmarks[10]
				val rawData = extractRawData(dim, DrawLine.FACE)
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
				val rawData0 = extractRawData(dim, DrawLine.A_HORIZON)
				val nullCheckRawData0 = if (rawData0.isNotEmpty()) rawData0[0] else ""
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



				val nullCheckRawData1 = if (rawData0.isNotEmpty()) rawData0[1] else ""
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

				val nullCheckRawData2 = if (rawData0.isNotEmpty()) rawData0[2] else ""
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


				val nullCheckRawData3 = if (rawData0.isNotEmpty()) rawData0[3] else ""
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
				val rawData = extractRawData(dim, DrawLine.L_CORNEA)
				val nullCheckRawData = if (rawData.isNotEmpty()) rawData[0] else ""
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
				val rawData = extractRawData(dim, DrawLine.EYEBROW)
				val nullCheckRawData = if (rawData.isNotEmpty()) rawData[0] else ""
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
				val rawData = extractRawData(dim, DrawLine.NOSE)
				val nullCheckRawData = if (rawData.isNotEmpty()) rawData[0] else ""
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
				val rawData = extractRawData(dim, DrawLine.MOUSE)
				val nullCheckRawData = if (rawData.isNotEmpty()) rawData[0] else ""
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
				val rawData = extractRawData(dim, DrawLine.A_FACIAL_AXIS)
				val nullCheckRawData = if (rawData.isNotEmpty()) rawData[0] else ""
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
			if ( dim.setedLine.contains(DrawLine.A_RATIO_VERTI)) {

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
//			if (dim.setedLine.isEmpty()) {
//				// ----------# 얼굴 외곽 선 #----------
//				if (dim.seq !in listOf(Sequence.SIDE_LEFT, Sequence.SIDE_RIGHT)) {
//					val outLinePath = Path()
//					if (dim.lms.faceLandmarks.landmarks.isNotEmpty()) {
//						faceOutLineIndexes.forEachIndexed { i, landmarkIndex ->
//							val landmark = dimFLS.landmarks[landmarkIndex]
//
//							val x = landmark.x
//							val y = landmark.y
//
//							if (i == 0) {
//								outLinePath.moveTo(x, y) // 시작점
//							} else {
//								outLinePath.lineTo(x, y) // 선 연결
//							}
//						}
//						canvas.drawPath(outLinePath, outLinePaint)
//					}
//
//					// -----------# 쇄골 중심 선 #------------
//					if (dimPLS.landmarks.isNotEmpty()) {
//						val midShoulderX = (dimPLS.landmarks[11].x + dimPLS.landmarks[12].x) / 2
//						val midShoulderY = (dimPLS.landmarks[11].y + dimPLS.landmarks[12].y) / 2
//						drawExtendedLine(
//							canvas,
//							midShoulderX,
//							midShoulderY,
//							midShoulderX,
//							midShoulderY + 1,
//							1500f,
//							1500f,
//							neckDashedPaint
//						)
//						canvas.drawCircle(dimPLS.landmarks[11].x, dimPLS.landmarks[11].y, 12f, circlePaint)
//						canvas.drawCircle(dimPLS.landmarks[12].x, dimPLS.landmarks[12].y, 12f, circlePaint)
//
//						val centerForehead = dimFLS.landmarks[10]
//						val betweenEyeBrows = dimFLS.landmarks[9]
//						// 얼굴 대칭 선 길이 계산
//						val length = abs(centerForehead.y - betweenEyeBrows.y)
//						val topLength = when (dim.seq) {
//							Sequence.JAW_OPENING, Sequence.NECK_EXTENTION -> length * 3f
//							else -> length * 1.5f
//						}
//						val bottomLength = when (dim.seq) {
//							Sequence.JAW_OPENING, Sequence.NECK_EXTENTION -> length * 10f
//							else -> length * 5f
//						}
//						drawExtendedLine(
//							canvas,
//							centerForehead.x,
//							centerForehead.y,
//							betweenEyeBrows.x,
//							betweenEyeBrows.y,
//							topLength,
//							bottomLength,
//							faceDashedPaint
//						)
//					}
//				} else if (dim.seq == Sequence.SIDE_LEFT) {
//					if (dimPLS.landmarks.isNotEmpty()) {
//						val pls = dimPLS.landmarks
//						canvas.drawCircle(pls[0].x, pls[0].y, 12f, circlePaint)
//						canvas.drawCircle(pls[11].x, pls[11].y, 12f, circlePaint)
//						drawExtendedLine(
//							canvas,
//							pls[11].x,
//							pls[11].y - 1,
//							pls[11].x,
//							pls[11].y + 1,
//							1500f,
//							1500f,
//							faceDashedPaint
//						)
//					}
//				} else if (dim.seq == Sequence.SIDE_RIGHT) {
//					if (dimPLS.landmarks.isNotEmpty()) {
//						val pls = dimPLS.landmarks
//						canvas.drawCircle(pls[0].x, pls[0].y, 12f, circlePaint)
//						canvas.drawCircle(pls[12].x, pls[12].y, 12f, circlePaint)
//						drawExtendedLine(
//							canvas,
//							pls[12].x,
//							pls[12].y - 1,
//							pls[12].x,
//							pls[12].y + 1,
//							1500f,
//							1500f,
//							faceDashedPaint
//						)
//					}
//				}
//			}
// ------------------------------# 선택에 따라 바로 그리기 #----------------------------------
		createRawDataBox(canvas, dim)
		return drawDirectionUIOnBitmap(resultBitmap)
	}

//	fun drawExtendedLine(
//		canvas: Canvas,
//		x0: Float,
//		y0: Float,
//		x1: Float,
//		y1: Float,
//		startExtension: Float,
//		endExtension: Float,
//		paint: Paint,
//		text: String = "",
//		textPosition: Int = 2,
//		lineType: LineType = LineType.LENGTH
//	) {
//		val dx = x1 - x0
//		val dy = y1 - y0
//		val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
//
//		if (length == 0f) return
//
//		val nx = dx / length
//		val ny = dy / length
//
//		val extendedStartX = x0 - nx * startExtension
//		val extendedStartY = y0 - ny * startExtension
//		val extendedEndX = x1 + nx * endExtension
//		val extendedEndY = y1 + ny * endExtension
//
//		// 점선 효과
//		val dashedPaint = Paint(paint).apply {
//			pathEffect = DashPathEffect(floatArrayOf(2f, 4f), 0f)
//		}
//		canvas.drawLine(extendedStartX, extendedStartY, extendedEndX, extendedEndY, dashedPaint)
//
//		// 끝점 마커
//		when (lineType) {
//			LineType.ANGLE -> {
//				// ◆ 다이아몬드
//				drawDiamond(canvas, extendedStartX, extendedStartY, paint)
//				drawDiamond(canvas, extendedEndX, extendedEndY, paint)
//
//			}
//			LineType.LENGTH -> {
//				// ▼ ▲ 삼각형 화살표
//				drawTriangleArrow(canvas, extendedStartX, extendedStartY, nx, ny, paint, isStart = true)
//				drawTriangleArrow(canvas, extendedEndX, extendedEndY, nx, ny, paint, isStart = false)
//			}
//		}
//
//		// 텍스트 (기존 동일)
//		if (text != "") {
//			val textPaint = Paint().apply {
//				color = Color.BLACK
//				textSize = 16f
//				isAntiAlias = true
//			}
//			val bgPaint = Paint().apply {
//				color = Color.argb(100, 255, 255, 255)
//				isAntiAlias = true
//			}
//
//			val textOffset = 20f
//			val (baseX, baseY) = when (textPosition) {
//				0 -> Pair(extendedStartX - nx * textOffset, extendedStartY - ny * textOffset)
//				1 -> Pair((extendedStartX + extendedEndX) / 2f, (extendedStartY + extendedEndY) / 2f)
//				2 -> Pair(extendedEndX + nx * textOffset, extendedEndY + ny * textOffset)
//				else -> Pair(extendedEndX + nx * textOffset, extendedEndY + ny * textOffset)
//			}
//
//			val bounds = Rect()
//			textPaint.getTextBounds(text, 0, text.length, bounds)
//			val padding = 12f
//
//			val textX = when (textPosition) {
//				0 -> baseX - bounds.width() - padding
//				1 -> baseX - bounds.width() / 2f
//				2 -> baseX
//				else -> baseX
//			}
//			val textY = baseY
//
//			val left = textX - padding
//			val top = textY + bounds.top - padding
//			val right = textX + bounds.width() + padding
//			val bottom = textY + bounds.bottom + padding
//			val radius = minOf(right - left, bottom - top) / 2f
//
//			canvas.drawRoundRect(left, top, right, bottom, radius, radius, bgPaint)
//			canvas.drawText(text, textX, textY, textPaint)
//		}
//	}
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

//	private data class _Pt2(val x: Float, val y: Float)
//
//	/** 좌표가 [0..1] or [0..2] 정규화돼 있으면 비트맵 크기에 맞게 자동 스케일 */
//	private fun _inferScaleToBitmap(pts: List<_Pt2>, bmpW: Int, bmpH: Int): Pair<Float, Float> {
//		val maxX = pts.maxOf { it.x }
//		val maxY = pts.maxOf { it.y }
//		// MP 포즈/페이스는 보통 0..width/height 픽셀로 오는 경우와 0..1 정규화 둘 다 존재
//		return when {
//			maxX <= 2f && maxY <= 2f -> bmpW.toFloat() to bmpH.toFloat()
//			else -> 1f to 1f
//		}
//	}
//
//	/** 외곽 2D 점들로 z = a*x + b*y + c 평면 피팅(최소제곱) */
//	private fun _fitPlane2D(samples: List<Triple<Float, Float, Float>>): Triple<Float, Float, Float> {
//		var Sx = 0.0;
//		var Sy = 0.0;
//		var Sz = 0.0
//		var Sxx = 0.0;
//		var Syy = 0.0;
//		var Sxy = 0.0
//		var Sxz = 0.0;
//		var Syz = 0.0
//		val n = samples.size.toDouble()
//		for ((x, y, z) in samples) {
//			val xd = x.toDouble();
//			val yd = y.toDouble();
//			val zd = z.toDouble()
//			Sx += xd; Sy += yd; Sz += zd
//			Sxx += xd * xd; Syy += yd * yd; Sxy += xd * yd
//			Sxz += xd * zd; Syz += yd * zd
//		}
//		fun det(
//			a: Double,
//			b: Double,
//			c: Double,
//			d: Double,
//			e: Double,
//			f: Double,
//			g: Double,
//			h: Double,
//			i: Double
//		) =
//			a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
//
//		val D = det(Sxx, Sxy, Sx, Sxy, Syy, Sy, Sx, Sy, n)
//		val Da = det(Sxz, Sxy, Sx, Syz, Syy, Sy, Sz, Sy, n)
//		val Db = det(Sxx, Sxz, Sx, Sxy, Syz, Sy, Sx, Sz, n)
//		val Dc = det(Sxx, Sxy, Sxz, Sxy, Syy, Syz, Sx, Sy, Sz)
//		return Triple((Da / D).toFloat(), (Db / D).toFloat(), (Dc / D).toFloat())
//	}
//
//	/** 3×3 박스블러 1회 (그리드 톱니/끊김 완화) */
//	private fun _smoothField3x3(field: Array<FloatArray>) {
//		val h = field.size;
//		val w = field[0].size
//		val copy = Array(h) { field[it].clone() }
//		for (y in 1 until h - 1) for (x in 1 until w - 1) {
//			var s = 0f
//			for (dy in -1..1) for (dx in -1..1) s += copy[y + dy][x + dx]
//			field[y][x] = s / 9f
//		}
//	}
//
//	/** Marching Squares → Path 목록 */
//	private fun _marchingSquares(
//		field: Array<FloatArray>, bbox: RectF, cellPx: Int, iso: Float
//	): List<Path> {
//		val H = field.size - 1
//		val W = field[0].size - 1
//		fun v(gx: Int, gy: Int) = field[gy][gx]
//		fun interp(ax: Float, ay: Float, bx: Float, by: Float, av: Float, bv: Float): PointF {
//			val t = if (abs(bv - av) < 1e-6f) .5f else (iso - av) / (bv - av)
//			return PointF(ax + (bx - ax) * t, ay + (by - ay) * t)
//		}
//
//		val segs = ArrayList<Pair<PointF, PointF>>()
//		for (gy in 0 until H) for (gx in 0 until W) {
//			val x0 = bbox.left + gx * cellPx;
//			val y0 = bbox.top + gy * cellPx
//			val x1 = x0 + cellPx;
//			val y1 = y0 + cellPx
//			val vTL = v(gx, gy);
//			val vTR = v(gx + 1, gy);
//			val vBR = v(gx + 1, gy + 1);
//			val vBL = v(gx, gy + 1)
//			var idx = 0
//			if (vTL > iso) idx = idx or 1; if (vTR > iso) idx = idx or 2
//			if (vBR > iso) idx = idx or 4; if (vBL > iso) idx = idx or 8
//			if (idx == 0 || idx == 15) continue
//			fun top() = interp(x0, y0, x1, y0, vTL, vTR)
//			fun right() = interp(x1, y0, x1, y1, vTR, vBR)
//			fun bottom() = interp(x0, y1, x1, y1, vBL, vBR)
//			fun left() = interp(x0, y0, x0, y1, vTL, vBL)
//			when (idx) {
//				1, 14 -> segs += (top() to left())
//				2, 13 -> segs += (top() to right())
//				3, 12 -> segs += (right() to left())
//				4, 11 -> segs += (right() to bottom())
//				5 -> {
//					val a = top();
//					val b = right();
//					val c = bottom();
//					val d = left(); segs += (a to b); segs += (d to c)
//				}
//
//				6, 9 -> segs += (top() to bottom())
//				7, 8 -> segs += (bottom() to left())
//				10 -> {
//					val a = right();
//					val b = bottom();
//					val c = top();
//					val d = left(); segs += (a to b); segs += (c to d)
//				}
//			}
//		}
//		// 세그먼트 → Path (허용오차 2.5px로 연결성 강화)
//		fun near(a: PointF, b: PointF, tol: Float = 2.5f) =
//			abs(a.x - b.x) <= tol && abs(a.y - b.y) <= tol
//
//		val left = segs.toMutableList()
//		val paths = ArrayList<Path>()
//		while (left.isNotEmpty()) {
//			val (s0, e0) = left.removeAt(left.lastIndex)
//			val pts = arrayListOf(s0, e0)
//			var extended = true
//			while (extended) {
//				extended = false;
//				var i = 0
//				while (i < left.size) {
//					val (a, b) = left[i]
//					when {
//						near(pts.last(), a) -> {
//							pts.add(b);left.removeAt(i);extended = true;continue
//						}
//
//						near(pts.last(), b) -> {
//							pts.add(a);left.removeAt(i);extended = true;continue
//						}
//
//						near(pts.first(), a) -> {
//							pts.add(0, b);left.removeAt(i);extended = true;continue
//						}
//
//						near(pts.first(), b) -> {
//							pts.add(0, a);left.removeAt(i);extended = true;continue
//						}
//
//						else -> i++
//					}
//				}
//			}
//			val path = Path().apply {
//				moveTo(pts[0].x, pts[0].y); for (k in 1 until pts.size) lineTo(
//				pts[k].x,
//				pts[k].y
//			)
//			}
//			paths += path
//		}
//		return paths
//	}
//
//	/** 레벨별 색상 (0 근처 연녹, +는 따뜻한 톤, -는 차가운 톤) */
//	private fun _contourColor(level: Float, step: Float, n: Int): Int {
//		val t = (level / (step * max(1, n))).coerceIn(-1f, 1f)
//		return if (t >= 0f) {
//			val r = (200 + 40 * t).toInt()
//			val g = (180 - 140 * t).toInt()
//			val b = (140 - 100 * t).toInt()
//			Color.rgb(r, g, b)
//		} else {
//			val u = -t
//			val r = (80 - 60 * u).toInt()
//			val g = (180 + 40 * u).toInt()
//			val b = (220 + 20 * u).toInt()
//			Color.rgb(r, g, b)
//		}
//	}
//
//	fun drawNoseDepthContour(
//		canvas: Canvas,
//		depthData: ShortArray,
//		depthWidth: Int,
//		depthHeight: Int,
//		faceLandmarks: FaceLandmarkResult,
//		scaleX: Float,
//		scaleY: Float,
//		roiSizePx: Int = 100,      // 코 주변 ROI 크기
//		levelCount: Int = 10,      // 등고선 개수
//		lineColor: Int = Color.argb(200, 255, 220, 180),
//		strokePx: Float = 2f
//	) {
//		val nose = faceLandmarks.landmarks.getOrNull(4) ?: return
//		val cx = (nose.x * depthWidth).toInt()
//		val cy = (nose.y * depthHeight).toInt()
//
//		val half = roiSizePx / 2
//		val x0 = (cx - half).coerceAtLeast(0)
//		val y0 = (cy - half).coerceAtLeast(0)
//		val x1 = (cx + half).coerceAtMost(depthWidth - 1)
//		val y1 = (cy + half).coerceAtMost(depthHeight - 1)
//		val w = x1 - x0
//		val h = y1 - y0
//		if (w <= 4 || h <= 4) return
//
//		// 1️⃣ depth ROI 추출 (mm → m)
//		val zmap = Array(h) { y ->
//			FloatArray(w) { x ->
//				val idx = (y0 + y) * depthWidth + (x0 + x)
//				val d = depthData[idx].toInt() and 0xFFFF
//				if (d > 0) d / 1000f else Float.NaN
//			}
//		}
//
//		// 2️⃣ 유효 z 평균
//		val values = zmap.flatMap { it.filter { v -> v.isFinite() } }
//		if (values.isEmpty()) return
//		val zMin = values.minOrNull() ?: return
//		val zMax = values.maxOrNull() ?: return
//
//		// 3️⃣ 등고선 레벨 정의
//		val levels = (0 until levelCount).map { i ->
//			zMin + (zMax - zMin) * (i / (levelCount - 1f))
//		}
//
//		val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//			style = Paint.Style.STROKE
//			strokeWidth = strokePx
//			color = lineColor
//		}
//
//		// 4️⃣ marching squares로 등고선 계산
//		for (level in levels) {
//			val path = Path()
//			for (y in 0 until h - 1) {
//				for (x in 0 until w - 1) {
//					val v = FloatArray(4)
//					for (k in 0..3) {
//						val (xx, yy) = when (k) {
//							0 -> Pair(x, y)
//							1 -> Pair(x + 1, y)
//							2 -> Pair(x + 1, y + 1)
//							else -> Pair(x, y + 1)
//						}
//						v[k] = zmap[yy][xx]
//					}
//					if (v.any { it.isNaN() }) continue
//					val mask = (if (v[0] < level) 1 else 0) or
//						(if (v[1] < level) 2 else 0) or
//						(if (v[2] < level) 4 else 0) or
//						(if (v[3] < level) 8 else 0)
//					if (mask == 0 || mask == 15) continue
//
//					fun interp(p1: Float, p2: Float, t1: Float, t2: Float): Float {
//						return if (abs(p2 - p1) < 1e-6f) p1
//						else p1 + (level - t1) * (p2 - p1) / (t2 - t1)
//					}
//
//					val edges = mutableListOf<Pair<Float, Float>>()
//					if ((mask and 1) != (mask and 2)) {
//						val fx = interp(x.toFloat(), x + 1f, v[0], v[1])
//						edges += Pair(fx, y.toFloat())
//					}
//					if ((mask and 2) != (mask and 4)) {
//						val fy = interp(y.toFloat(), y + 1f, v[1], v[2])
//						edges += Pair(x + 1f, fy)
//					}
//					if ((mask and 4) != (mask and 8)) {
//						val fx = interp(x.toFloat(), x + 1f, v[3], v[2])
//						edges += Pair(fx, y + 1f)
//					}
//					if ((mask and 8) != (mask and 1)) {
//						val fy = interp(y.toFloat(), y + 1f, v[0], v[3])
//						edges += Pair(x.toFloat(), fy)
//					}
//
//					if (edges.size >= 2) {
//						val (p1, p2) = edges
//						val px1 = (x0 + p1.first) * (scaleX / depthWidth)
//						val py1 = (y0 + p1.second) * (scaleY / depthHeight)
//						val px2 = (x0 + p2.first) * (scaleX / depthWidth)
//						val py2 = (y0 + p2.second) * (scaleY / depthHeight)
//						path.moveTo(px1, py1)
//						path.lineTo(px2, py2)
//					}
//				}
//			}
//			canvas.drawPath(path, paint)
//		}
//	}


	val df = DecimalFormat("#.#")
	private fun extractRawData(dim: DrawImageUiModel, dl: DrawLine): List<String> {
		val fr = dim.faceResult

		val seq = dim.seq
		val seqss = fr.seqs // 0 1 2 3 4 5 6 7 8 9
		val seqIndex = seqss.indexOf(dim.seq)

		val keys = when (dl) {
			DrawLine.A_SAGGING_FACE -> listOf("comfort_left_sagging_scale", "comfort_right_sagging_scale")
			DrawLine.A_SHOULDER -> when (seq) {
				Sequence.COMFORT -> listOf("comfort_shoulder_horizontal_angle")
				else -> listOf("neck_extention_shoulder_horizontal_angle")
			}

			DrawLine.NECK -> when (seq) {
				Sequence.COMFORT -> listOf("comfort_neck_vertical_angle", "comfort_neck_length")
				else -> listOf("neck_extention_neck_vertical_angle")
			}
			DrawLine.FACE -> when (seq) {
				Sequence.COMFORT -> listOf("comfort_face_vertical_angle", "comfort_face_length")
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
			DrawLine.EYE -> listOf(
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
			DrawLine.L_CORNEA -> listOf(
				"comfort_left_cornea_length",
				"comfort_right_cornea_length",
				)
			DrawLine.EYEBROW -> listOf(
				"comfort_left_eyebrow_horizontal_angle",
				"comfort_right_eyebrow_horizontal_angle",
				"comfort_left_eyebrow_start_length",
				"comfort_left_eyebrow_arch_length",
				"comfort_left_eyebrow_end_length",
				"comfort_right_eyebrow_start_length",
				"comfort_right_eyebrow_arch_length",
				"comfort_right_eyebrow_end_length",
			)
			DrawLine.NOSE -> listOf("comfort_nose_width",
				"comfort_nose_height",
				"comfort_alar_horizontal_angle",
				"comfort_left_alar_length",
				"comfort_right_alar_length"
			)
			DrawLine.A_CANTHUS_ORAL -> listOf("comfort_canthus_oral_left_vertical_angle", "comfort_canthus_oral_right_vertical_angle")
			DrawLine.A_ALAR_ORAL -> listOf("comfort_alar_tip_of_lips_left_vertical_angle", "comfort_alar_tip_of_lips_right_vertical_angle")
			DrawLine.A_SIDE_HEAD -> listOf("comfort_side_head_left_angle", "comfort_side_head_right_angle")
			DrawLine.E_CHEEKS -> listOf("comfort_left_cheeks_extent", "comfort_right_cheeks_extent")
			DrawLine.E_CHEEKBONE -> listOf("comfort_left_cheekbone_extent","comfort_right_cheekbone_extent")
			DrawLine.MOUSE -> listOf(
				"comfort_left_mouse_corner_angle",
				"comfort_right_mouse_corner_angle",
				"comfort_philtrum_length",
				"comfort_mouse_width",
				"comfort_upper_lips_height",
				"comfort_lower_lips_height",
			)

			DrawLine.A_FACIAL_AXIS -> listOf("comfort_length_symmetry_factor", "comfort_angle_symmetry_factor")

			DrawLine.A_RATIO_VERTI -> listOf("")
			DrawLine.A_RATIO_HORIZON -> listOf("")
		}
		val jsonObject = fr.results.getJSONObject(seqIndex)
		return keys.map { key -> df.format(jsonObject.getJSONObject("data").optDouble(key)) }
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



 // 🔥🔥🔥🔥🔥🔥🔥 rawData 박스 만들기 🔥🔥🔥🔥🔥🔥🔥
	 data class TextDrawInfo(
		 val text: String,
		 val baseX: Float,
		 val baseY: Float,
		 val textPosition: Int // 0: 왼쪽, 1: 중앙, 2: 오른쪽
	 )
	val textPaint = Paint().apply {
		color = Color.BLACK
		textSize = 16f
		isAntiAlias = true
	}
	val bgPaint = Paint().apply {
		color = Color.argb(100, 255, 255, 255)
		isAntiAlias = true
	}
	val textOffset = 20f

	private fun createRawDataBox(canvas: Canvas, dim: DrawImageUiModel) {
		val dl = dim.setedLine
		val flm = dim.lms.faceLandmarks.landmarks

		val textDrawInfos = mutableListOf<TextDrawInfo>()

		// 각 DrawLine에 따라 데이터와 위치 수집
		if (dl.contains(DrawLine.A_SAGGING_FACE)) {
			val boxLocation = flm[152]
			val rawData = extractRawData(dim, DrawLine.A_SAGGING_FACE)

			rawData.forEachIndexed { index, value ->
				textDrawInfos.add(
					TextDrawInfo(
						text = value,
						baseX = boxLocation.x,
						baseY = boxLocation.y + (index * textOffset),
						textPosition = 0 // 또는 원하는 위치
					)
				)
			}
		}

		if (dl.contains(DrawLine.A_SHOULDER)) {
			val boxLocation = flm
			val rawData = extractRawData(dim, DrawLine.A_SHOULDER)

			rawData.forEachIndexed { index, value ->
				textDrawInfos.add(
					TextDrawInfo(
						text = value,
						baseX = boxLocation.x,
						baseY = boxLocation.y + (index * textOffset),
						textPosition = 1
					)
				)
			}
		}

		if (dl.contains(DrawLine.NECK)) {

		}
		if (dl.contains(DrawLine.FACE)) {

		}
		if (dl.contains(DrawLine.A_HORIZON)) {

		}
		if (dl.contains(DrawLine.EYE)) {

		}
		if (dl.contains(DrawLine.L_CORNEA)) {

		}
		if (dl.contains(DrawLine.EYEBROW)) {

		}
		if (dl.contains(DrawLine.NOSE)) {

		}

		if (dl.contains(DrawLine.E_CHEEKBONE)) {

		}
		if (dl.contains(DrawLine.A_ALAR_ORAL)) {

		}
		if (dl.contains(DrawLine.A_SIDE_HEAD)) {

		}
		if (dl.contains(DrawLine.A_CANTHUS_ORAL)) {

		}
		if (dl.contains(DrawLine.E_CHEEKS)) {

		}
		if (dl.contains(DrawLine.MOUSE)) {

		}
		if (dl.contains(DrawLine.A_FACIAL_AXIS)) {

		}
		if (dl.contains(DrawLine.A_RATIO_VERTI)) {

		}
		if (dl.contains(DrawLine.A_RATIO_HORIZON)) {

		}

		// 수집된 모든 텍스트를 한 번에 그리기
		textDrawInfos.forEach { info ->
			drawTextWithBackground(canvas, info)
		}
	}

	private fun drawTextWithBackground(canvas: Canvas, info: TextDrawInfo) {
		val bounds = Rect()
		textPaint.getTextBounds(info.text, 0, info.text.length, bounds)
		val padding = 12f

		val textX = when (info.textPosition) {
			0 -> info.baseX - bounds.width() - padding
			1 -> info.baseX - bounds.width() / 2f
			2 -> info.baseX
			else -> info.baseX
		}
		val textY = info.baseY

		val left = textX - padding
		val top = textY + bounds.top - padding
		val right = textX + bounds.width() + padding
		val bottom = textY + bounds.bottom + padding
		val radius = minOf(right - left, bottom - top) / 2f

		canvas.drawRoundRect(left, top, right, bottom, radius, radius, bgPaint)
		canvas.drawText(info.text, textX, textY, textPaint)
	}
}