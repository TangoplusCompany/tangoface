package com.tangoplus.tangoface.ui.view


import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

class CircularGuideView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null
) : View(context, attrs) {

	private var hasAnimationStarted = false
	private var overlayAlpha = 0f // 0 ~ 1f (투명 → 짙어짐)
	private var overlayAnimator: ValueAnimator? = null

	// 성공 상태 애니메이션 관련
	private var isSuccessMode = false
	private var successProgress = 0f // 0 ~ 1f (성공 애니메이션 진행도)
	private var successAnimator: ValueAnimator? = null

	// 원의 크기를 화면 대비 비율로 설정 (0.0 ~ 1.0)
	private var circleRadiusRatio = 0.65f // 기본값: 화면 최소 크기의 30%

	// 배경 어둠 정도 설정
	private var backgroundDarkness = 0.7f // 0.0 ~ 1.0 (0: 투명, 1: 완전 어둠)

	// 원의 세로 위치 조정 (-1.0 ~ 1.0, 0이 가운데)
	private var verticalBias = -0.25f // -1.0: 맨 위, 0: 가운데, 1.0: 맨 아래

	fun setLandscape(isLandscape: Boolean) {
		if (isLandscape) {
			circleRadiusRatio = 0.65f
			verticalBias = -0.15f
			offset = 128f
		} else {
			circleRadiusRatio = 0.7f
			verticalBias = -0.2f
			offset = 86f
		}
		invalidate() // 화면 갱신
	}

	fun triggerIntroAnimationIfNeeded() {
		if (!hasAnimationStarted) {
			hasAnimationStarted = true
			startOverlayFadeIn()
//            startIntroAnimation()
		}
	}

	fun triggerOffAnimationIfNeeded() {
		if (hasAnimationStarted) {
			hasAnimationStarted = false
			startOverlayFadeOut()
//            startIntroAnimation()
		}
	}


	// 성공 애니메이션 시작
//    fun startSuccessAnimation(onComplete: (() -> Unit)? = null) {
//        if (isSuccessMode) return // 이미 성공 모드면 중복 실행 방지
//
//        isSuccessMode = true
//        successAnimator?.cancel()
//        successAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
//            duration = 1500L
//            interpolator = AccelerateDecelerateInterpolator()
//            addUpdateListener {
//                successProgress = it.animatedValue as Float
//                invalidate()
//            }
//            addListener(object : AnimatorListenerAdapter() {
//                override fun onAnimationEnd(animation: Animator) {
//                    if (successProgress == 1f) onComplete?.invoke()
//                }
//            })
//            start()
//        }
//    }

	// 성공 모드 리셋
//    fun resetSuccessMode() {
//        isSuccessMode = false
//        successProgress = 0f
//        successAnimator?.cancel()
//        invalidate()
//    }
	var offset = 128f
	val guidePaint = Paint().apply {
		color = Color.WHITE   // 기본 흰색
		strokeWidth = 6f
		isAntiAlias = true
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
	}
	fun Int.toPx(context: Context): Float {
		return this * context.resources.displayMetrics.density
	}
	private val lineLength = 25.toPx(context)

//    private var progressAngle = 0f // 0~360도

//    private fun startIntroAnimation() {
//        ValueAnimator.ofFloat(0f, 360f).apply {
//            duration = 1000L
//            interpolator = AccelerateDecelerateInterpolator()
//            addUpdateListener {
//                progressAngle = it.animatedValue as Float
//                invalidate()
//            }
//            start()
//        }
//    }


	@SuppressLint("DrawAllocation")
	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		val cx = width / 2f
		// verticalBias를 적용한 Y 좌표 계산
		val cy = height / 2f + (height / 2f * verticalBias)

		// 화면 크기 기준으로 원의 반지름 계산
		val minDimension = min(width, height)
		val circleRadius = minDimension * circleRadiusRatio / 2f
		val lineRadius = circleRadius - lineLength

		// ✅ 1. 전체 배경을 어둡게 처리
		if (overlayAlpha > 0f) {
			// 전체 화면을 어둡게 칠하기
			val backgroundPaint = Paint().apply {
				color = Color.BLACK
				alpha = (overlayAlpha * backgroundDarkness * 255).toInt()
				isAntiAlias = true
			}
			canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

			// 가운데 원형 영역을 투명하게 만들기 (블렌드 모드 사용)
			val clearPaint = Paint().apply {
				isAntiAlias = true
				xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
			}
			canvas.drawCircle(cx, cy, circleRadius + lineLength, clearPaint)


			canvas.drawLine(cx, cy - circleRadius, cx, cy - circleRadius + offset, guidePaint) // 북쪽
			canvas.drawLine(cx, cy + circleRadius, cx, cy + circleRadius - offset, guidePaint) // 남쪽
			canvas.drawLine(cx - circleRadius, cy, cx - circleRadius + offset, cy, guidePaint) // 서쪽
			canvas.drawLine(cx + circleRadius, cy, cx + circleRadius - offset, cy, guidePaint) // 동쪽

		}

		// ✅ 2. Face ID 스타일 원형 선들 그리기
//        for (i in 0 until numLines) {
//            val rawAngleDeg = i * (360f / numLines)
//            val angleDeg = rawAngleDeg - 90f  // 시작 각도를 12시 방향으로 보정
//
//            if (rawAngleDeg > progressAngle) break  // 비교는 기존 각도로
//
//            val angleRad = Math.toRadians(angleDeg.toDouble())
//
//            val startX = cx + cos(angleRad) * lineRadius
//            val startY = cy + sin(angleRad) * lineRadius
//            val endX = cx + cos(angleRad) * circleRadius
//            val endY = cy + sin(angleRad) * circleRadius
//
//            // 성공 모드일 때 색상 결정
//            val paintToUse = if (isSuccessMode) {
//                val currentSuccessAngle = successProgress * 360f
//
//                if (angleDeg <= currentSuccessAngle) {
//                    // 성공 애니메이션이 지나간 부분: 노란색→녹색 그라데이션
//                    val progressRatio = angleDeg / 360f
//                    val yellow = "#FFD700".toColorInt() // 골드 옐로우
//                    val green = "#00FF00".toColorInt()  // 라임 그린
//
//                    // 노란색에서 녹색으로 보간
//                    val red = Color.red(yellow) + ((Color.red(green) - Color.red(yellow)) * progressRatio).toInt()
//                    val greenVal = Color.green(yellow) + ((Color.green(green) - Color.green(yellow)) * progressRatio).toInt()
//                    val blue = Color.blue(yellow) + ((Color.blue(green) - Color.blue(yellow)) * progressRatio).toInt()
//
//                    successPaint.apply {
//                        color = Color.rgb(red.coerceIn(0, 255), greenVal.coerceIn(0, 255), blue.coerceIn(0, 255))
//                    }
//                } else {
//                    // 아직 애니메이션이 지나가지 않은 부분: 기본 색상
//                    linePaint
//                }
//            } else {
//                // 일반 모드: 기본 색상
//                linePaint
//            }
//
//            canvas.drawLine(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), paintToUse)
//        }
	}

	private fun startOverlayFadeIn() {
		overlayAnimator?.cancel()
		overlayAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = 800L
			interpolator = DecelerateInterpolator()
			addUpdateListener {
				overlayAlpha = it.animatedValue as Float
				invalidate()
			}
			start()
		}
	}

	private fun startOverlayFadeOut() {
		overlayAnimator?.cancel()
		overlayAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
			duration = 800L
			interpolator = DecelerateInterpolator()
			addUpdateListener {
				overlayAlpha = it.animatedValue as Float
				invalidate()
			}
			start()
		}
	}
}