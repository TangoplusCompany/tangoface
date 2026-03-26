package com.tangoplus.tangoface.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.tangoplus.tangoface.databinding.FragmentListDialogBinding
import com.tangoplus.tangoface.domain.util.BitmapUtility.getScreenOrientation
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.ui.adapter.GridRVAdapter
import com.tangoplus.tangoface.ui.adapter.SelectRVAdapter
import com.tangoplus.tangoface.ui.settings.vm.SequenceSelectViewModel
import com.tangoplus.tangoface.ui.settings.vm.SettingsViewModel

class ListDialogFragment : DialogFragment() {
	private lateinit var bd: FragmentListDialogBinding
	private val svm: SettingsViewModel by activityViewModels()
	private val ssvm: SequenceSelectViewModel by activityViewModels()
	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentListDialogBinding.inflate(inflater)
		return bd.root
	}

	override fun onResume() {
		super.onResume()
		dialog?.window?.setLayout(
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.MATCH_PARENT
		)
		dialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
		dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		setTabAdapter()
		setObserver()
		setClickListener()
	}

	private fun setObserver() {
		svm.currentTabIndex.observe(viewLifecycleOwner) { idx ->
			setGridAdapter(idx)
		}
	}

	private fun setTabAdapter() {
		val tabAdapter = SelectRVAdapter(this@ListDialogFragment, ssvm, svm, 3)
		val horizonLm = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
		bd.rvLD1.apply {
			layoutManager = horizonLm
			adapter = tabAdapter
			isNestedScrollingEnabled = false
		}
	}

	private fun setGridAdapter(tabIdx: Int) {
		val adapterItems = when (tabIdx) {
			0 -> svm.allMeasureItem
			else -> svm.allUserItem

		}
		val mgiAdapter = GridRVAdapter(
			this@ListDialogFragment,
			adapterItems, null, null, svm, 2
		)
		val isPortrait = activity?.getScreenOrientation()?.contains("portrait") == true
		val lm = GridLayoutManager(context, if (isPortrait) 2 else 3)
		bd.rvLD0.apply {
			layoutManager = lm
			adapter = mgiAdapter
		}
	}

	private fun setClickListener() {
		bd.ibtnLDBack.setOnSingleClickListener { dismiss() }
	}
}