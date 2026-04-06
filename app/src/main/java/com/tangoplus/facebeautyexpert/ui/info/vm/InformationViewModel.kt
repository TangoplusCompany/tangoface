package com.tangoplus.facebeautyexpert.ui.info.vm

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tangoplus.facebeautyexpert.domain.vo.FaceStaticItem
import com.tangoplus.facebeautyexpert.domain.vo.Sequence
import com.tangoplus.facebeautyexpert.ui.info.vo.DrawLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InformationViewModel : ViewModel() {
	val currentCheckedLines = mutableSetOf<DrawLine>()

	// 체크가 가능한 전체 데이터 VO 항목 담는 곳
	var currentFaceStaticsItem = mutableListOf<FaceStaticItem>()

	// info의 우측 사진 하단의  정면,교합/턱이동/목폄,개구
	private var currentSeq = Sequence.COMFORT
	fun setSeq(seq: Sequence) {
		currentSeq = seq
	}

	fun getSeq(): Sequence {
		return currentSeq
	}

	private var isLineOn = true
	fun switchedLineOn() { isLineOn = !isLineOn }
	fun getLineOn(): Boolean = isLineOn

	private var isGridLineOn = true
	fun switchedGridLineOn() { isGridLineOn = !isGridLineOn }
	fun getGridLineOn(): Boolean = isGridLineOn


	val unitIdxFromFaceResult = MutableLiveData(Pair(0, 0))
	val unitIdxFromCompare = MutableLiveData(Pair(0, 0))

	fun releaseUnitIdx() {
		unitIdxFromFaceResult.value = Pair(0, 0)
		unitIdxFromCompare.value = Pair(0, 0)
	}

	// angle pointer 모드
	// 각도 측정 모드 활성화 여부
	private val _isAngleModeActive = MutableStateFlow(false)
	val isAngleModeActive = _isAngleModeActive.asStateFlow()

	// 실시간으로 계산된 각도 (UI에서 관찰용)
	private val _currentManualAngle = MutableStateFlow(180f)
	val currentManualAngle = _currentManualAngle.asStateFlow()

	fun toggleAngleMode() {
		_isAngleModeActive.value = !_isAngleModeActive.value
	}

	fun updateManualAngle(angle: Float) {
		_currentManualAngle.value = angle
	}
}