package com.tangoplus.tangoface_compose.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tangoplus.tangoface_compose.domain.model.BowItem
import com.tangoplus.tangoface_compose.domain.model.UserItem

@Dao
interface FaceDao {
	// ------------------------------------# user #-----------------------------------------------

	// --------------------------# AI 결과 저장하기 #-------------------------------
	@Insert
	suspend fun insertInsight(insight: FaceInsight): Long

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertAllInsights(insights: List<FaceInsight>)

	@Query("SELECT * FROM t_face_insight")
	suspend fun getAllInsights(): List<FaceInsight>

	@Query("SELECT * FROM t_face_insight WHERE server_sn = :tempServerSn LIMIT 1")
	suspend fun getInsight(tempServerSn: Int): FaceInsight?

	// ------------------------------------# static #-----------------------------------------------
	@Insert
	suspend fun insertStatic(faceStatic: FaceStatic): Long

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertAllStatics(statics: List<FaceStatic>)

	@Query("SELECT * FROM t_face_static")
	suspend fun getAllStatics(): List<FaceStatic>

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

	@Query(
		"""
			SELECT COUNT(DISTINCT temp_server_sn)
			FROM t_face_static
			WHERE activated = 1
			AND reg_date = date(reg_date) = date('now', 'localtime')
		"""
	)
	suspend fun getDailyMeasureCount(): Int

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
					S.measure_type AS measureType,
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

	@Query(
		"""
   SELECT
    temp_server_sn AS tempServerSn,
    name AS name,
    mobile AS mobile,
    birthday AS birthday,
    nationality AS nationality,
    reg_date AS regDate,
    1 AS activated
   FROM t_face_static
   WHERE measure_type == 2 AND 
			((:isGuest = 1 AND name = 'GUEST')
      OR (:isGuest = 0 AND temp_server_sn = :tempServerSn))
  """
	)
	suspend fun getAllBowItems(
		tempServerSn: Int? = null,
		isGuest: Boolean = false
	): List<BowItem>

	@Query(
		"""
   SELECT
    temp_server_sn AS tempServerSn,
    name AS name,
    mobile AS mobile,
    birthday AS birthday,
    nationality AS nationality,
    reg_date AS regDate,
    1 AS activated
   FROM t_face_static
   WHERE temp_server_sn = :tempServerSn
  """
	)
	suspend fun getMatchedBowItem(
		tempServerSn: Int? = null,
	): BowItem

	@Query("SELECT * FROM t_face_static WHERE temp_server_sn == :tempServerSn ORDER BY seq ASC")
	suspend fun getStatic(tempServerSn: Int): List<FaceStatic>

	@Query("SELECT COUNT(DISTINCT temp_server_sn) FROM t_face_static WHERE activated = 1")
	suspend fun getDataCount(): Int

	@Query("SELECT temp_server_sn FROM t_face_static WHERE activated = 1 ORDER BY temp_server_sn DESC LIMIT 1")
	suspend fun getLatestTempServerSn(): Int

	// ------------------------------------# 설문지 넣기 #------------------------------------
	@Insert
	suspend fun insertPreCheckListResult(precheckListResult: PreCheckList): Long

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertAllPreCheckLists(checklists: List<PreCheckList>)

	@Query("SELECT * FROM t_face_pre_checklist")
	suspend fun getAllCheckListData(): List<PreCheckList>

	@Query("SELECT COUNT(DISTINCT local_sn) FROM t_face_pre_checklist")
	suspend fun getPreChecklistCount(): Int
}
