package com.tangoplus.facebeautyexpert.ui.measure

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.util.Log
import android.util.Size
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.data.db.FaceDao
import com.tangoplus.facebeautyexpert.data.db.FaceDatabase
import com.tangoplus.facebeautyexpert.data.db.FaceStatic.Companion.getCurrentDateTime
import com.tangoplus.facebeautyexpert.databinding.ActivityNormalCameraBinding
import com.tangoplus.facebeautyexpert.domain.util.AnimationUtility.startFadeIn
import com.tangoplus.facebeautyexpert.domain.util.ClassifyUtility.calculateHorizontalRatio
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.getFileName
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.toFaceStatic
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.toJsonString
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.calculateScreenX
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.calculateScreenY
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.calculateAngle
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.calculateScaleFromPart
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.calculateSlope
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.getNormalizedDistance
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.setScaleFactor
import com.tangoplus.facebeautyexpert.domain.util.MeasureUtility.animationSet
import com.tangoplus.facebeautyexpert.domain.util.MeasureUtility.getGuideText
import com.tangoplus.facebeautyexpert.domain.util.MeasureUtility.hasPermissions
import com.tangoplus.facebeautyexpert.domain.util.MeasureUtility.showGoMainDialog
import com.tangoplus.facebeautyexpert.domain.util.MeasureUtility.showPreviousDialog
import com.tangoplus.facebeautyexpert.domain.util.PermissionUtility.getRequiredPermissions
import com.tangoplus.facebeautyexpert.domain.util.PreferenceUtility
import com.tangoplus.facebeautyexpert.domain.util.SecurityUtility.generateCustomUUID
import com.tangoplus.facebeautyexpert.domain.util.UXUtility.playSound
import com.tangoplus.facebeautyexpert.domain.util.UXUtility.startLongVibrate
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceBlendshapesResultAdapter
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkAdapter
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkerHelper
import com.tangoplus.facebeautyexpert.domain.vision.pose.PoseLandmarkerHelper
import com.tangoplus.facebeautyexpert.domain.vo.Sequence
import com.tangoplus.facebeautyexpert.ui.main.MainActivity
import com.tangoplus.facebeautyexpert.ui.main.vm.MainViewModel
import com.tangoplus.facebeautyexpert.ui.main.vo.UserItem
import com.tangoplus.facebeautyexpert.ui.measure.vm.InputViewModel
import com.tangoplus.facebeautyexpert.ui.measure.vm.MeasureViewModel
import com.tangoplus.facebeautyexpert.ui.statistics.GyroDialogFragment
import com.tangoplus.facebeautyexpert.ui.view.OverlayView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.getValue
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.use

