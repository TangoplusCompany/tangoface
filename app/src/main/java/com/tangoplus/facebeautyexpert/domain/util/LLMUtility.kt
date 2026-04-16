package com.tangoplus.facebeautyexpert.domain.util

import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
import com.tangoplus.facebeautyexpert.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.String

object LLMUtility {
	suspend fun getGeminiInsight(results: JSONArray): String? {
		return withContext(Dispatchers.IO) {
			try {

				// 1. 모델 초기화 (AI Studio에서 확인한 flash 모델명을 적어주세요)
				val generativeModel = GenerativeModel(
					modelName = "gemini-3-flash-preview", // 또는 "gemini-1.5-flash"
					apiKey = BuildConfig.GEMINI_API_KEY
				)
				val jsonInput = mergedResults(results)
				// 2. 프롬프트 작성 (명확하게 포맷을 지정하는 것이 중요합니다)
				val prompt = """
			    너는 인공지능 얼굴 분석 전문가야. 정면 얼굴에서 나오는 데이터들을 통해 분석 insight를 적어줘.
			    아래의 JSON 데이터는 사용자의 얼굴을 정밀 분석한 수치들이야.
			    
			    [데이터 설명]
			    - 길이 데이터인 key명에 width, height, length, extent가 포함된 데이터가 0일 경우 측정되지 않은 데이터니 제외하고 분석해줘.
			    - 모든 각도는 Degree 단위야. 
			
			    [분석 데이터]
			    $jsonInput
			
			    [요청 사항]
			    1. 두괄식으로 구성해줘. 먼저 전체적인 분석 결과를  2~3줄 요약해줘.
			    2. 상안부, 중안부, 하안부 각각의 특징을 2마디 이내로 개조식 문체로 적고 문단은 이모지를 사용해서 만들어줘.
			    3. 수치적으로 가장 눈에 띄는 균형이나 특징이 있다면 1~2개만 짚어줘. 분석 데이터에 포함된 key값은 적지 말아줘.
			    4. 전체 12줄 이내로 작성해줘.
				""".trimIndent()
				// 3. API 요청 및 응답 받기
				val response = generativeModel.generateContent(prompt)

				// 4. 결과 텍스트 반환
				return@withContext response.text

			} catch (e: Exception) {
				e.printStackTrace()
				// 에러 발생 시 기본 텍스트 반환 (또는 null 반환 후 UI에서 에러 처리)
				return@withContext "인사이트를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
			}
		}
	}

