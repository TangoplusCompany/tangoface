package com.tangoplus.tangoface.data.vo

import com.tangoplus.tangoface.ui.main.vo.UserItem

data class FaceDisplayBasic(
	val userSn: Int,
	val tempServerSns: String,
	val userName: String?,
	val userMobile: String?,
	val userBirthday: String?,
	val userNationality: String,
	val userGender: Int,
	val regDate: String?,
	val faceType: Int?
) {
	fun toUserItem(): UserItem {
		return UserItem(
			userSn = userSn,
			tempServerSn = tempServerSns
				.split(",")
				.mapNotNull { it.toIntOrNull() },
			userName = userName,
			userMobile = userMobile,
			userBirthday = userBirthday,
			userNationality = userNationality,
			userGender = userGender,
			regDate = regDate?.split(","),
			faceType = faceType
		)
	}
}