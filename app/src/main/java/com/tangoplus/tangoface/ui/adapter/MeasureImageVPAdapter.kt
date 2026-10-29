package com.tangoplus.tangoface.ui.adapter

import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MeasureImageVPAdapter(private val item: String?) : RecyclerView.Adapter<MeasureImageVPAdapter.VH>() {
	inner class VH(val iv: ImageView) : RecyclerView.ViewHolder(iv)
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
		VH(ImageView(parent.context).apply {
			layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
			scaleType = ImageView.ScaleType.CENTER_CROP
		})
	override fun onBindViewHolder(holder: VH, position: Int) {
		Glide.with(holder.iv).load(item).into(holder.iv)
	}
	override fun getItemCount() = 1
}