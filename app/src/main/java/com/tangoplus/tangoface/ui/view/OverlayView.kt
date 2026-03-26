package com.tangoplus.tangoface.ui.view

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.plus
import androidx.core.graphics.times
import androidx.core.graphics.toColorInt
import com.tangoplus.tangoface.domain.opencv.HsvColorTracker
import com.tangoplus.tangoface.domain.util.BitmapUtility.drawExtendedLine
import com.tangoplus.tangoface.domain.vision.face.FaceLandmarkResult
import com.tangoplus.tangoface.orbbec.vo.Point3D
import kotlin.math.max
import kotlin.math.min

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
			drawFaceLandmarks(canvas, faceLandmarkerResult.landmarks, point3dResult, offsetX, offsetY)
		}

		drawColorTrack(
			canvas,
			outLinePaint,
			trajectory1Paint,
		)
	}
//    val faceOutLineIndexes = listOf(
//        151, 108, 69, 104, 68, 71, 139, 34, 227, 137, 177, 215, 138,135, 169, 170, 140, 171,
//        175, 396, 369, 395, 394, 364, 367, 435, 401, 366, 447, 264, 368, 301, 298, 333, 299, 337, 151
//    )

	private fun drawFaceLandmarks(
		canvas: Canvas,
		faceLandmarks: List<FaceLandmarkResult.FaceLandmark>,
		point3dResults: List<Point3D?>?,
		offsetX: Float,
		offsetY: Float
	) {
		if (faceLandmarks.isEmpty() && point3dResults == null) return
		if (faceLandmarks.isNotEmpty() && point3dResults == null) {
			when (currentRunningMode) {
				RunningMode.LIVE_STREAM -> {
// --------------------------# 10 - 1 - 152 양귓볼 - 코 #------------------------------
					val horizontalEarFlapIndexes = listOf(234, 1, 454)
					val horizontalEarFlapLinePath = Path()
					val horizontalPoints = horizontalEarFlapIndexes.map { landmarkIndex ->
						val landmark = faceLandmarks[landmarkIndex]
						val x = landmark.x * imageWidth * scaleFactor + offsetX
						val y = landmark.y * imageHeight * scaleFactor + offsetY
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
						val y = landmark.y * imageHeight * scaleFactor + offsetY
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
						val y = landmark.y * imageHeight * scaleFactor + offsetY
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
						val y = landmark.y * imageHeight * scaleFactor + offsetY
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

	enum class ScaleType { FIT, FILL }

	private var srcW: Int = 0
	private var srcH: Int = 0
	private var scaleType: ScaleType = ScaleType.FIT
	private var rotateDeg: Int = 0
	private var mirrorX: Boolean = false

	// 다중 타깃(원본 좌표계 기준)
	private var targets: List<HsvColorTracker.Target> = emptyList()
	private var visible: Boolean = false

	fun updateColorTracks(
		targets: List<HsvColorTracker.Target>,
		srcW: Int,
		srcH: Int,
		scaleType: ScaleType = ScaleType.FIT,
		rotateDeg: Int = 0,
		mirrorX: Boolean = false
	) {
		this.targets = targets
		this.srcW = srcW
		this.srcH = srcH
		this.scaleType = scaleType
		this.rotateDeg = rotateDeg
		this.mirrorX = mirrorX
		this.visible = targets.isNotEmpty()
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

		targets.forEach { t ->
			// 1) 회전/미러 반영된 포인트/박스 얻기
			val (cx, cy) = rotateMirrorPoint(t.center.x, t.center.y, srcW, srcH, rotateDeg, mirrorX)
			val (bx0, by0) = rotateMirrorPoint(
				t.bbox.x.toDouble(),
				t.bbox.y.toDouble(),
				srcW,
				srcH,
				rotateDeg,
				mirrorX
			)
			val (bx1, by1) = rotateMirrorPoint(
				(t.bbox.x + t.bbox.width).toDouble(),
				(t.bbox.y + t.bbox.height).toDouble(),
				srcW, srcH, rotateDeg, mirrorX
			)

			// 2) (src) → (dst) 스케일 & 오프셋 적용
			val vx = (cx * scale + offX).toFloat()
			val vy = (cy * scale + offY).toFloat()
			val left = (minOf(bx0, bx1) * scale + offX).toFloat()
			val top = (minOf(by0, by1) * scale + offY).toFloat()
			val right = (maxOf(bx0, bx1) * scale + offX).toFloat()
			val bottom = (maxOf(by0, by1) * scale + offY).toFloat()

			// 3) 그리기
			canvas.drawCircle(vx, vy, 20f, circlePaint)
//            canvas.drawRect(RectF(left, top, right, bottom), boxPaint)
		}
	}

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


}
