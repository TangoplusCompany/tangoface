package com.tangoplus.tangoface.ui.statistics

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.FragmentFlickerSettingsDialogBinding
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.util.PreferenceUtility
import com.tangoplus.tangoface.ui.statistics.vm.SettingsViewModel

class FlickerSettingsDialogFragment : DialogFragment() {
	private lateinit var bd: FragmentFlickerSettingsDialogBinding
	private val svm : SettingsViewModel by activityViewModels()
	private lateinit var prefs : PreferenceUtility
	private val btns = mutableListOf<AppCompatButton>()
	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentFlickerSettingsDialogBinding.inflate(inflater)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		context?.let {
			prefs = PreferenceUtility(it)
			val savedFlickerType = prefs.getFlickerType()
			svm.selectedFlicker = savedFlickerType

			btns.add(0, bd.btnFSDOff)
			btns.add(1, bd.btnFSD50)
			btns.add(2, bd.btnFSD60)
			setClickListener()
		} ?: run {
			dismiss()
			Log.e("flickError", "FLicker설정에서 context 오류가 발생했습니다")
		}
	}
	private fun setClickListener() {
		btns.forEachIndexed { idx, btn ->
			if (idx == svm.selectedFlicker) {
				btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.black))
				btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
			} else {
				btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
				btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
			}
			btn.setOnSingleClickListener {
				svm.selectedFlicker = idx
				prefs.setFlickerType(idx)
				dialog?.dismiss()
			}
		}
	}
}