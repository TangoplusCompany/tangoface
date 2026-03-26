package com.tangoplus.tangoface.ui.listener

import com.tangoplus.tangoface.ui.settings.vo.PreCheckListFilter

interface PreCheckListFilterCheckListener {
    fun preCheckListFilterCheck(item: PreCheckListFilter, isChecked: Boolean)
}