package com.tangoplus.facebeautyexpert.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.tangoplus.facebeautyexpert.domain.util.BitmapUtility
import kotlin.math.abs

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
	private var padding = 8
	private var dataTextSize = 12
	fun setTabletUI() {
		padding = 8
		dataTextSize = 14
	}
	fun setMobileUI() {
		padding = 4
		dataTextSize = 12
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
		gridRect: RectF,
		boxW: Float,  // 추가
		boxH: Float   // 추가
	): PointF {
		val viewW = width.toFloat()
		val viewH = height.toFloat()
		if (viewW <= 0 || viewH <= 0 || gridRect.isEmpty) return PointF(0f, 0f)

		val colCount = 5
		val rowCount = 7
		val cellW = gridRect.width() / colCount
		val cellH = gridRect.height() / rowCount

		val col = gridIndex % colCount
		val row = gridIndex / colCount

		// 1. 기본 그리드 고향 위치 계산
		val gridX = gridRect.left + (col * cellW) + (cellW / 2f)
		val gridY = gridRect.top + (row * cellH) + (cellH / 2f)

		// 2. 화면 경계 마진 (박스가 테두리에 딱 붙지 않게 여유를 줌)
		val safeMargin = padding.dp
		val minX = boxW / 2f + safeMargin
		val maxX = viewW - (boxW / 2f) - safeMargin
		val minY = boxH / 2f + safeMargin
		val maxY = viewH - (boxH / 2f) - safeMargin

		// 3. 앵커가 화면 안에 있을 때도 그리드 위치 자체가 화면 밖이면 화면 안으로 밀어넣기
		if (anchor != null && anchor.x in 0f..viewW && anchor.y in 0f..viewH) {
			return PointF(gridX.coerceIn(minX, maxX), gridY.coerceIn(minY, maxY))
		}

		// 4. 레이더 추적 및 테두리 부착 로직
		val tx = anchor?.x ?: gridX
		val ty = anchor?.y ?: gridY
		val dx = tx - gridX
		val dy = ty - gridY

		val distToL = gridX - minX
		val distToR = maxX - gridX
		val distToT = gridY - minY
		val distToB = maxY - gridY

		val scaleX = if (dx < 0) abs(distToL / dx) else if (dx > 0) abs(distToR / dx) else Float.MAX_VALUE
		val scaleY = if (dy < 0) abs(distToT / dy) else if (dy > 0) abs(distToB / dy) else Float.MAX_VALUE

		val scale = Math.min(scaleX, scaleY).coerceAtMost(1.0f)

		// 최종 좌표도 다시 한번 안전하게 고정
		val finalX = (gridX + dx * scale).coerceIn(minX, maxX)
		val finalY = (gridY + dy * scale).coerceIn(minY, maxY)

		return PointF(finalX, finalY)
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
		val expansionFactor = 2f
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
			val screenAnchor = iv.sourceToViewCoord(box.anchorX, box.anchorY)
			val isCollapsed = collapsedIndices.contains(box.targetGridIndex)

			// 박스 크기 미리 계산
			val (boxW, boxH) = getBoxDimensions(box.info, isCollapsed)

			// 수정된 함수 호출 (크기 정보 전달)
			val target = calculateFluidGridPosition(
				box.targetGridIndex,
				screenAnchor,
				gridRect,
				boxW,
				boxH
			)

			val animPos = animatedPositions.getOrPut(box.targetGridIndex) {
				AnimatedPosition(target.x, target.y, target.x, target.y)
			}

			animPos.targetX = target.x
			animPos.targetY = target.y

			// Lerp (부드러운 추적 애니메이션)
			val lerp = 0.2f
			if (abs(animPos.currentX - animPos.targetX) > 1f || abs(animPos.currentY - animPos.targetY) > 1f) {
				animPos.currentX += (animPos.targetX - animPos.currentX) * lerp
				animPos.currentY += (animPos.targetY - animPos.currentY) * lerp
				needInvalidate = true
			}

			drawTextBox(canvas, PointF(animPos.currentX, animPos.currentY), box.info, box.targetGridIndex)
		}

		if (needInvalidate) invalidate()
	}
	private val collapsedIndices = mutableSetOf<Int>() // 접힌 박스들의 index 저장
	private val eyeIcon: Drawable? by lazy {
		ContextCompat.getDrawable(context, android.R.drawable.ic_menu_view) // 또는 프로젝트의 R.drawable.ic_eye
	}

	private fun drawTextBox(canvas: Canvas, pos: PointF, info: BitmapUtility.TextDrawInfo, gridIndex: Int) {
		val isCollapsed = collapsedIndices.contains(gridIndex)
		val p = 10.dp

		if (isCollapsed) {
			// --- 1. 접힌 상태: 작은 눈 아이콘 그리기 ---
			val size = 32.dp
			val rect = RectF(pos.x - size/2, pos.y - size/2, pos.x + size/2, pos.y + size/2)

			bgPaint.color = Color.parseColor("#CC000000") // 좀 더 진한 배경
			canvas.drawOval(rect, bgPaint) // 동그랗게 표현 (또는 drawRoundRect)

			eyeIcon?.let {
				val iconMargin = 6.dp.toInt()
				it.setBounds(
					(rect.left + iconMargin).toInt(),
					(rect.top + iconMargin).toInt(),
					(rect.right - iconMargin).toInt(),
					(rect.bottom - iconMargin).toInt()
				)
				it.setTint(Color.WHITE)
				it.draw(canvas)
			}
		} else {
			// --- 2. 펼쳐진 상태: 기존 텍스트 박스 로직 ---
			val hasTitle = info.title.isNotEmpty() // 타이틀 존재 여부 체크

			val titlePaint = Paint(textPaint).apply { color = "#FF0055".toColorInt(); textSize = (dataTextSize +2).dp; isFakeBoldText = true }
			val itemPaint = Paint(textPaint).apply { color = Color.WHITE; textSize = (dataTextSize).dp }

			// 1. 가로 길이 계산: 타이틀이 있을 때만 가로폭 측정에 포함
			var maxW = if (hasTitle) titlePaint.measureText(info.title) else 0f
			info.items.forEach { maxW = maxOf(maxW, itemPaint.measureText("${it.label} ${it.value}")) }

			val boxW = maxW + p * 2
			val lineH = 18.dp

			// 2. 세로 길이 계산: 타이틀이 없으면 titlePaint.textSize와 간격을 계산에서 제외
			val titleSectionH = if (hasTitle) titlePaint.textSize else 0f
			val boxH = p * 2 + titleSectionH + (info.items.size * lineH)

			val rect = RectF(pos.x - boxW/2, pos.y - boxH/2, pos.x + boxW/2, pos.y + boxH/2)

			bgPaint.color = "#7F000000".toColorInt()
			canvas.drawRoundRect(rect, 6.dp, 6.dp, bgPaint)

			// 3. 그리기 로직
			var curY = rect.top + p

			if (hasTitle) {
				curY -= titlePaint.fontMetrics.ascent
				canvas.drawText(info.title, rect.left + p, curY, titlePaint)
				// 타이틀을 그린 후에는 다음 항목과의 간격을 위해 현재 위치 보정 (ascent만큼 뺀 것을 되돌림)
				curY += titlePaint.fontMetrics.descent
			} else {
				// 타이틀이 없으면 첫 번째 아이템이 위에서부터 적절히 배치되도록 보정
				curY -= itemPaint.fontMetrics.ascent
			}

			info.items.forEachIndexed { index, it ->
				// 타이틀이 없고 첫 번째 아이템인 경우 이미 위치를 잡았으므로 lineH를 더하지 않음
				if (hasTitle || index > 0) curY += lineH
				canvas.drawText("${it.label} ${it.value}", rect.left + p, curY, itemPaint)
			}
		}
	}

	private val Int.dp: Float get() = this * context.resources.displayMetrics.density
	private val Int.sp: Float get() = this * context.resources.displayMetrics.scaledDensity

	override fun onTouchEvent(event: MotionEvent): Boolean {
		val x = event.x
		val y = event.y

		when (event.action) {
			MotionEvent.ACTION_DOWN -> {
				// 1. 터치 시작 시점에 박스(아이콘)를 눌렀는지 확인
				val hitBoxIndex = findHitBoxIndex(x, y)
				return if (hitBoxIndex != -1) {
					// 박스를 눌렀다면 이 터치 시퀀스를 내가 소유함
					true
				} else {
					// 박스가 아니면 false를 리턴해서 아래 SSIV가 터치를 가져가게 함
					false
				}
			}
			MotionEvent.ACTION_UP -> {
				// 2. 손을 뗐을 때 해당 위치에 박스가 있다면 토글
				val hitBoxIndex = findHitBoxIndex(x, y)
				if (hitBoxIndex != -1) {
					toggleBox(hitBoxIndex)
					return true
				}
			}
		}
		return super.onTouchEvent(event)
	}

	/**
	 * 특정 좌표(x, y)에 박스나 아이콘이 있는지 확인하고 index를 반환
	 */
	private fun findHitBoxIndex(x: Float, y: Float): Int {
		textBoxes.forEach { box ->
			val pos = animatedPositions[box.targetGridIndex] ?: return@forEach
			val isCollapsed = collapsedIndices.contains(box.targetGridIndex)

			val touchRect = if (isCollapsed) {
				// 접힌 아이콘 영역 (클릭하기 편하게 조금 넓게 48dp 정도로 설정 추천)
				val size = 48.dp
				RectF(pos.currentX - size/2, pos.currentY - size/2, pos.currentX + size/2, pos.currentY + size/2)
			} else {
				// 펼쳐진 텍스트 박스 영역 (기존 계산 로직과 동일하게)
				val titlePaint = Paint().apply { textSize = 16.sp; isFakeBoldText = true }
				val itemPaint = Paint().apply { textSize = 14.sp }
				val p = 10.dp
				val lineH = 18.dp

				var maxW = titlePaint.measureText(box.info.title)
				box.info.items.forEach { maxW = maxOf(maxW, itemPaint.measureText("${it.label} ${it.value}")) }

				val boxW = maxW + p * 2
				val boxH = p * 2 + titlePaint.textSize + (box.info.items.size * lineH)
				RectF(pos.currentX - boxW/2, pos.currentY - boxH/2, pos.currentX + boxW/2, pos.currentY + boxH/2)
			}

			if (touchRect.contains(x, y)) return box.targetGridIndex
		}
		return -1
	}

	private fun toggleBox(index: Int) {
		if (collapsedIndices.contains(index)) {
			collapsedIndices.remove(index)
		} else {
			collapsedIndices.add(index)
		}
		invalidate() // 다시 그리기
	}
	private fun getBoxDimensions(info: BitmapUtility.TextDrawInfo, isCollapsed: Boolean): Pair<Float, Float> {
		val p = 10.dp
		if (isCollapsed) return Pair(32.dp, 32.dp)

		val titlePaint = Paint().apply { textSize = 16.sp; isFakeBoldText = true }
		val itemPaint = Paint().apply { textSize = 14.sp }

		val hasTitle = info.title.isNotEmpty()
		var maxW = if (hasTitle) titlePaint.measureText(info.title) else 0f
		info.items.forEach { maxW = maxOf(maxW, itemPaint.measureText("${it.label} ${it.value}")) }

		val lineH = 18.dp
		val titleSectionH = if (hasTitle) titlePaint.textSize else 0f

		val boxW = maxW + p * 2
		val boxH = p * 2 + titleSectionH + (info.items.size * lineH)

		return Pair(boxW, boxH)
	}
}