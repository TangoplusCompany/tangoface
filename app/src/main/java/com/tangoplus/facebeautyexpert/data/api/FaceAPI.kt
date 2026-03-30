package com.tangoplus.facebeautyexpert.data.api

object FaceAPI {
//    suspend fun sendFaceResult(
//        myUrl: String,
//        parts: List<MultipartBody.Part>,
//    ): Result<JSONObject> = withContext(Dispatchers.IO) {
//        try {
//            val baseUrl = myUrl.substringBeforeLast("/") + "/" // Retrofit은 baseUrl이 필요함
//            val path = myUrl.removePrefix(baseUrl)             // 나머지 경로를 @Url로 전달
//
//            val service = RetrofitClient.create(baseUrl)
//            val response = service.sendFaceResult(path, parts)
//
//            if (!response.isSuccessful) {
//                val err = response.errorBody()?.string()
//                return@withContext Result.failure(Exception("Failed: ${response.code()} $err"))
//            }
//
//            val bodyStr = response.body()?.string() ?: ""
//            val bodyJo = JSONObject(bodyStr)
//
//            return@withContext Result.success(bodyJo)
//        } catch (e: Exception) {
//            Log.e("sendMeasureDataRetrofit", "Error: ${e.message}")
//            return@withContext Result.failure(e)
//        }
//    }
//
//    suspend fun getFaceResultDetail(
//        myUrl: String,
//        inputBody : JSONObject
//    ) : Result<JSONObject> = withContext(Dispatchers.IO) {
//        try {
//            val baseUrl = myUrl.substringBeforeLast("/") + "/"
//            val path = myUrl.removePrefix(baseUrl)
//
//            val service = RetrofitClient.create(baseUrl)
//            val response = service.getFaceResultDetail(path, inputBody)
//
//            if (!response.isSuccessful) {
//                val err = response.errorBody()?.string()
//                return@withContext Result.failure(Exception("Failed ${response.code()} $err"))
//            }
//            val bodyString = response.body()?.string() ?: ""
//            val bodyJo = JSONObject(bodyString)
//            return@withContext Result.success(bodyJo)
//        } catch (e: SocketTimeoutException) {
//            Log.e("getFaceResultDetail", "SocketTimeoutException: ${e.message}")
//            Result.failure(SocketTimeoutException("Failed ${e.message}"))
//        } catch (e: IllegalStateException) {
//            Log.e("getFaceResultDetail", "IllegalStateException: ${e.message}")
//            Result.failure(IllegalStateException("Failed ${e.message}"))
//        } catch (e: Exception) {
//            Log.e("getFaceResultDetail", "Exception: ${e.message}")
//            Result.failure(Exception("Failed ${e.message}"))
//        }
//    }
}