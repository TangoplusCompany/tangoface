package com.tangoplus.tangoface.data.db

enum class FacialAxisShape(val displayName: String) {
	NORMAL("정상"),
	C_ASYMMETRY("C형 비대칭"),
	S_ASYMMETRY("S형 비대칭"),
	OFF_CENTER_AXIS("중심축 이탈"),
	DEFAULT("미설정");

	override fun toString(): String {
		return displayName
	}
}