package com.tangoplus.tangoface_compose.domain.repository

import com.tangoplus.tangoface_compose.data.db.FaceInsight
import com.tangoplus.tangoface_compose.data.db.FaceStatic
import com.tangoplus.tangoface_compose.domain.model.BowItem
import com.tangoplus.tangoface_compose.domain.model.UserItem

interface FaceRepository {
	suspend fun insertStatic(faceStatic: FaceStatic): Long
	suspend fun getAllData(): List<FaceStatic>
	suspend fun getUserCount(): Int
	suspend fun activatingStatics(tempServerSn: Int, activated: Int)
	suspend fun insertInsight(insight: FaceInsight): Long
	suspend fun getInsight(tempServerSn: Int): FaceInsight?
	suspend fun getUserItems(): List<UserItem>
	suspend fun getBowItems(): List<BowItem>
}

