package com.tangoplus.tangoface.ui.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.graphics.plus
import androidx.core.graphics.times
import androidx.core.graphics.toColorInt
import androidx.core.view.animation.PathInterpolatorCompat
import com.tangoplus.tangoface.domain.opencv.HsvColorTracker
import com.tangoplus.tangoface.domain.util.BitmapUtility.drawExtendedLine
import com.tangoplus.tangoface.domain.vision.face.FaceLandmarkResult
import com.tangoplus.tangoface.orbbec.view.OBGLView
import com.tangoplus.tangoface.orbbec.vo.Point3D
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class OverlayView(context: Context?, attrs: AttributeSet?) :
	View(context, attrs) {

	private var results: FaceLandmarkResult? = null
	private var point3dResult: List<Point3D?>? = null
	private var outLinePaint = Paint()
	private var standardPaint = Paint()
	private var notStandardPaint = Paint()
	private var trajectory1Paint = Paint()
	private var trajectory2Paint = Paint()
	private var scaleFactor: Float = 1f
	private var imageWidth: Int = 1
	private var imageHeight: Int = 1

	private var verticalLineSuccess = false
	private var horizontalLineSuccess = false
	private var currentRunningMode: RunningMode = RunningMode.IMAGE

	private var dotPaint = Paint()
	private var scanAnimator: ValueAnimator? = null
	private var isScanning = false
	private var currentScanY = 0f
	private var scanMinY = 0f
	private var scanMaxY = 0f

	private var currentRepeatCount = 0
	private var isStopRequested = false

	// 애니메이션 종료 시점을 Activity에 알리는 리스너
	private var onScanFinishedListener: (() -> Unit)? = null

	private val waveHeight = 250f // 빛이 퍼지는 범위
	private val dotRadius = 1.5f    // 점의 크기

	fun setOnScanFinishedListener(listener: () -> Unit) {
		this.onScanFinishedListener = listener
	}

	fun setVerti(isSuccess: Boolean) {
		verticalLineSuccess = isSuccess
	}

	fun setHorizon(isSuccess: Boolean) {
		horizontalLineSuccess = isSuccess
	}

	init {
		initPaints()
	}

	private val chinTrajectoryPoints = mutableListOf<PointF>() // 누적 궤적 좌표

	fun addChinTrajectoryPoint(x: Float, y: Float) {
		chinTrajectoryPoints.add(PointF(x, y))
		invalidate()
	}

	fun getVerti(): Boolean {
		return verticalLineSuccess
	}

	fun getHorizon(): Boolean {
		return horizontalLineSuccess
	}

	fun clear() {
		results = null
		standardPaint.reset()
		notStandardPaint.reset()
		outLinePaint.reset()
		chinTrajectoryPoints.clear()
		invalidate()
		initPaints()
	}

	enum class RunningMode {
		IMAGE, VIDEO, LIVE_STREAM
	}
	private val scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.parseColor("#B0FF00")
		strokeWidth = 4f
		style = Paint.Style.STROKE
		maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
	}
	private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG)

	// 랜드마크 인덱스 -> 마지막으로 파동이 닿은 시각(ms). trail fade용
	private val lastLitTime = HashMap<Int, Long>()
	private val trailDurationMs = 900L

	private fun initPaints() {

		standardPaint = Paint().apply {
			color = "#8000FF00".toColorInt()
			strokeWidth = 3f
			isAntiAlias = true
			style = Paint.Style.STROKE
			maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.SOLID)
		}
		notStandardPaint = Paint().apply {
			color = "#80FF2819".toColorInt()
			strokeWidth = 3f
			isAntiAlias = true
			style = Paint.Style.STROKE
			maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.SOLID)
		}
		outLinePaint = Paint().apply {
			color = "#FFFFFF".toColorInt()
			strokeWidth = 4f
			style = Paint.Style.STROKE
			isAntiAlias = true
//            maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
		}
		trajectory1Paint = Paint().apply {
			color = "#FFD700".toColorInt()
			strokeWidth = 4f
			style = Paint.Style.STROKE
			isAntiAlias = true
//            maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
		}
		trajectory2Paint = Paint().apply {
			color = "#00FF00".toColorInt()
			strokeWidth = 4f
			style = Paint.Style.STROKE
			isAntiAlias = true
//            maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
		}

		dotPaint = Paint().apply {
			color = "#80FFFFFF".toColorInt() // 스캐너 느낌의 형광 민트/녹색
			strokeWidth = 8f
			style = Paint.Style.STROKE
			strokeCap = Paint.Cap.ROUND
			isAntiAlias = true
			// 빛나는 효과(Glow) 추가
			maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.SOLID)
		}
	}
	override fun draw(canvas: Canvas) {
		super.draw(canvas)

		if (results == null || results?.landmarks.isNullOrEmpty()) {
			return
		}
		val scaledImageWidth = imageWidth * scaleFactor
		val scaledImageHeight = imageHeight * scaleFactor
		val offsetX = (width - scaledImageWidth) / 2f
		val offsetY = (height - scaledImageHeight) / 2f
		results?.let { faceLandmarkerResult ->
			drawFaceLandmarks(canvas, faceLandmarkerResult.landmarks, offsetX, offsetY) // point3dResult,
		}

		drawColorTrack(
			canvas,
			outLinePaint,
			trajectory1Paint,
		)

		if (isScanning) {
			drawScanEffect(canvas, scaledImageWidth, scaledImageHeight, offsetX, offsetY)
		}
	}
