package com.tangoplus.tangoface.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * 3D 얼굴 스캔 백엔드 통신용 Retrofit 인터페이스
 */
interface FaceScanApiService {

    // 1. 태블릿 세션 생성 (6자리 번호 발급)
    @POST("api/sessions")
    suspend fun createSession(
        @Header("X-Tablet-Key") tabletKey: String
    ): Response<CreateSessionResponse>

    // 2. 세션 상태 조회 (폴링용)
    @GET("api/sessions/{id}/status")
    suspend fun getSessionStatus(
        @Path("id") sessionId: String,
        @Header("X-Session-Secret") sessionSecret: String
    ): Response<SessionStatusResponse>

    // 3. 3D GLB 모델 결과 서명 다운로드 URL 요청 (상태가 DONE일 때 호출)
    @GET("api/sessions/{id}/result")
    suspend fun getSessionResult(
        @Path("id") sessionId: String,
        @Header("X-Session-Secret") sessionSecret: String
    ): Response<SessionResultResponse>

    // 4. 서명된 URL로부터 3D GLB 바이너리 파일 스트리밍 다운로드
    @Streaming
    @GET
    suspend fun downloadGlbFile(
        @Url downloadUrl: String
    ): Response<ResponseBody>

    // 5. 태블릿 직접 촬영 시 6자리 PIN으로 pair_token 발급
    @POST("api/sessions/pair")
    suspend fun pairSession(
        @Body request: PairSessionRequest
    ): Response<PairSessionResponse>

    // 6. Supabase Storage Signed Upload URL 요청
    @POST("api/uploads/presign")
    suspend fun presignUpload(
        @Header("Authorization") bearerToken: String
    ): Response<PresignUploadResponse>

    // 7. 영상 업로드 완료 검증 및 Colab 3D 복원 시작 요청
    @POST("api/uploads/complete")
    suspend fun completeUpload(
        @Header("Authorization") bearerToken: String
    ): Response<CompleteUploadResponse>

    // 8. Colab 워커 상태 조회
    @GET("api/worker/health")
    suspend fun getWorkerHealth(): Response<WorkerHealthResponse>
}
