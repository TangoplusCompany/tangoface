package com.tangoplus.facebeautyexpert.ui.statistics

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.tangoplus.facebeautyexpert.databinding.FragmentSettingsBinding
import com.tangoplus.facebeautyexpert.domain.util.BitmapUtility.getScreenOrientation
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.ui.adapter.SettingsRVAdapter
import com.tangoplus.facebeautyexpert.ui.statistics.vm.SettingsViewModel


class StatisticsFragment : Fragment() {
	private lateinit var bd: FragmentSettingsBinding
	private val ssvm: SequenceSelectViewModel by activityViewModels()
	private val svm: SettingsViewModel by activityViewModels()
	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentSettingsBinding.inflate(inflater)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		userDataInit()
		setAdapter()
		bd.ibtnSBack.setOnSingleClickListener { parentFragmentManager.popBackStack() }
		// 버튼 drawable 크기 조절 필요
	}

	private fun setAdapter() {
		val isPortrait = activity?.getScreenOrientation()?.contains("portrait")
		val gridManager = GridLayoutManager(context, if (isPortrait == true) 2 else 3)
		val sAdapter = SettingsRVAdapter(this@StatisticsFragment, svm, ssvm)
		bd.rvS.apply {
			layoutManager = gridManager
			adapter = sAdapter
		}
	}

	private fun userDataInit() {
		if (svm.allMeasureItem.isEmpty() && svm.allUserItem.isEmpty()) {
			context?.let { svm.initDataFromDB(it) }
		}
	}
}