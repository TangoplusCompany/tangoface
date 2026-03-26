package com.tangoplus.tangoface.ui.settings.vm

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tangoplus.tangoface.domain.util.PreferenceUtility
import com.tangoplus.tangoface.domain.vo.FaceType
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.ui.settings.vo.SeqCategory

class SequenceSelectViewModel : ViewModel() {
	val allSeq = Sequence.entries.toList().filter { it != Sequence.BOW }

	var savedSeqs = setOf<Sequence>()
	var fragmentCase = 0

	/* 0 -> MainFragment -> 그냥 activity 동작
	*  1 -> UserFragment -> User intent에 담아서 동작
	* */
	private lateinit var prefs: PreferenceUtility

	fun createPrefs(context: Context) {
		prefs = PreferenceUtility(context)
	}

	private val _tempSelectedSeq = MutableLiveData<Set<Sequence>>(Sequence.entries.toSet())
	val tempSelectedSeq: LiveData<Set<Sequence>> = _tempSelectedSeq
	fun setTempSelectedSeq(seqs: Set<Sequence>) {
		_tempSelectedSeq.value = seqs
	}

	fun clearTempSelectedSeq() {
		_tempSelectedSeq.value = emptySet()
	}

	fun saveSelectedSeq() {
		_tempSelectedSeq.value?.let { prefs.setLastMeasureSeqs(it) }
	}

	fun getSelectedSeq(): Set<Sequence> {
		return prefs.getLastMeasureSeqs()
	}

	fun initMoreSeqs() {
		val seqs = getSelectedSeq()
		_tempSelectedSeq.value = seqs
		savedSeqs = seqs
	}

	private val _isChanged = MutableLiveData<Boolean>(false)
	val isChanged: LiveData<Boolean> get() = _isChanged
	fun onTempSelectionChanged() {
		checkIfChanged()
	}

	private fun checkIfChanged() {
		_isChanged.value = tempSelectedSeq != savedSeqs
	}

	private val linkedSeqMap = mapOf(
		Sequence.JAW_LEFT_TILT to listOf(Sequence.JAW_RIGHT_TILT),
		Sequence.JAW_RIGHT_TILT to listOf(Sequence.JAW_LEFT_TILT),
		Sequence.SIDE_LEFT to listOf(Sequence.SIDE_RIGHT),
		Sequence.SIDE_RIGHT to listOf(Sequence.SIDE_LEFT)
	)

	fun addSeqWithLinked(seq: Sequence) {
		val currentSet = _tempSelectedSeq.value?.toMutableSet() ?: mutableSetOf()
		if (seq !in currentSet) {
			currentSet.add(seq)
			linkedSeqMap[seq]?.forEach { linkedSeq ->
				if (linkedSeq !in currentSet) {
					currentSet.add(linkedSeq)
				}
			}
			_tempSelectedSeq.value = currentSet
		}
	}

	fun removeSeqWithLinked(seq: Sequence) {
		val currentSet = _tempSelectedSeq.value?.toMutableSet() ?: mutableSetOf()
		if (seq in currentSet) {
			currentSet.remove(seq)
			linkedSeqMap[seq]?.forEach { linkedSeq ->
				currentSet.remove(linkedSeq)
			}

			_tempSelectedSeq.value = currentSet
		}
	}

	// -------------# face type #---------------
	val allFaceType = FaceType.entries.toList()

	// 역순으로 faceType일 때, ssvm에 담기는 livedata들이 init 됨 (temp먼저 -> origin)
	val tempSelectedFaceType = MutableLiveData<FaceType>()
	var originFaceType = MutableLiveData<FaceType>()

	// ---------------# Seq Category #-------------
	val allSeqCategory = SeqCategory.entries.toList()
	val selectedSeqCategory = MutableLiveData<SeqCategory>(SeqCategory.CUSTOM)

	fun getStandardSequences(category: SeqCategory): Set<Sequence> {
		return when (category) {
			SeqCategory.TMJ_STATE -> setOf(
				Sequence.RESTING,
				Sequence.OCCLUSAL,
				Sequence.JAW_LEFT_TILT,
				Sequence.JAW_RIGHT_TILT,
				Sequence.JAW_OPENING,
				Sequence.NECK_EXTENTION
			)

			SeqCategory.PILATES -> setOf(
				Sequence.COMFORT,
				Sequence.RESTING,
				Sequence.OCCLUSAL,
				Sequence.SIDE_LEFT,
				Sequence.SIDE_RIGHT
			)

			SeqCategory.CUSTOM -> emptySet()
			else -> emptySet()
		}
	}

	private var isCategorySelection = false // CUSTOM을 눌렀을 때는
	fun onSeqCategorySelectionChanged() {
		val currentCategory = selectedSeqCategory.value ?: return

		// CUSTOM은 체크하지 않음
		if (currentCategory == SeqCategory.CUSTOM) return

		val standardSeq = getStandardSequences(currentCategory)

		// 순서 상관없이 내용물만 비교 (순서도 중요하면 == 사용)
		if (tempSelectedSeq.value?.toSet() != standardSeq.toSet()) {
			isCategorySelection = false
			selectedSeqCategory.value = SeqCategory.CUSTOM
		}
	}

	// 원상복귀 체크 (CUSTOM에서 다시 규격에 맞으면 자동 전환)
	fun checkAndRestoreCategory() {
		if (isCategorySelection) return
		if (selectedSeqCategory.value != SeqCategory.CUSTOM) return

		// TMJ_STATE 규격과 일치하는지 확인
		if (tempSelectedSeq.value?.toSet() == getStandardSequences(SeqCategory.TMJ_STATE).toSet()) {
			selectedSeqCategory.value = SeqCategory.TMJ_STATE
			return
		}

		// PILATES 규격과 일치하는지 확인
		if (tempSelectedSeq.value?.toSet() == getStandardSequences(SeqCategory.PILATES).toSet()) {
			selectedSeqCategory.value = SeqCategory.PILATES
			return
		}
	}

	fun onCategoryCustomSelected(category: SeqCategory) {
		isCategorySelection = (category == SeqCategory.CUSTOM)
		selectedSeqCategory.value = category
	}

}