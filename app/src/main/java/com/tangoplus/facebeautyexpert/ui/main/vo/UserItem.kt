package com.tangoplus.facebeautyexpert.ui.main.vo

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


// TODO UserItem은 이제 MeasureHistory에서 보여주는 Data Class로 수정 ( 이름, 휴대폰 번호, 나이, grade 등)
@Parcelize
data class UserItem(
	val userSn: Int,
	val tempServerSn: Int,
	val userName: String? = null,
	val userMobile: String? = null,
	val userBirthday: String? = null,
	val userNationality: String? = null,
	val userGender: Int? = null,
	val agingIndex : Int? = null,
	val symmetryIndex: Int? = null,
	val score: String? = null
) : Parcelable