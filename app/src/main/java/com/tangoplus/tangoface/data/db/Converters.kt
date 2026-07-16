package com.tangoplus.tangoface.data.db

import androidx.room.TypeConverter
import com.tangoplus.tangoface.domain.vo.MeasureType

class Converters {
	@TypeConverter
	fun fromFaceShape(shape: FaceShape): String = shape.name

	@TypeConverter
	fun toFaceShape(value: String): FaceShape = FaceShape.valueOf(value)

	@TypeConverter
	fun fromFacialAxisShape(curve: FacialAxisShape): String = curve.name
	@TypeConverter
	fun toFacialAxisShape(value: String): FacialAxisShape = FacialAxisShape.valueOf(value)


	@TypeConverter
	fun fromMeasureType(type: MeasureType?): Int? {
		return type?.idx
	}

	@TypeConverter
	fun toMeasureType(idx: Int?): MeasureType? {
		return MeasureType.fromIdx(idx)
	}


}