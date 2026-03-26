package com.tangoplus.tangoface.ui.measure

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.JsonWriter
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.camera.video.Recording
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.orbbec.obsensor.Config
import com.orbbec.obsensor.DepthFrame
import com.orbbec.obsensor.Device
import com.orbbec.obsensor.DeviceChangedCallback
import com.orbbec.obsensor.DeviceList
import com.orbbec.obsensor.HoleFillingFilter
import com.orbbec.obsensor.Pipeline
import com.orbbec.obsensor.property.DeviceProperty
import com.orbbec.obsensor.types.AlignMode
import com.orbbec.obsensor.types.CameraDistortion
import com.orbbec.obsensor.types.Format
import com.orbbec.obsensor.types.FrameType
import com.orbbec.obsensor.types.SensorType
import com.orbbec.obsensor.types.StreamType
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.data.db.FaceDao
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.data.db.FaceStatic.Companion.getCurrentDateTime
import com.tangoplus.tangoface.data.db.FaceUser
import com.tangoplus.tangoface.databinding.ActivityDepthCameraBinding
import com.tangoplus.tangoface.domain.opencv.HsvColorTracker
import com.tangoplus.tangoface.domain.util.AnimationUtility.animateTextViewToTopLeft
import com.tangoplus.tangoface.domain.util.AnimationUtility.startFadeIn
import com.tangoplus.tangoface.domain.util.AnimationUtility.startFadeOut
import com.tangoplus.tangoface.domain.util.BitmapUtility.getScreenOrientation
import com.tangoplus.tangoface.domain.util.FileUtility.getFileName
import com.tangoplus.tangoface.ui.view.OverlayView
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.util.MathHelpers.calculateScaleFromPart
import com.tangoplus.tangoface.domain.util.MathHelpers.calculateSlope
import com.tangoplus.tangoface.domain.util.SoundManager.playSound
import com.tangoplus.tangoface.domain.vision.face.FaceBlendshapesResultAdapter
import com.tangoplus.tangoface.domain.vision.face.FaceLandmarkerHelper
import com.tangoplus.tangoface.ui.main.MainViewModel
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
import com.tangoplus.tangoface.domain.util.FileUtility.toFaceStatic
import com.tangoplus.tangoface.domain.util.FileUtility.toJsonString
import com.tangoplus.tangoface.domain.util.FileUtility.writeJsonValue
import com.tangoplus.tangoface.domain.util.HairLineUtility.findHairline
import com.tangoplus.tangoface.domain.util.MathHelpers.calculateAngle
import com.tangoplus.tangoface.domain.util.MathHelpers.getNormalizedDistance
import com.tangoplus.tangoface.domain.util.MathHelpers.getScaleFactor
import com.tangoplus.tangoface.domain.util.MathHelpers.setScaleFactor
import com.tangoplus.tangoface.domain.util.MathHelpers.toFloatTarget
import com.tangoplus.tangoface.domain.util.MeasureUtility
import com.tangoplus.tangoface.domain.util.MeasureUtility.animationSet
import com.tangoplus.tangoface.domain.util.MeasureUtility.getGuideText
import com.tangoplus.tangoface.domain.util.MeasureUtility.showGoMainDialog
import com.tangoplus.tangoface.domain.util.MeasureUtility.showPreviousDialog
import com.tangoplus.tangoface.domain.util.MeasureUtility.showReStartDialog
import com.tangoplus.tangoface.domain.util.MediaUtility.calculateScreenX
import com.tangoplus.tangoface.domain.util.MediaUtility.calculateScreenY
import com.tangoplus.tangoface.domain.util.MediaUtility.colorFrameToBitmapSafe
import com.tangoplus.tangoface.domain.util.MediaUtility.convertFaceLandmarksTo3D
import com.tangoplus.tangoface.domain.util.MediaUtility.scaleAndCropIntrinsicCenterCrop
import com.tangoplus.tangoface.domain.util.PreferenceUtility
import com.tangoplus.tangoface.domain.util.SecurityUtility.generateCustomUUID
import com.tangoplus.tangoface.domain.vision.face.FaceLandmarkAdapter
import com.tangoplus.tangoface.domain.vision.face.FaceLandmarkResult
import com.tangoplus.tangoface.domain.vision.pose.PoseLandmarkerHelper
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.orbbec.vo.Point3D
import com.tangoplus.tangoface.orbbec.vo.ScaledIntrinsic
import com.tangoplus.tangoface.orbbec.BaseActivity
import com.tangoplus.tangoface.ui.main.MainActivity
import com.tangoplus.tangoface.ui.settings.GyroDialogFragment
import com.tangoplus.tangoface.ui.main.vo.UserItem
import com.tangoplus.tangoface.ui.measure.vm.InputViewModel
import com.tangoplus.tangoface.ui.measure.vm.MeasureViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.ByteBuffer
import kotlin.collections.listOf
import kotlin.lazy
import kotlin.math.abs
import androidx.core.graphics.scale
import com.tangoplus.tangoface.domain.util.clearAll
import com.tangoplus.tangoface.domain.util.copyFrom
import java.math.RoundingMode

class DepthCameraActivity : BaseActivity(), FaceLandmarkerHelper.LandmarkerListener {

	private lateinit var bd: ActivityDepthCameraBinding
	companion object {
		private const val TAG = "Face Landmarker"

		init {
//			System.loadLibrary("facebeautyexpert")
//			if (!OpenCVLoader.initLocal()) {
//				Log.e("CameraActivity", "OpenCV initialization failure!")
//			} else {
//				Log.d("CameraActivity", "OpenCV Initialization Successful!")
//			}
		}
	}

	// ------------# pipeline #------------
	private var mPipeline: Pipeline? = null
	private var mStreamThread: Thread? = null
	private var last3DCalculateTime = 0L
	private val calculationInterval = 200L // 5fps

	private var mIsStreamRunning = false
	@Volatile private var mIsDeviceDetached = false
	private var mDevice: Device? = null

