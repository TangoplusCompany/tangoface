package com.tangoplus.tangoface.ui.settings

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.FragmentStatisticsBinding
import com.tangoplus.tangoface.domain.vo.PreCheckList
import com.tangoplus.tangoface.orbbec.application.MyApplication
import com.tangoplus.tangoface.ui.adapter.ChartRVAdapter
import com.tangoplus.tangoface.ui.adapter.PreCheckListFilterRVAdapter
import com.tangoplus.tangoface.ui.adapter.SettingsRVAdapter
import com.tangoplus.tangoface.ui.listener.PreCheckListFilterCheckListener
import com.tangoplus.tangoface.ui.settings.vm.ChartViewModel
import com.tangoplus.tangoface.ui.settings.vo.PreCheckListFilter
import com.tangoplus.tangoface.ui.view.BarChartRender
import kotlinx.coroutines.launch
import kotlin.collections.iterator


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
		val maxY = counts.values.maxOrNull() ?: 0
		val unitHeightDp = 10f
		val minHeightDp = 256f
		val maxHeightDp = 500f
		val density = resources.displayMetrics.density
		val calculatedHeight = (maxY * unitHeightDp).let {
			if (it < minHeightDp) minHeightDp else if (it > maxHeightDp) maxHeightDp else it
		} * density
		val bc = bd.bcCD
		bc.layoutParams.height = calculatedHeight.toInt()
		bc.requestLayout()

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
			textColor = ContextCompat.getColor(context, R.color.white)
		}

		bc.invalidate()
		bc.animateY(750, Easing.EaseInOutQuad)
	}

	private fun clearAllCheckLists() {
		adapters.forEach { it.setAllOff() }
	}

	private fun setAdapter() {
		val rvs = listOf(bd.rvS0, bd.rvS1, bd.rvS2)
		rvs.forEachIndexed { i, rv ->
			val isTablet = MyApplication.isTablet()
			val layoutManager = GridLayoutManager(requireContext(), if (isTablet) 4 else 2)
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
		bd.rvSPie.layoutManager = GridLayoutManager(context, 4)
		bd.rvSPie.adapter = cAdapter
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