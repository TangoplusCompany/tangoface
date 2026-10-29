package com.tangoplus.tangoface.ui.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class PanoramaGuideView @JvmOverloads constructor(
	context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

	private val totalFrames = 15
	private val minAngle = -60f
	private val maxAngle = 60f

	val targetAngles = List(totalFrames) { i ->
		minAngle + i * ((maxAngle - minAngle) / (totalFrames - 1))
	}
	val capturedIndices = BooleanArray(totalFrames)

	var currentYaw: Float = -60f
		set(value) {
			field = value.coerceIn(minAngle, maxAngle)
			invalidate()
		}

	// 상하 기울기(Pitch) 오차 (0도가 수평, ±10도 범위 반영)
	var currentPitchOffset: Float = 0f
		set(value) {
			field = value.coerceIn(-15f, 15f)
			invalidate()
		}

	private val bgPaint = Paint().apply {
		color = Color.parseColor("#80000000") // 반투명 검정
		style = Paint.Style.FILL
	}
	private val centerLinePaint = Paint().apply {
		color = Color.parseColor("#4DD0E1") // 평행 기준선 (시안)
		strokeWidth = 4f
	}
	private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		style = Paint.Style.FILL
	}
	private val capturedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.parseColor("#00E676") // 캡처 완료 점
		style = Paint.Style.FILL
	}
	private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		style = Paint.Style.FILL
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val w = width.toFloat()
		val h = height.toFloat()
		val centerY = h / 2f
		val paddingX = 40f

		// 1. 배경 박스 & 중앙 수평선
		canvas.drawRoundRect(0f, 0f, w, h, 16f, 16f, bgPaint)
		canvas.drawLine(paddingX, centerY, w - paddingX, centerY, centerLinePaint)

		// 2. 15개 촬영 포인트 (중앙선 위 점)
		targetAngles.forEachIndexed { index, angle ->
			val ratio = (angle - minAngle) / (maxAngle - minAngle)
			val px = paddingX + ratio * (w - 2 * paddingX)
			val paint = if (capturedIndices[index]) capturedPaint else pointPaint
			canvas.drawCircle(px, centerY, 8f, paint)
		}

		// 3. 이동 화살표 위치 계산 (X: 회전각, Y: 상하 기울기 오차)
		val currentRatio = (currentYaw - minAngle) / (maxAngle - minAngle)
		val arrowX = paddingX + currentRatio * (w - 2 * paddingX)
		val arrowY = centerY + (currentPitchOffset / 15f) * (h * 0.35f)

		drawArrow(canvas, arrowX, arrowY)
	}

	private fun drawArrow(canvas: Canvas, x: Float, y: Float) {
		val path = Path().apply {
			moveTo(x + 20f, y)       // 화살표 머리 끝
			lineTo(x - 10f, y - 16f) // 상단 날개
			lineTo(x - 5f, y - 6f)
			lineTo(x - 24f, y - 6f)  // 몸통 좌상
			lineTo(x - 24f, y + 6f)  // 몸통 좌하
			lineTo(x - 5f, y + 6f)
			lineTo(x - 10f, y + 16f) // 하단 날개
			close()
		}
		canvas.drawPath(path, arrowPaint)
	}

	fun markCaptured(index: Int) {
		if (index in 0 until totalFrames) {
			capturedIndices[index] = true
			invalidate()
		}
	}
}