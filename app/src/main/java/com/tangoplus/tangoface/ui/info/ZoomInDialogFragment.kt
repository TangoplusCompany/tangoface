package com.tangoplus.tangoface.ui.info

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.FragmentZoomInDialogBinding
import com.tangoplus.tangoface.domain.util.AnimationUtility.scrollToView
import com.tangoplus.tangoface.domain.util.BitmapUtility.getCombineBitmap
import com.tangoplus.tangoface.domain.util.BitmapUtility.setSSIV
import com.tangoplus.tangoface.domain.util.FaceStaticUtility.addCommentInFCI
import com.tangoplus.tangoface.domain.util.FaceStaticUtility.buildFaceStaticItemList
import com.tangoplus.tangoface.domain.util.FaceStaticUtility.setLinesInImage
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.ui.adapter.FaceDataRVAdapter
import com.tangoplus.tangoface.ui.info.vo.DrawLine
import com.tangoplus.tangoface.ui.listener.OnFaceStaticCheckListener
import com.tangoplus.tangoface.ui.main.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.properties.Delegates

class ZoomInDialogFragment : DialogFragment(), OnFaceStaticCheckListener {
	private lateinit var bd: FragmentZoomInDialogBinding
	private val ivm: InformationViewModel by activityViewModels()
	private val mvm: MainViewModel by activityViewModels()
	private lateinit var faceStaticAdapter: FaceDataRVAdapter
	private var seq by Delegates.notNull<Sequence>() // 이건 실제 10단계의 seq임 (btn index가 아님)

	// 현재 실제 seq 0~9가 전달됨.

	companion object {
		private const val ARG_SEQ = "arguement_sequence"
		private const val ARG_COMPARISON = "arguement_comparison"
		fun newInstance(seq: Sequence, isLeft: Boolean? = null): ZoomInDialogFragment {
			val fragment = ZoomInDialogFragment()
			val args = Bundle()
			args.putParcelable(ARG_SEQ, seq)
			if (isLeft != null) {
				args.putBoolean(ARG_COMPARISON, isLeft)
			}
			fragment.arguments = args
			return fragment
		}
	}

