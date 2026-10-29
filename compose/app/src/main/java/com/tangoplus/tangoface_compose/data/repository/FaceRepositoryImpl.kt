package com.tangoplus.tangoface_compose.data.repository

import com.tangoplus.tangoface_compose.data.api.retrofit.FaceApiService
import com.tangoplus.tangoface_compose.data.db.FaceDao
import com.tangoplus.tangoface_compose.data.db.FaceInsight
import com.tangoplus.tangoface_compose.data.db.FaceStatic
import com.tangoplus.tangoface_compose.domain.model.BowItem
import com.tangoplus.tangoface_compose.domain.model.UserItem
import com.tangoplus.tangoface_compose.domain.repository.FaceRepository

class FaceRepositoryImpl(
	private val faceDao: FaceDao,
	private val apiService: FaceApiService? = null
) : FaceRepository {

	override suspend fun insertStatic(faceStatic: FaceStatic): Long {
		return faceDao.insertStatic(faceStatic)
	}

	override suspend fun getAllData(): List<FaceStatic> {
		return faceDao.getAllData()
	}

	override suspend fun getUserCount(): Int {
		return faceDao.getUserCount()
	}

	override suspend fun activatingStatics(tempServerSn: Int, activated: Int) {
		faceDao.activatingStatics(tempServerSn, activated)
	}

	override suspend fun insertInsight(insight: FaceInsight): Long {
		return faceDao.insertInsight(insight)
	}

	override suspend fun getInsight(tempServerSn: Int): FaceInsight? {
		return faceDao.getInsight(tempServerSn)
	}

	override suspend fun getUserItems(): List<UserItem> {
		return faceDao.getAllUserItem()
	}

	override suspend fun getBowItems(): List<BowItem> {
		return faceDao.getAllBowItems()
	}
}

