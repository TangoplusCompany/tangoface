package com.tangoplus.tangoface.ui.info.vm

import androidx.lifecycle.ViewModel
import com.tangoplus.tangoface.domain.vo.MeasureType
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.ui.info.vo.FacialPart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SelectViewModel: ViewModel() {
	val facialHorizonSelect = FacialPart.entries.toList()

	private val _currentFacialSelect = MutableStateFlow(FacialPart.DERIVE)
	val currentFacialPart = _currentFacialSelect.asStateFlow()

	fun updateFacialPart(part: FacialPart) {
		_currentFacialSelect.value = part
	}


	val sequenceEntries = Sequence.entries.toList()

	// 현재 선택된 Sequence 상태 관리
	private val _currentSequence = MutableStateFlow(Sequence.COMFORT)
	val currentSequence = _currentSequence.asStateFlow()




	// 선택 변경 시 호출할 함수
	fun updateSequence(seq: Sequence) {
		_currentSequence.value = seq
	}


	// 🏳️‍🌈🏳️‍🌈INFO-EXPERT에서 사용하는 Seq 선택 🏳️‍🌈🏳️‍🌈
	private val sequenceMap = mapOf(
		MeasureType.NORMAL to listOf(Sequence.COMFORT, Sequence.NECK_EXTENTION),
		MeasureType.EXPERT to Sequence.entries.filter { it != Sequence.BOW },
		MeasureType.BOW to listOf(Sequence.BOW)
	)
	private var allSeqs : List<Sequence> = emptyList()
	fun getAllSeqs() : List<Sequence> = allSeqs
  fun setAllSeqs(measureType: MeasureType) {
		allSeqs =  sequenceMap[measureType] ?: emptyList()
	}

}