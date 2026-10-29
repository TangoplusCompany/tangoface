package com.tangoplus.tangoface.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.RvFactorItemBinding

class SurveyFactorRVAdapter(private val fragment : Fragment, private val scores: List<Int>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

  inner class FactorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val tvFITitle0 : TextView = view.findViewById(R.id.tvFITitle0)
    val tvFITitle1 : TextView = view.findViewById(R.id.tvFITitle1)
    val tvFITitle2 : TextView = view.findViewById(R.id.tvFITitle2)

    val tvFIValue0: TextView = view.findViewById(R.id.tvFIValue0)
    val tvFIValue10: TextView = view.findViewById(R.id.tvFIValue10)
    val tvFIValue11: TextView = view.findViewById(R.id.tvFIValue11)

    val tvFIValue2: TextView = view.findViewById(R.id.tvFIValue2)
    val llFIValue: LinearLayout = view.findViewById(R.id.llFIValue)

    fun bind(currentItem: List<Int>) {
      tvFITitle0.text = "통증 점수"
      tvFITitle1.text = "전문가 촉진"
      tvFITitle2.text = "불편함 점수"

      tvFIValue0.text = currentItem[0].toString()
      tvFIValue10.text = currentItem[1].toString()
      tvFIValue11.visibility = View.GONE
      tvFIValue2.text = currentItem[2].toString()
    }
  }

  override fun onCreateViewHolder(
    p0: ViewGroup,
    p1: Int
  ): RecyclerView.ViewHolder {
    val layoutInflater = LayoutInflater.from(p0.context)
    val binding = RvFactorItemBinding.inflate(layoutInflater, p0, false)
    return FactorViewHolder(binding.root)
  }

  override fun onBindViewHolder(
    p0: RecyclerView.ViewHolder,
    p1: Int
  ) {
    if (p0 is FactorViewHolder) p0.bind(scores )
  }

  override fun getItemCount(): Int {
    return 1
  }


}