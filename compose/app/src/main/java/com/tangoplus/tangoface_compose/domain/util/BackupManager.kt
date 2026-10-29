package com.tangoplus.tangoface_compose.domain.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import com.tangoplus.tangoface_compose.data.db.FaceDao
import com.tangoplus.tangoface_compose.data.db.FaceInsight
import com.tangoplus.tangoface_compose.data.db.FaceStatic
import com.tangoplus.tangoface_compose.data.db.PreCheckList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 측정 데이터 백업 및 복원 메타데이터
 */
data class BackupStats(
	val fileName: String,
	val targetPath: String,
	val staticsCount: Int,
	val insightsCount: Int,
	val checklistsCount: Int,
	val mediaFilesCount: Int,
	val formattedSize: String
)

data class RestoreStats(
	val staticsCount: Int,
	val insightsCount: Int,
	val checklistsCount: Int,
	val mediaFilesCount: Int
)

data class BackupArchivePayload(
	val exportDate: String = "",
	val appVersion: String = "1.0-alpha",
	val statics: List<FaceStatic> = emptyList(),
	val insights: List<FaceInsight> = emptyList(),
	val checkLists: List<PreCheckList> = emptyList()
)

object BackupManager {
	private const val TAG = "BackupManager"
	private val gson = Gson()

