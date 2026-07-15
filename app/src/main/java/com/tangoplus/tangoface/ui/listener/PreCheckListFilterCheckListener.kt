package com.tangoplus.tangoface.ui.listener

import com.tangoplus.tangoface.ui.statistics.vo.PreCheckListFilter

interface PreCheckListFilterCheckListener {
    fun preCheckListFilterCheck(item: PreCheckListFilter, isChecked: Boolean)
}