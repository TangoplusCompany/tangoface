package com.tangoplus.tangoface.ui.settings.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tangoplus.tangoface.domain.util.PreferenceUtility
import java.io.File

class PosterViewModel(private val pPrefs: PreferenceUtility)  : ViewModel() {

	private val _posterList = MutableLiveData<MutableList<String>>(mutableListOf())
	val posterList: LiveData<MutableList<String>> get() = _posterList

	// 현재 수정/추가 중인 위치 (-1: 추가, 0~4: 수정)
	var targetPosition: Int = -1
	val MAX_COUNT = 6

	init {
		loadPosters()
	}

	// 데이터 로드
	fun loadPosters() {
		_posterList.value = pPrefs.getPosters().toMutableList()
	}

	// 추가/수정 모드 설정
	fun setTarget(position: Int) {
		targetPosition = position
	}

	// 이미지 저장 처리 (추가 또는 수정)
	fun handleSavedPath(newPath: String) {
		val currentList = _posterList.value ?: mutableListOf()

		if (targetPosition == -1) {
			// 추가 모드
			if (currentList.size < MAX_COUNT) {
				currentList.add(newPath)
			}
		} else {
			// 수정 모드
			if (targetPosition < currentList.size) {
				// 기존 파일 삭제 후 교체
				val oldPath = currentList[targetPosition]
				File(oldPath).delete()
				currentList[targetPosition] = newPath
			}
		}

		saveAndRefresh(currentList)
	}

	// 삭제 처리
	fun deletePoster(position: Int) {
		val currentList = _posterList.value ?: return
		if (position < currentList.size) {
			val removedPath = currentList.removeAt(position)
			File(removedPath).delete()
			saveAndRefresh( currentList)
		}
	}

	private fun saveAndRefresh(list: MutableList<String>) {
		pPrefs.savePosters( list)
		_posterList.value = list // LiveData 업데이트
	}

}