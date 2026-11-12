package com.tangoplus.tangoface.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * RN 웹 스캐너의 PanoramaGuide 상단 트랙과 동일한 100° 수평 스윕 게이지 뷰
 * - #1C191A 트랙 배경, #FF0055 진행 채움(100% 도달 시 #00FFCC 전환), 이동 포인터
 */
class ScanSweepTrackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progressRatio: Float = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E01C191A")
        style = Paint.Style.FILL
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF0055")
        alpha = 115
        style = Paint.Style.FILL
    }

    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(64, 255, 255, 255)
        strokeWidth = 3f
    }

    private val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)
        textSize = 26f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF0055")
        style = Paint.Style.FILL
    }

    private val pointerInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val trackRect = RectF()
    private val fillRect = RectF()

    fun setProgress(ratio: Float) {
        val clamped = ratio.coerceIn(0f, 1f)
        if (clamped != progressRatio) {
            progressRatio = clamped
            val color = if (progressRatio >= 1f) {
                Color.parseColor("#00FFCC")
            } else {
                Color.parseColor("#FF0055")
            }
            fillPaint.color = color
            fillPaint.alpha = 115
            pointerPaint.color = color
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val corner = h / 2f
        trackRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(trackRect, corner, corner, trackPaint)

        val inset = h * 0.08f
        val fillMaxWidth = (w - inset * 2f).coerceAtLeast(0f)
        if (progressRatio > 0f) {
            fillRect.set(inset, inset, inset + fillMaxWidth * progressRatio, h - inset)
            canvas.drawRoundRect(fillRect, (h - inset * 2f) / 2f, (h - inset * 2f) / 2f, fillPaint)
        }

        val cy = h / 2f
        val pad = h * 0.35f
        canvas.drawLine(pad, cy, w - pad, cy, centerLinePaint)

        // 진행 방향 화살표 5개
        val chevronCount = 5
        val chevronStep = (w - pad * 2f) / (chevronCount + 1)
        val textOffset = (chevronPaint.descent() + chevronPaint.ascent()) / 2f
        for (i in 1..chevronCount) {
            canvas.drawText("▶", pad + chevronStep * i, cy - textOffset, chevronPaint)
        }

        // 이동 인디케이터 포인터
        val pointerRadius = h * 0.28f
        val minCx = pointerRadius + inset * 1.5f
        val maxCx = w - minCx
        val cx = minCx + (maxCx - minCx).coerceAtLeast(0f) * progressRatio
        canvas.drawCircle(cx, cy, pointerRadius, pointerPaint)
        canvas.drawCircle(cx, cy, pointerRadius * 0.35f, pointerInnerPaint)
    }
}

