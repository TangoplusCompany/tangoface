package com.tangoplus.tangoface.domain.util

import com.tangoplus.tangoface.domain.vo.FaceType

object FaceTypeUtility {
	fun getIntFromFaceType(faceType: FaceType?): Int {
		return when (faceType) {
			FaceType.EGG -> 0
			FaceType.RHOMBUS -> 1
			FaceType.HEART -> 2
			FaceType.PEANUT -> 3
			FaceType.HEXAGON -> 4
			FaceType.CIRCLE -> 5
			null -> -1
		}
	}

	fun getFaceTypeFromInt(faceIndex: Int?): FaceType? {
		return when (faceIndex) {
			0 -> FaceType.EGG
			1 -> FaceType.RHOMBUS
			2 -> FaceType.HEART
			3 -> FaceType.PEANUT
			4 -> FaceType.HEXAGON
			5 -> FaceType.CIRCLE
			else -> null
		}
	}
}