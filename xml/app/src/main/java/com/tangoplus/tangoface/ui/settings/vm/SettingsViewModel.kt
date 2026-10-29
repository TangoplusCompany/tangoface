package com.tangoplus.tangoface.ui.settings.vm

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {


	var currentTabIndex = MutableLiveData(0)

	var selectedFlicker = 2
}