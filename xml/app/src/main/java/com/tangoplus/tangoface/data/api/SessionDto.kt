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
 * 2. 세션 상태 조회 응답 DTO
 * GET api/sessions/{id}/status
 */
data class SessionStatusResponse(
    @SerializedName("status")
    val status: String, // "CREATED", "PAIRED", "UPLOADED", "PROCESSING", "DONE", "FAILED"

    @SerializedName("fail_reason")
    val failReason: String? = null
)

/**
 * 3. 3D GLB 모델 결과 서명 다운로드 URL 응답 DTO
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
    val downloadUrl: String? = null
) {
    val isDone: Boolean get() = status == STATUS_DONE
    val isFailed: Boolean get() = status == STATUS_FAILED

    companion object {
        const val STATUS_CREATED = "CREATED"
        const val STATUS_PAIRED = "PAIRED"
        const val STATUS_UPLOADED = "UPLOADED"
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
    }
}
