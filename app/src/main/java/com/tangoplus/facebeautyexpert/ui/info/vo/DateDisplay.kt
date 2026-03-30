package com.tangoplus.facebeautyexpert.ui.info.vo

data class DateDisplay(
	val fullDateTime: String,
	val displayDate: String
) {
	override fun toString(): String = displayDate
}