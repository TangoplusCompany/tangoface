package com.tangoplus.facebeautyexpert.domain.util

import com.tangoplus.facebeautyexpert.data.db.PreCheckList
import com.tangoplus.facebeautyexpert.ui.statistics.vm.ChartViewModel

import com.tangoplus.facebeautyexpert.ui.statistics.vo.PreCheckListFilter
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

object PreCheckListUtility {
	fun getAge(birthday: String): Int {
		return try {
			val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
			val birthDate = LocalDate.parse(birthday, formatter)
			Period.between(birthDate, LocalDate.now()).years
		} catch (e: Exception) {
			0
		}
	}

	fun filteredChecklist(cvm: ChartViewModel): List<PreCheckList> {
		val cpf = cvm.currentCheckedPreCheckListFilter
		val allData = cvm.currentPreCheckList

		// 필터링 결과
		var filtered = allData.toList()

		// --------# 연령대 필터 #--------
		if (cpf.any { it.name.startsWith("AGE_") }) {
			filtered = filtered.filter { item ->
				val age = getAge(item.birthday ?: "")
				cpf.any { filter ->
					when (filter) {
						PreCheckListFilter.AGE_0_20 -> age < 20
						PreCheckListFilter.AGE_20_25 -> age in 20 until 25
						PreCheckListFilter.AGE_25_30 -> age in 25 until 30
						PreCheckListFilter.AGE_30_35 -> age in 30 until 35
						PreCheckListFilter.AGE_35_40 -> age in 35 until 40
						PreCheckListFilter.AGE_40_50 -> age in 40 until 50
						PreCheckListFilter.AGE_50_60 -> age in 50 until 60
						PreCheckListFilter.AGE_60_ -> age >= 60
						else -> false  // AGE_ 필터가 아닌 경우 무시
					}
				}
			}
		}

		// ----------# 성별 필터 #----------
		if (cpf.contains(PreCheckListFilter.GENDER_M) || cpf.contains(PreCheckListFilter.GENDER_F)) {
			filtered = filtered.filter { item ->
				when (item.gender) {
					0 -> cpf.contains(PreCheckListFilter.GENDER_M) // 남성
					1 -> cpf.contains(PreCheckListFilter.GENDER_F) // 여성
					else -> false
				}
			}
		}

		// ----------#  국적 필터 #--------------
		if (cpf.any { it.name.startsWith("NATIONALITY_") }) {
			filtered = filtered.filter { item ->
				when {
					cpf.contains(PreCheckListFilter.NATIONALITY_KOR) -> item.nationality == "82"
					cpf.contains(PreCheckListFilter.NATIONALITY_EU) -> item.nationality in listOf(
						"33",
						"34",
						"36",
						"39",
						"40",
						"41",
						"43",
						"44",
						"45",
						"46",
						"47",
						"48",
						"49",
						"351",
						"352",
						"353",
						"354",
						"355",
						"358",
						"359",
						"370",
						"380",
						"381",
						"385"
					)

					cpf.contains(PreCheckListFilter.NATIONALITY_ASIA) -> item.nationality in listOf(
						"81",
						"84",
						"60",
						"62",
						"63",
						"65",
						"66",
						"86",
						"852",
						"853",
						"856",
						"880",
						"91",
						"92",
						"94",
						"95",
						"960",
						"971",
						"64",
						"61",
					)

					cpf.contains(PreCheckListFilter.NATIONALITY_AMERICA) -> item.nationality in listOf(
						"1",
						"52",
						"54",
						"55",
						"56",
						"57",
						"58",
						"507",
						"591",
						"593",
						"595",
						"598"
					)

					cpf.contains(PreCheckListFilter.NATIONALITY_ARAB_AFRICA) -> item.nationality in listOf(
						"20",
						"27",
						"212",
						"213",
						"216",
						"218",
						"249",
						"254",
						"966",
						"971",
						"974",
						"968"
					)

					cpf.contains(PreCheckListFilter.NATIONALITY_ETC) -> item.nationality !in listOf(
						"82", "33", "34", "36", "39", "40", "41", "43", "44", "45", "46", "47", "48",
						"49", "351", "352", "353", "354", "355", "358", "359", "370", "380", "381",
						"385", "81", "84", "60", "62", "63", "65", "66", "86", "852", "853", "856",
						"880", "91", "92", "94", "95", "960", "971", "64", "61", "1", "52", "54",
						"55", "56", "57", "58", "507", "591", "593", "595", "598", "20", "27", "212",
						"213", "216", "218", "249", "254", "966", "971", "974", "968"
					) // 기타는 필터 미포함 시 나머지
					else -> true
				}
			}
		}

		return filtered
	}

}