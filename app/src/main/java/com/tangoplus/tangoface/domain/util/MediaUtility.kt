package com.tangoplus.tangoface.domain.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Log
import org.json.JSONObject
import kotlin.let

object MediaUtility {
  fun extractVideoCoordinates(jsonData: JSONObject): List<List<Pair<Float, Float>>> {
    val faceLandmarks = jsonData.getJSONArray("face_landmark")

    return List(faceLandmarks.length()) { i ->
      val frameLandmarks = faceLandmarks.getJSONArray(i)

      List(frameLandmarks.length()) { j ->
        val landmark = frameLandmarks.getJSONArray(j)
        Pair(
          landmark.getDouble(1).toFloat(),  // x
          landmark.getDouble(2).toFloat()   // y
        )
      }
    }
  }

  fun saveDebugBitmap(context: Context, bitmap: Bitmap, seqName: String) {
    try {
      val fileName = "DEBUG_${seqName}_${System.currentTimeMillis()}.jpg"
      val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DebugTango")
      }

      val imageUri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
      )

      imageUri?.let { uri ->
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
          // 현재 상태 그대로 퀄리티 100으로 저장
          bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
          Log.d("DebugBitmap", "✅ 디버그 이미지 저장 완료: Pictures/DebugTango/$fileName")
        }
      }
    } catch (e: Exception) {
      Log.e("DebugBitmap", "❌ 디버그 이미지 저장 실패: ${e.message}")
    }
  }
}