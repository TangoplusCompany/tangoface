package com.tangoplus.facebeautyexpert.data.db

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
	var birthday: String? = "",
	var gender: Int = -1, //
	var nationality: String = "",
	var seq: Int = 0,
	var media_file_name: String = "", // 확장자 까지
	var json_file_name: String = "", // 확장자 까지
	val reg_date: String? = getCurrentDateTime(),
	val scale_factor: Float = 1f,
	val depth_result: Boolean = false,

	// 🍿🍿🍿🍿🍿🍿 현재 사용하지 않는 칼럼들 🍿🍿🍿🍿🍿🍿🍿
	val front_camera: Boolean = true,
	val bow_eye_horizontal_angle: Float = 0f,
	val bow_tip_of_lips_horizontal_angle: Float = 0f,
	val bow_tip_of_chin_horizontal_angle: Float = 0f,
	val comfort_nasal_wing_tip_of_lips_left_vertical_angle: Float = 0f,
	val comfort_nasal_wing_tip_of_lips_right_vertical_angle: Float = 0f,

	val resting_eye_horizontal_angle: Float = 0f,
	val resting_eyebrow_horizontal_angle: Float = 0f,
	val resting_tip_of_lips_horizontal_angle: Float = 0f,
	val resting_tip_of_chin_horizontal_angle: Float = 0f,
	val resting_canthus_oral_left_vertical_angle: Float = 0f,
	val resting_canthus_oral_right_vertical_angle: Float = 0f,
	val resting_nasal_wing_tip_of_lips_left_vertical_angle: Float = 0f,
	val resting_nasal_wing_tip_of_lips_right_vertical_angle: Float = 0f,
	val resting_side_head_left_angle: Float = 0f,
	val resting_side_head_right_angle: Float = 0f,
	val resting_left_cheeks_extent: Float = 0f,
	val resting_right_cheeks_extent: Float = 0f,
	val resting_left_cheekbone_extent: Float = 0f,
	val resting_right_cheekbone_extent: Float = 0f,

	val occlusal_eye_horizontal_angle: Float = 0f,
	val occlusal_eyebrow_horizontal_angle: Float = 0f,
	val occlusal_tip_of_lips_horizontal_angle: Float = 0f,
	val occlusal_tip_of_chin_horizontal_angle: Float = 0f,
	val occlusal_canthus_oral_left_vertical_angle: Float = 0f,
	val occlusal_canthus_oral_right_vertical_angle: Float = 0f,
	val occlusal_nasal_wing_tip_of_lips_left_vertical_angle: Float = 0f,
	val occlusal_nasal_wing_tip_of_lips_right_vertical_angle: Float = 0f,
	val occlusal_side_head_left_angle: Float = 0f,
	val occlusal_side_head_right_angle: Float = 0f,
	val occlusal_left_cheeks_extent: Float = 0f,
	val occlusal_right_cheeks_extent: Float = 0f,
	val occlusal_left_cheekbone_extent: Float = 0f,
	val occlusal_right_cheekbone_extent: Float = 0f,

	val jaw_left_tilt_nose_chin_vertical_angle: Float = 0f,
	val jaw_left_tilt_tip_of_lips_horizontal_angle: Float = 0f,
	val jaw_left_tilt_left_mandibular_distance: Float = 0f,
	val jaw_left_tilt_right_mandibular_distance: Float = 0f,

	val jaw_right_tilt_nose_chin_vertical_angle: Float = 0f,
	val jaw_right_tilt_tip_of_lips_horizontal_angle: Float = 0f,
	val jaw_right_tilt_left_mandibular_distance: Float = 0f,
	val jaw_right_tilt_right_mandibular_distance: Float = 0f,

	val jaw_opening_lips_distance: Float = 0f,
	val jaw_opening_lips_vertical_angle: Float = 0f,
	val jaw_opening_left_cheeks_extent: Float = 0f,
	val jaw_opening_right_cheeks_extent: Float = 0f,


	val side_left_shoulder_nose_vertical_angle: Float = 0f,
	val side_left_shoulder_ear_vertical_angle: Float = 0f,

	val side_right_shoulder_nose_vertical_angle: Float = 0f,
	val side_right_shoulder_ear_vertical_angle: Float = 0f,
	// 🍿🍿🍿🍿🍿🍿 현재 사용하지 않는 칼럼들 🍿🍿🍿🍿🍿🍿🍿

	// 얼굴 전체
	val horizontal_ratio: String = "",
	val vertical_ratio: String = "",

	val comfort_face_shape: FaceShape = FaceShape.DEFAULT,
	val comfort_left_sagging_scale: Float = 0f,
	val comfort_right_sagging_scale: Float = 0f,
	val comfort_length_symmetry_factor : Float = 0f,
	val comfort_angle_symmetry_factor: Float = 0f,
	val comfort_facial_axis_shape: FacialAxisShape = FacialAxisShape.DEFAULT,
	val comfort_face_vertical_angle: Float = 0f, // 이마중심-턱중심 각도 // ✅
	val comfort_neck_vertical_angle: Float = 0f, // ✅
	val comfort_shoulder_horizontal_angle: Float = 0f, // ✅
	val comfort_face_length: Float = 0f, // ✅
	val comfort_neck_length: Float = 0f, // ✅

	// 상안부
	val comfort_eye_horizontal_angle: Float = 0f, // ✅

	val comfort_left_eye_horizontal_angle: Float = 0f, // ✅
	val comfort_right_eye_horizontal_angle: Float = 0f, // ✅
	val comfort_left_medial_canthus_angle : Float = 0f, // ✅
	val comfort_right_medial_canthus_angle : Float = 0f, // ✅
	val comfort_left_lateral_canthus_angle: Float = 0f, // ✅
	val comfort_right_lateral_canthus_angle: Float = 0f, // ✅

	val comfort_left_eye_width: Float = 0f, // ✅
	val comfort_right_eye_width: Float = 0f, // ✅
	val comfort_left_eye_height: Float = 0f, // ✅
	val comfort_right_eye_height: Float = 0f, // ✅
	val comfort_left_cornea_length: Float = 0f, // ✅
	val comfort_right_cornea_length: Float = 0f, // ✅

	val comfort_eyebrow_horizontal_angle: Float = 0f,
	val comfort_left_eyebrow_horizontal_angle: Float = 0f, // ✅
	val comfort_right_eyebrow_horizontal_angle: Float = 0f, // ✅
	val comfort_left_eyebrow_start_length: Float = 0f, // ✅
	val comfort_left_eyebrow_arch_length: Float = 0f, // ✅
	val comfort_left_eyebrow_end_length: Float = 0f, // ✅
	val comfort_right_eyebrow_start_length: Float = 0f, // ✅
	val comfort_right_eyebrow_arch_length: Float = 0f, // ✅
	val comfort_right_eyebrow_end_length: Float = 0f, // ✅

	// 중안부
	val comfort_nose_width: Float = 0f, // ✅
	val comfort_nose_height: Float = 0f, // ✅
	val comfort_earflaps_horizontal_angle : Float = 0f, // ✅
	val comfort_alar_horizontal_angle: Float = 0f,
	val comfort_left_alar_length : Float = 0f, // ✅
	val comfort_right_alar_length: Float = 0f, // ✅

	// 하안부
	val comfort_tip_of_lips_horizontal_angle: Float = 0f, // ✅
	val comfort_tip_of_chin_horizontal_angle: Float = 0f, // ✅
	val comfort_canthus_oral_left_vertical_angle: Float = 0f, // ✅
	val comfort_canthus_oral_right_vertical_angle: Float = 0f, // ✅
	val comfort_alar_tip_of_lips_left_vertical_angle: Float = 0f, // ✅
	val comfort_alar_tip_of_lips_right_vertical_angle: Float = 0f, // ✅
	val comfort_side_head_left_angle: Float = 0f, // ✅ 관자놀이 각도
	val comfort_side_head_right_angle: Float = 0f, // ✅
	val comfort_left_cheeks_extent: Float = 0f, // ✅
	val comfort_right_cheeks_extent: Float = 0f, // ✅
	val comfort_left_cheekbone_extent: Float = 0f, // ✅
	val comfort_right_cheekbone_extent: Float = 0f, // ✅
	val comfort_left_mouse_corner_angle: Float = 0f, // ✅
	val comfort_right_mouse_corner_angle: Float = 0f, // ✅
	val comfort_philtrum_length : Float = 0f, // ✅
	val comfort_mouse_width: Float = 0f, // ✅
	val comfort_upper_lips_height: Float = 0f, // ✅
	val comfort_lower_lips_height: Float = 0f, // ✅


	val neck_extention_shoulder_horizontal_angle: Float = 0f,
	val neck_extention_ear_horizontal_angle: Float = 0f,
	val neck_extention_neck_vertical_angle: Float = 0f,
	val neck_extention_face_vertical_angle: Float = 0f,
	val neck_extention_left_mandibular_chin_angle: Float = 0f,
	val neck_extention_right_mandibular_chin_angle: Float = 0f,
	val neck_extention_middle_mandibular_chin_angle: Float = 0f,
	var activated: Int = 1, // 삭제 필터링 flag column
	) {
	companion object {
		fun getCurrentDateTime(): String =
			LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
	}
}