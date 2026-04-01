package com.tangoplus.facebeautyexpert.ui.info.vm

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tangoplus.facebeautyexpert.domain.vo.FaceStaticItem
import com.tangoplus.facebeautyexpert.domain.vo.Sequence
import com.tangoplus.facebeautyexpert.ui.info.vo.DrawLine

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
	fun switchedLineOn() {
		isLineOn = !isLineOn
	}

	fun getLineOn(): Boolean = isLineOn

	val unitIdxFromFaceResult = MutableLiveData(Pair(0, 0))
	val unitIdxFromCompare = MutableLiveData(Pair(0, 0))

	fun releaseUnitIdx() {
		unitIdxFromFaceResult.value = Pair(0, 0)
		unitIdxFromCompare.value = Pair(0, 0)
	}
}