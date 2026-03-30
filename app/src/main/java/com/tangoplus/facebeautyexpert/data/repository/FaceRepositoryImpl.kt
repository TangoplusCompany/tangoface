package com.tangoplus.facebeautyexpert.data.repository

import com.tangoplus.facebeautyexpert.data.db.FaceDao
import com.tangoplus.facebeautyexpert.domain.repository.FaceRepository

class FaceRepositoryImpl(
    private val fDao : FaceDao
) : FaceRepository {

}
