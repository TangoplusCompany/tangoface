package com.tangoplus.tangoface.ui.view

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
import com.tangoplus.tangoface.domain.util.BitmapUtility
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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

	private var faceRectInPixels: FaceRect? = null
	private var stateChangeCallback: (() -> Unit)? = null

	private var padding = 8
	private var dataTextSize = 12

	private val collapsedIndices = mutableSetOf<Int>()
	private val eyeIcon: Drawable? by lazy {
		ContextCompat.getDrawable(context, android.R.drawable.ic_menu_view)
	}

	private val Int.dp: Float get() = this * context.resources.displayMetrics.density
	private val Int.sp: Float get() = this * context.resources.displayMetrics.scaledDensity

	fun attachToImageView(imageView: SubsamplingScaleImageView, onStateChanged: (() -> Unit)? = null) {
		this.imageView = imageView
		this.stateChangeCallback = onStateChanged

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

	fun setTabletUI() {
		padding = 8
		dataTextSize = 14
	}

	fun setMobileUI() {
		padding = 4
		dataTextSize = 12
	}

	fun setFaceRect(rect: FaceRect?) {
		this.faceRectInPixels = rect
		invalidate()
	}

	fun setTextBoxes(boxes: List<BitmapUtility.TextBoxData>) {
		textBoxes.clear()
		textBoxes.addAll(boxes)
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val iv = imageView ?: return
		if (width <= 0 || height <= 0 || textBoxes.isEmpty() || !iv.isReady) return

		val facePixels = faceRectInPixels ?: return
		val tl = iv.sourceToViewCoord(facePixels.left, facePixels.top) ?: return
		val br = iv.sourceToViewCoord(facePixels.right, facePixels.bottom) ?: return
		val faceRectScreen = RectF(tl.x, tl.y, br.x, br.y)

		// 1. 각 박스의 크기 및 기본 1차 타겟 위치 계산
		val calculatedTargets = mutableListOf<BoxTarget>()
		textBoxes.forEach { box ->
			val isCollapsed = collapsedIndices.contains(box.targetGridIndex)
			val (boxW, boxH) = getBoxDimensions(box.info, isCollapsed)

			// 원본 anchor 좌표가 0f인 비율 인디케이터인지 판별
			val isRatioIndicator = (box.anchorX == 0f && box.anchorY == 0f)

			val targetPos = if (isRatioIndicator) {
				// ⭐ 비율 전용 좌표 계산 (faceRectScreen 기준 직접 계산)
				calculateRatioPosition(
					gridIndex = box.targetGridIndex,
					faceRect = faceRectScreen,
					boxW = boxW,
					boxH = boxH
				)
			} else {
				// ⭐ 일반 랜드마크 추적 박스
				val screenAnchor = iv.sourceToViewCoord(box.anchorX, box.anchorY)
				calculateFluidPosition(
					anchor = screenAnchor,
					faceRect = faceRectScreen,
					boxW = boxW,
					boxH = boxH,

				)
			}

			calculatedTargets.add(
				BoxTarget(
					gridIndex = box.targetGridIndex,
					boxData = box,
					pos = targetPos,
					width = boxW,
					height = boxH
				)
			)
		}

		// 2. ⭐ 박스 간 80% 이상 겹침 방지 (Relaxation) ⭐
		resolveOverlaps(calculatedTargets)

		var needInvalidate = false

		// 3. 부드러운 애니메이션(Lerp) 적용 및 그리기
		calculatedTargets.forEach { item ->
			val target = item.pos
			val animPos = animatedPositions.getOrPut(item.gridIndex) {
				AnimatedPosition(target.x, target.y, target.x, target.y)
			}

			animPos.targetX = target.x
			animPos.targetY = target.y

			val lerp = 0.25f
			if (abs(animPos.currentX - animPos.targetX) > 1f || abs(animPos.currentY - animPos.targetY) > 1f) {
				animPos.currentX += (animPos.targetX - animPos.currentX) * lerp
				animPos.currentY += (animPos.targetY - animPos.currentY) * lerp
				needInvalidate = true
			}

			drawTextBox(canvas, PointF(animPos.currentX, animPos.currentY), item.boxData.info, item.gridIndex)
		}

		if (needInvalidate) invalidate()
	}

	// --- 겹침 해소 데이터 구조 및 알고리즘 ---
	private data class BoxTarget(
		val gridIndex: Int,
		val boxData: BitmapUtility.TextBoxData,
		val pos: PointF,
		val width: Float,
		val height: Float
	) {
		fun getRect(): RectF = RectF(
			pos.x - width / 2f,
			pos.y - height / 2f,
			pos.x + width / 2f,
			pos.y + height / 2f
		)
	}

	/**
	 * 80% 이상 겹치는 박스를 위/아래로 밀어내 서로 비껴가게 정렬
	 */
	private fun resolveOverlaps(targets: MutableList<BoxTarget>, iterations: Int = 4) {
		val viewH = height.toFloat()
		val safeMargin = padding.dp

		repeat(iterations) {
			for (i in 0 until targets.size) {
				for (j in i + 1 until targets.size) {
					val a = targets[i]
					val b = targets[j]

					val rA = a.getRect()
					val rB = b.getRect()

					// 두 박스의 겹치는 영역 계산
					val intersectL = max(rA.left, rB.left)
					val intersectT = max(rA.top, rB.top)
					val intersectR = min(rA.right, rB.right)
					val intersectB = min(rA.bottom, rB.bottom)

					if (intersectL < intersectR && intersectT < intersectB) {
						val overlapW = intersectR - intersectL
						val overlapH = intersectB - intersectT
						val overlapArea = overlapW * overlapH
						val minArea = min(rA.width() * rA.height(), rB.width() * rB.height())

						// 겹치는 면적이 작은 쪽 박스의 20% 이상인 경우(내부 80% 침범 방지)
						if (overlapArea > minArea * 0.2f) {
							val pushDist = (overlapH / 2f) + 4.dp
							if (a.pos.y < b.pos.y) {
								a.pos.y -= pushDist
								b.pos.y += pushDist
							} else {
								a.pos.y += pushDist
								b.pos.y -= pushDist
							}

							// 화면 상/하단 클램핑
							a.pos.y = a.pos.y.coerceIn(a.height / 2f + safeMargin, viewH - a.height / 2f - safeMargin)
							b.pos.y = b.pos.y.coerceIn(b.height / 2f + safeMargin, viewH - b.height / 2f - safeMargin)
						}
					}
				}
			}
		}
	}

	private fun calculateFluidPosition(
		anchor: PointF?,
		faceRect: RectF,
		boxW: Float,
		boxH: Float
	): PointF {
		val viewW = width.toFloat()
		val viewH = height.toFloat()
		if (viewW <= 0 || viewH <= 0 || anchor == null) return PointF(viewW / 2f, viewH / 2f)

		val faceCenterX = faceRect.centerX()
		val faceCenterY = faceRect.centerY()

		val margin = 12.dp
		var targetX = anchor.x
		var targetY = anchor.y

		// 좌/우 판단: 얼굴 중심에서 바깥쪽으로 배치
		if (anchor.x >= faceCenterX) {
			targetX += (boxW / 2f + margin)
		} else {
			targetX -= (boxW / 2f + margin)
		}

		// 상/하 판단
		if (anchor.y > faceCenterY + (faceRect.height() * 0.2f)) {
			targetY += (boxH / 2f + margin)
		} else if (anchor.y < faceCenterY - (faceRect.height() * 0.2f)) {
			targetY -= (boxH / 2f + margin)
		}

		val safeMargin = padding.dp
		val minX = boxW / 2f + safeMargin
		val maxX = viewW - (boxW / 2f) - safeMargin
		val minY = boxH / 2f + safeMargin
		val maxY = viewH - (boxH / 2f) - safeMargin

		return PointF(targetX.coerceIn(minX, maxX), targetY.coerceIn(minY, maxY))
	}

	private fun drawTextBox(canvas: Canvas, pos: PointF, info: BitmapUtility.TextDrawInfo, gridIndex: Int) {
		val isCollapsed = collapsedIndices.contains(gridIndex)
		val p = 10.dp

		if (isCollapsed) {
			val size = 32.dp
			val rect = RectF(pos.x - size / 2, pos.y - size / 2, pos.x + size / 2, pos.y + size / 2)
			bgPaint.color = Color.parseColor("#CC000000")
			canvas.drawOval(rect, bgPaint)

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
			val hasTitle = info.title.isNotEmpty()
			val titlePaint = Paint(textPaint).apply {
				color = "#FF0055".toColorInt()
				textSize = (dataTextSize + 2).dp
				isFakeBoldText = true
			}
			val itemPaint = Paint(textPaint).apply {
				color = Color.WHITE
				textSize = dataTextSize.dp
			}

			var maxW = if (hasTitle) titlePaint.measureText(info.title) else 0f
			info.items.forEach { maxW = maxOf(maxW, itemPaint.measureText("${it.label} ${it.value}")) }

			val boxW = maxW + p * 2
			val lineH = 18.dp
			val titleSectionH = if (hasTitle) titlePaint.textSize else 0f
			val boxH = p * 2 + titleSectionH + (info.items.size * lineH)

			val rect = RectF(pos.x - boxW / 2, pos.y - boxH / 2, pos.x + boxW / 2, pos.y + boxH / 2)

			bgPaint.color = "#7F000000".toColorInt()
			canvas.drawRoundRect(rect, 6.dp, 6.dp, bgPaint)

			var curY = rect.top + p
			if (hasTitle) {
				curY -= titlePaint.fontMetrics.ascent
				canvas.drawText(info.title, rect.left + p, curY, titlePaint)
				curY += titlePaint.fontMetrics.descent
			} else {
				curY -= itemPaint.fontMetrics.ascent
			}

			info.items.forEachIndexed { index, it ->
				if (hasTitle || index > 0) curY += lineH
				canvas.drawText("${it.label} ${it.value}", rect.left + p, curY, itemPaint)
			}
		}
	}

	private fun getBoxDimensions(info: BitmapUtility.TextDrawInfo, isCollapsed: Boolean): Pair<Float, Float> {
		val p = 10.dp
		if (isCollapsed) return Pair(32.dp, 32.dp)

		val titlePaint = Paint().apply { textSize = (dataTextSize + 2).dp; isFakeBoldText = true }
		val itemPaint = Paint().apply { textSize = dataTextSize.dp }

		val hasTitle = info.title.isNotEmpty()
		var maxW = if (hasTitle) titlePaint.measureText(info.title) else 0f
		info.items.forEach { maxW = maxOf(maxW, itemPaint.measureText("${it.label} ${it.value}")) }

		val lineH = 18.dp
		val titleSectionH = if (hasTitle) titlePaint.textSize else 0f
		val boxW = maxW + p * 2
		val boxH = p * 2 + titleSectionH + (info.items.size * lineH)

		return Pair(boxW, boxH)
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		val x = event.x
		val y = event.y

		when (event.action) {
			MotionEvent.ACTION_DOWN -> {
				val hitBoxIndex = findHitBoxIndex(x, y)
				return hitBoxIndex != -1
			}
			MotionEvent.ACTION_UP -> {
				val hitBoxIndex = findHitBoxIndex(x, y)
				if (hitBoxIndex != -1) {
					toggleBox(hitBoxIndex)
					return true
				}
			}
		}
		return super.onTouchEvent(event)
	}

	private fun findHitBoxIndex(x: Float, y: Float): Int {
		textBoxes.forEach { box ->
			val pos = animatedPositions[box.targetGridIndex] ?: return@forEach
			val isCollapsed = collapsedIndices.contains(box.targetGridIndex)
			val (boxW, boxH) = getBoxDimensions(box.info, isCollapsed)
			val touchRect = RectF(pos.currentX - boxW / 2, pos.currentY - boxH / 2, pos.currentX + boxW / 2, pos.currentY + boxH / 2)

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
		invalidate()
	}

	private fun calculateRatioPosition(
		gridIndex: Int,
		faceRect: RectF,
		boxW: Float,
		boxH: Float
	): PointF {
		val viewW = width.toFloat()
		val viewH = height.toFloat()
		val safeMargin = padding.dp

		val minX = boxW / 2f + safeMargin
		val maxX = viewW - (boxW / 2f) - safeMargin
		val minY = boxH / 2f + safeMargin
		val maxY = viewH - (boxH / 2f) - safeMargin

		var targetX = faceRect.centerX()
		var targetY = faceRect.centerY()

		when (gridIndex) {
			// 하단 가로 5분할 (30..34)
			in 25..29 -> {
				val col = gridIndex - 25 // 0, 1, 2, 3, 4
				val cellW = faceRect.width() / 5f

				targetX = faceRect.left + (cellW * col) + (cellW / 2f)
				targetY = faceRect.bottom - 80.dp + (boxH / 2f) // 턱선 아래 16dp
			}

			// 우측 세로 3분할 (35..37 또는 사용하시는 우측 인덱스)
			4, 14, 24 -> {
				targetX = faceRect.right + 80.dp + (boxW / 2f) // 얼굴 우측 16dp

				val faceH = faceRect.height()
				targetY = when (gridIndex) {
					// 9: 눈썹 라인 (얼굴 상단에서 약 18~20% 지점)
					4 -> faceRect.top + (faceH * -0.25f)

					// 19: 콧볼/코끝 라인 (얼굴 상단에서 약 50~52% 지점)
					14 -> faceRect.top + (faceH * 0.0f)

					// 29: 입술 라인 (얼굴 상단에서 약 72~75% 지점)
					24 -> faceRect.top + (faceH * 0.25f)

					else -> faceRect.top + (faceH * 0.5f)
				}
			}
		}

		return PointF(targetX.coerceIn(minX, maxX), targetY.coerceIn(minY, maxY))
	}

}