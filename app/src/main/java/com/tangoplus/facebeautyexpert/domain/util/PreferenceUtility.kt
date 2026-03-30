package com.tangoplus.facebeautyexpert.domain.util

import android.content.Context
import androidx.core.content.edit
import com.tangoplus.facebeautyexpert.domain.vo.Sequence

class PreferenceUtility(context: Context) {
	private val tssPrefs = context.getSharedPreferences("temp_server_sn_prefs", Context.MODE_PRIVATE)
	private val dcPrefs = context.getSharedPreferences("data_count_prefs", Context.MODE_PRIVATE)
	private val bPrefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
	private val mPrefs = context.getSharedPreferences("measure_prefs", Context.MODE_PRIVATE)
	private val fPrefs = context.getSharedPreferences("flicker_prefs", Context.MODE_PRIVATE)

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
	fun setLastMeasureSeqs(seqs: Set<Sequence>) {
		mPrefs.edit {
			putStringSet("sequences", seqs.map { it.displayName }.toSet())
		}
	}

	fun getLastMeasureSeqs(): Set<Sequence> {
		val savedDisplayNames = mPrefs.getStringSet("sequences", emptySet()) ?: emptySet()
		return savedDisplayNames.mapNotNull { Sequence.fromDisplayName(it) }.toSet()
	}
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
}