package com.tangoplus.tangoface.ui.statistics.vm

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {


	var currentTabIndex = MutableLiveData(0)

	var selectedFlicker = 2
}