package com.tangoplus.tangoface.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.RvChartItemBinding
import com.tangoplus.tangoface.ui.settings.vm.ChartViewModel

class ChartRVAdapter(
    private val cvm: ChartViewModel,
    private val fragment: Fragment
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val titles = listOf("항목 분포", "연령별", "성별", "국적별")
    inner class ChartViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val pcCI : PieChart = view.findViewById(R.id.pcCI)
        val tvCITitle : TextView = view.findViewById(R.id.tvCITitle)
        fun bind(currentItem: ArrayList<PieEntry>, position: Int) {
            tvCITitle.text = titles[position]

            val context = fragment.requireContext()
            val white = ContextCompat.getColor(context, R.color.white)
            val black = ContextCompat.getColor(context, R.color.black)
            val grayPalette = listOf(
                ContextCompat.getColor(context, R.color.subColor900),
                ContextCompat.getColor(context, R.color.subColor700),
                ContextCompat.getColor(context, R.color.subColor500),
                ContextCompat.getColor(context, R.color.subColor300),
                ContextCompat.getColor(context, R.color.subColor150),
            )

            val dataSet = PieDataSet(currentItem, "")
            dataSet.apply {
                sliceSpace = 2f
                selectionShift = 6f
                valueTextSize = 10f
                valueTextColor = white
                colors = grayPalette
//                valueFormatter = object : ValueFormatter() {
//                    override fun getFormattedValue(value: Float): String? {
//                        return if (value == 0f) "" else String.format("%.1f%%", value)
//                    }
//                }
            }

            val pd = PieData(dataSet)
            pd.apply {
                pd.setValueFormatter(PercentFormatter(pcCI))
            }
            pcCI.data = pd

            pcCI.apply {
                setUsePercentValues(true)
                isDrawHoleEnabled = true
                holeRadius = 40f
                transparentCircleRadius = 45f
                setDrawEntryLabels(true)
                setEntryLabelColor(white)
                setEntryLabelTextSize(12f)

                description.isEnabled = false
                animateY(1000, Easing.EaseInOutQuad)
                invalidate()
            }
            pcCI.legend.apply {
                isEnabled = false
                textSize = 12f
                form = Legend.LegendForm.CIRCLE
//                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
//                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
//                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val bd = RvChartItemBinding.inflate(inflater, parent, false)
        return ChartViewHolder(bd.root)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        if (holder is ChartViewHolder) {
            val currentItem = cvm.getPieDatas()[position]
            holder.bind(currentItem, position)
        }
    }

    override fun getItemCount(): Int {
        return cvm.getPieDatas().size
    }
}