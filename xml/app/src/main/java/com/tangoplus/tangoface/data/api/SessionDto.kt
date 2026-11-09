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
    val elapsedSeconds: Int, // 경과 시간 (초)

    @SerializedName("estimated_total_seconds")
    val estimatedTotalSeconds: Int, // 총 예상 소요 시간 (초, 예: 45)

    @SerializedName("remaining_seconds")
    val remainingSeconds: Int, // 예상 남은 시간 (초)

    @SerializedName("progress_percent")
    val progressPercent: Int, // 진행률 (0 ~ 100%)

    @SerializedName("stage")
    val stage: String // 단계 안내 문구 ("1단계: 특징점 추출 중...", 등)
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

    @SerializedName("progress")
    val progress: ProcessingProgressInfo? = null
)

/**
 * 4. 3D GLB 모델 결과 서명 다운로드 URL 응답 DTO
 * GET api/sessions/{id}/result
 */
data class SessionResultResponse(
    @SerializedName("download_url")
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
    var failReason: String? = null
) {
    val isDone: Boolean get() = status == "DONE"
    val isFailed: Boolean get() = status == "FAILED"
    val isProcessing: Boolean get() = status in listOf("PAIRED", "UPLOADED", "PROCESSING")
}
