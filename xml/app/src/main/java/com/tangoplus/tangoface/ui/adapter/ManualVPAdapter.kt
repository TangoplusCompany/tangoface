package com.tangoplus.tangoface.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.RvJawGuideItemBinding

class ManualVPAdapter(private val drawables: List<Int>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	inner class ManualViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val ivJGI : ImageView = view.findViewById(R.id.ivJGI)

		fun bind(currentItem : Int, idx: Int) {

			ivJGI.setImageResource(currentItem)
		}
	}

	override fun onCreateViewHolder(
		p0: ViewGroup,
		p1: Int
	): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(p0.context)
		val bd = RvJawGuideItemBinding.inflate(inflater, p0, false)
		return ManualViewHolder(bd.root)
	}

	override fun onBindViewHolder(
		p0: RecyclerView.ViewHolder,
		p1: Int
	) {
		if (p0 is ManualViewHolder) {
			p0.bind(drawables[p1], p1)
		}
	}

	override fun getItemCount(): Int {
		return drawables.size
	}
}