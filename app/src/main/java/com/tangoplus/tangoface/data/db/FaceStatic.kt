package com.tangoplus.tangoface.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Entity(tableName = "t_face_static")
data class FaceStatic(
	@PrimaryKey(autoGenerate = true) val local_sn: Int = 0,
	val server_sn: Int = 0,
	var temp_server_sn: Int = 0,
	var uuid: String = "",
	var user_sn: Int = 0,
	var name: String = "",
	var mobile: String = "",
	var seq: Int = 0,
	var media_file_name: String = "", // 확장자 까지
	var json_file_name: String = "", // 확장자 까지
	val reg_date: String? = getCurrentDateTime(),
	val scale_factor: Float = 1f,
	val depth_result: Boolean = false,
	val front_camera: Boolean = true,
	var activated: Int = 1, // 삭제 필터링 flag column
	val bow_eye_horizontal_angle: Float = 0f,
	val bow_tip_of_lips_horizontal_angle: Float = 0f,
	val bow_tip_of_chin_horizontal_angle: Float = 0f,

	val comfort_eye_horizontal_angle: Float = 0f,
	val comfort_eyebrow_horizontal_angle: Float = 0f,
	val comfort_tip_of_lips_horizontal_angle: Float = 0f,
	val comfort_tip_of_chin_horizontal_angle: Float = 0f,
	val comfort_canthus_oral_left_vertical_angle: Float = 0f,
	val comfort_canthus_oral_right_vertical_angle: Float = 0f,
	val comfort_nasal_wing_tip_of_lips_left_vertical_angle: Float = 0f,
	val comfort_nasal_wing_tip_of_lips_right_vertical_angle: Float = 0f,
	val comfort_side_head_left_angle: Float = 0f,
	val comfort_side_head_right_angle: Float = 0f,
	val comfort_left_cheeks_extent: Float = 0f,
	val comfort_right_cheeks_extent: Float = 0f,
	val comfort_left_cheekbone_extent: Float = 0f,
	val comfort_right_cheekbone_extent: Float = 0f,
	val comfort_neck_vertical_angle: Float = 0f,
	val comfort_face_vertical_angle: Float = 0f,

	val neck_extention_shoulder_horizontal_angle: Float = 0f,
	val neck_extention_ear_horizontal_angle: Float = 0f,
	val neck_extention_neck_vertical_angle: Float = 0f,
	val neck_extention_face_vertical_angle: Float = 0f,
	val neck_extention_left_mandibular_chin_angle: Float = 0f,
	val neck_extention_right_mandibular_chin_angle: Float = 0f,
	val neck_extention_middle_mandibular_chin_angle: Float = 0f,

) {
	companion object {
		fun getCurrentDateTime(): String =
			LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
	}
}