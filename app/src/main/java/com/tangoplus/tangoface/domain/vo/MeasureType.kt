package com.tangoplus.tangoface.domain.vo

import java.io.Serializable

enum class MeasureType(val idx: Int) : Serializable {
  NORMAL(0),
  EXPERT(1),
  BOW(2);

  companion object {
    // idx 값으로 MeasureType을 찾는 함수
    fun fromIdx(idx: Int?): MeasureType? {
      return entries.find { it.idx == idx }
    }
  }
}