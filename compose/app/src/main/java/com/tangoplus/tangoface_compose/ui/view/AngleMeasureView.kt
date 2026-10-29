package com.tangoplus.tangoface_compose.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.core.graphics.toColorInt

class AngleMeasureView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null
) : View(context, attrs) {

	// 선 데이터를 담을 클래스
	data class LineData(val p1: PointF, val p2: PointF)

	private val lines = mutableListOf<LineData>()
	private var selectedIndex = -1 // 현재 편집 중인 선 번호
	private var activePoint = 0 // 1이면 p1, 2이면 p2 이동 중
	private var lastX = 0f
	private var lastY = 0f

	// UI 레이아웃 상수
	private val maxLines = 5
	private val buttonSize = 40.dp
	private val buttonMargin = 8.dp
	private val panelPadding = 12.dp

	// Paint 설정
	private val activePaint = Paint().apply { color = "#B0FF00".toColorInt(); strokeWidth = 5f; isAntiAlias = true }
	private val pointPaint = Paint().apply { color = "#B0FF00".toColorInt(); style = Paint.Style.FILL; isAntiAlias = true }
	private val textPaint = Paint().apply {
		color = Color.WHITE; textSize = 14.sp; textAlign = Paint.Align.CENTER; isFakeBoldText = true
	}
	private val bgPaint = Paint().apply { color = "#AA000000".toColorInt(); isAntiAlias = true }

	init {
		// 초기 실행 시 첫 번째 선 생성
		post { if (lines.isEmpty()) addNewLine() }
	}

	private fun addNewLine() {
		if (lines.size >= maxLines) return
		val w = width.toFloat()
		val h = height.toFloat()
		val centerX = w / 2f
		val centerY = h / 2f
		val offset = lines.size * 48.dp // 겹치지 않게 약간씩 어긋나게 생성

		val newLine = LineData(
			PointF(centerX - 64.dp + offset, centerY + offset),
			PointF(centerX + 64.dp + offset, centerY + offset)
		)
		lines.add(newLine)
		selectedIndex = lines.size - 1
		invalidate()
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		val x = event.x
		val y = event.y

		when (event.action) {
			MotionEvent.ACTION_DOWN -> {

				lastX = x
				lastY = y

				if (checkPanelClick(x, y)) return true

				// 터치한 위치에서 가장 가까운 선/점을 찾아 즉시 이동 준비
				selectedIndex = -1
				activePoint = 0

				// 나중에 생성된(위에 그려진) 선부터 우선 터치되도록 역순 검사
				for (i in lines.indices.reversed()) {
					val line = lines[i]
					if (dist(x, y, line.p1.x, line.p1.y) < 50.dp) {
						activePoint = 1; selectedIndex = i; break
					}
					if (dist(x, y, line.p2.x, line.p2.y) < 50.dp) {
						activePoint = 2; selectedIndex = i; break
					}
					if (distToSegment(x, y, line.p1, line.p2) < 50.dp) {
						activePoint = 3; selectedIndex = i; break
					}
				}
				if (selectedIndex != -1) {
					parent.requestDisallowInterceptTouchEvent(true)
					return true
				}
			}
			MotionEvent.ACTION_MOVE -> {
				if (selectedIndex != -1 && activePoint != 0) {
					val line = lines[selectedIndex]
					when (activePoint) {
						1 -> line.p1.set(x, y)
						2 -> line.p2.set(x, y)
						3 -> {
							val dx = x - lastX
							val dy = y - lastY
							line.p1.offset(dx, dy)
							line.p2.offset(dx, dy)
						}
					}
					lastX = x
					lastY = y
					invalidate()
					return true
				}
			}
			MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
				parent.requestDisallowInterceptTouchEvent(false)
				activePoint = 0
				selectedIndex = -1 // 터치 종료 시 선택 해제
			}
		}
		return true
	}

	private fun checkPanelClick(x: Float, y: Float): Boolean {
		val panelTop = height - panelPadding * 2 - buttonSize
		if (y < panelTop) return false

		// 1. 숫자 버튼 체크 (클릭 시 삭제)
		for (i in 0 until lines.size) {
			val btnLeft = panelPadding + i * (buttonSize + buttonMargin)
			if (x in btnLeft..(btnLeft + buttonSize)) {
				lines.removeAt(i)
				selectedIndex = if (lines.isEmpty()) -1 else lines.size - 1
				invalidate()
				return true
			}
		}

		// 2. 추가 버튼 (+) 체크 (마지막 위치)
		val addBtnLeft = panelPadding + lines.size * (buttonSize + buttonMargin)
		if (x in addBtnLeft..(addBtnLeft + buttonSize)) {
			addNewLine()
			return true
		}
		return false
	}

	private fun drawControlPanel(canvas: Canvas) {
		// + 버튼 표시 여부에 따라 패널 너비 계산
		val totalButtons = if (lines.size < maxLines) lines.size + 1 else lines.size
		val panelW = panelPadding * 2 + totalButtons * (buttonSize + buttonMargin)
		val panelH = buttonSize + panelPadding * 2
		val rect = RectF(0f, height - panelH, panelW, height.toFloat())

		canvas.drawRoundRect(rect, 12.dp, 12.dp, bgPaint)

		// 선 번호 버튼들
		for (i in 0 until lines.size) {
			val btnLeft = panelPadding + i * (buttonSize + buttonMargin)
			drawButton(canvas, btnLeft, height - panelPadding - buttonSize, (i + 1).toString(), false)
		}

		// 추가 버튼 (+) - 5개 미만일 때만 그리기
		if (lines.size < maxLines) {
			val addBtnLeft = panelPadding + lines.size * (buttonSize + buttonMargin)
			drawButton(canvas, addBtnLeft, height - panelPadding - buttonSize, "+", false)
		}
	}
	val dashedPaint = Paint(activePaint).apply {
		pathEffect = DashPathEffect(floatArrayOf(2f, 4f), 0f)
	}
	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		lines.forEachIndexed { index, line ->
			// 항상 활성화 스타일 사용
			canvas.drawLine(line.p1.x, line.p1.y, line.p2.x, line.p2.y, dashedPaint)
			canvas.drawCircle(line.p1.x, line.p1.y, 10.dp, pointPaint)
			canvas.drawCircle(line.p2.x, line.p2.y, 10.dp, pointPaint)

			// p2 점 정중앙에 숫자 넣기
			textPaint.color = Color.WHITE
			val textOffset = (textPaint.descent() + textPaint.ascent()) / 2
			canvas.drawText((index + 1).toString(), line.p1.x, line.p1.y - textOffset, textPaint)

			// 각도 텍스트 (선 중앙)
			val angle = calculateAngle(line.p1, line.p2)
			val textX = (line.p1.x + line.p2.x) / 2
			val textY = (line.p1.y + line.p2.y) / 2 - 15.dp
			canvas.drawText(String.format("%.1f°", angle), textX, textY, textPaint)
		}
		drawControlPanel(canvas)
	}

	private fun drawButton(canvas: Canvas, x: Float, y: Float, text: String, isSelected: Boolean) {
		val rect = RectF(x, y, x + buttonSize, y + buttonSize)
		bgPaint.color = if (isSelected) Color.GREEN else Color.parseColor("#44FFFFFF")
		canvas.drawRoundRect(rect, 8.dp, 8.dp, bgPaint)

		textPaint.color = if (isSelected) Color.BLACK else Color.WHITE
		val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
		canvas.drawText(text, rect.centerX(), textY, textPaint)

		// 다시 원래 배경색으로 복구
		bgPaint.color = Color.parseColor("#AA000000")
	}

	fun resetSelectedLine() {
		if (selectedIndex == -1 || lines.isEmpty()) return

		val w = width.toFloat()
		val h = height.toFloat()
		if (w > 0 && h > 0) {
			val centerX = w / 2f
			val centerY = h / 2f
			val lineLength = Math.min(w, h) * 0.3f

			lines[selectedIndex].p1.set(centerX - lineLength / 2f, centerY)
			lines[selectedIndex].p2.set(centerX + lineLength / 2f, centerY)
			invalidate()
		}
	}

	// --- 유틸리티 함수들 ---
	private fun calculateAngle(p1: PointF, p2: PointF): Float {
		val deltaY = (p2.y - p1.y).toDouble()
		val deltaX = (p2.x - p1.x).toDouble()
		return abs(Math.toDegrees(atan2(deltaY, deltaX))).toFloat()
	}

	private fun distToSegment(px: Float, py: Float, p1: PointF, p2: PointF): Float {
		val l2 = (p1.x - p2.x).toDouble().pow(2.0) + (p1.y - p2.y).toDouble().pow(2.0)
		if (l2 == 0.0) return dist(px, py, p1.x, p1.y) // p1과 p2가 같은 점일 때

		// 점을 선분 위에 투영
		var t = ((px - p1.x) * (p2.x - p1.x) + (py - p1.y) * (p2.y - p1.y)) / l2
		t = 0.0.coerceAtLeast(1.0.coerceAtMost(t)) // 선분 밖으로 벗어나지 않게 0~1로 제한

		val projX = (p1.x + t * (p2.x - p1.x)).toFloat()
		val projY = (p1.y + t * (p2.y - p1.y)).toFloat()

		return dist(px, py, projX, projY)
	}

	private fun dist(x1: Float, y1: Float, x2: Float, y2: Float) =
		sqrt((x1 - x2).toDouble().pow(2.0) + (y1 - y2).toDouble().pow(2.0)).toFloat()

	private val Int.dp: Float get() = this * context.resources.displayMetrics.density
	private val Int.sp: Float get() = this * context.resources.displayMetrics.scaledDensity
}