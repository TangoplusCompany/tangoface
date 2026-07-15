package com.tangoplus.tangoface.domain.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
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
import androidx.fragment.app.Fragment
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import androidx.core.content.edit

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
		typingSpeed: Long = 10,
		lineBreakDelay: Long = 100
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

	// fragment에서 쓰는 상단 가이드 배너 include로 붙은 xml 텍스트 대응하기
	fun Fragment.setupGuideBanner(
		bannerView: View,
		closeButton: View,
		textView: TextView,
		message: String,
		prefKey: String
	) {
		val prefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

		val dismissedTime = prefs.getLong(prefKey, 0L)
		val currentTime = System.currentTimeMillis()

		val oneMonthInMillis = 30L * 24 * 60 * 60 * 1000

		if (dismissedTime > 0 && (currentTime - dismissedTime) < oneMonthInMillis) {
			bannerView.visibility = View.GONE
			return
		}

		textView.text = message

		closeButton.setOnClickListener {
			bannerView.visibility = View.GONE
			prefs.edit { putLong(prefKey, System.currentTimeMillis()) }
		}
	}

	fun crossFadeViews(oldView: View, newView: View) {
		// 기존 리사이클러뷰 페이드 아웃
		oldView.animate()
			.alpha(0f)
			.setDuration(400)
			.withEndAction { oldView.visibility = View.GONE }

		// 새로운 결과창 페이드 인 + 아래에서 위로 살짝 올라오는 효과
		newView.alpha = 0f
		newView.translationY = 50f // 밑으로 50포인트 내려놓은 상태에서
		newView.visibility = View.VISIBLE

		newView.animate()
			.alpha(1f)
			.translationY(0f) // 제자리로 올리면서 나타나기
			.setDuration(500)
			.start()
	}


}