package com.tangoplus.facebeautyexpert.domain.vo

data class BitmapFlag(
	val isDepthCamera: Boolean,
	val isLineOn: Boolean,
	val isFrontCamera: Boolean = true,
)