	private fun mergedResults(statics: JSONArray): JSONObject {
		// 1. 첫 번째(정면)와 두 번째(목 확장)의 "data" 오브젝트만 추출
		val comfortData = statics.optJSONObject(0)?.optJSONObject("data") ?: JSONObject()
		val neckExtData = statics.optJSONObject(1)?.optJSONObject("data") ?: JSONObject()

		// 2. 결과물이 담길 새로운 JSON (comfortData를 복사해서 시작)
		val combinedJson = JSONObject(comfortData.toString())

		// 3. 필요 없는 메타데이터 삭제 (AI 분석에 방해되는 필드들)
		val excludeKeys = listOf(
			"local_sn",
			"server_sn",
			"temp_server_sn",
			"uuid",
			"user_sn",
			"name",
			"mobile",
			"birthday",
			"gender",
			"nationality",
			"seq",
			"media_file_name",
			"json_file_name",
			"reg_date",
			"scale_factor",
			"depth_result",

			"front_camera",
			"bow_eye_horizontal_angle",
			"bow_tip_of_lips_horizontal_angle",
			"bow_tip_of_chin_horizontal_angle",
			"comfort_nasal_wing_tip_of_lips_left_vertical_angle",
			"comfort_nasal_wing_tip_of_lips_right_vertical_angle",

			"resting_eye_horizontal_angle",
			"resting_eyebrow_horizontal_angle",
			"resting_tip_of_lips_horizontal_angle",
			"resting_tip_of_chin_horizontal_angle",
			"resting_canthus_oral_left_vertical_angle",
			"resting_canthus_oral_right_vertical_angle",
			"resting_nasal_wing_tip_of_lips_left_vertical_angle",
			"resting_nasal_wing_tip_of_lips_right_vertical_angle",
			"resting_side_head_left_angle",
			"resting_side_head_right_angle",
			"resting_left_cheeks_extent",
			"resting_right_cheeks_extent",
			"resting_left_cheekbone_extent",
			"resting_right_cheekbone_extent",

			"occlusal_eye_horizontal_angle",
			"occlusal_eyebrow_horizontal_angle",
			"occlusal_tip_of_lips_horizontal_angle",
			"occlusal_tip_of_chin_horizontal_angle",
			"occlusal_canthus_oral_left_vertical_angle",
			"occlusal_canthus_oral_right_vertical_angle",
			"occlusal_nasal_wing_tip_of_lips_left_vertical_angle",
			"occlusal_nasal_wing_tip_of_lips_right_vertical_angle",
			"occlusal_side_head_left_angle",
			"occlusal_side_head_right_angle",
			"occlusal_left_cheeks_extent",
			"occlusal_right_cheeks_extent",
			"occlusal_left_cheekbone_extent",
			"occlusal_right_cheekbone_extent",

			"jaw_left_tilt_nose_chin_vertical_angle",
			"jaw_left_tilt_tip_of_lips_horizontal_angle",
			"jaw_left_tilt_left_mandibular_distance",
			"jaw_left_tilt_right_mandibular_distance",

			"jaw_right_tilt_nose_chin_vertical_angle",
			"jaw_right_tilt_tip_of_lips_horizontal_angle",
			"jaw_right_tilt_left_mandibular_distance",
			"jaw_right_tilt_right_mandibular_distance",

			"jaw_opening_lips_distance",
			"jaw_opening_lips_vertical_angle",
			"jaw_opening_left_cheeks_extent",
			"jaw_opening_right_cheeks_extent",

			"side_left_shoulder_nose_vertical_angle",
			"side_left_shoulder_ear_vertical_angle",

			"side_right_shoulder_nose_vertical_angle",
			"side_right_shoulder_ear_vertical_angle",

			"activated"
		)
		excludeKeys.forEach { combinedJson.remove(it) }

		// 4. 목 확장 데이터에서 "neck_extention_" 필드만 골라서 추가
		val neckKeys = neckExtData.keys()
		while (neckKeys.hasNext()) {
			val key = neckKeys.next()
			if (key.startsWith("neck_extention_")) {
				combinedJson.put(key, neckExtData.get(key))
			}
		}

		return combinedJson
	}

//	fun FaceStatic.toGeminiData() = FaceGeminiData(
//		horizontal_ratio = this.horizontal_ratio,
//		vertical_ratio = this.vertical_ratio,
//
//		comfort_face_shape = this.comfort_face_shape,
//		comfort_left_sagging_scale = this.comfort_left_sagging_scale,
//		comfort_right_sagging_scale = this.comfort_right_sagging_scale,
//		comfort_length_symmetry_factor = this.comfort_length_symmetry_factor,
//		comfort_angle_symmetry_factor = this.comfort_angle_symmetry_factor,
//		comfort_facial_axis_shape = this.comfort_facial_axis_shape,
//		comfort_face_vertical_angle = this.comfort_face_vertical_angle, // 이마중심-턱중심 각도
//		comfort_neck_vertical_angle = this.neck_extention_neck_vertical_angle,
//		comfort_shoulder_horizontal_angle = this.comfort_shoulder_horizontal_angle,
//		comfort_face_width = this.comfort_face_width,
//		comfort_face_height = this.comfort_face_height,
//		//	val comfort_neck_width: Float = this.,
//		comfort_neck_height = this.comfort_neck_height,
//
//		// 상안부
//		comfort_eye_horizontal_angle = this.comfort_eye_horizontal_angle,
//
//		comfort_left_eye_horizontal_angle = this.comfort_left_eye_horizontal_angle,
//		comfort_right_eye_horizontal_angle = this.comfort_right_eye_horizontal_angle,
//		comfort_left_medial_canthus_angle = this.comfort_left_medial_canthus_angle,
//		comfort_right_medial_canthus_angle = this.comfort_right_medial_canthus_angle,
//		comfort_left_lateral_canthus_angle = this.comfort_left_lateral_canthus_angle,
//		comfort_right_lateral_canthus_angle = this.comfort_right_lateral_canthus_angle,
//
//		comfort_left_eye_width = this.comfort_left_eye_width,
//		comfort_right_eye_width = this.comfort_right_eye_width,
//		comfort_left_eye_height = this.comfort_left_eye_height,
//		comfort_right_eye_height = this.comfort_right_eye_height,
//		comfort_left_cornea_length = this.comfort_left_cornea_length,
//		comfort_right_cornea_length = this.comfort_right_cornea_length,
//
//		comfort_eyebrow_horizontal_angle = this.comfort_eyebrow_horizontal_angle,
//		comfort_left_eyebrow_horizontal_angle = this.comfort_left_eyebrow_horizontal_angle,
//		comfort_right_eyebrow_horizontal_angle = this.comfort_right_eyebrow_horizontal_angle,
//		comfort_left_eyebrow_start_length = this.comfort_left_eyebrow_start_length,
//		comfort_left_eyebrow_arch_length = this.comfort_left_eyebrow_arch_length,
//		comfort_left_eyebrow_end_length = this.comfort_left_eyebrow_end_length,
//		comfort_right_eyebrow_start_length = this.comfort_right_eyebrow_start_length,
//		comfort_right_eyebrow_arch_length = this.comfort_right_eyebrow_arch_length,
//		comfort_right_eyebrow_end_length = this.comfort_right_eyebrow_end_length,
//
//		// 중안부
//		comfort_nose_width = this.comfort_nose_width,
//		comfort_nose_height = this.comfort_nose_height,
//		comfort_earflaps_horizontal_angle = this.comfort_earflaps_horizontal_angle,
//		comfort_alar_horizontal_angle = this.comfort_alar_horizontal_angle,
//		comfort_left_alar_length = this.comfort_left_alar_length,
//		comfort_right_alar_length = this.comfort_right_alar_length,
//
//		// 하안부
//		comfort_tip_of_lips_horizontal_angle = this.comfort_tip_of_lips_horizontal_angle,
//		comfort_tip_of_chin_horizontal_angle = this.comfort_tip_of_chin_horizontal_angle,
//		comfort_canthus_oral_left_vertical_angle = this.comfort_canthus_oral_left_vertical_angle,
//		comfort_canthus_oral_right_vertical_angle = this.comfort_canthus_oral_right_vertical_angle,
//		comfort_alar_tip_of_lips_left_vertical_angle = this.comfort_alar_tip_of_lips_left_vertical_angle,
//		comfort_alar_tip_of_lips_right_vertical_angle = this.comfort_alar_tip_of_lips_right_vertical_angle,
//		comfort_side_head_left_angle = this.comfort_alar_tip_of_lips_right_vertical_angle,
//		comfort_side_head_right_angle = this.comfort_side_head_right_angle,
//		comfort_left_cheeks_extent = this.comfort_left_cheeks_extent,
//		comfort_right_cheeks_extent = this.comfort_right_cheeks_extent,
//		comfort_left_cheekbone_extent = this.comfort_left_cheekbone_extent,
//		comfort_right_cheekbone_extent = this.comfort_right_cheekbone_extent,
//		comfort_left_mouse_corner_angle = this.comfort_left_mouse_corner_angle,
//		comfort_right_mouse_corner_angle = this.comfort_right_mouse_corner_angle,
//		comfort_philtrum_length = this.comfort_philtrum_length,
//		comfort_mouse_width = this.comfort_mouse_width,
//		comfort_upper_lips_height = this.comfort_upper_lips_height,
//		comfort_lower_lips_height = this.comfort_lower_lips_height,
//
//
//		neck_extention_shoulder_horizontal_angle = this.neck_extention_shoulder_horizontal_angle,
//		neck_extention_ear_horizontal_angle = this.neck_extention_ear_horizontal_angle,
//		neck_extention_neck_vertical_angle = this.neck_extention_neck_vertical_angle,
//		neck_extention_face_vertical_angle = this.neck_extention_face_vertical_angle,
//		neck_extention_left_mandibular_chin_angle = this.neck_extention_left_mandibular_chin_angle,
//		neck_extention_right_mandibular_chin_angle = this.neck_extention_right_mandibular_chin_angle,
//		neck_extention_middle_mandibular_chin_angle = this.neck_extention_middle_mandibular_chin_angle,
//	)
//	fun FaceGeminiData?.toJsonString(): String = Gson().toJson(this)
}