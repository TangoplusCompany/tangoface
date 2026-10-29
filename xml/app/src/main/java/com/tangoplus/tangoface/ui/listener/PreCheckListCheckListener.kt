package com.tangoplus.tangoface.ui.listener

import com.tangoplus.tangoface.domain.vo.PreCheckList


interface PreCheckListCheckListener {
    fun preCheckListCheck(item: PreCheckList, isChecked: Boolean)
}