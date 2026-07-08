package com.tangoplus.facebeautyexpert.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "t_after_survey")
data class AfterSurvey(
  @PrimaryKey(autoGenerate = true) val local_sn : Int = 0,
  val server_sn: Int = 0,
  val pain_score: Int = 0,
  val palpation_score : Int = 0,
  val discomfort_score: Int = 0,
)
