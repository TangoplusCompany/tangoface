package com.tangoplus.facebeautyexpert.data.db

import androidx.room.TypeConverter
import com.tangoplus.facebeautyexpert.data.db.FaceShape

class Converters {
	@TypeConverter
	fun fromFaceShape(shape: FaceShape): String = shape.name

	@TypeConverter
	fun toFaceShape(value: String): FaceShape = FaceShape.valueOf(value)

	@TypeConverter
	fun fromFacialAxisShape(curve: FacialAxisShape): String = curve.name
	@TypeConverter
	fun toFacialAxisShape(value: String): FacialAxisShape = FacialAxisShape.valueOf(value)

}