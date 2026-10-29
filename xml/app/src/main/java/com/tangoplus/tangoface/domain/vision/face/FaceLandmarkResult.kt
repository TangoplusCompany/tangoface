package com.tangoplus.tangoface.domain.vision.face

data class FaceLandmarkResult(val landmarks: List<FaceLandmark>) {
	data class FaceLandmark(
		var x: Float,
		var y: Float
	)

	companion object {
		fun fromFaceCoordinates(coordinates: List<Pair<Float, Float>>?): FaceLandmarkResult {
			val landmarks = coordinates?.map { (x, y) ->
				FaceLandmark(x, y)
			}
			return if (landmarks != null) FaceLandmarkResult(landmarks) else FaceLandmarkResult(listOf())
		}
	}

}