package com.tangoplus.facebeautyexpert.ui.info

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Typeface
import android.icu.text.DecimalFormat
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.skydoves.balloon.ArrowPositionRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.showAlignTop
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkResult.Companion.fromFaceCoordinates
import com.tangoplus.facebeautyexpert.data.db.FaceDao
import com.tangoplus.facebeautyexpert.data.db.FaceDatabase
import com.tangoplus.facebeautyexpert.databinding.FragmentInformationBinding
import com.tangoplus.facebeautyexpert.domain.util.BitmapUtility.calculateFaceScale
import com.tangoplus.facebeautyexpert.ui.adapter.FaceDataRVAdapter
import com.tangoplus.facebeautyexpert.ui.listener.OnFaceStaticCheckListener
import com.tangoplus.facebeautyexpert.ui.view.OverlayView
import com.tangoplus.facebeautyexpert.domain.util.BitmapUtility.scaleAndCropBitmap
import com.tangoplus.facebeautyexpert.domain.util.BitmapUtility.getCombineBitmap
import com.tangoplus.facebeautyexpert.domain.util.FaceStaticUtility.addCommentInFCI
import com.tangoplus.facebeautyexpert.domain.util.FaceStaticUtility.buildFaceStaticItemList
import com.tangoplus.facebeautyexpert.domain.util.FaceStaticUtility.setLinesInImage
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.domain.util.MediaUtility.extractVideoCoordinates
import com.tangoplus.facebeautyexpert.domain.vo.Sequence
import com.tangoplus.facebeautyexpert.ui.info.vo.DateDisplay
import com.tangoplus.facebeautyexpert.ui.info.vo.DrawLine
import com.tangoplus.facebeautyexpert.ui.info.vo.DrawRatioLine
import com.tangoplus.facebeautyexpert.ui.main.vm.MainViewModel
import com.tangoplus.facebeautyexpert.ui.user.UserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tangoplus.facebeautyexpert.domain.util.BitmapUtility.setSSIV
import com.tangoplus.facebeautyexpert.domain.util.FaceStaticUtility.getBtnMatchedSeq
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.extractFaceCoordinates
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope


class InformationFragment : Fragment(), OnFaceStaticCheckListener {
	private lateinit var bd: FragmentInformationBinding

	//    private val mvm : MeasureViewModel by activityViewModels()
	private val ivm: InformationViewModel by activityViewModels()
	private val mvm: MainViewModel by activityViewModels()
	private var simpleExoPlayer1: SimpleExoPlayer? = null
	private var simpleExoPlayer2: SimpleExoPlayer? = null
	private val onPlayer1Ready = MutableLiveData(false)
	private val onPlayer2Ready = MutableLiveData(false)
	private var leftPlayBackPosition = 0L
	private var rightPlayBackPosition = 0L
	private var exoPlay1: ImageButton? = null
	private var exoPause1: ImageButton? = null
	private var exoPlay2: ImageButton? = null
	private var exoPause2: ImageButton? = null
	private lateinit var fDao: FaceDao
	private lateinit var faceStaticAdapter: FaceDataRVAdapter

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

