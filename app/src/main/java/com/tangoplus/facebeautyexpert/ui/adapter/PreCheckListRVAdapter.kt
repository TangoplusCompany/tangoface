package com.tangoplus.facebeautyexpert.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.domain.vo.PreCheckList
import com.tangoplus.facebeautyexpert.databinding.RvCheckListItemBinding
import com.tangoplus.facebeautyexpert.ui.listener.PreCheckListCheckListener
import com.tangoplus.facebeautyexpert.ui.measure.vm.OnBoardingViewModel

class PreCheckListRVAdapter(obvm: OnBoardingViewModel, idx: Int) :
	RecyclerView.Adapter<RecyclerView.ViewHolder>() {
	var preChecklistCheckListener: PreCheckListCheckListener? = null
	private val separatedCheckList = when (idx) {
		1 -> obvm.getCheckList1()
		2 -> obvm.getCheckList2()
		3 -> obvm.getCheckList3()
		else -> obvm.getCheckList0()
	}
	private val checkedStates = mutableMapOf<PreCheckList, Boolean>().apply {
		separatedCheckList.forEach { this[it] = false }
	}

	inner class CheckListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val mcbCLI: MaterialCheckBox = view.findViewById(R.id.mcbCLI)
		fun bind(currentItem: PreCheckList) {
			mcbCLI.text = currentItem.displayName
			mcbCLI.setOnCheckedChangeListener(null)
			mcbCLI.isChecked = checkedStates[currentItem] ?: false

			mcbCLI.setOnCheckedChangeListener { _, isChecked ->
				checkedStates[currentItem] = isChecked
				preChecklistCheckListener?.preCheckListCheck(currentItem, isChecked)
			}
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		val binding = RvCheckListItemBinding.inflate(inflater, parent, false)
		return CheckListViewHolder(binding.root)
	}

	override fun getItemCount(): Int {
		return separatedCheckList.size

	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		if (holder is CheckListViewHolder) {
			val currentItem = separatedCheckList[position]
			holder.bind(currentItem)
		}
	}

	fun setAllOff() {
		checkedStates.keys.forEach { checkedStates[it] = false }
		notifyDataSetChanged()
	}
}