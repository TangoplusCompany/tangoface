package com.tangoplus.tangoface.domain.vision.pose

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

object PoseLandmarkAdapter {
	fun toCustomPoseLandmarkResult(poseLandmarkerResult: PoseLandmarkerResult): PoseLandmarkResult {
		val landmarks = poseLandmarkerResult.landmarks().firstOrNull()?.map { landmark ->
			PoseLandmarkResult.PoseLandmark(
				x = landmark.x(),
				y = landmark.y(),

				)
		} ?: emptyList()

		return PoseLandmarkResult(landmarks)
	}
}