		bd.ssiv1.recycle()
		bd.ssiv2.recycle()
		mvm.comparisonDoubleItem = null
		ivm.currentCheckedLines.clear()
		ivm.setBtnIndex(1)
		ivm.releaseUnitIdx()
		if (ivm.getRatioState() != DrawRatioLine.A_NONE) ivm.setAllOrNone()
		if (!ivm.getLineOn()) ivm.switchedLineOn()
	}

	// TODO 여기서 화면당 사진 1장. 그리고 간소화 등
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val fd = FaceDatabase.getDatabase(requireContext())
		fDao = fd.faceDao()
		setDialogDismissObserve()
		// double확인해서 비교인지 상세보기인지 판단
		if (mvm.comparisonDoubleItem != null) {
			setComparisonButtons()
			setComparisonUI()
			setCompareBtnEnable()
		} else {
			setDetailUI()
			setDetailButtons()
			setDetailBtnEnable()
			setUserInfo()
		}
		setResult()
		setRatioBtn()
		ivm.setPreviousBtnIndex(1)
	}

	// zoomin dialog 감지
	private fun setDialogDismissObserve() {
		childFragmentManager.setFragmentResultListener(
			"zoomInClosed",
			viewLifecycleOwner
		) { key, bundle ->
			val result = bundle.getBoolean("zoomIn")
			Log.v("줌인에서나오기", "$result ${ivm.getBtnIndex()} ${ivm.getPreviousBtnIndex()}")
			if (result) {
				faceStaticAdapter.setAllChecked(false)
				setResult()
			}
		}
	}

	private fun setRatioBtn() {
		bd.btnIDRatio.setOnClickListener {
			ivm.setRatioState()
			setImage()
		}
		bd.btnIDRatio.setOnLongClickListener {
			ivm.setAllOrNone()
			setImage()
			true
		}

		bd.btnIDClean.setOnSingleClickListener {
			ivm.switchedLineOn()
			bd.btnIDClean.text = if (ivm.getLineOn()) "🔍 가이드라인 끄기" else "🔍 가이드라인 켜기"
			setImage()
		}
	}

	private fun setRatioCheckSwitch() {
		val df = DecimalFormat("#.#")

		bd.tvGDVerti.visibility = View.VISIBLE
		bd.llGDRatioTitle.visibility = View.VISIBLE
		if (mvm.currentResult.value?.seqs?.contains(Sequence.RESTING) == true) {

			val unitIdxResult = ivm.unitIdxFromFaceResult.value
			val jsonData = mvm.currentResult.value?.results?.getJSONObject(unitIdxResult?.first ?: 0)
			val coordinates = extractFaceCoordinates(jsonData)
//			if (coordinates != null) {
//				val vertiIndices = listOf(234, 130, 133, 362, 359, 356)
//				val vertiCoordinates = vertiIndices.map { coordinates[it] }
////            Log.v("vertiCoordinates", "$vertiCoordinates")
//				val extractHairPointResult = extractHairLineCoordinate(jsonData)
//
//				// -----------# 헤어라인 저장값이 없으면 바로 꺼내서 계산 #----------------
//				val hairLineCoordinate = if (extractHairPointResult == null) {
//					val currentUri = mvm.currentResult.value?.mediaUri?.get(unitIdxResult?.first ?: 0)
//					val originalBitmap = BitmapFactory.decodeStream(
//						currentUri?.let { requireContext().contentResolver.openInputStream(it) }
//					)
//					val faceLandmarkResult = extractFaceLandmarkResult(jsonData)
//					val hairLineResult = findHairline(originalBitmap, faceLandmarkResult)
//					if (hairLineResult != null) {
//						Pair(hairLineResult.hairlinePoints.first, hairLineResult.hairlinePoints.y)
//					} else {
//						Pair(0f, 0f)
//					}
//
//				} else extractHairPointResult
//				val horizonCoordinates = listOf(
//					hairLineCoordinate,
//					coordinates[8],
//					coordinates[2],
//					coordinates[152]
//				)
//
//        Log.v("horizonCoordinates", "$horizonCoordinates $hairLineCoordinate")
//				val vertiText = calculateRatios(vertiCoordinates, true)
//				val horizonText = calculateRatios(horizonCoordinates, false)
//
//				bd.tvGDVerti.text = "${vertiText.map { df.format(it) }}"
//					.replace("[", "")
//					.replace("]", "")
//					.replace(", ", " : ")
//				bd.tvGDHorizon.text = "${horizonText.map { df.format(it) }}"
//					.replace("[", "")
//					.replace("]", "")
//					.replace(", ", " : ")
//			}
		}


		bd.tvGDVerti.setOnSingleClickListener {
			val balloon = Balloon.Builder(requireContext())
				.setWidth(BalloonSizeSpec.WRAP)
				.setHeight(BalloonSizeSpec.WRAP)
				.setText("모든 비율이 1:1:1:1:1로 일정 할 수록 좋은 비율입니다.")
				.setTextColorResource(R.color.black)
				.setTextSize(20f)
				.setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
				.setArrowSize(0)
				.setMargin(6)
				.setPadding(12)
				.setCornerRadius(8f)
				.setBackgroundColorResource(R.color.white)
				.setBalloonAnimation(BalloonAnimation.OVERSHOOT)
				.setLifecycleOwner(viewLifecycleOwner)
				.setOnBalloonDismissListener { }
				.build()
			bd.tvGDVerti.showAlignTop(balloon)
			balloon.dismissWithDelay(3000L)
			balloon.setOnBalloonClickListener { balloon.dismiss() }
		}
		bd.tvGDHorizon.setOnSingleClickListener {
			val balloon = Balloon.Builder(requireContext())
				.setWidth(BalloonSizeSpec.WRAP)
				.setHeight(BalloonSizeSpec.WRAP)
				.setText("앞머리 라인-코끝-턱끝 1:1:1에 가까울수록 좋은 비율입니다.")
				.setTextColorResource(R.color.black)
				.setTextSize(20f)
				.setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
				.setArrowSize(0)
				.setMargin(6)
				.setPadding(12)
				.setCornerRadius(8f)
				.setBackgroundColorResource(R.color.white)
				.setBalloonAnimation(BalloonAnimation.OVERSHOOT)
				.setLifecycleOwner(viewLifecycleOwner)
				.setOnBalloonDismissListener { }
				.build()
			bd.tvGDHorizon.showAlignTop(balloon)
			balloon.dismissWithDelay(3000L)
			balloon.setOnBalloonClickListener { balloon.dismiss() }
		}
	}

	private fun releaseRatioUI() {
		bd.tvGDRatioTitle.visibility = View.INVISIBLE
		bd.llGDRatioTitle.visibility = View.INVISIBLE
	}

	private fun setResult() {
		bd.ssiv1.recycle()
		bd.ssiv2.recycle()

		val isComparison = mvm.comparisonDoubleItem != null
		val btnIndex = ivm.getBtnIndex()

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
			ivm.coordinates1 = if (btnIndex == 3 || btnIndex == 4) {
				mvm.comparisonDoubleItem?.first?.results?.optJSONObject(unitIdxCompare?.first ?: 0)
					?.let { extractVideoCoordinates(it) }
			} else {
				null
			}

			ivm.coordinates2 = if (btnIndex == 3 || btnIndex == 4) {
				mvm.comparisonDoubleItem?.second?.results?.optJSONObject(unitIdxCompare?.second ?: 0)
					?.let { extractVideoCoordinates(it) }
			} else {
				null
			}

		} else {
			val faceStaticJson = mvm.currentResult.value?.results
			Log.v("setResult", "faceStaticJson: ${faceStaticJson?.length()}")
			when (btnIndex) {
				2 -> {
					val unitIdxResult = ivm.unitIdxFromFaceResult.value
					ivm.coordinates1 = faceStaticJson?.optJSONObject(unitIdxResult?.first ?: 0)?.let { extractVideoCoordinates(it) }
					ivm.coordinates2 = faceStaticJson?.optJSONObject(unitIdxResult?.second ?: 0)?.let { extractVideoCoordinates(it) }
				}

				else -> {
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
			}
		}
//		Log.v("staticDatas", "${ivm.currentFaceStaticsItem}")
		val basicInfo = if (isComparison) {
			Pair(mvm.comparisonDoubleItem?.first?.regDate!!, mvm.comparisonDoubleItem?.second?.regDate!!)
		} else null


		ivm.currentFaceStaticsItem.forEach { addCommentInFCI(it, isComparison) }
		faceStaticAdapter = FaceDataRVAdapter(this@InformationFragment, ivm, basicInfo)
		bd.msID.setOnCheckedChangeListener { _, isChecked ->
			// 리스너 동작
			setLinesInImage(ivm, mvm, isChecked) {
				val previousIndex = ivm.getPreviousBtnIndex()
				ivm.setBtnIndex(previousIndex)
				setImage()
			}
			faceStaticAdapter.setAllChecked(isChecked)
			if (!isChecked) {
				ivm.currentCheckedLines.clear()
			}
		}

		if (isComparison) {
			releaseRatioUI()
			when (btnIndex) {
				3, 4 -> {
					// 어차피 null을 null하면 null이 나옴
					ivm.leftTrajectory = ivm.coordinates1?.map { it[152] }
					ivm.rightTrajectory = ivm.coordinates2?.map { it[152] }
					setVideoUI()
					setPlayer1()
					setPlayer2()
					bd.llIDImageProcess.visibility = View.INVISIBLE
				}

				else -> {
					releasePlayers()
					setImageUI()
					setImage()
					showZoomInDialogFragment()

					bd.llIDImageProcess.visibility =
						if (btnIndex in listOf(7, 8)) View.INVISIBLE else View.VISIBLE
				}
			}
		} else {
			if (btnIndex == 2) {
				ivm.leftTrajectory = ivm.coordinates1?.map { it[152] }
				ivm.rightTrajectory = ivm.coordinates2?.map { it[152] }
				setVideoUI()
				setPlayer1()
				setPlayer2()
				bd.llIDImageProcess.visibility = View.INVISIBLE
			} else {
				releasePlayers()
				setImageUI()
				setImage()
				showZoomInDialogFragment()
				setRatioCheckSwitch()
				bd.llIDImageProcess.visibility = if (btnIndex == 4) View.INVISIBLE else View.VISIBLE

			}
		}

		val lm = if (!isComparison) {
			if (btnIndex == 2) {
				GridLayoutManager(context, 2)
			} else {
				LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
			}
		} else {
			if (btnIndex == 3 || btnIndex == 4) {
				GridLayoutManager(context, 2)
			} else {
				LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
			}
		}
		bd.rvGD2.apply {
			faceStaticAdapter.faceStaticCheckListener = this@InformationFragment
			layoutManager = lm
			adapter = faceStaticAdapter
		}
		faceStaticAdapter.notifyDataSetChanged()
	}

	private fun setDetailBtnEnable() {
		val seqs = mvm.currentResult.value?.seqs
		if (seqs != null) {
			bd.tvID0.isEnabled = seqs.contains(Sequence.COMFORT)
			bd.tvID1.isEnabled = seqs.contains(Sequence.RESTING) || seqs.contains(Sequence.OCCLUSAL)
			bd.tvID2.isEnabled =
				seqs.contains(Sequence.JAW_LEFT_TILT) || seqs.contains(Sequence.JAW_RIGHT_TILT)
			bd.tvID3.isEnabled =
				seqs.contains(Sequence.JAW_OPENING) || seqs.contains(Sequence.NECK_EXTENTION)
			bd.tvID4.isEnabled = seqs.contains(Sequence.SIDE_LEFT) || seqs.contains(Sequence.SIDE_RIGHT)
			bd.tvID5.isEnabled = seqs.contains(Sequence.BOW)
		}
	}

	private fun setCompareBtnEnable() {
		if (mvm.comparisonDoubleItem != null) {
			val seqs0 = mvm.comparisonDoubleItem!!.first.seqs
			val seqs1 = mvm.comparisonDoubleItem!!.second.seqs
			Log.v("컴패리슨버튼", "$seqs0 $seqs1")
			bd.tvID0.isEnabled = seqs0.contains(Sequence.COMFORT) || seqs1.contains(Sequence.COMFORT)
			bd.tvID1.isEnabled = seqs0.contains(Sequence.RESTING) || seqs1.contains(Sequence.RESTING)
			bd.tvID2.isEnabled = seqs0.contains(Sequence.OCCLUSAL) || seqs1.contains(Sequence.OCCLUSAL)
			bd.tvID3.isEnabled =
				seqs0.contains(Sequence.JAW_LEFT_TILT) && seqs1.contains(Sequence.JAW_LEFT_TILT)
			bd.tvID4.isEnabled =
				seqs0.contains(Sequence.JAW_RIGHT_TILT) && seqs1.contains(Sequence.JAW_RIGHT_TILT)
			bd.tvID5.isEnabled =
				seqs0.contains(Sequence.JAW_OPENING) || seqs1.contains(Sequence.JAW_OPENING)
			bd.tvID6.isEnabled =
				seqs0.contains(Sequence.NECK_EXTENTION) || seqs1.contains(Sequence.NECK_EXTENTION)
			bd.tvID7.isEnabled = seqs0.contains(Sequence.SIDE_LEFT) || seqs1.contains(Sequence.SIDE_LEFT)
			bd.tvID8.isEnabled =
				seqs0.contains(Sequence.SIDE_RIGHT) || seqs1.contains(Sequence.SIDE_RIGHT)
			bd.tvID9.isEnabled = seqs0.contains(Sequence.BOW) || seqs1.contains(Sequence.BOW)

		}
	}

	@SuppressLint("UseKtx")
	private fun setImage() {
		val seqIndex = ivm.getBtnIndex()
		val isComparisonMode = mvm.comparisonDoubleItem != null
		val (leftSeq, rightSeq) = getBtnMatchedSeq(seqIndex, isComparisonMode)
		lifecycleScope.launch {
			if (isComparisonMode) {
				mvm.comparisonDoubleItem?.let { (left, right) ->
					// ✅ 병렬로 비트맵 로드
					val (leftFinalBitmap, rightFinalBitmap) = coroutineScope {
						val leftDeferred =
							async { getCombineBitmap(this@InformationFragment, left, leftSeq, ivm) }
						val rightDeferred =
							async { getCombineBitmap(this@InformationFragment, right, rightSeq, ivm) }
						leftDeferred.await() to rightDeferred.await()
					}
					Log.d("ImageDebug", "Left bitmap: ${leftFinalBitmap != null}, Right bitmap: ${rightFinalBitmap != null}")
					val seqs0 = mvm.comparisonDoubleItem?.first?.seqs
					val seqs1 = mvm.comparisonDoubleItem?.second?.seqs
					val leftSeqIndex = seqs0?.indexOf(leftSeq) ?: -1
					val rightSeqIndex = seqs1?.indexOf(rightSeq) ?: -1
					val leftCoords = if (leftSeqIndex != -1) {
						extractFaceCoordinates(left.results.getJSONObject(leftSeqIndex))
					} else null

					val rightCoords = if (rightSeqIndex != -1) {
						extractFaceCoordinates(right.results.getJSONObject(rightSeqIndex))
					} else null

					withContext(Dispatchers.Main) {
						if (leftCoords != null && rightCoords != null && seqIndex !in listOf(7, 8)) {
							val (isLeftBigger, scale) = calculateFaceScale(leftCoords, rightCoords)

							if (isLeftBigger) {
								// 왼쪽은 그대로 표시
								leftFinalBitmap?.let {
									setSSIV(it, bd.ssiv1)
								}

								// 오른쪽만 스케일링
								rightFinalBitmap?.let { original ->
									val safeBitmap = original.copy(original.config ?: Bitmap.Config.ARGB_8888, true)
									val scaledBitmap = scaleAndCropBitmap(safeBitmap, scale * 0.9f, original.width, original.height)
									setSSIV(scaledBitmap, bd.ssiv2)
								}

							} else {
								// 왼쪽만 스케일링
								leftFinalBitmap?.let { original ->
									val safeBitmap = original.copy(original.config ?: Bitmap.Config.ARGB_8888, true)
									val scaledBitmap = scaleAndCropBitmap(safeBitmap, scale * 0.9f, original.width, original.height)
									setSSIV(scaledBitmap, bd.ssiv1)
								}

								// 오른쪽은 그대로 표시
								rightFinalBitmap?.let {
									setSSIV(it, bd.ssiv2)
								}
							}
						} else {
							// 스케일링 불필요한 경우 둘 다 그대로 표시
							leftFinalBitmap?.let { setSSIV(it, bd.ssiv1) }
							rightFinalBitmap?.let { setSSIV(it, bd.ssiv2) }
						}
					}
				}
			} else {
				// 기존의 단일 모드 로직
				mvm.currentResult.value?.let { result ->
					coroutineScope {

						val leftDeferred = async { getCombineBitmap(this@InformationFragment, result, leftSeq, ivm) }
						val rightDeferred = async { getCombineBitmap(this@InformationFragment, result, rightSeq, ivm) }
						leftDeferred.await()?.let { setSSIV(it, bd.ssiv1) }
						rightDeferred.await()?.let { setSSIV(it, bd.ssiv2) }
					}
				}

			}
		}
	}

	override fun onFaceStaticCheck(drawLineIndex: Int, isChecked: Boolean) {
		Log.v("어댑터클릭", "${ivm.getBtnIndex()}, drawLineIndex: $drawLineIndex")
		val selectedLine = if (mvm.comparisonDoubleItem == null) {
			when (ivm.getBtnIndex()) {
				1 -> {
					when (drawLineIndex) {
						0 -> DrawLine.A_CANTHUS
						1 -> DrawLine.A_TIP_OF_LIPS
						2 -> DrawLine.A_CHIN
						3 -> DrawLine.A_CANTHUS_ORAL
						4 -> DrawLine.A_NASALWINGS_ORAL
						5 -> DrawLine.A_SIDE_HEAD
						6 -> DrawLine.E_CHEEKS_REST
						7 -> DrawLine.E_CHEEKBONE
						else -> DrawLine.A_CANTHUS
					}
				}

				3 -> {
					when (drawLineIndex) {
						0 -> DrawLine.A_BELOW_LIPS
						1 -> DrawLine.A_BELOW_LIPS
						2 -> DrawLine.E_CHEEKS_OPENING
						3 -> DrawLine.A_SHOULDER
						4 -> DrawLine.A_EAR
						5 -> DrawLine.A_NECK
						6 -> DrawLine.A_FACE
						else -> DrawLine.A_MANDIBULAR_CHIN
					}
				}

				4 -> {
					when (drawLineIndex) {
						0 -> DrawLine.A_SHOULDER_EAR
						else -> DrawLine.A_SHOULDER_NOSE
					}
				}

				else -> DrawLine.A_GLABELLA_NOSE
			}
		} else {
			when (ivm.getBtnIndex()) {
				0 -> {
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
				}

				1, 2 -> {
					when (drawLineIndex) {
						0 -> DrawLine.A_CANTHUS
						1 -> DrawLine.A_TIP_OF_LIPS
						2 -> DrawLine.A_CHIN
						3 -> DrawLine.A_CANTHUS_ORAL
						4 -> DrawLine.A_NASALWINGS_ORAL
						5 -> DrawLine.A_SIDE_HEAD
						6 -> DrawLine.E_CHEEKS_REST
						7 -> DrawLine.E_CHEEKBONE
						else -> DrawLine.A_CANTHUS
					}
				}

				3, 4 -> DrawLine.A_NOSE_JAW // 0 ~ 3 item 전부 동시에 체크
				5 -> {
					when (drawLineIndex) {
						0, 1 -> DrawLine.A_BELOW_LIPS
						else -> DrawLine.E_CHEEKS_OPENING
					}
				}

				6 -> {
					when (drawLineIndex) {
						0 -> DrawLine.A_SHOULDER
						1 -> DrawLine.A_EAR
						2 -> DrawLine.A_NECK
						3 -> DrawLine.A_FACE
						else -> DrawLine.A_MANDIBULAR_CHIN
					}
				}

				9 -> {
					when (drawLineIndex) {
						0 -> DrawLine.A_CANTHUS
						1 -> DrawLine.A_BOW
						2 -> DrawLine.A_CHIN
						else -> DrawLine.A_MANDIBULAR_CHIN
					}
				}

				else -> DrawLine.A_GLABELLA_NOSE
			}
		}
		when (isChecked) {
			true -> ivm.currentCheckedLines.add(selectedLine)
			false -> ivm.currentCheckedLines.remove(selectedLine)
		}
		Log.v("체크", "$drawLineIndex, $isChecked / $selectedLine ${ivm.currentCheckedLines}")
		setImage()
	}

	private fun showZoomInDialogFragment() { // 0 , 1, 2인데
		val index = ivm.getBtnIndex()
		val isComparison = mvm.comparisonDoubleItem != null

		val (leftSeq, rightSeq) = getBtnMatchedSeq(index, isComparison)
		ivm.currentCheckedLines.clear()
		bd.ssiv1.setOnLongClickListener {
			Log.v("seq와left", "$isComparison $leftSeq, $rightSeq")
			val zoomInDialog = if (!isComparison) {
				ZoomInDialogFragment.newInstance(leftSeq, null)
			} else {
				ZoomInDialogFragment.newInstance(leftSeq, true)
			}
			activity?.let {
				if (!zoomInDialog.isAdded) {
					zoomInDialog.show(childFragmentManager, "")
				}
			}
			true
		}
		bd.ssiv2.setOnLongClickListener {
			Log.v("seq와right", "$isComparison")

			val zoomInDialog = if (!isComparison) {
				ZoomInDialogFragment.newInstance(rightSeq, null)
			} else {
				ZoomInDialogFragment.newInstance(rightSeq, false)
			}
			activity?.let {
				if (!zoomInDialog.isAdded) {
					zoomInDialog.show(childFragmentManager, "")
				}
			}
			true
		}
	}

	private fun setComparisonButtons() {
		bd.llID1.visibility = View.VISIBLE
		bd.llID2.visibility = View.VISIBLE
		bd.divIDHorizon1.visibility = View.VISIBLE
		bd.divIDHorizon2.visibility = View.VISIBLE

		val leftContainedBow = mvm.comparisonDoubleItem?.first?.seqs?.contains(Sequence.BOW) == true
		val rightContainedBow = mvm.comparisonDoubleItem?.second?.seqs?.contains(Sequence.BOW) == true
		val showBow = leftContainedBow || rightContainedBow
		bd.divIDHorizon3.visibility = if (showBow) View.VISIBLE else View.GONE
		bd.tvID9.visibility = if (showBow) View.VISIBLE else View.GONE


		val btns = listOf(
			bd.tvID0,
			bd.tvID1,
			bd.tvID2,
			bd.tvID3,
			bd.tvID4,
			bd.tvID5,
			bd.tvID6,
			bd.tvID7,
			bd.tvID8,
			bd.tvID9
		)

		// 초기 색상 설정
		btns.forEachIndexed { index, tv ->
			tv.setTextColor(ContextCompat.getColor(requireContext(), getCompareButtonColor(index)))
			tv.typeface = if (index == ivm.getBtnIndex() &&
				mvm.comparisonDoubleItem?.first?.seqs?.contains(Sequence.RESTING) == true &&
				mvm.comparisonDoubleItem?.second?.seqs?.contains(Sequence.RESTING) == true
			) {
				Typeface.defaultFromStyle(Typeface.BOLD)
			} else {
				Typeface.DEFAULT
			}

			tv.setOnSingleClickListener {
				if (index == ivm.getBtnIndex()) return@setOnSingleClickListener
				ivm.setPreviousBtnIndex(index)
				ivm.setBtnIndex(index)
				val compares= mvm.comparisonDoubleItem
				ivm.setComparePreProcessingSeqIdx(compares?.first?.seqs, compares?.second?.seqs)
				ivm.currentCheckedLines.clear()
				if (bd.msID.isChecked) bd.msID.isChecked = false

				// 모든 버튼 텍스트 색상 갱신
				btns.forEachIndexed { i, t ->
					t.setTextColor(ContextCompat.getColor(requireContext(), getCompareButtonColor(i)))
					t.typeface = if (i == ivm.getBtnIndex()) {
						Typeface.defaultFromStyle(Typeface.BOLD)
					} else {
						Typeface.DEFAULT
					}
				}
				setImage()
				setResult()
			}
		}
	}

	private fun getCompareButtonColor(index: Int): Int {
		// 현재 선택된 버튼은 검은색
		if (index == ivm.getBtnIndex()) {
			return R.color.black
		}
		// comparisonDoubleItem이 없으면 기본 색상
		val comparisonItem = mvm.comparisonDoubleItem ?: return R.color.subColor500

		val seqs0 = comparisonItem.first.seqs
		val seqs1 = comparisonItem.second.seqs

		// 각 버튼별로 해당하는 Sequence가 있는지 확인
		val hasSequence = when (index) {
			0 -> seqs0.contains(Sequence.COMFORT) || seqs1.contains(Sequence.COMFORT)
			1 -> seqs0.contains(Sequence.RESTING) || seqs1.contains(Sequence.RESTING)
			2 -> seqs0.contains(Sequence.OCCLUSAL) || seqs1.contains(Sequence.OCCLUSAL)
			3 -> seqs0.contains(Sequence.JAW_LEFT_TILT) && seqs1.contains(Sequence.JAW_LEFT_TILT)
			4 -> seqs0.contains(Sequence.JAW_RIGHT_TILT) && seqs1.contains(Sequence.JAW_RIGHT_TILT)
			5 -> seqs0.contains(Sequence.JAW_OPENING) || seqs1.contains(Sequence.JAW_OPENING)
			6 -> seqs0.contains(Sequence.NECK_EXTENTION) || seqs1.contains(Sequence.NECK_EXTENTION)
			7 -> seqs0.contains(Sequence.SIDE_LEFT) || seqs1.contains(Sequence.SIDE_LEFT)
			8 -> seqs0.contains(Sequence.SIDE_RIGHT) || seqs1.contains(Sequence.SIDE_RIGHT)
			9 -> seqs0.contains(Sequence.BOW) || seqs1.contains(Sequence.BOW)
			else -> true
		}
		return if (hasSequence) R.color.subColor500 else R.color.subColor100
	}

	private fun setComparisonUI() {
		val isGuestComparison = uvm.getUserItem().userSn == -1
		if (isGuestComparison) return

		mvm.dateDisplayList = mvm.createDateDisplayListByUser(uvm.getUserItem())
		bd.tvIdInfo.visibility = View.GONE
		setLeftActv()
		setRightActv()
		setBothObserver()
	}

	private fun setDetailUI() {
		bd.actvID1.visibility = View.GONE
		bd.actvID2.visibility = View.GONE
	}

	private fun setDetailButtons() {
		bd.llID2.visibility = View.GONE
		bd.divIDHorizon2.visibility = View.GONE
		bd.tvID0.text = "편한 상태"
		bd.tvID1.text = "정면, 교합"
		bd.tvID2.text = "턱 가측 이동"
		bd.tvID3.text = "입 벌림, 목폄"
		bd.tvID4.text = "좌우 측면"
		bd.tvID5.text = "페이스 보우"
		bd.tvID6.visibility = View.GONE
		bd.tvID7.visibility = View.GONE
		bd.tvID8.visibility = View.GONE

		bd.divIDHorizon3.visibility = View.GONE
		bd.tvID9.visibility = View.GONE
		bd.tvID5.visibility = if (mvm.currentResult.value?.seqs?.contains(Sequence.BOW) == true ) View.VISIBLE else View.INVISIBLE
		val btns = listOf(bd.tvID0, bd.tvID1, bd.tvID2, bd.tvID3, bd.tvID4, bd.tvID5)
		val seqs = mvm.currentResult.value?.seqs

		btns.forEachIndexed { indexx, tv ->
			// 초기 색상 설정
			tv.setTextColor(
				ContextCompat.getColor(
					requireContext(),
					if (indexx == ivm.getBtnIndex() &&
						(
							mvm.currentResult.value?.seqs?.contains(Sequence.RESTING) == true ||
							mvm.currentResult.value?.seqs?.contains(Sequence.OCCLUSAL) == true
							)
						) {
						R.color.black
					} else {

						if (seqs != null) {
							when (indexx) {
								0 -> if (!seqs.contains(Sequence.COMFORT)) R.color.subColor100 else R.color.subColor500
								1 -> if (!seqs.contains(Sequence.RESTING) && !seqs.contains(Sequence.OCCLUSAL)) R.color.subColor100 else R.color.subColor500
								2 -> if (!seqs.contains(Sequence.JAW_LEFT_TILT) && !seqs.contains(Sequence.JAW_RIGHT_TILT)) R.color.subColor100 else R.color.subColor500
								3 -> if (!seqs.contains(Sequence.JAW_OPENING) && !seqs.contains(Sequence.NECK_EXTENTION)) R.color.subColor100 else R.color.subColor500
								4 -> if (!seqs.contains(Sequence.SIDE_LEFT) && !seqs.contains(Sequence.SIDE_RIGHT)) R.color.subColor100 else R.color.subColor500
								else -> R.color.subColor500
							}
						} else {
							R.color.subColor500
						}
					}
				)
			)
			tv.setOnSingleClickListener {
				if (indexx == ivm.getBtnIndex()) return@setOnSingleClickListener
				ivm.setBtnIndex(indexx)
				ivm.setDetailPreProcessingSeqIdx(mvm.currentResult.value?.seqs)
				// 편한상태는 전체화면으로 보기 경로 처리
				when (indexx) {
					0, 5 -> {
						val zoomInSeq = if (indexx == 5) Sequence.BOW else Sequence.COMFORT
						val zoomInDialog = ZoomInDialogFragment.newInstance(zoomInSeq, null)
						activity?.let {
							if (!zoomInDialog.isAdded) {
								zoomInDialog.show(childFragmentManager, "")
							}
						}
					}

					else -> {
						ivm.setPreviousBtnIndex(indexx)
						// checkbox 해제
						ivm.currentCheckedLines.clear()
						if (bd.msID.isChecked) bd.msID.isChecked = false
						if (indexx == 4 && ivm.getRatioState() != DrawRatioLine.A_NONE) ivm.setAllOrNone()
						// 모든 버튼 텍스트 색상 갱신
						btns.forEachIndexed { i, t ->
							t.setTextColor(
								ContextCompat.getColor(
									requireContext(),
									if (i == indexx) {
										R.color.black
									} else {

										if (seqs != null) {
											when (i) {
												0 -> if (!seqs.contains(Sequence.COMFORT)) R.color.subColor100 else R.color.subColor500
												1 -> if (!seqs.contains(Sequence.RESTING) && !seqs.contains(Sequence.OCCLUSAL)) R.color.subColor100 else R.color.subColor500
												2 -> if (!seqs.contains(Sequence.JAW_LEFT_TILT) && !seqs.contains(Sequence.JAW_RIGHT_TILT)) R.color.subColor100 else R.color.subColor500
												3 -> if (!seqs.contains(Sequence.JAW_OPENING) && !seqs.contains(Sequence.NECK_EXTENTION)) R.color.subColor100 else R.color.subColor500
												4 -> if (!seqs.contains(Sequence.SIDE_LEFT) && !seqs.contains(Sequence.SIDE_RIGHT)) R.color.subColor100 else R.color.subColor500
												else -> R.color.subColor500
											}
										} else {
											R.color.subColor500
										}
									}
								)
							)
						}
//                setImage()
						setResult()
					}
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

	private fun setVideoUI() {
		bd.ssiv2.apply {
			recycle()
			visibility = View.GONE
		}
		bd.ssiv1.apply {
			recycle()
			visibility = View.GONE
		}
		bd.clIDVideo1.visibility = View.VISIBLE
		bd.clIDVideo2.visibility = View.VISIBLE

		exoPlay1 = bd.pvID1.findViewById(R.id.btnPlay)
		exoPause1 = bd.pvID1.findViewById(R.id.btnPause)
		exoPlay2 = bd.pvID2.findViewById(R.id.btnPlay)
		exoPause2 = bd.pvID2.findViewById(R.id.btnPause)

		exoPlay1?.visibility = View.GONE
		exoPause1?.visibility = View.VISIBLE
		exoPlay1?.setOnClickListener {
			if (wasEnded1) {
				simpleExoPlayer1?.seekTo(0) // 처음부터
				wasEnded1 = false
			}
			simpleExoPlayer1?.play()
			exoPlay1?.visibility = View.GONE
			exoPause1?.visibility = View.VISIBLE

		}
		exoPause1?.setOnClickListener {
			simpleExoPlayer1?.pause()
			exoPlay1?.visibility = View.VISIBLE
			exoPause1?.visibility = View.GONE
		}
		exoPlay2?.visibility = View.GONE
		exoPause2?.visibility = View.VISIBLE
		exoPlay2?.setOnClickListener {
			if (wasEnded2) {
				simpleExoPlayer2?.seekTo(0) // 처음부터
				wasEnded2 = false
			}
			simpleExoPlayer2?.play()
			exoPlay2?.visibility = View.GONE
			exoPause2?.visibility = View.VISIBLE

		}
		exoPause2?.setOnClickListener {
			simpleExoPlayer2?.pause()
			exoPlay2?.visibility = View.VISIBLE
			exoPause2?.visibility = View.GONE
		}

		bd.ovID1.clear()
		bd.ovID2.clear()
	}

	private fun setImageUI() {
		bd.btnIDRatio.visibility = View.VISIBLE

		bd.ssiv2.apply {
			visibility = View.VISIBLE
		}
		bd.ssiv1.apply {
			visibility = View.VISIBLE
		}
		bd.clIDVideo1.visibility = View.GONE
		bd.clIDVideo2.visibility = View.GONE
	}

	private fun setLeftActv() {
		bd.tilID1.visibility = View.VISIBLE
		val isGuest = mvm.comparisonDoubleItem?.first?.userName == "GUEST"

		val comparisonDates = listOfNotNull(
			mvm.comparisonDoubleItem?.first?.regDate,
		)
		val leftDateDisplays = mvm.dateDisplayList.filter { it.fullDateTime !in comparisonDates }
		val leftAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, leftDateDisplays)
		bd.actvID1.setAdapter(leftAdapter)

		bd.actvID1.visibility = View.VISIBLE
		// 최초 1회만
		mvm.leftDate.value =
			mvm.dateDisplayList.find { it.fullDateTime == mvm.comparisonDoubleItem?.first?.regDate }
		bd.actvID1.setText(mvm.leftDate.value?.displayDate, false)
		Log.v("왼쪽", "${mvm.leftDate.value?.displayDate}")

		bd.actvID1.setOnItemClickListener { _, _, position, _ ->
			val selectedDate = bd.actvID1.adapter.getItem(position) as DateDisplay
			mvm.leftDate.value = mvm.dateDisplayList.find { it.fullDateTime == selectedDate.fullDateTime }
			bd.actvID1.setText(mvm.leftDate.value?.displayDate, false)

			lifecycleScope.launch(Dispatchers.IO) {
				// user쪽임 (user에서는 가능)
				if (isGuest) {
					val leftGuest =
						mvm.currentGuestItems.find { it.regDate == mvm.leftDate.value?.fullDateTime }
					val leftStatics = leftGuest?.let { fDao.getStaticWithPlaceholders(it.tempServerSn) }
					withContext(Dispatchers.Main) {
						val leftResult =
							leftStatics?.let { it1 -> mvm.convertToFaceResult(requireContext(), it1) }
						if (leftResult != null && mvm.comparisonDoubleItem?.second != null) {
							val newComparisonItem = Pair(leftResult, mvm.comparisonDoubleItem?.second!!)
							mvm.comparisonDoubleItem = newComparisonItem
							Log.v(
								"왼쪽최신",
								"fulldateTime: ${mvm.comparisonDoubleItem?.first?.regDate}, leftDate: ${mvm.leftDate.value}, leftGuest: ${leftGuest.tempServerSn} leftStatics: $leftStatics"
							)
						}
						setComparisonButtons()
						setResult()
					}
				} else {
					// 데이터 가져오기
					val sortedTempItems = uvm.getUserItem()
					val leftStaticsIndex = sortedTempItems.regDate?.indexOf(selectedDate.fullDateTime)
					val leftStatics =
						leftStaticsIndex?.let { fDao.getStaticWithPlaceholders(sortedTempItems.tempServerSn[it]) }
//                    Log.v("왼쪽최신", "fulldateTime: ${mvm.comparisonDoubleItem?.first?.regDate}, leftDate: ${mvm.leftDate.value}, staticsIndex: $leftStaticsIndex, sortedTempItem: $sortedTempItems, leftStatics: $leftStatics")

					withContext(Dispatchers.Main) {
						val leftResult =
							leftStatics?.let { it1 -> mvm.convertToFaceResult(requireContext(), it1) }
						if (leftResult != null && mvm.comparisonDoubleItem?.second != null) {
							val newComparisonItem = Pair(leftResult, mvm.comparisonDoubleItem?.second!!)
							mvm.comparisonDoubleItem = newComparisonItem
						}


						setComparisonButtons()
						setResult()
					}
				}
			}
		}
	}

	private fun setRightActv() {
		bd.tilID2.visibility = View.VISIBLE
		val isGuest = mvm.comparisonDoubleItem?.second?.userName == "GUEST"

		val comparisonDates = listOfNotNull(
			mvm.comparisonDoubleItem?.second?.regDate
		)
		val rightDateDisplays = mvm.dateDisplayList.filter { it.fullDateTime !in comparisonDates }
		val rightAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, rightDateDisplays)
		bd.actvID2.setAdapter(rightAdapter)

		bd.actvID2.visibility = View.VISIBLE
		mvm.rightDate.value =
			mvm.dateDisplayList.find { it.fullDateTime == mvm.comparisonDoubleItem?.second?.regDate }
		Log.v("오른쪽", "${mvm.rightDate.value?.displayDate}")
		bd.actvID2.setText(mvm.rightDate.value?.displayDate, false)

		bd.actvID2.setOnItemClickListener { _, _, position, _ ->
			val selectedDate = bd.actvID2.adapter.getItem(position) as DateDisplay

			mvm.rightDate.value =
				mvm.dateDisplayList.find { it.fullDateTime == selectedDate.fullDateTime }
			bd.actvID2.setText(mvm.rightDate.value?.displayDate, false)

			lifecycleScope.launch(Dispatchers.IO) {
				if (isGuest) {
					val rightGuest =
						mvm.currentGuestItems.find { it.regDate == mvm.rightDate.value?.fullDateTime }
					val rightStatics = rightGuest?.let { fDao.getStaticWithPlaceholders(it.tempServerSn) }
					withContext(Dispatchers.Main) {
						val rightResult =
							rightStatics?.let { it1 -> mvm.convertToFaceResult(requireContext(), it1) }
						if (rightResult != null && mvm.comparisonDoubleItem?.first != null) {
							val newComparisonItem = Pair(mvm.comparisonDoubleItem?.first!!, rightResult)
							mvm.comparisonDoubleItem = newComparisonItem
							Log.v(
								"오른쪽최신",
								"fulldateTime: ${mvm.comparisonDoubleItem?.second?.regDate}, leftDate: ${mvm.rightDate.value}, leftGuest: ${rightGuest.tempServerSn} leftStatics: $rightStatics"
							)
						}
						setComparisonButtons()
						setResult()
					}
				} else {
					// 데이터 가져오기
					val sortedTempItems = uvm.getUserItem()
					val rightStaticsIndex = sortedTempItems.regDate?.indexOf(selectedDate.fullDateTime)
					val rightStatics =
						rightStaticsIndex?.let { fDao.getStaticWithPlaceholders(sortedTempItems.tempServerSn[it]) }
					withContext(Dispatchers.Main) {
						val rightResult =
							rightStatics?.let { it1 -> mvm.convertToFaceResult(requireContext(), it1) }
						if (rightResult != null && mvm.comparisonDoubleItem?.first != null) {
							val newComparisonItem = Pair(mvm.comparisonDoubleItem?.first!!, rightResult)
							mvm.comparisonDoubleItem = newComparisonItem
						}
						setComparisonButtons()
						setResult()
					}
				}
			}
		}
	}

	private fun setBothObserver() {
		val combinedObserver = Observer<Any?> {
			val leftSelected = mvm.leftDate.value
			val rightSelected = mvm.rightDate.value
			Log.v("ACTVDate", "${mvm.dateDisplayList}, $leftSelected, $rightSelected")
			if (leftSelected == null && rightSelected == null) return@Observer
			val comparisonDates = listOfNotNull(
				leftSelected,
				rightSelected
			)

			// 오른쪽은 왼쪽 선택값 제외, 왼쪽은 오른쪽 선택값 제외
			val filteredLeftDates = mvm.getFilteredDates(comparisonDates)
			val filteredRightDates = mvm.getFilteredDates(comparisonDates)

			// Left adapter 갱신
			bd.actvID1.setAdapter(
				ArrayAdapter(requireContext(), R.layout.dropdown_item, filteredLeftDates)
			)

			// Right adapter 갱신
			bd.actvID2.setAdapter(
				ArrayAdapter(requireContext(), R.layout.dropdown_item, filteredRightDates)
			)
		}
		mvm.leftDate.observe(viewLifecycleOwner, combinedObserver)
		mvm.rightDate.observe(viewLifecycleOwner, combinedObserver)
	}

	private var wasEnded1 = false
	private fun setPlayer1() {
		simpleExoPlayer1 = SimpleExoPlayer.Builder(requireContext()).build()
		bd.pvID1.player = simpleExoPlayer1
		bd.pvID1.controllerShowTimeoutMs = 1100
		val isComparison = mvm.comparisonDoubleItem != null

		val leftUri = when (isComparison) {
			false -> {
				val unitIdxResult = ivm.unitIdxFromFaceResult.value
				mvm.currentResult.value?.mediaUri?.getOrNull(unitIdxResult?.first ?: 0)
			}
			true -> {
				val unitIdxCompare = ivm.unitIdxFromCompare.value
				when (ivm.getBtnIndex()) {
					3 -> mvm.comparisonDoubleItem?.first?.mediaUri?.get(unitIdxCompare?.first ?: 0)
					else -> mvm.comparisonDoubleItem?.first?.mediaUri?.get(unitIdxCompare?.second ?: 0) // 비교라서 뭘 넣어도 상관없음
				}
			}
		}
		Log.v("leftURI", "결과: ${leftUri} / isComparison: $isComparison ${mvm.currentResult.value?.mediaUri}")
		val mediaItem = leftUri?.let { MediaItem.fromUri(it) }
		val mediaSource = mediaItem?.let {
			ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext()))
				.createMediaSource(it)
		}

		mediaSource.let {
			if (it != null) {
				simpleExoPlayer1?.prepare(it)
			}
			// 저장된 위치로 정확하게 이동
			simpleExoPlayer1?.seekTo(0)
			onPlayer1Ready.value = true
			simpleExoPlayer1?.playWhenReady = true

		}
		simpleExoPlayer1?.addListener(object : Player.Listener {
			override fun onPlaybackStateChanged(playbackState: Int) {
				super.onPlaybackStateChanged(playbackState)
				if (playbackState == Player.STATE_READY) {
					if (wasEnded1) {
						bd.ovID1.clear()
					}
					wasEnded1 = false

				} else if (playbackState == Player.STATE_ENDED) {
					rightPlayBackPosition = 0
					wasEnded1 = true
					exoPlay1?.visibility = View.VISIBLE
					exoPause1?.visibility = View.GONE
				}
			}

			override fun onIsPlayingChanged(isPlaying: Boolean) {
				super.onIsPlayingChanged(isPlaying)
				if (isPlaying) {
					// 재생 시작 (처음이든 일시정지 후든)
					lifecycleScope.launch {
						val videoDuration = simpleExoPlayer1?.duration ?: 0L
						while (simpleExoPlayer1?.isPlaying == true) {
							updateOverlay1(videoDuration)
							delay(24)
						}
					}
				}
			}
		})
	}

	private var wasEnded2 = false
	private fun setPlayer2() {
		simpleExoPlayer2 = SimpleExoPlayer.Builder(requireContext()).build()
		bd.pvID2.player = simpleExoPlayer2
		bd.pvID2.controllerShowTimeoutMs = 1100
		lifecycleScope.launch {
			val isComparison = mvm.comparisonDoubleItem != null
			val rightUri = when (isComparison) {
				false -> {
					val unitIdxResult = ivm.unitIdxFromFaceResult.value
					mvm.currentResult.value?.mediaUri?.getOrNull(unitIdxResult?.second ?: 0)
				}
				true -> {
					val unitIdxCompare = ivm.unitIdxFromCompare.value
					when (ivm.getBtnIndex()) {
						3 -> mvm.comparisonDoubleItem?.first?.mediaUri?.get(unitIdxCompare?.second ?: 0)
						else -> mvm.comparisonDoubleItem?.first?.mediaUri?.get(unitIdxCompare?.second ?: 0) // 비교라서 뭘 넣어도 상관없음
					}
				}
			}
			Log.v("rightUri", "$${mvm.currentResult.value?.mediaUri}/${rightUri}")
			val mediaItem = rightUri?.let { MediaItem.fromUri(it) }
			val mediaSource = mediaItem?.let {
				ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext()))
					.createMediaSource(it)
			}

			mediaSource.let {
				if (it != null) {
					simpleExoPlayer2?.prepare(it)
				}
				// 저장된 위치로 정확하게 이동
				simpleExoPlayer2?.seekTo(0)
				onPlayer2Ready.value = true
				simpleExoPlayer2?.playWhenReady = true
			}
			simpleExoPlayer2?.addListener(object : Player.Listener {
				override fun onPlaybackStateChanged(playbackState: Int) {
					super.onPlaybackStateChanged(playbackState)
					if (playbackState == Player.STATE_READY) {
						if (wasEnded2) {
							bd.ovID2.clear()
						}
						wasEnded2 = false
					} else if (playbackState == Player.STATE_ENDED) {
						rightPlayBackPosition = 0
						wasEnded2 = true
						exoPlay2?.visibility = View.VISIBLE
						exoPause2?.visibility = View.GONE

					}
				}

				override fun onIsPlayingChanged(isPlaying: Boolean) {
					super.onIsPlayingChanged(isPlaying)
					if (isPlaying) {
						// 재생 시작 (처음이든 일시정지 후든)
						lifecycleScope.launch {
							val videoDuration = simpleExoPlayer2?.duration ?: 0L
							while (simpleExoPlayer2?.isPlaying == true) {
								updateOverlay2(videoDuration)
								delay(24)
							}
						}
					}
				}
			})
		}
	}

	private fun updateOverlay1(videoDuration: Long) {
		val currentPosition = simpleExoPlayer1?.currentPosition ?: 0L
		val totalFrames = ivm.coordinates1?.size ?: 0
		val frameIndex = ((currentPosition.toFloat() / videoDuration) * totalFrames).toInt()

		if (frameIndex in 0 until totalFrames) {
			val faceLandmarkResult = fromFaceCoordinates(ivm.coordinates1?.get(frameIndex))
			val point152 = ivm.coordinates1?.get(frameIndex)?.get(152)
			requireActivity().runOnUiThread {
//                bd.ovID1.scaleX = -1f
				bd.ovID1.setResults(
					faceLandmarkResult,
					simpleExoPlayer1?.videoFormat?.width ?: 0,
					simpleExoPlayer1?.videoFormat?.height ?: 0,
					OverlayView.RunningMode.VIDEO,

					)
				bd.ovID1.addChinTrajectoryPoint(point152?.first ?: 0f, point152?.second ?: 0f)
				bd.ovID1.invalidate()
			}
		}
	}

	private fun updateOverlay2(videoDuration: Long) {
		val currentPosition = simpleExoPlayer2?.currentPosition ?: 0L
		val totalFrames = ivm.coordinates2?.size ?: 0
		val frameIndex = ((currentPosition.toFloat() / videoDuration) * totalFrames).toInt()


		if (frameIndex in 0 until totalFrames) {
			val faceLandmarkResult = fromFaceCoordinates(ivm.coordinates2?.get(frameIndex))
//            Log.v("랜드마크확인", "${faceLandmarkResult.landmarks}")

			val point152 = ivm.coordinates2?.get(frameIndex)?.get(152)
			requireActivity().runOnUiThread {
//                bd.ovID2.scaleX = -1f
				bd.ovID2.setResults(
					faceLandmarkResult,
					simpleExoPlayer2?.videoFormat?.width ?: 0,
					simpleExoPlayer2?.videoFormat?.height ?: 0,
					OverlayView.RunningMode.VIDEO
				)
				bd.ovID2.addChinTrajectoryPoint(point152?.first ?: 0f, point152?.second ?: 0f)
				bd.ovID2.invalidate()

			}
		}
	}

	private fun releasePlayers() {
		simpleExoPlayer1?.let {
			it.stop()
			it.release()
		}
		simpleExoPlayer1 = null

		simpleExoPlayer2?.let {
			it.stop()
			it.release()
		}
		simpleExoPlayer2 = null
	}

}