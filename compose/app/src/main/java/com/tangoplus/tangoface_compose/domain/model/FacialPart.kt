package com.tangoplus.tangoface_compose.domain.model

enum class FacialPart(val displayName: String) {
	DERIVE("얼굴분석"),
	TOP("상안부"),
	MIDDLE("중안부"),
	BOTTOM("하안부"),
	RATIO("얼굴비율");

	override fun toString(): String = displayName

}