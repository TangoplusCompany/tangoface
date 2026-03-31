package com.tangoplus.facebeautyexpert.ui.main.vo

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


// TODO UserItem은 이제 MeasureHistory에서 보여주는 Data Class로 수정 ( 이름, 휴대폰 번호, 나이, grade 등)
@Parcelize
data class UserItem(
	val tempServerSn: Int,
	val name: String? = null,
	val mobile: String? = null,
	val birthday: String? = null,
	val nationality: String? = null,
	val gender: Int? = null,
	val regDate: String = "",
	var activated: Int = 1
) : Parcelable