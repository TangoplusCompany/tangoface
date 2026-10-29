package com.tangoplus.tangoface_compose.domain.model

data class DateDisplay(
	val fullDateTime: String,
	val displayDate: String
) {
	override fun toString(): String = displayDate
}