	override fun onDismiss(dialog: DialogInterface) {
		super.onDismiss(dialog)

		// btn 초기화 전 선택된 line들 전부 해제 하기
		if (seq in listOf(Sequence.COMFORT, Sequence.BOW)) {
			Log.v("이전버튼", "${ivm.getPreviousBtnIndex()}, ${ivm.getBtnIndex()}")
			ivm.setBtnIndex(ivm.getPreviousBtnIndex())
			parentFragmentManager.setFragmentResult(
				"zoomInClosed",
				Bundle().apply { putBoolean("zoomIn", true) })
			ivm.currentCheckedLines.clear()
		}
		val isComparison = mvm.comparisonDoubleItem != null
		val seq = ivm.getBtnIndex()
		if (isComparison) {
			if (seq !in listOf(3, 4)) {
				val faceStaticJson = mvm.currentResult.value?.results
				val result = buildFaceStaticItemList(
					ivm,
					false,
					faceStaticJson,
					faceStaticJson,
					mvm.currentResult.value?.seqs,
					null
				)?.toMutableList()
				if (result != null) {
					Log.v("닫힐때확인", "${ivm.getBtnIndex()}, $faceStaticJson")
					ivm.currentFaceStaticsItem = result
					ivm.currentFaceStaticsItem.forEach { addCommentInFCI(it, isComparison) }
				}
			}
		} else {
			if (seq != 2) {
				val faceStaticJson = mvm.currentResult.value?.results
				val result = buildFaceStaticItemList(
					ivm,
					false,
					faceStaticJson,
					faceStaticJson,
					mvm.currentResult.value?.seqs,
					null
				)?.toMutableList()

				if (result != null) {
					Log.v("닫힐때확인", "${ivm.getBtnIndex()}, $faceStaticJson")
					ivm.currentFaceStaticsItem = result
					ivm.currentFaceStaticsItem.forEach { addCommentInFCI(it, isComparison) }
				}
			}
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentZoomInDialogBinding.inflate(inflater)
		return bd.root
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setStyle(STYLE_NORMAL, R.style.AppTheme_FlexableDialogFragment)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		seq = arguments?.getParcelable(ARG_SEQ) ?: Sequence.RESTING
		val isLeft: Boolean? = if (arguments?.containsKey(ARG_COMPARISON) == true) {
			arguments?.getBoolean(ARG_COMPARISON)
		} else {
			null
		}
		Log.v("뷰가 다시 그려지나요?", "onViewCreated, $isLeft -> $seq")
		lifecycleScope.launch {
			when (isLeft) {
				true -> {
					mvm.comparisonDoubleItem?.first.let {
						if (it != null) {
							val bitmap = getCombineBitmap(this@ZoomInDialogFragment, it, seq, ivm, true)
							bitmap?.let { bt -> setSSIV(bt, bd.ssivZID) }
						}
					}
				}

				false -> {
					mvm.comparisonDoubleItem?.second.let {
						if (it != null) {
							val bitmap = getCombineBitmap(this@ZoomInDialogFragment, it, seq, ivm, true)
							bitmap?.let { bt -> setSSIV(bt, bd.ssivZID) }
						}
					}
				}

				null -> {
					if (seq == Sequence.COMFORT || seq == Sequence.BOW) {
						ivm.currentCheckedLines.clear()
						setAdapter()
						setSwitch()
						mvm.currentResult.value?.let {
							val bitmap = getCombineBitmap(this@ZoomInDialogFragment, it, seq, ivm, true)
							bitmap?.let { bt -> setSSIV(bt, bd.ssivZID) }
						}
					} else {
						bd.rvZID.visibility = View.GONE
						bd.msZIDAngle.visibility = View.GONE
						bd.btnZIDClean.visibility = View.GONE
						mvm.currentResult.value?.let {
							withContext(Dispatchers.Main) {
								val bitmap = getCombineBitmap(this@ZoomInDialogFragment, it, seq, ivm, true)
								Log.v(
									"비트맵확인",
									"${bitmap?.width} x ${bitmap?.height} = ${it.seqs}, ${it.results.length()}"
								)
								bitmap?.let { bt -> setSSIV(bt, bd.ssivZID) }
							}
						}
						bd.ssivZID.post {
							val ssivHeight = bd.ssivZID.height
							bd.clZID.layoutParams = bd.clZID.layoutParams.apply {
								height = ssivHeight
							}
							bd.flZID.layoutParams = bd.flZID.layoutParams.apply {
								height = ssivHeight
							}
						}
					}
				}
			}
		}

		bd.ibtnZIDExit.setOnSingleClickListener {
			dismiss()
		}
		bd.msZIDLock.setOnCheckedChangeListener { _, isChecked ->
			dialog?.setCancelable(!isChecked)
			bd.ibtnZIDExit.visibility = if (isChecked) View.INVISIBLE else View.VISIBLE
		}

		bd.msZIDContour.apply {
			visibility =
				if (mvm.currentResult.value?.depthResult == true) View.INVISIBLE else View.INVISIBLE
			setOnCheckedChangeListener { _, isChecked ->
				lifecycleScope.launch {
					if (isChecked && ivm.getLineOn()) ivm.switchedLineOn()
					mvm.currentResult.value?.let {
						val bitmap = getCombineBitmap(this@ZoomInDialogFragment, it, seq, ivm, true)
						bitmap?.let { bt -> setSSIV(bt, bd.ssivZID) }
					}
				}
			}
		}
	}

	private fun setSwitch() {
		bd.msZIDAngle.setOnCheckedChangeListener { _, isChecked ->
			// 리스너 동작
			setLinesInImage(ivm, mvm, isChecked) {
				lifecycleScope.launch {
					mvm.currentResult.value?.let {
						val bitmap = getCombineBitmap(this@ZoomInDialogFragment, it, seq, ivm, true)
						bitmap?.let { bt -> setSSIV(bt, bd.ssivZID) }
					}
				}
			}
			faceStaticAdapter.setAllChecked(isChecked)
			if (!isChecked) {
				ivm.currentCheckedLines.clear()
			}
		}
		bd.btnZIDClean.visibility = View.VISIBLE
		bd.btnZIDClean.text = if (ivm.getLineOn()) "🔍 가이드라인 끄기" else "🔍 가이드라인 켜기"
		bd.btnZIDClean.setOnSingleClickListener {
			ivm.switchedLineOn()
			bd.btnZIDClean.text = if (ivm.getLineOn()) "🔍 가이드라인 끄기" else "🔍 가이드라인 켜기"
			lifecycleScope.launch {
				mvm.currentResult.value?.let {
					val bitmap = getCombineBitmap(this@ZoomInDialogFragment, it, seq, ivm, true)
					bitmap?.let { bt -> setSSIV(bt, bd.ssivZID) }
				}
			}
		}
	}

	private fun setAdapter() {
//        ivm.setSeqIndex(0)
		val faceStaticJson = mvm.currentResult.value?.results
//        Log.v("스태틱봐보자", "${faceStaticJson?.length()}, ${mvm.currentResult.value?.mediaUri?.getOrNull(9)} 9번index: ${faceStaticJson?.optJSONObject(9)}")
		val result = buildFaceStaticItemList(
			ivm,
			false,
			faceStaticJson,
			faceStaticJson,
			mvm.currentResult.value?.seqs,
			null
		)?.toMutableList()
		if (result != null) {
			ivm.currentFaceStaticsItem = result
		}

		val isComparison = mvm.comparisonDoubleItem != null
		val basicInfo = if (isComparison) {
			Pair(mvm.comparisonDoubleItem?.first?.regDate!!, mvm.comparisonDoubleItem?.second?.regDate!!)
		} else null

		val lm = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
		faceStaticAdapter = FaceDataRVAdapter(this@ZoomInDialogFragment, ivm, basicInfo)
		faceStaticAdapter.faceStaticCheckListener = this@ZoomInDialogFragment
		bd.rvZID.apply {
			layoutManager = lm
			adapter = faceStaticAdapter
		}

	}

	override fun onFaceStaticCheck(drawLineIndex: Int, isChecked: Boolean) {
		val selectedLine = if (ivm.getBtnIndex() == 0) {
			when (drawLineIndex) {
				0 -> DrawLine.A_CANTHUS
				1 -> DrawLine.A_TIP_OF_LIPS
				2 -> DrawLine.A_CHIN
				3 -> DrawLine.A_CANTHUS_ORAL
				4 -> DrawLine.A_NASALWINGS_ORAL
				5 -> DrawLine.A_SIDE_HEAD
				6 -> DrawLine.E_CHEEKS_REST
				7 -> DrawLine.E_CHEEKBONE
				8 -> DrawLine.A_NECK
				9 -> DrawLine.A_FACE
				else -> DrawLine.A_CANTHUS
			}
		} else {
			when (drawLineIndex) {
				0 -> DrawLine.A_CANTHUS
				1 -> DrawLine.A_BOW
				2 -> DrawLine.A_CHIN
				else -> DrawLine.A_CANTHUS
			}
		}
		when (isChecked) {
			true -> {
				ivm.currentCheckedLines.add(selectedLine)
				// 체크하면 바로 사진 보이게끔 스크롤 되기.
				scrollToView(bd.ssivZID, bd.nsvZID)
			}

			false -> ivm.currentCheckedLines.remove(selectedLine)
		}
		lifecycleScope.launch {
			mvm.currentResult.value?.let {
				val bitmap = getCombineBitmap(this@ZoomInDialogFragment, it, seq, ivm, true)
				bitmap?.let { bt -> setSSIV(bt, bd.ssivZID) }
			}
		}

	}
}