package com.tangoplus.tangoface.data.api.retrofit

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

interface FaceApiService {
    @Multipart
    @POST
    suspend fun sendFaceResult(
        @Url url: String,
        @Part parts: List<MultipartBody.Part>
    ) : Response<ResponseBody>

    @GET
    suspend fun getFaceResultsDisplay(
        @Url url: String,
    ) : Response<ResponseBody>

    @POST("users")
    suspend fun getFaceResultDetail(
        @Url url : String,
        @Body serverSn: JSONObject
    ) : Response<ResponseBody>

    // TODO 여기서 토큰을 발급받을 계획이면 로그인/회원가입/회원탈퇴에 맞는 회원가입 방식을 만들어야함 (playStore에 관리자용 code를 넣기.)
}