package com.tangoplus.facebeautyexpert.ui.info

import android.annotation.SuppressLint
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.tangoplus.facebeautyexpert.R
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
		svm.updateFacialPart(FacialPart.DERIVE)
		svm.updateSequence(Sequence.COMFORT)
		if (ivm.isAngleModeActive.value) ivm.toggleAngleMode()

		ivm.setSeq(Sequence.COMFORT)
		ivm.releaseUnitIdx()
		if (!ivm.getLineOn()) ivm.switchedLineOn()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val fd = FaceDatabase.getDatabase(requireContext())
		fDao = fd.faceDao()
		ivm.currentCheckedLines.addAll(listOf(DrawLine.NECK, DrawLine.FACE, DrawLine.A_SAGGING_FACE, DrawLine.A_FACIAL_AXIS, DrawLine.A_HORIZON))

		setUserInfo()
		setResult()
		setRatioBtn()
		initAngleTool()
		observeViewModel()
	}

	private fun setAdapter() {
		val selectCase = if (ivm.getSeq() == Sequence.COMFORT) 0 else 1
		val vLayoutManager = GridLayoutManager(context, 5)
		val sAdapter = SelectRVAdapter(svm, selectCase)
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
		ivm.currentFaceStaticsItem.forEach { addCommentInFCI(it, isComparison) }

		val width = bd.ssiv.width
		val height = bd.ssiv.height
		ivm.setCropRatio(width, height)

		setImage()
		bd.rvGD2.apply {
			layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

		}
		setupSequenceDropdown()
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
		bd.overlayView.setTextBoxes(textInfos)
	}

	private fun initAngleTool() {
		viewLifecycleOwner.lifecycleScope.launch {
			ivm.isAngleModeActive.collect { isActive ->
				if (isActive) {
					val savedScale = bd.ssiv.scale
					val savedCenter = bd.ssiv.center?.let { PointF(it.x, it.y) }

					Log.d("AngleDebug", "Saved - scale: $savedScale, center: $savedCenter")

					bd.ssiv.apply {
						isPanEnabled = false
						isZoomEnabled = false
						isQuickScaleEnabled = false
					}

					bd.angleMeasureView.visibility = View.VISIBLE
					bd.angleMeasureView.post {
						if (savedCenter != null) {
							bd.ssiv.setScaleAndCenter(savedScale, savedCenter)
						}

						bd.angleMeasureView.postDelayed({
							bd.angleMeasureView.resetSelectedLine()
						}, 50)
					}

				} else {
					bd.angleMeasureView.visibility = View.GONE
					bd.ssiv.apply {
						isPanEnabled = true
						isZoomEnabled = true
						isQuickScaleEnabled = true
					}
				}
			}
		}

		bd.btnIDAngle.setOnClickListener {
			ivm.toggleAngleMode()
			bd.btnIDAngle.text = if (bd.btnIDAngle.text.contains("끄기")) "📐 각도계산 켜기" else "📐 각도계산 끄기"
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

		// item 클릭 후 facialPart, Adapter전부 확인
		ivm.currentCheckedLines.addAll(drawLines)
		svm.updateFacialPart(part)
		if (ivm.isAngleModeActive.value) {
			ivm.toggleAngleMode()
			bd.btnIDAngle.text = "📐 각도계산 켜기"
		}
		bd.rvGD1.adapter?.notifyDataSetChanged()
		setImage()
	}

	private fun setupSequenceDropdown() {
		// 1. 어댑터 설정 (표시할 이름들을 리스트로 추출)
		val displayNames = svm.sequenceEntries.map { it.displayName }
		val adapter = ArrayAdapter(requireContext(), R.layout.list_item_dropdown, displayNames)
		bd.actvTSeq.setAdapter(adapter)

		// 2. 드롭다운 아이템 클릭 리스너
		bd.actvTSeq.setOnItemClickListener { _, _, position, _ ->
			val selectedSeq = svm.sequenceEntries[position]

			// ViewModel 업데이트
			svm.updateSequence(selectedSeq)

			// 🕊️ 여기서 사진을 바꾸는 로직을 추가하시면 됩니다.
			ivm.setSeq(selectedSeq)
			setResult()
			bd.actvTSeq.clearFocus()
		}
		setAdapter()
	}

	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				svm.currentSequence.collect { seq ->
					// 3. ViewModel 상태와 UI 텍스트 동기화 (필터링 방지를 위해 filter = false)
					bd.actvTSeq.setText(seq.displayName, false)
				}
			}
		}
	}
}