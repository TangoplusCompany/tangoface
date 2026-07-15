package com.tangoplus.tangoface.domain.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tangoplus.tangoface.R
import java.io.File
import java.io.FileOutputStream

class PreferenceUtility(context: Context) {
	private val tssPrefs = context.getSharedPreferences("temp_server_sn_prefs", Context.MODE_PRIVATE)
	private val dcPrefs = context.getSharedPreferences("data_count_prefs", Context.MODE_PRIVATE)
	private val bPrefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
	private val mPrefs = context.getSharedPreferences("measure_prefs", Context.MODE_PRIVATE)
	private val fPrefs = context.getSharedPreferences("flicker_prefs", Context.MODE_PRIVATE)
	private val veloPrefs = context.getSharedPreferences("velocity_prefs", Context.MODE_PRIVATE)
	private val pPrefs = context.getSharedPreferences("poster_prefs", Context.MODE_PRIVATE)
	private val KEY_LAST_SN = "last_temp_server_sn"
	private val KEY_LAST_COUNT = "last_data_count"
	fun setNextTempServerSn() {
		val lastSn = tssPrefs.getInt(KEY_LAST_SN, 0)
		val nextSn = lastSn + 1
		// 새로운 SN 저장
		tssPrefs.edit { putInt(KEY_LAST_SN, nextSn) }
	}

	// 앱 초기화 시 마지막 SN 확인
	fun getLastTempServerSn(): Int {
		return tssPrefs.getInt(KEY_LAST_SN, 0)
	}

	fun initExportTempServerSn(exportedSn: Int) {
		tssPrefs.edit { putInt(KEY_LAST_SN, exportedSn + 1) }
	}

	fun setDataCount(count: Int) {
		dcPrefs.edit { putInt(KEY_LAST_COUNT, count) }
	}

	fun getLastDataCount(): Int {
		return dcPrefs.getInt(KEY_LAST_COUNT, 0)
	}

	// ------------# backup 마지막 파일명 보기 #-------------
	fun getLastBackupFilePrefix(): String = bPrefs.getString("last_backup_file_prefix", "") ?: ""

	fun setLastBackupFilePrefix(prefix: String) {
		bPrefs.edit { putString("last_backup_file_prefix", prefix) }
	}

	fun getLastBackupDataCount(): Int = bPrefs.getInt("last_backup_file_count", 0)

	fun setLastBackupDataCount(dataCount: Int) {
		bPrefs.edit { putInt("last_backup_file_count", dataCount) }
	}


	// -----------# 마지막 측정 방식 #-------------
//	fun setLastMeasureSeqs(seqs: Set<Sequence>) {
//		mPrefs.edit {
//			putStringSet("sequences", seqs.map { it.displayName }.toSet())
//		}
//	}
//
//	fun getLastMeasureSeqs(): Set<Sequence> {
//		val savedDisplayNames = mPrefs.getStringSet("sequences", emptySet()) ?: emptySet()
//		return savedDisplayNames.mapNotNull { Sequence.fromDisplayName(it) }.toSet()
//	}
	/* ObTypes.h 라는 파일에 정의
		typedef enum {
		    OB_POWER_LINE_FREQ_MODE_CLOSE = 0,  ///< Close
		    OB_POWER_LINE_FREQ_MODE_50HZ  = 1,  ///< 50Hz
		    OB_POWER_LINE_FREQ_MODE_60HZ  = 2,  ///< 60Hz
		} ob_power_line_freq_mode,
		    OBPowerLineFreqMode;
	* */

	fun setFlickerType(freq_type: Int) {
		mPrefs.edit {
			putInt("FREQ_MODE", freq_type)
		}
	}

	fun getFlickerType(): Int {
		return mPrefs.getInt("FREQ_MODE" , 2)
	}

	// ---- poster url 저장 ----
	private val KEY_IMAGES = "images"
	private val KEY_IS_INIT = "is_initialized"
	private val MAX_COUNT = 6
	fun savePosters( urls: List<String>) {
		val json = Gson().toJson(urls.take(MAX_COUNT))
		pPrefs.edit { putString(KEY_IMAGES, json) }
	}

	fun getPosters(): List<String> {
		val json = pPrefs.getString(KEY_IMAGES, null) ?: return emptyList()
		val type = object : TypeToken<List<String>>() {}.type
		return Gson().fromJson(json, type)
	}
	fun initDefaultPosters(context: Context) {
		// 1. 이미 초기화(기본값 생성이) 완료되었다면 바로 종료
		val isInitialized = pPrefs.getBoolean(KEY_IS_INIT, false)
		if (isInitialized) return

		Log.v("포스터", "최초 실행: 기본 포스터를 생성합니다.")

		val defaultResIds = listOf(R.drawable.poster_main_0, R.drawable.poster_main_1, R.drawable.poster_main_2)
		val defaultPaths = mutableListOf<String>()

		defaultResIds.forEachIndexed { index, resId ->
			val fileName = "default_poster_$index.jpg"
			val path = copyResourceToFile(context, resId, fileName)
			path?.let { defaultPaths.add(it) }
		}

		if (defaultPaths.isNotEmpty()) {
			savePosters(defaultPaths)
			// 2. ✅ 초기화 완료 플래그를 true로 저장 (리스트를 다 지워도 이건 계속 true)
			pPrefs.edit { putBoolean(KEY_IS_INIT, true) }
		}
	}

	// drawable 리소스를 내부 파일로 복사하는 헬퍼 함수
	private fun copyResourceToFile(context: Context, resId: Int, fileName: String): String? {
		return try {
			val inputStream = context.resources.openRawResource(resId)
			val file = File(context.filesDir, fileName)
			val outputStream = FileOutputStream(file)

			inputStream.use { input ->
				outputStream.use { output ->
					input.copyTo(output)
				}
			}
			file.absolutePath
		} catch (e: Exception) {
			e.printStackTrace()
			null
		}
	}

	// --------# count prefs #------
	fun setSlideVelocity(velo: Float) {
		veloPrefs.edit {
			putFloat("SLIDE_VELOCITY", velo)
		}
	}

	fun getSlideVelocity(): Float {
		return veloPrefs.getFloat("SLIDE_VELOCITY" , 7.0f)
	}
}