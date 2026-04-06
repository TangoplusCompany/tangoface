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
	private var activePoint: Int = 0 // 1이면 p1 드래그 중, 2면 p2 드래그 중

	private val pointPaint = Paint().apply {
		color = Color.parseColor("#00FF00") // 형광 초록
		style = Paint.Style.FILL
		isAntiAlias = true
	}

	private val linePaint = Paint().apply {
		color = Color.parseColor("#00FF00")
		strokeWidth = 4f
		style = Paint.Style.STROKE
		isAntiAlias = true
	}

	private val textPaint = Paint().apply {
		color = Color.WHITE
		textSize = 16.sp
		textAlign = Paint.Align.CENTER
		isFakeBoldText = true
		setShadowLayer(4f, 0f, 0f, Color.BLACK)
	}

	var onAngleChangeListener: ((Float) -> Unit)? = null

	// 뷰의 크기가 결정될 때 초기 위치를 중앙으로 잡음
	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		p1.set(w * 0.3f, h * 0.5f)
		p2.set(w * 0.7f, h * 0.5f)
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		val x = event.x
		val y = event.y

		when (event.action) {
			MotionEvent.ACTION_DOWN -> {
				// 어떤 점과 더 가까운지 판단 (터치 영역 여유 있게 40dp 정도)
				activePoint = when {
					dist(x, y, p1.x, p1.y) < 40.dp -> 1
					dist(x, y, p2.x, p2.y) < 40.dp -> 2
					else -> 0
				}
				return activePoint != 0
			}
			MotionEvent.ACTION_MOVE -> {
				if (activePoint == 1) p1.set(x, y)
				if (activePoint == 2) p2.set(x, y)

				if (activePoint != 0) {
					calculateAngle()
					invalidate()
				}
			}
			MotionEvent.ACTION_UP -> activePoint = 0
		}
		return true
	}

	private fun calculateAngle() {
		val angle = Math.toDegrees(Math.atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())).toFloat()
		// 0~360도 혹은 -180~180도 등 원하는 포맷으로 정규화 가능
		val normalizedAngle = Math.abs(angle)
		onAngleChangeListener?.invoke(normalizedAngle)
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		// 1. 선 그리기
		canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint)

		// 2. 점 그리기
		canvas.drawCircle(p1.x, p1.y, 6.dp, pointPaint)
		canvas.drawCircle(p2.x, p2.y, 6.dp, pointPaint)

		// 3. 각도 텍스트 그리기 (두 점의 중앙 상단)
		val midX = (p1.x + p2.x) / 2
		val midY = (p1.y + p2.y) / 2
		val angle = Math.toDegrees(Math.atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())).let {
			String.format("%.1f°", Math.abs(it))
		}
		canvas.drawText(angle, midX, midY - 20.dp, textPaint)
	}

	private fun dist(x1: Float, y1: Float, x2: Float, y2: Float) =
		Math.sqrt(Math.pow((x1 - x2).toDouble(), 2.0) + Math.pow((y1 - y2).toDouble(), 2.0)).toFloat()

	private val Int.dp: Float get() = this * context.resources.displayMetrics.density
	private val Int.sp: Float get() = this * context.resources.displayMetrics.scaledDensity
}