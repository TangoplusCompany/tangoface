package com.tangoplus.tangoface.ui.info.vo

data class DateDisplay(
	val fullDateTime: String,
	val displayDate: String
) {
	override fun toString(): String = displayDate
}