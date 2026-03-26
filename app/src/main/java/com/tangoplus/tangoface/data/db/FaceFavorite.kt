package com.tangoplus.tangoface.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "t_face_favorite")
data class FaceFavorite(
	@PrimaryKey(autoGenerate = true) val local_sn: Int = 0,
	var temp_server_sn: Int = 0,
	var user_sn: Int = 0,
	var title: String = "",
	var reg_date: String = ""
)