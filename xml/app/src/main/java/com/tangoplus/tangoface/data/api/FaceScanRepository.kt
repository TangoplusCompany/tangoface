package com.tangoplus.tangoface.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 3D 얼굴 스캔 세션 관리, 상태 폴링 및 GLB 다운로드 Repository 인터페이스
 */
interface FaceScanRepository {

    /**
     * 태블릿 세션 생성 (스마트폰 연동용 6자리 페어링 번호 발급)
     * @param tabletKey 태블릿 인증 비밀키
     * @return Result<CreateSessionResponse> (성공 시 sessionId, code, sessionSecret 포함)
     */
    suspend fun createScanSession(
        tabletKey: String = FaceScanApiClient.getTabletKey()
    ): Result<CreateSessionResponse>

    /**
     * 세션 상태를 2초 간격으로 실시간 폴링하는 Flow
     * - DONE 또는 FAILED가 되면 루프 자동 종료
     * - DONE 상태 감지 시 자동으로 결과 GLB 서명 URL을 조회하여 downloadUrl과 함께 emit
     *
     * @param sessionId 발급받은 세션 ID
     * @param sessionSecret 태블릿 인증 비밀 토큰
     * @param intervalMillis 폴링 주기 (기본값: 2000ms = 2초)
     * @return Flow<SessionStatus>
     */
    fun pollSessionStatus(
        sessionId: String,
        sessionSecret: String,
        intervalMillis: Long = 2000L
    ): Flow<SessionStatus>

    /**
     * 서명된 downloadUrl로부터 3D GLB 모델 파일을 기기 로컬 저장소로 다운로드
     *
     * @param downloadUrl 5분 유효 서명된 Supabase Storage 다운로드 URL
     * @param destinationFile 저장할 로컬 대상 파일 경로 (예: cacheDir/scan_xxx.glb)
     * @param onProgress 다운로드 진행률 콜백 (다운로드 바이트 수, 총 바이트 수)
     * @return Result<File> 저장 완료된 File 객체
     */
    suspend fun downloadGlb(
        downloadUrl: String,
        destinationFile: File,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long) -> Unit)? = null
    ): Result<File>

    /**
     * 태블릿 직접 촬영 업로드 파이프라인용 API
     */
    suspend fun pairSession(code: String): Result<PairSessionResponse>
    suspend fun presignUpload(pairToken: String): Result<PresignUploadResponse>
    suspend fun uploadVideoToSignedUrl(
        uploadUrl: String,
        videoFile: File,
        onProgress: ((percent: Int) -> Unit)? = null
    ): Result<Unit>
    suspend fun completeUpload(pairToken: String): Result<CompleteUploadResponse>
    suspend fun checkWorkerHealth(): Result<WorkerHealthResponse>
}

/**
 * FaceScanRepository 기본 구현체
 */
