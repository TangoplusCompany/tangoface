package com.tangoplus.facebeautyexpert.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.databinding.RvSettingsItemBinding
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.ui.statistics.ChartDialogFragment
import com.tangoplus.facebeautyexpert.ui.statistics.FlickerSettingsDialogFragment
import com.tangoplus.facebeautyexpert.ui.statistics.GyroDialogFragment
import com.tangoplus.facebeautyexpert.ui.statistics.ListDialogFragment
import com.tangoplus.facebeautyexpert.ui.statistics.vm.SettingsViewModel

class SettingsRVAdapter(
	private val fragment: Fragment,
	private val svm: SettingsViewModel,
	private val ssvm: SequenceSelectViewModel
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	inner class SettingsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		private val tvSITitle: TextView = view.findViewById(R.id.tvSITitle)
		private val btnSIStart: AppCompatButton = view.findViewById(R.id.btnSIStart)

		fun bind(item: Pair<String, String>, idx: Int) {
			tvSITitle.text = item.first
			btnSIStart.text = item.second
			btnSIStart.setOnSingleClickListener {
				val sfm = fragment.activity?.supportFragmentManager
				when (idx) {
					0 -> {
						ssvm.fragmentCase = 2
						val dialog = SequenceSelectDialogFragment()
						sfm?.let { manager -> dialog.show(manager, "SequenceSelect") }
						return@setOnSingleClickListener
					}

					1 -> {
						val gyro = GyroDialogFragment()
						sfm?.let { manager -> gyro.show(manager, "gyroDialogFragment") }
					}

					2 -> {
						val chart = ChartDialogFragment()
						sfm?.let { manager -> chart.show(manager, "chartDialogFragment") }
					}

					3 -> {
						val list = ListDialogFragment()
						sfm?.let { manager -> list.show(manager, "ListDialogFragment") }
					}

					4 -> {
//						val intent = Intent(fragment.requireActivity(), NormalCameraActivity::class.java)
//						fragment.startActivity(intent)
//						fragment.requireActivity().finish()
					}
					5 -> {
						val flicker = FlickerSettingsDialogFragment()
						sfm?.let { manager -> flicker.show(manager, "FlickerDialogFragment") }
					}
				}
			}
		}
	}

	override fun onCreateViewHolder(
		parent: ViewGroup,
		viewType: Int
	): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		val bd = RvSettingsItemBinding.inflate(inflater, parent, false)
		return SettingsViewHolder(bd.root)
	}

	override fun onBindViewHolder(
		holder: RecyclerView.ViewHolder,
		position: Int
	) {
		if (holder is SettingsViewHolder) {
			val currentItem = svm.settings[position]
			holder.bind(currentItem, position)
		}
	}

	override fun getItemCount(): Int {
		return svm.settings.size
	}
}