	private lateinit var flh: FaceLandmarkerHelper
	private val viewModel: MainViewModel by viewModels()
	private val mvm: MeasureViewModel by viewModels()
	private val ivm: InputViewModel by viewModels()
	private val faceBlendshapesResultAdapter by lazy {
		FaceBlendshapesResultAdapter()
	}
	private var isCountDown = false
	private var recordingJob: Job? = null
	private var recording: Recording? = null
	private var startRecording = false
	private lateinit var backgroundExecutor: ExecutorService
	private var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))

	var latestResult: List<Point3D> = listOf()
	var customResult: FaceLandmarkResult? = null

	private var isRecording = false
	private var videoEncoder: MediaCodec? = null
	private var mediaMuxer: MediaMuxer? = null
	private var videoTrackIndex = -1


	private lateinit var fDao: FaceDao
	private lateinit var prefsUtil: PreferenceUtility
	private var bounceAnimator: AnimatorSet? = null

	private lateinit var poseLandmarker: PoseLandmarkerHelper
	private var mHoleFillingFilter: HoleFillingFilter? = null

	override fun getDeviceChangedCallback(): DeviceChangedCallback? {
		return mDeviceChangedCallback
	}

	private fun setPowerLineFrequency(device: Device, value: Int) {
		try {
			device.setPropertyValueI(DeviceProperty.OB_PROP_COLOR_POWER_LINE_FREQUENCY_INT, value)
			val readback = device.getPropertyValueI(DeviceProperty.OB_PROP_COLOR_POWER_LINE_FREQUENCY_INT)
			Log.i("Flicker", "PowerLineFrequency set=$value, readback=$readback")
		} catch (e: Exception) {
			Log.e("Flicker", "Failed to set PowerLineFrequency($value)", e)
		}
	}
	override fun onStart() {
		super.onStart()
		initSDK()
	}

	override fun onStop() {
		try {
			stopAndJoinFrameLoop(500)

			if (null != mPipeline) {
				mPipeline!!.close()
			}

			if (mDevice != null) {
				mDevice!!.close()
			}
		} catch (e: Exception) {
			Log.e(TAG, "onStop: " + e.message)
		}
		super.onStop()
	}

	private val mDeviceChangedCallback: DeviceChangedCallback = object : DeviceChangedCallback {
		override fun onDeviceAttach(deviceList: DeviceList) {
			try {
				Log.v("디바이스콜백", "onDevice Attach is started")
				mIsDeviceDetached = false
				requestCleanup("onDeviceAttach")
				if (null == mPipeline) {
					// 2.Create Device and initialize Pipeline through Device
					mDevice = deviceList.getDevice(0)

					val colorSensor = mDevice!!.getSensor(SensorType.COLOR)
					if (null == colorSensor) {
						showToast("This device does not support Color sensor")
						mDevice!!.close()
						mDevice = null
						return
					}
					// 세팅에서 설정한 fps 값을 가져옴 default 60
					val fpsValue = prefsUtil.getFlickerType()
					Log.v("fpsValue", "$fpsValue")
					setPowerLineFrequency(mDevice!!, fpsValue)
					// 3. Create Device and initialize Pipeline through Device
					mPipeline = Pipeline(mDevice)

					// 4.Create Pipeline configuration
					val config = Config()

					mPipeline!!.getStreamProfileList(SensorType.COLOR).use { colorProfileList ->
						val colorProfile =
							colorProfileList.getVideoStreamProfile(1920, 1080, Format.RGB, 30)
						config.enableStream(colorProfile)
						colorProfile.close()
					}
					mPipeline!!.getStreamProfileList(SensorType.DEPTH).use { depthProfileList ->
						// 컬러 프로파일과 해상도가 맞는 뎁스 프로파일 선택
						val depthProfile =
							depthProfileList.getVideoStreamProfile(1280, 720, Format.Y16, 30)
						config.enableStream(depthProfile)
						depthProfile.close()
					}
					config.setAlignMode(AlignMode.ALIGN_D2C_SW_MODE)

					mPipeline!!.start(config)

					val deviceInfo = mDevice!!.info
					val serialNumber = deviceInfo.serialNumber
					val uid = deviceInfo.uid

					try {
						mHoleFillingFilter = HoleFillingFilter(serialNumber)
					} catch (e: Exception) {
						Log.e(TAG, "Failed with serial, trying uid: " + e.message)
						try {
							mHoleFillingFilter = HoleFillingFilter(uid)
						} catch (e2: Exception) {
							Log.e(TAG, "Failed with uid too: " + e2.message)
						}
					}
					config.close()
					start()
				}
			} catch (e: Exception) {
				Log.e(TAG, "onDeviceAttach: " + e.printStackTrace())
			} finally {
				// 9.Release device list resources
				deviceList.close()
			}
		}

		override fun onDeviceDetach(deviceList: DeviceList) {
			try {
				mIsDeviceDetached = true
				val loopStopped = stopAndJoinFrameLoop(1500)

				if (loopStopped) {
					try { mPipeline?.stop() } catch (_: Throwable) {}
					try { mPipeline?.close() } catch (_: Throwable) {}
					mPipeline = null
					try { mDevice?.close() } catch (_: Throwable) {}
					mDevice = null
				} else {
					// 루프가 안 죽었으면 close 시도 자체가 크래시 확률을 올립니다
					mPipeline = null
					mDevice = null
				}
			} finally {
				deviceList.close()
			}
		}
	}

	private val mCountDown: CountDownTimer by lazy {
		object : CountDownTimer(3000, 1000) {
			@SuppressLint("SetTextI18n")
			override fun onTick(millisUntilFinished: Long) {
				runOnUiThread {
					bd.clCountDown.visibility = View.VISIBLE
					bd.clCountDown.alpha = 1f
					bd.tvCountDown.text = "${(millisUntilFinished / 1000.0f).roundToInt()}"
					Log.v("count", "${bd.tvCountDown.text}")
					if (millisUntilFinished > 500f) {
						playSound(R.raw.camera_countdown)
					}
				}
			}

			override fun onFinish() {
				Log.v("카운트 시작", "${mvm.coordinatesJA.length()}, 현재 seq: ${mvm.currentSeq}")
				if (mvm.currentSeq == Sequence.BOW) {
					hideViews()
					// 보우 측정 시 data가 3개 미만일 경우 혹은/ 1개의 y축 값이 튀었을 경우 다시 촬영
					val yValues = mvm.currentTarget.map { it.center.y.toFloat() }
					if (yValues.size < 3) {
						Log.d("보우측정", "데이터가 3개 미만 → 다시 촬영")
						showToast("랜드마크가 3개 미만으로 추출됐습니다")
						isCountDown = false
						bd.tvGoGallery.isEnabled = true
						bd.btnShooting.apply {
							isEnabled = true
						}
						return
					} else {
						val maxY = yValues.maxOrNull() ?: 0f
						val minY = yValues.minOrNull() ?: 0f
						val diff = maxY - minY
						Log.v("diff 확인", "$diff")
						if (diff > 50f) {
							Log.d("보우측정", "Y값 차이 $diff → 튄 값 있음, 다시 촬영")
							showToast("랜드마크가 페이스 보우와 다른 위치를 추출했습니다")
							isCountDown = false
							bd.tvGoGallery.isEnabled = true
							bd.btnShooting.apply {
								isEnabled = true
							}
							return
						}
					}
					playSound(R.raw.camera_shutter)

					lifecycleScope.launch {
						captureImage(mvm.currentSeq)
						customResult?.let { resultHsvToJson(it, mvm.currentTarget) }
						viewModel.setSeqFinishedFlag(true)
						updateUI()
					}
					setGuideAnimation(mvm.currentSeq, false)
					isCountDown = false
					bd.tvGoGallery.isEnabled = true
					bd.btnShooting.apply {
						isEnabled = true
					}
					bd.btnPrevious.apply {
						visibility = View.VISIBLE
						isEnabled = true
					}
					Handler(Looper.getMainLooper()).postDelayed({ bd.overlay.clearColorTrack() }, 1000)
					return
				}

				if (latestResult.isNotEmpty()) {

					timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
					hideViews()
					setJawTiltGuideText(mvm.currentSeq)
					when (mvm.currentSeq) {
						Sequence.JAW_LEFT_TILT, Sequence.JAW_RIGHT_TILT -> {
							startVideoRecording {
								mvm.tempStaticJo = null
								// 좌표 초기화
								Log.v("녹화 완료", "좌표값: ${mvm.videoTempCoordinatesJA.length()}")
								mvm.coordinatesJA.put(mvm.videoTempCoordinatesJA)
								mvm.videoTempCoordinatesJA = JSONArray()
								Log.v("동적 저장완료", "${mvm.coordinatesJA.length()}, 현재 seq: ${mvm.currentSeq}")

								viewModel.setSeqFinishedFlag(true)
								updateUI()
								setCameraRecordEndAnimation()
							}
						}

						else -> {
							playSound(R.raw.camera_shutter)
							lifecycleScope.launch {
								viewModel.setContourSave(true) // 컨투어를 위한 shortArray 추출 및 저장

								// 이미지 캡처 -> scale init -> resultBundleToJson 으로 좌표값 및 데이터 저장

								captureImage(mvm.currentSeq)
								customResult?.let { mvm.resultBundleToJson(it, latestResult, mvm.currentSeq) }
								viewModel.setSeqFinishedFlag(true)
								updateUI()
							}
						}
					}
				}
			}
		}
	} //  ------! 카운트 다운 끝 !-------
	private fun showToast(msg: String?) {
		runOnUiThread {
			Toast.makeText(this@DepthCameraActivity, msg, Toast.LENGTH_SHORT).show()
		}
	}
	private val cleanupLock = Any()
	@Volatile private var cleaningUp = false

	override fun onDestroy() {
		super.onDestroy()
		MeasureUtility.dismissSafely()
		requestCleanup("ACTIVITY_DESTROY")
		releaseSDK()
		releasePose()
		releaseFace()
		releaseHsv()

//		// ---------# 메모리 정리 #---------
//		System.gc()
//		Runtime.getRuntime().gc()
	}
	private fun requestCleanup(reason: String) {
		if (cleaningUp) return
		cleaningUp = true

		runOnUiThread {
			synchronized(cleanupLock) {
				try {
					Log.w(TAG, "Cleanup start: $reason")

					stopAndJoinFrameLoop(2000)
					// 2) USB_DETACHED면 close를 스킵(또는 지연)
					if (reason == "USB_DETACHED") {
						mPipeline = null
						mDevice = null

						Log.w(TAG, "Detach cleanup: skip pipeline/device close to avoid native crash")
						return@runOnUiThread
					}

					// 3) 정상 종료인 경우에만 close
					try { mPipeline?.stop() } catch (_: Throwable) {}
					try { mPipeline?.close() } catch (_: Throwable) {}
					mPipeline = null
					try { mDevice?.close() } catch (_: Throwable) {}
					mDevice = null

				} finally {
					cleaningUp = false
					Log.w(TAG, "Cleanup done: $reason")
				}
			}
		}
	}
	// HSV Tracking
	private lateinit var hsvTracker: HsvColorTracker
	private lateinit var trackerExecutor: ExecutorService
	// ---------------------------------# stream 시작 #-------------------------------------------

	private fun start() {
		mIsStreamRunning = true

		if (mStreamThread == null) {
			mStreamThread = Thread(mStreamRunnable)
			mStreamThread!!.start()
		}
	}

	private var scaledIntrinsic: ScaledIntrinsic? = null
	private var colorDistortion: CameraDistortion? = null
	private var isIntrinsicInitialized = false
	private val mainHandler = Handler(Looper.getMainLooper())
	private val mStreamRunnable = Runnable {
		var buffer: ByteBuffer? = null
		while (mIsStreamRunning && !Thread.currentThread().isInterrupted) {


			if (mIsDeviceDetached) break

			val pipeline = mPipeline
			if (pipeline == null) break
			try {
				mPipeline!!.waitForFrameSet(1000).use { frameSet ->
					if (frameSet == null) {
						// stop 요청이 들어왔으면 빠져나감
						if (!mIsStreamRunning || mIsDeviceDetached) return@Runnable
						return@use
					}

					// Intrinsic 초기화...
					if (!isIntrinsicInitialized) {
						val params = mDevice?.calibrationCameraParamList
						if (params != null) {
							val paramsUnit = params.getCameraParam(0) // 캘리브 원해상도 기준
							val base = paramsUnit.colorIntrinsic
							colorDistortion = paramsUnit.colorDistortion

							val targetW = 1920
							val targetH = 1080

							// ✅ 종횡비 보정 포함 (CENTER_CROP 가정)
							scaledIntrinsic = scaleAndCropIntrinsicCenterCrop(
								fx = base.fx,
								fy = base.fy,
								cx = base.cx,
								cy = base.cy,
								baseWidth = base.width.toInt(),
								baseHeight = base.height.toInt(),
								targetWidth = targetW,
								targetHeight = targetH
							)
							isIntrinsicInitialized = true
						}
					}

					val colorFrame = frameSet.getColorFrame()

					// ===== Color Frame 처리 =====
					var bitmap: Bitmap? = null
					var mpImage: MPImage? = null

					try {
						colorFrame?.let { frame ->
							// ByteBuffer 처리
							val dataSize = frame.getDataSize()
							if (buffer == null || buffer!!.capacity() != dataSize) {
								buffer = ByteBuffer.allocateDirect(dataSize)
							}
							buffer?.clear()
							frame.getData(buffer)
							bd.obglvC.setCenterCrop(true)
							bd.obglvC.update(
								frame.getWidth(),
								frame.getHeight(),
								StreamType.COLOR,
								frame.getFormat(),
								buffer,
								1.0f
							)
							// ✅ Bitmap 생성 (한 번만!)
							if (scaledIntrinsic != null && colorDistortion != null) {
								bitmap = colorFrameToBitmapSafe(frame)

								bitmap?.let { bmp ->
									// 1️⃣ MPImage 생성 (Face Detection용)
									mpImage = BitmapImageBuilder(bmp).build()

									val timestamp = System.currentTimeMillis()
									mainHandler.post {
										if (!flh.isClose()) {
											flh.detectAsync(mpImage!!, timestamp)
										}
									}

									// 2️⃣ HSV Tracking
									if (mvm.currentSeq == Sequence.BOW) {
										val result = hsvTracker.process(bmp, makeDebugMask = false)
										trackerExecutor.execute {
											if (result.targets.isEmpty()) {
												bd.overlay.clearColorTrack()
											} else {
												bd.overlay.updateColorTracks(
													targets = result.targets,
													srcW = 1920,
													srcH = 1080,
													scaleType = OverlayView.ScaleType.FIT,
													rotateDeg = 0,
													mirrorX = true
												)
												mvm.currentTarget = result.targets
											}
										}
									}
								}
							}
						}
					} finally {
						colorFrame?.close()  // Frame 해제
					}

					// ===== Depth Frame 처리 =====
					val originalDepthFrame = frameSet.getDepthFrame()
					var depthFrame: DepthFrame? = null

					try {
						depthFrame = if (originalDepthFrame != null && mHoleFillingFilter != null) {
							try {
								val filtered = mHoleFillingFilter?.process(originalDepthFrame)
								val filteredDepth = filtered?.`as`(FrameType.DEPTH) as DepthFrame
								originalDepthFrame.close()  // ✅ 원본 해제
								filteredDepth
							} catch (e: Exception) {
								Log.e(TAG, "Filter failed: ${e.message}")
								originalDepthFrame
							}
						} else {
							originalDepthFrame
						}

						depthFrame?.let { frame ->
							val w = frame.width
							val h = frame.height
							val depthByteData = ByteArray(w * h * 2)
							frame.getData(depthByteData)

							val depthData = ShortArray(w * h)
							for (i in 0 until w * h) {
								val lowByte = depthByteData[i * 2].toInt() and 0xFF
								val highByte = depthByteData[i * 2 + 1].toInt() and 0xFF
								depthData[i] = (highByte shl 8 or lowByte).toShort()
							}
//                            if (viewModel.getContourSave() && customResult != null ) {
//                                val faceRect = customResult!!.getBoundingRect(w, h) // 아래에 함수 정의됨
//                                val croppedDepth = extractDepthAroundFace(depthData, w, h, faceRect, margin = 25)
//                                mvm.contourSA.add(croppedDepth)
//                                Log.v("컨투어저장", "seq: ${seqStep.value} -> ${croppedDepth.size}")
//                                viewModel.setContourSave(false)
//                            }
							// 3D 좌표 계산
							val params = mDevice?.calibrationCameraParamList
							val currentTime = System.currentTimeMillis()
							if (currentTime - last3DCalculateTime >= calculationInterval
								&& customResult != null
								&& params != null
							) {
								val paramsUnit = params.getCameraParam(0) // 캘리브 원해상도 기준
								val base = paramsUnit.colorIntrinsic
								last3DCalculateTime = currentTime
								val landmarks3D = convertFaceLandmarksTo3D(
									customResult!!,
									depthData,
									w,
									h,
									base
								)
								latestResult = landmarks3D
								runOnUiThread {
									bd.overlay.setResults(landmarks3D)
									bd.overlay.invalidate()
								}
							}
						}
					} finally {
						depthFrame?.close()  // ✅ Depth Frame 해제
					}
				}
			} catch (e: com.orbbec.obsensor.OBException) {
				val msg = e.message.orEmpty()
				// USB detach/비활성화면 즉시 탈출 (중요)
				if (msg.contains("deactivated", true) || msg.contains("disconnected", true)) {
					break
				}
				// 그 외 예외도 안전하게 종료 쪽이 detach 안정성에 좋습니다
				break
			} catch (t: Throwable) {
				break
			}
		}
	}

	private fun stopAndJoinFrameLoop(timeoutMs: Long = 1500L): Boolean {
		// 1) 루프 종료 플래그
		mIsStreamRunning = false

		val t = mStreamThread ?: return true

		// 2) waitForFrameSet 블로킹을 깨기 위해 interrupt 시도
		try {
			t.interrupt()
		} catch (_: Throwable) {
		}

		// 3) join
		return try {
			t.join(timeoutMs)
			val finished = !t.isAlive
			if (finished) {
				mStreamThread = null
			}
			finished
		} catch (_: InterruptedException) {
			// 호출자(메인스레드)가 인터럽트 됐다면 "종료 실패"로 처리
			false
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
//        handler.post(logRunnable)
		bd = ActivityDepthCameraBinding.inflate(layoutInflater)
		WindowCompat.setDecorFitsSystemWindows(window, false)

		setContentView(bd.root)
		setTitle("Stream-Color")
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

		initFace()
		initPose()

		// 상단, 하단 상태표시줄 넣기
		val controller = WindowCompat.getInsetsController(window, window.decorView)
		controller.apply {
			hide(WindowInsetsCompat.Type.systemBars())
			systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		}

		mvm.initMeasure.observe(this) {
			if (it) {
				initSettings()
				mvm.initMeasure.value = false
			}
		}
		ivm.isFinishInput.observe(this) {
			if (it && mIsStreamRunning) {
				createMaterialDialog(this@DepthCameraActivity)
			}
		}
		prefsUtil = PreferenceUtility(this@DepthCameraActivity)

		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				showGoMainDialog(this@DepthCameraActivity)
			}
		})
		bd.btnShooting.setOnSingleClickListener {
			if (bd.btnShooting.text == "결과보기") {
				// ---------------# 데이터 갈무리해서 gallery DialogFragment로 넘기기 #-----------------
				// 각각의 static에 사용자 정보 넣기

				// GUEST일 경우 초기화
				if (ivm.birthdayValue.value == "날짜 선택") {
					ivm.birthdayValue.value = ""
				}

				val faceUser = FaceUser(
					name = ivm.nameValue.value,
					mobile = ivm.mobileValue.value,
					uuid = mvm.currentUUID,
					birthday = ivm.birthdayValue.value,
					nationality = ivm.nationalityValue.value,
					userGender = ivm.genderValue.value
				)

				lifecycleScope.launch(Dispatchers.IO) {
					val fd = FaceDatabase.getDatabase(this@DepthCameraActivity)
					fDao = fd.faceDao()

					val existedUserSn = fDao.getExistUserSn(mvm.currentUUID)
					if (existedUserSn == null) {
						val userSn = fDao.insertUser(faceUser).toInt()
						withContext(Dispatchers.Main) {
							mvm.currentUserSn = userSn
						}
					} else {
						withContext(Dispatchers.Main) {
							mvm.currentUserSn = existedUserSn
						}
					}
					Log.v("userSn설정잘됐는지", "is init? = ${mvm.currentUserSn}, ${mvm.allSeqs}")
					val allSeqs = mvm.allSeqs
					val seqsExceptBow = allSeqs.filterNot { it == Sequence.BOW }
					val staticJos = mutableListOf<JSONObject>()

					seqsExceptBow.forEachIndexed { index, sequence ->
						val staticJSON = setJo(mvm.staticJA.getJSONObject(index), sequence)
						staticJos.add(staticJSON)
						Log.v("staticJSON", "$staticJSON")
					}
					Log.v("staticJson까보기0", "${staticJos.size}")
					// facebow가 있을 경우
					if (allSeqs.contains(Sequence.BOW)) {
						val staticJSON9 = setJo(mvm.hsvColorJo, Sequence.BOW) // 미디어 이름의 첫번째를 가져와야 함.
						staticJos.add(staticJos.size, staticJSON9) // 마지막에 넣기
						Log.v("staticJSON", "$staticJSON9, ${staticJos}")
					}

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
							if (i == staticJos.size - 1 && currentStatic.seq == 9) {
								put("face_landmark", mvm.hsvFLCoordinatesJA)
								put("pose_landmark", mvm.hsvPLCoordinatesJA)
								put("bow_landmark", mvm.resultTargetFs)
							} else {
								put("face_landmark", mvm.coordinatesJA.optJSONArray(i))
								put("pose_landmark", mvm.plrJA.optJSONArray(i))

								// 헤어라인과 기타 등등 담을 걸 etc > hairline, 다른 랜드마크..
								mvm.etcJO.put("hairline", mvm.hairLineJA.optJSONObject(i) ?: JSONObject())
								// TODO 추후 다른 랜드마크를 추출할 시
//								mvm.etcJO.put("otherline", mvm.otherJA.optJSONArray(i) ?: JSONArray())

								put("etc_landmark", mvm.etcJO)
								mvm.etcJO = JSONObject()
							}
						}
						mergedJOBeforeFileName.add(mergedJSON)
						Log.v("json저장 전", "seq: $i")
						val jsonPath = saveJsonToStorage(
							mergedJSON,
							if (i == staticJos.size - 1 && currentStatic.seq == 9) {
								mvm.hsvMediaFileName.replace(".jpg", "").replace(".mp4", "")
							} else {
								mvm.mediaFileNames[i].replace(".jpg", "").replace(".mp4", "")
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

						Log.v("스태틱스Sn", "$lastTempServerSn, ${mvm.currentUserSn}: ${transStatics.map { it.user_sn }}")
						viewModel.comparisonDoubleItem = null
						val intent = Intent(this@DepthCameraActivity, MainActivity::class.java)
						intent.putExtra("isMeasureFinish", true)
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

		bd.btnPrevious.setOnSingleClickListener {
			showPreviousDialog(this@DepthCameraActivity, { setPreviousStep() })
		}

		bd.tvGoGallery.setOnSingleClickListener {
			val intent = Intent(this@DepthCameraActivity, MainActivity::class.java)
			startActivity(intent)
			finishAffinity()
		}

		bd.tvRetry.setOnSingleClickListener {
			showReStartDialog(this@DepthCameraActivity, { initSettings() })

		}

		bd.tvGyro.setOnSingleClickListener {
			val gyro = GyroDialogFragment()
			gyro.show(supportFragmentManager, "gyroDialogFragment")
		}
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

	// 녹화 시작과 종료
	private var mouseClosedTime: Long? = null
	private var recordingStartTime: Long? = null
	override fun onResults(
		resultBundle: FaceLandmarkerHelper.ResultBundle
	) {
//        Log.v(TAG, "✅ onResults 호출됨! 스레드: ${Thread.currentThread().name}")
//        Log.v("결과가 나오긴함?", "onResult is started. ${resultBundle.result.faceLandmarks()[0].size}")

		runOnUiThread {
			customResult = FaceLandmarkAdapter.toCustomFaceLandmarkResult(resultBundle.result)
			if (customResult != null) {
				// depthCamera Stream 좌우반전 대응 - 얼굴 랜드마크 좌우 반전
          val flippedLandmarks = customResult!!.landmarks.map { landmark ->
              landmark.copy(x = 1.0f - landmark.x)
          }
				val overlayResult = customResult!!.copy(landmarks = flippedLandmarks)

				bd.overlay.setResults(
					overlayResult,
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
			val landmarks = customResult?.landmarks
			if (startRecording && customResult != null) {
				mvm.resultBundleToJson(customResult!!, latestResult, seq = mvm.currentSeq)
			}

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
					Sequence.JAW_OPENING -> horizontalLineVector in 120f..190f // 개구 상태
					else -> horizontalLineVector in 115f..180f
				}

				bd.overlay.setHorizon(horizonBoolean)
				//                Log.v("얼굴 중앙", "$isFaceCenter")
				//                Log.v("라인벡터", "코: ${faceLandmarks[0].x()}, ${faceLandmarks[0].y()}, 왼쪽눈: ${faceLandmarks[33].x()}, ${faceLandmarks[33].x()}, 오른쪽 눈: ${faceLandmarks[263].x()}, ${faceLandmarks[263].x()}")

				// verti state 설정
				val vertiBoolean = when (mvm.currentSeq) {
					Sequence.COMFORT, Sequence.NECK_EXTENTION, Sequence.SIDE_LEFT, Sequence.SIDE_RIGHT -> true // 5 목폄 상태일때만
					else -> if (eyeDistanceGap < 0.35f) true else false
				}
				val eyeSlope = calculateSlope(leftEye.x, leftEye.y, rightEye.x, rightEye.y)
				// 눈 가로 선 (얼굴이 360도 방향을 x, y 방향 회전)
//                Log.v("슬롭봐보기", "$eyeSlope")
				val eyeParallel = when (mvm.currentSeq) {
					Sequence.COMFORT, Sequence.NECK_EXTENTION, Sequence.SIDE_LEFT, Sequence.SIDE_RIGHT -> true
					else -> (eyeSlope in 167.5f..187.5f) || (eyeSlope in -187.5f..-167.5f)
				}
				val vertiMediator = vertiBoolean && eyeParallel

				bd.overlay.setVerti(vertiMediator)

				// 영상 녹화 시 체크 하기
				if (startRecording) {
					val topLips = landmarks[13].y
					val bottomLips = landmarks[14].y
					val lipThreshold = 0.01f // 실험적으로 조정 필요
					val mouseClosed = abs(topLips - bottomLips) < lipThreshold
					val currentTime = System.currentTimeMillis()

					// 녹화 시작 시간 기록 (최초 1회만)
					if (recordingStartTime == null) {
						recordingStartTime = currentTime
					}

					// 녹화 시작 후 2초가 지났는지 확인
					val recordingElapsed = currentTime - (recordingStartTime ?: 0L)
					val canDetectMouthClose = recordingElapsed >= 2000 // 2초

					Log.v(
						"입술감지",
						"녹화경과: ${recordingElapsed}ms, 감지가능: $canDetectMouthClose, 입다물음: $mouseClosed"
					)

					if (canDetectMouthClose && mouseClosed) {
						if (mouseClosedTime == null) {
							mouseClosedTime = currentTime
							Log.v("입술감지", "입 다물기 시작")
						} else {
							val elapsed = currentTime - (mouseClosedTime ?: 0L)
							Log.v("입술감지", "입 다문 시간: ${elapsed}ms")
							if (elapsed >= 700) { // 0.7초
								Log.v("입술감지", "0.7초 입 다물고 있어서 녹화 중단")
								stopVideoRecording()

								// 녹화 중단 후 시간 초기화
								recordingStartTime = null
								mouseClosedTime = null
							}
						}
					} else {
						// 입이 열리거나 아직 2초가 안 지났으면 타이머 리셋
						if (mouseClosedTime != null) {
							Log.v("입술감지", "입 열림 - 타이머 리셋")
						}
						mouseClosedTime = null
					}
				}

			}

//            Log.v("인풋이미지", "${resultBundle.inputImageHeight}, ${resultBundle.inputImageWidth}")
//            Log.v("results", "${resultBundle.result.faceLandmarks().map { it.size }}")
			// Force a redraw
			bd.overlay.invalidate()

			if (ivm.isFinishInput.value == true && resultBundle.result.faceLandmarks()
					.isNotEmpty() && !viewModel.getSeqFinishedFlag()
			) { // && isFaceCenter
				// 애니메이션 제거 flag
				if (!viewModel.getGuideTextFlag()) {
					bd.lavCamera.clearAnimation()
					bd.lavCamera.visibility = View.INVISIBLE
					bounceAnimator?.cancel()
					bounceAnimator = null
					bd.tvSeqGuide.animate().cancel()
					bd.tvSeqGuide.clearAnimation()
					bd.tvSeqGuide.translationY = 0f
					bd.tvSeqGuide.text = "안녕하세요!\n${getGuideText(mvm.currentSeq)}"
					viewModel.setGuideTextFlag(true)
				}

				// guideText 움직이기 flag
				if (!viewModel.getTextAnimationFlag()) {
					Handler(Looper.getMainLooper()).postDelayed({
						val isPortrait = if (getScreenOrientation().contains("portrait")) true else false

						animateTextViewToTopLeft(
							bd.clSeqGuide,
							bd.tvSeqGuide,
							if (isPortrait) 0.05f else 0.1f,
							0.05f
						)
					}, 2000)
					bd.llbtn.visibility = View.VISIBLE

					// animation flag에다가 애니메이션
					if (mvm.currentSeq in listOf(
							Sequence.RESTING,
							Sequence.OCCLUSAL,
							Sequence.JAW_LEFT_TILT,
							Sequence.JAW_RIGHT_TILT,
							Sequence.JAW_OPENING,
							Sequence.NECK_EXTENTION
						)
					) {
						viewModel.setTextAnimationFlag(true)
					}
				}
			}
		}
	}


	// -------------------------# 타이머, UI업데이트, 이미지캡처, 결과처리 #-----------------------------
	private fun hideViews() {
		bd.clCountDown.visibility = View.INVISIBLE

		when (mvm.currentSeq) {
			Sequence.JAW_LEFT_TILT, Sequence.JAW_RIGHT_TILT -> startCameraRecordAnimation()
			else -> startCameraShutterAnimation()
		}
	}

	private fun startCameraRecordAnimation() {
		bd.cvRecordMark.visibility = View.VISIBLE
		bd.tvGoGallery.visibility = View.GONE
		bd.tvRetry.visibility = View.GONE
		bd.tvSeqCount.visibility = View.GONE
		bd.tvGyro.visibility = View.GONE
	}

	private fun setCameraRecordEndAnimation() {
		bd.cvRecordMark.visibility = View.GONE
		bd.tvGoGallery.visibility = View.VISIBLE
		bd.tvRetry.visibility = View.VISIBLE
		bd.tvSeqCount.visibility = View.VISIBLE
		bd.tvGyro.visibility = View.VISIBLE
	}

	private fun startCameraShutterAnimation() {
		// 첫 번째 애니메이션: VISIBLE로 만들고 alpha를 0에서 1로
		bd.flCameraShutter.visibility = View.VISIBLE
		bd.flCameraShutter.alpha = 0f
		Handler(Looper.getMainLooper()).postDelayed({
			val fadeIn = ObjectAnimator.ofFloat(bd.flCameraShutter, "alpha", 0f, 1f).apply {
				duration = 100 // 0.1초
				interpolator = AccelerateDecelerateInterpolator()
			}
			// 두 번째 애니메이션: alpha를 1에서 0으로 만들고, 끝난 후 INVISIBLE로 설정
			val fadeOut = ObjectAnimator.ofFloat(bd.flCameraShutter, "alpha", 1f, 0f).apply {
				duration = 100 // 0.1초
				interpolator = AccelerateDecelerateInterpolator()
				addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						bd.flCameraShutter.visibility = View.INVISIBLE
					}
				})
			}
			fadeIn.start()
			fadeIn.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					fadeOut.start()
				}
			})
		}, 0)
	}

	private fun setGuideAnimation(seq: Sequence, isEnded: Boolean) {
		bd.btnShooting.isEnabled = false
		bd.btnPrevious.isEnabled = false
		bd.tvSeqGuide.apply {
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

	private val guideHandler = Handler(Looper.getMainLooper())
	private val guideRunnables = mutableListOf<Runnable>()

	private fun setJawTiltGuideText(step: Sequence) {
		val guideText = when (step) {
			Sequence.JAW_LEFT_TILT -> "미소지은 상태로 턱을 왼쪽으로 끝까지 이동해주세요\n그리고 입을 최대한 벌려주세요\n입을 다물어 측정을 완료하세요"
			Sequence.JAW_RIGHT_TILT -> "미소지은 상태로 턱을 오른쪽으로 끝까지 이동해주세요\n그리고 입을 최대한 벌려주세요\n입을 다물어 측정을 완료하세요"
			else -> return
		}

		cancelJawTiltGuideText()
		val lines = guideText.split("\n")
		var accumulatedDelay = 0L

		lines.forEachIndexed { index, line ->
			val currentDelay = if (index == 0) 1750L else 1400L
			val runnable = Runnable {
				bd.tvSeqGuide.text = line
			}

			// 등록
			guideRunnables.add(runnable)
			guideHandler.postDelayed(runnable, accumulatedDelay)
			accumulatedDelay += currentDelay
		}
	}

	fun cancelJawTiltGuideText() {
		guideRunnables.forEach { runnable -> guideHandler.removeCallbacks(runnable) }
		guideRunnables.clear()
	}

//    private fun isFaceInCenter(glabellaX: Float, glabellaY: Float): Boolean {
//        val targetRect = RectF(0.35f, 0.175f, 0.65f, 0.525f)
//        return targetRect.contains(glabellaX, glabellaY)
//    }

	// ------! 타이머 control 시작 !------
	private fun startTimer() {
		// 시작 버튼 후 시작
		bd.btnShooting.isEnabled = false
		bd.btnPrevious.isEnabled = false
		bd.tvGoGallery.isEnabled = false
		Log.v("startTimer", "${mvm.currentSeq} / ${mvm.allSeqs.size}")
		mCountDown.start()
		viewModel.setCountDownFlag(true)
		// ------! 타이머 control 끝 !------
	}

	private fun updateUI() {
		val isProcessing = mvm.moveToNextSeq()
		cancelJawTiltGuideText()
		if (!isProcessing) {
			setGuideAnimation(mvm.currentSeq, true)
			bd.clCountDown.visibility = View.GONE
			bd.btnShooting.apply {
				text = "결과보기"
				isEnabled = true
			}

			mCountDown.cancel()
			Log.v("몇단계?", "Max repeats reached, stopping the loop")
		} else {
			setGuideAnimation(mvm.currentSeq, false)
			val seqCount = "${mvm.getSeqIndex() + 1} / ${mvm.allSeqs.size}"
			bd.tvSeqCount.text = seqCount
			viewModel.setCountDownFlag(false)
			when (mvm.currentSeq) {
				Sequence.BOW -> {
					bd.fdgv.visibility = View.INVISIBLE
					bd.ivFrame.visibility = View.INVISIBLE
				}
				Sequence.COMFORT, Sequence.SIDE_LEFT, Sequence.SIDE_RIGHT -> {
					bd.fdgv.triggerOffAnimationIfNeeded()
					Handler(Looper.getMainLooper()).postDelayed({ bd.fdgv.visibility = View.INVISIBLE }, 800)
					bd.ivFrame.visibility = View.VISIBLE
					startFadeIn(bd.ivFrame)
				}
				else -> {
					bd.fdgv.visibility = View.VISIBLE
					bd.fdgv.triggerIntroAnimationIfNeeded()
					Handler(Looper.getMainLooper()).postDelayed({ bd.ivFrame.visibility = View.INVISIBLE }, 800)
					startFadeOut(bd.ivFrame)
				}
			}

			when (mvm.currentSeq) {
				Sequence.SIDE_LEFT -> bd.ivFrame.setImageDrawable(
					ContextCompat.getDrawable(
						this@DepthCameraActivity,
						R.drawable.drawable_seq_7
					)
				)

				Sequence.SIDE_RIGHT -> bd.ivFrame.setImageDrawable(
					ContextCompat.getDrawable(
						this@DepthCameraActivity,
						R.drawable.drawable_seq_8
					)
				)
				Sequence.COMFORT -> bd.ivFrame.setImageDrawable(
					ContextCompat.getDrawable(
						this@DepthCameraActivity,
						R.drawable.drawable_seq_0
					)
				)

				else -> {}
			}
		}

		bd.btnShooting.apply {
			isEnabled = true
		}
		bd.btnPrevious.apply {
			visibility = View.VISIBLE
			isEnabled = true
		}
		Handler(Looper.getMainLooper()).postDelayed({
			viewModel.setSeqFinishedFlag(false)
		}, 1500)
		isCountDown = false
		bd.tvGoGallery.isEnabled = true
		Log.v("업데이트UI", "${mvm.plrJA.length()}")
	}

	private fun setPreviousStep() {
		mvm.moveToPreviousSeq()
		if (mvm.currentSeq == Sequence.BOW) {
			mvm.currentTarget = listOf()
			mvm.resultTargetFs.clearAll()
			mvm.hsvColorJo.clearAll()
			mvm.hsvFLCoordinatesJA.clearAll()
			mvm.hsvPLCoordinatesJA.clearAll()
			mvm.hsvMediaFileName = ""
			bd.tvSeqGuide.text = "페이스 보우를 촬영할게요 !\n보우를 입에 물고 화면 정면을 응시해주세요"
			mvm.currentSeq = Sequence.BOW
			bd.btnPrevious.visibility = View.INVISIBLE
			bd.ivFrame.visibility = View.INVISIBLE
			bd.fdgv.visibility = View.INVISIBLE
			Log.v(
				"이전단계로",
				"${mvm.currentSeq}, 미디어파일명: ${mvm.mediaFileNames}, plr: ${mvm.plrJA}, flr.statc: ${mvm.staticJA}, coordinates: ${mvm.coordinatesJA}"
			)
			return
		}
		// -----------# frame -> fdgv 변경하기 #--------
		Log.v("이전seq", "${mvm.allSeqs}/${mvm.currentSeq}")
		when (mvm.currentSeq) {
			Sequence.COMFORT, Sequence.SIDE_LEFT, Sequence.SIDE_RIGHT -> {
				Handler(Looper.getMainLooper()).postDelayed({ bd.fdgv.visibility = View.INVISIBLE }, 800)
				bd.fdgv.triggerOffAnimationIfNeeded()
				bd.ivFrame.visibility = View.VISIBLE
				startFadeIn(bd.ivFrame)
			}
			else -> {
				bd.fdgv.visibility = View.VISIBLE
				bd.fdgv.triggerIntroAnimationIfNeeded()
				startFadeOut(bd.ivFrame)
				Handler(Looper.getMainLooper()).postDelayed({ bd.ivFrame.visibility = View.INVISIBLE }, 800)
			}
		}
		// ivFrame 이미지 프레임을 사용할 때만 7, 8, 0 넣기
		bd.ivFrame.setImageDrawable(
			ContextCompat.getDrawable(
				this@DepthCameraActivity,
				when (mvm.currentSeq) {
					Sequence.SIDE_LEFT -> R.drawable.drawable_seq_7
					Sequence.SIDE_RIGHT -> R.drawable.drawable_seq_8
					else -> R.drawable.drawable_seq_0
				}
			)
		)

		bd.clCountDown.visibility = View.GONE
		mCountDown.cancel()
		setGuideAnimation(mvm.currentSeq, false)

		bd.btnShooting.apply {
			text = "촬영"
			isEnabled = true
		}
		bd.btnPrevious.apply {
			visibility = if (mvm.currentSeq == mvm.allSeqs[0]) View.INVISIBLE else View.VISIBLE
			isEnabled = true
		}

		val seqCount = "${mvm.getSeqIndex() + 1} / ${mvm.allSeqs.size}"
		bd.tvSeqCount.text = seqCount
		viewModel.setCountDownFlag(false)

		val ci = if (mvm.allSeqs.contains(Sequence.BOW)) mvm.getSeqIndex()-1  else mvm.getSeqIndex()
		when (mvm.currentSeq) {
			Sequence.BOW -> {
				mvm.hsvFLCoordinatesJA.clearAll()
				mvm.hsvPLCoordinatesJA.clearAll()
				mvm.hsvMediaFileName = ""
				mvm.hsvColorJo.clearAll()
				Log.v("Bow삭제", "${mvm.hsvFLCoordinatesJA.length()}, ${mvm.hsvPLCoordinatesJA.length()}, ${mvm.hsvMediaFileName}, ${mvm.hsvColorJo}")
			}
			Sequence.JAW_LEFT_TILT, Sequence.JAW_RIGHT_TILT -> {
				mvm.tempStaticJo = null
				mvm.videoTempCoordinatesJA = JSONArray()
				mvm.staticJA.remove(ci)
				mvm.coordinatesJA.remove(ci)
				mvm.plrJA.remove(ci)
				mvm.mediaFileNames.removeAt(ci)
				Log.v("턱관절이동삭제", "${mvm.tempStaticJo}, ${mvm.videoTempCoordinatesJA}, ${mvm.hsvMediaFileName}, ${mvm.hsvColorJo}")
			}
			else -> {
				mvm.tempStaticJo = null
				mvm.staticJA.remove(ci)
				mvm.coordinatesJA.remove(ci)
				mvm.plrJA.remove(ci)
				mvm.mediaFileNames.removeAt(ci)
				Log.v("그외STATIC삭제", "${mvm.tempStaticJo}, ${mvm.staticJA.length()}, ${mvm.coordinatesJA.length()}, ${mvm.plrJA.length()}, ${mvm.mediaFileNames}")
			}
		}
		isCountDown = false
		bd.tvGoGallery.isEnabled = true
	}

	private suspend fun captureImage(seq: Sequence): Boolean {
		val name = getFileName(seq)
		return withContext(Dispatchers.IO) {
        try {
            mPipeline?.waitForFrameSet(1000)?.use { frameSet ->
                val colorFrame = frameSet.getColorFrame() ?: return@use false

                val dataSize = colorFrame.getDataSize()
                Log.d("captureImage", "✅ 데이터 읽을 크기: $dataSize bytes")

                val byteBuffer = ByteBuffer.allocateDirect(dataSize)
                colorFrame.getData(byteBuffer)
                byteBuffer.rewind() // 읽기 위치를 처음으로
                Log.d("captureImage", "✅ colorFrame.getData 완료")

                val width = colorFrame.width
                val height = colorFrame.height
                Log.d("captureImage", "✅ 크기: ${width}x${height}")

                val pixels = IntArray(width * height)
                for (i in pixels.indices) {
                    if (byteBuffer.remaining() >= 3) {
                        val r = byteBuffer.get().toInt() and 0xFF
                        val g = byteBuffer.get().toInt() and 0xFF
                        val b = byteBuffer.get().toInt() and 0xFF

                        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

                // 임시 파일 저장
                val tempFile = File.createTempFile("color_capture", ".jpg", cacheDir)
                FileOutputStream(tempFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                }
                val tempUri = Uri.fromFile(tempFile)
                saveMediaToStorage(this@DepthCameraActivity, tempUri, name, true)

                return@withContext true
            } ?: false
        } catch (e: Exception) {
            Log.e("captureImage", "❌ Frame capture failed: ${e.message}", e)
            false
        }
    }
	}

	fun saveJsonToStorage(inputJson: JSONObject, fileName: String): String? {
		Log.e("JSON_SAVE", "ENTER(JsonWriter): len=${inputJson.length()}, fileName=$fileName")

		MeasureUtility.show(this)
		val resolver = contentResolver
		val contentValues = ContentValues().apply {
			put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.json")
			put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
			put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Documents/TangoPlus")
		}

		return try {
			val uri: Uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
				?: run {
					Log.e("JSON_SAVE", "insert returned null uri")
					return null
				}

			resolver.openOutputStream(uri)?.use { os ->
				OutputStreamWriter(os, Charsets.UTF_8).use { osw ->
					JsonWriter(osw).use { w ->
						// 보기 좋게 들여쓰기(원치 않으면 제거)
						w.setIndent("  ")
						// ✅ JSONObject/JSONArray를 toString() 없이 직접 기록
						writeJsonValue(w, inputJson)
					}
				}
			} ?: run {
				Log.e("JSON_SAVE", "openOutputStream returned null")
				return null
			}

			Log.d("JSON_SAVE", "saved(JsonWriter): $fileName.json, uri=$uri")
			MeasureUtility.dismiss(this)
			"$fileName.json"
		} catch (e: Exception) {
			Log.e("JSON_SAVE", "save failed(JsonWriter)", e)
			null
		}
	}

	fun saveMediaToStorage(context: Context, uri: Uri, fileName: String, isImage: Boolean) {
		try {
			val extension = if (isImage) ".jpg" else ".mp4"
//			val file = File(context.cacheDir, "$fileName$extension")

			if (isImage) {
				val contentValues = ContentValues().apply {
					put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName$extension")
					put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
					put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TangoPlus")
				}
				val inputStream = context.contentResolver.openInputStream(uri)
				val tempFile = File.createTempFile("tempImage", null, context.cacheDir)
				inputStream?.use { input ->
					tempFile.outputStream().use { output ->
						input.copyTo(output)
					}
				}

				// EXIF 데이터 읽기
				val exif = ExifInterface(tempFile.absolutePath)
				val orientation =
					exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
//                Log.d("ExifDebug", "Exif Orientation: $orientation")
				// 비트맵 디코딩
				val options = BitmapFactory.Options().apply {
					inJustDecodeBounds = true
				}
				BitmapFactory.decodeFile(tempFile.absolutePath, options)

				val sourceWidth = options.outWidth
				val sourceHeight = options.outHeight

				val targetWidth = 1920
				val targetHeight = 1080

				// 이미지 스케일 계산
				val widthRatio = sourceWidth.toFloat() / targetWidth
				val heightRatio = sourceHeight.toFloat() / targetHeight
				val scale = maxOf(widthRatio, heightRatio)

				options.inJustDecodeBounds = false
				options.inSampleSize = scale.toInt()

				var bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, options)
				val matrix = Matrix()

				// 회전 적용
				when (orientation) {
					ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)   // 시계 방향 90도
					ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f) // 시계 방향 180도
					ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f) // 시계 방향 270도
				}

				// ★★★ 좌우반전 ★★★
				matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)

				// 모든 변환을 한번에 적용
				bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

				// 스케일 조정
				bitmap.scale(targetWidth, targetHeight).also { bitmap = it }

				// Pictures/TangoBeauty에 저장
				val imageUri = context.contentResolver.insert(
					MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
					contentValues
				)

				// --------------------------# poselandmark를 잡기 위한 detectAsync #-------------------------
				customResult?.let {
					val absoluteLandmarks = it.landmarks.map { landmark ->
						FaceLandmarkResult.FaceLandmark(
							x = calculateScreenX(mvm.overlayW, landmark.x),
							y = calculateScreenY(mvm.overlayH, landmark.y)
						)
					}
					val absoluteResult = FaceLandmarkResult(absoluteLandmarks)
					val hairResult = findHairline(bitmap, absoluteResult)
					if (hairResult != null) {
						Log.v("헤어라인 찾기", "헤어라인좌표: ${hairResult.hairlinePoint}")
						mvm.tempHairLineJO.apply {
							put("sx", hairResult.hairlinePoint.x)
							put("sy", hairResult.hairlinePoint.y)
						}
						mvm.hairLineJA.put(mvm.tempHairLineJO)
						mvm.tempHairLineJO = JSONObject()
					}
				}

				// --------------------------# poselandmark를 잡기 위한 detectAsync #-------------------------
				val resultBundle = poseLandmarker.detectImage(bitmap)
				if (resultBundle?.results?.first()?.landmarks()?.isNotEmpty() == true) {
					val plr = resultBundle.results.first().landmarks()[0]

					mvm.tempPlrJA = JSONArray()
					plr.forEachIndexed { index, _ ->
						val swapIndex = if (index >= 7 && index % 2 == 0) index - 1 // 짝수인 경우 뒤의 홀수 인덱스로 교체
						else if (index >= 7) index + 1 // 홀수인 경우 앞의 짝수 인덱스로 교체
						else index // 7 미만인 경우 그대로 사용
						val targetLandmark = plr[swapIndex]

						val scaledX = calculateScreenX(mvm.overlayW, targetLandmark.x())
						val scaledY = calculateScreenY(mvm.overlayH, targetLandmark.y())

						val jo = JSONObject().apply {
							put("index", index)
							put("isActive", true)
							put("sx", scaledX.toBigDecimal().setScale(1, RoundingMode.HALF_UP))
							put("sy", scaledY.toBigDecimal().setScale(1, RoundingMode.HALF_UP))
							put("wx", targetLandmark.x().toBigDecimal().setScale(4, RoundingMode.HALF_UP))
							put("wy", targetLandmark.y().toBigDecimal().setScale(4, RoundingMode.HALF_UP))
							put("wz", targetLandmark.z().toBigDecimal().setScale(4, RoundingMode.HALF_UP))
						}
						mvm.tempPlrJA.put(jo)
						mvm.currentPlrCoordinate.add(Pair(targetLandmark.x(), targetLandmark.y())) // 33 개가 다 담김
					}

					// 약 30개 랜드마크 JO를 저장한 seq 1개의 JA를 전체 seq를 담을 JA에 넣기
					if (mvm.currentSeq == Sequence.BOW) {
						mvm.hsvPLCoordinatesJA.copyFrom(mvm.tempPlrJA)
					} else {
						mvm.plrJA.put(mvm.tempPlrJA)
					}
				} else {
					showToast("poselandmark를 찾을 수 없습니다.")
					mvm.plrJA.put(JSONArray())
				}
				// -------------------------# 목젖 위치를 잡기 위한 detectAsync 끝 #-----------------------
				imageUri?.let { imageURI ->
					context.contentResolver.openOutputStream(imageURI)?.use { outputStream ->
						bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
						outputStream.flush()
					}
					Log.d("SaveMedia", "Image saved to Pictures: ${mvm.currentSeq} ->  $fileName$extension")
				} ?: run {
					Log.e("SaveMedia", "Failed to create image URI")
				}
				if (mvm.currentSeq == Sequence.BOW) {
					mvm.hsvMediaFileName = "$fileName$extension"
				} else {
					mvm.mediaFileNames.add("$fileName$extension")
				}

				Log.d("SaveMedia", "seqStep: ${mvm.currentSeq} vmFileName: ${mvm.mediaFileNames}")

				// 임시 파일과 비트맵 정리
				tempFile.delete()
				bitmap.recycle()

				// -----------------# video 저장 #------------------
			} else {
				val videoCollection =
					MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
				val contentValues = ContentValues().apply {
					put(MediaStore.Video.Media.DISPLAY_NAME, fileName)  // ex: 4-20251020145123.mp4
					put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
					put(
						MediaStore.Video.Media.RELATIVE_PATH,
						"${Environment.DIRECTORY_MOVIES}/TangoPlus"
					) // Movies/TangoPlus/
					put(MediaStore.Video.Media.IS_PENDING, 1)
				}

				val videoUri = context.contentResolver.insert(videoCollection, contentValues)

				if (videoUri != null) {
					context.contentResolver.openInputStream(uri)?.use { input ->
						context.contentResolver.openOutputStream(videoUri)?.use { output ->
							input.copyTo(output)

							contentValues.clear()
							contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
							context.contentResolver.update(videoUri, contentValues, null, null)
							Log.v("영상작업완료 후 plr넣기", "$fileName$extension")

							mvm.mediaFileNames.add("$fileName$extension")
							// 빈 jsonArray를 넣어서 index 갯수 맞추기
							mvm.plrJA.put(JSONArray())
							Log.v("영상작업완료 후 plr넣기", "${mvm.plrJA.length()}")
						}
					}
				}
			}
		} catch (e: IndexOutOfBoundsException) {
			Log.e("SaveMediaIndex", "${e.message}")
		} catch (e: IllegalArgumentException) {
			Log.e("SaveMediaIllegal", "${e.message}")
		} catch (e: IllegalStateException) {
			Log.e("SaveMediaIllegal", "${e.message}")
		} catch (e: NullPointerException) {
			Log.e("SaveMediaNull", "${e.message}")
		} catch (e: java.lang.Exception) {
			Log.e("SaveMediaException", "${e.message}")
		}
	}


	// --------------------------------- # pose landmark # -----------------------------------------
	private val poseLandmarkListener = object : PoseLandmarkerHelper.LandmarkerListener {
		override fun onError(error: String, errorCode: Int) {
			runOnUiThread {
				Log.e("PoseLandmark", "Pose detection error: $error $errorCode")
				Toast.makeText(this@DepthCameraActivity, "Pose detection error: $error", Toast.LENGTH_SHORT)
					.show()
			}
		}

		override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {}
	}

	@SuppressLint("SuspiciousIndentation")
	private fun stopVideoRecording() {
		recordingJob?.cancel()
		isRecording = false
    cleanup()
		releaseVideoRecording()
	}

	private fun startRecordingMaxTimer() {
		lifecycleScope.launch(Dispatchers.Main) {
			delay(6000) // 6초가 max 영상 길이
			stopVideoRecording()
		}
	}

	private fun startVideoRecording(callback: () -> Unit) {
      startDepthCameraRecording(callback)
	}

	@SuppressLint("SuspiciousIndentation")
	private fun releaseVideoRecording() {
		// 1) 타이머/잡 정리 (이미 있는 변수만 사용)
		try {
			recordingJob?.cancel()
		} catch (_: Throwable) {
		}
		recordingJob = null
		isRecording = false
		startRecording = false
		recordingStartTime = null
		// 2) 메인 루퍼에 걸어둔 지연 콜백들 제거
		//    (Start에서 Handler(Looper.getMainLooper()).postDelayed(...) 사용)
		try {
			Handler(Looper.getMainLooper()).removeCallbacksAndMessages(null)
		} catch (_: Throwable) {
		}

		// 3) CameraX Recording 자원 해제
		recording?.let { r ->
			runCatching { r.stop() }
			runCatching { r.close() }
		}
		recording = null

    runCatching { cleanup() }

		// 5) 변환 중 남은 임시 파일들 중 ‘빈 파일/부분 파일’만 정리 (안전 범위)
		runCatching {
			cacheDir?.listFiles()
				?.filter { it.isFile && it.extension.equals("mp4", ignoreCase = true) }
				// 0바이트 또는 아주 작은 부분 파일(예: 64KB 미만)만 제거
				?.filter { it.length() in 0..65535 }
				?.forEach { runCatching { it.delete() } }
		}
	}


	private fun cleanup() {
		try {
			videoEncoder?.let { encoder ->
				try {
					encoder.stop()
				} catch (e: Exception) {
					Log.e("cleanup", "Error stopping encoder: ${e.message}")
				}
				encoder.release()
				videoEncoder = null
			}

			if (::inputSurface.isInitialized) {
				try {
					inputSurface.release()
				} catch (e: Exception) {
					Log.e("cleanup", "Error releasing surface: ${e.message}")
				}
			}

			mediaMuxer?.let { muxer ->
				try {
					muxer.stop()
				} catch (e: Exception) {
					Log.e("cleanup", "Error stopping muxer: ${e.message}")
				}
				muxer.release()
				mediaMuxer = null
			}

			videoTrackIndex = -1
			isRecording = false

			Log.d("cleanup", "Video recording cleanup completed")
		} catch (e: Exception) {
			Log.e("cleanup", "Cleanup failed: ${e.message}")
		}
	}

	private lateinit var inputSurface: Surface

	private fun startDepthCameraRecording(callback: () -> Unit) {
		recordingJob = CoroutineScope(Dispatchers.IO).launch {
			try {
				val outputFile = File(cacheDir, "$timestamp.mp4")
				val width = 1920
				val height = 1080
				val fps = 30
				val bitRate = 6_000_000

				val format =
					MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
						setInteger(
							MediaFormat.KEY_COLOR_FORMAT,
							MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
						)
						setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
						setInteger(MediaFormat.KEY_FRAME_RATE, fps)
						setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
					}

				videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
				videoEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
				inputSurface = videoEncoder!!.createInputSurface()

				mediaMuxer =
					MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
				videoEncoder?.start()
				isRecording = true

				withContext(Dispatchers.Main) {
					startRecording = true
					startRecordingMaxTimer()
					Log.v("startRecording", "startRecording $startRecording")
				}

				val frameInterval = 1000L / fps
				var frameCount = 0
				val bufferInfo = MediaCodec.BufferInfo()
				var trackIndex = -1
				var muxerStarted = false
				val canvasPaint = Paint()

				while (isRecording) {
					val frameStart = System.currentTimeMillis()

					mPipeline?.waitForFrameSet(1000)?.use { frameSet ->
						val colorFrame = frameSet.getColorFrame() ?: return@use

						val dataSize = colorFrame.getDataSize()
						val byteBuffer = ByteBuffer.allocateDirect(dataSize)
						colorFrame.getData(byteBuffer)
						byteBuffer.rewind()

						val pixels = IntArray(width * height)
						for (i in pixels.indices) {
							if (byteBuffer.remaining() >= 3) {
								val r = byteBuffer.get().toInt() and 0xFF
								val g = byteBuffer.get().toInt() and 0xFF
								val b = byteBuffer.get().toInt() and 0xFF
								pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
							}
						}

						val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
						bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

						if (!isRecording || !::inputSurface.isInitialized) return@use

						try {
							val canvas = inputSurface.lockCanvas(null)
							canvas.drawBitmap(bitmap, 0f, 0f, canvasPaint)
							inputSurface.unlockCanvasAndPost(canvas)
						} catch (e: Exception) {
							Log.e("startRecording", "Surface draw failed: ${e.message}")
							isRecording = false
							return@use
						}
					}

					// ✅ encoder 상태 확인 후 dequeue
					val encoder = videoEncoder
					if (encoder == null || !isRecording) break

					try {
						var outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
						while (outputBufferIndex >= 0) {
							val encodedData = encoder.getOutputBuffer(outputBufferIndex)

							if (encodedData != null) {
								if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
									bufferInfo.size = 0
								}

								if (bufferInfo.size != 0) {
									encodedData.position(bufferInfo.offset)
									encodedData.limit(bufferInfo.offset + bufferInfo.size)

									if (!muxerStarted) {
										trackIndex = mediaMuxer!!.addTrack(encoder.outputFormat)
										mediaMuxer!!.start()
										muxerStarted = true
									}

									mediaMuxer?.writeSampleData(trackIndex, encodedData, bufferInfo)
								}
							}

							encoder.releaseOutputBuffer(outputBufferIndex, false)
							outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
						}
					} catch (e: IllegalStateException) {
						Log.e("startRecording", "Encoder already released: ${e.message}")
						break
					}

					frameCount++

					val elapsed = System.currentTimeMillis() - frameStart
					val delay = frameInterval - elapsed
					if (delay > 0) delay(delay)
				}

				stopDepthCameraRecording(outputFile, callback)

			} catch (e: Exception) {
				Log.e("startRecording", "Recording failed: ${e.message}", e)
				withContext(Dispatchers.Main) {
					isRecording = false
					startRecording = false
				}
				// cleanup 호출하지 않음 - stopDepthCameraRecording에서 처리
			}
		}
	}

	private fun stopDepthCameraRecording(outputFile: File, callback: () -> Unit) {
		isRecording = false
		startRecording = false
		Log.v("stopDepthRecording", "stopDepthCameraRecording")

		try {
			// ✅ 순서 중요: encoder -> surface -> muxer
			videoEncoder?.let { encoder ->
				try {
					encoder.stop()
				} catch (e: Exception) {
					Log.e("stopRecording", "Error stopping encoder: ${e.message}")
				}
				encoder.release()
				videoEncoder = null
			}

			if (::inputSurface.isInitialized) {
				try {
					inputSurface.release()
				} catch (e: Exception) {
					Log.e("stopRecording", "Error releasing surface: ${e.message}")
				}
			}

			mediaMuxer?.let { muxer ->
				try {
					muxer.stop()
				} catch (e: Exception) {
					Log.e("stopRecording", "Error stopping muxer: ${e.message}")
				}
				muxer.release()
				mediaMuxer = null
			}

			Log.v("startRecording", "finish Recording")

			// 좌우 반전 적용
			val inputPath = outputFile.absolutePath
			val outputPath = File(cacheDir, "mirrored_video.mp4").absolutePath

			mvm.flipVideoHorizontally(this@DepthCameraActivity, inputPath, outputPath) { success ->
				if (success) {
					saveMediaToStorage(
						this@DepthCameraActivity,
						Uri.fromFile(File(outputPath)),
						getFileName(mvm.currentSeq),
						false
					)
					callback()
				} else {
					Log.e(TAG, "Failed to apply mirror effect to video.")
				}
			}

		} catch (e: Exception) {
			Log.e("stopRecording", "Error stopping recording: ${e.message}", e)
		}
	}

	// --------------------------------------# resultBundle #----------------------------------


	fun resultHsvToJson(
		resultBundle: FaceLandmarkResult,
		targets: List<HsvColorTracker.Target>,
		flr3D: List<Point3D> = listOf()
	) {
		if (resultBundle.landmarks.isNotEmpty()) {
			val flr = resultBundle.landmarks

			val targetFs = targets.map { toFloatTarget(it, baseW = 1920, baseH = 1080, mirrorX = true) }
				.sortedBy { it.cx }
			val ja = JSONArray()
			for (t in targetFs) {
				val obj = JSONObject()
					.put("cx", t.cx)
					.put("cy", t.cy)
				ja.put(obj)
			}
			mvm.resultTargetFs.copyFrom(ja)
//            Log.v("좌표까봐-결과", "resultTargetFs: ${mvm.resultTargetFs}")

			mvm.currentCoordinate.clear()
			mvm.relativeCoordinate.clear()
			mvm.current3DCoordinate.clear()

			flr.forEachIndexed { index, faceLandmark ->
				val scaledX = calculateScreenX(mvm.overlayW, 1f - faceLandmark.x)
				val scaledY = calculateScreenY(mvm.overlayH, faceLandmark.y)
				val x = flr3D.getOrNull(index)?.x ?: 0f
				val y = flr3D.getOrNull(index)?.y ?: 0f
				val z = flr3D.getOrNull(index)?.z ?: 0f

				val ja = JSONArray().apply {
					put(index)
					put(scaledX)
					put(scaledY)
					put(x)
					put(y)
					put(z)
				}
				mvm.tempCoordinateJA.put(ja)
				// 절대좌표
				mvm.currentCoordinate.add(Pair(scaledX, scaledY))
				// 상대좌표

				mvm.relativeCoordinate.add(
					Pair(
						faceLandmark.x,
						faceLandmark.y
					)
				)
			}
			mvm.hsvFLCoordinatesJA.copyFrom(mvm.tempCoordinateJA)

			val vmFlr = mvm.relativeCoordinate
			if (targetFs.size >= 3) {
				val eyeAngle =
					calculateSlope(vmFlr[468].first, vmFlr[468].second, vmFlr[473].first, vmFlr[473].second)
				val lipsAngle =
					calculateSlope(targetFs[0].cx, targetFs[0].cy, targetFs[2].cx, targetFs[2].cy)
				val chinAngle =
					calculateSlope(vmFlr[291].first, vmFlr[291].second, vmFlr[61].first, vmFlr[61].second)

				mvm.hsvColorJo.apply {
					put("bow_eye_horizontal_angle", eyeAngle)
					put("bow_tip_of_lips_horizontal_angle", lipsAngle)
					put("bow_tip_of_chin_horizontal_angle", chinAngle)
				}
//				Log.v("bow각도", "eyeAngle: $eyeAngle chinAngle: $chinAngle")
//				Log.v(
//					"bow각도",
//					"lipsAngle: $lipsAngle, targetFs[0]: [${targetFs[0].cx}, ${targetFs[0].cy}], , targetFs[1]: [${targetFs[1].cx}, ${targetFs[1].cy}]"
//				)
//				Log.v("bow각도", "mvm.hsvColorJo: ${mvm.hsvColorJo}")
			}
		}
	}

	// --------------------------------------# init #-----------------------------------------------
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
						context = this@DepthCameraActivity,
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
				poseLandmarker = PoseLandmarkerHelper(
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

	private fun initHsv() {
		try {
			// HSV Tracker 쓰레드 종료
			Log.d("이닛", "initializing HSV Tracker resources...")
			hsvTracker = HsvColorTracker(
//            hsvRange = PresetHsv.WHITE,
				minBlobAreaPx = 140,
				useOpenMorphology = true,
				smoothFactor = 0.4f
			)
			trackerExecutor = Executors.newSingleThreadExecutor()
			Log.d("이닛", "HSV Tracker resources initialized.")
		} catch (e: Exception) {
			Log.e("이닛", "Failed to initialize HSV Tracker: ${e.message}", e)
		}
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
			if (::poseLandmarker.isInitialized && !poseLandmarker.isClose()) {
				poseLandmarker.clearPoseLandmarker()
			}
			Log.d("릴리즈", "PoseLandmarker released.")
		} catch (e: Exception) {
			Log.e("릴리즈", "Failed to release pose landmarker: ${e.message}", e)
		}
	}

	private fun releaseHsv() {
		try {
			// HSV Tracker 쓰레드 종료
			Log.d("릴리즈", "Releasing HSV Tracker resources...")

			if (::trackerExecutor.isInitialized && !trackerExecutor.isShutdown) {
				trackerExecutor.shutdown()
			}
			Log.d("릴리즈", "HSV Tracker resources released.")
		} catch (e: Exception) {
			Log.e("릴리즈", "Failed to release HSV Tracker: ${e.message}", e)
		}
	}


	override fun finish() {

		requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
		super.finish()
	}

	private fun initSettings() {

		mvm.allSeqs = prefsUtil.getLastMeasureSeqs().sortedBy { it.ordinal }.toMutableList()
		mvm.currentSeq = mvm.allSeqs[0]

		bd.llbtn.apply {
			visibility = View.INVISIBLE
		}
		val seqCount = "${mvm.getSeqIndex() + 1} / ${mvm.allSeqs.size}"

		bd.tvSeqCount.text = seqCount
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
			ivm.mobileValue.value = currentUserItem.userMobile
			ivm.nameValue.value = currentUserItem.userName
			ivm.nationalityValue.value = currentUserItem.userNationality
			ivm.birthdayValue.value = currentUserItem.userBirthday
			ivm.genderValue.value = currentUserItem.userGender
			mvm.currentUserSn = currentUserItem.userSn
		}
		val key = getString(R.string.kekeke)
		mvm.currentUUID = generateCustomUUID(ivm.nameValue.value, ivm.mobileValue.value, key)


		Log.v("가져온게 있나요", "Out: ${ivm.mobileValue.value}, ${ivm.nameValue.value}, ${mvm.currentUserSn}")
		if (ivm.nameValue.value?.isNotEmpty() == true && ivm.mobileValue.value?.isNotEmpty() == true && mvm.currentUserSn != -1) {
			val onBoardingDialog = OnBoardingDialogFragment()
			onBoardingDialog.show(supportFragmentManager, "")
		} else {
			val userDialog = UserSetupDialogFragment()
			userDialog.show(supportFragmentManager, "")
		}
		// flat 초기화
		viewModel.setSeqFinishedFlag(false)
		viewModel.setGuideTextFlag(false)
		viewModel.setCountDownFlag(false)
		viewModel.setTextAnimationFlag(false)
		mvm.currentTarget = listOf()
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
		mvm.etcJO.clearAll()
		mvm.inputWidth = 0
		mvm.inputHeight = 0
		mvm.overlayW = 0f
		mvm.overlayH = 0f
		// 화면 인물 프레임 init
		bd.ivFrame.setImageDrawable(
			ContextCompat.getDrawable(
				this@DepthCameraActivity,
				when (mvm.currentSeq) {
					Sequence.SIDE_LEFT -> R.drawable.drawable_seq_7
					Sequence.SIDE_RIGHT -> R.drawable.drawable_seq_8
					else -> R.drawable.drawable_seq_0
				}
			)
		)
		when (mvm.currentSeq) {
			Sequence.SIDE_LEFT, Sequence.SIDE_RIGHT, Sequence.COMFORT -> {
				bd.fdgv.triggerOffAnimationIfNeeded()
				Handler(Looper.getMainLooper()).postDelayed({ bd.fdgv.visibility = View.INVISIBLE }, 800)
				bd.ivFrame.visibility = View.VISIBLE
				startFadeIn(bd.ivFrame)
			}
			else -> {
				bd.fdgv.visibility = View.VISIBLE
				bd.fdgv.triggerIntroAnimationIfNeeded()
				Handler(Looper.getMainLooper()).postDelayed({ bd.ivFrame.visibility = View.INVISIBLE }, 800)
				startFadeOut(bd.ivFrame)
			}
		}
	}

	private fun setJo(originJo: JSONObject, seq: Sequence): JSONObject {
		val seqInt = when (seq) {
			Sequence.COMFORT -> 0
			Sequence.RESTING -> 1
			Sequence.OCCLUSAL -> 2
			Sequence.JAW_LEFT_TILT -> 3
			Sequence.JAW_RIGHT_TILT -> 4
			Sequence.JAW_OPENING -> 5
			Sequence.NECK_EXTENTION -> 6
			Sequence.SIDE_LEFT -> 7
			Sequence.SIDE_RIGHT -> 8
			Sequence.BOW -> 9
		}
		val seqIndex = if (mvm.allSeqs.contains(Sequence.BOW)) {
			mvm.allSeqs.indexOf(seq) - 1
		} else {
			mvm.allSeqs.indexOf(seq)
		}
		val mediaFileName = if (seqInt == 9) {
			mvm.hsvMediaFileName
		} else {
			mvm.mediaFileNames[seqIndex]
		}

		return originJo.apply {
			put("temp_server_sn", prefsUtil.getLastTempServerSn())
			put("media_file_name", mediaFileName)
			put("json_file_name", mediaFileName.replace(".jpg", ".json").replace(".mp4", ".json"))
			put("seq", seqInt)
			put("uuid", mvm.currentUUID)
			put("name", ivm.nameValue.value)
			put("mobile", ivm.mobileValue.value)
			put("scale_factor", getScaleFactor())
			put("depth_result", true)
		}
	}

	fun createMaterialDialog(context: Context) {
		MaterialAlertDialogBuilder(
			context,
			R.style.ThemeOverlay_App_MaterialAlertDialog
		).apply {
			setTitle("알림")
			setMessage("페이스 보우 촬영을 포함 하시겠습니까? ")
			setPositiveButton("예") { _, _ ->
				initHsv()
				mvm.allSeqs.add(0, Sequence.BOW)
				mvm.currentSeq = mvm.allSeqs[0]
				bd.tvSeqCount.text = "1  / ${mvm.allSeqs.size}"
				bd.ivFrame.visibility = View.INVISIBLE
				bd.fdgv.visibility = View.INVISIBLE
				bd.tvSeqGuide.text = "페이스 보우를 먼저 촬영할게요 !\n보우를 입에 물고 화면 정면을 응시해주세요"
				Log.v("올시퀀", "${mvm.allSeqs}")
				ivm.isFinishInput.removeObservers(this@DepthCameraActivity)
			}
			setNegativeButton("아니오") { _, _ -> }
			show()
		}
	}
}