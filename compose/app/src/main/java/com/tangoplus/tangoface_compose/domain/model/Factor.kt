package com.tangoplus.tangoface_compose.domain.model

import com.tangoplus.tangoface_compose.data.db.FaceShape
import com.tangoplus.tangoface_compose.data.db.FacialAxisShape

data class Factor(
	val facialAxis : FacialAxisShape,
	val primaryFaceShape: FaceShape,
	val secondaryFaceShape: FaceShape? = null,
	val saggingScale: Pair<Float, Float>,
)