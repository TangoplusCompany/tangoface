package com.tangoplus.tangoface.ui.info.vo

import com.tangoplus.tangoface.ui.main.vo.UserItem

sealed class HistoryMappingItem {
	data class Header(val date: String) : HistoryMappingItem()
	data class Item(val data: UserItem) : HistoryMappingItem()
}