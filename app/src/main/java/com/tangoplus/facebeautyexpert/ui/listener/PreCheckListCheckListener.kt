package com.tangoplus.facebeautyexpert.ui.listener

import com.tangoplus.facebeautyexpert.domain.vo.PreCheckList


interface PreCheckListCheckListener {
    fun preCheckListCheck(item: PreCheckList, isChecked: Boolean)
}