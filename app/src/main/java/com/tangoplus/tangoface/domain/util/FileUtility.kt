package com.tangoplus.tangoface.domain.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.JsonWriter
import android.util.Log
import android.view.View
import com.google.gson.Gson
import com.tangoplus.tangoface.data.db.FaceStatic
import com.tangoplus.tangoface.domain.vo.Sequence
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileUtility {
	fun getFileName(seq: Sequence, time: String? = null): String {
		val timestamp = time ?: LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
		return when (seq) {
			Sequence.COMFORT -> "0-$timestamp"
//			Sequence.RESTING -> "2-$timestamp"
			Sequence.OCCLUSAL -> "1-$timestamp"
			Sequence.JAW_LEFT_TILT -> "2-$timestamp"
			Sequence.JAW_RIGHT_TILT -> "3-$timestamp"
			Sequence.NECK_EXTENTION -> "6-$timestamp"
			Sequence.BOW -> "5-$timestamp"
			Sequence.JAW_OPENING -> "4-$timestamp"
//			Sequence.SIDE_LEFT -> "8-$timestamp"
//			Sequence.SIDE_RIGHT -> "9-$timestamp"
		}
	}
	fun getMoireFileName(seq: Sequence, time: String? = null): String {
		val timestamp = time ?: LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
		return when (seq) {
			Sequence.COMFORT -> "0-moire-$timestamp"
//			Sequence.RESTING -> "2-$timestamp"
			Sequence.OCCLUSAL -> "1-moire-$timestamp"
			Sequence.JAW_LEFT_TILT -> "2-moire-$timestamp"
			Sequence.JAW_RIGHT_TILT -> "3-moire-$timestamp"
			Sequence.NECK_EXTENTION -> "6-moire-$timestamp"
			Sequence.BOW -> "5-moire-$timestamp"
			Sequence.JAW_OPENING -> "4-moire-$timestamp"
//			Sequence.SIDE_LEFT -> "8-$timestamp"
//			Sequence.SIDE_RIGHT -> "9-$timestamp"
		}
	}

	fun View.setOnSingleClickListener(action: (v: View) -> Unit) {
		val listener = View.OnClickListener { action(it) }
		setOnClickListener(OnSingleClickListener(listener))
	}

	fun getPathFromContentUri(context: Context, contentUri: Uri): String? {
		var filePath: String? = null
		var cursor: Cursor? = null
		try {
			// 쿼리할 컬럼을 정의합니다. MediaStore.Images.Media.DATA는 실제 파일 경로를 나타냅니다.
			val projection = arrayOf(MediaStore.Images.Media.DATA)

			// ContentResolver를 사용하여 MediaStore를 쿼리합니다.
			cursor = context.contentResolver.query(contentUri, projection, null, null, null)

			// 커서가 유효하고 첫 번째 결과로 이동할 수 있다면
			if (cursor != null && cursor.moveToFirst()) {
				// _DATA 컬럼의 인덱스를 가져옵니다.
				val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
				// 해당 인덱스의 문자열 값을 가져와 filePath에 저장합니다.
				filePath = cursor.getString(columnIndex)
			}
		} catch (e: Exception) {
			e.printStackTrace()
			// 예외 처리: 예를 들어, 권한이 없거나 URI가 유효하지 않을 때 발생할 수 있습니다.
		} finally {
			// 커서를 닫아 리소스를 해제합니다.
			cursor?.close()
		}
		return filePath
	}

	fun readJsonFromUri(context: Context, uri: Uri): JSONObject {
		return context.contentResolver.openInputStream(uri)?.use { inputStream ->
			val jsonString = inputStream.bufferedReader().use { it.readText() }
			JSONObject(jsonString)
		} ?: JSONObject()
	}

	fun getVideoUriFromFileName(context: Context, fileName: String): Uri? {
		val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
		val projection = arrayOf(MediaStore.Video.Media._ID)
		val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ?"
		val selectionArgs = arrayOf(fileName)

		context.contentResolver.query(
			collection,
			projection,
			selection,
			selectionArgs,
			null
		)?.use { cursor ->
			if (cursor.moveToFirst()) {
				val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
				val id = cursor.getLong(idColumn)
				return ContentUris.withAppendedId(collection, id)
			}
		}
		return null
	}

	fun getImageUriFromFileName(context: Context, fileName: String): Uri? {
		val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
		val projection = arrayOf(MediaStore.Images.Media._ID)
		val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
		val selectionArgs = arrayOf(fileName)

		context.contentResolver.query(
			collection,
			projection,
			selection,
			selectionArgs,
			null
		)?.use { cursor ->
			if (cursor.moveToFirst()) {
				val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
				return ContentUris.withAppendedId(collection, id)
			}
		}
		return null
	}

	fun getJsonUriFromFileName(context: Context, fileName: String): Uri? {
		val collection = MediaStore.Files.getContentUri("external")
		val projection = arrayOf(MediaStore.Files.FileColumns._ID)
		val selection =
			"${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
		val selectionArgs = arrayOf(fileName, "Documents/TangoPlus/") // 경로 끝에 `/` 포함해야 안정적
		context.contentResolver.query(
			collection,
			projection,
			selection,
			selectionArgs,
			null
		)?.use { cursor ->
			if (cursor.moveToFirst()) {
				val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
				return ContentUris.withAppendedId(collection, id)
			}
		}
		return null
	}

	fun readJsonFromFile(fileName: String): JSONObject? {
		return try {
			// fileName 유효성 체크
			if (fileName.isBlank()) {
				Log.e("JSON", "파일명이 비어있음")
				return null
			}

			val file = File(
				Environment.getExternalStorageDirectory(),
				"Documents/Tangoplus/$fileName"
			)

			// 파일 존재 여부 + 디렉토리가 아닌지 체크
			if (!file.exists() || !file.isFile) {
				Log.e("JSON", "파일 없음 또는 디렉토리: ${file.absolutePath}")
				return null
			}

			val jsonString = file.readText()
			Log.d("JSON", "파일 읽기 성공: ${file.name}")
			JSONObject(jsonString)
		} catch (e: Exception) {
			Log.e("JSON", "파일 읽기 실패: ${e.message}", e)
			null
		}
	}

	fun JSONObject?.toFaceStatic(): FaceStatic {
		return Gson().fromJson(this.toString(), FaceStatic::class.java)
	}

	fun FaceStatic?.toJsonString(): String = Gson().toJson(this)
	fun writeJsonValue(w: JsonWriter, value: Any?) {
		when (value) {
			null, JSONObject.NULL -> w.nullValue()

			is JSONObject -> writeJsonObject(w, value)
			is JSONArray -> writeJsonArray(w, value)

			is Boolean -> w.value(value)
			is Int -> w.value(value.toLong())
			is Long -> w.value(value)
			is Double -> w.value(value)
			is Float -> w.value(value.toDouble())
			is Number -> w.value(value.toDouble())

			is String -> w.value(value)

			else -> {
				// JSONObject/JSONArray 안에 예상치 못한 타입이 들어올 때 방어
				w.value(value.toString())
			}
		}
	}

	private fun writeJsonObject(w: JsonWriter, obj: JSONObject) {
		w.beginObject()
		val keys = obj.keys()
		while (keys.hasNext()) {
			val key = keys.next()
			w.name(key)
			writeJsonValue(w, obj.opt(key))
		}
		w.endObject()
	}

	private fun writeJsonArray(w: JsonWriter, arr: JSONArray) {
		w.beginArray()
		for (i in 0 until arr.length()) {
			writeJsonValue(w, arr.opt(i))
		}
		w.endArray()
	}


	// --------------------------------------# 백업 기능 #-------------------------------------------
	fun deleteExistingBackupFiles(context: Context) {
		try {
			val projection = arrayOf(
				MediaStore.Files.FileColumns._ID,
				MediaStore.Files.FileColumns.DISPLAY_NAME
			)

			val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? AND " +
				"${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
			val selectionArgs = arrayOf("Documents/TangoPlusBackup/", "tango_face_general_backup_%.zip")

			val cursor = context.contentResolver.query(
				MediaStore.Files.getContentUri("external"),
				projection,
				selection,
				selectionArgs,
				null
			)

			cursor?.use {
				val idColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
				val nameColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)

				while (it.moveToNext()) {
					val id = it.getLong(idColumn)
					val name = it.getString(nameColumn)
					val uri = MediaStore.Files.getContentUri("external").buildUpon()
						.appendPath(id.toString()).build()

					val deleted = context.contentResolver.delete(uri, null, null)
					if (deleted > 0) {
						Log.d("Backup", "기존 백업 파일 삭제: $name")
					}
				}
			}
		} catch (e: Exception) {
			Log.e("Backup", "기존 백업 파일 삭제 실패", e)
		}
	}

	private fun deleteIfExists(context: Context, fileName: String) {
		val collection = MediaStore.Files.getContentUri("external")
		val projection = arrayOf(MediaStore.MediaColumns._ID)
		val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
		val selectionArgs = arrayOf(fileName)

		context.contentResolver.query(collection, projection, selection, selectionArgs, null)
			?.use { cursor ->
				val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
				while (cursor.moveToNext()) {
					val id = cursor.getLong(idCol)
					val uri = ContentUris.withAppendedId(collection, id)
					val deleted = context.contentResolver.delete(uri, null, null)
					Log.d("Backup", "기존 파일 삭제: $fileName ($deleted)")
				}
			}
	}

	fun backupUserData(context: Context) {
		val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
		val backupFileName = "tango_face_expert_backup_$dateStr.zip"

		try {
			// 기존 백업 파일들 삭제
			deleteIfExists(context, backupFileName)
			deleteExistingBackupFiles(context)

			// MediaStore를 통해 Documents 폴더에 ZIP 파일 생성
			val contentValues = ContentValues().apply {
				put(MediaStore.MediaColumns.DISPLAY_NAME, backupFileName)
				put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
				put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Documents/TangoPlusBackup")
			}
			val uri =
				context.contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

			uri?.let { fileUri ->
				context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
					ZipOutputStream(outputStream).use { zipOut ->

						// 압축 대상 디렉토리들
						val dbDir = File("/data/data/${context.packageName}/databases")
						val pictureDir = File(
							Environment.getExternalStoragePublicDirectory(
								Environment.DIRECTORY_PICTURES
							), "TangoPlus"
						)
						val documentsJsonDir = File(
							Environment.getExternalStoragePublicDirectory(
								Environment.DIRECTORY_DOCUMENTS
							), "TangoPlus"
						)
						val appInternalJsonDir = File(context.filesDir, "json")

						// 1. 데이터베이스 파일들 압축
						if (dbDir.exists() && dbDir.canRead()) {
							addDirectoryToZip(zipOut, dbDir, "databases/")
							Log.d("Backup", "Database 파일 압축 완료")
						}

						// 2. Pictures 파일들 압축
						if (pictureDir.exists() && pictureDir.canRead()) {
							addDirectoryToZip(zipOut, pictureDir, "pictures/")
							Log.d("Backup", "Picture 파일 압축 완료")
						}

						// 3. Documents JSON 파일들 압축
						if (documentsJsonDir.exists() && documentsJsonDir.canRead()) {
							addDirectoryToZip(zipOut, documentsJsonDir, "documents/")
							Log.d("Backup", "Documents JSON 파일 압축 완료")
						}
					}
				}

				Log.d("Backup", "MediaStore 백업 완료: $backupFileName")
				Log.d("Backup", "URI: $fileUri")

			} ?: throw Exception("MediaStore URI 생성 실패")

		} catch (e: Exception) {
			Log.e("Backup", "MediaStore 백업 실패", e)
		}
	}

	private fun addDirectoryToZip(zipOut: ZipOutputStream, sourceDir: File, basePath: String) {
		if (!sourceDir.exists() || !sourceDir.canRead()) {
			Log.w("Backup", "디렉토리 접근 불가: ${sourceDir.absolutePath}")
			return
		}

		val files = sourceDir.listFiles() ?: return

		for (file in files) {
			val entryPath = basePath + file.name

			if (file.isDirectory) {
				if (file.canRead()) {
					// 빈 폴더도 ZIP에 추가
					val entry = ZipEntry("$entryPath/")
					zipOut.putNextEntry(entry)
					zipOut.closeEntry()

					// 재귀적으로 하위 폴더 처리
					addDirectoryToZip(zipOut, file, "$entryPath/")
				}
			} else if (file.canRead()) {
				// 파일을 ZIP에 추가
				addFileToZip(zipOut, file, entryPath)
			}
		}
	}

	private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
		try {
			val entry = ZipEntry(entryName)
			entry.time = file.lastModified()
			zipOut.putNextEntry(entry)

			FileInputStream(file).use { fis ->
				val buffer = ByteArray(8192)
				var bytesRead: Int
				while (fis.read(buffer).also { bytesRead = it } != -1) {
					zipOut.write(buffer, 0, bytesRead)
				}
			}

			zipOut.closeEntry()
			Log.d("Backup", "파일 추가 완료: $entryName (${file.length()} bytes)")

		} catch (e: Exception) {
			Log.e("Backup", "파일 압축 실패: ${file.name}", e)
		}
	}

	suspend fun waitForFileReady(file: File, timeoutMs: Long = 3000): Boolean {
		val start = System.currentTimeMillis()
		var lastSize = -1L
		var stableCount = 0

		Log.v("FileWait", "▶ 파일 준비 대기 시작: ${file.path}")
		while (System.currentTimeMillis() - start < timeoutMs) {
			val curSize = file.length()
			Log.v(
				"FileWait",
				"현재 파일 크기: $curSize bytes (이전: $lastSize bytes)"
			)

			if (curSize > 0 && curSize == lastSize) {
				stableCount++
				Log.v("FileWait", "파일 크기 변화 없음 ($stableCount 회)")
				if (stableCount >= 3) { // 3번 연속 동일하면 안정화된 것으로 간주
					Log.v("FileWait", "✅ 파일 크기 안정화됨 → 준비 완료")
					return true
				}
			} else {
				stableCount = 0
			}

			lastSize = curSize
			delay(200)
		}

		Log.e("FileWait", "⛔ 파일 준비 실패 또는 타임아웃 (${timeoutMs}ms 초과)")
		return false
	}
	fun saveImageToInternalStorage(context: Context, uri: Uri, fileName: String): String? {
		return try {
			val inputStream = context.contentResolver.openInputStream(uri)
			val file = File(context.filesDir, fileName) // 앱 내부 저장소 경로
			val outputStream = FileOutputStream(file)

			inputStream?.use { input ->
				outputStream.use { output ->
					input.copyTo(output)
				}
			}
			file.absolutePath // 저장된 파일의 로컬 경로 반환
		} catch (e: Exception) {
			e.printStackTrace()
			null
		}
	}
}