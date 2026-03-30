package com.tangoplus.facebeautyexpert.domain.vo

import android.net.Uri
import org.json.JSONArray

data class FaceResult(
	val tempServerSn: Int,
	val userName: String? = null,
	val userMobile: String? = null,
	val mediaUri: List<Uri?> = listOf(),
	val results: JSONArray = JSONArray(),
	val depthResult: Boolean = false,
	val seqs: Set<Sequence>,
	var regDate: String? = null
)