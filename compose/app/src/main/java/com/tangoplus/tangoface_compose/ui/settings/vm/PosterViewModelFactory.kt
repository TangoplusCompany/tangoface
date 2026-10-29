package com.tangoplus.tangoface_compose.ui.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tangoplus.tangoface_compose.domain.util.PreferenceUtility

class PosterViewModelFactory(private val prefs: PreferenceUtility) : ViewModelProvider.Factory {
	override fun <T : ViewModel> create(modelClass: Class<T>): T {
		return PosterViewModel(prefs) as T
	}
}