package com.tangoplus.facebeautyexpert.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.databinding.RvSurveyItemBinding
import com.tangoplus.facebeautyexpert.ui.info.vm.SelectViewModel

class SurveyRVAdapter(
  private val onAllChecked: (pain: Int, palpation: Int, discomfort: Int) -> Unit // 모두 완료 시 콜백
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

  // 각 포지션별 점수 저장 (-1은 아직 체크 안 됨을 의미)
  private val scores = IntArray(3) { -1 }
  private val titles = listOf("주관적 통증 정도 (VAS)", "전문가 촉진 점수", "턱관절 일상 불편함")

  inner class SurveyViewHolder(v: View) : RecyclerView.ViewHolder(v) {
    val tvRSITitle: TextView = v.findViewById(R.id.tvRSITitle)
    val tvRSIScore: TextView = v.findViewById(R.id.tvRSIScore)
    val sbRSIVas: SeekBar = v.findViewById(R.id.sbRSIVas)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    val bd = RvSurveyItemBinding.inflate(inflater, parent, false)
    return SurveyViewHolder(bd.root)
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    if (holder is SurveyViewHolder) {
      holder.tvRSITitle.text = titles[position]

      // 초기 상태 설정
      val currentScore = scores[position]
      if (currentScore == -1) {
        holder.sbRSIVas.progress = 0
        holder.tvRSIScore.text = "0 / 10"
      } else {
        holder.sbRSIVas.progress = currentScore
        holder.tvRSIScore.text = "$currentScore / 10"
      }

      holder.sbRSIVas.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          holder.tvRSIScore.text = "$progress / 10"
          if (fromUser) {
            scores[position] = progress // 유저가 건드린 점수 기록
          }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
          // 유저가 손을 뗐을 때, 혹시 한 번도 안 건드렸어도 0점이나 현재 점수로 확정
          if (scores[position] == -1) {
            scores[position] = seekBar?.progress ?: 0
          }

          // 3개 항목 모두 체크되었는지 확인 (-1이 없는지)
          if (scores.all { it != -1 }) {
            // 액티비티/프래그먼트로 결과 전달
            onAllChecked(scores[0], scores[1], scores[2])
          }
        }
      })
    }
  }

  override fun getItemCount(): Int = 3
}