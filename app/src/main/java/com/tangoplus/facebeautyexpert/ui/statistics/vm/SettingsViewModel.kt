package com.tangoplus.facebeautyexpert.ui.statistics.vm

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangoplus.facebeautyexpert.data.db.FaceDatabase
import com.tangoplus.facebeautyexpert.ui.main.vo.MeasureGridItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel : ViewModel() {
	val settings = listOf(
		Pair("측정 항목 선택하기", "수정 및 변경"),
		Pair("기기 수평 설정하기", "수평계 시작"),
		Pair("사전 체크리스트 확인하기", "차트 확인"),
		Pair("측정 결과 확인하기", "기록 관리"),
		Pair("일반 카메라로 측정하기", "기능 분리 후 변경 예정"),
		Pair("플리커 현상 관련", "카메라 설정 변경")
	)

	var allMeasureItem = listOf<MeasureGridItem>()
	var allUserItem = listOf<MeasureGridItem>()

	fun initDataFromDB(context: Context) {
		viewModelScope.launch(Dispatchers.IO) {
			val dao = FaceDatabase.getDatabase(context).faceDao()
			val allMeasureData: List<MeasureGridItem> = dao.getAllMeasureItem()
			val allUserData: List<MeasureGridItem> = dao.getAllUserItem()
			withContext(Dispatchers.IO) {
				allMeasureItem = allMeasureData
				allUserItem = allUserData
				Log.v("삭제아이템들", " user: ${allUserItem}")
			}
		}
	}

	var currentTabIndex = MutableLiveData(0)

	var selectedFlicker = 2
}