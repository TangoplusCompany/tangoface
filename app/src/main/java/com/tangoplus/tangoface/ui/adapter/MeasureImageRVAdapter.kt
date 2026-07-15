package com.tangoplus.tangoface.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.RvPosterItemBinding

class MeasureImageRVAdapter(
	private val fragment: Fragment,
	private val uris: List<String?>,
	private val onImageClick: (Int) -> Unit // 클릭 시 인덱스를 넘겨줌
) : RecyclerView.Adapter<MeasureImageRVAdapter.MeasureImageViewHolder>() {

	inner class MeasureImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val ivPI: ImageView = view.findViewById(R.id.ivPI)
		val tvPI: TextView = view.findViewById(R.id.tvPI)
		val cvPI: CardView = view.findViewById(R.id.cvPI)
		val ibtnPIDelete: ImageButton = view.findViewById(R.id.ibtnPIDelete)
		fun bind(uri: String?, position: Int) {
			// 1. 비율 설정 (16:9)
			ibtnPIDelete.visibility = View.INVISIBLE
			val params = cvPI.layoutParams as ConstraintLayout.LayoutParams
			params.dimensionRatio = "H,3:4"
			cvPI.layoutParams = params

			// 2. 이미지 로드 (null이거나 비어있으면 추가 아이콘)
			if (!uri.isNullOrEmpty()) {
				Glide.with(ivPI).load(uri).centerCrop().into(ivPI)
			} else {
				Glide.with(ivPI)
					.load(R.drawable.drawable_image_add) // 미리 준비한 추가 이미지
					.into(ivPI)
			}

			// 3. 텍스트 설정
			tvPI.text = if (position == 0) "정면 자세" else "목폄 자세"

			// 4. 클릭 시 수정/추가 동작 (인덱스 전달)
			itemView.setOnClickListener {
				onImageClick(position)
			}
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeasureImageViewHolder {
		val bd = RvPosterItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return MeasureImageViewHolder(bd.root)
	}

	override fun onBindViewHolder(p0: MeasureImageViewHolder, p1: Int) {
		p0.bind(uris[p1], p1)
	}


	override fun getItemCount(): Int = 2
}