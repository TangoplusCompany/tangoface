package com.tangoplus.tangoface_compose.domain.model

sealed class HistoryMappingItem {
	data class Header(val date: String) : HistoryMappingItem()
	data class Item(val data: UserItem) : HistoryMappingItem()
}
