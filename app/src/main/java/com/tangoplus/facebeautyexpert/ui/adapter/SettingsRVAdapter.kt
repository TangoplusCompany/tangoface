package com.tangoplus.facebeautyexpert.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.databinding.RvSettingsItemBinding
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.ui.statistics.PosterManageFragment

class SettingsRVAdapter(private val fragment: Fragment) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	private val items = listOf(
		"슬라이드 포스터 관리",
	)

	inner class SettingsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val tvSITitle: TextView = view.findViewById(R.id.tvSITitle)
		val clSI : ConstraintLayout = view.findViewById(R.id.clSI)
		fun bind(currentItem : String) {

			tvSITitle.text = currentItem
			clSI.setOnSingleClickListener {
				when (currentItem) {
					"슬라이드 포스터 관리" -> {
						fragment.requireActivity().supportFragmentManager.beginTransaction().apply {
							replace(R.id.flMain, PosterManageFragment())
							addToBackStack("slideposter")
							commit()
						}
					}
				}
			}

		}
	}

	override fun onCreateViewHolder(
		p0: ViewGroup,
		p1: Int
	): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(p0.context)
		val bd = RvSettingsItemBinding.inflate(inflater, p0, false)
		return SettingsViewHolder(bd.root)
	}

	override fun onBindViewHolder(
		p0: RecyclerView.ViewHolder,
		p1: Int
	) {
		if (p0 is SettingsViewHolder) {
			p0.bind(items[p1])
		}
	}

	override fun getItemCount(): Int {
		return items.size
	}
}