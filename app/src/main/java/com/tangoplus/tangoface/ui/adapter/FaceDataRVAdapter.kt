package com.tangoplus.tangoface.ui.adapter

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.domain.vo.FaceStaticItem
import com.tangoplus.tangoface.databinding.RvFaceStaticItemBinding
import com.tangoplus.tangoface.databinding.RvFaceStaticTrajectoryItemBinding
import com.tangoplus.tangoface.domain.util.FaceStaticUtility.distanceKeyword
import com.tangoplus.tangoface.domain.util.FaceStaticUtility.earShoulderKeyword
import com.tangoplus.tangoface.domain.util.FaceStaticUtility.extentKeyword
import com.tangoplus.tangoface.domain.util.FaceStaticUtility.horizontalKeyword
import com.tangoplus.tangoface.domain.util.FaceStaticUtility.noseShoulderKeyword
import com.tangoplus.tangoface.domain.util.FaceStaticUtility.verticalKeyword
import com.tangoplus.tangoface.ui.listener.OnFaceStaticCheckListener
import com.tangoplus.tangoface.domain.util.TrajectoryUtility.detectJawKeyPoints
import com.tangoplus.tangoface.ui.view.TrajectoryView
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.util.MathHelpers.getRealWorldDistance
import com.tangoplus.tangoface.ui.info.InformationViewModel
import java.text.DecimalFormat
import kotlin.math.abs

