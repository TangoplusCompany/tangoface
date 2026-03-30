package com.tangoplus.facebeautyexpert.ui.measure

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.data.db.FaceDao
import com.tangoplus.facebeautyexpert.data.db.FaceDatabase
import com.tangoplus.facebeautyexpert.data.db.FaceStatic.Companion.getCurrentDateTime
import com.tangoplus.facebeautyexpert.databinding.ActivityNormalCameraBinding
import com.tangoplus.facebeautyexpert.domain.util.AnimationUtility.animateTextViewToTopLeft
import com.tangoplus.facebeautyexpert.domain.util.AnimationUtility.startFadeIn
import com.tangoplus.facebeautyexpert.domain.util.AnimationUtility.startFadeOut
import com.tangoplus.facebeautyexpert.domain.util.BitmapUtility.getScreenOrientation
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.getFileName
import com.tangoplus.facebeautyexpert.ui.view.OverlayView
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.calculateScaleFromPart
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.calculateSlope
import com.tangoplus.facebeautyexpert.domain.util.SoundManager.playSound
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceBlendshapesResultAdapter
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkerHelper
import com.tangoplus.facebeautyexpert.ui.main.vm.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.toFaceStatic
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.toJsonString
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.waitForFileReady
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.calculateAngle
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.calculatePolygonArea
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.correctingValue
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.getNormalizedDistance
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.getScaleFactor
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.normalizeAngle90
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.setScaleFactor
import com.tangoplus.facebeautyexpert.domain.util.MeasureUtility.animationSet
import com.tangoplus.facebeautyexpert.domain.util.MeasureUtility.getGuideText
import com.tangoplus.facebeautyexpert.domain.util.MeasureUtility.hasPermissions
import com.tangoplus.facebeautyexpert.domain.util.MeasureUtility.showGoMainDialog
import com.tangoplus.facebeautyexpert.domain.util.MeasureUtility.showPreviousDialog
import com.tangoplus.facebeautyexpert.domain.util.MeasureUtility.showReStartDialog
import com.tangoplus.facebeautyexpert.domain.util.MediaUtility.calculateScreenX
import com.tangoplus.facebeautyexpert.domain.util.MediaUtility.calculateScreenY
import com.tangoplus.facebeautyexpert.domain.util.PermissionUtility.getRequiredPermissions
import com.tangoplus.facebeautyexpert.domain.util.PreferenceUtility
import com.tangoplus.facebeautyexpert.domain.util.SecurityUtility.generateCustomUUID
import com.tangoplus.facebeautyexpert.domain.util.copyFrom
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkAdapter
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkResult
import com.tangoplus.facebeautyexpert.domain.vision.pose.PoseLandmarkerHelper
import com.tangoplus.facebeautyexpert.domain.vo.Sequence
import com.tangoplus.facebeautyexpert.orbbec.vo.Point3D
import com.tangoplus.facebeautyexpert.ui.main.MainActivity
import com.tangoplus.facebeautyexpert.ui.statistics.GyroDialogFragment
import com.tangoplus.facebeautyexpert.ui.main.vo.UserItem
import com.tangoplus.facebeautyexpert.ui.measure.vm.InputViewModel
import com.tangoplus.facebeautyexpert.ui.measure.vm.MeasureViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.collections.listOf
import kotlin.coroutines.resume
import kotlin.lazy
import kotlin.math.abs

