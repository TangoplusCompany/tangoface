package com.tangoplus.facebeautyexpert.data.db

enum class MidLowerFaceShape(val displayName: String) {
	NORMAL("정상"),
	C_ASYMMETRY("C형 비대칭"),
	S_ASYMMETRY("S형 비대칭"),
	OFF_CENTER_AXIS("중심축 이탈");


	override fun toString(): String {
		return displayName
	}

}