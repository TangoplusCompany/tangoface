package com.tangoplus.facebeautyexpert.ui.info.vo

enum class FacialPart(val displayName: String) {
	RATIO("얼굴비율"),
	DERIVE("얼굴분석"),
	TOP("상안부"),
	MIDDLE("중안부"),
	BOTTOM("하안부");

	override fun toString(): String = displayName

}