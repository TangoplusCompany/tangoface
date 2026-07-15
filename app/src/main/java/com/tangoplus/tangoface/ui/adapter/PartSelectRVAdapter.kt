package com.tangoplus.tangoface.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.RvFacialPartItemBinding
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.ui.info.vm.SelectViewModel
import com.tangoplus.tangoface.ui.info.vo.FacialPart
import com.tangoplus.tangoface.ui.listener.OnFacialPartClickListener

class PartSelectRVAdapter(private val svm: SelectViewModel, private val case: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
	var onFacialPartClickListener : OnFacialPartClickListener? = null
	inner class FacialPartViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val clFHAI : ConstraintLayout = view.findViewById(R.id.clFHAI)
		val tvFHAI: TextView = view.findViewById(R.id.tvFHAI)

		fun bind(currentItem: FacialPart) {
			tvFHAI.text = currentItem.displayName
			val isSelected = currentItem == svm.currentFacialPart.value

			// 2. Context 가져오기 (색상 리소스 참조용)
			val context = itemView.context

			if (isSelected) {
				clFHAI.backgroundTintList = ContextCompat.getColorStateList(context, R.color.accent)
			} else {
				clFHAI.backgroundTintList = ContextCompat.getColorStateList(context, R.color.transparent)
			}
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