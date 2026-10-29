package com.tangoplus.tangoface_compose.domain.util

import org.json.JSONArray
import org.json.JSONObject

fun JSONArray.clearAll() {
	while (this.length() > 0) {
		this.remove(0)
	}
}

fun JSONObject.clearAll() {
	val keys = this.keys()
	val toRemove = mutableListOf<String>()

	while (keys.hasNext()) {
		toRemove.add(keys.next())
	}

	for (key in toRemove) {
		this.remove(key)
	}
}

fun JSONArray.copyFrom(other: JSONArray) {
	this.clearAll()
	for (i in 0 until other.length()) {
		this.put(other.get(i))
	}
}
