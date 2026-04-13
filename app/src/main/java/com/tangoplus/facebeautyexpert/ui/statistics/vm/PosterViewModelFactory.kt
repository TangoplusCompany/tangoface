package com.tangoplus.facebeautyexpert.ui.statistics.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tangoplus.facebeautyexpert.domain.util.PreferenceUtility

class PosterViewModelFactory(private val prefs: PreferenceUtility) : ViewModelProvider.Factory {
	override fun <T : ViewModel> create(modelClass: Class<T>): T {
		return PosterViewModel(prefs) as T
	}
}