package com.tangoplus.tangoface_compose.data.api.retrofit

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
    ): Response<ResponseBody>

    @GET
    suspend fun getFaceResultsDisplay(
        @Url url: String
    ): Response<ResponseBody>

    @POST("users")
    suspend fun getFaceResultDetail(
        @Url url: String,
        @Body serverSn: JSONObject
    ): Response<ResponseBody>
}

