package com.tangoplus.tangoface.ui.info.vo

import com.tangoplus.tangoface.data.db.FaceShape
import com.tangoplus.tangoface.data.db.FacialAxisShape

data class Factor(
	val facialAxis : FacialAxisShape,
	val primaryFaceShape: FaceShape,
	val secondaryFaceShape: FaceShape? = null,
	val saggingScale: Pair<Float, Float>,
)