//    val faceOutLineIndexes = listOf(
//        151, 108, 69, 104, 68, 71, 139, 34, 227, 137, 177, 215, 138,135, 169, 170, 140, 171,
//        175, 396, 369, 395, 394, 364, 367, 435, 401, 366, 447, 264, 368, 301, 298, 333, 299, 337, 151
//    )
fun drawScanEffect(
	canvas: Canvas,
	scaledImageWidth : Float,
	scaledImageHeight: Float,
	offsetX: Float,
	offsetY: Float
	) {
	if (!isScanning) return
	val faceLandmarks = results?.landmarks ?: return
	val centerX = width / 2f
	val now = System.currentTimeMillis()

	// ---- 1) 랜드마크 점 + trail ----
	faceLandmarks.forEachIndexed { index, landmark ->
		val px = landmark.x * scaledImageWidth + offsetX
		val py = landmark.y * scaledImageHeight + offsetY + OBGLView.yCorrectionOffset

		val distFromCenter = (px - centerX) / (scaledImageWidth / 2f)
		val curveOffset = (distFromCenter * distFromCenter) * 35f
		val waveEffectiveY = currentScanY - curveOffset
		// 대각선(NW->SE)으로 바꾸려면 위 두 줄 대신:
		// val diag = (px + py) / 1.4142f
		// val waveEffectiveY = currentScanY - (diag - py) // 스케일은 별도 조정 필요

		val deltaY = py - waveEffectiveY

		when {
			deltaY in -waveThickness..waveThickness -> {
				// 파동이 지금 지나가는 중 -> 가장 밝고 큼
				val ratio = 1f - (Math.abs(deltaY) / waveThickness)
				val dynamicRadius = dotRadius + (3.5f * ratio)
				pointPaint.color = Color.parseColor("#B0FF00")
				pointPaint.alpha = (80 + (175 * ratio)).toInt()
				canvas.drawCircle(px, py, dynamicRadius, pointPaint)
				lastLitTime[index] = now
			}
			deltaY > waveThickness -> {
				// 아직 파동이 안 닿은 영역 -> 은은한 기본 점
				pointPaint.color = Color.parseColor("#40FFFFFF")
				pointPaint.alpha = 30
				canvas.drawCircle(px, py, dotRadius, pointPaint)
			}
			else -> {
				// 파동이 이미 지나간 영역 -> trail로 서서히 페이드
				val litAt = lastLitTime[index]
				if (litAt != null) {
					val elapsed = now - litAt
					if (elapsed < trailDurationMs) {
						val fadeRatio = 1f - (elapsed / trailDurationMs.toFloat())
						trailPaint.color = Color.parseColor("#B0FF00")
						trailPaint.alpha = (fadeRatio * 150).toInt()
						canvas.drawCircle(px, py, dotRadius + 1.5f * fadeRatio, trailPaint)
					} else {
						pointPaint.color = Color.parseColor("#40FFFFFF")
						pointPaint.alpha = 30
						canvas.drawCircle(px, py, dotRadius, pointPaint)
					}
				} else {
					pointPaint.color = Color.parseColor("#40FFFFFF")
					pointPaint.alpha = 30
					canvas.drawCircle(px, py, dotRadius, pointPaint)
				}
			}
		}
	}
}
	private fun drawFaceLandmarks(
		canvas: Canvas,
		faceLandmarks: List<FaceLandmarkResult.FaceLandmark>,
//		point3dResults: List<Point3D?>?,
		offsetX: Float,
		offsetY: Float
	) {
//		if (faceLandmarks.isEmpty() ) return
		if (faceLandmarks.isNotEmpty()) {
			when (currentRunningMode) {
				RunningMode.LIVE_STREAM -> {
//					faceLandmarks.map { lm ->
//						val x = lm.x * imageWidth * scaleFactor + offsetX
//						val y = lm.y * imageHeight * scaleFactor + offsetY + OBGLView.yCorrectionOffset
//						canvas.drawCircle(x, y, 4f, dotPaint)
//					}
					// --------------------------# 10 - 1 - 152 양귓볼 - 코 #------------------------------
					val horizontalEarFlapIndexes = listOf(234, 1, 454)
					val horizontalEarFlapLinePath = Path()
					val horizontalPoints = horizontalEarFlapIndexes.map { landmarkIndex ->
						val landmark = faceLandmarks[landmarkIndex]
						val x = landmark.x * imageWidth * scaleFactor + offsetX
						val y = landmark.y * imageHeight * scaleFactor + offsetY + OBGLView.yCorrectionOffset
						PointF(x, y)
					}
					if (horizontalPoints.isNotEmpty()) {
						horizontalEarFlapLinePath.moveTo(horizontalPoints[0].x, horizontalPoints[0].y)

						for (i in 1 until horizontalPoints.size) {
							val prev = horizontalPoints[i - 1]
							val curr = horizontalPoints[i]

							// 이전 점과 현재 점의 중간 지점을 목표로 부드럽게 이어줌
							val midX = (prev.x + curr.x) / 2
							val midY = (prev.y + curr.y) / 2

							horizontalEarFlapLinePath.quadTo(prev.x, prev.y, midX, midY)
						}
						horizontalEarFlapLinePath.lineTo(horizontalPoints.last().x, horizontalPoints.last().y)
					}
					val horizonLinePaint = if (horizontalLineSuccess) standardPaint else notStandardPaint
					canvas.drawPath(horizontalEarFlapLinePath, horizonLinePaint)


					// --------------------------# 10 - 1 - 152 이마 - 턱 #------------------------------
					val verticalIndexes = listOf(10, 1, 152)
					val verticalPath = Path()
					val verticalPoints = verticalIndexes.map { landmarkIndex ->
						val landmark = faceLandmarks[landmarkIndex]
						val x = landmark.x * imageWidth * scaleFactor + offsetX
						val y = landmark.y * imageHeight * scaleFactor + offsetY + OBGLView.yCorrectionOffset
						PointF(x, y)
					}
					if (verticalPoints.isNotEmpty()) {
						verticalPath.moveTo(verticalPoints[0].x, verticalPoints[0].y)

						for (i in 1 until verticalPoints.size) {
							val prev = verticalPoints[i - 1]
							val curr = verticalPoints[i]

							// 이전 점과 현재 점의 중간 지점을 목표로 부드럽게 이어줌
							val midX = (prev.x + curr.x) / 2
							val midY = (prev.y + curr.y) / 2
							verticalPath.quadTo(prev.x, prev.y, midX, midY)
						}
						verticalPath.lineTo(verticalPoints.last().x, verticalPoints.last().y)
					}
					val vertiLinePaint = if (verticalLineSuccess) standardPaint else notStandardPaint
					canvas.drawPath(verticalPath, vertiLinePaint)
				}
				// --------------------------# video #------------------------------
				RunningMode.VIDEO -> {

					// 턱 관절 모양 잡기
					val chinOutLinePath = Path()
					val chinLineindexes = listOf(
						234,
						93,
						132,
						58,
						172,
						136,
						150,
						149,
						176,
						148,
						152,
						377,
						400,
						378,
						379,
						365,
						397,
						288,
						361,
						323,
						454,
						352,
						427,
						291,
						61,
						187,
						123,
						234
					)

					chinLineindexes.forEachIndexed { i, landmarkIndex ->
						val landmark = faceLandmarks[landmarkIndex]
						val x = landmark.x * scaleFactor + offsetX
						val y = landmark.y * scaleFactor + offsetY

						if (i == 0) {
							chinOutLinePath.moveTo(x, y) // 시작점
						} else {
							chinOutLinePath.lineTo(x, y) // 선 연결
						}
					}
					canvas.drawPath(chinOutLinePath, outLinePaint)
					for (i in 1 until chinTrajectoryPoints.size) {
						val p1 = chinTrajectoryPoints[i - 1] * scaleFactor + offsetX
						val p2 = chinTrajectoryPoints[i] * scaleFactor + offsetY
						canvas.drawLine(p1.x, p1.y, p2.x, p2.y, trajectory2Paint)
					}

					val forehead = faceLandmarks[10]
					val chin = faceLandmarks[152]
					val x0 = forehead.x * scaleFactor + offsetX
					val y0 = forehead.y * scaleFactor + offsetY
					val x1 = chin.x * scaleFactor + offsetX
					val y1 = chin.y * scaleFactor + offsetY

					drawExtendedLine(canvas, x0, y0, x1, y1, 100f, 0f, standardPaint)
				}

				RunningMode.IMAGE -> {}
			}
		} else {
			when (currentRunningMode) {
				RunningMode.LIVE_STREAM -> {
// --------------------------# 10 - 1 - 152 양귓볼 - 코 #------------------------------
					val horizontalEarFlapIndexes = listOf(234, 1, 454)
					val horizontalEarFlapLinePath = Path()
					val horizontalPoints = horizontalEarFlapIndexes.map { landmarkIndex ->
						val landmark = faceLandmarks[landmarkIndex]
						val x = landmark.x * imageWidth * scaleFactor + offsetX
						val y = landmark.y * imageHeight * scaleFactor + offsetY + OBGLView.yCorrectionOffset
						PointF(x, y)
					}
					if (horizontalPoints.isNotEmpty()) {
						horizontalEarFlapLinePath.moveTo(horizontalPoints[0].x, horizontalPoints[0].y)

						for (i in 1 until horizontalPoints.size) {
							val prev = horizontalPoints[i - 1]
							val curr = horizontalPoints[i]

							// 이전 점과 현재 점의 중간 지점을 목표로 부드럽게 이어줌
							val midX = (prev.x + curr.x) / 2
							val midY = (prev.y + curr.y) / 2

							horizontalEarFlapLinePath.quadTo(prev.x, prev.y, midX, midY)
						}
						horizontalEarFlapLinePath.lineTo(horizontalPoints.last().x, horizontalPoints.last().y)
					}
					val horizonLinePaint = if (horizontalLineSuccess) standardPaint else notStandardPaint
					canvas.drawPath(horizontalEarFlapLinePath, horizonLinePaint)


					// --------------------------# 10 - 1 - 152 이마 - 턱 #------------------------------
					val verticalIndexes = listOf(10, 1, 152)
					val verticalPath = Path()
					val verticalPoints = verticalIndexes.map { landmarkIndex ->
						val landmark = faceLandmarks[landmarkIndex]
						val x = landmark.x * imageWidth * scaleFactor + offsetX
						val y = landmark.y * imageHeight * scaleFactor + offsetY + OBGLView.yCorrectionOffset
						PointF(x, y)
					}
					if (verticalPoints.isNotEmpty()) {
						verticalPath.moveTo(verticalPoints[0].x, verticalPoints[0].y)

						for (i in 1 until verticalPoints.size) {
							val prev = verticalPoints[i - 1]
							val curr = verticalPoints[i]

							// 이전 점과 현재 점의 중간 지점을 목표로 부드럽게 이어줌
							val midX = (prev.x + curr.x) / 2
							val midY = (prev.y + curr.y) / 2
							verticalPath.quadTo(prev.x, prev.y, midX, midY)
						}
						verticalPath.lineTo(verticalPoints.last().x, verticalPoints.last().y)
					}
					val vertiLinePaint = if (verticalLineSuccess) standardPaint else notStandardPaint
					canvas.drawPath(verticalPath, vertiLinePaint)
				}

				RunningMode.VIDEO -> {}
				RunningMode.IMAGE -> {}
			}
		}
	}

	fun setResults(
		face3dResults: List<Point3D?>,
	) {
		point3dResult = face3dResults
		invalidate()
	}

	fun setResults(
		faceLandmarkerResults: FaceLandmarkResult,
		imageWidth: Int,
		imageHeight: Int,
		runningMode: RunningMode = RunningMode.IMAGE,
		isSDKInit: Boolean = false
	) {
		// overlayview자체를 그냥 크게 키워버림 (OBGLView 처럼)
		updateSize(imageWidth, imageHeight, isSDKInit)

		results = faceLandmarkerResults

		this.imageHeight = imageHeight
		this.imageWidth = imageWidth
		currentRunningMode = runningMode
		scaleFactor = when (runningMode) {
			RunningMode.IMAGE,
			RunningMode.VIDEO -> {
				min(width * 1f / imageWidth, height * 1f / imageHeight)
			}

			RunningMode.LIVE_STREAM -> {
				// PreviewView is in FILL_START mode. So we need to scale up the
				// landmarks to match with the size that the captured images will be
				// displayed.
				if (isSDKInit) {
					max(width * 1f / imageWidth, height * 1f / imageHeight)
				} else {
					min(width * 1f / imageWidth, height * 1f / imageHeight)
				}
			}
		}

		invalidate()
	}
	private var targets: List<HsvColorTracker.Target> = emptyList()
	private var targetOffsetY: Float = 0f
	// HSV 페이스 보우
	fun updateColorTracks(
		targets: List<HsvColorTracker.Target>,
		srcW: Int,
		srcH: Int,
		scaleType: ScaleType = ScaleType.FIT,
		rotateDeg: Int = 0,
		mirrorX: Boolean = false,
		offsetY: Float = 0f
	) {
		this.targets = targets
		this.srcW = srcW
		this.srcH = srcH
		this.scaleType = scaleType
		this.rotateDeg = rotateDeg
		this.mirrorX = mirrorX
		this.visible = targets.isNotEmpty()
		this.targetOffsetY = offsetY
		postInvalidateOnAnimation()
	}

	fun clearColorTrack() {
		this.visible = false
		this.targets = emptyList()
		postInvalidateOnAnimation()
	}

	fun drawColorTrack(
		canvas: Canvas,
		circlePaint: Paint,
		boxPaint: Paint,
	) {
		if (!visible || targets.isEmpty() || srcW <= 0 || srcH <= 0) return

		val dstW = width
		val dstH = height

		// 콘텐츠 배치(레터박스/크롭) 계산
		val (scale, offX, offY, effW, effH) = computePlacement(srcW, srcH, dstW, dstH, scaleType)

		val targetPoints = targets.map { t ->
			val shiftedY = t.center.y - targetOffsetY

			val (cx, cy) = rotateMirrorPoint(t.center.x, shiftedY, srcW, srcH, rotateDeg, mirrorX)
			val vx = (cx * scale + offX).toFloat()
			val vy = (cy * scale + offY).toFloat()
			PointF(vx, vy)
		}

		val sortedPoints = targetPoints
			.map { PointF(it.x, it.y - 40f) } // 💡 Y축 -40 오프셋 적용
			.sortedBy { it.x }                // 💡 X값 작은 순서대로 정렬

		val targetsLinePath = Path()
		if (sortedPoints.isNotEmpty()) {
			targetsLinePath.moveTo(sortedPoints[0].x, sortedPoints[0].y)

			for (i in 1 until sortedPoints.size) {
				val prev = sortedPoints[i - 1]
				val curr = sortedPoints[i]

				val midX = (prev.x + curr.x) / 2
				val midY = (prev.y + curr.y) / 2

				targetsLinePath.quadTo(prev.x, prev.y, midX, midY)
			}
			targetsLinePath.lineTo(sortedPoints.last().x, sortedPoints.last().y)
		}

// 3) 선 그리기
		val targetsLinePaint = standardPaint
		canvas.drawPath(targetsLinePath, targetsLinePaint)

// 4) 보정된 좌표 위치에 원(Circle) 그리기
		sortedPoints.forEach { pt ->
			canvas.drawCircle(pt.x, pt.y, 20f, circlePaint)
		}
	}

	enum class ScaleType { FIT, FILL }

	private var srcW: Int = 0
	private var srcH: Int = 0
	private var scaleType: ScaleType = ScaleType.FIT
	private var rotateDeg: Int = 0
	private var mirrorX: Boolean = false

	private var visible: Boolean = false


	companion object {
		private const val LANDMARK_STROKE_WIDTH = 2F
		private const val TAG = "Face Landmarker Overlay"
	}

	private fun computePlacement(
		sw: Int, sh: Int, dw: Int, dh: Int, type: ScaleType
	): Placement {
		val sFit = minOf(dw.toFloat() / sw, dh.toFloat() / sh)
		val sFill = maxOf(dw.toFloat() / sw, dh.toFloat() / sh)
		val s = if (type == ScaleType.FIT) sFit else sFill
		val cw = sw * s
		val ch = sh * s
		val ox = (dw - cw) * 0.5f
		val oy = (dh - ch) * 0.5f
		return Placement(s, ox, oy, cw, ch)
	}

	data class Placement(
		val scale: Float,
		val offX: Float,
		val offY: Float,
		val effW: Float,
		val effH: Float
	)

	private data class RotatedPoint(
		val x: Double,
		val y: Double,
		val w: Int,
		val h: Int
	)

	/** 회전(0/90/180/270) + 수평 미러 반영 */
	private fun rotateMirrorPoint(
		x: Double, y: Double, w: Int, h: Int, deg: Int, mirrorX: Boolean
	): Pair<Double, Double> {
		val normDeg = ((deg % 360) + 360) % 360
		// 회전
		val (rx, ry, rw, rh) = when (normDeg) {
			90 -> RotatedPoint(y, (w - 1 - x), h, w)
			180 -> RotatedPoint((w - 1 - x), (h - 1 - y), w, h)
			270 -> RotatedPoint((h - 1 - y), x, h, w)
			else -> RotatedPoint(x, y, w, h)
		}
		// 미러
		return if (mirrorX) Pair((rw - 1 - rx), ry) else Pair(rx, ry)
	}


	private var viewOffsetX = 0f
	private var viewOffsetY = 0f

	fun updateSize(frameW: Int, frameH: Int, centerCrop: Boolean) {
		val parent = parent as? ViewGroup ?: return

		if (parent.width <= 0 || parent.height <= 0 || frameH <= 0 || frameW <= 0) {
			return
		}

		// OBGLView와 동일한 계산
		val w1 = parent.width
		val h1 = parent.width * frameH / frameW

		val h2 = parent.height
		val w2 = parent.height * frameW / frameH

		val targetWidth: Int
		val targetHeight: Int

		if (!centerCrop) {
			// Fit Center
			if (h1 <= parent.height) {
				targetWidth = w1
				targetHeight = h1
			} else {
				targetWidth = w2
				targetHeight = h2
			}
		} else {
			// Center Crop
			if (h1 >= parent.height) {
				targetWidth = w1
				targetHeight = h1
			} else {
				targetWidth = w2
				targetHeight = h2
			}
		}

		// 레이아웃 파라미터 적용
		val params = layoutParams
		params.width = targetWidth
		params.height = targetHeight

		if (params is ViewGroup.MarginLayoutParams) {
			val dx = (parent.width - targetWidth) / 2
			val dy = (parent.height - targetHeight) / 2

			params.leftMargin = Math.abs(dx / 2)
			params.rightMargin = Math.abs(dx / 2)
			params.topMargin = dy

			// 오프셋 저장 (나중에 좌표 변환에 사용 가능)
			viewOffsetX = dx.toFloat()
			viewOffsetY = dy.toFloat()
		}

		layoutParams = params
	}

	// 스캔 이징 및 곡면 파동 제어
	private val waveInterpolator = PathInterpolatorCompat.create(0.2f, 0f, 0.2f, 1f) // Fast-Out Slow-In
	private val waveThickness = 120f // 스캔 영향 범위 (두께)

	// 레이저 라인 & 글로우 페인트
	private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeWidth = 3f
		color = Color.parseColor("#B0FF00") // accent / normal 네온 컬러
	}

	private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
	}

	fun startScanAnimation() {
		if (isScanning) return
		val faceLandmarks = results?.landmarks ?: return
		if (faceLandmarks.isEmpty()) return

		val scaledHeight = imageHeight * scaleFactor
		val offsetY = (height - scaledHeight) / 2f

		val yCoords = faceLandmarks.map { it.y * scaledHeight + offsetY }
		scanMinY = (yCoords.minOrNull() ?: 0f) - waveThickness
		scanMaxY = (yCoords.maxOrNull() ?: 0f) + waveThickness

		isScanning = true
		isStopRequested = false
		currentRepeatCount = 0

		scanAnimator?.cancel()
		scanAnimator = ValueAnimator.ofFloat(scanMinY, scanMaxY).apply {
			duration = 1400 // 1.4초로 살짝 템포 업
			interpolator = waveInterpolator
			repeatMode = ValueAnimator.RESTART
			repeatCount = ValueAnimator.INFINITE

			addUpdateListener { animator ->
				currentScanY = animator.animatedValue as Float
				invalidate()
			}

			addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationRepeat(animation: Animator) {
					currentRepeatCount++
					if (isStopRequested && currentRepeatCount >= 1) {
						animation.cancel()
					}
				}

				override fun onAnimationEnd(animation: Animator) {
					isScanning = false
					currentRepeatCount = 0
					isStopRequested = false
					invalidate()
					onScanFinishedListener?.invoke()
				}
			})
		}
		scanAnimator?.start()
	}

	fun stopScanAnimation() {
		if (currentRepeatCount >= 1) {
			// 이미 1번 이상 훑었다면 즉시 종료
			scanAnimator?.cancel()
		} else {
			// 아직 한 번도 다 못 훑었다면, 이번 사이클이 끝날 때 종료되도록 예약
			isStopRequested = true
		}
	}
}