class NormalCameraActivity : AppCompatActivity(), FaceLandmarkerHelper.LandmarkerListener {
	private lateinit var bd: ActivityNormalCameraBinding
	private lateinit var flh: FaceLandmarkerHelper
	private lateinit var plh : PoseLandmarkerHelper
	private val viewModel: MainViewModel by viewModels()
	private val mvm: MeasureViewModel by viewModels()
	private val ivm: InputViewModel by viewModels()
	private val faceBlendshapesResultAdapter by lazy {
		FaceBlendshapesResultAdapter()
	}
	private var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))

	private var preview: Preview? = null
	private var imageAnalyzer: ImageAnalysis? = null
	private var camera: Camera? = null
	private var cameraProvider: ProcessCameraProvider? = null
	private var cameraFacing = CameraSelector.LENS_FACING_FRONT
	private var isCountDown = false

	private lateinit var backgroundExecutor: ExecutorService
	private lateinit var imageCapture: ImageCapture

	private lateinit var fDao: FaceDao
	private lateinit var prefsUtil: PreferenceUtility
	private var bounceAnimator: AnimatorSet? = null

	private val mCountDown: CountDownTimer by lazy {
		object : CountDownTimer(3000, 1000) {
			@SuppressLint("SetTextI18n")
			override fun onTick(millisUntilFinished: Long) {
				runOnUiThread {
					bd.clNCCountDown.visibility = View.VISIBLE
					bd.clNCCountDown.alpha = 1f
					bd.tvNCCountDown.text = "${(millisUntilFinished / 1000.0f).roundToInt()}"
					Log.v("count", "${bd.tvNCCountDown.text}")
					if (millisUntilFinished > 500f) {
						playSound(R.raw.camera_countdown)
					}
				}
			}

			override fun onFinish() {
				Log.v("카운트 끝", "${mvm.face3DResult}, 현재 seq: ${mvm.currentSeq}")
				if (mvm.face3DResult.isNotEmpty()) {
					timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
					hideViews()
					playSound(R.raw.camera_shutter)

					lifecycleScope.launch {
						viewModel.setContourSave(true)

						val success = captureImage(mvm.currentSeq)  // 여기서 saveMediaStorage까지 전부 완료
						if (success) {
							// hairline, pose 전부 끝난 시점
							mvm.resultBundleToJson( mvm.currentSeq)
							if (mvm.currentHairLine3DCoordinate.size == 7) {
								val absoluteLandmarkPairs = mvm.face2DResult?.landmarks?.map {
									Pair(calculateScreenX( it.x), calculateScreenY( it.y))
								}?.toMutableList()
								// 🕊️🕊️🕊️🕊️ 여기서 vertical 비율 넣기
								absoluteLandmarkPairs?.let { pairs ->
									val horizontalRatio = calculateHorizontalRatio(pairs, mvm.currentHairLine3DCoordinate)
									if (mvm.staticJA.length() > 0) {
										// 2. 첫 번째(인덱스 0) JSONObject를 꺼내서 값을 추가(또는 덮어쓰기)
										val firstJsonObject = mvm.staticJA.getJSONObject(0)
										firstJsonObject.put("horizontal_ratio", horizontalRatio)
									} else {
										// 3. 만약 배열이 비어있다면 새로 만들어서 첫 번째로 넣어줌
										mvm.staticJA.put(JSONObject().apply {
											put("horizontal_ratio", horizontalRatio)
										})
									}
//									Log.v("스태틱", "${mvm.staticJA}")
								}
							}
							mvm.currentHairLine3DCoordinate.clear()
							bd.overlay.setOnScanFinishedListener {
								startLongVibrate(this@NormalCameraActivity)
								// 이 블록 안의 코드는 애니메이션이 왕복을 마치고 완전히 멈췄을 때 실행됩니다.
								viewModel.setSeqFinishedFlag(true)
								updateUI()

								// 리스너 재사용을 방지하고 싶다면 여기서 초기화
								bd.overlay.setOnScanFinishedListener {}
							}
							// 3. 스캔 중단 요청 (OverlayView 내부 로직에 의해 왕복 후 멈춤)
							bd.overlay.stopScanAnimation()
						}
					}
				} else {
					Toast.makeText(this@NormalCameraActivity, "얼굴 인식에 문제가 발생했습니다. 다시 시도해주세요", Toast.LENGTH_SHORT).show()
					updateUI()
					setPreviousStep()
				}
			}
		}
	} //  ------! 카운트 다운 끝 !-------
	private val requestPermissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestMultiplePermissions()
	) { permissions ->
		if (permissions.all { it.value }) {
			backgroundExecutor.execute {
				if (flh.isClose()) {
					flh.setupFaceLandmarker()
				}
			}
		} else {
			// 권한이 거부됨
			Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
		}
	}

	override fun onResume() {
		super.onResume()
		Log.v("오버라이딩", "onResume 시작 ")
		if (ivm.isFinishInput.value == true) {
			if (!hasPermissions(this)) {
				requestPermissionLauncher.launch(getRequiredPermissions())
			}
		}
	}

	override fun onPause() {
		super.onPause()
	}

	override fun onDestroy() {
		super.onDestroy()
		// 2️⃣ 모든 리소스 완전 해제 (안전망)
		releaseCamera()
		releasePose()
		releaseFace()
		// ---------# 메모리 정리 #---------
		System.gc()
		Runtime.getRuntime().gc()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		bd = ActivityNormalCameraBinding.inflate(layoutInflater)
		WindowCompat.setDecorFitsSystemWindows(window, false)

		setContentView(bd.root)
		title = "normalCamera"
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		Log.v("노말init", "onCreate 시작")
		initFace()
		initPose()

		val controller = WindowCompat.getInsetsController(window, window.decorView)
		controller.apply {
			hide(WindowInsetsCompat.Type.systemBars())
			systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		}

		mvm.initMeasure.observe(this) {
			if (it) {
				Log.v("이닛measure","${mvm.initMeasure}")
				initSettings()
				mvm.initMeasure.value = false
			}
		}

		ivm.isFinishInput.observe(this) {
			Log.v("인풋", "2: ${ivm.nameValue.value}, ${ivm.mobileValue.value}, ${ivm.genderValue.value}, ${ivm.birthdayValue.value}, ${ivm.nationalityValue.value}")
			initCamera()
		}

		prefsUtil = PreferenceUtility(this@NormalCameraActivity)
		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				showGoMainDialog(this@NormalCameraActivity)
			}
		})

		bd.btnNCShooting.setOnSingleClickListener {
			if (bd.btnNCShooting.text == "결과보기") {
				// ---------------# 데이터 갈무리해서 gallery DialogFragment로 넘기기 #-----------------
				// 각각의 static에 사용자 정보 넣기

				// GUEST일 경우 초기화
				if (ivm.birthdayValue.value == "날짜 선택") {
					ivm.birthdayValue.value = ""
				}

				lifecycleScope.launch(Dispatchers.IO) {
					val fd = FaceDatabase.getDatabase(this@NormalCameraActivity)
					fDao = fd.faceDao()

					Log.v("userSn설정잘됐는지", "is init? = ${mvm.currentUserSn}, ${mvm.allSeqs}")
					val allSeqs = mvm.allSeqs
					val staticJos = mutableListOf<JSONObject>()

					allSeqs.forEachIndexed { index, sequence ->
						val staticJSON = mvm.setJo(mvm.staticJA.getJSONObject(index), sequence, ivm, prefsUtil, true)
						staticJos.add(staticJSON)
//						Log.v("staticJSON", "$staticJSON")
					}
//					Log.v("staticJson까보기0", "${staticJos.size}")

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
							this@NormalCameraActivity,
							mergedJSON,
							mvm.mediaFileNames[i].replace(".webp", "").replace(".mp4", "")
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
						val intent = Intent(this@NormalCameraActivity, MainActivity::class.java)
						intent.putExtra("serverSn", lastTempServerSn)
						startActivity(intent)
						finishAffinity()
					}
				}
			} else {
				if (!isCountDown) {
					startTimer()
				}
			}
		}

		bd.tvNCPrevious.setOnSingleClickListener {
			showPreviousDialog(this@NormalCameraActivity, { setPreviousStep() })
		}

		bd.tvNCGoGallery.setOnSingleClickListener {
			showGoMainDialog(this)
		}

		bd.tvNCCameraDirection.setOnSingleClickListener {
			switchCamera()
		}
		bd.tvNCGyro.setOnSingleClickListener {
			val gyro = GyroDialogFragment()
			gyro.show(supportFragmentManager, "gyroDialogFragment")
		}
	}

	private fun switchCamera() {
		cameraProvider?.unbindAll()

		cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
			CameraSelector.LENS_FACING_BACK
		} else {
			CameraSelector.LENS_FACING_FRONT
		}
		bindCameraUseCases()
	}

	private fun setGuideAnimation(seq: Sequence, isEnded: Boolean) {
		bd.btnNCShooting.isEnabled = false
		bd.tvNCPrevious.isEnabled = false
		bd.tvNCSeqGuide.apply {
			clearAnimation()
			text = if (isEnded) {
				"수고하셨습니다\n버튼을 눌러 결과를 확인해보세요"
			} else {
				getGuideText(seq)
			}
			visibility = View.VISIBLE
			animation = animationSet
		}
	}

	private fun setPreviousStep() {
		mvm.moveToPreviousSeq()

		Log.v("이전seq", "${mvm.allSeqs}/${mvm.currentSeq}")
		bd.ivNCFrame.setImageDrawable(
			ContextCompat.getDrawable(
				this@NormalCameraActivity,
				when (mvm.currentSeq) {
					Sequence.COMFORT -> R.drawable.drawable_seq_0
					else -> R.drawable.drawable_seq_1
				}
			)
		)
		bd.clNCCountDown.visibility = View.GONE
		mCountDown.cancel()
		setGuideAnimation(mvm.currentSeq, false)

		bd.btnNCShooting.apply {
			text = "촬영"
			isEnabled = true
		}
		bd.tvNCPrevious.apply {
			visibility = if (mvm.currentSeq == mvm.allSeqs[0]) View.INVISIBLE else View.VISIBLE
			isEnabled = true
		}

		val seqCount = "${mvm.getSeqIndex() + 1}. ${mvm.currentSeq.displayName}"
		bd.tvNCSeqCount.text = seqCount
		viewModel.setCountDownFlag(false)

		val ci =  mvm.getSeqIndex()
		mvm.tempStaticJo = null
		mvm.staticJA.remove(ci)
		mvm.coordinatesJA.remove(ci)
		mvm.plrJA.remove(ci)
		mvm.mediaFileNames.removeAt(ci)
		Log.v("그외STATIC삭제", "${mvm.tempStaticJo}, ${mvm.staticJA.length()}, ${mvm.coordinatesJA.length()}, ${mvm.plrJA.length()}, ${mvm.mediaFileNames}")
		isCountDown = false
		bd.tvNCGoGallery.isEnabled = true
	}

	private fun startTimer() {
		// 시작 버튼 후 시작
		bd.btnNCShooting.isEnabled = false
		bd.tvNCPrevious.isEnabled = false
		bd.tvNCGoGallery.isEnabled = false
		Log.v("startTimer", "${mvm.currentSeq} / ${mvm.allSeqs.size}")
		mCountDown.start()
		viewModel.setCountDownFlag(true)
		// ------! 타이머 control 끝 !------
	}

	private fun updateUI() {
		val isProcessing = mvm.moveToNextSeq()
		if (!isProcessing) {
			setGuideAnimation(mvm.currentSeq, true)
			bd.clNCCountDown.visibility = View.GONE
			bd.btnNCShooting.apply {
				text = "결과보기"
				isEnabled = true
			}

			mCountDown.cancel()
			Log.v("몇단계?", "Max repeats reached, stopping the loop")
		} else {
			setGuideAnimation(mvm.currentSeq, false)
			val seqCount = "${mvm.getSeqIndex() + 1}. ${mvm.currentSeq.displayName} / ${mvm.allSeqs.size}"
			bd.tvNCSeqCount.text = seqCount
			viewModel.setCountDownFlag(false)
			bd.ivNCFrame.visibility = View.VISIBLE
			startFadeIn(bd.ivNCFrame)
			bd.ivNCFrame.setImageDrawable(
				ContextCompat.getDrawable(this@NormalCameraActivity, R.drawable.drawable_seq_1)
			)
			startLookUpAnimation()
		}

		bd.btnNCShooting.apply {
			isEnabled = true
		}

		Handler(Looper.getMainLooper()).postDelayed({
			viewModel.setSeqFinishedFlag(false)
		}, 1500)
		isCountDown = false
		bd.tvNCGoGallery.isEnabled = true
		Log.v("업데이트UI", "${mvm.plrJA.length()}")
	}

	override fun onError(error: String, errorCode: Int) {
		runOnUiThread {
			Log.e("faceError", "$errorCode, $error")
//            Toast.makeText(this@CameraActivity, error, Toast.LENGTH_SHORT).show()
			faceBlendshapesResultAdapter.updateResults(null)
			faceBlendshapesResultAdapter.notifyDataSetChanged()

			if (errorCode == FaceLandmarkerHelper.GPU_ERROR) {
				Log.e("GPU_ERROR", "$errorCode, FaceLandmarkerHelper GPU_ERROR")
			}
		}
	}

	override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle) {
		runOnUiThread {
			mvm.face2DResult = FaceLandmarkAdapter.toCustomFaceLandmarkResult(resultBundle.result)
			if (mvm.face2DResult != null) {
				// depthCamera Stream 좌우반전 대응 - 얼굴 랜드마크 좌우 반전
//				val flippedLandmarks = mvm.face2DResult!!.landmarks.map { landmark ->
//					landmark.copy(x = 1.0f - landmark.x)
//				}
//				val overlayResult = mvm.face2DResult!!.copy(landmarks = flippedLandmarks)

				bd.overlay.setResults(
					mvm.face2DResult!!,
					resultBundle.inputImageWidth,
					resultBundle.inputImageHeight,
					OverlayView.RunningMode.LIVE_STREAM,
					true
				)
			}

			// 처음 faceLanmdark가 init할 때 전부 선언
			mvm.initWidthHeight(resultBundle)
			mvm.initOverlaySize(bd.overlay)
			mvm.initScaleFactor(bd.overlay)
			val landmarks = mvm.face2DResult?.landmarks

			if (!landmarks.isNullOrEmpty()) {
				val point1 = landmarks.getOrNull(468)
				val point2 = landmarks.getOrNull(473)

				if (point1 != null && point2 != null) {
					val dx = point1.x - point2.x
					val dy = point1.y - point2.y
					val distance = sqrt(dx * dx + dy * dy)
					// 이 distance는 0~1 사이의 상대 거리입니다. 예: 0.05 = 화면 너비의 5%
					// 여기서 원하는 scale값을 거리 기반으로 정하면 됩니다.
					//                    val scale = yourScalingFunction(distance)
					//                    Log.v("거리", "$distance")
					// 0.085 ~ 0.09 일 때 32f
					// 0.0
					// 3.7
					val scale = calculateScaleFromPart(distance)
					//                    Log.v("스케일설정", "$distance $scale")
					setScaleFactor(scale)
				}

				val leftEye = landmarks[33]
				val rightEye = landmarks[263]
				val middleEye = landmarks[6]

				val noseTip = landmarks[1]     // 코끝
				val leftEarPoint = landmarks[234] // 왼쪽 귓바퀴 근처
				val rightEarPoint = landmarks[454] // 오른쪽 귓바퀴 근처

				val leftEyeDistance =
					getNormalizedDistance(Pair(leftEye.x, leftEye.y), Pair(middleEye.x, middleEye.y))
				val rightEyeDistance =
					getNormalizedDistance(Pair(rightEye.x, rightEye.y), Pair(middleEye.x, middleEye.y))
				val eyeDistanceGap = abs(leftEyeDistance - rightEyeDistance)
				// horizon state 설정
				val horizontalLineVector = calculateAngle(
					leftEarPoint.x,
					leftEarPoint.y,
					noseTip.x,
					noseTip.y,
					rightEarPoint.x,
					rightEarPoint.y
				)
				val horizonBoolean = when (mvm.currentSeq) {
					Sequence.COMFORT, Sequence.NECK_EXTENTION -> true
					else -> horizontalLineVector in 115f..180f
				}

				bd.overlay.setHorizon(horizonBoolean)
				//                Log.v("얼굴 중앙", "$isFaceCenter")
				//                Log.v("라인벡터", "코: ${faceLandmarks[0].x()}, ${faceLandmarks[0].y()}, 왼쪽눈: ${faceLandmarks[33].x()}, ${faceLandmarks[33].x()}, 오른쪽 눈: ${faceLandmarks[263].x()}, ${faceLandmarks[263].x()}")

				// verti state 설정
				val vertiBoolean = when (mvm.currentSeq) {
					Sequence.COMFORT, Sequence.NECK_EXTENTION,-> true // 5 목폄 상태일때만
					else -> if (eyeDistanceGap < 0.35f) true else false
				}
				val eyeSlope = calculateSlope(leftEye.x, leftEye.y, rightEye.x, rightEye.y)
				// 눈 가로 선 (얼굴이 360도 방향을 x, y 방향 회전)
//                Log.v("슬롭봐보기", "$eyeSlope")
				val eyeParallel = when (mvm.currentSeq) {
					Sequence.COMFORT, Sequence.NECK_EXTENTION, -> true
					else -> (eyeSlope in 167.5f..187.5f) || (eyeSlope in -187.5f..-167.5f)
				}
				val vertiMediator = vertiBoolean && eyeParallel

				bd.overlay.setVerti(vertiMediator)

			}

//            Log.v("인풋이미지", "${resultBundle.inputImageHeight}, ${resultBundle.inputImageWidth}")
//            Log.v("results", "${resultBundle.result.faceLandmarks().map { it.size }}")
			bd.overlay.invalidate()

			if (ivm.isFinishInput.value == true && resultBundle.result.faceLandmarks()
					.isNotEmpty() && !viewModel.getSeqFinishedFlag()
			) { // && isFaceCenter
				// 애니메이션 제거 flag
				if (!viewModel.getGuideTextFlag()) {

					bounceAnimator?.cancel()
					bounceAnimator = null
					bd.tvNCSeqGuide.animate().cancel()
					bd.tvNCSeqGuide.clearAnimation()
					bd.tvNCSeqGuide.translationY = 0f
					bd.tvNCSeqGuide.text = "안녕하세요!\n${getGuideText(mvm.currentSeq)}"
					viewModel.setGuideTextFlag(true)
				}

				// guideText 움직이기 flag
				/*if (!viewModel.getTextAnimationFlag()) {
					Handler(Looper.getMainLooper()).postDelayed({
						val isPortrait = if (getScreenOrientation().contains("portrait")) true else false

						animateTextViewToTopLeft(
							bd.clSeqGuide,
							bd.tvSeqGuide,
							if (isPortrait) 0.05f else 0.1f,
							0.05f
						)
					}, 2000)


					// animation flag에다가 애니메이션
					if (mvm.currentSeq in listOf(
							Sequence.NECK_EXTENTION
						)
					) {
						viewModel.setTextAnimationFlag(true)
					}
				}*/
			}
		}
	}
	// --------------------------------- # pose landmark # -----------------------------------------
	private val poseLandmarkListener = object : PoseLandmarkerHelper.LandmarkerListener {
		override fun onError(error: String, errorCode: Int) {
			runOnUiThread {
				Log.e("PoseLandmark", "Pose detection error: $error $errorCode")
				Toast.makeText(
					this@NormalCameraActivity,
					"Pose detection error: $error",
					Toast.LENGTH_SHORT
				).show()
			}
		}
		override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {}
	}

	private fun initFace() {
		try {
			Log.d("이닛", "initializing FaceLandmarker resources...")
			// 이제 this가 완전히 초기화된 상태
			try {
				if (!::backgroundExecutor.isInitialized || backgroundExecutor.isShutdown) {
					backgroundExecutor = Executors.newSingleThreadExecutor()
				}
				backgroundExecutor.execute {
					flh = FaceLandmarkerHelper(
						context = this@NormalCameraActivity,
						runningMode = RunningMode.LIVE_STREAM,
						minFaceDetectionConfidence = viewModel.currentMinFaceDetectionConfidence,
						minFaceTrackingConfidence = viewModel.currentMinFaceTrackingConfidence,
						minFacePresenceConfidence = viewModel.currentMinFacePresenceConfidence,
						maxNumFaces = viewModel.currentMaxFaces,
						currentDelegate = viewModel.currentDelegate,
						faceLandmarkerHelperListener = this
					)
				}
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
				plh = PoseLandmarkerHelper(
					context = this, // 이제 this가 완전히 초기화된 상태
					runningMode = RunningMode.IMAGE,
					minPoseDetectionConfidence = PoseLandmarkerHelper.DEFAULT_POSE_DETECTION_CONFIDENCE,
					minPoseTrackingConfidence = PoseLandmarkerHelper.DEFAULT_POSE_TRACKING_CONFIDENCE,
					minPosePresenceConfidence = PoseLandmarkerHelper.DEFAULT_POSE_PRESENCE_CONFIDENCE,
					currentDelegate = PoseLandmarkerHelper.DELEGATE_CPU,
					poseLandmarkerHelperListener = poseLandmarkListener
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

	private fun initCamera() {
		try {
			requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

			if (!::backgroundExecutor.isInitialized || backgroundExecutor.isShutdown) {
				backgroundExecutor = Executors.newSingleThreadExecutor()
			}
			backgroundExecutor.execute {
				flh = FaceLandmarkerHelper(
					context = this@NormalCameraActivity,
					runningMode = RunningMode.LIVE_STREAM,
					minFaceDetectionConfidence = viewModel.currentMinFaceDetectionConfidence,
					minFaceTrackingConfidence = viewModel.currentMinFaceTrackingConfidence,
					minFacePresenceConfidence = viewModel.currentMinFacePresenceConfidence,
					maxNumFaces = viewModel.currentMaxFaces,
					currentDelegate = viewModel.currentDelegate,
					faceLandmarkerHelperListener = this
				)
			}

			val cameraProviderFuture =
				ProcessCameraProvider.getInstance(this@NormalCameraActivity)
			cameraProviderFuture.addListener(
				{
					// CameraProvider
					cameraProvider = cameraProviderFuture.get()

					// Build and bind the camera use cases
					bindCameraUseCases()
				}, ContextCompat.getMainExecutor(this@NormalCameraActivity)
			)

			Log.d("이닛", "Camera resources initialized.")
		} catch (e: Exception) {
			Log.e("이닛", "Failed to initialize camera resources: ${e.message}", e)
		}
	}
	private fun bindCameraUseCases() {
		// CameraProvider
		val cameraProvider = cameraProvider
			?: throw IllegalStateException("Camera initialization failed.")

		val cameraSelector =
			CameraSelector.Builder().requireLensFacing(cameraFacing).build()
		bd.viewFinder.doOnLayout {
//            bd.viewFinder.viewTreeObserver.removeOnGlobalLayoutListener(this)
			preview = Preview.Builder().setTargetResolution(Size(1080, 1920))
				.setTargetRotation(bd.viewFinder.display.rotation)
				.build()
			imageAnalyzer =
				ImageAnalysis.Builder().setTargetResolution(Size(1080, 1920))
					.setTargetRotation(bd.viewFinder.display.rotation)
					.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
					.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
					.build()
					// The analyzer can then be assigned to the instance
					.also {
						it.setAnalyzer(backgroundExecutor) { image ->
							detectFace(image)
						}
					}
			imageCapture = ImageCapture.Builder()
				.setTargetResolution(Size(1080, 1920))
				.setTargetRotation(bd.viewFinder.display.rotation)
				.build()
			cameraProvider.unbindAll()

			try {
				camera = cameraProvider.bindToLifecycle(
					this@NormalCameraActivity,
					cameraSelector,
					preview,
					imageAnalyzer,
					imageCapture,
				)
				preview?.surfaceProvider = bd.viewFinder.surfaceProvider
			} catch (exc: Exception) {
				Log.e("cameraBind", "Use case binding failed", exc)
			}
		}
	}

	private fun detectFace(imageProxy: ImageProxy) {
		flh.detectLiveStream(
			imageProxy = imageProxy,
			isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
		)
	}

	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)
		imageAnalyzer?.targetRotation =
			bd.viewFinder.display.rotation
	}

	// -------------------------------------# release #---------------------------------------------
	private fun releaseFace() {
		try {
			Log.d("릴리즈", "Releasing FaceLandmarker resources...")

			// backgroundExecutor를 안전하게 정리
			if (::backgroundExecutor.isInitialized && !backgroundExecutor.isShutdown) {
				backgroundExecutor.shutdownNow()
			}

			// FaceLandmarkerHelper 해제
			flh.clearFaceLandmarker()

			Log.d("릴리즈", "FaceLandmarker released.")
		} catch (e: Exception) {
			Log.e("릴리즈", "Failed to release face landmarker: ${e.message}", e)
		}
	}

	private fun releasePose() {
		try {
			Log.d("릴리즈", "Releasing PoseLandmarker resources...")
			if (::plh.isInitialized && !plh.isClose()) {
				plh.clearPoseLandmarker()
			}
			Log.d("릴리즈", "PoseLandmarker released.")
		} catch (e: Exception) {
			Log.e("릴리즈", "Failed to release pose landmarker: ${e.message}", e)
		}
	}

	override fun finish() {
		// 방향을 UNSPECIFIED로 설정하여 자연스럽게 복원 ( 일반카메라의 강제 가로모드에서 리소스 A로그 방지 )
		requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
		super.finish()
	}

	private fun releaseCamera() {
		try {
			Log.d("릴리즈", "Releasing RGB camera resources...")
			if (::backgroundExecutor.isInitialized) {
				try {
					backgroundExecutor.execute { flh.clearFaceLandmarker() }
					backgroundExecutor.shutdown()
					backgroundExecutor.awaitTermination(
						Long.MAX_VALUE, TimeUnit.NANOSECONDS
					)
					Log.d("릴리즈", "BackgroundExecutor shut down successfully")
				} catch (e: InterruptedException) {
					backgroundExecutor.shutdownNow()
					Thread.currentThread().interrupt()
				}
			}

			if (imageAnalyzer != null) {
				imageAnalyzer?.clearAnalyzer()
				imageAnalyzer = null
			}
			if (::flh.isInitialized && !flh.isClose()) {
				flh.clearFaceLandmarker()
			}

			// 100ms 후 해제
			Handler(Looper.getMainLooper()).postDelayed({
				cameraProvider?.unbindAll()
				cameraProvider = null

				Log.d("릴리즈", "Camera resources released.")
			}, 500)

		} catch (e: Exception) {
			Log.e("릴리즈", "Failed to release camera resources: ${e.message}", e)
		}
	}


	private fun initSettings() {

		mvm.allSeqs = prefsUtil.getLastMeasureSeqs().sortedBy { it.ordinal }.toMutableList()
		mvm.currentSeq = Sequence.COMFORT

		val seqCount = "${mvm.getSeqIndex() + 1} / ${mvm.allSeqs.size}"

		bd.tvNCSeqCount.text = seqCount
		mvm.mediaFileNames.clear()
		mvm.currentCoordinate = mutableListOf()
		ivm.nameValue.value = ""
		ivm.mobileValue.value = ""
		ivm.isShownBtn = false
		viewModel.comparisonDoubleItem = null

		// userFragment에서 왔을 경우 이름과 휴대폰 번호 넣고, 설문지만 넣기
		val currentUserItem = intent.getParcelableExtra<UserItem>("currentUserItem")
		Log.v("currentUserItem", "is init? = $currentUserItem, ${currentUserItem == null}")
		if (currentUserItem != null) {
			ivm.mobileValue.value = currentUserItem.mobile
			ivm.nameValue.value = currentUserItem.name
			ivm.nationalityValue.value = currentUserItem.nationality
			ivm.birthdayValue.value = currentUserItem.birthday
			ivm.genderValue.value = currentUserItem.gender
			mvm.currentUserSn = currentUserItem.tempServerSn
		}
		val key = getString(R.string.kekeke)
		mvm.currentUUID = generateCustomUUID(ivm.nameValue.value, ivm.mobileValue.value, ivm.birthdayValue.value,ivm.genderValue.value,key)


		Log.v("가져온게 있나요", "Out: ${ivm.mobileValue.value}, ${ivm.nameValue.value}, ${mvm.currentUserSn}")
		val userDialog = UserSetupDialogFragment.newInstance(0)
		userDialog.show(supportFragmentManager, "")

		mvm.currentSeq = Sequence.COMFORT

		// flat 초기화
		viewModel.setSeqFinishedFlag(false)
		viewModel.setGuideTextFlag(false)
		viewModel.setCountDownFlag(false)
		viewModel.setTextAnimationFlag(false)
	}

	private fun hideViews() {
		bd.overlay.startScanAnimation()
	}

	private var lookUpAnimator: ValueAnimator? = null
	fun startLookUpAnimation() {
		val iv = bd.ivNCFrame
		val container = bd.llNCLookUpContainer
		val arrow = bd.tvLookUpArrow

		iv.post {
			// 1. 컨테이너(눈+화살표 전체)의 위치를 75%, 30% 지점으로 이동
			val startX = iv.x + (iv.width * 0.8f) - (container.width / 2f)
			val startY = iv.y + (iv.height * 0.25f)

			container.x = startX
			container.y = startY
			container.visibility = View.VISIBLE

			lookUpAnimator?.cancel()

			lookUpAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
				duration = 1500 // 1.5초
				repeatCount = ValueAnimator.INFINITE // 무한 반복

				addUpdateListener { animator ->
					val fraction = animator.animatedValue as Float

					// 2. 화살표(arrow)만 위로 이동
					arrow.translationY = -100f * fraction

					// 3. 화살표(arrow)만 투명도 조절
					arrow.alpha = when {
						fraction < 0.2f -> fraction / 0.2f
						fraction > 0.7f -> (1f - fraction) / 0.3f
						else -> 1f
					}
				}
			}
			lookUpAnimator?.start()
		}
	}
	private suspend fun captureImage(seq: Sequence): Boolean {
		val name = getFileName(seq)

		return withContext(Dispatchers.IO) {
			// 1. 이미지가 임시 파일로 저장될 때까지 대기 (Uri 반환)
			val tempUri = suspendCancellableCoroutine { continuation ->
				val tempFile = File.createTempFile("temp_capture", ".jpg", cacheDir)
				val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

				imageCapture.takePicture(
					outputOptions,
					ContextCompat.getMainExecutor(this@NormalCameraActivity),
					object : ImageCapture.OnImageSavedCallback {
						override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
							continuation.resume(Uri.fromFile(tempFile))
						}
						override fun onError(exception: ImageCaptureException) {
							if (tempFile.exists()) tempFile.delete()
							continuation.resume(null)
						}
					}
				)
			}

			// 2. 임시 파일 생성 성공 시, 미디어 스토어 저장까지 완료 후 리턴
			if (tempUri != null) {
				try {
					// 여기서 suspend 함수를 호출하므로 저장이 끝날 때까지 대기함
					mvm.saveMediaToStorage(this@NormalCameraActivity, tempUri, name, flh, plh)
					true
				} catch (e: Exception) {
					Log.e("captureImage", "Save to storage failed", e)
					false
				}
			} else {
				false
			}
		}
	}
}