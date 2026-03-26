package com.tangoplus.tangoface.domain.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewAnimationUtils
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.widget.NestedScrollView
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.hypot

object AnimationUtility {
	fun startDismissWithRipple(
		root: FrameLayout,
		touchX: Float,
		touchY: Float,
		isFinish: () -> Unit
	) {
		val finalRadius = hypot(root.width.toDouble(), root.height.toDouble()).toFloat()
		val anim = ViewAnimationUtils.createCircularReveal(
			root,
			touchX.toInt(),
			touchY.toInt(),
			finalRadius,
			0f
		)
		anim.duration = 300
		anim.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				super.onAnimationEnd(animation)
				isFinish()
			}
		})
		anim.start()
	}


	fun setAnimation(tv: View, duration: Long, delay: Long, fade: Boolean, callback: () -> Unit) {

		val animator = ObjectAnimator.ofFloat(tv, "alpha", if (fade) 0f else 1f, if (fade) 1f else 0f)
		animator.duration = duration
		animator.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				super.onAnimationEnd(animation)
				tv.visibility = if (fade) View.VISIBLE else View.INVISIBLE
				callback()
			}
		})
		Handler(Looper.getMainLooper()).postDelayed({
			animator.start()
		}, delay)
	}

	fun scrollToView(view: View, nsv: NestedScrollView) {
		val location = IntArray(2)
		view.getLocationInWindow(location)
		val viewTop = location[1]
		val scrollViewLocation = IntArray(2)

		nsv.getLocationInWindow(scrollViewLocation)
		val scrollViewTop = scrollViewLocation[1]
		val scrollY = nsv.scrollY
		val scrollTo = scrollY + viewTop - scrollViewTop
		nsv.smoothScrollTo(0, scrollTo)
	}


	fun startFadeOut(v: View) {
		ValueAnimator.ofFloat(1f, 0f).apply {
			duration = 800L
			interpolator = DecelerateInterpolator()
			addUpdateListener {
				v.alpha = it.animatedValue as Float
				v.invalidate()
			}
			start()
		}
	}

	fun startFadeIn(v: View) {
		ValueAnimator.ofFloat(0f, 1f).apply {
			duration = 800L
			interpolator = DecelerateInterpolator()
			addUpdateListener {
				v.alpha = it.animatedValue as Float
				v.invalidate()
			}
			start()
		}
	}


	fun startBouncingAnimation(bounceAnimator: AnimatorSet?, tv: View) {
		val bounceDistance = -10 * tv.resources.displayMetrics.density

		val upAnim = ObjectAnimator.ofFloat(tv, "translationY", 0f, bounceDistance).apply {
			duration = 400
			interpolator = AccelerateDecelerateInterpolator()
		}

		val downAnim = ObjectAnimator.ofFloat(tv, "translationY", bounceDistance, 0f).apply {
			duration = 400
			interpolator = AccelerateDecelerateInterpolator()
		}

		bounceAnimator?.apply {
			playSequentially(upAnim, downAnim)
			addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					Handler(Looper.getMainLooper()).postDelayed({
						if (!isCancelled) {
							start()
						}
					}, 1000) // 1초 딜레이
				}

				var isCancelled = false
				override fun onAnimationCancel(animation: Animator) {
					isCancelled = true
				}
			})
			start()
		}
	}


	fun animateTextViewToTopLeft(
		cl: ConstraintLayout,
		tv: View,
		endVerti: Float = 0.5f,
		endHorizon: Float = 0.5f
	) {
		val constraintSet = ConstraintSet()
		constraintSet.clone(cl)

		constraintSet.setVerticalBias(tv.id, endVerti)
		constraintSet.setHorizontalBias(tv.id, endHorizon)

		val transition = ChangeBounds().apply {
			duration = 800
			interpolator = AccelerateDecelerateInterpolator()
		}

		// 애니메이션 실행
		TransitionManager.beginDelayedTransition(cl, transition)
		constraintSet.applyTo(cl)
	}

	suspend fun startTypingAnimation(
		textView: TextView,
		fullText: String,
		typingSpeed: Long = 50,
		lineBreakDelay: Long = 250
	) = withContext(Dispatchers.Main) {
		textView.text = ""

		fullText.forEachIndexed { index, char ->
			textView.text = fullText.substring(0, index + 1)

			val delay = if (char == '\n') {
				typingSpeed + lineBreakDelay
			} else {
				typingSpeed
			}

			delay(delay)
		}
	}

	fun setGuideAnimation(vv: View, case: Int = 0) {
		// case 1: 버튼 아래-위 슬라이드 else: view 위-아래 슬라이드
		val slideCase = when (case) {
			1 -> 100f
			else -> -100f
		}
		val slideDuration = when (case) {
			1 -> 500L
			else -> 600L
		}
		val slide = TranslateAnimation(0f, 0f, slideCase, 0f)
		slide.apply {
			duration = slideDuration
			interpolator = AccelerateDecelerateInterpolator()
			fillAfter = true
		}
		val fadeCase = when (case) {
			1 -> 0L
			else -> 600L
		}
		val fadeIn = AlphaAnimation(0f, 1f).apply {
			duration = fadeCase
		}
		val animationSet = AnimationSet(true).apply {
			addAnimation(slide)
			addAnimation(fadeIn)
			fillAfter = true
		}
		vv.apply {
			visibility = View.VISIBLE
			animation = animationSet
		}
	}
}