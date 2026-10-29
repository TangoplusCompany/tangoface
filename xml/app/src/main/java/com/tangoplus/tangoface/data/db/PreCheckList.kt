package com.tangoplus.tangoface.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Entity(tableName = "t_face_pre_checklist")
data class PreCheckList(

	@PrimaryKey(autoGenerate = true) val local_sn: Int = 0,
	val server_sn: Int = 0,
	var temp_server_sn: Int = 0,
	var gender: Int = -1, //
	var birthday: String? = "",
	var nationality: String = "", // 국적 (ex KOR) 예정
	val reg_date: String? = getCurrentDateTime(),
	val FACE_ASYMMETRY: Int = 0,
	val FACE_SIZE: Int = 0,
	val HEAD_SIZE: Int = 0,
	val CHEEKBONE_SIZE: Int = 0,
	val SQUARE_JAW: Int = 0,
	val PEANUT_FACE: Int = 0,
	val FLAT_FACE: Int = 0,

	val UNEVEN_EYEBROWS: Int = 0,
	val EYE_TILT: Int = 0,
	val TEAR_TROUGH: Int = 0,
	val EYE_DISTANCE: Int = 0,
	val WIDE_FOREHEAD: Int = 0,
	val NARROW_FOREHEAD: Int = 0,

	val MIDFACE_LENGTH: Int = 0,
	val CHEEK_ASYMMETRY: Int = 0,
	val CHEEK_HOLLOW: Int = 0,
	val NASOLABIAL_FOLD: Int = 0,
	val LOW_BRIDGE_NOSE: Int = 0,
	val BULBOUS_NOSE_TIP: Int = 0,
	val CROOKED_NOSE: Int = 0,

	val UNEVEN_LIPS: Int = 0,
	val DROOPY_MOUTH_CORNER: Int = 0,
	val LONG_PHILTRUM: Int = 0,
	val WEAK_JAWLINE: Int = 0,
	val JAW_ASYMMETRY: Int = 0,
	val MOUTH_HOLLOW: Int = 0,
	val PROGNATHISM: Int = 0,
	val RETROGNATHIA: Int = 0,
	) {
	companion object {
		fun getCurrentDateTime(): String =
			LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
	}
}