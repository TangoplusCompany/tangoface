package com.tangoplus.tangoface.domain.vo

import java.io.Serializable

enum class MeasureType(val idx: Int, val displayName: String) : Serializable {
  NORMAL(0, "간편 검사"),
  EXPERT(1, "정밀 검사"),
  BOW(2, "페이스 보우");


  override fun toString(): String = displayName
  companion object {
    // idx 값으로 MeasureType을 찾는 함수
    fun fromIdx(idx: Int?): MeasureType? {
      return entries.find { it.idx == idx }
    }
  }
}