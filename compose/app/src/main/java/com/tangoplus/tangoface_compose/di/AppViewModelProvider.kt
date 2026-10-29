package com.tangoplus.tangoface_compose.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tangoplus.tangoface_compose.orbbec.application.MyApplication
import com.tangoplus.tangoface_compose.ui.main.vm.MainViewModel
import com.tangoplus.tangoface_compose.ui.measure.MeasureViewModel

/**
 * Compose 화면에서 ViewModel에 의존성을 자동 주입하는 Provider
 */
object AppViewModelProvider {
	val Factory: ViewModelProvider.Factory = viewModelFactory {
		initializer {
			val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MyApplication)
			MeasureViewModel(
				faceRepository = app.container.faceRepository,
				getMeasurementHistoryUseCase = app.container.getMeasurementHistoryUseCase,
				saveFaceStaticUseCase = app.container.saveFaceStaticUseCase
			)
		}
		initializer {
			MainViewModel()
		}
	}
}

fun CreationExtras.appContainer(): AppContainer =
	(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MyApplication).container

