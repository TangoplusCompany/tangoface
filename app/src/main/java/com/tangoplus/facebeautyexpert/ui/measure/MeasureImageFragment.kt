package com.tangoplus.facebeautyexpert.ui.measure

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.data.db.FaceDao
import com.tangoplus.facebeautyexpert.data.db.FaceDatabase
import com.tangoplus.facebeautyexpert.data.db.FaceStatic.Companion.getCurrentDateTime
import com.tangoplus.facebeautyexpert.databinding.FragmentMeasureImageBinding
import com.tangoplus.facebeautyexpert.domain.util.ClassifyUtility.calculateHorizontalRatio
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.saveImageToInternalStorage
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.toFaceStatic
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.toJsonString
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.calculateScreenX
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.calculateScreenY
import com.tangoplus.facebeautyexpert.domain.util.PreferenceUtility
import com.tangoplus.facebeautyexpert.domain.util.clearAll
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceBlendshapesResultAdapter
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkerHelper
import com.tangoplus.facebeautyexpert.domain.vision.pose.PoseLandmarkerHelper
import com.tangoplus.facebeautyexpert.domain.vo.Sequence
import com.tangoplus.facebeautyexpert.ui.LoadingDialogFragment
import com.tangoplus.facebeautyexpert.ui.adapter.MeasureImageRVAdapter
import com.tangoplus.facebeautyexpert.ui.adapter.MeasureImageVPAdapter
import com.tangoplus.facebeautyexpert.ui.info.vm.InformationViewModel
import com.tangoplus.facebeautyexpert.ui.main.MainActivity
import com.tangoplus.facebeautyexpert.ui.main.vm.MainViewModel
import com.tangoplus.facebeautyexpert.ui.measure.vm.InputViewModel
import com.tangoplus.facebeautyexpert.ui.measure.vm.MeasureImageViewModel
import com.tangoplus.facebeautyexpert.ui.measure.vm.MeasureViewModel
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.model.AspectRatio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File


