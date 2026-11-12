package com.tangoplus.tangoface.ui.settings.vm

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.domain.util.MeasurementBackupUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

	private val _mode = MutableStateFlow(Mode.EXPORT)   // 진입 시 무조건 내보내기
	val mode = _mode.asStateFlow()

	private val _state = MutableStateFlow<UiState>(UiState.Loading)
	val state = _state.asStateFlow()

	private var pendingImportUri: Uri? = null

	init { loadExportPreview() }

	// ---------- 모드 / 상태 초기화 ----------
	fun setMode(m: Mode) {
		if (_state.value is UiState.Running) return      // 실행 중 전환 막기
		if (_mode.value == m && _state.value !is UiState.Done && _state.value !is UiState.Error) return
		_mode.value = m
		resetToInitial()
	}

	/** Done/Error 확인 후, 또는 모드 전환 시 현재 모드의 초기 상태로 */
	fun resetToInitial() {
		clearImportCache()
		pendingImportUri = null
		_mode.value = Mode.EXPORT          // 가져오기 완료/실패 후엔 내보내기로 복귀
		loadExportPreview()
	}

	// ---------- Export ----------
	private fun loadExportPreview() {
		_state.value = UiState.Loading
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val ctx = getApplication<Application>()
				val db = FaceDatabase.getDatabase(ctx).openHelper.readableDatabase

				var count = 0
				var period = "-"
				db.query(
					"SELECT COUNT(DISTINCT temp_server_sn), MIN(reg_date), MAX(reg_date) " +
						"FROM t_face_static WHERE activated = 1"
				).use { c ->
					if (c.moveToFirst()) {
						count = c.getInt(0)
						val min = c.getString(1)?.take(10)
						val max = c.getString(2)?.take(10)
						if (min != null && max != null) period = "$min ~ $max"
					}
				}

				if (count == 0) {
					_state.value = UiState.ExportReady(null)
					return@launch
				}

				// export 대상 파일 수 / 용량 (export()와 같은 규칙: 사진, JSON, 동영상, 3D 모델)
				val targets = listOf(
					File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "TangoPlus") to "jpg",
					File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "TangoPlus") to "json",
					File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "TangoPlus/3DModels") to "glb",
					File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "TangoPlus") to "glb",
					File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "TangoPlus") to "mp4"
				).flatMap { (dir, ext) ->
					if (!dir.exists()) emptyList()
					else dir.listFiles { f -> f.isFile && f.extension.equals(ext, true) }?.toList() ?: emptyList()
				}.distinctBy { it.name }
				val dbBytes = ctx.getDatabasePath("face_database").parentFile
					?.listFiles()?.filter { it.isFile && it.name.startsWith("face_database") }
					?.sumOf { it.length() } ?: 0L
				val totalBytes = targets.sumOf { it.length() } + dbBytes

				_state.value = UiState.ExportReady(
					ExportPreview(count, period, targets.size, totalBytes / (1024 * 1024))
				)
			} catch (e: Exception) {
				_state.value = UiState.Error("미리보기를 불러오지 못했습니다: ${e.message}")
			}
		}
	}

	fun runExport(outUri: Uri) {
		if (_state.value is UiState.Running) return
		viewModelScope.launch {
			_state.value = UiState.Running(0)
			val r = MeasurementBackupUtility.export(getApplication(), outUri) { p ->
				_state.value = UiState.Running(p)
			}
			_state.value =
				if (r.success) UiState.Done("내보내기 완료\n파일 ${r.fileCount}개를 저장했습니다.")
				else UiState.Error("내보내기 실패: ${r.errorMessage}")
		}
	}

	// ---------- Import ----------
	private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 1)
	val toast = _toast.asSharedFlow()

	private var analyzing = false

	fun onImportFilePicked(uri: Uri, displayName: String) {
		if (_state.value is UiState.Running || analyzing) return
		analyzing = true
		viewModelScope.launch {
			clearImportCache()
			val preview = MeasurementBackupUtility.analyze(getApplication(), uri, displayName)
			analyzing = false
			if (preview == null) {
				_toast.tryEmit("올바른 백업 파일이 아닙니다.")   // 모드/상태는 그대로
			} else {
				pendingImportUri = uri
				_mode.value = Mode.IMPORT                       // 검증 성공 후에야 전환
				_state.value = UiState.ImportReady(preview)
			}
		}
	}

	fun runImport() {
		val uri = pendingImportUri ?: return
		if (_state.value is UiState.Running) return
		viewModelScope.launch {
			_state.value = UiState.Running(0)
			val r = MeasurementBackupUtility.commit(getApplication(), uri) { p ->
				_state.value = UiState.Running(p)
			}
			pendingImportUri = null
			_state.value =
				if (r.success) UiState.Done("가져오기 완료\n측정 ${r.addedMeasurements}건, 파일 ${r.restoredFiles}개를 추가했습니다.")
				else UiState.Error("가져오기 실패: ${r.errorMessage}")
		}
	}

	// ---------- 정리 ----------
	private fun clearImportCache() {
		val ctx = getApplication<Application>()
		File(ctx.cacheDir, "import_src.zip").delete()
		File(ctx.cacheDir, "import_analyze").deleteRecursively()
	}

	override fun onCleared() {
		// 실행 중이 아닐 때만 정리 (실행 중이면 commit의 finally가 정리)
		if (_state.value !is UiState.Running) clearImportCache()
		super.onCleared()
	}
}

enum class Mode { EXPORT, IMPORT }

data class ExportPreview(val count: Int, val period: String, val files: Int, val sizeMb: Long)

sealed interface UiState {
	data object Loading : UiState
	data class ExportReady(val preview: ExportPreview?) : UiState      // null이면 내보낼 측정 없음
	data object ImportIdle : UiState                                   // 파일 미선택
	data class ImportReady(val preview: MeasurementBackupUtility.ImportPreview) : UiState
	data class Running(val percent: Int) : UiState
	data class Done(val message: String) : UiState
	data class Error(val message: String) : UiState
}