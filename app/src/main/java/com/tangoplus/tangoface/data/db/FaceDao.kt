package com.tangoplus.tangoface.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tangoplus.tangoface.data.vo.FaceDisplayBasic
import com.tangoplus.tangoface.data.vo.UserSeed
import com.tangoplus.tangoface.ui.main.vo.MeasureGridItem

@Dao
interface FaceDao {
	// ------------------------------------# user #-----------------------------------------------

	// t_user_info에 넣기
	@Query(
		"""
        SELECT 
            uuid,
            MIN(name) AS name,
            MIN(mobile) AS mobile
        FROM t_face_static
        WHERE activated = 1
        GROUP BY uuid
    """
	)
	suspend fun getDistinctUsersFromStatic(): List<UserSeed>

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertUser(user: FaceUser): Long

	@Query("UPDATE t_face_static SET user_sn = :userSn WHERE uuid = :uuid")
	suspend fun updateUserSnForUuid(uuid: String, userSn: Int)

	@Query(
		"""
        SELECT COUNT(DISTINCT user_sn)
        FROM t_face_user
        WHERE activated = 1
    """
	)
	suspend fun getUserCount(): Int

	@Query("SELECT user_sn FROM t_face_user WHERE uuid = :uuid AND activated = 1")
	suspend fun getExistUserSn(uuid: String): Int?


	@Query(
		"""
        UPDATE t_face_user
        SET faceType = :faceType 
        WHERE user_sn = :userSn
    """
	)
	suspend fun updateFaceType(faceType: Int, userSn: Int)


	@Query(
		"""
        UPDATE t_face_user
        SET activated = :activated
        WHERE user_sn = :userSn
    """
	)
	suspend fun activatingUser(userSn: Int, activated: Int)

	@Query(
		"""
        UPDATE t_face_user
        SET name = "GUEST",
            mobile = :userSn,
            uuid = :uuid,
            birthday = "",
            nationality = "",
            userGender = -1,
            faceType = null
        WHERE user_sn = :userSn
    """
	)
	suspend fun convertGuestFromUser(userSn: Int, uuid: String)

	// -------------# 회원관리할 때 user-guest 가릴거 없이 가져오기 #---------------
	@Query(
		"""
        SELECT
            DISTINCT(user_sn) AS tempServerSn,
            name AS userTitle,
            birthday AS regDate,
            1 AS activated
        FROM t_face_user
        WHERE activated = 1 
        AND name != 'GUEST'
    """
	)
	suspend fun getAllUserItem(): List<MeasureGridItem>

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
            u.user_sn AS userSn,
            u.name AS userName,
            u.mobile AS userMobile,
            u.birthday AS userBirthday,
            u.nationality AS userNationality,
            u.userGender AS userGender,
            u.faceType AS faceType,
            IFNULL((
               SELECT GROUP_CONCAT(DISTINCT temp_server_sn) 
               FROM (SELECT temp_server_sn FROM t_face_static 
                     WHERE user_sn = u.user_sn 
                     ORDER BY temp_server_sn)
            ), '') AS tempServerSns,
            (SELECT GROUP_CONCAT(DISTINCT reg_date) 
             FROM (SELECT reg_date FROM t_face_static 
                   WHERE user_sn = u.user_sn 
                   ORDER BY reg_date)) AS regDate
        FROM t_face_user u
        WHERE u.name != 'GUEST'
        AND activated = 1
    """
	)
	suspend fun getFaceDisplayList(): List<FaceDisplayBasic>

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

	// ------------------------------------# 즐겨찾기 #-----------------------------------------------

	@Query(
		"""
        SELECT 
            temp_server_sn AS tempServerSn,
            title AS userTitle,
            reg_date AS regDate,
            1 as activated
        FROM t_face_favorite
    """
	)
	suspend fun getAllFavorite(): List<MeasureGridItem>

	@Query("SELECT temp_server_sn FROM t_face_favorite")
	suspend fun getAllFavoriteSn(): List<Int>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertFavorite(faceFavorite: FaceFavorite): Long

	@Query("DELETE FROM t_face_favorite WHERE temp_server_sn = :serverSn")
	suspend fun deleteFavoriteByServerSn(serverSn: Int): Int


	// ------------------------------------# 메모 #-----------------------------------------------
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertMemo(memo: Memo): Long

	@Query(
		"""
        UPDATE t_memo
        SET memo = :memo 
        WHERE user_sn = :userSn
    """
	)
	suspend fun updateMemo(memo: String, userSn: Int)

	@Query(
		"""
        SELECT 
            "" AS tempServerSn,
            memo AS userTitle,
            reg_date AS regDate,
            1 as activated
        FROM t_memo
        WHERE user_sn = :userSn
        ORDER BY local_sn DESC
    """
	)
	suspend fun getMemo(userSn: Int): MeasureGridItem?

	// 새로 추가: user_sn이 있는지 확인하고, 있으면 update, 없으면 insert
	suspend fun insertOrUpdateMemo(memo: Memo) {
		val existingMemo = getMemo(memo.user_sn) // user_sn이 이미 있는지 확인
		if (existingMemo != null) {
			updateMemo(memo.memo, memo.user_sn) // 존재하면 update
		} else {
			insertMemo(memo) // 없으면 insert
		}
	}

	/* memo와 user은 같은
	* MeasureGridItem이라는
	*  data class를 사용
	* */
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