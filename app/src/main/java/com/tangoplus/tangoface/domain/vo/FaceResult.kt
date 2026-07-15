package com.tangoplus.tangoface.domain.vo

import android.net.Uri
import com.tangoplus.tangoface.ui.info.vo.Factor
import org.json.JSONArray

data class FaceResult(
	val tempServerSn: Int,
	val userName: String? = null,
	val userMobile: String? = null,
	val mediaUri: List<Uri?> = listOf(),
	val moireUri: List<Uri?> = listOf(),
	val results: JSONArray = JSONArray(),
	val depthResult: Boolean = false,
	val seqs: Set<Sequence>,
	val factors: Factor,
	var insight: String,
	var regDate: String? = null,
	var measureType : Int,
	var bowSns : List<Int> = listOf()
)