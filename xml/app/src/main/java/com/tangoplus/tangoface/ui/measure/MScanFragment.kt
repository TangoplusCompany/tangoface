package com.tangoplus.tangoface.ui.measure

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.FragmentMScanBinding
import com.tangoplus.tangoface.ui.measure.vm.MeasureViewModel
import com.tangoplus.tangoface.ui.measure.vm.ScanViewModel
import kotlin.getValue

class MScanFragment : Fragment() {
	private lateinit var bd : FragmentMScanBinding
	private val svm : ScanViewModel by activityViewModels()
	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentMScanBinding.inflate(inflater)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		bd.tvMSValue.text = svm.currentPitch
	}
}