package com.tangoplus.tangoface_compose.domain.usecase

import com.tangoplus.tangoface_compose.domain.model.UserItem
import com.tangoplus.tangoface_compose.domain.repository.FaceRepository

class GetMeasurementHistoryUseCase(
	private val repository: FaceRepository
) {
	suspend operator fun invoke(): List<UserItem> {
		return repository.getUserItems()
	}
}

