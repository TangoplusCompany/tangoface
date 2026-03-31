package com.tangoplus.facebeautyexpert.ui.statistics.vm

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangoplus.facebeautyexpert.data.db.FaceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel : ViewModel() {


	var currentTabIndex = MutableLiveData(0)

	var selectedFlicker = 2
}