class FaceDataRVAdapter(
	private val fragment: Fragment,
	private val ivm: InformationViewModel,
	private val basicInfo: Pair<String, String>? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
	var faceStaticCheckListener: OnFaceStaticCheckListener? = null

	// ui 판단하는 list ( 좌우 주의/정상 색 처리 )
	// angleKeyword는 중앙에 값이 1개만 나와야 함.

	companion object {
		const val TYPE_NORMAL = 0
		const val TYPE_TRAJECTORY = 1
	}

	private val emptyString = "-"
	private val linkedCheckDetailMap = listOf(
		mapOf(
			0 to listOf(1),
			1 to listOf(0),
			7 to listOf(8, 9),
			8 to listOf(7, 9),
			9 to listOf(7, 8)
		)
	)
	private val linkedCheckComparisonMap = listOf(
		mapOf(
			0 to listOf(1),
			1 to listOf(0),
		),
		mapOf(
			4 to listOf(5, 6),
			5 to listOf(4, 6),
			6 to listOf(4, 5),

			)
	)

	inner class FSViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        private val ivFSISeq0 : ImageView = view.findViewById(R.id.ivFSISeq0)
//        private val ivFSISeq1 : ImageView = view.findViewById(R.id.ivFSISeq1)

		private val view4: View = view.findViewById(R.id.view4)
		private val clFSI: ConstraintLayout = view.findViewById(R.id.clFSI)
		private val cbFSITitle: MaterialCheckBox = view.findViewById(R.id.cbFSITitle)

		private val tvFSIStandard: TextView = view.findViewById(R.id.tvFSIStandard)
		private val tvFSISeq0: TextView = view.findViewById(R.id.tvFSISeq0)
		private val tvFSISeq1: TextView = view.findViewById(R.id.tvFSISeq1)
		private val tvFSI10: TextView = view.findViewById(R.id.tvFSI10)
		private val tvFSI11: TextView = view.findViewById(R.id.tvFSI11)
		private val tvFSI12: TextView = view.findViewById(R.id.tvFSI12)
		private val tvFSI20: TextView = view.findViewById(R.id.tvFSI20)
		private val tvFSI21: TextView = view.findViewById(R.id.tvFSI21)
		private val tvFSI22: TextView = view.findViewById(R.id.tvFSI22)
		private val tvFSI31: TextView = view.findViewById(R.id.tvFSI31)
		private val tvFSI32: TextView = view.findViewById(R.id.tvFSI32)
		private val tvFSI41: TextView = view.findViewById(R.id.tvFSI41)
		private val tvFSI42: TextView = view.findViewById(R.id.tvFSI42)
		private val tvFSI50: TextView = view.findViewById(R.id.tvFSI50)
		private val tvFSI51: TextView = view.findViewById(R.id.tvFSI51)
		private val tvFSI52: TextView = view.findViewById(R.id.tvFSI52)
		private val tvFSI60: TextView = view.findViewById(R.id.tvFSI60)
		private val tvFSI61: TextView = view.findViewById(R.id.tvFSI61)
		private val tvFSI62: TextView = view.findViewById(R.id.tvFSI62)
		private val tvFSI71: TextView = view.findViewById(R.id.tvFSI71)

		private val llFSI0: LinearLayout = view.findViewById(R.id.llFSI0)
		private val llFSI1: LinearLayout = view.findViewById(R.id.llFSI1)
		private val llFSI2: LinearLayout = view.findViewById(R.id.llFSI2)
		private val llFSI3: LinearLayout = view.findViewById(R.id.llFSI3)
		private val llFSI4: LinearLayout = view.findViewById(R.id.llFSI4)
		private val llFSI5: LinearLayout = view.findViewById(R.id.llFSI5)
		private val llFSI6: LinearLayout = view.findViewById(R.id.llFSI6)
		private val llFSI7: LinearLayout = view.findViewById(R.id.llFSI7)

		fun bind(item: FaceStaticItem, position: Int) {
			val df = DecimalFormat("#.##")
			cbFSITitle.text = item.label

			// state 확인
			val notation = when (item.label) {
				in distanceKeyword -> "cm"
				in extentKeyword -> "㎠"
				else -> "˚"
			}
			val seqString = if (basicInfo == null) {
				when (ivm.getBtnIndex()) {
					1 -> Pair("정면", "교합")
					3 -> Pair("개구", "목폄")
					else -> Pair("좌측", "우측")
				}
			} else {
				Pair("좌측", "우측")
			}

			tvFSISeq0.text = seqString.first
			tvFSISeq1.text = seqString.second
			/* 1. 1개의 기록을 보는 Detail
			* - comparisonItem을 가져와서 adapter측에서 좌우 차이 계산 (계산 후 좌우 주의/정상 보여주기)
			*
			* 2. 2개의 기록을 보는 comparison
			* - comparisonItem 좌우 전부 차이 계산후 들어옴 (그대로 보여주기)
			* */

			clFSI.visibility = View.VISIBLE
			val standard = when (item.label) {
				in horizontalKeyword -> 180
				in verticalKeyword -> 90
				in noseShoulderKeyword -> 70
				in earShoulderKeyword -> 90
//                in standardKeyword -> 0
				else -> -1
			}

			// ---------# standard가 -1일 경우 기준값이 없음 #----------
			tvFSIStandard.text = if (standard != -1) {
				"$standard˚"
			} else {
				"-"
			}
			// ----------# value case에 맞게 #-----------
			// OOOO
			if (item.value00 != null && item.value01 != null && item.value10 != null && item.value11 != null) {
				// layout 맞추기
				llFSI0.visibility = View.VISIBLE
				llFSI1.visibility = View.VISIBLE
				llFSI2.visibility = View.VISIBLE
				llFSI3.visibility = View.VISIBLE
				llFSI4.visibility = View.VISIBLE
				llFSI5.visibility = View.VISIBLE
				llFSI6.visibility = View.VISIBLE
				llFSI7.visibility = View.VISIBLE

				tvFSI10.text = "L"
				tvFSI20.text = "R"
				tvFSI50.text = "좌우 차이"
				tvFSI60.text = "이탈 부위"
				val layoutParams0 = tvFSI50.layoutParams as LinearLayout.LayoutParams
				layoutParams0.weight = 1f
				tvFSI50.layoutParams = layoutParams0

				val layoutParams2 = tvFSI60.layoutParams as LinearLayout.LayoutParams
				layoutParams2.weight = 1f
				tvFSI60.layoutParams = layoutParams2

				tvFSI52.visibility = View.VISIBLE
				tvFSI62.visibility = View.VISIBLE


				val seq0Data = df.format(item.value00) + notation
				val seq1Data = df.format(item.value01) + notation
				val seq2Data = df.format(item.value10) + notation
				val seq3Data = df.format(item.value11) + notation

				tvFSI11.text = seq0Data
				tvFSI21.text = seq1Data
				tvFSI12.text = seq2Data
				tvFSI22.text = seq3Data

				val leftStandardDiff0 = if (standard != -1) {
					"${df.format(abs(standard - abs(item.value10)))}$notation"
				} else {
					"-"
				}
				val leftStandardDiff1 = if (standard != -1) {
					"${df.format(abs(standard - abs(item.value01)))}$notation"
				} else {
					"-"
				}

				val rightStandardDiff0 = if (standard != -1) {
					"${df.format(abs(standard - abs(item.value10)))}$notation"
				} else {
					"-"
				}
				val rightStandardDiff1 = if (standard != -1) {
					"${df.format(abs(standard - abs(item.value11)))}$notation"
				} else {
					"-"
				}

				tvFSI31.text = leftStandardDiff0
				tvFSI32.text = leftStandardDiff1
				tvFSI41.text = rightStandardDiff0
				tvFSI42.text = rightStandardDiff1

				// 좌우 차이
				// 원본 차이 (부호 포함)
				val leftSeqRawDiff = abs(item.value00) - abs(item.value01)
				val rightSeqRawDiff = abs(item.value10) - abs(item.value11)

				// 절댓값만 따로 계산
				val leftSeqDiff = abs(leftSeqRawDiff)
				val rightSeqDiff = abs(rightSeqRawDiff)

				tvFSI51.text = "${df.format(leftSeqDiff)}$notation"
				tvFSI52.text = "${df.format(rightSeqDiff)}$notation"

				// 이탈 부위 판단
				val worsePart = when {
					leftSeqRawDiff < 0 -> "R" // 음수면 오른쪽이 더 안 좋음
					leftSeqRawDiff > 0 -> "L" // 양수면 왼쪽이 더 안 좋음
					else -> "-"               // 차이가 없을 때
				}

				tvFSI61.text = worsePart
				tvFSI62.text = worsePart


				// ----------# 값이 2개만 있는 경우 ex) 비교 or 정면-교합의 턱끝 각도 등 #----------
				// OXOX
			} else if (item.value00 != null && item.value10 != null) {
				// layout 맞추기
				llFSI2.visibility = View.GONE
				llFSI4.visibility = View.GONE

				val soloTitle = if (item.label in distanceKeyword) "길이" else "기울기"
				tvFSI10.text = soloTitle
//                Log.v("케이스", "value00&01 -> ${item.label} $soloTitle")
				val seq0Data = df.format(item.value00) + notation
				val seq2Data = df.format(item.value10) + notation
				tvFSI11.text = seq0Data
				tvFSI12.text = seq2Data

				val leftStandardDiff = if (standard != -1) {
					"${df.format(abs(standard - abs(item.value00)))}$notation"
				} else {
					"-"
				}
				val rightStandardDiff = if (standard != -1) {
					"${df.format(abs(standard - abs(item.value10)))}$notation"
				} else {
					"-"
				}
				tvFSI31.text = leftStandardDiff
				tvFSI32.text = rightStandardDiff

				// 좌우차이 다르게하기
				tvFSI50.text = "단계 차이"
				val layoutParams = tvFSI50.layoutParams as LinearLayout.LayoutParams
				layoutParams.weight = 2f
				tvFSI50.layoutParams = layoutParams
				tvFSI52.visibility = View.GONE

				val seqDiff = abs(abs(item.value00) - abs(item.value10))
				tvFSI51.text = df.format(seqDiff) + notation

				tvFSI60.text = "이탈 단계"
				val layoutParams2 = tvFSI50.layoutParams as LinearLayout.LayoutParams
				layoutParams2.weight = 2f
				tvFSI60.layoutParams = layoutParams2
				tvFSI62.visibility = View.GONE


				val seqRawDiff = abs(item.value00) - abs(item.value10)
				val worsePart = when {
					seqRawDiff < 0 -> seqString.first // 음수면 오른쪽이 더 안 좋음
					seqRawDiff > 0 -> seqString.second // 양수면 왼쪽이 더 안 좋음
					else -> "-"               // 차이가 없을 때
				}
				tvFSI61.text = worsePart


				// ----------# 개구 (볼 너비) 일 때 #--------
				// OOXX
			} else if (item.value00 != null && item.value01 != null && item.value10 == null && item.value11 == null) {
				llFSI0.visibility = View.VISIBLE
				llFSI2.visibility = View.VISIBLE
				tvFSI10.text = "L"
				tvFSI20.text = "R"
				val seq0Data = df.format(item.value00) + notation
				val seq1Data = df.format(item.value01) + notation
				tvFSI11.text = seq0Data
				tvFSI21.text = seq1Data

				val leftStandardDiff = if (standard != -1) {
					"${df.format(abs(standard - abs(item.value00)))}$notation"
				} else {
					"-"
				}

				val rightStandardDiff = if (standard != -1) {
					"${df.format(abs(standard - abs(item.value01)))}$notation"
				} else {
					"-"
				}

				tvFSI31.text = leftStandardDiff
				tvFSI41.text = rightStandardDiff

				val seqDiff = abs(abs(item.value00) - abs(item.value01))
				tvFSI51.text = df.format(seqDiff) + notation
				val leftSeqRawDiff = abs(item.value00) - abs(item.value01)
				val worsePart = when {
					leftSeqRawDiff < 0 -> "R" // 음수면 오른쪽이 더 안 좋음
					leftSeqRawDiff > 0 -> "L" // 양수면 왼쪽이 더 안 좋음
					else -> "-"               // 차이가 없을 때
				}
				tvFSI61.text = worsePart

				// 빈값은 전부 "-" 처리
				val emptyTextView = listOf(tvFSI12, tvFSI22, tvFSI32, tvFSI42, tvFSI52, tvFSI62)
				emptyTextView.forEach { it.text = emptyString }

				// ---------# 값이 00 1개만 있을 때 #----------
				// OXXX
			} else if (item.value00 != null && item.value11 == null) {
				llFSI2.visibility = View.GONE
				llFSI4.visibility = View.GONE

				tvFSI10.text = "각도"

				val seq0Data = df.format(item.value00) + notation
				tvFSI11.text = seq0Data

				val leftStandardDiff = if (standard != -1) {
					"${df.format(abs(standard - abs(item.value00)))}$notation"
				} else {
					"-"
				}
				tvFSI31.text = leftStandardDiff

				val emptyTextView = listOf(tvFSI12, tvFSI32, tvFSI51, tvFSI61, tvFSI52, tvFSI62)
				emptyTextView.forEach { it.text = emptyString }
				// ----------# value10 1개만 있는 경우 ex) 개구 상태 볼너비 #----------
				// XXOX
			} else if (item.value00 == null && item.value01 == null && item.value10 != null && item.value11 == null) {
				llFSI2.visibility = View.GONE
				llFSI4.visibility = View.GONE

				tvFSI10.text = "각도"
				val seq0Data = df.format(item.value10) + notation
				tvFSI12.text = seq0Data

				val rightStandardDiff = if (standard != -1) {
					"${df.format(abs(standard - abs(item.value10)))}$notation"
				} else {
					"-"
				}
				tvFSI32.text = rightStandardDiff

				val emptyTextView = listOf(tvFSI11, tvFSI31, tvFSI41, tvFSI51, tvFSI61, tvFSI52, tvFSI62)
				emptyTextView.forEach { it.text = emptyString }
				// XOXO
			} else if (item.value00 == null && item.value01 != null && item.value10 == null && item.value11 != null) {
				llFSI0.visibility = View.VISIBLE
				llFSI1.visibility = View.GONE
				llFSI2.visibility = View.VISIBLE
				llFSI3.visibility = View.GONE
				llFSI4.visibility = View.VISIBLE
				llFSI5.visibility = View.VISIBLE
				llFSI6.visibility = View.VISIBLE
				llFSI7.visibility = View.VISIBLE

				tvFSI10.text = "R"
				tvFSI50.text = "좌우 차이"
				tvFSI60.text = "이탈 부위"

				val seq1Data = df.format(item.value01) + notation
				val seq3Data = df.format(item.value11) + notation

				tvFSI11.text = seq1Data  // 첫 번째 행에 01 값
				tvFSI12.text = seq3Data  // 두 번째 행에 11 값

				val leftStandardDiff = if (standard != -1) {
					"${df.format(abs(standard - abs(item.value01)))}$notation"
				} else "-"

				val rightStandardDiff = if (standard != -1) {
					"${df.format(abs(standard - abs(item.value11)))}$notation"
				} else "-"

				tvFSI31.text = leftStandardDiff
				tvFSI32.text = rightStandardDiff

				val seqDiff = abs(abs(item.value01) - abs(item.value11))
				tvFSI51.text = "${df.format(seqDiff)}$notation"
				tvFSI52.visibility = View.GONE

				val seqRawDiff = abs(item.value01) - abs(item.value11)
				val worsePart = when {
					seqRawDiff < 0 -> "하단"
					seqRawDiff > 0 -> "상단"
					else -> "-"
				}
				tvFSI61.text = worsePart
				tvFSI62.visibility = View.GONE

				// 빈 칸 처리
				val emptyTextView = listOf(tvFSI21, tvFSI22, tvFSI41, tvFSI42)
				emptyTextView.forEach { it.text = emptyString }

				// XXOO
			} else if (item.value00 == null && item.value01 == null && item.value10 != null && item.value11 != null) {
				llFSI0.visibility = View.VISIBLE
				llFSI1.visibility = View.VISIBLE
				llFSI2.visibility = View.VISIBLE
				llFSI3.visibility = View.VISIBLE
				llFSI4.visibility = View.VISIBLE
				llFSI5.visibility = View.VISIBLE
				llFSI6.visibility = View.VISIBLE
				llFSI7.visibility = View.VISIBLE

				tvFSI10.text = "L"
				tvFSI20.text = "R"
				tvFSI50.text = "좌우 차이"
				tvFSI60.text = "이탈 부위"
				val layoutParams0 = tvFSI50.layoutParams as LinearLayout.LayoutParams
				layoutParams0.weight = 1f
				tvFSI50.layoutParams = layoutParams0

				val layoutParams2 = tvFSI60.layoutParams as LinearLayout.LayoutParams
				layoutParams2.weight = 1f
				tvFSI60.layoutParams = layoutParams2

				tvFSI52.visibility = View.VISIBLE
				tvFSI62.visibility = View.VISIBLE

				val seq2Data = df.format(item.value10) + notation
				val seq3Data = df.format(item.value11) + notation

				tvFSI12.text = seq2Data
				tvFSI22.text = seq3Data

				val leftStandardDiff0 = if (standard != -1) {
					"${df.format(abs(standard - abs(item.value10)))}$notation"
				} else {
					"-"
				}


				val rightStandardDiff0 = if (standard != -1) {
					"${df.format(abs(standard - abs(item.value10)))}$notation"
				} else {
					"-"
				}


				tvFSI31.text = leftStandardDiff0
				tvFSI41.text = rightStandardDiff0

				// 좌우 차이
				// 원본 차이 (부호 포함)
				val rightSeqRawDiff = abs(item.value10) - abs(item.value11)

				// 절댓값만 따로 계산
				val rightSeqDiff = abs(rightSeqRawDiff)

				tvFSI52.text = "${df.format(rightSeqDiff)}$notation"

			}

			// ---------# UI 조건 넣기 처음/끝 #-------------

			// ----------# text 색넣기 #----------

//            val fullText = item.comment
//            val spannable = SpannableString(fullText)
//            val middleIndex = fullText.indexOf(">").takeIf { it != -1 } ?: fullText.length
//            val endIndex = fullText.indexOf("\n").takeIf { it != -1 } ?: fullText.length
//
//            if (fullText.contains(">")) {
//                spannable.setSpan(ForegroundColorSpan(
//                    ContextCompat.getColor(fragment.context, R.color.deleteColor)
//                ), 0, middleIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
//                spannable.setSpan(ForegroundColorSpan(
//                    ContextCompat.getColor(fragment.context, R.color.mainColor)
//                ), middleIndex + 1, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
//                tvFSIState.text = spannable
//            }

			tvFSI71.text = item.comment

			// 없는 값일 경우 - 처리
			if (tvFSISeq0.text.contains("NaN")) tvFSISeq0.text = "-"
			if (tvFSISeq1.text.contains("NaN")) tvFSISeq1.text = "-"


			clFSI.setOnSingleClickListener {
				cbFSITitle.performClick()
			}
//            tvFSI0.setTypeface(null, Typeface.NORMAL)
//            tvFSI1.setTypeface(null, Typeface.NORMAL)

			cbFSITitle.setOnCheckedChangeListener(null)
			cbFSITitle.isChecked = item.isChecked
			clFSI.background = ContextCompat.getDrawable(
				fragment.requireContext(),
				when (item.isChecked) {
					true -> R.drawable.bckgnd_stroke_2_start_end
					false -> {
						when (position) {
							0 -> R.drawable.effect_top_20
							ivm.currentFaceStaticsItem.size - 1 -> R.drawable.effect_bottom_20
							else -> R.drawable.effect_0
						}
					}
				}
			)
			view4.visibility = if (position == ivm.currentFaceStaticsItem.size - 1) {
				View.INVISIBLE
			} else {
				View.VISIBLE
			}

			cbFSITitle.setOnCheckedChangeListener { _, isChecked ->

				notifyItemChanged(position)

				item.isChecked = isChecked
				faceStaticCheckListener?.onFaceStaticCheck(position, isChecked)

				if (basicInfo == null && ivm.getBtnIndex() == 3) {
					val detailMap = linkedCheckDetailMap.first() // 또는 linkedCheckDetailMap[0]
					detailMap[position]?.forEach { linkedPos ->
						val linkedItem = ivm.currentFaceStaticsItem[linkedPos]
						if (linkedItem.isChecked != isChecked) {
							linkedItem.isChecked = isChecked
							notifyItemChanged(linkedPos)
						}
					}
				} else if (basicInfo != null && ivm.getBtnIndex() in listOf(5, 6)) {
					val mapIndex = when (ivm.getBtnIndex()) {
						5 -> 0
						6 -> 1
						else -> return@setOnCheckedChangeListener
					}
					val compMap =
						linkedCheckComparisonMap.getOrNull(mapIndex) ?: return@setOnCheckedChangeListener
					compMap[position]?.forEach { linkedPos ->
						val linkedItem = ivm.currentFaceStaticsItem[linkedPos]
						if (linkedItem.isChecked != isChecked) {
							linkedItem.isChecked = isChecked
							notifyItemChanged(linkedPos)
						}
					}
				}
			}
		}
	}

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
			val breakPoints =
				detectJawKeyPoints(result, if (position == 0) true else false, ivm.getBtnIndex())
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
			tvFSTIName.text = when (basicInfo) {
				null -> if (position == 0) "턱 왼쪽 이동 후 입 벌림" else "턱 오른쪽 이동 후 입 벌림"
				else -> {
					when (ivm.getBtnIndex()) {
						2 -> {
							"턱 왼쪽 이동 후 입 벌림"
						}

						else -> {
							"턱 오른쪽 이동 후 입 벌림"
						}
					}
				}
			}

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
		return when (viewType) {
			TYPE_TRAJECTORY -> {
				val binding = RvFaceStaticTrajectoryItemBinding.inflate(inflater, parent, false)
				FSTIViewHolder(binding.root)
			}

			else -> {
				val binding = RvFaceStaticItemBinding.inflate(inflater, parent, false)
				FSViewHolder(binding.root)
			}
		}
	}

	override fun getItemCount(): Int {
		return if (basicInfo == null) {
			when (ivm.getBtnIndex()) {
				2 -> 2
				else -> ivm.currentFaceStaticsItem.size
			}
		} else {
			when (ivm.getBtnIndex()) {
				3, 4 -> 2
				else -> ivm.currentFaceStaticsItem.size
			}
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		if (holder is FSViewHolder) {
			val item = ivm.currentFaceStaticsItem[position]
			holder.bind(item, position)
		} else if (holder is FSTIViewHolder) {
			Log.v("Traject", "${ivm.leftTrajectory} ${ivm.rightTrajectory}")

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

	override fun getItemViewType(position: Int): Int {
		return if (basicInfo == null) {
			when (ivm.getBtnIndex()) {
				2 -> TYPE_TRAJECTORY
				else -> TYPE_NORMAL
			}
		} else {
			when (ivm.getBtnIndex()) {
				3, 4 -> TYPE_TRAJECTORY
				else -> TYPE_NORMAL
			}
		}
	}

	fun setAllChecked(checked: Boolean) {
		ivm.currentFaceStaticsItem.forEach { item ->
			item.isChecked = checked
		}
		notifyDataSetChanged()
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

	private fun bindCell(container: View, tv: TextView, value: Float?) {
		if (value == null) {
			container.visibility = View.GONE          // 공간까지 제거
			// container.visibility = View.INVISIBLE  // 공간 유지하고 싶으면 이걸 사용
			tv.text = ""
			container.isEnabled = false
			container.alpha = 0.4f
		} else {
			container.visibility = View.VISIBLE
			tv.text = String.format("%.2f", value)
			container.isEnabled = true
			container.alpha = 1f
		}
	}

}
