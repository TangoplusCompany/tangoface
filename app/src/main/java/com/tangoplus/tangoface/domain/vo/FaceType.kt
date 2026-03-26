package com.tangoplus.tangoface.domain.vo

enum class FaceType(val displayName: String) {
	EGG("계란형"),
	RHOMBUS("마름모형"),
	HEART("하트형"),
	PEANUT("땅콩형"),
	HEXAGON("육각형"),
	CIRCLE("둥근형");

	override fun toString(): String {
		return displayName
	}
}