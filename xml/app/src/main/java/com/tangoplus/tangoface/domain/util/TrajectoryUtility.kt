package com.tangoplus.tangoface.domain.util

import android.graphics.PointF
import com.tangoplus.tangoface.domain.vo.Sequence

object TrajectoryUtility {
	// 좌표 기반 턱 특징점 검출
	fun detectJawKeyPoints(
		points: List<PointF>,
		isLeft: Boolean,
		seq: Sequence, // 0: 좌측(x값 감소), 1: 우측(x값 증가)
		trimPercent: Float = 0.10f,      // 전후 15% 제거

	): List<Int> {
		if (points.size < 10) return emptyList()

		// 1. 전후 15% 제거
		val trimCount = (points.size * trimPercent).toInt()
		val workingPoints = points.subList(trimCount, points.size - trimCount)
		val startOffset = trimCount

		val keyPoints = mutableListOf<Int>()


		val extremeXIndex = when (seq) {
			Sequence.JAW_LEFT_TILT -> {
				if (isLeft) workingPoints.indices.minByOrNull { workingPoints[it].x }
				else workingPoints.indices.maxByOrNull { workingPoints[it].x }
			}

			Sequence.JAW_RIGHT_TILT -> workingPoints.indices.minByOrNull { workingPoints[it].x }
//			4 -> workingPoints.indices.maxByOrNull { workingPoints[it].x }
			else -> workingPoints.indices.maxByOrNull { workingPoints[it].x }
		}

		if (extremeXIndex != null) keyPoints.add(extremeXIndex + startOffset)

		val maxYIndex = workingPoints.indices.maxByOrNull { workingPoints[it].y }
		if (maxYIndex != null) {
			keyPoints.add(maxYIndex + startOffset)
		}

		return keyPoints.sorted()
	}
}