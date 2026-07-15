package com.tangoplus.tangoface.domain.vision.face

import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

object FaceLandmarkAdapter {
	fun toCustomFaceLandmarkResult(faceLandmarkerResult: FaceLandmarkerResult): FaceLandmarkResult {
		val landmarks = faceLandmarkerResult.faceLandmarks().firstOrNull()?.map { landmark ->
			FaceLandmarkResult.FaceLandmark(
				x = landmark.x(),
				y = landmark.y(),

				)
		} ?: emptyList()

		return FaceLandmarkResult(landmarks)
	}
}