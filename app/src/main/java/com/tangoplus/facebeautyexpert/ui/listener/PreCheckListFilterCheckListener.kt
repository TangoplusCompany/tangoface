package com.tangoplus.facebeautyexpert.ui.listener

import com.tangoplus.facebeautyexpert.ui.statistics.vo.PreCheckListFilter

interface PreCheckListFilterCheckListener {
    fun preCheckListFilterCheck(item: PreCheckListFilter, isChecked: Boolean)
}