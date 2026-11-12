package com.tangoplus.tangoface.domain.util

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tangoplus.tangoface.data.db.FaceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object MeasurementBackupUtility {

	private const val TAG = "Import"
	private const val DB_NAME = "face_database"

	class ByteProgress(private val total: Long, private val onPercent: ((Int) -> Unit)?) {
		private var written = 0L
		private var last = -1

		fun add(n: Int) {
			written += n
			if (total <= 0) return
			val p = (written * 100 / total).toInt().coerceAtMost(100)
			if (p != last) { last = p; onPercent?.invoke(p) }   // 퍼센트가 바뀔 때만
		}
	}


	data class Result(
		val success: Boolean,
		val addedMeasurements: Int = 0,
		val restoredFiles: Int = 0,
		val errorMessage: String? = null
	)

	suspend fun commit(
		context: Context,
		zipUri: Uri,
		onProgress: ((percent: Int) -> Unit)? = null
	): Result = withContext(Dispatchers.IO) {
		val tmpDir = File(context.cacheDir, "import_tmp").apply { deleteRecursively(); mkdirs() }
		val zipFile = File(context.cacheDir, "import_src.zip")
		try {
			copyToCache(context, zipUri)

			ZipFile(zipFile).use { zf ->
				val entries = zf.entries().asSequence().filter { !it.isDirectory }.toList()

				// 진행률 total = 처리할 entry의 압축 해제 크기 합 (backup_info.json 제외)
				val totalBytes = entries.filter { it.name != "backup_info.json" }.sumOf { it.size }
				val progress = ByteProgress(totalBytes, onProgress)

				var restored = 0
				entries.forEach { e ->
					val name = File(e.name).name // zip slip 방지
					zf.getInputStream(e).use { ins ->
						when {
							e.name.startsWith("databases/") ->
								copyWithProgress(ins, File(tmpDir, name), progress)
							e.name.startsWith("pictures/") ->
								if (restore(ins, Environment.DIRECTORY_PICTURES, null, name, progress)) restored++
							e.name.startsWith("documents/3DModels/") || e.name.startsWith("models/") ->
								if (restore(ins, Environment.DIRECTORY_DOCUMENTS, "3DModels", name, progress)) restored++
							e.name.startsWith("documents/") -> {
								if (name.endsWith(".glb", ignoreCase = true)) {
									if (restore(ins, Environment.DIRECTORY_DOCUMENTS, "3DModels", name, progress)) restored++
								} else {
									if (restore(ins, Environment.DIRECTORY_DOCUMENTS, null, name, progress)) restored++
								}
							}
							e.name.startsWith("movies/") ->
								if (restore(ins, Environment.DIRECTORY_MOVIES, null, name, progress)) restored++
						}
					}
				}

				val srcFile = File(tmpDir, DB_NAME).takeIf { it.exists() }
					?: return@withContext Result(false, errorMessage = "백업에 DB가 없습니다.")

				val added = mergeDatabase(context, srcFile)
				scanMedia(context)
				onProgress?.invoke(100)
				Result(true, added, restored)
			}
		} catch (e: Exception) {
			Log.e(TAG, "import 실패", e)
			Result(false, errorMessage = e.message)
		} finally {
			tmpDir.deleteRecursively()
			zipFile.delete()
		}
	}

	private fun mergeDatabase(context: Context, srcFile: File): Int {
		val src = SQLiteDatabase.openDatabase(srcFile.path, null, SQLiteDatabase.OPEN_READONLY)
		val dst = FaceDatabase.getDatabase(context).openHelper.writableDatabase
		val prefs = PreferenceUtility(context)
		val tempSnMap = mutableMapOf<Int, Int>() // 옛 temp_server_sn -> 새 temp_server_sn

		try {
			var nextSn = maxOf(
				maxInt(dst, "t_face_static", "temp_server_sn"),
				maxInt(dst, "t_face_pre_checklist", "temp_server_sn")
			) + 1

			dst.beginTransaction()
			try {
				// FaceStatic: json_file_name(일반 측정) 또는 media_file_name(3D 모델 스캔) 중복 스킵
				val existingJson = dst.query(
					"SELECT json_file_name FROM t_face_static WHERE json_file_name IS NOT NULL AND json_file_name != ''"
				).use { c ->
					generateSequence { if (c.moveToNext()) c.getString(0) else null }.toHashSet()
				}
				val existingMedia = dst.query(
					"SELECT media_file_name FROM t_face_static WHERE (json_file_name IS NULL OR json_file_name = '') AND media_file_name IS NOT NULL AND media_file_name != ''"
				).use { c ->
					generateSequence { if (c.moveToNext()) c.getString(0) else null }.toHashSet()
				}
				copyRows(src, dst, "t_face_static") { cv ->
					val json = cv.getAsString("json_file_name").orEmpty()
					val media = cv.getAsString("media_file_name").orEmpty()
					val isDup = if (json.isNotEmpty()) {
						json in existingJson
					} else if (media.isNotEmpty()) {
						media in existingMedia
					} else {
						false
					}
					if (isDup) return@copyRows false

					val old = cv.getAsInteger("temp_server_sn") ?: 0
					cv.put("temp_server_sn", tempSnMap.getOrPut(old) { nextSn++ })
					if (json.isNotEmpty()) existingJson += json
					if (media.isNotEmpty()) existingMedia += media
					true
				}

				// PreCheckList: 이번에 새로 들어온 측정 것만
				copyRows(src, dst, "t_face_pre_checklist") { cv ->
					val new = tempSnMap[cv.getAsInteger("temp_server_sn") ?: 0] ?: return@copyRows false
					cv.put("temp_server_sn", new)
					true
				}

				// FaceInsight: server_sn + insight 중복 스킵
				copyRows(src, dst, "t_face_insight") { cv ->
					val new = tempSnMap[cv.getAsInteger("server_sn") ?: 0] ?: return@copyRows false
					cv.put("server_sn", new)
					true
				}

				dst.setTransactionSuccessful()
			} finally {
				dst.endTransaction()
			}

			// 새로 만든 sn 중 최대값을 prefs에 반영
			if (tempSnMap.isNotEmpty()) {
				prefs.initExportTempServerSn(nextSn - 1)
			}
			return tempSnMap.size
		} finally {
			src.close()
		}
	}

	/** 공통 컬럼만 복사. filter가 false를 반환하면 해당 행은 스킵. local_sn은 제외(새로 발급). */
	private fun copyRows(
		src: SQLiteDatabase,
		dst: SupportSQLiteDatabase,
		table: String,
		filter: (ContentValues) -> Boolean
	) {
		val srcCols = src.rawQuery("PRAGMA table_info($table)", null).use { c ->
			generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toSet()
		}
		// dst 컬럼 정보: name, type, notnull, dflt_value
		data class Col(val name: String, val type: String, val notNull: Boolean, val hasDefault: Boolean)
		val dstCols = dst.query("PRAGMA table_info($table)").use { c ->
			generateSequence {
				if (c.moveToNext()) Col(
					c.getString(c.getColumnIndexOrThrow("name")),
					c.getString(c.getColumnIndexOrThrow("type")).uppercase(),
					c.getInt(c.getColumnIndexOrThrow("notnull")) == 1,
					!c.isNull(c.getColumnIndexOrThrow("dflt_value"))
				) else null
			}.toList()
		}.filter { it.name != "local_sn" }

		src.rawQuery("SELECT * FROM $table", null).use { c ->
			while (c.moveToNext()) {
				val cv = ContentValues()
				dstCols.forEach { col ->
					val i = c.getColumnIndex(col.name)
					if (i >= 0 && col.name in srcCols) {
						when (c.getType(i)) {
							Cursor.FIELD_TYPE_INTEGER -> cv.put(col.name, c.getLong(i))
							Cursor.FIELD_TYPE_FLOAT -> cv.put(col.name, c.getDouble(i))
							Cursor.FIELD_TYPE_STRING -> cv.put(col.name, c.getString(i))
							Cursor.FIELD_TYPE_BLOB -> cv.put(col.name, c.getBlob(i))
							else -> cv.putNull(col.name)
						}
					} else if (col.notNull && !col.hasDefault) {
						// export 쪽에 없는 NOT NULL 컬럼 → 타입별 기본값
						when (col.type) {
							"REAL" -> cv.put(col.name, 0.0)
							"TEXT" -> cv.put(col.name, "")
							else -> cv.put(col.name, 0)
						}
					}
				}
				if (filter(cv)) dst.insert(table, SQLiteDatabase.CONFLICT_ABORT, cv)
			}
		}
	}

	private fun maxInt(db: SupportSQLiteDatabase, table: String, col: String): Int =
		db.query("SELECT MAX($col) FROM $table").use {
			if (it.moveToFirst() && !it.isNull(0)) it.getInt(0) else -1
		}

	/** 이미 있는 파일은 덮어쓰지 않고 스킵. 새로 쓰면 true */
	private fun restore(
		ins: InputStream,
		type: String,
		subDir: String? = null,
		name: String,
		progress: ByteProgress
	): Boolean {
		val parentDir = File(Environment.getExternalStoragePublicDirectory(type), "TangoPlus")
		val dir = if (subDir != null) File(parentDir, subDir) else parentDir
		dir.mkdirs()
		val target = File(dir, name)
		if (target.exists()) {
			// 건너뛴 파일도 진행률에는 반영해야 100%에 도달함
			skipWithProgress(ins, progress)
			return false
		}
		copyWithProgress(ins, target, progress)
		return true
	}

	private fun scanMedia(context: Context) {
		val dirs = listOf(
			File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "TangoPlus"),
			File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "TangoPlus"),
			File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "TangoPlus/3DModels")
		)
		val paths = dirs
			.filter { it.exists() }
			.flatMap { it.listFiles()?.toList() ?: emptyList() }
			.map { it.absolutePath }
			.toTypedArray()
		if (paths.isNotEmpty()) {
			MediaScannerConnection.scanFile(context, paths, null, null)
		}
	}

	private fun copyToCache(context: Context, uri: Uri): File {
		val f = File(context.cacheDir, "import_src.zip")
		context.contentResolver.openInputStream(uri)?.use { ins ->
			f.outputStream().use { ins.copyTo(it) }
		} ?: throw IllegalStateException("파일을 열 수 없습니다.")
		return f
	}

	private fun copyWithProgress(ins: InputStream, target: File, progress: ByteProgress) {
		target.outputStream().use { out ->
			val buf = ByteArray(64 * 1024)
			while (true) {
				val n = ins.read(buf)
				if (n < 0) break
				out.write(buf, 0, n)
				progress.add(n)
			}
		}
	}

	private fun skipWithProgress(ins: InputStream, progress: ByteProgress) {
		val buf = ByteArray(64 * 1024)
		while (true) {
			val n = ins.read(buf)
			if (n < 0) break
			progress.add(n)
		}
	}

	data class ImportPreview(
		val fileName: String,
		val exportedDate: String,
		val newCount: Int,
		val dupCount: Int,
		val fileCount: Int
	)

	suspend fun analyze(context: Context, zipUri: Uri, displayName: String): ImportPreview? =
		withContext(Dispatchers.IO) {
			val tmpDir = File(context.cacheDir, "import_analyze").apply { deleteRecursively(); mkdirs() }
			val zipFile = File(context.cacheDir, "import_src.zip")
			try {
				if (!zipFile.exists()) copyToCache(context, zipUri)

				var exportedDate = ""
				var fileCount = 0
				ZipFile(zipFile).use { zf ->
					zf.entries().asSequence().filter { !it.isDirectory }.forEach { e ->
						when {
							e.name == "backup_info.json" -> {
								val json = JSONObject(zf.getInputStream(e).bufferedReader().readText())
								exportedDate = json.optString("date")
							}
							e.name.startsWith("databases/") ->
								zf.getInputStream(e).use { ins ->
									File(tmpDir, File(e.name).name).outputStream().use { ins.copyTo(it) }
								}
							else -> fileCount++
						}
					}
				}
				if (exportedDate.isEmpty()) return@withContext null
				val srcFile = File(tmpDir, DB_NAME).takeIf { it.exists() } ?: return@withContext null

				// DB는 수정하지 않고 읽기만 해서 새 건수/중복 건수 계산
				val src = SQLiteDatabase.openDatabase(srcFile.path, null, SQLiteDatabase.OPEN_READONLY)
				val dst = FaceDatabase.getDatabase(context).openHelper.readableDatabase
				try {
					val existingJson = dst.query(
						"SELECT json_file_name FROM t_face_static WHERE json_file_name IS NOT NULL AND json_file_name != ''"
					).use { c ->
						generateSequence { if (c.moveToNext()) c.getString(0) else null }.toHashSet()
					}
					val existingMedia = dst.query(
						"SELECT media_file_name FROM t_face_static WHERE (json_file_name IS NULL OR json_file_name = '') AND media_file_name IS NOT NULL AND media_file_name != ''"
					).use { c ->
						generateSequence { if (c.moveToNext()) c.getString(0) else null }.toHashSet()
					}
					val newSn = mutableSetOf<Int>()
					val dupSn = mutableSetOf<Int>()
					src.rawQuery("SELECT temp_server_sn, json_file_name, media_file_name FROM t_face_static", null).use { c ->
						while (c.moveToNext()) {
							val sn = c.getInt(0)
							val json = c.getString(1).orEmpty()
							val media = c.getString(2).orEmpty()
							val isDup = if (json.isNotEmpty()) {
								json in existingJson
							} else if (media.isNotEmpty()) {
								media in existingMedia
							} else {
								false
							}
							if (isDup) dupSn.add(sn) else newSn.add(sn)
						}
					}
					// 한 측정의 일부 행만 중복이어도 새 건수와 겹치지 않도록
					dupSn.removeAll(newSn)
					ImportPreview(displayName, exportedDate, newSn.size, dupSn.size, fileCount)
				} finally {
					src.close()
				}
			} catch (e: Exception) {
				Log.e(TAG, "analyze 실패", e)
				null
			} finally {
				tmpDir.deleteRecursively()
				// zipFile은 commit에서 재사용하므로 여기서 지우지 않음 (commit의 finally에서 삭제)
			}
		}
	// -----------------------------------------------------------------------------------------------
	private const val TAG_E = "Export"

	data class ExportResult(
		val success: Boolean,
		val fileCount: Int = 0,
		val errorMessage: String? = null
	)

	// 폴더별 export 대상 정의 (기존 백업 규칙과 동일한 zip 내부 경로 사용)
	private data class Source(val zipPrefix: String, val dir: File, val extension: String)

	suspend fun export(
		context: Context,
		outputUri: Uri,
		onProgress: ((percent: Int) -> Unit)? = null
	): ExportResult = withContext(Dispatchers.IO) {
		try {
			val sources = listOf(
				Source("pictures/", publicDir(Environment.DIRECTORY_PICTURES), "jpg"),
				Source("documents/", publicDir(Environment.DIRECTORY_DOCUMENTS), "json"),
				Source("documents/3DModels/", File(publicDir(Environment.DIRECTORY_DOCUMENTS), "3DModels"), "glb"),
				Source("documents/3DModels/", publicDir(Environment.DIRECTORY_DOCUMENTS), "glb"),
				Source("movies/", publicDir(Environment.DIRECTORY_MOVIES), "mp4")
			)

			// 대상 파일 수집 (전체: 사진, json, 3D 모델 glb, 영상)
			val seenNames = mutableSetOf<String>()
			val targets = sources.flatMap { src ->
				if (!src.dir.exists()) emptyList()
				else src.dir.listFiles { f -> f.isFile && f.extension.equals(src.extension, true) }
					?.filter { seenNames.add(it.name) }
					?.map { src.zipPrefix to it } ?: emptyList()
			}

			// DB 파일 수집 (WAL 반영 후)
			checkpointDatabase(context)
			val dbFiles = collectDatabaseFiles(context)

			val totalBytes = targets.sumOf { it.second.length() } + dbFiles.sumOf { it.length() }
			val progress = ByteProgress(totalBytes, onProgress)

			val dao = FaceDatabase.getDatabase(context).faceDao()
			val manifest = JSONObject().apply {
				put("backup_type", "full_export")
				put("version", 1)
				put("timestamp", System.currentTimeMillis())
				put("date", SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()))
				put("package", context.packageName)
				put("db_data_count", dao.getDataCount())
				put("latest_temp_server_sn", dao.getLatestTempServerSn())
				put("measurement_file_count", targets.size)
			}

			context.contentResolver.openOutputStream(outputUri)?.use { os ->
				ZipOutputStream(BufferedOutputStream(os)).use { zip ->
					// 1) manifest
					zip.putNextEntry(ZipEntry("backup_info.json"))
					zip.write(manifest.toString(2).toByteArray())
					zip.closeEntry()

					dbFiles.forEach { f -> addFile(zip, f, "databases/", progress) }
					targets.forEach { (prefix, f) -> addFile(zip, f, prefix, progress) }
				}
			} ?: return@withContext ExportResult(false, errorMessage = "출력 스트림을 열 수 없습니다.")

			Log.d(TAG_E, "export 완료: ${targets.size}개 측정 파일, DB ${dbFiles.size}개")
			ExportResult(true, targets.size)
		} catch (e: Exception) {
			Log.e(TAG_E, "export 실패", e)
			ExportResult(false, errorMessage = e.message)
		}
	}

	private fun publicDir(type: String) =
		File(Environment.getExternalStoragePublicDirectory(type), "TangoPlus")

	// WAL 내용을 본 DB 파일에 반영 (안 하면 최신 데이터가 -wal 에만 있을 수 있음)
	private fun checkpointDatabase(context: Context) {
		try {
			FaceDatabase.getDatabase(context).openHelper.writableDatabase
				.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
		} catch (e: Exception) {
			Log.w(TAG_E, "checkpoint 실패 (계속 진행)", e)
		}
	}

	private fun collectDatabaseFiles(context: Context): List<File> {
		val dbDir = context.getDatabasePath(DB_NAME).parentFile ?: return emptyList()
		return dbDir.listFiles { f ->
			f.isFile && f.canRead() &&
				(f.name == DB_NAME || f.name == "$DB_NAME-wal" || f.name == "$DB_NAME-shm")
		}?.toList() ?: emptyList()
	}

	private fun addFile(zip: ZipOutputStream, file: File, prefix: String, progress: ByteProgress) {
		try {
			zip.putNextEntry(ZipEntry(prefix + file.name))
			file.inputStream().use { ins ->
				val buf = ByteArray(64 * 1024)
				while (true) {
					val n = ins.read(buf)
					if (n < 0) break
					zip.write(buf, 0, n)
					progress.add(n)          // 영상 하나가 커도 중간중간 올라감
				}
			}
			zip.closeEntry()
		} catch (e: Exception) {
			Log.e(TAG_E, "압축 실패: ${file.name}", e)
		}
	}
}