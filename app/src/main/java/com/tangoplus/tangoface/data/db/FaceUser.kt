package com.tangoplus.tangoface.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
	tableName = "t_face_user",
	indices = [Index(value = ["uuid"], unique = true)]
)
data class FaceUser(
	@PrimaryKey(autoGenerate = true) val user_sn: Int = 0,
	var name: String? = "",
	var mobile: String? = "",
	var uuid: String = "",
	var birthday: String? = "",
	var nationality: String? = "",
	var userGender: Int? = 0,
	var faceType: Int? = null,
	var activated: Int = 1,
	var reg_date: String = "",
)
