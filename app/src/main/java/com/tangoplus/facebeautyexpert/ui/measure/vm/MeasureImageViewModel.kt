package com.tangoplus.facebeautyexpert.ui.measure.vm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

class MeasureImageViewModel : ViewModel() {
	private val _imagePaths = MutableLiveData<MutableList<String?>>(mutableListOf(null, null))
	val imagePaths: LiveData<MutableList<String?>> = _imagePaths

	var targetIndex: Int = 0 // 현재 작업 중인 인덱스 (0 또는 1)

	fun setImage(index: Int, path: String) {
		val current = _imagePaths.value?.toMutableList() ?: mutableListOf<String?>(null, null)
		current[index] = path
		_imagePaths.value = current
	}

	fun removeImage(index: Int) {

		val current = _imagePaths.value ?: return
		current[index]?.let { File(it).delete() }
		current[index] = null
		_imagePaths.value = current
		Log.v("초기화!", "이미지 list: $index 번 이미지 삭제: current[index] : ${current[index]}")
	}

	fun make169WithPadding(context: Context, sourceUri: Uri): Uri? {
		val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			// ImageDecoder는 EXIF 회전 정보를 자동으로 반영하여 편리합니다.
			ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, sourceUri)) { decoder, _, _ ->
				decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // Canvas 작업을 위해 소프트웨어 할당
			}
		} else {
			MediaStore.Images.Media.getBitmap(context.contentResolver, sourceUri)
		}.copy(Bitmap.Config.ARGB_8888, true)

		val targetWidth = 1920
		val targetHeight = 1080

		// 1. 결과물 비트맵 생성 및 검은색 배경
		val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(outputBitmap)
		canvas.drawColor(Color.TRANSPARENT)

		// 2. 비율 계산 및 Matrix 설정 (중간 비트맵 생성 방지)
		val scale = min(targetWidth.toFloat() / originalBitmap.width, targetHeight.toFloat() / originalBitmap.height)
		val xOffset = (targetWidth - originalBitmap.width * scale) / 2f
		val yOffset = (targetHeight - originalBitmap.height * scale) / 2f

		val matrix = Matrix().apply {
			postScale(scale, scale)
			postTranslate(xOffset, yOffset)
		}

		// 3. 그리기 및 메모리 해제
		canvas.drawBitmap(originalBitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
		originalBitmap.recycle() // 원본 메모리 즉시 해제

		// 4. 저장
		val tempFile = File(context.cacheDir, "tango_${System.currentTimeMillis()}.jpg")
		return try {
			FileOutputStream(tempFile).use { out ->
				outputBitmap.compress(Bitmap.CompressFormat.WEBP, 100, out)
			}
			outputBitmap.recycle()
			Uri.fromFile(tempFile)
		} catch (e: Exception) {
			null
		}
	}

}