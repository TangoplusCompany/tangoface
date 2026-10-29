package com.tangoplus.tangoface_compose.ui.measure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangoplus.tangoface_compose.domain.model.MeasureType
import com.tangoplus.tangoface_compose.domain.model.UserItem
import com.tangoplus.tangoface_compose.domain.repository.FaceRepository
import com.tangoplus.tangoface_compose.domain.usecase.GetMeasurementHistoryUseCase
import com.tangoplus.tangoface_compose.domain.usecase.SaveFaceStaticUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 측정 화면 전체 UI 상태 (MVI/MVVM State Pattern)
 */
data class MeasureUiState(
	val currentMode: MeasureType = MeasureType.NORMAL,
	val isCameraRunning: Boolean = false,
	val isImageUploadDialogOpen: Boolean = false,
	val isAngleMeasureActive: Boolean = false,
	val historyList: List<UserItem> = emptyList(),
	val isLoading: Boolean = false,
	val errorMessage: String? = null
)

class MeasureViewModel(
	private val faceRepository: FaceRepository,
	private val getMeasurementHistoryUseCase: GetMeasurementHistoryUseCase,
	private val saveFaceStaticUseCase: SaveFaceStaticUseCase
) : ViewModel() {

	private val _uiState = MutableStateFlow(MeasureUiState())
	val uiState: StateFlow<MeasureUiState> = _uiState.asStateFlow()

	init {
		loadHistory()
	}

	/**
	 * 측정 모드 변경
	 * - NORMAL, EXPERT, BOW, SCAN, LAB_3D_SCAN: 카메라 활성화
	 * - IMAGE: 다이얼로그 업로드 팝업 오픈
	 */
	fun selectMode(mode: MeasureType) {
		_uiState.update { current ->
			current.copy(
				currentMode = mode,
				isCameraRunning = mode.isCameraBased,
				isImageUploadDialogOpen = (mode == MeasureType.IMAGE)
			)
		}
	}

	/**
	 * 이미지 사진 검사 다이얼로그 닫기
	 */
	fun closeImageDialog() {
		_uiState.update { it.copy(isImageUploadDialogOpen = false) }
	}

	/**
	 * 각도 측정 오버레이 토글 (ON 시 이미지 터치 잠금)
	 */
	fun toggleAngleMeasureMode() {
		_uiState.update { it.copy(isAngleMeasureActive = !it.isAngleMeasureActive) }
	}

	/**
	 * 측정 이력 불러오기 (Room DB)
	 */
	fun loadHistory() {
		viewModelScope.launch {
			_uiState.update { it.copy(isLoading = true) }
			try {
				val history = getMeasurementHistoryUseCase()
				_uiState.update { it.copy(historyList = history, isLoading = false) }
			} catch (e: Exception) {
				_uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
			}
		}
	}
}

