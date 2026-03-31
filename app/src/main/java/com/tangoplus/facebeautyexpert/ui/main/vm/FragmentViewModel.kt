package com.tangoplus.facebeautyexpert.ui.main.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class FragmentViewModel : ViewModel() {

	private val _currentFragmentType = MutableLiveData<FragmentType>()
	val currentFragmentType: LiveData<FragmentType> = _currentFragmentType

	// Fragment 타입 enum
	enum class FragmentType {
		MAIN_FRAGMENT,
		MEASURE_HISTORY_FRAGMENT,
		STATISTICS_FRAGMENT,
	}

	// 현재 Fragment 설정
	fun setCurrentFragment(type: FragmentType) {
		_currentFragmentType.value = type
	}

	// 초기화 (필요시)
	init {
		_currentFragmentType.value = FragmentType.MAIN_FRAGMENT // 기본값
	}
}