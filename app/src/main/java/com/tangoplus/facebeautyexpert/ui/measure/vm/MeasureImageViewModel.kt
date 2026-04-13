package com.tangoplus.facebeautyexpert.ui.measure.vm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
		val current = _imagePaths.value ?: mutableListOf<String?>(null, null)
		current[index] = path
		_imagePaths.value = current
	}

	fun removeImage(index: Int) {
		val current = _imagePaths.value ?: return
		current[index]?.let { File(it).delete() }
		current[index] = null
		_imagePaths.value = current
	}

	fun make169WithPadding(context: Context, sourceUri: Uri): Uri? {
		val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, sourceUri))
		} else {
			MediaStore.Images.Media.getBitmap(context.contentResolver, sourceUri)
		}.copy(Bitmap.Config.ARGB_8888, true)

		val targetWidth = 1920
		val targetHeight = 1080

		// 1. 1920x1080 검은색 도화지 생성
		val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(outputBitmap)
		canvas.drawColor(Color.BLACK) // 배경색

		// 2. 원본 비율 유지하며 크기 계산
		val scale = min(targetWidth.toFloat() / originalBitmap.width, targetHeight.toFloat() / originalBitmap.height)
		val finalW = (originalBitmap.width * scale).toInt()
		val finalH = (originalBitmap.height * scale).toInt()

		val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, finalW, finalH, true)

		// 3. 중앙 배치를 위한 좌표 계산
		val left = (targetWidth - finalW) / 2f
		val top = (targetHeight - finalH) / 2f

		canvas.drawBitmap(scaledBitmap, left, top, null)

		// 4. 파일로 저장 후 Uri 반환
		val tempFile = File(context.cacheDir, "processed_${System.currentTimeMillis()}.jpg")
		return try {
			val out = FileOutputStream(tempFile)
			outputBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
			out.flush()
			out.close()
			Uri.fromFile(tempFile)
		} catch (e: Exception) {
			null
		}
	}

}