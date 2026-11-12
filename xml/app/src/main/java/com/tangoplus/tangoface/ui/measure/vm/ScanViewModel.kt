package com.tangoplus.tangoface.ui.measure.vm

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangoplus.tangoface.data.api.FaceScanApiClient
import com.tangoplus.tangoface.data.api.FaceScanApiService
import com.tangoplus.tangoface.data.api.FaceScanRepository
import com.tangoplus.tangoface.data.api.FaceScanRepositoryImpl
import com.tangoplus.tangoface.data.api.ScanErrorCodeMapper
import com.tangoplus.tangoface.data.api.ScanHistoryItem
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.data.db.FaceStatic
import com.tangoplus.tangoface.domain.util.PreferenceUtility
import com.tangoplus.tangoface.domain.vo.MeasureType
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.orbbec.application.GlbBakingLockManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 3D 얼굴 스캔 완료 후 UserSetupDialogFragment에서 입력받은 사용자 정보
 */
data class ScanUserInfo(
    val name: String = "GUEST",
    val mobile: String = "",
    val birthday: String = "",
    val gender: Int = -1,
    val nationality: String = "",
    val uuid: String = ""
)

/**
 * 3D 얼굴 스캔 화면 UI 상태 모델
 */
data class ScanUiState(
    val status: String = "IDLE", // IDLE, READY_TO_SCAN, CREATING, CREATED, PAIRED, UPLOADED, PROCESSING, DONE, FAILED
    val sessionId: String = "",
    val pairingCode: String = "",
    val progressPercent: Int = 0,
    val remainingSeconds: Int = 0,
    val isQueued: Boolean = false,
    val stageMessage: String = "",
    val glbDownloadUrl: String? = null,
    val isDownloadingGlb: Boolean = false,
    val localGlbPath: String? = null,
    val localGlbUri: String? = null,
    val localGlbFileName: String? = null,
    val localGlbSizeBytes: Long = 0L,
    val userInfo: ScanUserInfo? = null,
    val isUserSetupCompleted: Boolean = false,
    val savedTempServerSn: Int? = null,
    val errorMessage: String? = null,
    val bakingStartedElapsedMs: Long? = null
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

    private inline fun updateState(crossinline transform: (ScanUiState) -> ScanUiState) {
        _uiState.update { prev ->
            val rawNext = transform(prev)
            val upper = rawNext.status.uppercase()
            val bakingStart = when (upper) {
                "UPLOADED", "PROCESSING" -> rawNext.bakingStartedElapsedMs ?: prev.bakingStartedElapsedMs ?: SystemClock.elapsedRealtime()
                else -> null
            }
            val next = if (rawNext.bakingStartedElapsedMs == bakingStart) {
                rawNext
            } else {
                rawNext.copy(bakingStartedElapsedMs = bakingStart)
            }
            GlbBakingLockManager.setLocked(shouldLockForState(next))
            next
        }
    }

    private fun shouldLockForState(state: ScanUiState): Boolean {
        return when (state.status.uppercase()) {
            "UPLOADED", "PROCESSING" -> true
            "DONE" -> state.errorMessage == null && state.savedTempServerSn == null
            else -> false
        }
    }

    /**
     * 태블릿 직접 업로드 완료 직후 폴링 주기(최대 2초) 대기 없이 즉시 UPLOADED(잠금/진행 화면)로 전환
     */
    fun markDirectUploadCompleted() {
        updateState { prev ->
            val upper = prev.status.uppercase()
            if (upper == "CREATED" || upper == "PAIRED") {
                prev.copy(
                    status = "UPLOADED",
                    stageMessage = "영상이 업로드되었습니다. 3D 복원을 준비 중입니다."
                )
            } else {
                prev
            }
        }
    }

    // 스캔 세션 내역 목록 (뒤로가기 시에도 보존)
    private val _historyList = MutableStateFlow<List<ScanHistoryItem>>(emptyList())
    val historyList: StateFlow<List<ScanHistoryItem>> = _historyList.asStateFlow()

    // 세션별 백그라운드 폴링 작업 관리 (유실 방지)
    private val activePollingJobs = ConcurrentHashMap<String, Job>()

    // 세션별 중복 GLB 다운로드 / DB 저장 방지용 Set
    private val activeDownloadSessionIds = ConcurrentHashMap.newKeySet<String>()
    private val activePersistSessionIds = ConcurrentHashMap.newKeySet<String>()

    // 현재 포커스된 세션 ID 및 세션 초기화 Job
    private var currentSessionId: String? = null
    private var currentSessionSecret: String? = null
    private var sessionInitJob: Job? = null

    /**
     * 화면 진입 시 호출:
     * - 진입 시마다 기존 상태를 초기화하고 신규 세션(QR + 새 6자리 PIN 번호) 발급
     */
    fun onFragmentEnter(tabletKey: String = FaceScanApiClient.getTabletKey()) {
        resetAll()
        startNewScanFlow(delayMillis = 1000L, tabletKey = tabletKey)
    }

    /**
     * 신규 세션 플로우:
     * 1단계: "스마트폰으로 QR 코드를 먼저 스캔하세요" (READY_TO_SCAN)
     * 2단계: 1초 뒤 PIN 번호 생성 API 호출 -> 6자리 PIN 번호 표시 (CREATED)
     */
    fun startNewScanFlow(delayMillis: Long = 1000L, tabletKey: String = FaceScanApiClient.getTabletKey()) {
        sessionInitJob?.cancel()
        sessionInitJob = viewModelScope.launch {
            updateState {
                ScanUiState(
                    status = "READY_TO_SCAN",
                    stageMessage = "스마트폰 카메라로 QR 코드를 스캔해주세요..."
                )
            }

            // 1초 대기 후 PIN 발급
            delay(delayMillis)

            createSessionInternal(tabletKey)
        }
    }

    /**
     * 세션 생성 및 6자리 페어링 번호 발급
     */
    fun createSession(tabletKey: String = FaceScanApiClient.getTabletKey()) {
        sessionInitJob?.cancel()
        sessionInitJob = viewModelScope.launch {
            createSessionInternal(tabletKey)
        }
    }

    private suspend fun createSessionInternal(tabletKey: String) {
        updateState {
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

            updateState {
                it.copy(
                    status = "CREATED",
                    sessionId = response.sessionId,
                    pairingCode = response.code,
                    errorMessage = null,
                    stageMessage = "스마트폰에서 PIN 번호를 입력해주세요."
                )
            }

            // 백그라운드 폴링 시작
            startBackgroundPolling(response.sessionId, response.sessionSecret)
        }.onFailure { error ->
            updateState {
                it.copy(
                    status = "FAILED",
                    errorMessage = error.message ?: "세션 생성에 실패했습니다."
                )
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
                            val queuePos = progress?.queuePosition
                            val queued = queuePos != null && queuePos >= 1
                            val percent = progress?.progressPercent ?: 0
                            val remaining = progress?.etaSeconds ?: progress?.remainingSeconds ?: 0
                            val stage = when {
                                queued -> {
                                    val ahead = (queuePos!! - 1).coerceAtLeast(0)
                                    progress?.stage?.ifBlank { null } ?: "대기 중 (앞에 ${ahead}명)"
                                }
                                else -> progress?.stage?.ifBlank { null } ?: "3D 얼굴 복원 진행 중..."
                            }

                            updateHistoryItem(sessionId) { item ->
                                item.copy(
                                    status = "PROCESSING",
                                    progressPercent = percent,
                                    remainingSeconds = remaining,
                                    stageMessage = stage
                                )
                            }

                            if (currentSessionId == sessionId) {
                                updateState {
                                    it.copy(
                                        status = "PROCESSING",
                                        progressPercent = percent,
                                        remainingSeconds = remaining,
                                        isQueued = queued,
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
                                updateState {
                                    it.copy(
                                        status = "DONE",
                                        progressPercent = 100,
                                        remainingSeconds = 0,
                                        isQueued = false,
                                        stageMessage = "3D 복원이 완료되었습니다.",
                                        glbDownloadUrl = downloadUrl
                                    )
                                }
                            }
                            break
                        }

                        "FAILED" -> {
                            val friendlyReason = ScanErrorCodeMapper.toMessage(
                                statusBody.failCode ?: statusBody.failReason
                            )
                            handleSessionFailure(sessionId, friendlyReason)
                            break
                        }

                        else -> {
                            val serverStage = statusBody.progress?.stage?.takeIf { it.isNotBlank() }
                            val stageMsg = when (statusUpper) {
                                "CREATED" -> "스마트폰에서 6자리 코드를 입력해주세요."
                                "PAIRED" -> "스마트폰과 연결되었습니다. 촬영을 진행해주세요."
                                "UPLOADED" -> serverStage ?: "영상이 업로드되었습니다. 3D 복원을 준비 중입니다."
                                else -> statusBody.status
                            }

                            updateHistoryItem(sessionId) { item ->
                                item.copy(status = statusUpper, stageMessage = stageMsg)
                            }

                            if (currentSessionId == sessionId) {
                                updateState {
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
            updateState {
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
            updateState {
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

        val targetPath = target.localGlbPath
        val targetFile = targetPath?.takeIf { !it.startsWith("content://") }?.let { File(it) }

        updateState {
            ScanUiState(
                status = target.status,
                sessionId = target.sessionId,
                pairingCode = target.pairingCode,
                progressPercent = target.progressPercent,
                remainingSeconds = target.remainingSeconds,
                stageMessage = target.stageMessage,
                glbDownloadUrl = target.glbDownloadUrl,
                localGlbPath = targetFile?.absolutePath,
                localGlbUri = targetPath?.takeIf { it.startsWith("content://") },
                localGlbFileName = targetFile?.name,
                localGlbSizeBytes = targetFile?.takeIf { it.exists() }?.length() ?: 0L,
                isUserSetupCompleted = target.savedTempServerSn != null,
                savedTempServerSn = target.savedTempServerSn,
                errorMessage = target.failReason
            )
        }
    }

    /**
     * DONE 상태에서 발급된 Signed URL로부터 GLB 파일을 공용 저장소(Documents/TangoPlus/3DModels)에 다운로드
     * (최대 3회 자동 재시도, 파일 저장 후 UserSetupDialogFragment 입력 완료 시 DB 저장)
     */
    fun downloadGlbToLocal(
        context: Context,
        sessionId: String,
        downloadUrl: String,
        destinationFile: File
    ) {
        if (!activeDownloadSessionIds.add(sessionId)) {
            return
        }

        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                if (currentSessionId == sessionId) {
                    updateState {
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
                var mediaStoreUriString: String? = null
                var fileSizeBytes = 0L

                val cacheTempFile = File(appContext.cacheDir, destinationFile.name)

                for (attempt in 1..maxAttempts) {
                    // 1) 직접 Documents/TangoPlus/3DModels 경로에 쓰기 시도
                    destinationFile.parentFile?.mkdirs()
                    val directResult = repository.downloadGlb(downloadUrl, destinationFile)
                    if (directResult.isSuccess) {
                        val saved = directResult.getOrNull()
                        if (saved != null && saved.exists() && saved.length() > 0L) {
                            downloadedFile = saved
                            fileSizeBytes = saved.length()
                            MediaScannerConnection.scanFile(
                                appContext,
                                arrayOf(saved.absolutePath),
                                arrayOf("model/gltf-binary"),
                                null
                            )
                            break
                        }
                    }

                    // 2) 직접 쓰기 권한이 제한된 환경이면 캐시에 내려받은 뒤 MediaStore Documents/TangoPlus/3DModels로 복사
                    val cacheResult = repository.downloadGlb(downloadUrl, cacheTempFile)
                    if (cacheResult.isSuccess) {
                        val cached = cacheResult.getOrNull()
                        if (cached != null && cached.exists() && cached.length() > 0L) {
                            val copiedUri = copyGlbToMediaStoreDocuments(appContext, cached, destinationFile.name)
                            if (copiedUri != null) {
                                mediaStoreUriString = copiedUri
                                fileSizeBytes = cached.length()
                                cached.delete()
                                break
                            }
                        }
                    }

                    lastError = directResult.exceptionOrNull() ?: cacheResult.exceptionOrNull()
                    if (attempt < maxAttempts) {
                        delay(1500L)
                    }
                }

                if (downloadedFile != null || mediaStoreUriString != null) {
                    val savedPath = downloadedFile?.absolutePath
                    val fileName = downloadedFile?.name ?: destinationFile.name
                    val alreadyCompletedUser = _uiState.value
                        .takeIf { currentSessionId == sessionId && it.isUserSetupCompleted && it.userInfo != null }
                        ?.userInfo

                    updateHistoryItem(sessionId) { item ->
                        item.copy(
                            localGlbPath = savedPath ?: mediaStoreUriString,
                            stageMessage = if (alreadyCompletedUser != null) "3D 모델 저장 중..." else "사용자 정보를 입력해주세요."
                        )
                    }
                    if (currentSessionId == sessionId) {
                        updateState {
                            it.copy(
                                isDownloadingGlb = false,
                                localGlbPath = savedPath,
                                localGlbUri = mediaStoreUriString,
                                localGlbFileName = fileName,
                                localGlbSizeBytes = fileSizeBytes,
                                errorMessage = null,
                                stageMessage = if (alreadyCompletedUser != null) "3D 모델 저장 중..." else "사용자 정보를 입력해주세요."
                            )
                        }
                    }

                    // 진행 중 이미 UserSetupDialogFragment 입력이 끝났다면 즉시 파일명 변경 + DB 저장
                    if (alreadyCompletedUser != null) {
                        persistScanWithUserInfo(appContext, sessionId, alreadyCompletedUser)
                    }
                } else {
                    val errMsg = lastError?.message ?: "GLB 파일 다운로드에 실패했습니다."
                    if (currentSessionId == sessionId) {
                        updateState {
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

    private suspend fun copyGlbToMediaStoreDocuments(
        context: Context,
        sourceFile: File,
        fileName: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "model/gltf-binary")
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/TangoPlus/3DModels")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: return@withContext null

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            } ?: return@withContext null

            uri.toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * UserSetupDialogFragment 입력 완료 시 사용자 정보를 ViewModel에 저장하고,
     * GLB 파일이 이미 준비되어 있으면 즉시 파일명 변경 및 Room DB(t_face_static, SCAN) 저장 수행
     * (아직 복원/다운로드 중이면 downloadGlbToLocal 완료 직후 자동 저장)
     */
    fun saveUserInfoAndPersistToDb(
        context: Context,
        userInfo: ScanUserInfo
    ) {
        val state = _uiState.value
        val sessionId = state.sessionId.ifBlank { currentSessionId.orEmpty() }
        if (state.savedTempServerSn != null) return

        // 선입력 시에도 VM 상태에 우선 저장
        updateState {
            it.copy(
                userInfo = userInfo,
                isUserSetupCompleted = true,
                errorMessage = null
            )
        }

        val hasLocalFile = (state.localGlbFileName ?: state.localGlbPath?.let { File(it).name }) != null
        if (!hasLocalFile) return

        val appContext = context.applicationContext
        viewModelScope.launch {
            persistScanWithUserInfo(appContext, sessionId, userInfo)
        }
    }

    private suspend fun persistScanWithUserInfo(
        appContext: Context,
        sessionId: String,
        userInfo: ScanUserInfo
    ) {
        if (!activePersistSessionIds.add(sessionId)) return
        try {
            val state = _uiState.value
            if (state.savedTempServerSn != null) return
            val fileName = state.localGlbFileName ?: state.localGlbPath?.let { File(it).name } ?: return

            // 측정건(사용자 이름 + 측정 시각) 기준으로 파일명 변경 후 DB 에는 변경된 파일명 저장
            val renamed = withContext(Dispatchers.IO) {
                renameGlbForMeasurement(appContext, state, userInfo)
            }
            val finalFileName = renamed?.first ?: fileName
            val finalPath = renamed?.second ?: state.localGlbPath
            val finalUri = renamed?.third ?: state.localGlbUri

            val assignedSn = withContext(Dispatchers.IO) {
                saveScanResultToDb(appContext, sessionId, finalFileName, userInfo)
            }

            updateHistoryItem(sessionId) { item ->
                item.copy(
                    savedTempServerSn = assignedSn,
                    localGlbPath = finalPath ?: finalUri,
                    stageMessage = "3D 모델 및 사용자 정보 저장 완료"
                )
            }

            if (currentSessionId == sessionId) {
                updateState {
                    it.copy(
                        userInfo = userInfo,
                        isUserSetupCompleted = true,
                        savedTempServerSn = assignedSn,
                        localGlbFileName = finalFileName,
                        localGlbPath = finalPath,
                        localGlbUri = finalUri,
                        errorMessage = null,
                        stageMessage = "3D 모델 및 사용자 정보 저장 완료"
                    )
                }
            }
        } finally {
            activePersistSessionIds.remove(sessionId)
        }
    }

    /**
     * 저장된 GLB 파일명을 "{이름}_{yyyyMMddHHmmss}.glb" 로 변경
     * - 파일 직접 저장(file path)이면 renameTo, MediaStore(content://)이면 DISPLAY_NAME update
     * - 실패 시 null 반환(기존 파일명 유지)
     * @return Triple(새 파일명, 새 파일 경로(or null), 새 content uri(or null))
     */
    private fun renameGlbForMeasurement(
        context: Context,
        state: ScanUiState,
        userInfo: ScanUserInfo
    ): Triple<String, String?, String?>? {
        return try {
            val safeName = userInfo.name.ifBlank { "GUEST" }
                .replace(Regex("[\\\\/:*?\"<>|\\s]"), "")
                .ifEmpty { "GUEST" }
            val timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            val newName = "${safeName}_$timestamp.glb"

            val path = state.localGlbPath
            val uriStr = state.localGlbUri
            when {
                path != null -> {
                    val src = File(path)
                    val dst = File(src.parentFile, newName)
                    if (src.exists() && src.renameTo(dst)) {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(src.absolutePath, dst.absolutePath),
                            arrayOf("model/gltf-binary", "model/gltf-binary"),
                            null
                        )
                        Triple(newName, dst.absolutePath, null)
                    } else null
                }
                uriStr != null -> {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                    }
                    val updated = context.contentResolver.update(android.net.Uri.parse(uriStr), values, null, null)
                    if (updated > 0) Triple(newName, null, uriStr) else null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 다운로드된 3D 스캔 GLB 결과를 Room DB(t_face_static, SCAN)에 저장
     * (UserSetupDialogFragment 입력값 반영)
     */
    private suspend fun saveScanResultToDb(
        context: Context,
        sessionId: String,
        fileName: String,
        userInfo: ScanUserInfo
    ): Int {
        val prefsUtil = PreferenceUtility(context)
        val assignedSn = prefsUtil.getLastTempServerSn()
        val fDao = FaceDatabase.getDatabase(context).faceDao()

        val faceStatic = FaceStatic(
            temp_server_sn = assignedSn,
            uuid = userInfo.uuid.ifBlank { sessionId },
            name = userInfo.name.ifBlank { "GUEST" },
            mobile = userInfo.mobile,
            birthday = userInfo.birthday,
            gender = userInfo.gender,
            nationality = userInfo.nationality,
            seq = Sequence.COMFORT.id,
            media_file_name = fileName,
            json_file_name = "",
            reg_date = FaceStatic.getCurrentDateTime(),
            measure_type = MeasureType.SCAN.idx,
            activated = 1
        )
        fDao.insertStatic(faceStatic)
        prefsUtil.setNextTempServerSn()
        return assignedSn
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
     * 현재 스캔 상태 초기화
     */
    fun resetScanState() {
        currentSessionId = null
        currentSessionSecret = null
        updateState { ScanUiState(status = "IDLE") }
    }

    /**
     * 프래그먼트 이탈/종료 시 진행 중인 폴링·세션·내역·사용자 입력까지 전체 초기화
     */
    fun resetAll() {
        sessionInitJob?.cancel()
        sessionInitJob = null

        activePollingJobs.values.forEach { it.cancel() }
        activePollingJobs.clear()
        activeDownloadSessionIds.clear()
        activePersistSessionIds.clear()

        currentSessionId = null
        currentSessionSecret = null
        _historyList.value = emptyList()
        updateState { ScanUiState(status = "IDLE") }
    }

    override fun onCleared() {
        super.onCleared()
        resetAll()
    }
}

typealias MScanViewModel = ScanViewModel
