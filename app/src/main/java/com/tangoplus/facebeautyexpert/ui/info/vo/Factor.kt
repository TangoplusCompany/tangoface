package com.tangoplus.facebeautyexpert.ui.info.vo

import com.tangoplus.facebeautyexpert.data.db.FaceShape
import com.tangoplus.facebeautyexpert.data.db.FacialAxisShape

data class Factor(
	val facialAxis : FacialAxisShape,
	val primaryFaceShape: FaceShape,
	val secondaryFaceShape: FaceShape? = null,
	val saggingScale: Pair<Float, Float>,
)