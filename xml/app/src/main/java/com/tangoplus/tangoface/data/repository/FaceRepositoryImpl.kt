package com.tangoplus.tangoface.data.repository

import com.tangoplus.tangoface.data.db.FaceDao
import com.tangoplus.tangoface.domain.repository.FaceRepository

class FaceRepositoryImpl(
    private val fDao : FaceDao
) : FaceRepository {

}
