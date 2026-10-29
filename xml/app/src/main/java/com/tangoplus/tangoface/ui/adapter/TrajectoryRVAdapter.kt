package com.tangoplus.tangoface.ui.adapter

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.RvFaceStaticTrajectoryItemBinding
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.util.MathHelpers.getRealWorldDistance
import com.tangoplus.tangoface.domain.util.TrajectoryUtility.detectJawKeyPoints
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.ui.info.vm.InformationViewModel
import com.tangoplus.tangoface.ui.view.TrajectoryView
import kotlin.collections.get

class TrajectoryRVAdapter(
	private val ivm: InformationViewModel,
//	private val basicInfo: Pair<String, String>? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	inner class FSTIViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		private val trvFSTI = view.findViewById<TrajectoryView>(R.id.trvFSTI)
		private val tvFSTIName = view.findViewById<TextView>(R.id.tvFSTIName)
		private val tvFSTIReplay = view.findViewById<TextView>(R.id.tvFSTIReplay)
		private val tvFSTIData1 = view.findViewById<TextView>(R.id.tvFSTIData1)
		private val tvFSTIData2 = view.findViewById<TextView>(R.id.tvFSTIData2)
		private val tvFSTIData3 = view.findViewById<TextView>(R.id.tvFSTIData3)
		private val tvFSTIData4 = view.findViewById<TextView>(R.id.tvFSTIData4)
		private val tvFSTIData5 = view.findViewById<TextView>(R.id.tvFSTIData5)

		fun bind(result: List<PointF>, position: Int, maxBox: RectF) {
			Log.v("Traject", "$result")
			trvFSTI.setTrajectory(result, maxBox)
			val breakPoints = detectJawKeyPoints(result, if (position == 0) true else false, ivm.getSeq())
			if (breakPoints.isNotEmpty()) {
				trvFSTI.setBreakPoints(breakPoints)
				Log.v("breakPoints", "$breakPoints")
				tvFSTIData2.text = ""
				trvFSTI.restartDrawing()
				trvFSTI.setOnSingleClickListener {
					if (trvFSTI.isAnimating()) {
						trvFSTI.pause()
					} else {
						trvFSTI.resume()
					}
				}
				tvFSTIReplay.setOnSingleClickListener { trvFSTI.restartDrawing() }
			}

			// 관련 정보 넣기
//			tvFSTIName.text = when (basicInfo) {
//				null -> if (position == 0) "턱 왼쪽 이동 후 입 벌림" else "턱 오른쪽 이동 후 입 벌림"
//				else -> {
			tvFSTIName.text = 	when (ivm.getSeq()) {
						Sequence.JAW_LEFT_TILT -> {
							"턱 왼쪽 이동 후 입 벌림"
						}

						else -> {
							"턱 오른쪽 이동 후 입 벌림"
						}
					}
//				}
//			}

			val totalTime = if (result.isNotEmpty()) {
				"총 이동 시간: ${String.format("%.2f", result.size.toFloat() / 30)}초"
			} else {
				"총 이동 시간: 0.00초"
			}
			tvFSTIData1.text = totalTime

			// 턱 가측 이동 거리
			val chinDistance =
				if (result.isNotEmpty() && breakPoints.isNotEmpty() && breakPoints[0] < result.size) {
					val chinPoint1 = breakPoints[0]
					"턱 가측 이동 거리: ${
						String.format(
							"%.2f",
							getRealWorldDistance(result[0], result[chinPoint1], 0.05f)
						)
					}cm"
				} else {
					"턱 가측 이동 거리: 0.00cm"
				}
			tvFSTIData2.text = chinDistance

			// 턱 가측 이동 속도
			val chinSpeed = if (breakPoints.isNotEmpty()) {
				"턱 가측 이동 시간: ${String.format("%.2f", breakPoints[0].toFloat() / 30)}초"
			} else {
				"턱 가측 이동 시간: 0.00초"
			}
			tvFSTIData3.text = chinSpeed

			// 턱 개구 각도 (or 시간)
			val openMouseSpeend = if (breakPoints.size > 1) {
				"턱 개구 시간: ${String.format("%.2f", breakPoints[1].toFloat() / 30)}초"
			} else {
				"턱 개구 시간: 0.00초"
			}
			tvFSTIData4.text = openMouseSpeend
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		val binding = RvFaceStaticTrajectoryItemBinding.inflate(inflater, parent, false)
		return  FSTIViewHolder(binding.root)
	}

	override fun getItemCount(): Int {
		return 2
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		if (holder is FSTIViewHolder) {

			val items = mutableListOf<List<PointF>>()
			val leftPoints = ivm.leftTrajectory?.map { PointF(it.first, it.second) } ?: emptyList()
			val rightPoints = ivm.rightTrajectory?.map { PointF(it.first, it.second) } ?: emptyList()
			items.add(leftPoints)
			items.add(rightPoints)
			val maxBox = getMaxBouncingBox(items)
			val item = if (items.size == 2) items[position] else listOf()
			holder.bind(item, position, maxBox)
		}
	}

	private fun getBoundingBox(points: List<PointF>): RectF {
		val minX = points.minOf { it.x }
		val maxX = points.maxOf { it.x }
		val minY = points.minOf { it.y }
		val maxY = points.maxOf { it.y }
		return RectF(minX, minY, maxX, maxY)
	}

	private fun getMaxBouncingBox(pointsList: List<List<PointF>>): RectF {
		val rect = RectF()
		pointsList.forEach { points ->
			if (points.isNotEmpty()) {
				val box = getBoundingBox(points)
				if (rect.isEmpty) {
					rect.set(box)
				} else {
					rect.union(box)
				}
			}
		}
		return rect
	}
}