package com.tangoplus.facebeautyexpert.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tangoplus.facebeautyexpert.ui.main.vo.UserItem

@Dao
interface FaceDao {
	// ------------------------------------# user #-----------------------------------------------


	// --------------------------# AI 결과 저장하기 #-------------------------------
	@Insert
	suspend fun insertInsight(insight: FaceInsight) : Long

	@Query("SELECT * FROM t_face_insight WHERE server_sn = :tempServerSn LIMIT 1")
	suspend fun getInsight(tempServerSn: Int): FaceInsight?
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
        SELECT COUNT(DISTINCT temp_server_sn)
        FROM t_face_static
        WHERE activated = 1
    """
	)
	suspend fun getUserCount(): Int

	// -------------# 회원관리할 때 user-guest 가릴거 없이 가져오기 #---------------
	@Query(
		"""
        SELECT 
			    S.temp_server_sn AS tempServerSn, 
			    S.name AS name,
			    S.mobile AS mobile,
			    S.birthday AS birthday,
			    S.nationality AS nationality,
			    S.gender AS gender,
			    S.reg_date AS regDate, 
			    IFNULL(I.insight, '') AS insight,
			    S.uuid,
			    1 AS activated
			FROM t_face_static S
			LEFT JOIN t_face_insight I ON S.temp_server_sn = I.server_sn
			WHERE S.activated = 1
			GROUP BY S.temp_server_sn
			ORDER BY regDate DESC
    """
	)
	suspend fun getAllUserItem(): List<UserItem>


	@Query("SELECT * FROM t_face_static WHERE temp_server_sn == :tempServerSn ORDER BY seq ASC")
	suspend fun getStatic(tempServerSn: Int): List<FaceStatic>

//	suspend fun getStaticWithPlaceholders(tempServerSn: Int): List<FaceStatic> {
//		val statics = getStatic(tempServerSn)
//
//		return (0..1).map { seq ->
//			statics.find { it.seq == seq } ?: FaceStatic(
//				temp_server_sn = tempServerSn,
//				seq = seq
//			)
//		}
//	}

	@Query("SELECT COUNT(DISTINCT temp_server_sn) FROM t_face_static WHERE activated = 1")
	suspend fun getDataCount(): Int

	@Query("SELECT temp_server_sn FROM t_face_static WHERE activated = 1 ORDER BY temp_server_sn DESC LIMIT 1")
	suspend fun getLatestTempServerSn(): Int
	@Query("SELECT temp_server_sn FROM t_face_static WHERE measure_type = 2 AND uuid = :uuid ORDER BY reg_date DESC")
	suspend fun getBowSns(uuid: String): List<Int>
	// ------------------------------------# 설문지 넣기 #------------------------------------
	@Insert
	suspend fun insertPreCheckListResult(precheckListResult: PreCheckList): Long

	@Query("SELECT * FROM t_face_pre_checklist")
	suspend fun getAllCheckListData(): List<PreCheckList>

	@Query("SELECT COUNT(DISTINCT local_sn) FROM t_face_pre_checklist")
	suspend fun getPreChecklistCount(): Int


	// ------------# expert survey #--------------
	@Insert
	suspend fun insertAfterSurvey(survey: AfterSurvey): Long

	@Query("SELECT * FROM t_after_survey WHERE server_sn = :serverSn")
	suspend fun getAfterSurvey(serverSn: Int): AfterSurvey?
}