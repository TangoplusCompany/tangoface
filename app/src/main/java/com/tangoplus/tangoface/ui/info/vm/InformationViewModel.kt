package com.tangoplus.tangoface.ui.info.vm

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tangoplus.tangoface.domain.vo.FaceStaticItem
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.ui.info.vo.DrawLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InformationViewModel : ViewModel() {
	val currentCheckedLines = mutableSetOf<DrawLine>()

	// 체크가 가능한 전체 데이터 VO 항목 담는 곳
	var currentFaceStaticsItem = mutableListOf<FaceStaticItem>()

	// mvm 과 다르게 information Fragment에서 현재 선택된 Sequence를 담는 변수
	private var currentSeq = Sequence.COMFORT
	fun setSeq(seq: Sequence) {
		currentSeq = seq
	}

	fun getSeq(): Sequence {
		return currentSeq
	}
	var coordinates1: List<List<Pair<Float, Float>>>? = null
	var coordinates2: List<List<Pair<Float, Float>>>? = null
	var leftTrajectory: List<Pair<Float, Float>>? = null
	var rightTrajectory: List<Pair<Float, Float>>? = null

	private var isLineOn = true
	fun switchedLineOn() { isLineOn = !isLineOn }
	fun getLineOn(): Boolean = isLineOn

	private var isGridLineOn = true
	fun switchedGridLineOn() { isGridLineOn = !isGridLineOn }
	fun getGridLineOn(): Boolean = isGridLineOn

	private var isMoireOn = false
	fun switchedMoireOn() { isMoireOn = !isMoireOn }
	fun getMoireOn(): Boolean = isMoireOn
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

	private var cropRatio = Pair(0f, 0f)

	// 1. 비율 설정 함수
	fun setCropRatio(width: Int, height: Int) {
		if (width > 0 && height > 0) {
			// 전처리 비율로 사용할 수 있도록 Float Pair로 저장
			cropRatio = Pair(width.toFloat(), height.toFloat())
		}
	}
	fun getCropRatio() : Pair<Float, Float> = cropRatio


	var videoCoordinates: List<List<Pair<Float, Float>>>? = null
	var videoTrajectory: List<Pair<Float, Float>>? = null
}