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
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.tangoplus.facebeautyexpert.data.db.FaceDao
import com.tangoplus.facebeautyexpert.data.db.FaceDatabase
import com.tangoplus.facebeautyexpert.data.db.FaceShape
import com.tangoplus.facebeautyexpert.data.db.FacialAxisShape
import com.tangoplus.facebeautyexpert.databinding.FragmentInformationBinding
import com.tangoplus.facebeautyexpert.domain.util.BitmapUtility
import com.tangoplus.facebeautyexpert.domain.util.BitmapUtility.getCombineBitmap
import com.tangoplus.facebeautyexpert.domain.util.FaceStaticUtility.addCommentInFCI
import com.tangoplus.facebeautyexpert.domain.util.FaceStaticUtility.buildFaceStaticItemList
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.domain.vo.Sequence
import com.tangoplus.facebeautyexpert.ui.main.vm.MainViewModel
import kotlinx.coroutines.launch
import com.tangoplus.facebeautyexpert.domain.util.BitmapUtility.setSSIV
import com.tangoplus.facebeautyexpert.ui.adapter.FactorRVAdapter
import com.tangoplus.facebeautyexpert.ui.adapter.SelectRVAdapter
import com.tangoplus.facebeautyexpert.ui.info.vm.InformationViewModel
import com.tangoplus.facebeautyexpert.ui.info.vm.SelectViewModel
import com.tangoplus.facebeautyexpert.ui.info.vo.DrawLine
import com.tangoplus.facebeautyexpert.ui.info.vo.FacialPart
import com.tangoplus.facebeautyexpert.ui.info.vo.Factor
import com.tangoplus.facebeautyexpert.ui.listener.OnFacialPartClickListener
import com.tangoplus.facebeautyexpert.ui.view.TextBoxOverlayView
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
		initAngleTool()
	}

	private fun setAdapter() {
		val vLayoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
		val sAdapter = SelectRVAdapter(svm, 0)
		sAdapter.onFacialPartClickListener = this@InformationFragment
		bd.rvGD1.apply {
			adapter = sAdapter
			layoutManager = vLayoutManager
		}

		val fAdapter = FactorRVAdapter(this@InformationFragment, mvm.currentResult.value?.factors ?: Factor(
			FacialAxisShape.DEFAULT, FaceShape.DEFAULT, Pair(0f, 0f)))
		val fLayoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
		bd.rvGD2.apply {
			adapter = fAdapter
			layoutManager = fLayoutManager
		}
	}

	private fun setRatioBtn() {
		bd.btnIDClean.setOnSingleClickListener {
			ivm.switchedLineOn()
			bd.btnIDClean.text = if (ivm.getLineOn()) "🔍 가이드라인 끄기" else "🔍 가이드라인 켜기"
			setImage()
		}

		bd.btnIDGrid.setOnSingleClickListener {
			ivm.switchedGridLineOn()
			bd.btnIDGrid.text = if (ivm.getGridLineOn()) "ꖛ 그리드 끄기" else "ꖛ 그리드 켜기 "
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
					val deferred = async { getCombineBitmap(this@InformationFragment, result, currentSeq, ivm) }
					val awaitedResult = deferred.await()
					awaitedResult.first?.let { setSSIV(it, bd.ssiv) }

					val drawInfos = awaitedResult.second
					val faceRect = awaitedResult.third
					setBoxInfos(drawInfos, faceRect)
					bd.overlayView.visibility = if (ivm.getLineOn()) View.VISIBLE else View.GONE
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

	private fun setBoxInfos(textInfos: List<BitmapUtility.TextBoxData>, faceRect : TextBoxOverlayView.FaceRect?) {

		bd.overlayView.setFaceRect(faceRect)
		bd.overlayView.attachToImageView(bd.ssiv)
		Log.v("오버레이크기", "${textInfos.map { it -> Pair(it.anchorX, it.anchorY) }.subList(0, 2)}, (${bd.overlayView.width}, ${bd.overlayView.height})")
		bd.overlayView.setTextBoxes(textInfos)
	}

	private fun initAngleTool() {
		// 1. ViewModel 상태 관찰
		viewLifecycleOwner.lifecycleScope.launch {
			ivm.isAngleModeActive.collect { isActive ->
				// AngleMeasureView 가시성 조절
				bd.angleMeasureView.visibility = if (isActive) View.VISIBLE else View.GONE

				// ⭐ SSIV 조작 잠금 ⭐
				bd.ssiv.apply {
					isPanEnabled = !isActive
					isZoomEnabled = !isActive
				}

				// 버튼 디자인 변경 (선택 사항)
				bd.btnIDAngle.isSelected = isActive
			}
		}

		// 2. 버튼 클릭 이벤트
		bd.btnIDAngle.setOnClickListener {
			ivm.toggleAngleMode()
			bd.btnIDAngle.text = if (bd.btnIDAngle.text.contains("끄기")) "📐 각도계산 켜기" else "📐 각도계산 끄기"
		}

		// 3. 각도 변화 리스너 연결
		bd.angleMeasureView.onAngleChangeListener = { angle ->
			ivm.updateManualAngle(angle)
		}
	}


	override fun onFacialPartClick(part: FacialPart) {
		ivm.currentCheckedLines.clear()
		Log.v("클릭드", "${part}")
		val drawLines = when (part) {
			FacialPart.DERIVE -> listOf(DrawLine.NECK, DrawLine.FACE, DrawLine.A_SAGGING_FACE, DrawLine.A_FACIAL_AXIS, DrawLine.A_HORIZON)
			FacialPart.RATIO -> listOf(DrawLine.A_RATIO_HORIZON, DrawLine.A_RATIO_VERTI)
			FacialPart.TOP -> listOf(DrawLine.EYE,  DrawLine.EYEBROW,  DrawLine.L_CORNEA)
			FacialPart.MIDDLE -> listOf(DrawLine.NOSE,DrawLine.A_ALAR_ORAL, DrawLine.A_SIDE_HEAD, DrawLine.E_CHEEKBONE, )
			FacialPart.BOTTOM -> listOf(DrawLine.E_CHEEKS, DrawLine.A_CANTHUS_ORAL, DrawLine.MOUSE)
		}
		ivm.currentCheckedLines.addAll(drawLines)

		setImage()
	}
}