package com.tangoplus.tangoface.ui.measure.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangoplus.tangoface.data.api.FaceScanApiClient
import com.tangoplus.tangoface.data.api.FaceScanApiService
import com.tangoplus.tangoface.data.api.FaceScanRepository
import com.tangoplus.tangoface.data.api.FaceScanRepositoryImpl
import com.tangoplus.tangoface.data.api.ScanHistoryItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 3D 얼굴 스캔 화면 UI 상태 모델
 */
data class ScanUiState(
    val status: String = "IDLE", // IDLE, READY_TO_SCAN, CREATING, CREATED, PAIRED, UPLOADED, PROCESSING, DONE, FAILED
    val sessionId: String = "",
    val pairingCode: String = "",
    val progressPercent: Int = 0,
    val remainingSeconds: Int = 0,
    val stageMessage: String = "",
    val glbDownloadUrl: String? = null,
    val isDownloadingGlb: Boolean = false,
    val localGlbPath: String? = null,
    val errorMessage: String? = null
)

/**
 * 3D 얼굴 스캔 실시간 세션 및 진행률 관리 ViewModel
 */
class ScanViewModel(
    private val repository: FaceScanRepository = FaceScanRepositoryImpl(),
    private val apiService: FaceScanApiService = FaceScanApiClient.service
) : ViewModel() {

    // 기존 프로퍼티 유지
    var lastUpdateTime = 0L
    var currentPitch = ""

    // UI 상태 StateFlow
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    // 스캔 세션 내역 목록 (뒤로가기 시에도 보존)
    private val _historyList = MutableStateFlow<List<ScanHistoryItem>>(emptyList())
    val historyList: StateFlow<List<ScanHistoryItem>> = _historyList.asStateFlow()

    // 세션별 백그라운드 폴링 작업 관리 (유실 방지)
    private val activePollingJobs = ConcurrentHashMap<String, Job>()

    // 세션별 중복 GLB 다운로드 방지용 Set
    private val activeDownloadSessionIds = ConcurrentHashMap.newKeySet<String>()

    // 현재 포커스된 세션 ID
    private var currentSessionId: String? = null
    private var currentSessionSecret: String? = null

    /**
     * 화면 진입 시 호출:
     * - 이미 진행 중인 작업(PAIRED, UPLOADED, PROCESSING)이 있으면 유실 없이 복원
     * - 진행 중인 작업이 없다면: 1초 동안 QR 스캔 유도 후 PIN 번호 발급
     */
    fun onFragmentEnter(tabletKey: String = FaceScanApiClient.getTabletKey()) {
        // 이미 3D 스캔 굽는 작업이 진행 중이면 유실되지 않고 해당 작업 화면을 유지
        val ongoingStatus = _uiState.value.status
        if (ongoingStatus in listOf("PAIRED", "UPLOADED", "PROCESSING") && currentSessionId != null) {
            return
        }

        // 대기 중이거나 완료/실패 상태라면 신규 세션 플로우 시작 (1초 뒤 PIN 발급)
        startNewScanFlow(delayMillis = 1000L, tabletKey = tabletKey)
    }

    /**
     * 신규 세션 플로우:
     * 1단계: "스마트폰으로 QR 코드를 먼저 스캔하세요" (READY_TO_SCAN)
     * 2단계: 1초 뒤 PIN 번호 생성 API 호출 -> 6자리 PIN 번호 표시 (CREATED)
     */
    fun startNewScanFlow(delayMillis: Long = 1000L, tabletKey: String = FaceScanApiClient.getTabletKey()) {
        viewModelScope.launch {
            _uiState.update {
                ScanUiState(
                    status = "READY_TO_SCAN",
                    stageMessage = "스마트폰 카메라로 QR 코드를 스캔해주세요..."
                )
            }

            // 1초 대기 후 PIN 발급
            delay(delayMillis)

            createSession(tabletKey)
        }
    }

    /**
     * 세션 생성 및 6자리 페어링 번호 발급
     */
    fun createSession(tabletKey: String = FaceScanApiClient.getTabletKey()) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    status = "CREATING",
                    errorMessage = null
                )
            }

            val result = repository.createScanSession(tabletKey)
            result.onSuccess { response ->
                currentSessionId = response.sessionId
                currentSessionSecret = response.sessionSecret

                val historyItem = ScanHistoryItem(
                    sessionId = response.sessionId,
                    sessionSecret = response.sessionSecret,
                    pairingCode = response.code,
                    status = "CREATED",
                    stageMessage = "스마트폰에서 6자리 코드를 입력해주세요."
                )

                // 내역 목록에 추가
                _historyList.update { current ->
                    listOf(historyItem) + current.filter { it.sessionId != response.sessionId }
                }

                _uiState.update {
                    it.copy(
                        status = "CREATED",
                        sessionId = response.sessionId,
                        pairingCode = response.code,
                        errorMessage = null,
                        stageMessage = "스마트폰에서 PIN 번호를 입력해주세요."
                    )
                }

                // 백그라운드 폴링 시작 (뒤로 가기 해도 중단되지 않음)
                startBackgroundPolling(response.sessionId, response.sessionSecret)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        status = "FAILED",
                        errorMessage = error.message ?: "세션 생성에 실패했습니다."
                    )
                }
            }
        }
    }

    /**
     * 백그라운드 실시간 2초 주기 폴링 (ViewModel 생명주기 기반, 뒤로가기 시에도 유지)
     * - 일시적인 네트워크 끊김이나 5xx 서버 지연 시 즉시 실패 처리하지 않고 최대 15회(약 30초) 연속 재시도
     */
    private fun startBackgroundPolling(sessionId: String, sessionSecret: String) {
        activePollingJobs[sessionId]?.cancel()

        val maxConsecutiveFailures = 15
        val job = viewModelScope.launch {
            var consecutiveFailures = 0

            while (isActive) {
                delay(2000L)

                try {
                    val response = apiService.getSessionStatus(sessionId, sessionSecret)
                    if (!response.isSuccessful) {
                        val code = response.code()
                        // 401/403/404 등 재시도해도 복구 불가한 클라이언트 오류는 즉시 종료
                        if (code in listOf(400, 401, 403, 404)) {
                            handleSessionFailure(sessionId, "세션 인증/조회 오류 ($code)")
                            break
                        }

                        // 5xx 또는 429 등 일시 서버 장애는 재시도
                        consecutiveFailures++
                        if (consecutiveFailures >= maxConsecutiveFailures) {
                            handleSessionFailure(sessionId, "서버 응답 지연으로 상태 확인에 실패했습니다 ($code)")
                            break
                        }
                        notifyPollingRetrying(sessionId, consecutiveFailures, maxConsecutiveFailures)
                        continue
                    }

                    val statusBody = response.body() ?: continue
                    // 정상 응답 수신 시 연속 실패 카운터 초기화
                    consecutiveFailures = 0
                    val statusUpper = statusBody.status.uppercase()

                    when (statusUpper) {
                        "PROCESSING" -> {
                            val progress = statusBody.progress
                            val percent = progress?.progressPercent ?: 0
                            val remaining = progress?.remainingSeconds ?: 0
                            val stage = progress?.stage ?: "3D 얼굴 복원 진행 중..."

                            updateHistoryItem(sessionId) { item ->
                                item.copy(
                                    status = "PROCESSING",
                                    progressPercent = percent,
                                    remainingSeconds = remaining,
                                    stageMessage = stage
                                )
                            }

                            if (currentSessionId == sessionId) {
                                _uiState.update {
                                    it.copy(
                                        status = "PROCESSING",
                                        progressPercent = percent,
                                        remainingSeconds = remaining,
                                        stageMessage = stage
                                    )
                                }
                            }
                        }

                        "DONE" -> {
                            // 복원 완료 시 3D GLB 결과 서명 URL 요청 (일시 오류 시 다음 폴링에서 재시도)
                            val resultResponse = apiService.getSessionResult(sessionId, sessionSecret)
                            if (!resultResponse.isSuccessful) {
                                val code = resultResponse.code()
                                if (code in listOf(400, 401, 403, 404)) {
                                    handleSessionFailure(sessionId, "결과 URL 발급 오류 ($code)")
                                    break
                                }
                                consecutiveFailures++
                                if (consecutiveFailures >= maxConsecutiveFailures) {
                                    handleSessionFailure(sessionId, "결과 URL 발급에 실패했습니다 ($code)")
                                    break
                                }
                                notifyPollingRetrying(sessionId, consecutiveFailures, maxConsecutiveFailures)
                                continue
                            }

                            val downloadUrl = resultResponse.body()?.downloadUrl
                            if (downloadUrl.isNullOrBlank()) {
                                consecutiveFailures++
                                if (consecutiveFailures >= maxConsecutiveFailures) {
                                    handleSessionFailure(sessionId, "결과 다운로드 URL이 비어있습니다.")
                                    break
                                }
                                continue
                            }

                            updateHistoryItem(sessionId) { item ->
                                item.copy(
                                    status = "DONE",
                                    progressPercent = 100,
                                    remainingSeconds = 0,
                                    stageMessage = "3D 복원이 완료되었습니다.",
                                    glbDownloadUrl = downloadUrl
                                )
                            }

                            if (currentSessionId == sessionId) {
                                _uiState.update {
                                    it.copy(
                                        status = "DONE",
                                        progressPercent = 100,
                                        remainingSeconds = 0,
                                        stageMessage = "3D 복원이 완료되었습니다.",
                                        glbDownloadUrl = downloadUrl
                                    )
                                }
                            }
                            break
                        }

                        "FAILED" -> {
                            val failReason = statusBody.failReason ?: "3D 복원 중 오류가 발생했습니다."
                            handleSessionFailure(sessionId, failReason)
                            break
                        }

                        else -> {
                            val stageMsg = when (statusUpper) {
                                "CREATED" -> "스마트폰에서 6자리 코드를 입력해주세요."
                                "PAIRED" -> "스마트폰과 연결되었습니다. 촬영을 진행해주세요."
                                "UPLOADED" -> "영상이 업로드되었습니다. 3D 복원을 준비 중입니다."
                                else -> statusBody.status
                            }

                            updateHistoryItem(sessionId) { item ->
                                item.copy(status = statusUpper, stageMessage = stageMsg)
                            }

                            if (currentSessionId == sessionId) {
                                _uiState.update {
                                    it.copy(status = statusUpper, stageMessage = stageMsg)
                                }
                            }
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    consecutiveFailures++
                    if (consecutiveFailures >= maxConsecutiveFailures) {
                        handleSessionFailure(sessionId, e.message ?: "네트워크 연결이 끊어졌습니다.")
                        break
                    }
                    notifyPollingRetrying(sessionId, consecutiveFailures, maxConsecutiveFailures)
                }
            }
        }

        activePollingJobs[sessionId] = job
    }

    /**
     * 일시 네트워크 끊김 시 UI에 재연결 시도 상태 표시
     */
    private fun notifyPollingRetrying(sessionId: String, attempt: Int, maxAttempt: Int) {
        val retryMsg = "네트워크 연결이 불안정합니다. 재시도 중... ($attempt/$maxAttempt)"
        updateHistoryItem(sessionId) { item ->
            item.copy(stageMessage = retryMsg)
        }
        if (currentSessionId == sessionId) {
            _uiState.update {
                it.copy(stageMessage = retryMsg)
            }
        }
    }

    /**
     * 세션 실패 처리
     */
    private fun handleSessionFailure(sessionId: String, failReason: String) {
        updateHistoryItem(sessionId) { item ->
            item.copy(status = "FAILED", failReason = failReason)
        }

        if (currentSessionId == sessionId) {
            _uiState.update {
                it.copy(
                    status = "FAILED",
                    errorMessage = failReason
                )
            }
        }
    }

    /**
     * 내역 항목 개별 업데이트 헬퍼
     */
    private fun updateHistoryItem(sessionId: String, transform: (ScanHistoryItem) -> ScanHistoryItem) {
        _historyList.update { list ->
            list.map { item ->
                if (item.sessionId == sessionId) transform(item) else item
            }
        }
    }

    /**
     * 특정 세션을 화면의 현재 세션으로 선택(복원)
     */
    fun selectSession(sessionId: String) {
        val target = _historyList.value.find { it.sessionId == sessionId } ?: return
        currentSessionId = target.sessionId
        currentSessionSecret = target.sessionSecret

        _uiState.value = ScanUiState(
            status = target.status,
            sessionId = target.sessionId,
            pairingCode = target.pairingCode,
            progressPercent = target.progressPercent,
            remainingSeconds = target.remainingSeconds,
            stageMessage = target.stageMessage,
            glbDownloadUrl = target.glbDownloadUrl,
            localGlbPath = target.localGlbPath,
            errorMessage = target.failReason
        )
    }

    /**
     * DONE 상태에서 발급된 Signed URL로부터 GLB 파일을 태블릿 로컬 저장소에 다운로드 (최대 3회 자동 재시도)
     */
    fun downloadGlbToLocal(sessionId: String, downloadUrl: String, destinationFile: File) {
        if (!activeDownloadSessionIds.add(sessionId)) {
            return
        }

        viewModelScope.launch {
            try {
                destinationFile.parentFile?.mkdirs()

                if (currentSessionId == sessionId) {
                    _uiState.update {
                        it.copy(
                            isDownloadingGlb = true,
                            errorMessage = null,
                            stageMessage = "3D GLB 모델을 다운로드하는 중입니다..."
                        )
                    }
                }

                var lastError: Throwable? = null
                val maxAttempts = 3
                var downloadedFile: File? = null

                for (attempt in 1..maxAttempts) {
                    val result = repository.downloadGlb(downloadUrl, destinationFile)
                    if (result.isSuccess) {
                        downloadedFile = result.getOrNull()
                        break
                    }
                    lastError = result.exceptionOrNull()
                    if (attempt < maxAttempts) {
                        delay(1500L)
                    }
                }

                if (downloadedFile != null) {
                    val savedPath = downloadedFile.absolutePath
                    updateHistoryItem(sessionId) { item ->
                        item.copy(
                            localGlbPath = savedPath,
                            stageMessage = "3D 모델 다운로드 완료"
                        )
                    }
                    if (currentSessionId == sessionId) {
                        _uiState.update {
                            it.copy(
                                isDownloadingGlb = false,
                                localGlbPath = savedPath,
                                errorMessage = null,
                                stageMessage = "3D 모델 다운로드 완료"
                            )
                        }
                    }
                } else {
                    val errMsg = lastError?.message ?: "GLB 파일 다운로드에 실패했습니다."
                    if (currentSessionId == sessionId) {
                        _uiState.update {
                            it.copy(
                                isDownloadingGlb = false,
                                errorMessage = errMsg
                            )
                        }
                    }
                }
            } finally {
                activeDownloadSessionIds.remove(sessionId)
            }
        }
    }

    /**
     * 실패한 내역 단일 삭제
     */
    fun deleteHistory(sessionId: String) {
        activePollingJobs[sessionId]?.cancel()
        activePollingJobs.remove(sessionId)

        _historyList.update { list ->
            list.filter { it.sessionId != sessionId }
        }

        // 현재 화면에 표시되던 것이 삭제되면 초기화
        if (currentSessionId == sessionId) {
            resetScanState()
        }
    }

    /**
     * 실패(FAILED)한 모든 내역 일괄 삭제 ("만약 실패한 내역이 있으면 삭제되게끔")
     */
    fun deleteFailedHistories() {
        val failedIds = _historyList.value.filter { it.isFailed }.map { it.sessionId }
        failedIds.forEach { id ->
            activePollingJobs[id]?.cancel()
            activePollingJobs.remove(id)
        }

        _historyList.update { list ->
            list.filter { !it.isFailed }
        }

        if (currentSessionId in failedIds) {
            resetScanState()
        }
    }

    /**
     * 현재 스캔 상태 초기화 후 새로 시작
     */
    fun resetScanState() {
        currentSessionId = null
        currentSessionSecret = null
        _uiState.value = ScanUiState(status = "IDLE")
    }

    override fun onCleared() {
        super.onCleared()
        activePollingJobs.values.forEach { it.cancel() }
        activePollingJobs.clear()
    }
}

typealias MScanViewModel = ScanViewModel
