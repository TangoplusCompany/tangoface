package com.tangoplus.tangoface.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.graphics.toColorInt

class TrajectoryView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

	private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = "#FF0055".toColorInt()
		strokeWidth = 6f
		isAntiAlias = true
		style = Paint.Style.STROKE
//        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.SOLID)
	}
	private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = "#00FFCC".toColorInt()
		style = Paint.Style.FILL_AND_STROKE
	}
	private var preCalculatedBreakpoints: List<Int> = listOf()
	private var fullTrajectory: MutableList<PointF> = mutableListOf()
	private var currentIndex = 0
	private var boundingBox = RectF()
	private var sharedMaxBoundingBox = RectF()
	private var margin = 30f
	private var animator: ValueAnimator? = null
	private var isPlaying = false
	fun setBreakPoints(newBreakPoints: List<Int>) {
		preCalculatedBreakpoints = newBreakPoints
	}

	fun setTrajectory(points: List<PointF>, maxBox: RectF) {
		fullTrajectory = points.toMutableList()
		currentIndex = 0
		sharedMaxBoundingBox = maxBox
		calculateGlobalBoundingBox()
		invalidate()
	}

	fun restartDrawing() {
		stopAnimation()
		currentIndex = 0
		startAnimation()
	}

	fun pause() {
		isPlaying = false
		animator?.pause()
	}

	fun resume() {
		isPlaying = true
		animator?.resume()
	}

	fun isAnimating(): Boolean = isPlaying

	private fun startAnimation() {
		if (fullTrajectory.size < 2) return
		isPlaying = true

		animator = ValueAnimator.ofInt(0, fullTrajectory.size - 1).apply {
			duration = 2000L
			interpolator = LinearInterpolator()
			addUpdateListener { valueAnimator ->
				currentIndex = valueAnimator.animatedValue as Int
				invalidate()
			}
			doOnEnd { isPlaying = false }
			start()
		}
	}

	private fun stopAnimation() {
		animator?.cancel()
		isPlaying = false
	}

	private fun calculateGlobalBoundingBox() {
		if (fullTrajectory.isEmpty()) return
		val minX = fullTrajectory.minOf { it.x }
		val maxX = fullTrajectory.maxOf { it.x }
		val minY = fullTrajectory.minOf { it.y }
		val maxY = fullTrajectory.maxOf { it.y }
		boundingBox.set(minX, minY, maxX, maxY)
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		if (fullTrajectory.size < 2 || currentIndex < 1) return

		val visiblePoints = fullTrajectory.take(currentIndex + 1)
		val scaledPoints = scalePoints(visiblePoints, sharedMaxBoundingBox)

		// 브레이크포인트 그리기 (미리 계산된 것만 사용)
		for (breakpointIndex in preCalculatedBreakpoints) {
			// 현재 보이는 범위 내에 있고, scaledPoints 범위 내에 있는지 확인
			if (breakpointIndex <= currentIndex && breakpointIndex < scaledPoints.size) {
				canvas.drawCircle(
					scaledPoints[breakpointIndex].x,
					scaledPoints[breakpointIndex].y,
					12f,
					circlePaint
				)
			}
		}

		// 궤적 선 그리기
		for (i in 0 until scaledPoints.size - 1) {
			val p1 = scaledPoints[i]
			val p2 = scaledPoints[i + 1]
			canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
		}
	}

	private fun scalePoints(points: List<PointF>, maxBoundingBox: RectF): List<PointF> {
		val availableWidth = width - 2 * margin
		val availableHeight = height - 2 * margin

		val boxWidth = maxBoundingBox.width()
		val boxHeight = maxBoundingBox.height()

		if (boxWidth == 0f || boxHeight == 0f) return emptyList()

		val scale = minOf(availableWidth / boxWidth, availableHeight / boxHeight)

		val offsetX = (width - boxWidth * scale) / 2
		val offsetY = (height - boxHeight * scale) / 2

		return points.map { point ->
			val x = offsetX + (point.x - maxBoundingBox.left) * scale
			val y = offsetY + (point.y - maxBoundingBox.top) * scale
			PointF(x, y)
		}
	}

	// Optional: 자동으로 시작하려면 이 함수 호출
	fun play() {
		stopAnimation()
		currentIndex = 0
		startAnimation()
	}

}