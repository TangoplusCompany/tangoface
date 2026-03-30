package com.tangoplus.facebeautyexpert.domain.vo

data class FaceStaticItem(
	val label: String,
	val value00: Float? = null,
	val value01: Float? = null,
	val value10: Float? = null,
	val value11: Float? = null,

	var isChecked: Boolean = false,
	var comment: String = ""

)