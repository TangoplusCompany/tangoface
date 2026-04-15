package com.tangoplus.facebeautyexpert.ui.main.vo

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
data class UserItem(
	val tempServerSn: Int,
	val name: String? = null,
	val mobile: String? = null,
	val birthday: String? = null,
	val nationality: String? = null,
	val gender: Int? = null,
	val regDate: String = "",
	val insight: String  ="",
	var activated: Int = 1
) : Parcelable