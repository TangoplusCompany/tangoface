package com.tangoplus.tangoface_compose.domain.usecase

import com.tangoplus.tangoface_compose.data.db.FaceStatic
import com.tangoplus.tangoface_compose.domain.repository.FaceRepository

class SaveFaceStaticUseCase(
	private val repository: FaceRepository
) {
	suspend operator fun invoke(faceStatic: FaceStatic): Long {
		return repository.insertStatic(faceStatic)
	}
}

