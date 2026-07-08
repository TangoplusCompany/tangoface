package com.tangoplus.facebeautyexpert.domain.util

import org.json.JSONObject

object MediaUtility {
  fun extractVideoCoordinates(jsonData: JSONObject): List<List<Pair<Float, Float>>> {
    val faceLandmarks = jsonData.getJSONArray("face_landmark")

    return List(faceLandmarks.length()) { i ->
      val frameLandmarks = faceLandmarks.getJSONArray(i)

      List(frameLandmarks.length()) { j ->
        val landmark = frameLandmarks.getJSONArray(j)
        Pair(
          landmark.getDouble(1).toFloat(),  // x
          landmark.getDouble(2).toFloat()   // y
        )
      }
    }
  }


}