package com.tangoplus.tangoface.data.api

import com.google.gson.annotations.SerializedName

/**
 * 1. 태블릿 세션 생성 응답 DTO
 * POST api/sessions
 */
data class CreateSessionResponse(
    @SerializedName("session_id")
    val sessionId: String,

    @SerializedName("code")
    val code: String, // 스마트폰에 보여줄 6자리 페어링 번호

    @SerializedName("session_secret")
    val sessionSecret: String, // 태블릿 인증용 비밀 토큰

    @SerializedName("expires_in_seconds")
    val expiresInSeconds: Int
)

/**
 * 2. 실시간 3D 복원 진행률 정보 DTO
 */
data class ProcessingProgressInfo(
    @SerializedName("elapsed_seconds")
    val elapsedSeconds: Int = 0, // 경과 시간 (초)

    @SerializedName("estimated_total_seconds")
    val estimatedTotalSeconds: Int = 45, // 총 예상 소요 시간 (초, 예: 45)

    @SerializedName("remaining_seconds")
    val remainingSeconds: Int = 0, // 예상 남은 시간 (초)

    @SerializedName("progress_percent")
    val progressPercent: Int = 0, // 진행률 (0 ~ 100%)

    @SerializedName("stage")
    val stage: String = "", // 단계 안내 문구

    @SerializedName("queue_position")
    val queuePosition: Int? = null, // 대기열 순번 (1부터 시작, 1이 다음 차례)

    @SerializedName("eta_seconds")
    val etaSeconds: Int? = null // 예상 대기 시간 (초)
)

/**
 * 3. 세션 상태 조회 응답 DTO
 * GET api/sessions/{id}/status
 */
data class SessionStatusResponse(
    @SerializedName("session_id")
    val sessionId: String? = null,

    @SerializedName("status")
    val status: String, // "CREATED", "PAIRED", "UPLOADED", "PROCESSING", "DONE", "FAILED"

    @SerializedName("fail_reason")
    val failReason: String? = null,

    @SerializedName("fail_code")
    val failCode: String? = null,

    @SerializedName("progress")
    val progress: ProcessingProgressInfo? = null
)

/**
 * Colab 워커 / 백엔드 에러 코드 -> 사용자 안내 문구 매퍼
 */
object ScanErrorCodeMapper {
    private val ERROR_MESSAGES = mapOf(
        "FACE_NOT_FOUND" to "영상에서 얼굴을 인식하지 못했습니다. 밝은 곳에서 정면을 바라보고 다시 촬영해 주세요.",
        "TOO_BLURRY" to "영상이 흔들리거나 흐리게 촬영되었습니다. 카메라를 고정하고 천천히 다시 촬영해 주세요.",
        "ANGLE_RANGE_TOO_NARROW" to "촬영 각도 범위가 부족합니다. 좌측(-60°)에서 우측(+60°)까지 곡선으로 천천히 회전해 주세요.",
        "FACE_TOO_CLOSE" to "얼굴이 너무 가깝습니다. 조금 멀리서 찍어 주세요.",
        "GPU_OOM" to "서버 그래픽 처리 용량이 일시적으로 부족합니다. 잠시 후 다시 시도해 주세요.",
        "TIMEOUT" to "3D 모델 생성 시간이 제한(6분)을 초과했습니다. 잠시 후 다시 시도해 주세요.",
        "VIDEO_DOWNLOAD_FAILED" to "서버가 업로드된 영상을 불러오지 못했습니다. 다시 시도해 주세요.",
        "UPLOAD_FAILED" to "생성된 3D 모델 저장에 실패했습니다. 잠시 후 다시 시도해 주세요.",
        "RECONSTRUCTION_FAILED" to "3D 얼굴 모델 생성에 실패했습니다. 가이드를 확인한 뒤 다시 촬영해 주세요.",
        "WORKER_RESTARTED" to "3D 변환 서버가 재시작되어 작업이 중단되었습니다. 다시 시도해 주세요.",
        "WORKER_LOST" to "3D 변환 서버와의 연결이 끊어졌습니다. 다시 시도해 주세요."
    )

    fun toMessage(failReason: String?): String {
        val raw = failReason?.trim().orEmpty()
        if (raw.isEmpty()) return "3D 복원 중 오류가 발생했습니다. 다시 시도해 주세요."
        return ERROR_MESSAGES[raw.uppercase()] ?: raw
    }
}

/**
 * 4. 3D GLB 모델 결과 서명 다운로드 URL 응답 DTO
 * GET api/sessions/{id}/result
 */
data class SessionResultResponse(
    @SerializedName(value = "model_url", alternate = ["download_url"])
    val downloadUrl: String // Supabase Storage GLB 다운로드 5분 유효 서명 URL
)

/**
 * 태블릿 UI 및 Repository에서 구독(Collect)하여 사용할 세션 상태 모델
 */
data class SessionStatus(
    val status: String,
    val failReason: String? = null,
    val downloadUrl: String? = null,
    val progress: ProcessingProgressInfo? = null
) {
    val isDone: Boolean get() = status == STATUS_DONE
    val isFailed: Boolean get() = status == STATUS_FAILED

    companion object {
        const val STATUS_IDLE = "IDLE"
        const val STATUS_CREATED = "CREATED"
        const val STATUS_PAIRED = "PAIRED"
        const val STATUS_UPLOADED = "UPLOADED"
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
    }
}

/**
 * 스캔 세션 내역 관리 모델 (태블릿에서 뒤로가기 시에도 작업 유지 및 이력 관리)
 */
data class ScanHistoryItem(
    val sessionId: String,
    val sessionSecret: String,
    val pairingCode: String,
    val createdAt: Long = System.currentTimeMillis(),
    var status: String = "CREATED",
    var progressPercent: Int = 0,
    var remainingSeconds: Int = 0,
    var stageMessage: String = "",
    var glbDownloadUrl: String? = null,
    var localGlbPath: String? = null,
    var savedTempServerSn: Int? = null,
    var failReason: String? = null
) {
    val isDone: Boolean get() = status == "DONE"
    val isFailed: Boolean get() = status == "FAILED"
    val isProcessing: Boolean get() = status in listOf("PAIRED", "UPLOADED", "PROCESSING")
}

/**
 * 5. 페어링 요청/응답 DTO (POST api/sessions/pair)
 */
data class PairSessionRequest(
    @SerializedName("code")
    val code: String
)

data class PairSessionResponse(
    @SerializedName("session_id")
    val sessionId: String,

    @SerializedName("pair_token")
    val pairToken: String
)

/**
 * 6. 업로드 Presign URL 응답 DTO (POST api/uploads/presign)
 */
data class PresignUploadResponse(
    @SerializedName("upload_path")
    val uploadPath: String? = null,

    @SerializedName(value = "signed_url", alternate = ["upload_url"])
    val signedUrl: String
)

/**
 * 7. 업로드 완료 보고 응답 DTO (POST api/uploads/complete)
 */
data class CompleteUploadResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("queue_full")
    val queueFull: Boolean = false,

    @SerializedName("message")
    val message: String? = null
)

/**
 * 8. Colab 워커 헬스 체크 응답 DTO (GET api/worker/health)
 */
data class WorkerHealthResponse(
    @SerializedName("alive")
    val alive: Boolean = false,

    @SerializedName("status")
    val status: String = "UNKNOWN",

    @SerializedName("message")
    val message: String = "",

    @SerializedName("busy")
    val busy: Boolean? = null,

    @SerializedName("queue")
    val queue: Int? = null
)
