package com.tangoplus.facebeautyexpert.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.databinding.RvMainTabItemBinding
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.ui.listener.OnMainTabClickListener

class MainTabRVAdapter(private val tabs : List<String>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
  var onMainTabClickListener: OnMainTabClickListener? = null
//  val imgs = listOf(R.drawable.poster_main_0)
  inner class MainTabViewHoler(v: View) : RecyclerView.ViewHolder(v) {
    val clMTI : ConstraintLayout = v.findViewById(R.id.clMTI)
    val tvMTITitle : TextView = v.findViewById(R.id.tvMTITitle)
    val ivMTI : TextView = v.findViewById(R.id.ivMTI)

    fun bind(currentItem : String, idx: Int) {
      tvMTITitle.text = currentItem
      ivMTI.text = when(idx) {
        0 -> "🎀"
        1 -> "😬"
        2 -> "🏹"
        else -> "🖼️"
      }

      clMTI.setOnSingleClickListener {
        onMainTabClickListener?.onMainTabClick(tabs[idx])
      }
    }
  }
  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): RecyclerView.ViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    val bd = RvMainTabItemBinding.inflate(inflater, parent, false)
    return MainTabViewHoler(bd.root)
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    if (holder is MainTabViewHoler) {
      val currentItem = tabs[position]
      holder.bind(currentItem, position)
    }
  }

  override fun getItemCount(): Int {
    return tabs.size
  }
}