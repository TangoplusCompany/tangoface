package com.tangoplus.facebeautyexpert.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "t_face_insight")
data class FaceInsight (
	@PrimaryKey(autoGenerate = true) val local_sn : Int = 0,
	val server_sn: Int = 0,
	val insight: String = "",
)