class FaceScanRepositoryImpl(
    private val apiService: FaceScanApiService = FaceScanApiClient.service
) : FaceScanRepository {

    /**
     * 1. 태블릿 세션 생성 API 호출
     */
    override suspend fun createScanSession(
        tabletKey: String
    ): Result<CreateSessionResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.createSession(tabletKey)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(ApiException(response.code(), "응답 본문이 비어있습니다."))
                }
            } else {
                val code = response.code()
                val errorBody = response.errorBody()?.string()
                val exception = if (code == 401) {
                    UnauthorizedException("태블릿 키 인증에 실패했습니다 (401 Unauthorized)")
                } else {
                    ApiException(code, "세션 생성 실패", errorBody)
                }
                Result.failure(exception)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 2. 세션 상태 주기적 폴링 및 결과 GLB URL 자동 수신 Flow
     */
    override fun pollSessionStatus(
        sessionId: String,
        sessionSecret: String,
        intervalMillis: Long
    ): Flow<SessionStatus> = flow {
        while (currentCoroutineContext().isActive) {
            val statusResponse = apiService.getSessionStatus(
                sessionId = sessionId,
                sessionSecret = sessionSecret
            )

            if (!statusResponse.isSuccessful) {
                val code = statusResponse.code()
                val errorBody = statusResponse.errorBody()?.string()
                if (code == 401) {
                    throw UnauthorizedException("세션 시크릿 인증 실패 (401 Unauthorized)")
                } else {
                    throw ApiException(code, "세션 상태 조회 실패", errorBody)
                }
            }

            val statusBody = statusResponse.body()
                ?: throw ApiException(statusResponse.code(), "상태 응답 본문이 비어있습니다.")

            when (statusBody.status.uppercase()) {
                SessionStatus.STATUS_DONE -> {
                    // 상태가 DONE일 때 자동으로 3D GLB 결과 서명 URL 조회
                    val resultResponse = apiService.getSessionResult(
                        sessionId = sessionId,
                        sessionSecret = sessionSecret
                    )

                    if (!resultResponse.isSuccessful) {
                        val code = resultResponse.code()
                        val errorBody = resultResponse.errorBody()?.string()
                        throw ApiException(code, "3D GLB 결과 다운로드 URL 요청 실패", errorBody)
                    }

                    val downloadUrl = resultResponse.body()?.downloadUrl
                        ?: throw ApiException(resultResponse.code(), "다운로드 URL이 응답에 없습니다.")

                    // 최종 결과 방출 후 폴링 종료
                    emit(
                        SessionStatus(
                            status = SessionStatus.STATUS_DONE,
                            downloadUrl = downloadUrl
                        )
                    )
                    break
                }

                SessionStatus.STATUS_FAILED -> {
                    // 실패 상태 방출 후 폴링 종료
                    emit(
                        SessionStatus(
                            status = SessionStatus.STATUS_FAILED,
                            failReason = statusBody.failReason
                        )
                    )
                    break
                }

                else -> {
                    // 진행 중 상태 ("CREATED", "PAIRED", "UPLOADED", "PROCESSING" 등) 방출
                    emit(
                        SessionStatus(
                            status = statusBody.status,
                            failReason = statusBody.failReason,
                            progress = statusBody.progress
                        )
                    )
                    delay(intervalMillis)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 3. 3D GLB 파일 스트리밍 다운로드 및 로컬 디스크 저장
     */
    override suspend fun downloadGlb(
        downloadUrl: String,
        destinationFile: File,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long) -> Unit)?
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.downloadGlbFile(downloadUrl)
            if (!response.isSuccessful) {
                val code = response.code()
                val errorBody = response.errorBody()?.string()
                return@withContext Result.failure(
                    ApiException(code, "GLB 파일 다운로드 실패", errorBody)
                )
            }

            val responseBody = response.body()
                ?: return@withContext Result.failure(
                    ApiException(response.code(), "다운로드 응답 본문이 비어있습니다.")
                )

            // 부모 디렉토리 생성
            destinationFile.parentFile?.mkdirs()

            val totalBytes = responseBody.contentLength()
            var bytesDownloaded = 0L

            responseBody.byteStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                        onProgress?.invoke(bytesDownloaded, totalBytes)
                    }
                    output.flush()
                }
            }

            Result.success(destinationFile)
        } catch (e: Exception) {
            // 다운로드 실패 시 잔여 불완전 파일 정리
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            Result.failure(e)
        }
    }

    private val uploadHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun pairSession(code: String): Result<PairSessionResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.pairSession(PairSessionRequest(code))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(ApiException(response.code(), "페어링 실패", response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun presignUpload(pairToken: String): Result<PresignUploadResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.presignUpload("Bearer $pairToken")
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(ApiException(response.code(), "업로드 URL 발급 실패", response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadVideoToSignedUrl(
        uploadUrl: String,
        videoFile: File,
        onProgress: ((percent: Int) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val totalBytes = videoFile.length().coerceAtLeast(1L)
            val mediaType = "video/mp4".toMediaType()
            val body = object : RequestBody() {
                override fun contentType() = mediaType
                override fun contentLength() = totalBytes
                override fun writeTo(sink: BufferedSink) {
                    val buffer = ByteArray(16 * 1024)
                    var uploaded = 0L
                    var lastPercent = -1
                    FileInputStream(videoFile).use { input ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            sink.write(buffer, 0, read)
                            uploaded += read
                            val pct = ((uploaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (pct != lastPercent) {
                                lastPercent = pct
                                onProgress?.invoke(pct)
                            }
                        }
                    }
                }
            }

            val request = Request.Builder()
                .url(uploadUrl)
                .put(body)
                .header("Content-Type", "video/mp4")
                .build()

            uploadHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    onProgress?.invoke(100)
                    Result.success(Unit)
                } else {
                    Result.failure(ApiException(resp.code, "스토리지 업로드 실패", resp.body?.string()))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun completeUpload(pairToken: String): Result<CompleteUploadResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.completeUpload("Bearer $pairToken")
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(ApiException(response.code(), "업로드 완료 검증 실패", response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkWorkerHealth(): Result<WorkerHealthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getWorkerHealth()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(ApiException(response.code(), "서버 상태 조회 실패"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
