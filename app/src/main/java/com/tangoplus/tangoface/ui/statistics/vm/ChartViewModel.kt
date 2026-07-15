package com.tangoplus.tangoface.ui.statistics.vm

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.PieEntry
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.data.db.PreCheckList
import com.tangoplus.tangoface.domain.util.PreCheckListUtility.filteredChecklist
import com.tangoplus.tangoface.ui.statistics.vo.PreCheckListFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChartViewModel : ViewModel() {

	private val _preCheckCounts = MutableLiveData<Map<String, Int>>()
	val preCheckCounts: LiveData<Map<String, Int>> = _preCheckCounts

	val fieldAccessors: List<Pair<String, (PreCheckList) -> Int>> = listOf(
		"FACE_ASYMMETRY" to { it.FACE_ASYMMETRY },
		"FACE_SIZE" to { it.FACE_SIZE },
		"HEAD_SIZE" to { it.HEAD_SIZE },
		"CHEEKBONE_SIZE" to { it.CHEEKBONE_SIZE },
		"SQUARE_JAW" to { it.SQUARE_JAW },
		"PEANUT_FACE" to { it.PEANUT_FACE },
		"FLAT_FACE" to { it.FLAT_FACE },

		"UNEVEN_EYEBROWS" to { it.UNEVEN_EYEBROWS },
		"EYE_TILT" to { it.EYE_TILT },
		"TEAR_TROUGH" to { it.TEAR_TROUGH },
		"EYE_DISTANCE" to { it.EYE_DISTANCE },
		"WIDE_FOREHEAD" to { it.WIDE_FOREHEAD },
		"NARROW_FOREHEAD" to { it.NARROW_FOREHEAD },

		"MIDFACE_LENGTH" to { it.MIDFACE_LENGTH },
		"CHEEK_ASYMMETRY" to { it.CHEEK_ASYMMETRY },
		"CHEEK_HOLLOW" to { it.CHEEK_HOLLOW },
		"NASOLABIAL_FOLD" to { it.NASOLABIAL_FOLD },
		"LOW_BRIDGE_NOSE" to { it.LOW_BRIDGE_NOSE },
		"BULBOUS_NOSE_TIP" to { it.BULBOUS_NOSE_TIP },
		"CROOKED_NOSE" to { it.CROOKED_NOSE },

		"UNEVEN_LIPS" to { it.UNEVEN_LIPS },
		"DROOPY_MOUTH_CORNER" to { it.DROOPY_MOUTH_CORNER },
		"LONG_PHILTRUM" to { it.LONG_PHILTRUM },
		"WEAK_JAWLINE" to { it.WEAK_JAWLINE },
		"JAW_ASYMMETRY" to { it.JAW_ASYMMETRY },
		"MOUTH_HOLLOW" to { it.MOUTH_HOLLOW },
		"PROGNATHISM" to { it.PROGNATHISM },
		"RETROGNATHIA" to { it.RETROGNATHIA }
	)

	// -----------# 체크항목에 맞게 Count 수정 #-----------
	fun filteredCount() {
		val counts = mutableMapOf<String, Int>()
		val filtered = filteredChecklist(this)
		for ((fieldName, accessor) in fieldAccessors) {
			val count = filtered.count { checklist ->
				(accessor(checklist) as Int) > 0
			}
			counts[fieldName] = count
		}

		Log.v("_preCheckCounts", "✅ 최종 counts: $counts")
		_preCheckCounts.postValue(counts)
	}

	val currentPreCheckList = mutableListOf<PreCheckList>()
	fun loadPreCheckListFromDB(context: Context) {
		viewModelScope.launch(Dispatchers.IO) {
			val dao = FaceDatabase.getDatabase(context).faceDao()
			val allData = dao.getAllCheckListData()

			currentPreCheckList.clear()
			currentPreCheckList.addAll(allData)

			Log.v("allPreCheckList", "$allData")

			val counts = mutableMapOf<String, Int>()
			for ((fieldName, accessor) in fieldAccessors) {
				val count = currentPreCheckList.count { checklist ->
					(accessor(checklist) as Int) > 0
				}
				counts[fieldName] = count
			}

			Log.v("_preCheckCounts", "✅ 최종 counts: $counts")
			_preCheckCounts.postValue(counts)
			setPieDatas()
		}
	}

	val currentCheckedPreCheckListFilter = mutableSetOf<PreCheckListFilter>()
	val allPreChecklistFilter = PreCheckListFilter.entries.toList()
	fun getCheckListFilter0() = allPreChecklistFilter.subList(0, 8)
	fun getCheckListFilter1() = allPreChecklistFilter.subList(8, 14)
	fun getCheckListFilter2() = allPreChecklistFilter.subList(14, 16)
	fun getCheckListFilter3() = allPreChecklistFilter.subList(0, allPreChecklistFilter.size)


	// pieChart에 들어갈 arrayList 4개 (상한부/중안부/하안부 고민 비율), (나이) (성별) (국적),
	private val pieDatas = mutableListOf<ArrayList<PieEntry>>()
	fun getPieDatas(): MutableList<ArrayList<PieEntry>> = pieDatas
	fun setPieDatas() {
		if (pieDatas.isNotEmpty()) return

		pieDatas.add(countFaceParts(currentPreCheckList))
		pieDatas.add(countAgeGroup(currentPreCheckList))
//		pieDatas.add(countGender((currentPreCheckList)))
//		pieDatas.add(countNationality(currentPreCheckList))
		pieDatas.map { it -> Log.v("파이차트확인", "$it") }
	}

	fun countFaceParts(data: List<PreCheckList>): ArrayList<PieEntry> {
		var whole = 0
		var upper = 0
		var mid = 0
		var lower = 0

		for (item in data) {
			// 각 그룹별 항목 합산 (0 또는 1로 저장되어 있다고 가정)
			whole += with(item) { FACE_ASYMMETRY + FACE_SIZE + HEAD_SIZE + CHEEKBONE_SIZE + SQUARE_JAW + PEANUT_FACE + FLAT_FACE }
			upper += with(item) { UNEVEN_EYEBROWS + EYE_TILT + TEAR_TROUGH + EYE_DISTANCE + WIDE_FOREHEAD + NARROW_FOREHEAD }
			mid += with(item) { MIDFACE_LENGTH + CHEEK_ASYMMETRY + CHEEK_HOLLOW + NASOLABIAL_FOLD + LOW_BRIDGE_NOSE + BULBOUS_NOSE_TIP + CROOKED_NOSE }
			lower += with(item) { UNEVEN_LIPS + DROOPY_MOUTH_CORNER + LONG_PHILTRUM + WEAK_JAWLINE + JAW_ASYMMETRY + MOUTH_HOLLOW + PROGNATHISM + RETROGNATHIA }
		}

		return arrayListOf(
			PieEntry(whole.toFloat(), "얼굴 전체"),
			PieEntry(upper.toFloat(), "상안부"),
			PieEntry(mid.toFloat(), "중안부"),
			PieEntry(lower.toFloat(), "하안부")
		).filter { it.value > 0f } as ArrayList<PieEntry>
	}


	fun countAgeGroup(data: List<PreCheckList>): ArrayList<PieEntry> {
		val ageGroups = mutableMapOf(
			"20세 미만" to 0,
			"20~25세 미만" to 0,
			"25~30세 미만" to 0,
			"30~35세 미만" to 0,
			"35~40세 미만" to 0,
			"40~50세 미만" to 0,
			"50~60세 미만" to 0,
			"60세 이상" to 0
		)
		return ageGroups.map { (label, count) ->
			PieEntry(
				count.toFloat(),
				label.replace(" 미만", "")
			)
		}.filter { it.value > 0f } as ArrayList<PieEntry>
	}


}