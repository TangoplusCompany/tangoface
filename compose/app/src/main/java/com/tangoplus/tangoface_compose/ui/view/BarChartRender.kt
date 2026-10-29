package com.tangoplus.tangoface_compose.ui.view

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.interfaces.dataprovider.BarDataProvider
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.renderer.BarChartRenderer
import com.github.mikephil.charting.utils.ViewPortHandler

class BarChartRender(
	chart: BarDataProvider?,
	animator: ChartAnimator?,
	viewPortHandler: ViewPortHandler?
) : BarChartRenderer(chart, animator, viewPortHandler) {
	private var mRightRadius = 12f
	private var mLeftRadius = 12f

	override fun drawDataSet(c: Canvas, dataSet: IBarDataSet, index: Int) {
		val trans = mChart.getTransformer(dataSet.axisDependency)
		mShadowPaint.color = dataSet.barShadowColor
		val phaseX = mAnimator.phaseX
		val phaseY = mAnimator.phaseY

		// initialize the buffer
		val buffer = mBarBuffers[index]
		buffer.setPhases(phaseX, phaseY)
		buffer.setDataSet(index)
		buffer.setBarWidth(mChart.barData.barWidth)
		buffer.setInverted(mChart.isInverted(dataSet.axisDependency))
		buffer.feed(dataSet)
		trans.pointValuesToPixel(buffer.buffer)
		mRenderPaint.color = dataSet.color

		var j = 0
		while (j < buffer.size()) {
			if (!mViewPortHandler.isInBoundsLeft(buffer.buffer[j + 2])) {
				j += 4
				continue
			}
			if (!mViewPortHandler.isInBoundsRight(buffer.buffer[j])) break

			val barRect = RectF(
				buffer.buffer[j],     // left
				buffer.buffer[j + 1], // top
				buffer.buffer[j + 2], // right
				buffer.buffer[j + 3]  // bottom
			)

			// 그림자 바 그리기
			if (mChart.isDrawBarShadowEnabled) {
				val shadowPath = Path()
				val shadowRadii = floatArrayOf(
					mLeftRadius, mLeftRadius,     // top-left
					mRightRadius, mRightRadius,   // top-right
					0f, 0f,                       // bottom-right
					0f, 0f                        // bottom-left
				)
				shadowPath.addRoundRect(barRect, shadowRadii, Path.Direction.CW)
				c.drawPath(shadowPath, mShadowPaint)
			}

			// 실제 바 그리기
			val path = Path()
			val radii = floatArrayOf(
				mLeftRadius, mLeftRadius,     // top-left
				mRightRadius, mRightRadius,   // top-right
				0f, 0f,                       // bottom-right
				0f, 0f                        // bottom-left
			)
			path.addRoundRect(barRect, radii, Path.Direction.CW)
			c.drawPath(path, mRenderPaint)

			j += 4
		}
	}
}