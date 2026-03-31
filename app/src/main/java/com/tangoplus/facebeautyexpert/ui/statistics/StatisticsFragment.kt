package com.tangoplus.facebeautyexpert.ui.statistics

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.databinding.FragmentStatisticsBinding
import com.tangoplus.facebeautyexpert.domain.util.BitmapUtility.getScreenOrientation
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.domain.vo.PreCheckList
import com.tangoplus.facebeautyexpert.ui.adapter.ChartRVAdapter
import com.tangoplus.facebeautyexpert.ui.adapter.PreCheckListFilterRVAdapter
import com.tangoplus.facebeautyexpert.ui.listener.PreCheckListFilterCheckListener
import com.tangoplus.facebeautyexpert.ui.statistics.vm.ChartViewModel
import com.tangoplus.facebeautyexpert.ui.statistics.vm.SettingsViewModel
import com.tangoplus.facebeautyexpert.ui.statistics.vo.PreCheckListFilter
import com.tangoplus.facebeautyexpert.ui.view.BarChartRender
import kotlinx.coroutines.launch


class StatisticsFragment : Fragment(), PreCheckListFilterCheckListener {
	private lateinit var bd: FragmentStatisticsBinding
	private val cvm: ChartViewModel by activityViewModels()
	private val adapters = mutableListOf<PreCheckListFilterRVAdapter>()

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentStatisticsBinding.inflate(inflater)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		lifecycleScope.launch {
			observeChartData()
			setAdapter()
		}
		context?.let { cvm.loadPreCheckListFromDB(it) }
		setPieChart()
	}

	private fun observeChartData() {
		cvm.preCheckCounts.observe(viewLifecycleOwner) { counts ->
			setChart(counts)
		}
	}

	private var isChartInitialized = false
	private fun setChart(counts: Map<String, Int>) {
		val entries = ArrayList<BarEntry>()
		val labels = ArrayList<String>()

		var index = 0f
		for ((title, count) in counts) {
			entries.add(BarEntry(index, count.toFloat()))

			val displayName = try {
				PreCheckList.valueOf(title).displayName
			} catch (e: IllegalArgumentException) {
				title
			}

			labels.add(displayName)
			index++
		}

		val dataSet = BarDataSet(entries, "")
		dataSet.apply {
			color = resources.getColor(R.color.white, null)
			setDrawValues(true)
			valueTextSize = 12f
			valueTextColor = ContextCompat.getColor(context, R.color.white)
			valueFormatter = object : ValueFormatter() {
				override fun getBarLabel(barEntry: BarEntry?): String {
					val value = barEntry?.y?.toInt() ?: 0
					return if (value == 0) "" else value.toString()
				}
			}
		}
		val bcData = BarData(dataSet)
		bcData.barWidth = 0.5f

		val bc = bd.bcCD

		// ⭐ 렌더러와 기본 설정은 최초 1회만 설정
		if (!isChartInitialized) {
			bc.renderer = BarChartRender(bc, bc.animator, bc.viewPortHandler)
			bc.legend.apply { formSize = 0f }

			bc.xAxis.apply {
				position = XAxis.XAxisPosition.BOTTOM
				setDrawGridLines(false)
				setDrawAxisLine(true)
				axisLineColor = ContextCompat.getColor(context, R.color.white)
				axisLineWidth = 1f
				labelRotationAngle = -45f
			}

			bc.axisLeft.apply {
				axisMinimum = 0f
				setDrawAxisLine(false)
				setDrawGridLines(false)
				setDrawLabels(false)
				valueFormatter = object : ValueFormatter() {
					override fun getAxisLabel(value: Float, axis: AxisBase?): String {
						return value.toInt().toString()
					}
				}
			}

			bc.apply {
//                setExtraOffsets(0f, 0f, 0f, 40f)
				axisRight.isEnabled = false
				description.isEnabled = false
				legend.isEnabled = false
				setDrawGridBackground(false)
				setScaleEnabled(false)
				setTouchEnabled(false)
				setDrawValueAboveBar(true)
			}

			isChartInitialized = true
		}

		// 데이터와 라벨만 업데이트
		bc.data = bcData
		bc.xAxis.apply {
			labelCount = labels.size
			valueFormatter = IndexAxisValueFormatter(labels)
		}

		bc.invalidate()
		bc.animateY(750, Easing.EaseInOutQuad)
	}

	private fun clearAllCheckLists() {
		adapters.forEach { it.setAllOff() }
	}

	private fun setAdapter() {
		val rvs = listOf(bd.rvCD0, bd.rvCD1, bd.rvCD2)
		rvs.forEachIndexed { i, rv ->
			val isPortrait = activity?.getScreenOrientation()?.contains("portrait") == true
			val layoutManager = GridLayoutManager(requireContext(), if (isPortrait) 4 else 2)
			val cAdapter = PreCheckListFilterRVAdapter(cvm, i)
			cAdapter.preChecklistFilterCheckLister = this@StatisticsFragment
			rv.layoutManager = layoutManager
			rv.adapter = cAdapter
			adapters.add(cAdapter)
		}
	}

	private fun setPieChart() {
		// -----------------# pie chart 시작 #-----------------
		val cAdapter = ChartRVAdapter(cvm, this@StatisticsFragment)
		bd.rvCDPie.layoutManager = GridLayoutManager(context, 4)
		bd.rvCDPie.adapter = cAdapter
	}


	override fun preCheckListFilterCheck(
		item: PreCheckListFilter,
		isChecked: Boolean
	) {
		if (isChecked) cvm.currentCheckedPreCheckListFilter.add(item)
		else cvm.currentCheckedPreCheckListFilter.remove(item)
		Log.v("체크리스트체크", "${cvm.currentCheckedPreCheckListFilter}")
		cvm.filteredCount()
	}

}