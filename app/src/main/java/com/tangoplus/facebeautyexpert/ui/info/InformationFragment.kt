package com.tangoplus.facebeautyexpert.ui.info

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tangoplus.facebeautyexpert.data.db.FaceDao
import com.tangoplus.facebeautyexpert.data.db.FaceDatabase
import com.tangoplus.facebeautyexpert.databinding.FragmentInformationBinding
import com.tangoplus.facebeautyexpert.domain.util.BitmapUtility.getCombineBitmap
import com.tangoplus.facebeautyexpert.domain.util.FaceStaticUtility.addCommentInFCI
import com.tangoplus.facebeautyexpert.domain.util.FaceStaticUtility.buildFaceStaticItemList
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.domain.vo.Sequence
import com.tangoplus.facebeautyexpert.ui.main.vm.MainViewModel
import kotlinx.coroutines.launch
import com.tangoplus.facebeautyexpert.domain.util.BitmapUtility.setSSIV
import com.tangoplus.facebeautyexpert.ui.adapter.SelectRVAdapter
import com.tangoplus.facebeautyexpert.ui.info.vm.InformationViewModel
import com.tangoplus.facebeautyexpert.ui.info.vm.SelectViewModel
import com.tangoplus.facebeautyexpert.ui.info.vo.DrawLine
import com.tangoplus.facebeautyexpert.ui.info.vo.FacialPart
import com.tangoplus.facebeautyexpert.ui.listener.OnFacialPartClickListener
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope


class InformationFragment : Fragment(), OnFacialPartClickListener {
	private lateinit var bd: FragmentInformationBinding
	private val svm: SelectViewModel by activityViewModels()
	//    private val mvm : MeasureViewModel by activityViewModels()
	private val ivm: InformationViewModel by activityViewModels()
	private val mvm: MainViewModel by activityViewModels()
	private lateinit var fDao: FaceDao

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentInformationBinding.inflate(inflater)
		return bd.root
	}

	override fun onDestroy() {
		super.onDestroy()
		mvm.currentResult.value = null
	}

	override fun onDestroyView() {
		super.onDestroyView()

		bd.ssiv.recycle()
		mvm.comparisonDoubleItem = null
		ivm.currentCheckedLines.clear()
		ivm.setSeq(Sequence.COMFORT)
		ivm.releaseUnitIdx()
		if (!ivm.getLineOn()) ivm.switchedLineOn()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val fd = FaceDatabase.getDatabase(requireContext())
		fDao = fd.faceDao()
		ivm.currentCheckedLines.addAll(DrawLine.entries)
		ivm.currentCheckedLines.remove(DrawLine.A_RATIO_HORIZON)
		ivm.currentCheckedLines.remove(DrawLine.A_RATIO_VERTI)
		Log.v("currentCheckedLine", "${ivm.currentCheckedLines}")
		setAdapter()
		setUserInfo()
		setResult()
		setRatioBtn()
	}
	private fun setAdapter() {
		val vLayoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
		val sAdapter = SelectRVAdapter(svm, 0)
		sAdapter.onFacialPartClickListener = this@InformationFragment
		bd.rvGD1.apply {
			adapter = sAdapter
			layoutManager = vLayoutManager
		}
	}
	private fun setRatioBtn() {
		bd.btnIDClean.setOnSingleClickListener {
			ivm.switchedLineOn()
			bd.btnIDClean.text = if (ivm.getLineOn()) "🔍 가이드라인 끄기" else "🔍 가이드라인 켜기"
			setImage()
		}
	}

	private fun setResult() {
		bd.ssiv.recycle()

		val isComparison = mvm.comparisonDoubleItem != null
		val currentSeq = ivm.getSeq()

		if (isComparison) {
			val leftResult = mvm.comparisonDoubleItem?.first
			val rightResult = mvm.comparisonDoubleItem?.second

			val result = buildFaceStaticItemList(
				ivm,
				true,
				leftResult?.results,
				rightResult?.results,
				leftResult?.seqs,
				rightResult?.seqs
			)?.toMutableList()

			if (result != null) {
				ivm.currentFaceStaticsItem = result
			}

			val unitIdxCompare = ivm.unitIdxFromCompare.value


		} else {
			val faceStaticJson = mvm.currentResult.value?.results
			Log.v("setResult", "faceStaticJson: ${faceStaticJson?.length()}")
			val result =
				buildFaceStaticItemList(
					ivm,
					false,
					faceStaticJson,
					faceStaticJson,
					mvm.currentResult.value?.seqs ?: setOf(),
					null
				)?.toMutableList()
			if (result != null) {
				ivm.currentFaceStaticsItem = result
			}
		}
//		Log.v("staticDatas", "${ivm.currentFaceStaticsItem}")
		val basicInfo = if (isComparison) {
			Pair(mvm.comparisonDoubleItem?.first?.regDate!!, mvm.comparisonDoubleItem?.second?.regDate!!)
		} else null


		ivm.currentFaceStaticsItem.forEach { addCommentInFCI(it, isComparison) }


		setImage()
		bd.rvGD2.apply {
			layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

		}
	}


	@SuppressLint("UseKtx")
	private fun setImage() {
		val currentSeq = ivm.getSeq()
		lifecycleScope.launch {
			mvm.currentResult.value?.let { result ->
				coroutineScope {
					val leftDeferred = async { getCombineBitmap(this@InformationFragment, result, currentSeq, ivm) }
					leftDeferred.await()?.let { setSSIV(it, bd.ssiv) }
				}
			}
		}
	}



	private fun setUserInfo() {
		Log.v("info", "${mvm.currentResult.value?.userName}, ${mvm.currentResult.value?.userMobile}}")
		val name = mvm.currentResult.value?.userName ?: "GUEST"
		val mobile = mvm.currentResult.value?.userMobile ?: ""
		val transformMobile = if (mobile.length == 11 && mobile.startsWith("010")) {
			"${mobile.substring(0, 3)}-${mobile.substring(3, 7)}-${mobile.substring(7)}"
		} else {
			""
		}
		val info = "$name ${if (mobile.length < 5) "" else ", $transformMobile"}"

		bd.tvIdInfo.visibility = View.VISIBLE
		bd.tvIdInfo.text = info
		bd.tvIdInfo.gravity = if (name == "GUEST") {
			Gravity.CENTER
		} else {
			Gravity.NO_GRAVITY
		}
	}


	override fun onFacialPartClick(part: FacialPart) {
		ivm.currentCheckedLines.clear()
		val drawLines = when (part) {
			FacialPart.DERIVE -> listOf( DrawLine.A_FACE, DrawLine.A_SAGGING_FACE, DrawLine.A_FACIAL_AXIS, DrawLine.A_HORIZON)
			FacialPart.RATIO -> listOf(DrawLine.A_RATIO_HORIZON, DrawLine.A_RATIO_VERTI, )
			FacialPart.TOP -> listOf(DrawLine.A_EYE, DrawLine.L_EYE, DrawLine.A_EYEBROW, DrawLine.L_EYEBROW, DrawLine.L_CORNEA)
			FacialPart.MIDDLE -> listOf(DrawLine.A_NOSE, DrawLine.L_NOSE, DrawLine.A_EARFLAP, DrawLine.A_ALAR_ORAL, DrawLine.A_SIDE_HEAD, DrawLine.E_CHEEKBONE, )
			FacialPart.BOTTOM -> listOf(DrawLine.A_CHIN, DrawLine.E_CHEEKS, DrawLine.A_CANTHUS_ORAL)
		}
		ivm.currentCheckedLines.addAll(drawLines)

		setImage()
	}
}