	/**
	 * 1. 백업 파일 생성 (.ZIP)
	 * - Room DB 데이터(FaceStatic, FaceInsight, PreCheckList)를 JSON으로 직렬화
	 * - Pictures/TangoPlus, Documents/TangoPlus, Movies/TangoPlus의 사진/영상/로그 압축
	 * - Documents/TangoPlusBackup/ 경로에 저장
	 */
	suspend fun createBackup(
		context: Context,
		faceDao: FaceDao
	): Result<BackupStats> = withContext(Dispatchers.IO) {
		runCatching {
			val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
			val backupFileName = "TangoFace_Backup_$timestamp.zip"

			// 1. DB 엔티티 로드
			val statics = faceDao.getAllStatics()
			val insights = faceDao.getAllInsights()
			val checklists = faceDao.getAllCheckListData()

			// 2. 미디어 디렉토리 확인
			val pictureDir = File(
				Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
				"TangoPlus"
			)
			val documentsDir = File(
				Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
				"TangoPlus"
			)
			val moviesDir = File(
				Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
				"TangoPlus"
			)
			val dbDir = File("/data/data/${context.packageName}/databases")

			// 미디어 파일 수 카운트
			var mediaCount = 0

			// 3. 임시 파일 생성 후 압축 스트림 구성
			val tempZipFile = File(context.cacheDir, backupFileName)
			if (tempZipFile.exists()) tempZipFile.delete()

			ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZipFile))).use { zipOut ->
				// (A) DB 데이터 JSON 아카이브
				val payload = BackupArchivePayload(
					exportDate = timestamp,
					appVersion = "1.0-alpha",
					statics = statics,
					insights = insights,
					checkLists = checklists
				)
				val jsonString = gson.toJson(payload)
				zipOut.putNextEntry(ZipEntry("backup_data.json"))
				zipOut.write(jsonString.toByteArray(Charsets.UTF_8))
				zipOut.closeEntry()

				// (B) 백업 메타데이터 JSON
				val infoJson = """
					{
						"title": "TangoFace Alpha Measurement Archive",
						"export_time": "$timestamp",
						"statics_count": ${statics.size},
						"insights_count": ${insights.size},
						"checklists_count": ${checklists.size}
					}
				""".trimIndent()
				zipOut.putNextEntry(ZipEntry("backup_info.json"))
				zipOut.write(infoJson.toByteArray(Charsets.UTF_8))
				zipOut.closeEntry()

				// (C) Pictures/TangoPlus 압축
				if (pictureDir.exists() && pictureDir.isDirectory) {
					mediaCount += addDirectoryToZip(zipOut, pictureDir, "pictures/")
				}

				// (D) Documents/TangoPlus 압축
				if (documentsDir.exists() && documentsDir.isDirectory) {
					mediaCount += addDirectoryToZip(zipOut, documentsDir, "documents/")
				}

				// (E) Movies/TangoPlus 압축
				if (moviesDir.exists() && moviesDir.isDirectory) {
					mediaCount += addDirectoryToZip(zipOut, moviesDir, "movies/")
				}

				// (F) 원본 SQLite 파일 보존
				if (dbDir.exists() && dbDir.isDirectory) {
					addDirectoryToZip(zipOut, dbDir, "databases/")
				}
			}

			// 4. Documents/TangoPlusBackup 폴더로 이동 (MediaStore 또는 File API)
			val targetFolder = File(
				Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
				"TangoPlusBackup"
			)
			if (!targetFolder.exists()) {
				targetFolder.mkdirs()
			}
			val destFile = File(targetFolder, backupFileName)

			// 저장 위치로 파일 복사
			FileInputStream(tempZipFile).use { fis ->
				FileOutputStream(destFile).use { fos ->
					fis.copyTo(fos)
				}
			}
			tempZipFile.delete()

			// 미디어 스캔 등록
			MediaScannerConnection.scanFile(
				context,
				arrayOf(destFile.absolutePath),
				arrayOf("application/zip"),
				null
			)

			val fileSizeFormatted = formatFileSize(destFile.length())
			Log.d(TAG, "백업 완료: ${destFile.absolutePath} ($fileSizeFormatted)")

			BackupStats(
				fileName = backupFileName,
				targetPath = destFile.absolutePath,
				staticsCount = statics.size,
				insightsCount = insights.size,
				checklistsCount = checklists.size,
				mediaFilesCount = mediaCount,
				formattedSize = fileSizeFormatted
			)
		}
	}

	/**
	 * 2. 백업 파일 복원 / 불러오기 (.ZIP)
	 * - 선택된 ZIP 파일에서 backup_data.json 파싱 -> Room DB에 일괄 저장(Replace)
	 * - pictures/, documents/, movies/ 내의 파일들을 원본 폴더로 압축 해제
	 * - MediaScanner로 갤러리 및 시스템에 즉시 등록
	 */
	suspend fun restoreBackup(
		context: Context,
		zipUri: Uri,
		faceDao: FaceDao
	): Result<RestoreStats> = withContext(Dispatchers.IO) {
		runCatching {
			var staticsCount = 0
			var insightsCount = 0
			var checklistsCount = 0
			var restoredMediaCount = 0
			val restoredFilePaths = mutableListOf<String>()

			val inputStream: InputStream = context.contentResolver.openInputStream(zipUri)
				?: throw IllegalArgumentException("선택한 백업 파일을 열 수 없습니다: $zipUri")

			ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
				var entry: ZipEntry? = zipIn.nextEntry
				val buffer = ByteArray(8192)

				val pictureTargetDir = File(
					Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
					"TangoPlus"
				).apply { mkdirs() }

				val documentsTargetDir = File(
					Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
					"TangoPlus"
				).apply { mkdirs() }

				val moviesTargetDir = File(
					Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
					"TangoPlus"
				).apply { mkdirs() }

				while (entry != null) {
					val name = entry.name

					if (entry.isDirectory) {
						zipIn.closeEntry()
						entry = zipIn.nextEntry
						continue
					}

					when {
						// (A) DB JSON 복원
						name == "backup_data.json" -> {
							val reader = InputStreamReader(zipIn, Charsets.UTF_8)
							val payload = gson.fromJson(reader, BackupArchivePayload::class.java)

							if (payload != null) {
								if (payload.statics.isNotEmpty()) {
									faceDao.insertAllStatics(payload.statics)
									staticsCount = payload.statics.size
								}
								if (payload.insights.isNotEmpty()) {
									faceDao.insertAllInsights(payload.insights)
									insightsCount = payload.insights.size
								}
								if (payload.checkLists.isNotEmpty()) {
									faceDao.insertAllPreCheckLists(payload.checkLists)
									checklistsCount = payload.checkLists.size
								}
								Log.d(TAG, "DB 복원 완료: statics=$staticsCount, insights=$insightsCount, checklists=$checklistsCount")
							}
						}

						// (B) Pictures 복원
						name.startsWith("pictures/") -> {
							val subPath = name.removePrefix("pictures/")
							val destFile = File(pictureTargetDir, subPath)
							destFile.parentFile?.mkdirs()
							FileOutputStream(destFile).use { fos ->
								var bytesRead: Int
								while (zipIn.read(buffer).also { bytesRead = it } > 0) {
									fos.write(buffer, 0, bytesRead)
								}
							}
							restoredFilePaths.add(destFile.absolutePath)
							restoredMediaCount++
						}

						// (C) Documents 복원
						name.startsWith("documents/") -> {
							val subPath = name.removePrefix("documents/")
							val destFile = File(documentsTargetDir, subPath)
							destFile.parentFile?.mkdirs()
							FileOutputStream(destFile).use { fos ->
								var bytesRead: Int
								while (zipIn.read(buffer).also { bytesRead = it } > 0) {
									fos.write(buffer, 0, bytesRead)
								}
							}
							restoredFilePaths.add(destFile.absolutePath)
							restoredMediaCount++
						}

						// (D) Movies 복원
						name.startsWith("movies/") -> {
							val subPath = name.removePrefix("movies/")
							val destFile = File(moviesTargetDir, subPath)
							destFile.parentFile?.mkdirs()
							FileOutputStream(destFile).use { fos ->
								var bytesRead: Int
								while (zipIn.read(buffer).also { bytesRead = it } > 0) {
									fos.write(buffer, 0, bytesRead)
								}
							}
							restoredFilePaths.add(destFile.absolutePath)
							restoredMediaCount++
						}
					}

					zipIn.closeEntry()
					entry = zipIn.nextEntry
				}
			}

			// 복원된 미디어 파일 시스템 인덱싱
			if (restoredFilePaths.isNotEmpty()) {
				MediaScannerConnection.scanFile(
					context,
					restoredFilePaths.toTypedArray(),
					null,
					null
				)
			}

			Log.d(TAG, "복원 완료: DB($staticsCount/$insightsCount/$checklistsCount), 미디어 $restoredMediaCount 개")
			RestoreStats(
				staticsCount = staticsCount,
				insightsCount = insightsCount,
				checklistsCount = checklistsCount,
				mediaFilesCount = restoredMediaCount
			)
		}
	}

	/**
	 * 폴더 내 모든 파일을 재귀적으로 ZIP에 추가
	 */
	private fun addDirectoryToZip(
		zipOut: ZipOutputStream,
		sourceDir: File,
		basePath: String
	): Int {
		var count = 0
		val files = sourceDir.listFiles() ?: return 0

		for (file in files) {
			val entryPath = basePath + file.name
			if (file.isDirectory) {
				count += addDirectoryToZip(zipOut, file, "$entryPath/")
			} else {
				try {
					zipOut.putNextEntry(ZipEntry(entryPath))
					FileInputStream(file).use { fis ->
						val buffer = ByteArray(8192)
						var len: Int
						while (fis.read(buffer).also { len = it } > 0) {
							zipOut.write(buffer, 0, len)
						}
					}
					zipOut.closeEntry()
					count++
				} catch (e: Exception) {
					Log.e(TAG, "파일 압축 실패: ${file.name}", e)
				}
			}
		}
		return count
	}

	/**
	 * 시스템 파일 탐색기 열기 (Documents/TangoPlusBackup)
	 */
	fun openBackupFolder(context: Context) {
		try {
			val docId = "primary:Documents/TangoPlusBackup"
			val uri = DocumentsContract.buildDocumentUri(
				"com.android.externalstorage.documents",
				docId
			)

			val intent = Intent(Intent.ACTION_VIEW).apply {
				setDataAndType(uri, "vnd.android.document/directory")
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
				addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			}
			context.startActivity(intent)
		} catch (e: Exception) {
			// fallback: 일반 파일 선택기
			try {
				val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
					type = "*/*"
					addCategory(Intent.CATEGORY_OPENABLE)
					addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				}
				context.startActivity(intent)
			} catch (e2: Exception) {
				Toast.makeText(context, "파일 탐색기를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun formatFileSize(bytes: Long): String {
		val kb = bytes / 1024.0
		val mb = kb / 1024.0
		return if (mb >= 1.0) {
			String.format(Locale.getDefault(), "%.1f MB", mb)
		} else {
			String.format(Locale.getDefault(), "%.1f KB", kb)
		}
	}
}

