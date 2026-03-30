package com.tangoplus.facebeautyexpert.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tangoplus.facebeautyexpert.ui.main.vo.MeasureGridItem

@Dao
interface FaceDao {
	// ------------------------------------# user #-----------------------------------------------

	// ------------------------------------# static #-----------------------------------------------
	@Insert
	suspend fun insertStatic(faceStatic: FaceStatic): Long

	@Query("SELECT * FROM t_face_static WHERE activated = 1")
	suspend fun getAllData(): List<FaceStatic>

	@Query(
		"""
        UPDATE t_face_static
        SET activated = :activated
        WHERE temp_server_sn = :tempServerSn
    """
	)
	suspend fun activatingStatics(tempServerSn: Int, activated: Int)


	@Query(
		"""
        SELECT 
            GROUP_CONCAT(DISTINCT temp_server_sn) AS tempServerSn,
            name AS userTitle,
            GROUP_CONCAT(DISTINCT reg_date) AS regDate,
            uuid,
            1 AS activated
        FROM t_face_static
        WHERE name IS 'GUEST'
        AND activated = 1
        GROUP BY uuid
    """
	)
	suspend fun getGuestData(): List<MeasureGridItem>

	@Query(
		"""
        SELECT COUNT(DISTINCT temp_server_sn)
        FROM t_face_static
        WHERE name = 'GUEST' 
        AND activated = 1
    """
	)
	suspend fun getGuestCount(): Int

	// -------------# 회원관리할 때 user-guest 가릴거 없이 가져오기 #---------------
	@Query(
		"""
        SELECT 
            GROUP_CONCAT(DISTINCT temp_server_sn) AS tempServerSn,
            name AS userTitle,
            GROUP_CONCAT(DISTINCT reg_date) AS regDate,
            uuid,
            1 AS activated
        FROM t_face_static
        WHERE activated = 1
        GROUP BY uuid
    """
	)
	suspend fun getAllMeasureItem(): List<MeasureGridItem>


	@Query("SELECT * FROM t_face_static WHERE temp_server_sn == :tempServerSn ORDER BY seq ASC")
	suspend fun getStatic(tempServerSn: Int): List<FaceStatic>

	suspend fun getStaticWithPlaceholders(tempServerSn: Int): List<FaceStatic> {
		val statics = getStatic(tempServerSn)

		return (0..9).map { seq ->
			statics.find { it.seq == seq } ?: FaceStatic(
				temp_server_sn = tempServerSn,
				seq = seq
			)
		}
	}

	@Query("SELECT COUNT(DISTINCT temp_server_sn) FROM t_face_static WHERE activated = 1")
	suspend fun getDataCount(): Int

	@Query("SELECT temp_server_sn FROM t_face_static WHERE activated = 1 ORDER BY temp_server_sn DESC LIMIT 1")
	suspend fun getLatestTempServerSn(): Int

	// ------------------------------------# 설문지 넣기 #------------------------------------
	@Insert
	suspend fun insertPreCheckListResult(precheckListResult: PreCheckList): Long

	@Query("SELECT * FROM t_face_pre_checklist")
	suspend fun getAllCheckListData(): List<PreCheckList>

	@Query("SELECT COUNT(DISTINCT local_sn) FROM t_face_pre_checklist")
	suspend fun getPreChecklistCount(): Int

	// ------------------------------------# 메모 #-----------------------------------------------

	@Query(
		"""
        SELECT 
            temp_server_sn AS tempServerSn,
            substr(reg_date, 1, 16) || " " || name AS userTitle, 
            reg_date AS regDate,
            1 as activated
            FROM t_face_static
            WHERE user_sn = :userSn
            AND activated = 1
            GROUP BY temp_server_sn
    """
	)
	suspend fun getMeasureDataFrom1User(userSn: Int): List<MeasureGridItem>

}