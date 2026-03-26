package com.tangoplus.tangoface.domain.vo

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class Sequence(val displayName: String) : Parcelable {
	COMFORT("편한 상태"),
	RESTING("정면 상태"),
	OCCLUSAL("교합 상태"),
	JAW_LEFT_TILT("턱 가측 이동(왼쪽)"),
	JAW_RIGHT_TILT("턱 가측 이동(오른쪽)"),
	JAW_OPENING("개구 상태"),
	NECK_EXTENTION("목폄 상태"),
	SIDE_LEFT("왼쪽 상태"),
	SIDE_RIGHT("오른쪽 상태"),
	BOW("페이스 보우");


	override fun toString(): String = displayName

	companion object {
		fun fromDisplayName(displayName: String): Sequence? {
			return Sequence.entries.find { it.displayName == displayName }
		}

		fun fromIndex(index: Int): Sequence? {
			return Sequence.entries.getOrNull(index)
		}
	}
}