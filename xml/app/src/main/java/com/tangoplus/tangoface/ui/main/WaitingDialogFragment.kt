package com.tangoplus.tangoface.ui.main

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import androidx.viewpager2.widget.ViewPager2
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.FragmentWaitingDialogBinding
import com.tangoplus.tangoface.domain.util.PreferenceUtility
import com.tangoplus.tangoface.ui.adapter.AdVPAdapter

class WaitingDialogFragment : DialogFragment() {

	private var _bd: FragmentWaitingDialogBinding? = null
	private val bd get() = _bd!!

	private val autoScrollHandler = Handler(Looper.getMainLooper())
	private var autoScrollRunnable: Runnable? = null
	private var savedPosters: List<String> = emptyList()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_bd = FragmentWaitingDialogBinding.inflate(inflater, container, false)
		return bd.root
	}

	override fun onStart() {
		super.onStart()
		dialog?.window?.apply {
			setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
			setBackgroundDrawableResource(android.R.color.black)
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		setupViewPager()

		// 화면 어디든 터치하면 즉시 종료 후 복귀
		bd.root.setOnClickListener {
			dismiss()
		}
	}

	private fun setupViewPager() {
		val pPrefs = PreferenceUtility(requireContext())
		savedPosters = pPrefs.getPosters()

		if (savedPosters.isEmpty()) {
			pPrefs.initDefaultPosters(requireContext())
			savedPosters = pPrefs.getPosters()
		}

		// 1. 사용자 직접 스와이프 비활성화
		bd.vpM.isUserInputEnabled = false

		// 2. 어댑터 연결
		val adapter = AdVPAdapter(savedPosters)
		bd.vpM.adapter = adapter

		// 3. 인디케이터 초기화
		setupIndicators(savedPosters.size)

		// 4. 무한 스크롤 및 중앙 위치 지정
		if (savedPosters.size > 1) {
			val startPosition = Int.MAX_VALUE / 2
			val jumpPosition = startPosition - (startPosition % savedPosters.size)
			bd.vpM.setCurrentItem(jumpPosition, false)
			updateIndicator(0)
			startAutoScroll()
		}

		// 5. 페이지 변경 시 인디케이터 갱신
		bd.vpM.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
			override fun onPageSelected(position: Int) {
				super.onPageSelected(position)
				if (savedPosters.isNotEmpty()) {
					val realPosition = position % savedPosters.size
					updateIndicator(realPosition)
				}
			}
		})
	}

	private fun startAutoScroll() {
		stopAutoScroll()
		val prefs = PreferenceUtility(requireContext())
		// 1. 숫자로 변환 후 밀리초(Long) 계산
		val intervalMillis = (prefs.getSlideVelocity().takeIf { it > 0 } ?: 0L).toLong()

		autoScrollRunnable = object : Runnable {
			override fun run() {
				bd.vpM.currentItem += 1
				autoScrollHandler.postDelayed(this, intervalMillis * 1000)
			}
		}
		autoScrollHandler.postDelayed(autoScrollRunnable!!, intervalMillis* 1000)
	}

	private fun stopAutoScroll() {
		autoScrollRunnable?.let {
			autoScrollHandler.removeCallbacks(it)
		}
	}

	private fun setupIndicators(count: Int) {
		bd.vpIndicator.removeAllViews()
		if (count <= 1) return

		val params = LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.WRAP_CONTENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
		).apply {
			setMargins(8, 0, 8, 0)
		}

		for (i in 0 until count) {
			val dot = ImageView(requireContext()).apply {
				setImageResource(if (i == 0) R.drawable.dot_active else R.drawable.dot_inactive)
				layoutParams = params
			}
			bd.vpIndicator.addView(dot)
		}
	}

	private fun updateIndicator(selectedPosition: Int) {
		val childCount = bd.vpIndicator.childCount
		for (i in 0 until childCount) {
			val dot = bd.vpIndicator.getChildAt(i) as? ImageView
			dot?.setImageResource(
				if (i == selectedPosition) R.drawable.dot_active else R.drawable.dot_inactive
			)
		}
	}

	override fun onResume() {
		super.onResume()
		if (savedPosters.size > 1) startAutoScroll()
	}

	override fun onPause() {
		super.onPause()
		stopAutoScroll()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		stopAutoScroll()
		bd.vpM.adapter = null
		_bd = null
	}
}