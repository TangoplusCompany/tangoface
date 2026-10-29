package com.tangoplus.tangoface.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.VpAdItemBinding

class AdVPAdapter(private val items: List<String>) : RecyclerView.Adapter<AdVPAdapter.AdViewHolder>() {

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

	override fun onBindViewHolder(p0: AdViewHolder, position: Int) {
		// 무한 슬라이드를 위해 position % items.size 사용
		val actualPosition = position % items.size
		val imagePath = items[actualPosition]

		// 파일 경로로부터 이미지 로드
		Glide.with(p0.itemView.context)
			.load(imagePath)
			.placeholder(R.color.bg500) // 로딩 중 보여줄 색상이나 이미지
			.into(p0.imageView) // 아이템 레이아웃의 ImageView ID
	}

	override fun getItemCount(): Int = Int.MAX_VALUE // 무한 스크롤
}