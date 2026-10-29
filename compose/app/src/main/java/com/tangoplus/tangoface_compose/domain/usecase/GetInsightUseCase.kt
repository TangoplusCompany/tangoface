package com.tangoplus.tangoface_compose.domain.usecase

import com.tangoplus.tangoface_compose.data.db.FaceInsight
import com.tangoplus.tangoface_compose.domain.repository.FaceRepository

class GetInsightUseCase(
	private val repository: FaceRepository
) {
	suspend operator fun invoke(serverSn: Int): FaceInsight? {
		return repository.getInsight(serverSn)
	}

	suspend fun save(insight: FaceInsight): Long {
		return repository.insertInsight(insight)
	}
}

