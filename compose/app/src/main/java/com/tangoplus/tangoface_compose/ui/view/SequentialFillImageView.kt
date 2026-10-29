package com.tangoplus.tangoface_compose.ui.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat

class SequentialFillImageView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

  companion object {
    private const val FILL_DURATION_MS = 1250L
    private const val HOLD_DURATION_MS = 250L
    private const val MAX_LEVEL = 10000
  }

  private var drawableResIds: List<Int> = emptyList()
  @ColorInt
  private var accentColor: Int = 0
  private var onFinished: (() -> Unit)? = null

  private var currentIndex = 0
  private var clipDrawable: ClipDrawable? = null
  private var animator: ValueAnimator? = null

  /** 4개의 drawable resId와 accent 컬러를 세팅하고 애니메이션 시작 */
  fun setFramesAndStart(
    @DrawableRes drawableResIds: List<Int>,
    @ColorInt accentColor: Int,
    onFinished: (() -> Unit)? = null
  ) {
    require(drawableResIds.size == 4) { "drawable은 4개여야 합니다" }
    cancelAnimation()
    this.drawableResIds = drawableResIds
    this.accentColor = accentColor
    this.onFinished = onFinished
    currentIndex = 0
    showCurrentIcon()
  }

  fun cancelAnimation() {
    animator?.cancel()
    animator = null
    removeCallbacks(null)
  }

  private fun showCurrentIcon() {
    val resId = drawableResIds[currentIndex]

    // Layer 0: 흰색 원본
    val baseDrawable: Drawable = ContextCompat.getDrawable(context, resId)!!.mutate()

    // Layer 1: accent 틴트 + ClipDrawable(아래 기준)
    val accentDrawable: Drawable = ContextCompat.getDrawable(context, resId)!!.mutate()
    accentDrawable.colorFilter = PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_IN)
    val clip = ClipDrawable(accentDrawable, Gravity.BOTTOM, ClipDrawable.VERTICAL)
    clip.level = 0
    clipDrawable = clip

    val layered = LayerDrawable(arrayOf(baseDrawable, clip))
    setImageDrawable(layered) // ImageView의 기존 기능(scaleType 등) 그대로 적용됨

    animateFill()
  }

  private fun animateFill() {
    animator = ValueAnimator.ofInt(0, MAX_LEVEL).apply {
      duration = FILL_DURATION_MS
      interpolator = LinearInterpolator()
      addUpdateListener { anim ->
        clipDrawable?.level = anim.animatedValue as Int
      }
      addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
          postDelayed({ goToNext() }, HOLD_DURATION_MS)
        }
      })
      start()
    }
  }

  private fun goToNext() {
    if (currentIndex >= drawableResIds.size - 1) {
      onFinished?.invoke()
      return
    }
    currentIndex++
    showCurrentIcon()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    cancelAnimation()
  }
}