class MeasureImageFragment : Fragment() {
	private lateinit var bd : FragmentMeasureImageBinding
	private val mivm: MeasureImageViewModel by viewModels()
	private val mvm: MeasureViewModel by viewModels()
	private val ivm: InputViewModel by activityViewModels()
	private val viewModel: MainViewModel by viewModels()
	private lateinit var fDao: FaceDao
	private val faceBlendshapesResultAdapter by lazy {
		FaceBlendshapesResultAdapter()
	}
	private lateinit var prefsUtil: PreferenceUtility

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentMeasureImageBinding.inflate(inflater)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initFace()
		initPose()
		initSettings()
		ivm.isFinishInput.observe(requireActivity()) {
			Log.v("시작", "플래그확인: ${ivm.isFinishInput.value}")

			if (it) {
//				val loading = LoadingDialogFragment()
//				loading.show(requireActivity().supportFragmentManager, "")
				Log.v("시작", "플래그확인: true-${ivm.isFinishInput.value}")

				lifecycleScope.launch {
					val paths = mivm.imagePaths.value ?: emptyList()
					for (path in paths) {
						if (path != null) {
							Log.v("path갯수", "path: -> ${paths} / ${path}")
							// suspend 함수이므로 여기서 처리가 끝날 때까지 기다립니다.
							val success = mvm.scannedImage(requireActivity() as AppCompatActivity, paths, mvm.currentSeq)

							if (success) {
								Log.v("시작", "번들투제이슨시작: ${mvm.currentSeq}")

								mvm.resultBundleToJson(mvm.currentSeq)

								if (mvm.currentHairLine3DCoordinate.size == 7) {
									val absoluteLandmarkPairs = mvm.customResult?.landmarks?.map {
										Pair(calculateScreenX(it.x), calculateScreenY(it.y))
									}?.toMutableList()

									absoluteLandmarkPairs?.let { pairs ->
										val horizontalRatio = calculateHorizontalRatio(pairs, mvm.currentHairLine3DCoordinate)
										if (mvm.staticJA.length() > 0) {
											mvm.staticJA.getJSONObject(0).put("horizontal_ratio", horizontalRatio)
										} else {
											mvm.staticJA.put(JSONObject().apply {
												put("horizontal_ratio", horizontalRatio)
											})
										}
									}
								}
								Log.v("시작", "번들투제이슨시작: ${mvm.currentHairLine3DCoordinate}")
								mvm.currentHairLine3DCoordinate.clear()
								mvm.moveToNextSeq()
							}
						}
					} // for 루프 끝

					// 2 seq 둘다 했고, 이제 저장하고 메인 화면으로 가기
					if (ivm.birthdayValue.value == "날짜 선택") {
						ivm.birthdayValue.value = ""
					}

					withContext(Dispatchers.IO) {
						val fd = FaceDatabase.getDatabase(requireContext())
						fDao = fd.faceDao()

						Log.v("userSn설정잘됐는지", "is init? = ${mvm.currentUserSn}, ${mvm.allSeqs}")
						val allSeqs = mvm.allSeqs
						val staticJos = mutableListOf<JSONObject>()

						allSeqs.forEachIndexed { index, sequence ->
							Log.v("staticJA", "${mvm.staticJA}")
							val staticJSON = mvm.setJo(mvm.staticJA.getJSONObject(index), sequence, ivm, prefsUtil, false)
							Log.v("staticJSON", "${mvm.staticJA.getJSONObject(index)}")
							staticJos.add(staticJSON)
						}
						Log.v("staticJson까보기0", "${staticJos.size}")

						// 받아온 user_sn으로 수정
						val now = getCurrentDateTime()
						val transStatics = staticJos.map {
							it.toFaceStatic().copy(
								user_sn = mvm.currentUserSn,
								reg_date = now
							)
						}
						// 부여 받은 user_sn넣기
						for (static in transStatics) {
							fDao.insertStatic(static)
						}

						// data
						Log.v("mvm.plrJA", "${mvm.plrJA.length()}")
						val mergedJOBeforeFileName = mutableListOf<JSONObject>()
						for (i in 0 until staticJos.size) {
							val currentStatic = transStatics[i]
							val mergedJSON = JSONObject().apply {
								put("data", JSONObject(transStatics[i].toJsonString()))

								Log.v("LEN", "i=$i, coordinatesLen=${mvm.coordinatesJA.length()}, plrLen=${mvm.plrJA.length()}, mediaNames=${mvm.mediaFileNames.size}")
								put("face_landmark", mvm.coordinatesJA.optJSONArray(i))
								put("pose_landmark", mvm.plrJA.optJSONArray(i))

								if (i == 0) {
									put("hairline_landmark", mvm.hairLineJA.optJSONArray(i))
								}
							}
							mergedJOBeforeFileName.add(mergedJSON)
							Log.v("json저장 전", "seq: $i")
							val jsonPath = mvm.saveJsonToStorage(
								requireActivity() as AppCompatActivity,
								mergedJSON,
								if (i == staticJos.size - 1 && currentStatic.seq == 9) {
									mvm.hsvMediaFileName.replace(".webp", "").replace(".mp4", "")
								} else {
									mvm.mediaFileNames[i].replace(".webp", "").replace(".mp4", "")
								}
							)


							mergedJOBeforeFileName[i].put("json_file_name", jsonPath)
							mvm.mergedJA.put(mergedJOBeforeFileName[i])
						}

						Log.v("mvm.mergedJson", "${mvm.mergedJA.length()}")

						withContext(Dispatchers.Main) {
							val lastTempServerSn = prefsUtil.getLastTempServerSn()
							// prefs에서 마지막 sn을 +1 하기
							prefsUtil.setNextTempServerSn()

//						Log.v("스태틱스Sn", "$lastTempServerSn, ${mvm.currentUserSn}: ${transStatics.map { it.user_sn }}")
							viewModel.comparisonDoubleItem = null
							Log.v("전부 끝", "저장완료, $lastTempServerSn")
//							val intent = Intent(context, MainActivity::class.java)
//							intent.putExtra("isMeasureFinish", true)
//							intent.putExtra("serverSn", lastTempServerSn)
//							startActivity(intent)

						}
					}
				}
			}
		}

