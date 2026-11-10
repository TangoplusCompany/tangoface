package com.tangoplus.tangoface.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Next.js + Supabase 백엔드 연동을 위한 Retrofit 클라이언트 설정
 */
object FaceScanApiClient {
    // 프로덕션 기본 URL (Retrofit baseUrl은 항상 '/'로 끝나야 함)
    const val BASE_URL = "https://tangoface-backend.vercel.app/"

    // 안드로이드 에뮬레이터 로컬 테스트 URL
    const val LOCAL_EMULATOR_URL = "http://10.0.2.2:3000/"

    /**
     * strings.xml에 정의된 x_tablet_key 조회 (하드코딩 방지)
     */
    fun getTabletKey(context: android.content.Context? = com.tangoplus.tangoface.orbbec.application.MyApplication.getInstance()): String {
        return try {
            context?.getString(com.tangoplus.tangoface.R.string.x_tablet_key) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // 타임아웃 설정 (초)
    private const val CONNECT_TIMEOUT_SEC = 15L
    private const val READ_TIMEOUT_SEC = 30L
    private const val WRITE_TIMEOUT_SEC = 30L

    /**
     * OkHttpClient 인스턴스 생성 (HttpLoggingInterceptor 적용)
     */
    fun createOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * FaceScanApiService 생성
     * @param baseUrl 커스텀 백엔드 URL이 필요할 경우 전달 (기본: BASE_URL)
     */
    fun create(baseUrl: String = BASE_URL): FaceScanApiService {
        val formattedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(createOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FaceScanApiService::class.java)
    }

    /**
     * 기본 싱글톤 서비스 인스턴스
     */
    val service: FaceScanApiService by lazy {
        create(BASE_URL)
    }
}

/**
 * API 호출 실패 시 상세 정보를 담는 예외 클래스
 */
class ApiException(
    val statusCode: Int,
    override val message: String,
    val errorBody: String? = null
) : Exception("API Error [$statusCode] $message: ${errorBody ?: ""}")

/**
 * 401 권한 없음 (태블릿 키 또는 세션 시크릿 불일치) 예외 클래스
 */
class UnauthorizedException(
    override val message: String = "인증 실패 (401): 태블릿 키 또는 세션 시크릿이 유효하지 않습니다."
) : Exception(message)
