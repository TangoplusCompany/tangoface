package com.tangoplus.tangoface_compose.domain.model

import java.io.Serializable

data class BowItem(
	val tempServerSn: Int,
	val name: String? = null,
	val mobile: String? = null,
	val birthday: String? = null,
	val nationality: String? = null,
	val regDate: String = "",
	var activated: Int = 1
) : Serializable

