package com.tangoplus.facebeautyexpert.domain.vo

import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkResult
import com.tangoplus.facebeautyexpert.domain.vision.pose.PoseLandmarkResult

data class Landmarks (
	val faceLandmarks: FaceLandmarkResult,
	val face3DLandmarks: List<Triple<Float, Float, Float>>?,
	val poseLandmarks: PoseLandmarkResult,
	val pose3DLandmarks: List<Triple<Float, Float, Float>>?,
	val bowLandmarks: List<Pair<Float, Float>>?,
	val hairLineLandmarks: List<Pair<Float, Float>>?,
	val hairLine3DLandmarks: List<Triple<Float, Float, Float>>?,
)