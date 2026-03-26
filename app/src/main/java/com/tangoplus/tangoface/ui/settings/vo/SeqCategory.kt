package com.tangoplus.tangoface.ui.settings.vo

enum class SeqCategory(val displayName: String) {
	CUSTOM("사용자 지정"),
	TMJ_STATE("턱관절 분석"),
	PILATES("필라테스");

	override fun toString(): String {
		return displayName
	}
}