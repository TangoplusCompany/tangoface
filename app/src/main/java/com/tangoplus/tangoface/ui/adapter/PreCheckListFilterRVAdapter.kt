package com.tangoplus.tangoface.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.RvChecklistFilterItemBinding
import com.tangoplus.tangoface.ui.listener.PreCheckListFilterCheckListener
import com.tangoplus.tangoface.ui.settings.vm.ChartViewModel
import com.tangoplus.tangoface.ui.settings.vo.PreCheckListFilter
import kotlin.collections.set

class PreCheckListFilterRVAdapter(
	private val cvm: ChartViewModel,
	private val idx: Int
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
	var preChecklistFilterCheckLister: PreCheckListFilterCheckListener? = null

	private val separatedCheckListFilter = when (idx) {
		0 -> cvm.getCheckListFilter0()
		1 -> cvm.getCheckListFilter1()
		2 -> cvm.getCheckListFilter2()
		else -> cvm.getCheckListFilter3()
	}

	private val checkedStates = mutableMapOf<PreCheckListFilter, Boolean>().apply {
		cvm.allPreChecklistFilter.forEach { this[it] = false }
	}

	inner class ChecklistFilterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val mcbPCFI: MaterialCheckBox = view.findViewById(R.id.mcbPCFI)

		fun bind(currentItem: PreCheckListFilter) {
			mcbPCFI.text = currentItem.displayName
			mcbPCFI.setOnCheckedChangeListener(null)
			mcbPCFI.isChecked = checkedStates[currentItem] ?: false

			mcbPCFI.setOnCheckedChangeListener { _, isChecked ->
				checkedStates[currentItem] = isChecked
				preChecklistFilterCheckLister?.preCheckListFilterCheck(currentItem, isChecked)
			}
		}
	}

	override fun onCreateViewHolder(
		parent: ViewGroup,
		viewType: Int
	): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		val binding = RvChecklistFilterItemBinding.inflate(inflater, parent, false)
		return ChecklistFilterViewHolder(binding.root)
	}

	override fun onBindViewHolder(
		holder: RecyclerView.ViewHolder,
		position: Int
	) {
		val currentItem = separatedCheckListFilter[position]
		if (holder is ChecklistFilterViewHolder) holder.bind(currentItem)
	}

	override fun getItemCount(): Int {
		return separatedCheckListFilter.size
	}

	fun setAllOff() {
		checkedStates.keys.forEach { checkedStates[it] = false }
		notifyDataSetChanged()
	}

}