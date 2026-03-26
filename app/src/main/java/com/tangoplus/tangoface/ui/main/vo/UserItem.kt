package com.tangoplus.tangoface.ui.main.vo

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserItem(
	val userSn: Int,
	val tempServerSn: List<Int>,
	val userName: String? = null,
	val userMobile: String? = null,
	val userBirthday: String? = null,
	val userNationality: String? = null,
	val userGender: Int? = null,
	var regDate: List<String>? = null,
	var faceType: Int? = null
) : Parcelable