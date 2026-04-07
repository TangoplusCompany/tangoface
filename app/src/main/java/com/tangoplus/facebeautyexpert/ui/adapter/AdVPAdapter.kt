package com.tangoplus.facebeautyexpert.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.databinding.VpAdItemBinding

class AdVPAdapter(private val images: List<Int>) : RecyclerView.Adapter<AdVPAdapter.AdViewHolder>() {

	inner class AdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
		val imageView: ImageView = itemView.findViewById(R.id.ivAd)
	}
	override fun onCreateViewHolder(
		p0: ViewGroup,
		p1: Int
	): AdViewHolder {
		val inflater = LayoutInflater.from(p0.context)
		val binding = VpAdItemBinding.inflate(inflater, p0, false)
		return AdViewHolder(binding.root)
	}

	override fun onBindViewHolder(
		p0: AdViewHolder,
		p1: Int
	) {
		val actualPosition = p1 % images.size
		p0.imageView.setImageResource(images[actualPosition])
	}

	override fun getItemCount(): Int = Int.MAX_VALUE // 무한 스크롤
}