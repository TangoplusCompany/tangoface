package com.tangoplus.tangoface.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "t_memo")
data class Memo(
	@PrimaryKey(autoGenerate = true) val local_sn: Int = 0,
	var user_sn: Int = 0,
	var memo: String = "",
	var reg_date: String = ""
)