		setAdapter()
		mivm.imagePaths.observe(viewLifecycleOwner) { paths ->
			// 1. 기존 가이드 텍스트 숨김 처리 (하나라도 있으면 숨김)
			bd.tvMIGuide.visibility = if (paths.any { it != null }) View.GONE else View.VISIBLE

			setVPAdapter()
			setAdapter()

			// 2. 버튼 활성화 및 색상 변경 (둘 다 null이 아닐 때)
			// 리스트 사이즈가 항상 2로 고정되어 있다면 paths.all { it != null }만 하셔도 됩니다.
			val isBothFilled = paths.size >= 2 && paths.all { it != null }

			if (isBothFilled) {
				// 둘 다 사진이 들어왔을 때: 활성화 색상 (예: 파란색)
				bd.btnMI.setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.color_gradation_main_64))
				bd.btnMI.backgroundTintList = null
				bd.btnMI.isEnabled = true
			} else {
				// 아직 둘 다 채워지지 않았을 때: 비활성화 색상 (예: 회색)
				bd.btnMI.setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.color_gradation_main_64))
				bd.btnMI.backgroundTintList = ColorStateList.valueOf((ContextCompat.getColor(requireContext(), R.color.bg300)))
				bd.btnMI.isEnabled = false
			}
		}

		bd.btnMI.setOnSingleClickListener {
			val userDialog = UserSetupDialogFragment.newInstance(1)
			userDialog.show(requireActivity().supportFragmentManager, "")
		}
	}

	private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
		uri?.let { sourceUri ->
			val tempFile = File(requireContext().cacheDir, "temp_crop_${System.currentTimeMillis()}.jpg")
			val destinationUri = Uri.fromFile(tempFile)

			val options = UCrop.Options().apply {
				setCompressionFormat(Bitmap.CompressFormat.WEBP)
				setCompressionQuality(100)
				setHideBottomControls(false)
				setAspectRatioOptions(0,
					AspectRatio("16:9", 16f, 9f),
					AspectRatio("3:4", 3f, 4f),
					AspectRatio("4:3", 4f, 3f)
				)
				setFreeStyleCropEnabled(false)
				setToolbarTitle("측정 이미지 선택")
			}

			UCrop.of(sourceUri, destinationUri)
				.withOptions(options)
				.getIntent(requireContext())
				.let { uCropActivityResult.launch(it) }
		}
	}

	private val uCropActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			val resultUri = result.data?.let { UCrop.getOutput(it) }
			resultUri?.let { handleCroppedImage(it) }
		} else if (result.resultCode == UCrop.RESULT_ERROR) {
			val cropError = result.data?.let { UCrop.getError(it) }
			Toast.makeText(context, "크롭 실패: ${cropError?.message}", Toast.LENGTH_SHORT).show()
		}
	}

	private fun handleCroppedImage(croppedUri: Uri) {

		// 이전 대화에서 만든 16:9 패딩 유틸리티 사용 추천
		val processedUri = mivm.make169WithPadding(requireContext(), croppedUri)

		processedUri?.let { uri ->
			val fileName = "measure_${mivm.targetIndex}.jpg" // 인덱스별로 파일명 고정
			val finalPath = saveImageToInternalStorage(requireContext(), uri, fileName)

			finalPath?.let { path ->
				// VM에 경로 저장 (여기서 LiveData가 업데이트됨)
				mivm.setImage(mivm.targetIndex, path)
			}
		}
	}
	private fun setVPAdapter() {
		val vAdapter = MeasureImageVPAdapter(mivm.imagePaths.value!!)
		bd.vpMI.adapter = vAdapter
	}

	private fun setAdapter() {
		val adapter = MeasureImageRVAdapter(
			fragment = this,
			uris = mivm.imagePaths.value ?: listOf(null, null)
		) { position ->
			// [클릭 시 동작]
			mivm.targetIndex = position // 어떤 슬롯을 바꾸는지 VM에 저장
			pickImage.launch("image/*") // 갤러리 열기
		}

		bd.rvMI.apply {
			layoutManager = GridLayoutManager(context, 2)
			this.adapter = adapter
		}
	}

	private fun initFace() {
		try {
			Log.d("이닛", "initializing FaceLandmarker resources...")
			// 이제 this가 완전히 초기화된 상태
			try {
				mvm.flh = FaceLandmarkerHelper(
					context = requireContext(),
					runningMode = RunningMode.IMAGE,
					minFaceDetectionConfidence = viewModel.currentMinFaceDetectionConfidence,
					minFaceTrackingConfidence = viewModel.currentMinFaceTrackingConfidence,
					minFacePresenceConfidence = viewModel.currentMinFacePresenceConfidence,
					maxNumFaces = viewModel.currentMaxFaces,
					currentDelegate = viewModel.currentDelegate,
					faceLandmarkerHelperListener = null
				)
				Log.d("PoseLandmarker", "Initialization successful!")
			} catch (e: Exception) {
				Log.e("PoseLandmarker", "Failed to initialize: ${e.message}", e)
			}
			Log.d("이닛", "PoseLandmarker initialized.")
		} catch (e: Exception) {
			Log.e("이닛", "Failed to initialze pose landmarker: ${e.message}", e)
		}
	}

	private fun initPose() {
		try {
			Log.d("이닛", "initializing PoseLandmarker resources...")
			// 이제 this가 완전히 초기화된 상태
			try {
				mvm.poseLandmarker = PoseLandmarkerHelper(
					context = requireContext(), // 이제 this가 완전히 초기화된 상태
					runningMode = RunningMode.IMAGE,
					minPoseDetectionConfidence = PoseLandmarkerHelper.DEFAULT_POSE_DETECTION_CONFIDENCE,
					minPoseTrackingConfidence = PoseLandmarkerHelper.DEFAULT_POSE_TRACKING_CONFIDENCE,
					minPosePresenceConfidence = PoseLandmarkerHelper.DEFAULT_POSE_PRESENCE_CONFIDENCE,
					currentDelegate = PoseLandmarkerHelper.DELEGATE_CPU,
					poseLandmarkerHelperListener = null
				)
				Log.d("PoseLandmarker", "Initialization successful!")
			} catch (e: Exception) {
				Log.e("PoseLandmarker", "Failed to initialize: ${e.message}", e)
			}
			Log.d("이닛", "PoseLandmarker initialized.")
		} catch (e: Exception) {
			Log.e("이닛", "Failed to initialze pose landmarker: ${e.message}", e)
		}
	}

	private fun releaseFace() {
		try {
			Log.d("릴리즈", "Releasing FaceLandmarker resources...")

			if (mvm.flh?.isClose() == true) {
				mvm.flh?.clearFaceLandmarker()
			}
			Log.d("릴리즈", "FaceLandmarker released.")
		} catch (e: Exception) {
			Log.e("릴리즈", "Failed to release face landmarker: ${e.message}", e)
		}
	}

	private fun releasePose() {
		try {
			Log.d("릴리즈", "Releasing PoseLandmarker resources...")
			if (mvm.poseLandmarker?.isClose() == true) {
				mvm.poseLandmarker?.clearPoseLandmarker()
			}
			Log.d("릴리즈", "PoseLandmarker released.")
		} catch (e: Exception) {
			Log.e("릴리즈", "Failed to release pose landmarker: ${e.message}", e)
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		releasePose()
		releaseFace()
	}

	private fun initSettings() {
		prefsUtil = PreferenceUtility(requireContext())
		viewModel.setSeqFinishedFlag(false)
		viewModel.setGuideTextFlag(false)
		viewModel.setCountDownFlag(false)
		viewModel.setTextAnimationFlag(false)
		mvm.resultTargetFs.clearAll()
		mvm.hsvColorJo.clearAll()
		mvm.hsvFLCoordinatesJA.clearAll()
		mvm.hsvPLCoordinatesJA.clearAll()
		mvm.hsvMediaFileName = ""
		mvm.staticJA.clearAll()
		mvm.coordinatesJA.clearAll()
		mvm.mediaFileNames.clear()
		mvm.plrJA.clearAll()
		mvm.hairLineJA.clearAll()
		mvm.inputWidth = 0
		mvm.inputHeight = 0
		mvm.overlayW = 0f
		mvm.overlayH = 0f

	}
}