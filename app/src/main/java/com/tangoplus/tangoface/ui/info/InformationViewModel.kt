package com.tangoplus.tangoface.ui.info

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tangoplus.tangoface.domain.util.FaceStaticUtility.getBtnMatchedSeq
import com.tangoplus.tangoface.domain.util.FaceStaticUtility.getIdxFromSeq
import com.tangoplus.tangoface.domain.vo.FaceStaticItem
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.ui.info.vo.DrawLine
import com.tangoplus.tangoface.ui.info.vo.DrawRatioLine

class InformationViewModel : ViewModel() {
	val currentCheckedLines = mutableSetOf<DrawLine>()
	val currentCheckedRatioLines = mutableSetOf<DrawRatioLine>()

	// 체크가 가능한 전체 데이터 VO 항목 담는 곳
	var currentFaceStaticsItem = mutableListOf<FaceStaticItem>()

	// info의 우측 사진 하단의  정면,교합/턱이동/목폄,개구
	private var currentBtnIndex = 1
	fun setBtnIndex(indexx: Int) {
		currentBtnIndex = indexx
	}

	fun getBtnIndex(): Int {
		return currentBtnIndex
	}

	// detail에서만쓰는 마지막 버튼 index 이게 없으면 zoomin 나왔을 떄 radiobutton누르면 좌우측 seq로 고정됨
	private var previousBtnIndex = 1
	fun setPreviousBtnIndex(indexx: Int) {
		previousBtnIndex = indexx
	}

	fun getPreviousBtnIndex(): Int {
		return previousBtnIndex
	}


	// adapter에 들어갈 좌표 변환해서 담기 (adapter연결할 때, overlay에서 가져다 씀)
	var coordinates1: List<List<Pair<Float, Float>>>? = null
	var coordinates2: List<List<Pair<Float, Float>>>? = null

	var leftTrajectory: List<Pair<Float, Float>>? = null
	var rightTrajectory: List<Pair<Float, Float>>? = null


	private val ratioLines = listOf(
		DrawRatioLine.A_ALL,
		DrawRatioLine.A_VERTI,
		DrawRatioLine.A_HORIZON,
		DrawRatioLine.A_NONE
	)
	private var currentRatioLine = DrawRatioLine.A_NONE
	fun setRatioState() {
		val currentIndex = ratioLines.indexOf(currentRatioLine)
		val nextIndex = (currentIndex + 1) % ratioLines.size
		currentRatioLine = ratioLines[nextIndex]
	}

	fun setAllOrNone() {
		when (currentRatioLine) {
			DrawRatioLine.A_NONE -> currentRatioLine = DrawRatioLine.A_ALL
			else -> currentRatioLine = DrawRatioLine.A_NONE
		}
	}

	fun getRatioState(): DrawRatioLine {
		return currentRatioLine
	}

	private var isLineOn = true
	fun switchedLineOn() {
		isLineOn = !isLineOn
	}

	fun getLineOn(): Boolean = isLineOn

	// TODO seq 클릭시
	val unitIdxFromFaceResult = MutableLiveData(Pair(0, 0))
	val unitIdxFromCompare = MutableLiveData(Pair(0, 0))

	fun releaseUnitIdx() {
		unitIdxFromFaceResult.value = Pair(0, 0)
		unitIdxFromCompare.value = Pair(0, 0)
	}

	fun setComparePreProcessingSeqIdx(seqs0: Set<Sequence>?, seqs1: Set<Sequence>?) {
		val (leftSeq, rightSeq) = getBtnMatchedSeq(getBtnIndex(), true)
		val result = getIdxFromSeq(false, seqs0, seqs1, leftSeq, rightSeq)
		unitIdxFromCompare.value = result
	}

	fun setDetailPreProcessingSeqIdx(seqs: Set<Sequence>?) {
		val (leftSeq, rightSeq) = getBtnMatchedSeq(getBtnIndex(), false)
		val result = getIdxFromSeq(false, seqs, null, leftSeq, rightSeq)
		unitIdxFromFaceResult.value = result
	}
}