package com.tangoplus.tangoface.ui.measure.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tangoplus.tangoface.domain.vo.PreCheckList

class OnBoardingViewModel : ViewModel() {
	private val checkList = PreCheckList.entries.toList()

	fun getCheckList0() = checkList.subList(0, 7)
	fun getCheckList1() = checkList.subList(7, 13)
	fun getCheckList2() = checkList.subList(13, 20)
	fun getCheckList3() = checkList.subList(20, checkList.size)

	private val _selectedCheckList = MutableLiveData<Set<PreCheckList>>(emptySet())
	val selectedCheckList: LiveData<Set<PreCheckList>> = _selectedCheckList
	fun addCheckedItem(item: PreCheckList) {
		val currentList = _selectedCheckList.value.orEmpty()
		if (!currentList.contains(item)) {
			_selectedCheckList.value = currentList + item
		}
	}

	fun removeCheckedItem(item: PreCheckList) {
		val currentList = _selectedCheckList.value.orEmpty()
		if (currentList.contains(item)) {
			_selectedCheckList.value = currentList - item
		}
	}

	fun clearCheckedItem() {
		_selectedCheckList.value = emptySet()
	}

	fun getCheckedItem(): Set<PreCheckList> {
		return selectedCheckList.value ?: setOf()
	}

	fun toPreCheckListEntity(
		selectedItems: Set<PreCheckList>,
		tempServerSn: Int,
		uuid: String,
		name: String,
		mobile: String,
		gender: Int,
		birthday: String,
		nationality: String,
	): com.tangoplus.tangoface.data.db.PreCheckList {
		fun PreCheckList.isChecked() = selectedItems.contains(this)
		return com.tangoplus.tangoface.data.db.PreCheckList(
			temp_server_sn = tempServerSn,
			FACE_ASYMMETRY = if (PreCheckList.FACE_ASYMMETRY.isChecked()) 1 else 0,
			FACE_SIZE = if (PreCheckList.FACE_SIZE.isChecked()) 1 else 0,
			HEAD_SIZE = if (PreCheckList.HEAD_SIZE.isChecked()) 1 else 0,
			CHEEKBONE_SIZE = if (PreCheckList.CHEEKBONE_SIZE.isChecked()) 1 else 0,
			SQUARE_JAW = if (PreCheckList.SQUARE_JAW.isChecked()) 1 else 0,
			FLAT_FACE = if (PreCheckList.FLAT_FACE.isChecked()) 1 else 0,

			UNEVEN_EYEBROWS = if (PreCheckList.UNEVEN_EYEBROWS.isChecked()) 1 else 0,
			EYE_TILT = if (PreCheckList.EYE_TILT.isChecked()) 1 else 0,
			TEAR_TROUGH = if (PreCheckList.TEAR_TROUGH.isChecked()) 1 else 0,
			EYE_DISTANCE = if (PreCheckList.EYE_DISTANCE.isChecked()) 1 else 0,
			WIDE_FOREHEAD = if (PreCheckList.WIDE_FOREHEAD.isChecked()) 1 else 0,
			NARROW_FOREHEAD = if (PreCheckList.NARROW_FOREHEAD.isChecked()) 1 else 0,

			MIDFACE_LENGTH = if (PreCheckList.MIDFACE_LENGTH.isChecked()) 1 else 0,
			CHEEK_ASYMMETRY = if (PreCheckList.CHEEK_ASYMMETRY.isChecked()) 1 else 0,
			CHEEK_HOLLOW = if (PreCheckList.CHEEK_HOLLOW.isChecked()) 1 else 0,
			NASOLABIAL_FOLD = if (PreCheckList.NASOLABIAL_FOLD.isChecked()) 1 else 0,
			LOW_BRIDGE_NOSE = if (PreCheckList.LOW_BRIDGE_NOSE.isChecked()) 1 else 0,
			BULBOUS_NOSE_TIP = if (PreCheckList.BULBOUS_NOSE_TIP.isChecked()) 1 else 0,
			CROOKED_NOSE = if (PreCheckList.CROOKED_NOSE.isChecked()) 1 else 0,

			UNEVEN_LIPS = if (PreCheckList.UNEVEN_LIPS.isChecked()) 1 else 0,
			DROOPY_MOUTH_CORNER = if (PreCheckList.DROOPY_MOUTH_CORNER.isChecked()) 1 else 0,
			LONG_PHILTRUM = if (PreCheckList.LONG_PHILTRUM.isChecked()) 1 else 0,
			WEAK_JAWLINE = if (PreCheckList.WEAK_JAWLINE.isChecked()) 1 else 0,
			JAW_ASYMMETRY = if (PreCheckList.JAW_ASYMMETRY.isChecked()) 1 else 0,
			MOUTH_HOLLOW = if (PreCheckList.MOUTH_HOLLOW.isChecked()) 1 else 0,
			PROGNATHISM = if (PreCheckList.PROGNATHISM.isChecked()) 1 else 0,
			RETROGNATHIA = if (PreCheckList.RETROGNATHIA.isChecked()) 1 else 0,
		)
	}

}