class NormalCameraActivity : AppCompatActivity(), FaceLandmarkerHelper.LandmarkerListener {

//	private lateinit var bd: ActivityNormalCameraBinding
//
//	companion object {
//		private const val TAG = "Face Landmarker"
//	}
//	// ------------# pipeline #------------
//
//	private lateinit var flh: FaceLandmarkerHelper
//	private val viewModel: MainViewModel by viewModels()
//	private val mvm: MeasureViewModel by viewModels()
//	private val ivm: InputViewModel by viewModels()
//	private val faceBlendshapesResultAdapter by lazy {
//		FaceBlendshapesResultAdapter()
//	}
//
//	private var preview: Preview? = null
//	private var imageAnalyzer: ImageAnalysis? = null
//	private var camera: Camera? = null
//	private var cameraProvider: ProcessCameraProvider? = null
//	private var cameraFacing = CameraSelector.LENS_FACING_FRONT
//	private var isCountDown = false
//	private lateinit var videoCapture: VideoCapture<Recorder>
//	private var recordingJob: Job? = null
//	private var recording: Recording? = null
//	private var startRecording = false
//
//	private lateinit var backgroundExecutor: ExecutorService
//	private lateinit var imageCapture: ImageCapture
//
//	private var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
//
//	var customResult: FaceLandmarkResult? = null
//	private var scaleFactorX: Float? = null
//	private var scaleFactorY: Float? = null
//	private var inputWidth: Int = 0
//	private var inputHeight: Int = 0
//
//	private var isRecording = false
//
//	private lateinit var fDao: FaceDao
//	private lateinit var prefsUtil: PreferenceUtility
//	private var bounceAnimator: AnimatorSet? = null
//
//	// pose
//	private lateinit var poseLandmarker: PoseLandmarkerHelper
//
//	private val mCountDown: CountDownTimer by lazy {
//		object : CountDownTimer(3000, 1000) {
//			@SuppressLint("SetTextI18n")
//			override fun onTick(millisUntilFinished: Long) {
//				runOnUiThread {
//					bd.clNCCountDown.visibility = View.VISIBLE
//					bd.clNCCountDown.alpha = 1f
//					bd.tvNCCountDown.text = "${(millisUntilFinished / 1000.0f).roundToInt()}"
//					Log.v("count", "${bd.tvNCCountDown.text}")
//					if (millisUntilFinished > 500f) {
//						playSound(R.raw.camera_countdown)
//					}
//				}
//			}
//
//			override fun onFinish() {
//				Log.v("카운트 시작", "${mvm.coordinatesJA.length()}, 현재 seq: ${mvm.currentSeq}")
//				if (customResult?.landmarks?.isNotEmpty() == true) {
//					timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
//					hideViews()
//					setJawTiltGuideText(mvm.currentSeq)
//					when (mvm.currentSeq) {
//						Sequence.JAW_LEFT_TILT, Sequence.JAW_RIGHT_TILT -> {
//							startVideoRecording {
//								mvm.tempStaticJo = null
//								// 좌표 초기화
//								Log.v("녹화 완료", "좌표값: ${mvm.tempCoordinateJA.length()}")
//								mvm.coordinatesJA.put(mvm.videoTempCoordinatesJA)
//								mvm.videoTempCoordinatesJA = JSONArray()
//								Log.v("동적 저장완료", "${mvm.coordinatesJA.length()}, 현재 seq: ${mvm.currentSeq}")
//
//								viewModel.setSeqFinishedFlag(true)
//								updateUI()
//								setCameraRecordEndAnimation()
//							}
//						}
//
//						else -> {
//							playSound(R.raw.camera_shutter)
//							lifecycleScope.launch {
//								captureImage(mvm.currentSeq)
//								customResult?.let { resultBundleToJson(it, step = mvm.currentSeq) }
//								viewModel.setSeqFinishedFlag(true)
//								updateUI()
//							}
//						}
//					}
//				}
//			}
//		}
//	} //  ------! 카운트 다운 끝 !-------
//
//	private val requestPermissionLauncher = registerForActivityResult(
//		ActivityResultContracts.RequestMultiplePermissions()
//	) { permissions ->
//		if (permissions.all { it.value }) {
//
//			backgroundExecutor.execute {
//				if (flh.isClose()) {
//					flh.setupFaceLandmarker()
//				}
//			}
//		} else {
//			// 권한이 거부됨
//			Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
//		}
//	}
//
//	private fun showToast(msg: String?) {
//		runOnUiThread {
//			Toast.makeText(this@NormalCameraActivity, msg, Toast.LENGTH_SHORT).show()
//		}
//	}
//
//	override fun onResume() {
//		super.onResume()
//		Log.v("오버라이딩", "onResume 시작 ")
//		if (ivm.isFinishInput.value == true) {
//			if (!hasPermissions(this)) {
//				requestPermissionLauncher.launch(getRequiredPermissions())
//			}
//		}
//	}
//
//	override fun onPause() {
//		super.onPause()
//	}
//
//	override fun onDestroy() {
//		super.onDestroy()
//		// 2️⃣ 모든 리소스 완전 해제 (안전망)
//		releaseCamera()
//		releasePose()
//		releaseFace()
//		// ---------# 메모리 정리 #---------
//		System.gc()
//		Runtime.getRuntime().gc()
//	}
//	private val ACTION_USB_PERMISSION = "com.tangoplus.facebeautyexpert.USB_PERMISSION"
//
//	override fun onCreate(savedInstanceState: Bundle?) {
//		super.onCreate(savedInstanceState)
//		enableEdgeToEdge()
//		bd = ActivityNormalCameraBinding.inflate(layoutInflater)
//		WindowCompat.setDecorFitsSystemWindows(window, false)
//
//		setContentView(bd.root)
//		setTitle("Stream-Color")
//		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//
//
//		mvm.initMeasure.observe(this) {
//			if (it) {
//				initSettings()
//				mvm.initMeasure.value = false
//			}
//		}
//
//		prefsUtil = PreferenceUtility(this@NormalCameraActivity)
//		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
//			override fun handleOnBackPressed() {
//				showGoMainDialog(this@NormalCameraActivity)
//			}
//		})
//
//		bd.btnNCShooting.setOnSingleClickListener {
//			if (bd.btnNCShooting.text == "결과보기") {
//				// ---------------# 데이터 갈무리해서 gallery DialogFragment로 넘기기 #-----------------
//				// 각각의 static에 사용자 정보 넣기
//
//				// GUEST일 경우 초기화
//				if (ivm.birthdayValue.value == "날짜 선택") {
//					ivm.birthdayValue.value = ""
//				}
//
//				val faceUser = FaceUser(
//					name = ivm.nameValue.value,
//					mobile = ivm.mobileValue.value,
//					uuid = mvm.currentUUID,
//					birthday = ivm.birthdayValue.value,
//					nationality = ivm.nationalityValue.value,
//					userGender = ivm.genderValue.value
//				)
//
//				lifecycleScope.launch(Dispatchers.IO) {
//					val fd = FaceDatabase.getDatabase(this@NormalCameraActivity)
//					fDao = fd.faceDao()
//
//					val existedUserSn = fDao.getExistUserSn(mvm.currentUUID)
//					if (existedUserSn == null) {
//						val userSn = fDao.insertUser(faceUser).toInt()
//						withContext(Dispatchers.Main) {
//							mvm.currentUserSn = userSn
//						}
//					} else {
//						withContext(Dispatchers.Main) {
//							mvm.currentUserSn = existedUserSn
//						}
//					}
//					Log.v("userSn설정잘됐는지", "is init? = ${mvm.currentUserSn}")
//					val allSeqs = mvm.allSeqs
//					val goalIndex = mvm.allSeqs.size
//					val staticJos = mutableListOf<JSONObject>()
//					for (i in 0 until goalIndex) {
//						val staticJSON = setJo(mvm.staticJA.getJSONObject(i), i, allSeqs[i])
//						staticJos.add(staticJSON)
//					}
//					// 받아온 user_sn으로 수정
//					val now = getCurrentDateTime()
//					val transStatics = staticJos.map {
//						it.toFaceStatic().copy(
//							user_sn = mvm.currentUserSn,
//							reg_date = now
//						)
//					}
//					// 부여 받은 user_sn넣기
//					for (static in transStatics) {
//						fDao.insertStatic(static)
//					}
//
//					// data
//					val mergedJOBeforeFileName = mutableListOf<JSONObject>()
//					Log.v("mvm.plrJA", "${mvm.plrJA.length()}")
//					for (i in 0 until staticJos.size) {
//						val currentJO = staticJos[i]
//						val mergedJSON = JSONObject().apply {
//							// face bow 가 있을 경우 넣기
//							put("data", JSONObject(transStatics[i].toJsonString()))
//
//							if (i == staticJos.size - 1 && currentJO.getInt("seq") == 9) {
//								put("face_landmark", mvm.hsvFLCoordinatesJA)
//								put("pose_landmark", mvm.hsvPLCoordinatesJA)
//								put("bow_landmark", mvm.resultTargetFs)
//							} else {
//								put("face_landmark", mvm.coordinatesJA.getJSONArray(i))
//								put("pose_landmark", mvm.plrJA.getJSONArray(i))
//							}
//						}
//						mergedJOBeforeFileName.add(mergedJSON)
//						Log.v("json저장 전", "seq: $i")
//						val jsonPath = saveJsonToStorage(
//							mergedJSON,
//							if (i == staticJos.size - 1 && currentJO.getInt("seq") == 9) {
//								mvm.hsvMediaFileName.replace(".jpg", "").replace(".mp4", "")
//							} else {
//								mvm.mediaFileNames[i].replace(".jpg", "").replace(".mp4", "")
//							}
//						)
//						mergedJOBeforeFileName[i].put("json_file_name", jsonPath)
//						mvm.mergedJA.put(mergedJOBeforeFileName[i])
//					}
//
//					Log.v("mvm.mergedJson", "${mvm.mergedJA.length()}")
//
//					withContext(Dispatchers.Main) {
//						val lastTempServerSn = prefsUtil.getLastTempServerSn()
//						// prefs에서 마지막 sn을 +1 하기
//						prefsUtil.setNextTempServerSn()
//
//						Log.v(
//							"스태틱스Sn",
//							"$lastTempServerSn, ${mvm.currentUserSn}: ${transStatics.map { it.user_sn }}"
//						)
//						viewModel.comparisonDoubleItem = null
//						val intent = Intent(this@NormalCameraActivity, MainActivity::class.java)
//						intent.putExtra("isMeasureFinish", true)
//						intent.putExtra("serverSn", lastTempServerSn)
//						startActivity(intent)
//						finishAffinity()
//					}
//				}
//			} else {
//				if (!isCountDown) {
//					startTimer()
//				}
//			}
//		}
//
//		bd.btnNCPrevious.setOnSingleClickListener {
//			showPreviousDialog(this@NormalCameraActivity, { setPreviousStep() })
//		}
//
//		bd.tvNCGoGallery.setOnSingleClickListener {
//			val intent = Intent(this@NormalCameraActivity, MainActivity::class.java)
//			startActivity(intent)
//			finishAffinity()
//		}
//
//		bd.tvNCRetry.setOnSingleClickListener {
//			showReStartDialog(this@NormalCameraActivity, { initSettings() })
//		}
//
//		bd.tvNCCameraDirection.setOnSingleClickListener {
//			switchCamera()
//		}
//		bd.tvNCGyro.setOnSingleClickListener {
//			val gyro = GyroDialogFragment()
//			gyro.show(supportFragmentManager, "gyroDialogFragment")
//		}
//	}
//
//	private fun switchCamera() {
//		cameraProvider?.unbindAll()
//
//		cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
//			CameraSelector.LENS_FACING_BACK
//		} else {
//			CameraSelector.LENS_FACING_FRONT
//		}
//		bindCameraUseCases()
//	}
//
//	private fun bindCameraUseCases() {
//		// CameraProvider
//		val cameraProvider = cameraProvider
//			?: throw IllegalStateException("Camera initialization failed.")
//
//		val cameraSelector =
//			CameraSelector.Builder().requireLensFacing(cameraFacing).build()
//		bd.viewFinder.doOnLayout {
////            bd.viewFinder.viewTreeObserver.removeOnGlobalLayoutListener(this)
//			preview = Preview.Builder().setTargetResolution(Size(1920, 1080))
//				.setTargetRotation(bd.viewFinder.display.rotation)
//				.build()
//			imageAnalyzer =
//				ImageAnalysis.Builder().setTargetResolution(Size(1920, 1080))
//					.setTargetRotation(bd.viewFinder.display.rotation)
//					.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//					.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
//					.build()
//					// The analyzer can then be assigned to the instance
//					.also {
//						it.setAnalyzer(backgroundExecutor) { image ->
//							detectFace(image)
//						}
//					}
//			imageCapture = ImageCapture.Builder()
//				.setTargetResolution(Size(1920, 1080))
//				.setTargetRotation(bd.viewFinder.display.rotation)
//				.build()
//			videoCapture = VideoCapture.withOutput(
//				Recorder.Builder()
//					.setQualitySelector(QualitySelector.from(Quality.HD))
//					.build()
//			)
//			cameraProvider.unbindAll()
//
//			try {
//				camera = cameraProvider.bindToLifecycle(
//					this@NormalCameraActivity,
//					cameraSelector,
//					preview,
//					imageAnalyzer,
//					imageCapture,
//					videoCapture
//				)
//				preview?.surfaceProvider = bd.viewFinder.surfaceProvider
//			} catch (exc: Exception) {
//				Log.e(TAG, "Use case binding failed", exc)
//			}
//		}
//	}
//
//	private fun detectFace(imageProxy: ImageProxy) {
//		flh.detectLiveStream(
//			imageProxy = imageProxy,
//			isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
//		)
//		if (startRecording && customResult != null) {
//			resultBundleToJson(customResult!!, step = mvm.currentSeq)
//		}
//	}
//
//	override fun onConfigurationChanged(newConfig: Configuration) {
//		super.onConfigurationChanged(newConfig)
//		imageAnalyzer?.targetRotation =
//			bd.viewFinder.display.rotation
//	}
//
	override fun onError(error: String, errorCode: Int) {
//		runOnUiThread {
//			Log.e("faceError", "$errorCode, $error")
////            Toast.makeText(this@CameraActivity, error, Toast.LENGTH_SHORT).show()
//			faceBlendshapesResultAdapter.updateResults(null)
//			faceBlendshapesResultAdapter.notifyDataSetChanged()
//
//			if (errorCode == FaceLandmarkerHelper.GPU_ERROR) {
//				Log.e("GPU_ERROR", "$errorCode, FaceLandmarkerHelper GPU_ERROR")
//			}
//		}
	}
//
//	// 녹화 시작과 종료
//	private var mouseClosedTime: Long? = null
//	private var recordingStartTime: Long? = null
	override fun onResults(
		resultBundle: FaceLandmarkerHelper.ResultBundle
	) {
//        Log.v(TAG, "✅ onResults 호출됨! 스레드: ${Thread.currentThread().name}")
//        Log.v("결과가 나오긴함?", "onResult is started. ${resultBundle.result.faceLandmarks()[0].size}")

//		runOnUiThread {
//			customResult = FaceLandmarkAdapter.toCustomFaceLandmarkResult(resultBundle.result)
//			if (customResult != null) {
//				// depthCamera Stream 좌우반전 대응 - 얼굴 랜드마크 좌우 반전
//				bd.overlay.setResults(
//					customResult!!,
//					resultBundle.inputImageWidth,
//					resultBundle.inputImageHeight,
//					OverlayView.RunningMode.LIVE_STREAM,
//
//					)
//			}
//
//			if (inputWidth == 0 && inputHeight == 0) {
//				inputWidth = resultBundle.inputImageWidth
//				inputHeight = resultBundle.inputImageHeight
//			}
//
////            var isFaceCenter = false
//			// 263 -362 구간의 좌표값을 판단해서 scale을 가변설정해줘야함
//			val landmarks = customResult?.landmarks
//
//
//			if (startRecording && customResult != null) {
//				resultBundleToJson(customResult!!, step = mvm.currentSeq)
//			}
//
//			if (!landmarks.isNullOrEmpty()) {
////                isFaceCenter = isFaceInCenter(landmarks[6].x, landmarks[6].y)
//				val point1 = landmarks.getOrNull(468)
//				val point2 = landmarks.getOrNull(473)
//
//				if (point1 != null && point2 != null) {
//					val dx = point1.x - point2.x
//					val dy = point1.y - point2.y
//					val distance = sqrt(dx * dx + dy * dy)
//					// 이 distance는 0~1 사이의 상대 거리입니다. 예: 0.05 = 화면 너비의 5%
//					// 여기서 원하는 scale값을 거리 기반으로 정하면 됩니다.
//					//                    val scale = yourScalingFunction(distance)
//					//                    Log.v("거리", "$distance")
//					// 0.085 ~ 0.09 일 때 32f
//					// 0.0
//					// 3.7
//					val scale = calculateScaleFromPart(distance)
//					//                    Log.v("스케일설정", "$distance $scale")
//					setScaleFactor(scale)
//				}
//
//				val leftEye = landmarks[33]
//				val rightEye = landmarks[263]
//				val middleEye = landmarks[6]
//
//				val noseTip = landmarks[1]     // 코끝
//				val leftEarPoint = landmarks[234] // 왼쪽 귓바퀴 근처
//				val rightEarPoint = landmarks[454] // 오른쪽 귓바퀴 근처
//
//				val leftEyeDistance =
//					getNormalizedDistance(Pair(leftEye.x, leftEye.y), Pair(middleEye.x, middleEye.y))
//				val rightEyeDistance =
//					getNormalizedDistance(Pair(rightEye.x, rightEye.y), Pair(middleEye.x, middleEye.y))
//				val eyeDistanceGap = abs(leftEyeDistance - rightEyeDistance)
//				// horizon state 설정
//				val horizontalLineVector = calculateAngle(
//					leftEarPoint.x,
//					leftEarPoint.y,
//					noseTip.x,
//					noseTip.y,
//					rightEarPoint.x,
//					rightEarPoint.y
//				)
//				val horizonBoolean = when (mvm.currentSeq) {
//					Sequence.COMFORT, Sequence.NECK_EXTENTION -> true
//					Sequence.JAW_OPENING -> horizontalLineVector in 120f..190f // 개구 상태
//					else -> horizontalLineVector in 115f..180f
//				}
//
//				bd.overlay.setHorizon(horizonBoolean)
//				//                Log.v("얼굴 중앙", "$isFaceCenter")
//				//                Log.v("라인벡터", "코: ${faceLandmarks[0].x()}, ${faceLandmarks[0].y()}, 왼쪽눈: ${faceLandmarks[33].x()}, ${faceLandmarks[33].x()}, 오른쪽 눈: ${faceLandmarks[263].x()}, ${faceLandmarks[263].x()}")
//
//				// verti state 설정
//				val vertiBoolean = when (mvm.currentSeq) {
//					Sequence.COMFORT, Sequence.NECK_EXTENTION, Sequence.SIDE_LEFT, Sequence.SIDE_RIGHT -> true // 5 목폄 상태일때만
//					else -> if (eyeDistanceGap < 0.35f) true else false
//				}
//				val eyeSlope = calculateSlope(leftEye.x, leftEye.y, rightEye.x, rightEye.y)
//				// 눈 가로 선 (얼굴이 360도 방향을 x, y 방향 회전)
////                Log.v("슬롭봐보기", "$eyeSlope")
//				val eyeParallel = when (mvm.currentSeq) {
//					Sequence.COMFORT, Sequence.NECK_EXTENTION, Sequence.SIDE_LEFT, Sequence.SIDE_RIGHT -> true
//					else -> (eyeSlope in 167.5f..187.5f) || (eyeSlope in -187.5f..-167.5f)
//				}
//				val vertiMediator = vertiBoolean && eyeParallel
//
//				bd.overlay.setVerti(vertiMediator)
//
//				// 영상 녹화 시 체크 하기
//				if (startRecording) {
//					val topLips = landmarks[13].y
//					val bottomLips = landmarks[14].y
//					val lipThreshold = 0.01f // 실험적으로 조정 필요
//					val mouseClosed = abs(topLips - bottomLips) < lipThreshold
//					val currentTime = System.currentTimeMillis()
//
//					// 녹화 시작 시간 기록 (최초 1회만)
//					if (recordingStartTime == null) {
//						recordingStartTime = currentTime
//					}
//
//					// 녹화 시작 후 2초가 지났는지 확인
//					val recordingElapsed = currentTime - (recordingStartTime ?: 0L)
//					val canDetectMouthClose = recordingElapsed >= 2000 // 2초
//
//					Log.v("입술감지", "녹화경과: ${recordingElapsed}ms, 감지가능: $canDetectMouthClose, 입다물음: $mouseClosed")
//
//					if (canDetectMouthClose && mouseClosed) {
//						if (mouseClosedTime == null) {
//							mouseClosedTime = currentTime
//							Log.v("입술감지", "입 다물기 시작")
//						} else {
//							val elapsed = currentTime - (mouseClosedTime ?: 0L)
//							Log.v("입술감지", "입 다문 시간: ${elapsed}ms")
//							if (elapsed >= 700) { // 0.7초
//								Log.v("입술감지", "0.7초 입 다물고 있어서 녹화 중단")
//								stopVideoRecording()
//
//								// 녹화 중단 후 시간 초기화
//								recordingStartTime = null
//								mouseClosedTime = null
//							}
//						}
//					} else {
//						// 입이 열리거나 아직 2초가 안 지났으면 타이머 리셋
//						if (mouseClosedTime != null) {
//							Log.v("입술감지", "입 열림 - 타이머 리셋")
//						}
//						mouseClosedTime = null
//					}
//				}
//
//			}
//
////            Log.v("인풋이미지", "${resultBundle.inputImageHeight}, ${resultBundle.inputImageWidth}")
////            Log.v("results", "${resultBundle.result.faceLandmarks().map { it.size }}")
//			// Force a redraw
//			bd.overlay.invalidate()
//
//			if (ivm.isFinishInput.value == true && resultBundle.result.faceLandmarks()
//					.isNotEmpty() && !viewModel.getSeqFinishedFlag()
//			) { // && isFaceCenter
//				// 애니메이션 제거 flag
//				if (!viewModel.getGuideTextFlag()) {
//					bounceAnimator?.cancel()
//					bounceAnimator = null
//					bd.tvNCSeqGuide.animate().cancel()
//					bd.tvNCSeqGuide.clearAnimation()
//					bd.tvNCSeqGuide.translationY = 0f
//					bd.tvNCSeqGuide.text = "안녕하세요! 화면에 잘 보이게 위치해 주시고\n편한 상태로 입을 다물고 정면을 응시해주세요"
//					viewModel.setGuideTextFlag(true)
//				}
//
//				// guideText 움직이기 flag
//				if (!viewModel.getTextAnimationFlag()) {
//					Handler(Looper.getMainLooper()).postDelayed({
//						val isPortrait = if (getScreenOrientation().contains("portrait")) true else false
//
//						animateTextViewToTopLeft(
//							bd.clNCSeqGuide,
//							bd.tvNCSeqGuide,
//							if (isPortrait) 0.05f else 0.1f,
//							0.05f
//						)
//					}, 2000)
//					bd.llNCbtn.visibility = View.VISIBLE
//
//					// animation flag에다가 애니메이션
//					if (mvm.currentSeq in listOf(
//							Sequence.RESTING,
//							Sequence.OCCLUSAL,
//							Sequence.JAW_LEFT_TILT,
//							Sequence.JAW_RIGHT_TILT,
//							Sequence.JAW_OPENING,
//							Sequence.NECK_EXTENTION
//						)
//					) {
//						viewModel.setTextAnimationFlag(true)
//					}
//				}
//			}
//		}
	}
//
//
//	// -------------------------# 타이머, UI업데이트, 이미지캡처, 결과처리 #-----------------------------
//	private fun hideViews() {
//		bd.clNCCountDown.visibility = View.INVISIBLE
//
//		when (mvm.currentSeq) {
//			Sequence.JAW_LEFT_TILT, Sequence.JAW_RIGHT_TILT -> startCameraRecordAnimation()
//			else -> startCameraShutterAnimation()
//		}
//	}
//
//	private fun startCameraRecordAnimation() {
//		bd.cvNCRecordMark.visibility = View.VISIBLE
//		bd.tvNCGoGallery.visibility = View.GONE
//		bd.tvNCRetry.visibility = View.GONE
//		bd.tvNCSeqCount.visibility = View.GONE
//		bd.tvNCGyro.visibility = View.GONE
//	}
//
//	private fun setCameraRecordEndAnimation() {
//		bd.cvNCRecordMark.visibility = View.GONE
//		bd.tvNCGoGallery.visibility = View.VISIBLE
//		bd.tvNCRetry.visibility = View.VISIBLE
//		bd.tvNCSeqCount.visibility = View.VISIBLE
//		bd.tvNCGyro.visibility = View.VISIBLE
//	}
//
//	private fun startCameraShutterAnimation() {
//		// 첫 번째 애니메이션: VISIBLE로 만들고 alpha를 0에서 1로
//		bd.flNCCameraShutter.visibility = View.VISIBLE
//		bd.flNCCameraShutter.alpha = 0f
//		Handler(Looper.getMainLooper()).postDelayed({
//			val fadeIn = ObjectAnimator.ofFloat(bd.flNCCameraShutter, "alpha", 0f, 1f).apply {
//				duration = 100 // 0.1초
//				interpolator = AccelerateDecelerateInterpolator()
//			}
//			// 두 번째 애니메이션: alpha를 1에서 0으로 만들고, 끝난 후 INVISIBLE로 설정
//			val fadeOut = ObjectAnimator.ofFloat(bd.flNCCameraShutter, "alpha", 1f, 0f).apply {
//				duration = 100 // 0.1초
//				interpolator = AccelerateDecelerateInterpolator()
//				addListener(object : AnimatorListenerAdapter() {
//					override fun onAnimationEnd(animation: Animator) {
//						bd.flNCCameraShutter.visibility = View.INVISIBLE
//					}
//				})
//			}
//			fadeIn.start()
//			fadeIn.addListener(object : AnimatorListenerAdapter() {
//				override fun onAnimationEnd(animation: Animator) {
//					fadeOut.start()
//				}
//			})
//		}, 0)
//	}
//
//	private fun setGuideAnimation(seq: Sequence, isEnded: Boolean) {
//		bd.btnNCShooting.isEnabled = false
//		bd.btnNCPrevious.isEnabled = false
//		bd.tvNCSeqGuide.apply {
//			clearAnimation()
//			text = if (isEnded) {
//				"수고하셨습니다\n버튼을 눌러 결과를 확인해보세요"
//			} else {
//				getGuideText(seq)
//			}
//			visibility = View.VISIBLE
//			animation = animationSet
//		}
//	}
//
//	private val guideHandler = Handler(Looper.getMainLooper())
//	private val guideRunnables = mutableListOf<Runnable>()
//
//	private fun setJawTiltGuideText(step: Sequence) {
//		val guideText = when (step) {
//			Sequence.JAW_LEFT_TILT -> "미소지은 상태로 턱을 왼쪽으로 끝까지 이동해주세요\n그리고 입을 최대한 벌려주세요\n입을 다물어 측정을 완료하세요"
//			Sequence.JAW_RIGHT_TILT -> "미소지은 상태로 턱을 오른쪽으로 끝까지 이동해주세요\n그리고 입을 최대한 벌려주세요\n입을 다물어 측정을 완료하세요"
//			else -> return
//		}
//
//		cancelJawTiltGuideText()
//		val lines = guideText.split("\n")
//		var accumulatedDelay = 0L
//
//		lines.forEachIndexed { index, line ->
//			val currentDelay = if (index == 0) 1750L else 1400L
//			val runnable = Runnable {
//				bd.tvNCSeqGuide.text = line
//			}
//
//			// 등록
//			guideRunnables.add(runnable)
//			guideHandler.postDelayed(runnable, accumulatedDelay)
//			accumulatedDelay += currentDelay
//		}
//	}
//
//	fun cancelJawTiltGuideText() {
//		guideRunnables.forEach { runnable -> guideHandler.removeCallbacks(runnable) }
//		guideRunnables.clear()
//	}
//
////    private fun isFaceInCenter(glabellaX: Float, glabellaY: Float): Boolean {
////        val targetRect = RectF(0.35f, 0.175f, 0.65f, 0.525f)
////        return targetRect.contains(glabellaX, glabellaY)
////    }
//
//	// ------! 타이머 control 시작 !------
//	private fun startTimer() {
//		// 시작 버튼 후 시작
//		bd.btnNCShooting.isEnabled = false
//		bd.btnNCPrevious.isEnabled = false
//		bd.tvNCGoGallery.isEnabled = false
//		Log.v("startTimer", "${mvm.currentSeq} / ${mvm.allSeqs.size}")
//		mCountDown.start()
//		viewModel.setCountDownFlag(true)
//		// ------! 타이머 control 끝 !------
//	}
//
//	private fun updateUI() {
//		val isProcessing = mvm.moveToNextSeq()
//		cancelJawTiltGuideText()
//		if (!isProcessing) {
//			setGuideAnimation(mvm.currentSeq, true)
//			bd.clNCCountDown.visibility = View.GONE
//			bd.btnNCShooting.apply {
//				text = "결과보기"
//				isEnabled = true
//			}
//
//			mCountDown.cancel()
//			Log.v("몇단계?", "Max repeats reached, stopping the loop")
//		} else {
//			setGuideAnimation(mvm.currentSeq, false)
//			val seqCount = "${mvm.getSeqIndex() + 1} / ${mvm.allSeqs.size}"
//			bd.tvNCSeqCount.text = seqCount
//
//			viewModel.setCountDownFlag(false)
//
//			if (mvm.currentSeq == Sequence.SIDE_LEFT) {
//				bd.fdgv.triggerOffAnimationIfNeeded()
//				Handler(Looper.getMainLooper()).postDelayed({ bd.fdgv.visibility = View.INVISIBLE }, 800)
//				bd.ivNCFrame.visibility = View.VISIBLE
//				startFadeIn(bd.ivNCFrame)
//			} else if (mvm.currentSeq == Sequence.RESTING) {
//				bd.fdgv.visibility = View.VISIBLE
//				bd.fdgv.triggerIntroAnimationIfNeeded()
//
//				Handler(Looper.getMainLooper()).postDelayed(
//					{ bd.ivNCFrame.visibility = View.INVISIBLE },
//					800
//				)
//				startFadeOut(bd.ivNCFrame)
//			}
//			// 7, 8 일 경우 바로 넣기
//
//			when (mvm.currentSeq) {
//				Sequence.SIDE_LEFT -> bd.ivNCFrame.setImageDrawable(
//					ContextCompat.getDrawable(
//						this@NormalCameraActivity,
//						R.drawable.drawable_seq_7
//					)
//				)
//
//				Sequence.SIDE_RIGHT -> bd.ivNCFrame.setImageDrawable(
//					ContextCompat.getDrawable(
//						this@NormalCameraActivity,
//						R.drawable.drawable_seq_8
//					)
//				)
//
//				else -> {}
//			}
//		}
//
//		bd.btnNCShooting.apply {
//			isEnabled = true
//		}
//		bd.btnNCPrevious.apply {
//			visibility = View.VISIBLE
//			isEnabled = true
//		}
//		Handler(Looper.getMainLooper()).postDelayed({
//			viewModel.setSeqFinishedFlag(false)
//		}, 1500)
//		isCountDown = false
//		bd.tvNCGoGallery.isEnabled = true
//		Log.v("포즈랜드마크", "${mvm.plrJA.length()}")
//
//	}
//
//	private fun setPreviousStep() {
//		mvm.moveToPreviousSeq()
//
//		// -----------# frame -> fdgv 변경하기 #--------
//		if (mvm.currentSeq == Sequence.NECK_EXTENTION) {
//			bd.fdgv.visibility = View.VISIBLE
//			bd.fdgv.triggerIntroAnimationIfNeeded()
//			startFadeIn(bd.ivNCFrame)
//			Handler(Looper.getMainLooper()).postDelayed({ bd.ivNCFrame.visibility = View.INVISIBLE }, 800)
//		} else if (mvm.currentSeq == Sequence.COMFORT) {
//			Handler(Looper.getMainLooper()).postDelayed({ bd.fdgv.visibility = View.INVISIBLE }, 800)
//			bd.fdgv.triggerOffAnimationIfNeeded()
//
//			bd.ivNCFrame.visibility = View.VISIBLE
//			startFadeIn(bd.ivNCFrame)
//
//		}
//		// 7, 8 일 경우 바로 넣기
//		when (mvm.currentSeq) {
//			Sequence.SIDE_LEFT -> bd.ivNCFrame.setImageDrawable(
//				ContextCompat.getDrawable(
//					this@NormalCameraActivity,
//					R.drawable.drawable_seq_7
//				)
//			)
//
//			Sequence.SIDE_RIGHT -> bd.ivNCFrame.setImageDrawable(
//				ContextCompat.getDrawable(
//					this@NormalCameraActivity,
//					R.drawable.drawable_seq_8
//				)
//			)
//
//			Sequence.COMFORT -> bd.ivNCFrame.setImageDrawable(
//				ContextCompat.getDrawable(
//					this@NormalCameraActivity,
//					R.drawable.drawable_seq_0
//				)
//			)
//
//			else -> {}
//		}
//
//		bd.clNCCountDown.visibility = View.GONE
//		mCountDown.cancel()
//		setGuideAnimation(mvm.currentSeq, false)
//
//		bd.btnNCShooting.apply {
//			text = "촬영"
//			isEnabled = true
//		}
//		bd.btnNCPrevious.apply {
//			visibility = if (mvm.currentSeq == Sequence.COMFORT) {
//				View.INVISIBLE
//			} else {
//				View.VISIBLE
//			}
//			isEnabled = true
//		}
//
//		val seqCount = "${mvm.getSeqIndex() + 1} / ${mvm.allSeqs.size}"
//		bd.tvNCSeqCount.text = seqCount
//		viewModel.setCountDownFlag(false)
//
//		val ci = mvm.getSeqIndex()
//		when (mvm.currentSeq) {
//			Sequence.JAW_LEFT_TILT, Sequence.JAW_RIGHT_TILT -> {
//				mvm.tempStaticJo = null
//				mvm.videoTempCoordinatesJA = JSONArray()
//			}
//
//			else -> {
//				mvm.tempStaticJo = null
//				mvm.staticJA.remove(ci)
//			}
//		}
//		mvm.coordinatesJA.remove(ci)
//		mvm.plrJA.remove(ci)
//		mvm.mediaFileNames.removeAt(ci)
//		mvm.staticJA.remove(ci)
//
//		Log.v("좌표삭제", "${mvm.coordinatesJA.length()}, 현재 삭제된 seq: ${mvm.currentSeq}")
//		isCountDown = false
//		bd.tvNCGoGallery.isEnabled = true
//	}
//
//	private var permissionDialog: AlertDialog? = null
//	private fun showPermissionExplanationDialog() {
//		if (permissionDialog?.isShowing == true) return  // 이미 다이얼로그가 떠 있으면 return
//
//		permissionDialog = AlertDialog.Builder(this)
//			.setTitle("알림")
//			.setMessage("측정을 위해서는 사진 및 갤러리 권한을 모두 허용해야 합니다.")
//			.setPositiveButton("설정으로 이동") { _, _ ->
//				startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
//					data = Uri.fromParts("package", packageName, null)
//				})
//			}
//			.setNegativeButton("취소") { dialog, _ ->
//				dialog.dismiss()
//			}
//			.show()
//		permissionDialog?.let { dialog ->
//			dialog.getButton(AlertDialog.BUTTON_POSITIVE)
//				.setTextColor(ContextCompat.getColor(this, R.color.black))
//			dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
//				.setTextColor(ContextCompat.getColor(this, R.color.black))
//		}
//	}


//	private suspend fun captureImage(seq: Sequence): Boolean {
//
//		val name = getFileName(seq)
//		return withContext(Dispatchers.IO) {
//			suspendCancellableCoroutine { continuation ->
//				val tempFile = File.createTempFile("temp_capture", ".jpg", cacheDir)
//				val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
//				val imageCapture = imageCapture
//				imageCapture.takePicture(
//					outputOptions,
//					ContextCompat.getMainExecutor(this@NormalCameraActivity),
//					object : ImageCapture.OnImageSavedCallback {
//						override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
//							val tempUri = Uri.fromFile(tempFile)
//							saveMediaToStorage(this@NormalCameraActivity, tempUri, name, true)
//							continuation.resume(true)
//						}
//
//						override fun onError(exception: ImageCaptureException) {
//							Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
//							if (tempFile.exists()) tempFile.delete()
//							continuation.resume(false)
//						}
//					}
//				)
//			}
//		}
//	}
//
//	fun saveJsonToStorage(jsonObject: JSONObject, fileName: String): String? {
//		val resolver = contentResolver
//		Log.v("jsonO저장", "$jsonObject")
//		val contentValues = ContentValues().apply {
//			put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.json")
//			put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
//			put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Documents/TangoPlus")
//		}
//		return try {
//			val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
//			uri?.let {
//				resolver.openOutputStream(it)?.use { outputStream ->
//					outputStream.write(jsonObject.toString().toByteArray())
//					outputStream.flush()
//				}
//				// 저장 성공
//				Log.d("JSON_SAVE", "JSON 파일이 성공적으로 저장되었습니다: $fileName.json, uri: $uri")
//				"$fileName.json"
//			}
//		} catch (e: Exception) {
//			Log.e("JSON_SAVE", "JSON 파일 저장 실패", e)
//			null
//		}
//	}
//
//	fun saveMediaToStorage(context: Context, uri: Uri, fileName: String, isImage: Boolean) {
//		try {
//			val extension = if (isImage) ".jpg" else ".mp4"
//			val file = File(context.cacheDir, "$fileName$extension")
//
//			if (isImage) {
//				val contentValues = ContentValues().apply {
//					put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName$extension")
//					put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
//					put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TangoPlus")
//				}
//				val inputStream = context.contentResolver.openInputStream(uri)
//				val tempFile = File.createTempFile("tempImage", null, context.cacheDir)
//				inputStream?.use { input ->
//					tempFile.outputStream().use { output ->
//						input.copyTo(output)
//					}
//				}
//
//				// EXIF 데이터 읽기
//				val exif = ExifInterface(tempFile.absolutePath)
//				val orientation =
//					exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
////                Log.d("ExifDebug", "Exif Orientation: $orientation")
//				// 비트맵 디코딩
//				val options = BitmapFactory.Options().apply {
//					inJustDecodeBounds = true
//				}
//				BitmapFactory.decodeFile(tempFile.absolutePath, options)
//
//				val sourceWidth = options.outWidth
//				val sourceHeight = options.outHeight
//
//				val targetWidth = 1920
//				val targetHeight = 1080
//
//				// 이미지 스케일 계산
//				val widthRatio = sourceWidth.toFloat() / targetWidth
//				val heightRatio = sourceHeight.toFloat() / targetHeight
//				val scale = maxOf(widthRatio, heightRatio)
//
//				options.inJustDecodeBounds = false
//				options.inSampleSize = scale.toInt()
//
//				var bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, options)
//				val matrix = Matrix()
//
//				// 회전 적용
//				when (orientation) {
//					ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)   // 시계 방향 90도
//					ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f) // 시계 방향 180도
//					ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f) // 시계 방향 270도
//				}
//
//				// ★★★ 좌우반전 ★★★
//				matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
//
//				// 모든 변환을 한번에 적용
//				bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
//
//				// 스케일 조정
//				bitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
//
//				// Pictures/TangoBeauty에 저장
//				val imageUri = context.contentResolver.insert(
//					MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//					contentValues
//				)
//
//				// --------------------------# 목젖 위치를 잡기 위한 detectAsync #-------------------------
////            Log.v("포즈 들가자", "${seqStep.value}")
//				val resultBundle = poseLandmarker.detectImage(bitmap)
//				if (resultBundle?.results?.first()?.landmarks()?.isNotEmpty() == true) {
//					val plr = resultBundle.results.first().landmarks()[0]
//
//					mvm.tempPlrJA = JSONArray()
//					plr.forEachIndexed { index, _ ->
//						val swapIndex = if (index >= 7 && index % 2 == 0) index - 1 // 짝수인 경우 뒤의 홀수 인덱스로 교체
//						else if (index >= 7) index + 1 // 홀수인 경우 앞의 짝수 인덱스로 교체
//						else index // 7 미만인 경우 그대로 사용
//						val targetLandmark = plr[swapIndex]
//
//						val scaledX = calculateScreenX(bd.overlay.width.toFloat(), targetLandmark.x())
//						val scaledY = calculateScreenY(bd.overlay.height.toFloat(), targetLandmark.y())
//
//						val jo = JSONObject().apply {
//							put("index", index)
//							put("isActive", true)
//							put("sx", scaledX)
//							put("sy", scaledY)
//							put("wx", targetLandmark.x())
//							put("wy", targetLandmark.y())
//							put("wz", targetLandmark.z())
//						}
//						mvm.tempPlrJA.put(jo)
//						mvm.currentPlrCoordinate.add(Pair(targetLandmark.x(), targetLandmark.y())) // 33 개가 다 담김
//					}
//					// 약 30개 랜드마크 JO를 저장한 seq 1개의 JA를 전체 seq를 담을 JA에 넣기
//					if (mvm.currentSeq == Sequence.BOW) {
//						mvm.hsvPLCoordinatesJA.copyFrom(mvm.tempPlrJA)
//					} else {
//						mvm.plrJA.put(mvm.tempPlrJA)
//					}
//				} else {
//					showToast("poselandmark를 찾을 수 없습니다.")
//					mvm.plrJA.put(JSONArray())
//				}
//				// -------------------------# 목젖 위치를 잡기 위한 detectAsync 끝 #-----------------------
//				imageUri?.let { imageURI ->
//					context.contentResolver.openOutputStream(imageURI)?.use { outputStream ->
//						bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
//						outputStream.flush()
//					}
//					Log.d("SaveMedia", "Image saved to Pictures: ${mvm.currentSeq} ->  $fileName$extension")
//				} ?: run {
//					Log.e("SaveMedia", "Failed to create image URI")
//				}
//				if (mvm.currentSeq == Sequence.BOW) {
//					mvm.hsvMediaFileName = "$fileName$extension"
//				} else {
//					mvm.mediaFileNames.add("$fileName$extension")
//				}
//
//				Log.d("SaveMedia", "seqStep: ${mvm.currentSeq} vmFileName: ${mvm.mediaFileNames}")
//
//				// 임시 파일과 비트맵 정리
//				tempFile.delete()
//				bitmap.recycle()
//
//			} else {
//				val videoCollection =
//					MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
//				val contentValues = ContentValues().apply {
//					put(MediaStore.Video.Media.DISPLAY_NAME, fileName)  // ex: 4-20251020145123.mp4
//					put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
//					put(
//						MediaStore.Video.Media.RELATIVE_PATH,
//						"${Environment.DIRECTORY_MOVIES}/TangoPlus"
//					) // Movies/TangoPlus/
//					put(MediaStore.Video.Media.IS_PENDING, 1)
//				}
//
//				val videoUri = context.contentResolver.insert(videoCollection, contentValues)
//
//				if (videoUri != null) {
//					context.contentResolver.openInputStream(uri)?.use { input ->
//						context.contentResolver.openOutputStream(videoUri)?.use { output ->
//							input.copyTo(output)
//
//							contentValues.clear()
//							contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
//							context.contentResolver.update(videoUri, contentValues, null, null)
//							Log.v("영상작업완료 후 plr넣기", "$fileName$extension")
//
//							mvm.mediaFileNames.add("$fileName$extension")
//							// 빈 jsonArray를 넣어서 index 갯수 맞추기
//							mvm.plrJA.put(JSONArray())
//							Log.v("영상작업완료 후 plr넣기", "${mvm.plrJA.length()}")
//						}
//					}
//				}
//			}
//		} catch (e: IndexOutOfBoundsException) {
//			Log.e("SaveMediaIndex", "${e.message}")
//		} catch (e: IllegalArgumentException) {
//			Log.e("SaveMediaIllegal", "${e.message}")
//		} catch (e: IllegalStateException) {
//			Log.e("SaveMediaIllegal", "${e.message}")
//		} catch (e: NullPointerException) {
//			Log.e("SaveMediaNull", "${e.message}")
//		} catch (e: java.lang.Exception) {
//			Log.e("SaveMediaException", "${e.message}")
//		}
//	}
//
//
//	// --------------------------------- # pose landmark # -----------------------------------------
//	private val poseLandmarkListener = object : PoseLandmarkerHelper.LandmarkerListener {
//		override fun onError(error: String, errorCode: Int) {
//			runOnUiThread {
//				Log.e("PoseLandmark", "Pose detection error: $error $errorCode")
//				Toast.makeText(
//					this@NormalCameraActivity,
//					"Pose detection error: $error",
//					Toast.LENGTH_SHORT
//				).show()
//			}
//		}
//
//		override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {}
//	}
//
//	private fun stopVideoRecording() {
//		recordingJob?.cancel()
//		isRecording = false
//
//		val curRecording = recording
//		if (curRecording != null) {
//			curRecording.stop()
//			recording = null
//		}
//		releaseVideoRecording()
//	}
//
//	private fun startRecordingMaxTimer() {
//		lifecycleScope.launch(Dispatchers.Main) {
//			delay(6000) // 6초가 max 영상 길이
//			stopVideoRecording()
//		}
//	}
//
//	private fun startVideoRecording(callback: () -> Unit) {
//		recordingJob = CoroutineScope(Dispatchers.IO).launch {
//			val videoCapture = this@NormalCameraActivity.videoCapture
//			val curRecording = recording
//			if (curRecording != null) {
//				curRecording.stop()
//				recording = null
//				return@launch
//			}
//
//			val outputFile = File(cacheDir, "$timestamp.mp4")
//			val fileOutputOptions = FileOutputOptions.Builder(outputFile).build()
//
//			// Start recording video without audio
//			recording = videoCapture.output.prepareRecording(this@NormalCameraActivity, fileOutputOptions)
////                .withAudioEnabled()
//				.start(ContextCompat.getMainExecutor(this@NormalCameraActivity)) { recordEvent ->
//					when (recordEvent) {
//						is VideoRecordEvent.Start -> {
//							CoroutineScope(Dispatchers.Main).launch {
//								startRecording = true
//								startRecordingMaxTimer()
//								Log.v("startRecording", "startRecording $startRecording")
//								Handler(Looper.getMainLooper()).postDelayed({
////                                    setRecordGuideAnimation()
//								}, 1000)
//							}
//						}
//
//						is VideoRecordEvent.Finalize -> {
//							startRecording = false
//							if (!recordEvent.hasError()) {
//								val inputPath = outputFile.absolutePath
//								val outputPath = File(cacheDir, "mirrored_video.mp4").absolutePath
//
//								lifecycleScope.launch(Dispatchers.IO) {
//									val file = File(inputPath)
//									if (waitForFileReady(file)) {
//										Log.v("FileWait", "파일 준비 완료 후 flipVideoHorizontally 실행")
//										flipVideoHorizontally(inputPath, outputPath) { success ->
//											if (success) {
//												saveMediaToStorage(
//													this@NormalCameraActivity,
//													Uri.fromFile(File(outputPath)),
//													getFileName(mvm.currentSeq),
//													false
//												)
//												callback()
//											} else {
//												Log.e("FlipVideo", "⚠️ 미러 효과 적용 실패")
//											}
//										}
//									} else {
//										Log.e("FileWait", "❌ 파일이 완성되지 않아 flipVideoHorizontally 건너뜀")
//									}
//								}
//							} else {
//								Log.v("startRecording", "finish Recording but has error")
//								CoroutineScope(Dispatchers.Main).launch {
//									recording?.close()
//									recording = null
//									Log.e(TAG, "Video capture ends with error: ${recordEvent.error}")
//								}
//							}
//						}
//					}
//				}
//		}
//	}
//
//	private fun releaseVideoRecording() {
//		// 1) 타이머/잡 정리 (이미 있는 변수만 사용)
//		try {
//			recordingJob?.cancel()
//		} catch (_: Throwable) {
//		}
//		recordingJob = null
//		isRecording = false
//		startRecording = false
//		recordingStartTime = null
//		// 2) 메인 루퍼에 걸어둔 지연 콜백들 제거
//		//    (Start에서 Handler(Looper.getMainLooper()).postDelayed(...) 사용)
//		try {
//			Handler(Looper.getMainLooper()).removeCallbacksAndMessages(null)
//		} catch (_: Throwable) {
//		}
//
//		// 3) CameraX Recording 자원 해제
//		recording?.let { r ->
//			runCatching { r.stop() }
//			runCatching { r.close() }
//		}
//		recording = null
//		// 5) 변환 중 남은 임시 파일들 중 ‘빈 파일/부분 파일’만 정리 (안전 범위)
//		runCatching {
//			cacheDir?.listFiles()
//				?.filter { it.isFile && it.extension.equals("mp4", ignoreCase = true) }
//				// 0바이트 또는 아주 작은 부분 파일(예: 64KB 미만)만 제거
//				?.filter { it.length() in 0..65535 }
//				?.forEach { runCatching { it.delete() } }
//		}
//	}
//
//	@UnstableApi
//	private fun flipVideo(
//		inputPath: String,
//		outputPath: String,
//		onSuccess: (File) -> Unit,
//		onError: (String) -> Unit
//	) {
//		// 입력 비디오 URI 생성
//		val inputUri = Uri.fromFile(File(inputPath))
//		val mediaItem = MediaItem.fromUri(inputUri)
//
//		val flipEffect = ScaleAndRotateTransformation.Builder()
//			.setScale(-1f, 1f)
//			.build()
//		val effects = Effects(
//			listOf(ChannelMixingAudioProcessor()),
//			listOf(flipEffect)
//		)
//		val editedMediaItem = EditedMediaItem.Builder(mediaItem)
//			.setEffects(effects)
//			.build()
//
//		val transformer = Transformer.Builder(this@NormalCameraActivity)
//			.setVideoMimeType(MimeTypes.VIDEO_H264)
//			.addListener(object : Transformer.Listener {
//				override fun onCompleted(composition: Composition, exportResult: ExportResult) {
//					super.onCompleted(composition, exportResult)
//					onSuccess(File(outputPath))
//				}
//
//				override fun onError(
//					composition: Composition,
//					exportResult: ExportResult,
//					exportException: ExportException
//				) {
//					super.onError(composition, exportResult, exportException)
//					onError(exportException.message ?: "Unknown error")
//				}
//			}).build()
//		transformer.start(editedMediaItem, outputPath)
//	}
//
//	@OptIn(UnstableApi::class)
//	private fun flipVideoHorizontally(
//		inputPath: String,
//		outputPath: String,
//		callback: (Boolean) -> Unit
//	) {
//		CoroutineScope(Dispatchers.Main).launch {
//			flipVideo(
//				inputPath = inputPath,
//				outputPath = outputPath,
//				onSuccess = { file ->
//					callback(true)
//					println("비디오 플립 성공: ${file.path}")
//				},
//				onError = { errorMessage ->
//					callback(false)
//					println("비디오 크롭 실패: $errorMessage")
//				},
//			)
//		}
//	}
//
//	// --------------------------------------# resultBundle #----------------------------------
//	private val leftCheeks = listOf(197, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152)
//	private val rightCheeks = listOf(197, 234, 93, 132, 58, 172, 136, 150, 149, 176, 148, 152)
//	private val leftCheekBone = listOf(346, 347, 330, 425, 411, 352)
//	private val rightCheekBone = listOf(117, 118, 101, 205, 187, 123)
//
//	fun resultBundleToJson(
//		resultBundle: FaceLandmarkResult,
//		flr3D: List<Point3D> = listOf(),
//		step: Sequence
//	) {
//		if (scaleFactorX == null && scaleFactorY == null) {
//
//			if (inputWidth != 0 && inputHeight != 0) {
//				scaleFactorX = (bd.overlay.width / inputWidth).toFloat()
//				scaleFactorY = (bd.overlay.height / inputHeight).toFloat()
//			} else {
//				scaleFactorX = 1f
//				scaleFactorY = 1f
//			}
////            Log.v("ScreenSettings", "scaleFactor(x, y): ($scaleFactorX, $scaleFactorY), imageSize(width, height): (${latestResult?.inputImageWidth}, ${latestResult?.inputImageHeight})")
//		}
//
////        Log.v("resultBundleToJson", "${resultBundle.landmarks.size}")
//		if (resultBundle.landmarks.isNotEmpty()) {
//			val flr = resultBundle.landmarks
//
//			/* 1. 키오스크 -> pose좌표까지 좌우 반전 -> 8이 왼쪽 어깨
//			*  2. 일치시키기 위해 모바일 pose좌표 -> 동일
//			*  3. 실제 기울기를 계산하는 곳만 반대로 들어감 -> 7 이 왼쪽 어깨
//			*  4. 이유는 기울기와 거리 계산하는 곳의 모든 인자를 변경해줘야 하기 때문에.
//			*  5. 값 계산에서는 왼쪽의 기울기는 second 혹은 1번 index 임.
//			*  6. 이를 일치 시키려면? -> mvm에 들어가는 인자들을 거울모드로 변경 -> 값들은 원상복귀
//			* */
////            Log.v("현재렌즈위치", "$cameraFacing == 정면${CameraSelector.LENS_FACING_FRONT}, 후면${CameraSelector.LENS_FACING_BACK}")
//			// 비우기
//			mvm.tempCoordinateJA = JSONArray()
//			when (mvm.currentSeq) {
//				Sequence.JAW_LEFT_TILT, Sequence.JAW_RIGHT_TILT -> {
//					flr.forEachIndexed { index, faceLandmark ->
//						val scaledX = calculateScreenX(bd.overlay.width.toFloat(), faceLandmark.x)
//						val scaledY = calculateScreenY(bd.overlay.height.toFloat(), faceLandmark.y)
//						val x = flr3D.getOrNull(index)?.x ?: 0f
//						val y = flr3D.getOrNull(index)?.y ?: 0f
//						val z = flr3D.getOrNull(index)?.z ?: 0f
//
//						val ja = JSONArray().apply {
//							put(index)
//							put(scaledX)
//							put(scaledY)
//							put(x)
//							put(y)
//							put(z)
//						}
//						// 이 tempCoordinateJA는 영상 녹화가 종료됐을 때, timer가 끝나는 곳에서 저장후 초기화
//						mvm.tempCoordinateJA.put(ja)
////                        // 절대좌표
////                        mvm.currentCoordinate.add(Pair(scaledX, scaledY))
//						// 상대좌표
//						mvm.relativeCoordinate.add(Pair(faceLandmark.x, faceLandmark.y))
//					}
//					// seq 1개의 좌표를 담고 tempCoordinates는 초기화
//					mvm.videoTempCoordinatesJA.put(mvm.tempCoordinateJA)
//				}
//
//				else -> {
//					mvm.currentCoordinate.clear()
//					mvm.relativeCoordinate.clear()
//					mvm.current3DCoordinate.clear()
//
//					flr.forEachIndexed { index, faceLandmark ->
//						val scaledX = calculateScreenX(bd.overlay.width.toFloat(), faceLandmark.x)
//						val scaledY = calculateScreenY(bd.overlay.height.toFloat(), faceLandmark.y)
//						val x = flr3D.getOrNull(index)?.x ?: 0f
//						val y = flr3D.getOrNull(index)?.y ?: 0f
//						val z = flr3D.getOrNull(index)?.z ?: 0f
//						if (mvm.currentSeq == Sequence.JAW_OPENING && index in listOf(13, 14)) Log.v(
//							"xyz보기",
//							"$index: ($x $y $z), ${flr3D.size}"
//						)
//						val ja = JSONArray().apply {
//							put(index)
//							put(scaledX)
//							put(scaledY)
//							put(x)
//							put(y)
//							put(z)
//						}
//						mvm.tempCoordinateJA.put(ja)
//						// 절대좌표
//						mvm.currentCoordinate.add(Pair(scaledX, scaledY))
//						// 상대좌표
//						mvm.relativeCoordinate.add(
//							Pair(
//								faceLandmark.x,
//								faceLandmark.y
//							)
//						)
//
//					}
//					// seq 1개의 좌표를 담고 tempCoordinates는 초기화
//					mvm.coordinatesJA.put(mvm.tempCoordinateJA)
//				}
//			}
//			// 각도나 여러가지 계산할 때는 거울 모드가 아님. 그렇기 때문에 랜드마크 사진에서 오른쪽에 있는 landmark 265번은 좌측 눈매 끝의 피부 점인거임.
//			// 그렇기 때문에 각도 계산할 때 left에다가 second를 넣고, right에 first를 넣음.
//
//
//			// ---------# 편한 상태 #----------
//			val vmFlr = mvm.relativeCoordinate
//			when (step) {
//				Sequence.COMFORT -> {
//					val eyeBrowAngle =
//						calculateSlope(vmFlr[107].first, vmFlr[107].second, vmFlr[336].first, vmFlr[336].second)
//
//					// 468: 실제 오른쪽 눈  473: 실제 왼쪽 눈
//					val eyeAngle =
//						calculateSlope(vmFlr[468].first, vmFlr[468].second, vmFlr[473].first, vmFlr[473].second)
//
//					val correctionValue = 180f - eyeAngle // 180
//					// 입술끝 왼: 291 오: 61
//					val tipOfLipsAngle =
//						calculateSlope(vmFlr[61].first, vmFlr[61].second, vmFlr[291].first, vmFlr[291].second)
//					val correctTipOfLipsAngle = correctingValue(tipOfLipsAngle, correctionValue)
//					// 턱
//					val tipOfChinsAngle =
//						calculateSlope(vmFlr[148].first, vmFlr[148].second, vmFlr[377].first, vmFlr[377].second)
//					val correctTipOfChinsAngle = correctingValue(tipOfChinsAngle, correctionValue)
//
//					Log.v("각도보기", "$eyeAngle $correctionValue $tipOfLipsAngle -> $correctTipOfLipsAngle")
//
//					val canthusOralAngle = Pair(
//						calculateSlope(vmFlr[33].first, vmFlr[33].second, vmFlr[61].first, vmFlr[61].second),
//						calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[263].first, vmFlr[263].second)
//					)
//					val nasalWingLipsAngle = Pair(
//						calculateSlope(vmFlr[64].first, vmFlr[64].second, vmFlr[61].first, vmFlr[61].second),
//						calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[294].first, vmFlr[294].second)
//					)
//					val leftCheeksPoint = leftCheeks.map { mvm.currentCoordinate[it] }
//					val rightCheeksPoint = rightCheeks.map { mvm.currentCoordinate[it] }
//					val cheeksExtents = Pair(
//						calculatePolygonArea(leftCheeksPoint),
//						calculatePolygonArea(rightCheeksPoint)
//					)
//
//					val sideHeadAngle = Pair(
//						calculateAngle(
//							vmFlr[156].first,
//							vmFlr[156].second,
//							vmFlr[143].first,
//							vmFlr[143].second,
//							vmFlr[111].first,
//							vmFlr[111].second
//						),
//						calculateAngle(
//							vmFlr[383].first,
//							vmFlr[383].second,
//							vmFlr[372].first,
//							vmFlr[372].second,
//							vmFlr[340].first,
//							vmFlr[340].second
//						)
//					)
//					val leftCheekBonePoint = leftCheekBone.map { mvm.currentCoordinate[it] }
//					val rightCheekBonePoint = rightCheekBone.map { mvm.currentCoordinate[it] }
//					val cheekboneExtents = Pair(
//						calculatePolygonArea(leftCheekBonePoint),
//						calculatePolygonArea(rightCheekBonePoint)
//					)
//					val plr = mvm.currentPlrCoordinate
//					val midShoulder =
//						Pair((plr[11].first + plr[12].first) / 2, (plr[11].second + plr[12].second) / 2)
//
//					val neckVerticalAngle =
//						calculateSlope(midShoulder.first, midShoulder.second, vmFlr[1].first, vmFlr[1].second)
//					val faceVerticalAngle =
//						calculateSlope(vmFlr[152].first, vmFlr[152].second, vmFlr[10].first, vmFlr[10].second)
//
//					// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6
//					val tempStatic = JSONObject().apply {
//						put("comfort_eye_horizontal_angle", eyeAngle)
//						put("comfort_eyebrow_horizontal_angle", eyeBrowAngle)
//						put("comfort_tip_of_lips_horizontal_angle", correctTipOfLipsAngle)
//						put("comfort_tip_of_chin_horizontal_angle", correctTipOfChinsAngle)
//						put("comfort_canthus_oral_left_vertical_angle", canthusOralAngle.second)
//						put("comfort_canthus_oral_right_vertical_angle", canthusOralAngle.first)
//						put("comfort_nasal_wing_tip_of_lips_left_vertical_angle", nasalWingLipsAngle.second)
//						put("comfort_nasal_wing_tip_of_lips_right_vertical_angle", nasalWingLipsAngle.first)
//						put("comfort_side_head_left_angle", sideHeadAngle.second)
//						put("comfort_side_head_right_angle", sideHeadAngle.first)
//						put("comfort_left_cheeks_extent", cheeksExtents.second)
//						put("comfort_right_cheeks_extent", cheeksExtents.first)
//						put("comfort_left_cheekbone_extent", cheekboneExtents.second)
//						put("comfort_right_cheekbone_extent", cheekboneExtents.first)
//						put("comfort_neck_vertical_angle", neckVerticalAngle)
//						put("comfort_face_vertical_angle", faceVerticalAngle)
//					}
//					mvm.staticJA.put(tempStatic)
//				}
//
//				Sequence.RESTING -> {
//					val eyeBrowAngle =
//						calculateSlope(vmFlr[107].first, vmFlr[107].second, vmFlr[336].first, vmFlr[336].second)
//
//					// 468: 실제 오른쪽 눈  473: 실제 왼쪽 눈
//					val eyeAngle =
//						calculateSlope(vmFlr[468].first, vmFlr[468].second, vmFlr[473].first, vmFlr[473].second)
//					val correctionValue = 180f - eyeAngle
//					// 입술끝 왼: 291 오: 61
//					val tipOfLipsAngle =
//						calculateSlope(vmFlr[61].first, vmFlr[61].second, vmFlr[291].first, vmFlr[291].second)
//					val correctTipOfLipsAngle = correctingValue(tipOfLipsAngle, correctionValue)
//					// 턱
//					val tipOfChinsAngle =
//						calculateSlope(vmFlr[148].first, vmFlr[148].second, vmFlr[377].first, vmFlr[377].second)
//					val correctTipOfChinsAngle = correctingValue(tipOfChinsAngle, correctionValue)
//
//					val canthusOralAngle = Pair(
//						calculateSlope(vmFlr[33].first, vmFlr[33].second, vmFlr[61].first, vmFlr[61].second),
//						calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[263].first, vmFlr[263].second)
//					)
//					val nasalWingLipsAngle = Pair(
//						calculateSlope(vmFlr[64].first, vmFlr[64].second, vmFlr[61].first, vmFlr[61].second),
//						calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[294].first, vmFlr[294].second)
//					)
//					val leftCheeksPoint = leftCheeks.map { mvm.currentCoordinate[it] }
//					val rightCheeksPoint = rightCheeks.map { mvm.currentCoordinate[it] }
//					val cheeksExtents = Pair(
//						calculatePolygonArea(leftCheeksPoint),
//						calculatePolygonArea(rightCheeksPoint)
//					)
//
//					val sideHeadAngle = Pair(
//						calculateAngle(
//							vmFlr[156].first,
//							vmFlr[156].second,
//							vmFlr[143].first,
//							vmFlr[143].second,
//							vmFlr[111].first,
//							vmFlr[111].second
//						),
//						calculateAngle(
//							vmFlr[383].first,
//							vmFlr[383].second,
//							vmFlr[372].first,
//							vmFlr[372].second,
//							vmFlr[340].first,
//							vmFlr[340].second
//						)
//					)
//					val leftCheekBonePoint = leftCheekBone.map { mvm.currentCoordinate[it] }
//					val rightCheekBonePoint = rightCheekBone.map { mvm.currentCoordinate[it] }
//					val cheekboneExtents = Pair(
//						calculatePolygonArea(leftCheekBonePoint),
//						calculatePolygonArea(rightCheekBonePoint)
//					)
//
//					// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6
//					val tempStatic = JSONObject().apply {
//						put("resting_eye_horizontal_angle", eyeAngle)
//						put("resting_eyebrow_horizontal_angle", eyeBrowAngle)
//						put("resting_tip_of_lips_horizontal_angle", correctTipOfLipsAngle)
//						put("resting_tip_of_chin_horizontal_angle", correctTipOfChinsAngle)
//						put("resting_canthus_oral_left_vertical_angle", canthusOralAngle.second)
//						put("resting_canthus_oral_right_vertical_angle", canthusOralAngle.first)
//						put("resting_nasal_wing_tip_of_lips_left_vertical_angle", nasalWingLipsAngle.second)
//						put("resting_nasal_wing_tip_of_lips_right_vertical_angle", nasalWingLipsAngle.first)
//						put("resting_side_head_left_angle", sideHeadAngle.second)
//						put("resting_side_head_right_angle", sideHeadAngle.first)
//						put("resting_left_cheeks_extent", cheeksExtents.second)
//						put("resting_right_cheeks_extent", cheeksExtents.first)
//						put("resting_left_cheekbone_extent", cheekboneExtents.second)
//						put("resting_right_cheekbone_extent", cheekboneExtents.first)
//					}
//					mvm.staticJA.put(tempStatic)
//					Log.v(
//						"정면 각도들",
//						"cheeksExtent: $cheeksExtents, eyeAngle: $eyeAngle eyebrowAngle: $eyeBrowAngle tipOfLipsAngle: $tipOfLipsAngle chinAngle: $correctTipOfChinsAngle canthusOralAngle: $canthusOralAngle nasalWingLipsAngle:$nasalWingLipsAngle, "
//					)
////                    Log.v("제이슨", "${}")
//				}
//
//				Sequence.OCCLUSAL -> {
//					val eyeBrowAngle =
//						calculateSlope(vmFlr[107].first, vmFlr[107].second, vmFlr[336].first, vmFlr[336].second)
//
//					// 468: 실제 오른쪽 눈  473: 실제 왼쪽 눈
//					val eyeAngle =
//						calculateSlope(vmFlr[468].first, vmFlr[468].second, vmFlr[473].first, vmFlr[473].second)
//					val correctionValue = 180f - eyeAngle
//					// 입술끝 왼: 291 오: 61
//					val tipOfLipsAngle =
//						calculateSlope(vmFlr[61].first, vmFlr[61].second, vmFlr[291].first, vmFlr[291].second)
//					val correctTipOfLipsAngle = correctingValue(tipOfLipsAngle, correctionValue)
//					// 턱
//					val tipOfChinsAngle =
//						calculateSlope(vmFlr[148].first, vmFlr[148].second, vmFlr[377].first, vmFlr[377].second)
//					val correctTipOfChinsAngle = correctingValue(tipOfChinsAngle, correctionValue)
//					val canthusOralAngle = Pair(
//						calculateSlope(vmFlr[33].first, vmFlr[33].second, vmFlr[61].first, vmFlr[61].second),
//						calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[263].first, vmFlr[263].second)
//					)
//					val nasalWingLipsAngle = Pair(
//						calculateSlope(vmFlr[64].first, vmFlr[64].second, vmFlr[61].first, vmFlr[61].second),
//						calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[294].first, vmFlr[294].second)
//					)
//					val leftCheeksPoint = leftCheeks.map { mvm.currentCoordinate[it] }
//					val rightCheeksPoint = rightCheeks.map { mvm.currentCoordinate[it] }
//					val cheeksExtents = Pair(
//						calculatePolygonArea(leftCheeksPoint),
//						calculatePolygonArea(rightCheeksPoint)
//					)
//
//					val sideHeadAngle = Pair(
//						calculateAngle(
//							vmFlr[156].first,
//							vmFlr[156].second,
//							vmFlr[143].first,
//							vmFlr[143].second,
//							vmFlr[111].first,
//							vmFlr[111].second
//						),
//						calculateAngle(
//							vmFlr[383].first,
//							vmFlr[383].second,
//							vmFlr[372].first,
//							vmFlr[372].second,
//							vmFlr[340].first,
//							vmFlr[340].second
//						)
//					)
//					val leftCheekBonePoint = leftCheekBone.map { mvm.currentCoordinate[it] }
//					val rightCheekBonePoint = rightCheekBone.map { mvm.currentCoordinate[it] }
//					val cheekboneExtents = Pair(
//						calculatePolygonArea(leftCheekBonePoint),
//						calculatePolygonArea(rightCheekBonePoint)
//					)
//
//					// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6
//					val tempStatic = JSONObject().apply {
//						put("occlusal_eye_horizontal_angle", eyeAngle)
//						put("occlusal_eyebrow_horizontal_angle", eyeBrowAngle)
//						put("occlusal_tip_of_lips_horizontal_angle", correctTipOfLipsAngle)
//						put("occlusal_tip_of_chin_horizontal_angle", correctTipOfChinsAngle)
//						put("occlusal_canthus_oral_left_vertical_angle", canthusOralAngle.second)
//						put("occlusal_canthus_oral_right_vertical_angle", canthusOralAngle.first)
//						put("occlusal_nasal_wing_tip_of_lips_left_vertical_angle", nasalWingLipsAngle.second)
//						put("occlusal_nasal_wing_tip_of_lips_right_vertical_angle", nasalWingLipsAngle.first)
//						put("occlusal_side_head_left_angle", sideHeadAngle.second)
//						put("occlusal_side_head_right_angle", sideHeadAngle.first)
//						put("occlusal_left_cheeks_extent", cheeksExtents.second)
//						put("occlusal_right_cheeks_extent", cheeksExtents.first)
//						put("occlusal_left_cheekbone_extent", cheekboneExtents.second)
//						put("occlusal_right_cheekbone_extent", cheekboneExtents.first)
//					}
//					mvm.staticJA.put(tempStatic)
//					Log.v(
//						"교합 각도들",
//						"eyeAngle: $eyeAngle eyebrowAngle: $eyeBrowAngle tipOfLipsAngle: $tipOfLipsAngle chinAngle: $correctTipOfChinsAngle canthusOralAngle: $canthusOralAngle nasalWingLipsAngle:$nasalWingLipsAngle, cheeksExtent: $cheeksExtents"
//					)
//				}
//
//				Sequence.JAW_LEFT_TILT -> {
//					if (mvm.tempStaticJo == null) {
//						val tiltNoseChinAngle =
//							calculateSlope(vmFlr[1].first, vmFlr[1].second, vmFlr[152].first, vmFlr[152].second)
//						val tiltTipOfLipsAngle =
//							calculateSlope(vmFlr[61].first, vmFlr[61].second, vmFlr[291].first, vmFlr[291].second)
//						val mandibularDistance = Pair(
//							getNormalizedDistance(
//								Pair(vmFlr[13].first, vmFlr[13].second),
//								Pair(vmFlr[58].first, vmFlr[58].second)
//							),
//							getNormalizedDistance(
//								Pair(vmFlr[13].first, vmFlr[13].second),
//								Pair(vmFlr[288].first, vmFlr[288].second)
//							)
//						)
//
//						// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6
//						val tempStatic = JSONObject().apply {
//							put("jaw_left_tilt_nose_chin_vertical_angle", tiltNoseChinAngle)
//							put("jaw_left_tilt_tip_of_lips_horizontal_angle", tiltTipOfLipsAngle)
//							put("jaw_left_tilt_left_mandibular_distance", mandibularDistance.second)
//							put("jaw_left_tilt_right_mandibular_distance", mandibularDistance.first)
//						}
//						// 측정 값 초기화
//						mvm.tempStaticJo = tempStatic
//						mvm.staticJA.put(mvm.tempStaticJo)
//						Log.v(
//							"왼쪽 턱 쏠림 각도들",
//							"noseChinAngle: $tiltNoseChinAngle tipOfLipsAngle: $tiltTipOfLipsAngle  mandibularDistance: $mandibularDistance"
//						)
//					}
//				}
//
//				Sequence.JAW_RIGHT_TILT -> {
//					if (mvm.tempStaticJo == null) {
//						val tiltNoseChinAngle =
//							calculateSlope(vmFlr[1].first, vmFlr[1].second, vmFlr[152].first, vmFlr[152].second)
//						val tiltTipOfLipsAngle =
//							calculateSlope(vmFlr[61].first, vmFlr[61].second, vmFlr[291].first, vmFlr[291].second)
//						val mandibularDistance = Pair(
//							getNormalizedDistance(
//								Pair(vmFlr[13].first, vmFlr[13].second),
//								Pair(vmFlr[58].first, vmFlr[58].second)
//							),
//							getNormalizedDistance(
//								Pair(vmFlr[13].first, vmFlr[13].second),
//								Pair(vmFlr[288].first, vmFlr[288].second)
//							)
//						)
//
//						// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6
//						val tempStatic = JSONObject().apply {
//							put("jaw_right_tilt_nose_chin_vertical_angle", tiltNoseChinAngle)
//							put("jaw_right_tilt_tip_of_lips_horizontal_angle", tiltTipOfLipsAngle)
//							put("jaw_right_tilt_left_mandibular_distance", mandibularDistance.second)
//							put("jaw_right_tilt_right_mandibular_distance", mandibularDistance.first)
//
//						}
//						mvm.tempStaticJo = tempStatic
//						mvm.staticJA.put(mvm.tempStaticJo)
//						Log.v(
//							"왼쪽 턱 쏠림 각도들",
//							"noseChinAngle: $tiltNoseChinAngle tipOfLipsAngle: $tiltTipOfLipsAngle  mandibularDistance: $mandibularDistance"
//						)
//					}
//				}
//
//				Sequence.JAW_OPENING -> {
//					val openingLipsDistance = getNormalizedDistance(
//						Pair(vmFlr[13].first, vmFlr[13].second),
//						Pair(vmFlr[14].first, vmFlr[14].second)
//					)
//					val openingLipsAngle = calculateSlope(
//						vmFlr[13].first,
//						vmFlr[13].second,
//						vmFlr[14].first,
//						vmFlr[14].second
//					) % 180f
//					val leftCheeksPoint = leftCheeks.map { mvm.currentCoordinate[it] }
//					val rightCheeksPoint = rightCheeks.map { mvm.currentCoordinate[it] }
//					val cheeksExtents = Pair(
//						calculatePolygonArea(leftCheeksPoint),
//						calculatePolygonArea(rightCheeksPoint)
//					)
//
//					// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6
//					val tempStatic = JSONObject().apply {
//						put("jaw_opening_lips_distance", openingLipsDistance)
//						put("jaw_opening_lips_vertical_angle", openingLipsAngle)
//						put("jaw_opening_left_cheeks_extent", cheeksExtents.first)
//						put("jaw_opening_right_cheeks_extent", cheeksExtents.second)
//					}
//					mvm.staticJA.put(tempStatic)
//					Log.v(
//						"입 벌림 각도들",
//						"openingLipsDistance: $openingLipsDistance openingLipsAngle: $openingLipsAngle cheeksExtents: $cheeksExtents"
//					)
//
//				}
//
//				Sequence.NECK_EXTENTION -> {
//					val plr = mvm.currentPlrCoordinate
//					Log.v("plr봐보기", "$plr")
//					val shoulderHorizontalAngle =
//						calculateSlope(plr[11].first, plr[11].second, plr[12].first, plr[12].second)
//					val earHorizontalAngle =
//						calculateSlope(plr[7].first, plr[7].second, plr[8].first, plr[8].second)
//
//					val midShoulder =
//						Pair((plr[11].first + plr[12].first) / 2, (plr[11].second + plr[12].second) / 2)
//					val neckVerticalAngle =
//						calculateSlope(midShoulder.first, midShoulder.second, vmFlr[1].first, vmFlr[1].second)
//					val faceVerticalAngle =
//						calculateSlope(vmFlr[152].first, vmFlr[152].second, vmFlr[10].first, vmFlr[10].second)
//					val leftChinAngle = calculateAngle(
//						vmFlr[234].first,
//						vmFlr[234].second,
//						vmFlr[58].first,
//						vmFlr[58].second,
//						vmFlr[152].first,
//						vmFlr[152].second
//					)
//					val rightChinAngle = calculateAngle(
//						vmFlr[454].first,
//						vmFlr[454].second,
//						vmFlr[288].first,
//						vmFlr[288].second,
//						vmFlr[152].first,
//						vmFlr[152].second
//					)
//					val middleChinAngle = calculateAngle(
//						vmFlr[58].first,
//						vmFlr[58].second,
//						vmFlr[152].first,
//						vmFlr[152].second,
//						vmFlr[288].first,
//						vmFlr[288].second
//					)
//
//					// 양쪾을 벌렸을 때 7.2 안벌렸을 때 4.6
//					val tempStatic = JSONObject().apply {
//						put("neck_extention_shoulder_horizontal_angle", shoulderHorizontalAngle)
//						put("neck_extention_ear_horizontal_angle", earHorizontalAngle)
//						put("neck_extention_neck_vertical_angle", neckVerticalAngle)
//						put("neck_extention_face_vertical_angle", faceVerticalAngle)
//						put("neck_extention_left_mandibular_chin_angle", leftChinAngle)
//						put("neck_extention_right_mandibular_chin_angle", rightChinAngle)
//						put("neck_extention_middle_mandibular_chin_angle", middleChinAngle)
//
//					}
//					mvm.staticJA.put(tempStatic)
//					Log.v(
//						"고개 젖힘",
//						"shoulderHorizontalAngle: $shoulderHorizontalAngle earHorizontalAngle: $earHorizontalAngle  neckVerticalAngle: $neckVerticalAngle"
//					)
//				}
//
//				Sequence.SIDE_LEFT -> {
//					val plr = mvm.currentPlrCoordinate
//
//					val noseShoulderAngle = normalizeAngle90(
//						calculateSlope(
//							plr[0].first,
//							plr[0].second,
//							plr[11].first,
//							plr[11].second
//						)
//					) % 180
//					val earShoulderAngle = normalizeAngle90(
//						calculateSlope(
//							plr[7].first,
//							plr[7].second,
//							plr[11].first,
//							plr[11].second
//						)
//					) % 180
//
//					val tempStatic = JSONObject().apply {
//						put("side_left_shoulder_nose_vertical_angle", noseShoulderAngle)
//						put("side_left_shoulder_ear_vertical_angle", earShoulderAngle)
//					}
//					mvm.staticJA.put(tempStatic)
//					Log.v("좌측", "noseShoulderAngle: $noseShoulderAngle earShoulderAngle: $earShoulderAngle")
//				}
//
//				Sequence.SIDE_RIGHT -> {
//					val plr = mvm.currentPlrCoordinate
//
//					val noseShoulderAngle = normalizeAngle90(
//						calculateSlope(
//							plr[0].first,
//							plr[0].second,
//							plr[12].first,
//							plr[12].second
//						)
//					) % 180
//					val earShoulderAngle = normalizeAngle90(
//						calculateSlope(
//							plr[8].first,
//							plr[8].second,
//							plr[12].first,
//							plr[12].second
//						)
//					) % 180
//
//					val tempStatic = JSONObject().apply {
//						put("side_right_shoulder_nose_vertical_angle", noseShoulderAngle)
//						put("side_right_shoulder_ear_vertical_angle", earShoulderAngle)
//					}
//					mvm.staticJA.put(tempStatic)
//					Log.v(
//						"우측",
//						"staticJACount: ${mvm.staticJA.length()}/ noseShoulderAngle: $noseShoulderAngle earShoulderAngle: $earShoulderAngle"
//					)
//				}
//
//				Sequence.BOW -> {}
//			}
//		}
//	}

