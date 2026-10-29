package com.tangoplus.tangoface.ui.info.vo


data class BowItem(
	val tempServerSn: Int,
	val name: String? = null,
	val mobile: String? = null,
	val birthday: String? = null,
	val nationality: String? = null,
	val regDate: String = "",
	var activated: Int = 1
)