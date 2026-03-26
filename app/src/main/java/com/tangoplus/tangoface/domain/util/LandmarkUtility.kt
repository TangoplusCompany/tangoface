package com.tangoplus.tangoface.domain.util

import com.tangoplus.tangoface.domain.vision.face.FaceLandmarkResult
import com.tangoplus.tangoface.domain.vision.pose.PoseLandmarkResult
import org.json.JSONObject
import kotlin.Pair

object LandmarkUtility {
	// 이미지 bitmap 스케일링
	fun scaleHsvToFHD(
		originalHsv: List<Pair<Float, Float>>?,
		originalWidth: Int = 1280,
		originalHeight: Int = 720,
		targetWidth: Int = 1920,
		targetHeight: Int = 1080
	): List<Pair<Float, Float>> {
		val scaleX = targetWidth.toFloat() / originalWidth
		val scaleY = targetHeight.toFloat() / originalHeight

		return originalHsv?.map { hsv ->
			Pair(
				hsv.first * scaleX,
				hsv.second * scaleY
			)
		} ?: emptyList()
	}

	fun scaleLandmarksToFHD(
		faceLandmarks: FaceLandmarkResult?,
		poseLandmarks: PoseLandmarkResult?,
		originalWidth: Int = 1280,
		originalHeight: Int = 720,
		targetWidth: Int = 1920,
		targetHeight: Int = 1080
	): Pair<FaceLandmarkResult?, PoseLandmarkResult?> {
		val scaleX = targetWidth.toFloat() / originalWidth
		val scaleY = targetHeight.toFloat() / originalHeight

		val scaledFace = faceLandmarks?.let { result ->
			FaceLandmarkResult(
				result.landmarks.map { l ->
					FaceLandmarkResult.FaceLandmark(
						x = l.x * scaleX,
						y = l.y * scaleY
					)
				}
			)
		}

		val scaledPose = poseLandmarks?.let { result ->
			PoseLandmarkResult(
				result.landmarks.map { l ->
					PoseLandmarkResult.PoseLandmark(
						x = l.x * scaleX,
						y = l.y * scaleY
					)
				}
			)
		}

		return Pair(scaledFace, scaledPose)
	}

	fun scalePointToFHD(
		point: Pair<Float, Float>?,
		originalWidth: Int = 1280,
		originalHeight: Int = 720,
		targetWidth: Int = 1920,
		targetHeight: Int = 1080
	): Pair<Float, Float>? {
		val scaleX = targetWidth.toFloat() / originalWidth
		val scaleY = targetHeight.toFloat() / originalHeight
		return  if (point != null) {
			Pair(
				point.first.times(scaleX),
				point.second.times(scaleY)
			)
		} else null
	}


	fun extractFaceCoordinates(jsonData: JSONObject?): List<Pair<Float, Float>>? {
		val poseData = jsonData?.optJSONArray("face_landmark")
		return if (poseData != null) {
			List(poseData.length()) { i ->
				val landmark = poseData.getJSONArray(i)
				Pair(
					landmark.optDouble(1).toFloat(),
					landmark.optDouble(2).toFloat()
				)
			}
		} else null
	}

	fun extractFaceLandmarkResult(jsonData: JSONObject?): FaceLandmarkResult {
		if (jsonData == null) return FaceLandmarkResult(emptyList())

		val poseData = jsonData.optJSONArray("face_landmark") ?: return FaceLandmarkResult(emptyList())

		val landmarks = mutableListOf<FaceLandmarkResult.FaceLandmark>()

		for (i in 0 until poseData.length()) {
			val landmark = poseData.optJSONArray(i) ?: continue

			// [index, x, y] 형태라고 가정
			if (landmark.length() < 3) continue

			val x = landmark.optDouble(1, Double.NaN)
			val y = landmark.optDouble(2, Double.NaN)

			if (x.isNaN() || y.isNaN()) continue

			landmarks.add(
				FaceLandmarkResult.FaceLandmark(
					x = x.toFloat(),
					y = y.toFloat()
				)
			)
		}

		return FaceLandmarkResult(landmarks)
	}

	fun extractFace3DCoordinates(jsonData: JSONObject?): List<Triple<Float, Float, Float>>? {
		val poseData = jsonData?.optJSONArray("face_landmark")
		return if (poseData != null) {
			List(poseData.length()) { i ->
				val landmark = poseData.getJSONArray(i)
				Triple(
					landmark.optDouble(3).toFloat(),
					landmark.optDouble(4).toFloat(),
					landmark.optDouble(5).toFloat()
				)
			}
		} else null
	}

	fun extractPoseCoordinates(jsonData: JSONObject?): List<Pair<Float, Float>>? {
		val data = jsonData?.optJSONArray("pose_landmark")
		return if (data != null) {
			List(data.length()) { i ->
				val landmark = data.getJSONObject(i)
				Pair(
					landmark.optDouble("sx").toFloat(),
					landmark.optDouble("sy").toFloat()
				)
			}
		} else null
	}

	fun extractBowCoordinates(jsonData: JSONObject?): List<Pair<Float, Float>>? {
		val data = jsonData?.optJSONArray("bow_landmark")
		return if (data != null) {
			List(data.length()) { i ->
				val landmark = data.getJSONObject(i)
				Pair(
					landmark.optDouble("cx").toFloat(),
					landmark.optDouble("cy").toFloat()
				)
			}
		} else null
	}
	fun extractHairLineCoordinate(jsonData: JSONObject?): Pair<Float, Float>? {
		val data = jsonData?.optJSONObject("etc_landmark")
		return if (data != null) {
			val dataJo = data.optJSONObject("hairline")
			Pair(
				dataJo?.optDouble("sx")?.toFloat() ?: 0f,
				dataJo?.optDouble("sy")?.toFloat() ?: 0f
			)
		} else null
	}
}