package com.tangoplus.facebeautyexpert.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class AngleMeasureView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null
) : View(context, attrs) {

	private var p1 = PointF()
	private var p2 = PointF()
	private var isInitialized = false
	private var activePoint = 0

	// Paint 설정
	private val pointPaint = Paint().apply {
		color = Color.GREEN
		style = Paint.Style.FILL
		isAntiAlias = true
	}

	private val linePaint = Paint().apply {
		color = Color.GREEN
		strokeWidth = 5f
		isAntiAlias = true
	}

	private val textPaint = Paint().apply {
		color = Color.WHITE
		textSize = 24.sp
		textAlign = Paint.Align.CENTER
		isFakeBoldText = true
		setShadowLayer(5f, 0f, 0f, Color.BLACK)
	}

	/**
	 * ⭐ 단순하게 현재 뷰(화면)의 중앙에 선을 배치합니다.
	 * SSIV와 완전히 독립적으로 동작합니다.
	 */
	fun resetToScreenCenter() {
		val w = width.toFloat()
		val h = height.toFloat()

		if (w > 0 && h > 0) {
			// 화면 정중앙 기준으로 수평선 생성
			val centerX = w / 2f
			val centerY = h / 2f
			val lineLength = Math.min(w, h) * 0.3f // 화면 크기의 30%

			p1.set(centerX - lineLength / 2f, centerY)
			p2.set(centerX + lineLength / 2f, centerY)
			isInitialized = true
			invalidate()
		}
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		val x = event.x
		val y = event.y

		when (event.action) {
			MotionEvent.ACTION_DOWN -> {
				val d1 = dist(x, y, p1.x, p1.y)
				val d2 = dist(x, y, p2.x, p2.y)
				activePoint = when {
					d1 < 50.dp -> 1
					d2 < 50.dp -> 2
					else -> 0
				}
				return true // 터치 시작 시 항상 이벤트 소비
			}
			MotionEvent.ACTION_MOVE -> {
				if (activePoint == 1) p1.set(x, y)
				if (activePoint == 2) p2.set(x, y)
				invalidate()
				return true
			}
			MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
				activePoint = 0
				return true
			}
		}
		return true // 모든 터치 이벤트를 소비하여 SSIV로 전달 방지
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		if (!isInitialized) return

		// 선 그리기
		canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint)

		// 점 그리기
		canvas.drawCircle(p1.x, p1.y, 15.dp, pointPaint)
		canvas.drawCircle(p2.x, p2.y, 15.dp, pointPaint)

		// 각도 표시
		val angle = calculateAngle(p1, p2)
		val textX = (p1.x + p2.x) / 2
		val textY = (p1.y + p2.y) / 2 - 40.dp
		canvas.drawText(String.format("%.1f°", angle), textX, textY, textPaint)
	}

	private fun calculateAngle(p1: PointF, p2: PointF): Float {
		val deltaY = (p2.y - p1.y).toDouble()
		val deltaX = (p2.x - p1.x).toDouble()
		val result = Math.toDegrees(Math.atan2(deltaY, deltaX))
		return Math.abs(result).toFloat()
	}

	private fun dist(x1: Float, y1: Float, x2: Float, y2: Float) =
		Math.sqrt(Math.pow((x1 - x2).toDouble(), 2.0) + Math.pow((y1 - y2).toDouble(), 2.0)).toFloat()

	private val Int.dp: Float get() = this * context.resources.displayMetrics.density
	private val Int.sp: Float get() = this * context.resources.displayMetrics.scaledDensity
}