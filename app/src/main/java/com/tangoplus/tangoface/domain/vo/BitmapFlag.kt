package com.tangoplus.tangoface.domain.vo

data class BitmapFlag(
	val isDepthCamera: Boolean,
	val isLineOn: Boolean,
	val isFrontCamera: Boolean = true,
)
