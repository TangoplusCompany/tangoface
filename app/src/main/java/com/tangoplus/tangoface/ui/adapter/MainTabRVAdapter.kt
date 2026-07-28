package com.tangoplus.tangoface.ui.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.RvMainTabItemBinding
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.vo.MeasureType
import com.tangoplus.tangoface.ui.listener.OnMainTabClickListener

class MainTabRVAdapter(private val context: Context, private val tabs : List<String>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
  var onMainTabClickListener: OnMainTabClickListener? = null
//  val imgs = listOf(R.drawable.poster_main_0)
  inner class MainTabViewHoler(v: View) : RecyclerView.ViewHolder(v) {
    val clMTI : ConstraintLayout = v.findViewById(R.id.clMTI)
    val tvMTITitle : TextView = v.findViewById(R.id.tvMTITitle)
    val ivMTI : ImageView = v.findViewById(R.id.ivMTI)

    fun bind(currentItem : String, idx: Int) {
      val sizeInPx = (64 * context.resources.displayMetrics.density).toInt()

      ivMTI.layoutParams = ivMTI.layoutParams.apply {
        width = sizeInPx
        height = sizeInPx
      }

      tvMTITitle.text = currentItem
      val typeDrawable = ContextCompat.getDrawable(context,
        when (idx) {
          3 -> R.drawable.ic_normal
          2 -> R.drawable.ic_expert
          1 ->  R.drawable.ic_bow
          else -> R.drawable.ic_image
        }
      )
      ivMTI.setImageDrawable(typeDrawable)
      ImageViewCompat.setImageTintList(ivMTI, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent100)))
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