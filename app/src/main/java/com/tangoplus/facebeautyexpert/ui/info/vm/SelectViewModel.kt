package com.tangoplus.facebeautyexpert.ui.info.vm

import androidx.lifecycle.ViewModel
import com.tangoplus.facebeautyexpert.ui.info.vo.FacialPart

class SelectViewModel: ViewModel() {
	val facialHorizonSelect = FacialPart.entries.toList()

}