package com.tangoplus.tangoface_compose.domain.model

import java.io.Serializable

/**
 * 탱고페이스 측정 모드 정의
 * - NORMAL, EXPERT, BOW, SCAN: 카메라 기반 측정
 * - IMAGE: 다이얼로그 업로드 기반 측정
 * - LAB_3D_SCAN: 추후 확장될 실험실 3D 스캔 기능
 */
enum class MeasureType(
	val idx: Int,
	val displayName: String,
	val isCameraBased: Boolean
) : Serializable {
	NORMAL(0, "간편 검사", isCameraBased = true),
	EXPERT(1, "정밀 검사", isCameraBased = true),
	BOW(2, "페이스 보우", isCameraBased = true),
	IMAGE(3, "사진 검사", isCameraBased = false),
	SCAN(4, "3D 스캔", isCameraBased = true);

	override fun toString(): String = displayName

	companion object {
		fun fromIdx(idx: Int?): MeasureType? {
			return entries.find { it.idx == idx } ?: NORMAL
		}
	}
}

