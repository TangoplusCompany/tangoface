package com.tangoplus.tangoface.ui.measure.vm

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class InputViewModel : ViewModel() {
	// user 에서 넘어왔을 시, userItem에서 까서 5개 세팅
	val nameCondition = MutableLiveData(false)
	val nameValue = MutableLiveData("")
	val mobileCondition = MutableLiveData(false)
	val mobileValue = MutableLiveData("")
	val genderCondition = MutableLiveData(false)
	val genderValue = MutableLiveData(-1)
	val birthdayCondition = MutableLiveData(false)
	val birthdayValue = MutableLiveData("날짜 선택")

	val nationalityValue = MutableLiveData("")

	var isShownBtn = false
	var isFinishInput = MutableLiveData(false)

	private fun checkAllInputConditions(): Boolean {
		return nameCondition.value == true &&
			mobileCondition.value == true &&
			genderCondition.value == true &&
			birthdayCondition.value == true
	}

	val inputCondition = MediatorLiveData<Boolean>().apply {
		value = false
		val sources = listOf(nameCondition, mobileCondition, genderCondition, birthdayCondition)
		sources.forEach { source ->
			addSource(source) {
				value = checkAllInputConditions()
			}
		}
	}
}