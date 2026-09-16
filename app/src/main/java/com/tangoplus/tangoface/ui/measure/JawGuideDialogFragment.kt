package com.tangoplus.tangoface.ui.measure

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.FragmentJawGuideDialogBinding
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.ui.adapter.ManualVPAdapter
import com.tangoplus.tangoface.ui.measure.vm.MeasureViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class JawGuideDialogFragment : DialogFragment() {
	private lateinit var bd : FragmentJawGuideDialogBinding
	private val mvm : MeasureViewModel by activityViewModels()
	private val compositionMap = mutableMapOf<String, com.airbnb.lottie.LottieComposition>()
	private val autoSteps = listOf(
		"jaw_guide_0.json" to "[측정 준비]\n이빨을 맞물린 상태로 미소를\n지은 상태로 시작 버튼을 누릅니다.",
		"jaw_guide_0_1.json" to "[턱 가측 이동]\n측정이 시작되면 이빨을 맞물리려고 유지한 상태로\n아랫턱을 왼쪽 혹은 오른쪽으로 끝까지 움직여주세요",
		"jaw_guide_1_2.json" to "[입 벌림]\n가측 최대한 끝까지 이동된 상태에서\n자연스럽게 입을 최대한 벌려주세요",
		"jaw_guide_2_3.json" to "[측정 마무리]\n벌린 입을 앙다물면\n턱 가측 이동 단계가 종료됩니다."
	)
	private var currentAutoIndex = 0

	private val manualDrawables = listOf(
		R.drawable.drawable_seq_1 to "1단계 [측정 준비]\n이빨을 맞물린 상태로 미소를\n지은 상태로 시작 버튼을 누릅니다.",
		R.drawable.drawable_seq_2 to "2단계 [턱 가측 이동]\n측정이 시작되면 이빨을 맞물리려고 유지한 상태로\n아랫턱을 왼쪽 혹은 오른쪽으로 끝까지 움직여주세요",
		R.drawable.drawable_seq_4 to "3단계 [입 벌림]\n가측 최대한 끝까지 이동된 상태에서\n자연스럽게 입을 최대한 벌려주세요",
		R.drawable.drawable_seq_23_done to "4단계 [측정 마무리]\n벌린 입을 앙다물면\n턱 가측 이동 단계가 종료됩니다."
	)

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentJawGuideDialogBinding.inflate(inflater)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// 로티 미리 로드
		autoSteps.forEach { (fileName, _) ->
			com.airbnb.lottie.LottieCompositionFactory.fromAsset(requireContext(), fileName)
				.addListener { composition ->
					compositionMap[fileName] = composition
				}
		}
		bd.lavJGDGuide.setFailureListener { throwable ->
			Log.e("LottieError", "파싱 실패: ${throwable.message}", throwable)
		}

		mvm.resetGuideStep()
		currentAutoIndex = 0

		animateTitleIntro { setupAutoPlay() }
		setupManualPager()
		bd.ibtnJGDExit.setOnSingleClickListener { dismiss() }

	}
	private fun setupAutoPlay() {
		bd.clJGD0.visibility = View.VISIBLE
		bd.clJGD1.visibility = View.GONE

		// 메인 Lottie 종료 시 -> 0.3초 대기 후 체크 표시
		bd.lavJGDGuide.addAnimatorListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				lifecycleScope.launch {
					delay(300) // 0.3초 유지
					playNextStepCheck()
				}
			}
		})

		// 체크 Lottie 종료 시 -> 다음 단계 이동
		bd.lavJGDCheck.setAnimation("jaw_guide_next_step.json")
		bd.lavJGDCheck.addAnimatorListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				lifecycleScope.launch {
					delay(400)
					currentAutoIndex++
					if (currentAutoIndex < autoSteps.size) {
						playGuideStep(currentAutoIndex)
					} else {
						delay(600)
						switchToManualMode()
					}
				}
			}
		})

		playGuideStep(0)
	}
	private fun playGuideStep(index: Int) {
		mvm.setGuideStep(index)

		val (fileName, msg) = autoSteps[index]
		bd.tvJGDDesc.text = msg
		val comp = compositionMap[fileName]
		if (comp != null) {
			bd.lavJGDGuide.setComposition(comp)
		} else {
			bd.lavJGDGuide.setAnimation(fileName)
		}
		bd.lavJGDGuide.playAnimation()
		bd.cvJGDCheck.visibility = View.INVISIBLE
	}

	private fun playNextStepCheck() {
		bd.cvJGDCheck.visibility = View.VISIBLE
		bd.lavJGDCheck.playAnimation()
	}

	// --- 수동 ViewPager 모드 ---
	private fun switchToManualMode() {
		bd.clJGD0.visibility = View.GONE
		bd.clJGD1.visibility = View.VISIBLE
		bd.vpJGD.setCurrentItem(0, false)
		bd.tvJGDDesc.text = manualDrawables[0].second
	}

	private fun setupManualPager() {
		bd.vpJGD.adapter = ManualVPAdapter(manualDrawables.map { it.first })

		bd.ibtnJGDLeft.setOnClickListener {
			val prev = bd.vpJGD.currentItem - 1
			if (prev >= 0) bd.vpJGD.setCurrentItem(prev, true)
		}

		bd.ibtnJGDRight.setOnClickListener {
			val next = bd.vpJGD.currentItem + 1
			if (next < manualDrawables.size) bd.vpJGD.setCurrentItem(next, true)
		}

		bd.vpJGD.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
			override fun onPageSelected(position: Int) {
				mvm.setManualPageStep(position)
				bd.tvJGDDesc.text = manualDrawables[position].second
				bd.ibtnJGDLeft.isVisible = (position > 0)
				bd.ibtnJGDRight.isVisible = (position < manualDrawables.lastIndex)
			}
		})

		bd.btnJGDConfirm.setOnClickListener {
			dismiss()
		}
	}

	private fun animateTitleIntro(onEnd: () -> Unit) {
		bd.tvJGDTitle.post {
			val parent = bd.root as ViewGroup
			val title = bd.tvJGDTitle

			// 최종(XML에 정의된) 위치의 중심 좌표
			val finalCenterX = title.x + title.width / 2f
			val finalCenterY = title.y + title.height / 2f

			// 화면 중앙 좌표
			val screenCenterX = parent.width / 2f
			val screenCenterY = parent.height / 2f
			title.apply {
				scaleX = 1.6f
				scaleY = 1.6f
				translationX = screenCenterX - finalCenterX
				translationY = screenCenterY - finalCenterY
				alpha = 0f
			}

			title.animate()
				.alpha(1f)
				.setDuration(100)
				.withEndAction {
					// 0.2초 페이드인 후, 0.3초 대기하다가 이동 시작
					title.animate()
						.scaleX(1f)
						.scaleY(1f)
						.translationX(0f)
						.translationY(0f)
						.setStartDelay(500)
						.setDuration(1000)
						.setInterpolator(android.view.animation.OvershootInterpolator(0.8f))
						.withEndAction {
							title.postDelayed({ onEnd() }, 500)  // 이동 끝난 후 0.5초 대기
						}
						.start()
				}
				.start()
		}
	}

	override fun onDestroyView() {
		bd.lavJGDGuide.removeAllAnimatorListeners()
		bd.lavJGDCheck.removeAllAnimatorListeners()
		bd.lavJGDGuide.cancelAnimation()
		bd.lavJGDCheck.cancelAnimation()
		super.onDestroyView()
	}


	override fun onStart() {
		super.onStart()
		// 기기 화면 가로 0.5, 세로 0.5 크기 적용
		dialog?.window?.let { window ->
			val metrics = resources.displayMetrics
			window.setLayout(
				(metrics.widthPixels * 0.7f).toInt(),
				(metrics.heightPixels * 0.5f).toInt()
			)
			window.setBackgroundDrawableResource(android.R.color.transparent)
		}
	}

}