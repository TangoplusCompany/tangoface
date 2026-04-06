package com.tangoplus.facebeautyexpert.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.databinding.RvFacialPartItemBinding
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.ui.info.vm.SelectViewModel
import com.tangoplus.facebeautyexpert.ui.info.vo.FacialPart
import com.tangoplus.facebeautyexpert.ui.listener.OnFacialPartClickListener

class SelectRVAdapter(private val svm: SelectViewModel, private val case: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
	var onFacialPartClickListener : OnFacialPartClickListener? = null
	inner class FacialPartViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val clFHAI : ConstraintLayout = view.findViewById(R.id.clFHAI)
		val tvFHAI: TextView = view.findViewById(R.id.tvFHAI)

		fun bind(currentItem: FacialPart) {
			tvFHAI.text = currentItem.displayName
			clFHAI.setOnSingleClickListener {
				onFacialPartClickListener?.onFacialPartClick(currentItem)
			}
		}
	}
	override fun onCreateViewHolder(
		p0: ViewGroup,
		p1: Int
	): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(p0.context)
		val binding = RvFacialPartItemBinding.inflate(inflater, p0, false)
		return FacialPartViewHolder(binding.root)
	}

	override fun onBindViewHolder(
		holder: RecyclerView.ViewHolder,
		p1: Int
	) {
		val currentItem = svm.facialHorizonSelect[p1]
		if (holder is FacialPartViewHolder) holder.bind(currentItem)
	}

	override fun getItemCount(): Int {
		return when (case) {
			0 -> svm.facialHorizonSelect.size
			else -> 0
		}
	}
}