package com.tangoplus.tangoface_compose.di

import android.content.Context
import com.tangoplus.tangoface_compose.data.db.FaceDatabase
import com.tangoplus.tangoface_compose.data.repository.FaceRepositoryImpl
import com.tangoplus.tangoface_compose.domain.repository.FaceRepository
import com.tangoplus.tangoface_compose.domain.usecase.GetInsightUseCase
import com.tangoplus.tangoface_compose.domain.usecase.GetMeasurementHistoryUseCase
import com.tangoplus.tangoface_compose.domain.usecase.SaveFaceStaticUseCase

/**
 * 앱 전역 의존성 주입(DI) 컨테이너
 */
interface AppContainer {
	val faceRepository: FaceRepository
	val getMeasurementHistoryUseCase: GetMeasurementHistoryUseCase
	val saveFaceStaticUseCase: SaveFaceStaticUseCase
	val getInsightUseCase: GetInsightUseCase
}

class DefaultAppContainer(private val context: Context) : AppContainer {

	private val database: FaceDatabase by lazy {
		FaceDatabase.getDatabase(context)
	}

	override val faceRepository: FaceRepository by lazy {
		FaceRepositoryImpl(faceDao = database.faceDao())
	}

	override val getMeasurementHistoryUseCase: GetMeasurementHistoryUseCase by lazy {
		GetMeasurementHistoryUseCase(faceRepository)
	}

	override val saveFaceStaticUseCase: SaveFaceStaticUseCase by lazy {
		SaveFaceStaticUseCase(faceRepository)
	}

	override val getInsightUseCase: GetInsightUseCase by lazy {
		GetInsightUseCase(faceRepository)
	}
}

