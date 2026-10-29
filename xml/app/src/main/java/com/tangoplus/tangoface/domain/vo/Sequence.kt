package com.tangoplus.tangoface.domain.vo

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class Sequence(val id: Int, val displayName: String) : Parcelable {
	COMFORT(0, "편한 상태"),
	NECK_EXTENTION(6, "목폄 상태"),
  OCCLUSAL(1, "교합 상태"),
  JAW_LEFT_TILT(2, "턱 가측 이동(왼쪽)"),
  JAW_RIGHT_TILT(3, "턱 가측 이동(오른쪽)"),
  JAW_OPENING(4, "개구 상태"),

	BOW(5, "페이스 보우");

	override fun toString(): String = displayName

	companion object {
		fun fromDisplayName(displayName: String): Sequence? {
			return entries.find { it.displayName == displayName }
		}

		// 🕊️ 고유 ID(6 등)로 실제 Enum 객체를 찾는 함수 추가
		fun fromId(id: Int): Sequence? {
			return entries.find { it.id == id }
		}

		fun fromIndex(index: Int): Sequence? {
			return entries.getOrNull(index)
		}
	}
}