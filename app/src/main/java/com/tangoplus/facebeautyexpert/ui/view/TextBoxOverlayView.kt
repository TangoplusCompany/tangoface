package com.tangoplus.facebeautyexpert.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.tangoplus.facebeautyexpert.domain.util.BitmapUtility

class TextBoxOverlayView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null
) : View(context, attrs) {

	private val textBoxes = mutableListOf<BitmapUtility.TextBoxData>()
	private val animatedPositions = mutableMapOf<Int, AnimatedPosition>()
	data class FaceRect(
		val left: Float,
		val top: Float,
		val right: Float,
		val bottom: Float
	)

	data class AnimatedPosition(
		var currentX: Float, var currentY: Float,
		var targetX: Float, var targetY: Float
	)

	private var imageView: SubsamplingScaleImageView? = null
	private val bgPaint = Paint().apply { isAntiAlias = true }
	private val textPaint = Paint().apply { isAntiAlias = true }

	// Fragment에서 설정해 줄 얼굴 영역 좌표
	private var faceRectInPixels: FaceRect? = null

	private var stateChangeCallback: (() -> Unit)? = null

	fun attachToImageView(imageView: SubsamplingScaleImageView, onStateChanged: (() -> Unit)? = null) {
		this.imageView = imageView
		this.stateChangeCallback = onStateChanged

		// SSIV 상태 변경 시 invalidate 호출
		imageView.setOnStateChangedListener(object : SubsamplingScaleImageView.OnStateChangedListener {
			override fun onScaleChanged(newScale: Float, origin: Int) {
				invalidate()
				stateChangeCallback?.invoke()
			}
			override fun onCenterChanged(newCenter: PointF?, origin: Int) {
				invalidate()
				stateChangeCallback?.invoke()
			}
		})
	}

	/**
	 * Fragment에서 계산한 얼굴 영역(finalBitmap 픽셀 기준)을 설정합니다.
	 */
	fun setFaceRect(rect: FaceRect?) {
		this.faceRectInPixels = rect
		invalidate()
	}

	fun setTextBoxes(boxes: List<BitmapUtility.TextBoxData>) {
		textBoxes.clear()
		textBoxes.addAll(boxes)
		// 리스트가 바뀌어도 animatedPositions를 유지하여 쫀득함을 유지
		invalidate()
	}

	// --- 핵심: 얼굴 영역 기반 유동 그리드 위치 계산 ---

	private fun calculateFluidGridPosition(
		gridIndex: Int,
		anchor: PointF?,
		gridRect: RectF // onDraw에서 계산한 화면 기준 확장된 얼굴 그리드 영역
	): PointF {
		val viewW = width.toFloat()
		val viewH = height.toFloat()
		if (viewW <= 0 || viewH <= 0 || gridRect.isEmpty) return PointF(0f, 0f)

		// 1. 확장된 얼굴 그리드 영역 내에서 5 * 7 좌표 계산
		val colCount = 5
		val rowCount = 7
		val cellW = gridRect.width() / colCount
		val cellH = gridRect.height() / rowCount

		val col = gridIndex % colCount
		val row = gridIndex / colCount

		// 박스의 원래 고향 (Home Position)
		val gridX = gridRect.left + (col * cellW) + (cellW / 2f)
		val gridY = gridRect.top + (row * cellH) + (cellH / 2f)

		// 2. 앵커가 화면 안에 있으면 자기 고향(그리드)에 가만히 있기
		if (anchor != null && anchor.x in 0f..viewW && anchor.y in 0f..viewH) {
			return PointF(gridX, gridY)
		}

		// 3. 레이더 추적 로직 (그리드 홈 기준 벡터 추적)
		// 앵커가 없으면(null) 일단 그리드 홈에 둠
		val tx = anchor?.x ?: gridX
		val ty = anchor?.y ?: gridY
		val dx = tx - gridX
		val dy = ty - gridY

		// 화면 테두리 여백 (박스 크기 절반 정도)
		val marginW = 85.dp
		val marginH = 45.dp

		// 그리드 위치에서 각 테두리까지의 거리 계산
		val distToL = gridX - marginW
		val distToR = viewW - gridX - marginW
		val distToT = gridY - marginH
		val distToB = viewH - gridY - marginH

		// 테두리에 부딪히는 비율(Scale) 계산
		val scaleX = if (dx < 0) Math.abs(distToL / dx) else if (dx > 0) Math.abs(distToR / dx) else Float.MAX_VALUE
		val scaleY = if (dy < 0) Math.abs(distToT / dy) else if (dy > 0) Math.abs(distToB / dy) else Float.MAX_VALUE

		// 가장 먼저 부딪히는 비율을 선택 (최대 1.0 coerce)
		val scale = Math.min(scaleX, scaleY).coerceAtMost(1.0f)

		return PointF(gridX + dx * scale, gridY + dy * scale)
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val iv = imageView ?: return
		if (width <= 0 || height <= 0 || textBoxes.isEmpty()) return

		// ⭐ 얼굴 영역 기반 유동 그리드 영역 계산 ⭐
		val facePixels = faceRectInPixels ?: return // 얼굴 영역 정보가 없으면 안 그림

		// 픽셀 기준 좌상단/우하단 좌표를 화면 좌표로 변환
		val tl = iv.sourceToViewCoord(facePixels.left, facePixels.top)
		val br = iv.sourceToViewCoord(facePixels.right, facePixels.bottom)

		if (tl == null || br == null) return

		// 화면 기준 얼굴 Bounding Box 너비/높이
		val faceW = br.x - tl.x
		val faceH = br.y - tl.y

		// 1.25~1.5배 확장 (여기에선 1.3배 적용 - 필요에 따라 조절)
		val expansionFactor = 1.3f
		val expW = faceW * expansionFactor
		val expH = faceH * expansionFactor

		// 확장된 그리드 영역 (가운데 정렬)
		val gridRect = RectF(
			tl.x - (expW - faceW) / 2f,
			tl.y - (expH - faceH) / 2f,
			br.x + (expW - faceW) / 2f,
			br.y + (expH - faceH) / 2f
		)

		var needInvalidate = false

		textBoxes.forEach { box ->
			// 앵커의 실제 화면 좌표
			val screenAnchor = iv.sourceToViewCoord(box.anchorX, box.anchorY)

			// ⭐ 유동 그리드 로직으로 목표 좌표 계산 ⭐
			val target = calculateFluidGridPosition(box.targetGridIndex, screenAnchor, gridRect)

			val animPos = animatedPositions.getOrPut(box.targetGridIndex) {
				AnimatedPosition(target.x, target.y, target.x, target.y)
			}

			animPos.targetX = target.x
			animPos.targetY = target.y

			// Lerp (부드러운 추적 애니메이션)
			val lerp = 0.2f
			if (Math.abs(animPos.currentX - animPos.targetX) > 1f || Math.abs(animPos.currentY - animPos.targetY) > 1f) {
				animPos.currentX += (animPos.targetX - animPos.currentX) * lerp
				animPos.currentY += (animPos.targetY - animPos.currentY) * lerp
				needInvalidate = true
			}

			drawTextBox(canvas, PointF(animPos.currentX, animPos.currentY), box.info)
		}

		if (needInvalidate) invalidate()
	}

	private fun drawTextBox(canvas: Canvas, pos: PointF, info: BitmapUtility.TextDrawInfo) {
		// 배경 박스 및 텍스트 그리기 (기존 로직 유지)
		val p = 10.dp
		val titlePaint = Paint(textPaint).apply { color = Color.WHITE; textSize = 13.sp; isFakeBoldText = true }
		val itemPaint = Paint(textPaint).apply { color = Color.WHITE; textSize = 11.sp }

		var maxW = titlePaint.measureText(info.title)
		info.items.forEach { maxW = maxOf(maxW, itemPaint.measureText("${it.label} ${it.value}")) }

		val boxW = maxW + p * 2
		val lineH = 18.dp
		val boxH = p * 2 + titlePaint.textSize + (info.items.size * lineH)

		val rect = RectF(pos.x - boxW/2, pos.y - boxH/2, pos.x + boxW/2, pos.y + boxH/2)

		bgPaint.color = Color.parseColor("#99000000") // 반투명 블랙 (ARGB 128)
		canvas.drawRoundRect(rect, 6.dp, 6.dp, bgPaint)

		var curY = rect.top + p - titlePaint.fontMetrics.ascent
		canvas.drawText(info.title, rect.left + p, curY, titlePaint)
		info.items.forEach { curY += lineH; canvas.drawText("${it.label} ${it.value}", rect.left + p, curY, itemPaint) }
	}

	private val Int.dp: Float get() = this * context.resources.displayMetrics.density
	private val Int.sp: Float get() = this * context.resources.displayMetrics.scaledDensity
}