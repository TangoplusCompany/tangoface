package com.tangoplus.tangoface.ui.login

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LoginViewModel : ViewModel() {
	val currentEmailCon = MutableLiveData(false)
	val fullEmail = MutableLiveData("")
	val currentPwCon = MutableLiveData(false)
	val fullPassword = MutableLiveData("")
	val emailPwCondition = MediatorLiveData<Boolean>().apply {
		value = false
		addSource(currentEmailCon) { condition ->
			value = condition && (currentPwCon.value ?: false)
		}
		addSource(currentPwCon) { compare ->
			value = (currentEmailCon.value ?: false) && compare
		}
	}

	// 회원가입에서만 쓰는 비밀번호 정리
	val pwCondition = MutableLiveData(false)
	val pwCompare = MutableLiveData(false)
	val regNumCondition = MutableLiveData(false)

	val fullRegNum = MutableLiveData("")

	val signInAllCondition = MediatorLiveData<Boolean>().apply {
		fun update() {
			value = (currentEmailCon.value == true) &&
				(pwCondition.value == true) &&
				(pwCompare.value == true) &&
				(regNumCondition.value == true)
		}
		addSource(currentEmailCon) { update() }
		addSource(pwCondition) { update() }
		addSource(pwCompare) { update() }
		addSource(regNumCondition) { update() }
	}

}