	// --------------------------------------# init #-----------------------------------------------
//	private fun initFace() {
//		try {
//			Log.d("이닛", "initializing FaceLandmarker resources...")
//			// 이제 this가 완전히 초기화된 상태
//			try {
//				if (!::backgroundExecutor.isInitialized || backgroundExecutor.isShutdown) {
//					backgroundExecutor = Executors.newSingleThreadExecutor()
//				}
//				backgroundExecutor.execute {
//					flh = FaceLandmarkerHelper(
//						context = this@NormalCameraActivity,
//						runningMode = RunningMode.LIVE_STREAM,
//						minFaceDetectionConfidence = viewModel.currentMinFaceDetectionConfidence,
//						minFaceTrackingConfidence = viewModel.currentMinFaceTrackingConfidence,
//						minFacePresenceConfidence = viewModel.currentMinFacePresenceConfidence,
//						maxNumFaces = viewModel.currentMaxFaces,
//						currentDelegate = viewModel.currentDelegate,
//						faceLandmarkerHelperListener = this
//					)
//				}
//				Log.d("PoseLandmarker", "Initialization successful!")
//			} catch (e: Exception) {
//				Log.e("PoseLandmarker", "Failed to initialize: ${e.message}", e)
//			}
//			Log.d("이닛", "PoseLandmarker initialized.")
//		} catch (e: Exception) {
//			Log.e("이닛", "Failed to initialze pose landmarker: ${e.message}", e)
//		}
//	}
//
//	private fun initPose() {
//		try {
//			Log.d("이닛", "initializing PoseLandmarker resources...")
//			// 이제 this가 완전히 초기화된 상태
//			try {
//				poseLandmarker = PoseLandmarkerHelper(
//					context = this, // 이제 this가 완전히 초기화된 상태
//					runningMode = RunningMode.IMAGE,
//					minPoseDetectionConfidence = PoseLandmarkerHelper.DEFAULT_POSE_DETECTION_CONFIDENCE,
//					minPoseTrackingConfidence = PoseLandmarkerHelper.DEFAULT_POSE_TRACKING_CONFIDENCE,
//					minPosePresenceConfidence = PoseLandmarkerHelper.DEFAULT_POSE_PRESENCE_CONFIDENCE,
//					currentDelegate = PoseLandmarkerHelper.DELEGATE_CPU,
//					poseLandmarkerHelperListener = poseLandmarkListener
//				)
//				Log.d("PoseLandmarker", "Initialization successful!")
//			} catch (e: Exception) {
//				Log.e("PoseLandmarker", "Failed to initialize: ${e.message}", e)
//			}
//			Log.d("이닛", "PoseLandmarker initialized.")
//		} catch (e: Exception) {
//			Log.e("이닛", "Failed to initialze pose landmarker: ${e.message}", e)
//		}
//	}
//
//
//	private fun initCamera() {
//		try {
//			requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
//
//			if (!::backgroundExecutor.isInitialized || backgroundExecutor.isShutdown) {
//				backgroundExecutor = Executors.newSingleThreadExecutor()
//			}
//			backgroundExecutor.execute {
//				flh = FaceLandmarkerHelper(
//					context = this@NormalCameraActivity,
//					runningMode = RunningMode.LIVE_STREAM,
//					minFaceDetectionConfidence = viewModel.currentMinFaceDetectionConfidence,
//					minFaceTrackingConfidence = viewModel.currentMinFaceTrackingConfidence,
//					minFacePresenceConfidence = viewModel.currentMinFacePresenceConfidence,
//					maxNumFaces = viewModel.currentMaxFaces,
//					currentDelegate = viewModel.currentDelegate,
//					faceLandmarkerHelperListener = this
//				)
//			}
//
//			val cameraProviderFuture =
//				ProcessCameraProvider.getInstance(this@NormalCameraActivity)
//			cameraProviderFuture.addListener(
//				{
//					// CameraProvider
//					cameraProvider = cameraProviderFuture.get()
//
//					// Build and bind the camera use cases
//					bindCameraUseCases()
//				}, ContextCompat.getMainExecutor(this@NormalCameraActivity)
//			)
//
//			Log.d("이닛", "Camera resources initialized.")
//		} catch (e: Exception) {
//			Log.e("이닛", "Failed to initialize camera resources: ${e.message}", e)
//		}
//	}
//
//	// -------------------------------------# release #---------------------------------------------
//	private fun releaseFace() {
//		try {
//			Log.d("릴리즈", "Releasing FaceLandmarker resources...")
//
//			// backgroundExecutor를 안전하게 정리
//			if (::backgroundExecutor.isInitialized && !backgroundExecutor.isShutdown) {
//				backgroundExecutor.shutdownNow()
//			}
//
//			// FaceLandmarkerHelper 해제
//			flh.clearFaceLandmarker()
//
//			Log.d("릴리즈", "FaceLandmarker released.")
//		} catch (e: Exception) {
//			Log.e("릴리즈", "Failed to release face landmarker: ${e.message}", e)
//		}
//	}
//
//	private fun releasePose() {
//		try {
//			Log.d("릴리즈", "Releasing PoseLandmarker resources...")
//			if (::poseLandmarker.isInitialized && !poseLandmarker.isClose()) {
//				poseLandmarker.clearPoseLandmarker()
//			}
//			Log.d("릴리즈", "PoseLandmarker released.")
//		} catch (e: Exception) {
//			Log.e("릴리즈", "Failed to release pose landmarker: ${e.message}", e)
//		}
//	}
//
//	override fun finish() {
//		// 방향을 UNSPECIFIED로 설정하여 자연스럽게 복원 ( 일반카메라의 강제 가로모드에서 리소스 A로그 방지 )
//		requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
//		super.finish()
//	}
//
//	private fun releaseCamera() {
//		try {
//			Log.d("릴리즈", "Releasing RGB camera resources...")
//			if (::backgroundExecutor.isInitialized) {
//				try {
//					backgroundExecutor.execute { flh.clearFaceLandmarker() }
//					backgroundExecutor.shutdown()
//					backgroundExecutor.awaitTermination(
//						Long.MAX_VALUE, TimeUnit.NANOSECONDS
//					)
//					Log.d("릴리즈", "BackgroundExecutor shut down successfully")
//				} catch (e: InterruptedException) {
//					backgroundExecutor.shutdownNow()
//					Thread.currentThread().interrupt()
//				}
//			}
//
//			if (imageAnalyzer != null) {
//				imageAnalyzer?.clearAnalyzer()
//				imageAnalyzer = null
//			}
//			if (::flh.isInitialized && !flh.isClose()) {
//				flh.clearFaceLandmarker()
//			}
//
//			// 100ms 후 해제
//			Handler(Looper.getMainLooper()).postDelayed({
//				cameraProvider?.unbindAll()
//				cameraProvider = null
//
//				Log.d("릴리즈", "Camera resources released.")
//			}, 500)
//
//		} catch (e: Exception) {
//			Log.e("릴리즈", "Failed to release camera resources: ${e.message}", e)
//		}
//
//		try {
//			Log.d("릴리즈", "Releasing PoseLandmark resources...")
//
//			if (::poseLandmarker.isInitialized && !poseLandmarker.isClose()) {
//				poseLandmarker.clearPoseLandmarker()
//			}
//		} catch (e: Exception) {
//			Log.e("릴리즈", "Failed to release camera resources: ${e.message}", e)
//		}
//	}
//
//	private fun initSettings() {
//
//		mvm.allSeqs = prefsUtil.getLastMeasureSeqs().sortedBy { it.ordinal }.toMutableList()
//		mvm.currentSeq = Sequence.COMFORT
//
//		bd.llNCbtn.apply {
//			visibility = View.INVISIBLE
//		}
//		val seqCount = "${mvm.getSeqIndex() + 1} / ${mvm.allSeqs.size}"
//
//		bd.tvNCSeqCount.text = seqCount
//		mvm.mediaFileNames.clear()
//		mvm.currentCoordinate = mutableListOf()
//		ivm.nameValue.value = ""
//		ivm.mobileValue.value = ""
//		ivm.isShownBtn = false
//		viewModel.comparisonDoubleItem = null
//
//		// userFragment에서 왔을 경우 이름과 휴대폰 번호 넣고, 설문지만 넣기
//		val currentUserItem = intent.getParcelableExtra<UserItem>("currentUserItem")
//		Log.v("currentUserItem", "is init? = $currentUserItem, ${currentUserItem == null}")
//		if (currentUserItem != null) {
//			ivm.mobileValue.value = currentUserItem.userMobile
//			ivm.nameValue.value = currentUserItem.userName
//			ivm.nationalityValue.value = currentUserItem.userNationality
//			ivm.birthdayValue.value = currentUserItem.userBirthday
//			ivm.genderValue.value = currentUserItem.userGender
//			mvm.currentUserSn = currentUserItem.userSn
//		}
//		val key = getString(R.string.kekeke)
//		mvm.currentUUID = generateCustomUUID(ivm.nameValue.value, ivm.mobileValue.value, key)
//
//
//		Log.v("가져온게 있나요", "Out: ${ivm.mobileValue.value}, ${ivm.nameValue.value}, ${mvm.currentUserSn}")
//		if (ivm.nameValue.value?.isNotEmpty() == true && ivm.mobileValue.value?.isNotEmpty() == true && mvm.currentUserSn != -1) {
//			val onBoardingDialog = OnBoardingDialogFragment()
//			onBoardingDialog.show(supportFragmentManager, "")
//		} else {
//			val userDialog = UserSetupDialogFragment()
//			userDialog.show(supportFragmentManager, "")
//		}
//
//		mvm.currentSeq = Sequence.COMFORT
//
//		// flat 초기화
//		viewModel.setSeqFinishedFlag(false)
//		viewModel.setGuideTextFlag(false)
//		viewModel.setCountDownFlag(false)
//		viewModel.setTextAnimationFlag(false)
//	}
//
//	private fun setJo(originJo: JSONObject, seqIndex: Int, seq: Sequence): JSONObject {
//		val seqInt = when (seq) {
//			Sequence.COMFORT -> 0
//			Sequence.RESTING -> 1
//			Sequence.OCCLUSAL -> 2
//			Sequence.JAW_LEFT_TILT -> 3
//			Sequence.JAW_RIGHT_TILT -> 4
//			Sequence.JAW_OPENING -> 5
//			Sequence.NECK_EXTENTION -> 6
//			Sequence.SIDE_LEFT -> 7
//			Sequence.SIDE_RIGHT -> 8
//			Sequence.BOW -> 9
//		}
//
//		val mediaFileName = if (seqInt == 9) {
//			mvm.hsvMediaFileName
//		} else {
//			mvm.mediaFileNames[seqIndex]
//		}
//
//		return originJo.apply {
//			put("temp_server_sn", prefsUtil.getLastTempServerSn())
//			put("media_file_name", mediaFileName)
//			put("json_file_name", mediaFileName.replace(".jpg", ".json").replace(".mp4", ".json"))
//			put("seq", seqInt)
//			put("uuid", mvm.currentUUID)
//			put("name", ivm.nameValue.value)
//			put("mobile", ivm.mobileValue.value)
//			put("scale_factor", getScaleFactor())
//			put("depth_result", false)
//		}
//	}

}