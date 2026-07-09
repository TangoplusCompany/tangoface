package com.tangoplus.facebeautyexpert.ui.measure

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.orbbec.obsensor.types.Format
import com.orbbec.obsensor.types.FrameType
import com.orbbec.obsensor.types.SensorType
import com.orbbec.obsensor.types.StreamType
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.databinding.ActivityDepthCameraBinding
import com.tangoplus.facebeautyexpert.ui.view.OverlayView
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.calculateScaleFromPart
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.calculateSlope
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceBlendshapesResultAdapter
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkerHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.calculateAngle
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.getNormalizedDistance
import com.tangoplus.facebeautyexpert.domain.util.MathHelpers.setScaleFactor
import com.tangoplus.facebeautyexpert.domain.util.MeasureUtility.showGoMainDialog
import com.tangoplus.facebeautyexpert.domain.util.PreferenceUtility
import com.tangoplus.facebeautyexpert.domain.util.SecurityUtility.generateCustomUUID
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkAdapter
import com.tangoplus.facebeautyexpert.domain.vision.pose.PoseLandmarkerHelper
import com.tangoplus.facebeautyexpert.domain.vo.Sequence
import com.tangoplus.facebeautyexpert.orbbec.BaseActivity
import com.tangoplus.facebeautyexpert.ui.measure.vm.InputViewModel
import com.tangoplus.facebeautyexpert.ui.measure.vm.MeasureViewModel
import java.nio.ByteBuffer
import kotlin.lazy
import kotlin.math.abs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.orbbec.obsensor.VideoStreamProfile
import com.tangoplus.facebeautyexpert.domain.opencv.HsvColorTracker
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.colorFrameToBitmapSafe
import com.tangoplus.facebeautyexpert.domain.util.LandmarkUtility.scaleAndCropIntrinsicCenterCrop
import com.tangoplus.facebeautyexpert.domain.util.clearAll
import com.tangoplus.facebeautyexpert.domain.vo.MeasureType
import com.tangoplus.facebeautyexpert.ui.main.vm.MainViewModel
import com.tangoplus.facebeautyexpert.ui.measure.type.MBowFragment
import com.tangoplus.facebeautyexpert.ui.measure.type.MExpertFragment
import com.tangoplus.facebeautyexpert.ui.measure.type.MNormalFragment
import kotlin.use

class DepthCameraActivity : BaseActivity(), FaceLandmarkerHelper.LandmarkerListener {

	private lateinit var bd: ActivityDepthCameraBinding
	companion object {
		private const val TAG = "Face Landmarker"
	}
	// ------------# pipeline #------------
	private val viewModel: MainViewModel by viewModels()
	private val mvm: MeasureViewModel by viewModels()
	private val ivm: InputViewModel by viewModels()
	private val faceBlendshapesResultAdapter by lazy {
		FaceBlendshapesResultAdapter()
	}
	// bow
	private lateinit var hsvTracker: HsvColorTracker
	private lateinit var trackerExecutor: ExecutorService
	// landmarker helper
	private lateinit var backgroundExecutor: ExecutorService

	private lateinit var prefsUtil: PreferenceUtility

	private var isInit = false
	override fun getDeviceChangedCallback(): DeviceChangedCallback {
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
			if (null != mvm.mPipeline) {
				mvm.mPipeline!!.close()
			}
			if (mvm.mDevice != null) {
				mvm.mDevice!!.close()
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
				mvm.mIsDeviceDetached = false
				requestCleanup("onDeviceAttach")
				if (null == mvm.mPipeline) {
					// 2.Create Device and initialize Pipeline through Device
					mvm.mDevice = deviceList.getDevice(0)

					val colorSensor = mvm.mDevice!!.getSensor(SensorType.COLOR)
					if (null == colorSensor) {
						showToast("This device does not support Color sensor")
						mvm.mDevice!!.close()
						mvm.mDevice = null
						return
					}
					// 세팅에서 설정한 fps 값을 가져옴 default 60
					val fpsValue = prefsUtil.getFlickerType()
					Log.v("fpsValue", "$fpsValue")
					setPowerLineFrequency(mvm.mDevice!!, fpsValue)
					// 3. Create Device and initialize Pipeline through Device
					mvm.mPipeline = Pipeline(mvm.mDevice)

//					Log.v("리스트", "${mvm.mDevice?.getSupportedPropertyList()?.map { "${it.propertyName}, ${it.property.value()}, ${it.propertyType}" }}")

					val config = Config()
//					mDevice?.setPropertyValueB(DeviceProperty.OB_PROP_DEPTH_ALIGN_HARDWARE_BOOL, true)
					mvm.mPipeline!!.getStreamProfileList(SensorType.COLOR).use { colorProfileList ->
						for (i in 0 until colorProfileList.count) {
							val p = colorProfileList.getProfile(i)
//							Log.v("Profiles", "type=${p.type}")
							val vp = p.`as`(StreamType.COLOR) as? VideoStreamProfile
//							Log.v("Profiles", "${vp?.width}x${vp?.height}")
//							Log.v("ColorProfiles", "color: ${vp?.width}x${vp?.height} fmt=${vp?.format} fps=${vp?.fps}")
						}
						val colorProfile = colorProfileList.getVideoStreamProfile(1920, 1080, Format.RGB, 30)
						config.enableStream(colorProfile)
						colorProfile.close()
					}

					mvm.mPipeline!!.getStreamProfileList(SensorType.DEPTH).use { depthProfileList ->
						for (i in 0 until depthProfileList.count) {
							val p = depthProfileList.getProfile(i)
//							Log.v("Profiles", "type=${p.type}")
							val vp = p.`as`(StreamType.DEPTH) as? VideoStreamProfile
//							Log.v("Profiles", "${vp?.width}x${vp?.height}")
//							Log.v("DepthProfiles", "depth: ${vp?.width}x${vp?.height} fmt=${vp?.format} fps=${vp?.fps}")
						}
						val depthProfile = depthProfileList.getVideoStreamProfile(1280, 720, Format.Y16, 30)
						config.enableStream(depthProfile)
						depthProfile.close()
					}
//					config.setAlignMode(AlignMode.ALIGN_D2C_HW_MODE)

					mvm.mPipeline!!.start(config)

					val deviceInfo = mvm.mDevice!!.info
					val serialNumber = deviceInfo.serialNumber
					val uid = deviceInfo.uid

					try {
						mvm.mHoleFillingFilter = HoleFillingFilter(serialNumber)
					} catch (e: Exception) {
						Log.e(TAG, "Failed with serial, trying uid: " + e.message)
						try {
							mvm.mHoleFillingFilter = HoleFillingFilter(uid)
						} catch (e2: Exception) {
							Log.e(TAG, "Failed with uid too: " + e2.message)
						}
					}
					config.close()
					start()
				}
			} catch (e: Exception) {
				Log.e(TAG, "onDeviceAttach: " + e.printStackTrace())
				MaterialAlertDialogBuilder(this@DepthCameraActivity, R.style.MaterialAlertDialog_App).apply {
					setTitle("경고")
					setMessage("PD포트가 고속충전 상태입니다. 충전단자를 제거하고, 카메라화면을 켠 후 다시 재연결 해주세요.")
					setPositiveButton("예") { _, _ ->
						finishAffinity()
					}
				}
			} finally {
				// 9.Release device list resources
				deviceList.close()
			}
		}

		override fun onDeviceDetach(deviceList: DeviceList) {
			try {
				mvm.mIsDeviceDetached = true
				val loopStopped = stopAndJoinFrameLoop(1500)

				if (loopStopped) {
					try { mvm.mPipeline?.stop() } catch (_: Throwable) {}
					try { mvm.mPipeline?.close() } catch (_: Throwable) {}
					mvm.mPipeline = null
					try { mvm.mDevice?.close() } catch (_: Throwable) {}
					mvm.mDevice = null
				} else {
					// 루프가 안 죽었으면 close 시도 자체가 크래시 확률을 올립니다
					mvm.mPipeline = null
					mvm.mDevice = null
				}
			} finally {
				deviceList.close()
			}
		}
	}


	private fun showToast(msg: String?) {
		runOnUiThread {
			Toast.makeText(this@DepthCameraActivity, msg, Toast.LENGTH_SHORT).show()
		}
	}
	private val cleanupLock = Any()
	@Volatile private var cleaningUp = false

	override fun onDestroy() {
		super.onDestroy()
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
						mvm.mPipeline = null
						mvm.mDevice = null

						Log.w(TAG, "Detach cleanup: skip pipeline/device close to avoid native crash")
						return@runOnUiThread
					}

					// 3) 정상 종료인 경우에만 close
					try { mvm.mPipeline?.stop() } catch (_: Throwable) {}
					try { mvm.mPipeline?.close() } catch (_: Throwable) {}
					mvm.mPipeline = null
					try { mvm.mDevice?.close() } catch (_: Throwable) {}
					mvm.mDevice = null

				} finally {
					cleaningUp = false
					Log.w(TAG, "Cleanup done: $reason")
				}
			}
		}
	}
	// ---------------------------------# stream 시작 #-------------------------------------------

	private fun start() {
		mvm.mIsStreamRunning = true

		if (mvm.mStreamThread == null) {
			mvm.mStreamThread = Thread(mStreamRunnable)
			mvm.mStreamThread!!.start()
		}
	}


	private var isIntrinsicInitialized = false
	private val mainHandler = Handler(Looper.getMainLooper())
	private val mStreamRunnable = Runnable {
		var buffer: ByteBuffer? = null
		while (mvm.mIsStreamRunning && !Thread.currentThread().isInterrupted) {

			if (mvm.mIsDeviceDetached) break

			val pipeline = mvm.mPipeline ?: break
			try {
				mvm.mPipeline!!.waitForFrameSet(1000).use { frameSet ->
					if (frameSet == null) {
						// stop 요청이 들어왔으면 빠져나감
						if (!mvm.mIsStreamRunning || mvm.mIsDeviceDetached) return@Runnable
						return@use
					}

					// Intrinsic 초기화...
					if (!isIntrinsicInitialized) {
						val params = mvm.mDevice?.calibrationCameraParamList
						if (params != null) {
							val paramsUnit = params.getCameraParam(0) // 캘리브 원해상도 기준

							val base = paramsUnit.colorIntrinsic
							mvm.colorDistortion = paramsUnit.colorDistortion
							mvm.depthDistortion = paramsUnit.depthDistortion
							val targetW = 1920
							val targetH = 1080

							// ✅ 종횡비 보정 포함 (CENTER_CROP 가정)
							mvm.scaledIntrinsic = scaleAndCropIntrinsicCenterCrop(
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
							bd.obglvC.setRotation(0)
							bd.obglvC.update(
								frame.getWidth(),
								frame.getHeight(),
								StreamType.COLOR,
								frame.getFormat(),
								buffer,
								1.0f
							)

							bitmap = colorFrameToBitmapSafe(frame, 0)

							bitmap?.let { bmp ->
								// 1️⃣ MPImage 생성 (Face Detection용)

								mpImage = BitmapImageBuilder(bmp).build()


								val timestamp = System.currentTimeMillis()
								mainHandler.post {
									Log.v("결과가 나오긴함?", "${bitmap} | ${mvm.flh} ")
									if (mvm.flh != null && !mvm.flh?.isClose()!!) {
										mvm.flh!!.detectAsync(mpImage!!, timestamp)
									}
								}

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
					} finally {
						colorFrame?.close()  // Frame 해제
					}

					// ===== Depth Frame 처리 =====
					val originalDepthFrame = frameSet.getDepthFrame()
					var depthFrame: DepthFrame? = null

					try {
						depthFrame = if (originalDepthFrame != null && mvm.mHoleFillingFilter != null) {
							try {
								val filtered = mvm.mHoleFillingFilter?.process(originalDepthFrame)
								val filteredDepth = filtered?.`as`(FrameType.DEPTH) as DepthFrame
								originalDepthFrame.close()
								filteredDepth
							} catch (e: Exception) {
								Log.e(TAG, "Filter failed: ${e.message}")
								originalDepthFrame
							}
						} else {
							originalDepthFrame
						}

						depthFrame?.let { frame ->
							if (isInit) return@let

							// TODO 실시간 STREAM에서는 사용하지는 않음.
//							val w = frame.width
//							val h = frame.height
//							val depthByteData = ByteArray(w * h * 2)
//							frame.getData(depthByteData)
//
//							val depthData = ShortArray(w * h)
//							for (i in 0 until w * h) {
//								val lowByte = depthByteData[i * 2].toInt() and 0xFF
//								val highByte = depthByteData[i * 2 + 1].toInt() and 0xFF
//								depthData[i] = (highByte shl 8 or lowByte).toShort()
//							}
							// -> captureImage() 시점에만 계산
						}
					} finally {
						depthFrame?.close()
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
		mvm.mIsStreamRunning = false

		val t = mvm.mStreamThread ?: return true

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
				mvm.mStreamThread = null
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

		// ☀️☀️☀️☀️ measureType ☀️☀️☀️☀️
		val selectedMeasureType = IntentCompat.getSerializableExtra(intent, "selectedMeasureType", MeasureType::class.java)
			?: MeasureType.NORMAL
		mvm.measureType = selectedMeasureType
		val selectedFragment = when (mvm.measureType) {
			MeasureType.NORMAL -> MNormalFragment()
			MeasureType.EXPERT -> MExpertFragment()
			MeasureType.BOW -> MBowFragment()
			else -> MNormalFragment()
		}
		supportFragmentManager.beginTransaction().apply {
			replace(R.id.flDC, selectedFragment)
			commit()
		}
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
			Log.v("인풋", "2: ${ivm.nameValue.value}, ${ivm.mobileValue.value}, ${ivm.genderValue.value}, ${ivm.birthdayValue.value}, ${ivm.nationalityValue.value}")
		}
		prefsUtil = PreferenceUtility(this@DepthCameraActivity)

		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				showGoMainDialog(this@DepthCameraActivity)
			}
		})

		// 스캔 효과 끄기 observer
		viewModel.isScanning.observe(this) {
			if (!it) {
				bd.overlay.setOnScanFinishedListener {
					bd.overlay.setOnScanFinishedListener {}
				}
				bd.overlay.stopScanAnimation()
				bd.tvGoGallery.isEnabled = true
			} else {
				bd.tvGoGallery.isEnabled = false
			}
		}
		bd.tvGoGallery.setOnSingleClickListener {
			showGoMainDialog(this)
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
	override fun onResults(
		resultBundle: FaceLandmarkerHelper.ResultBundle
	) {
//        Log.v(TAG, "✅ onResults 호출됨! 스레드: ${Thread.currentThread().name}")
//        Log.v("결과가 나오긴함?", "onResult is started. ${resultBundle.result.faceLandmarks()[0].size}")

		runOnUiThread {
			mvm.face2DResult = FaceLandmarkAdapter.toCustomFaceLandmarkResult(resultBundle.result)
			if (mvm.face2DResult != null) {
				// depthCamera Stream 좌우반전 대응 - 얼굴 랜드마크 좌우 반전
				val flippedLandmarks = mvm.face2DResult!!.landmarks.map { landmark ->
					landmark.copy(x = 1.0f - landmark.x)
				}
				val overlayResult = mvm.face2DResult!!.copy(landmarks = flippedLandmarks)

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
			// Force a redraw
			bd.overlay.invalidate()

//			if (ivm.isFinishInput.value == true && resultBundle.result.faceLandmarks()
//					.isNotEmpty() && !viewModel.getSeqFinishedFlag()
//			) { // && isFaceCenter
//				// 애니메이션 제거 flag
//				if (!viewModel.getGuideTextFlag()) {
//					bd.lavCamera.clearAnimation()
//					bd.lavCamera.visibility = View.INVISIBLE
//					bounceAnimator?.cancel()
//					bounceAnimator = null
//					bd.tvSeqGuide.animate().cancel()
//					bd.tvSeqGuide.clearAnimation()
//					bd.tvSeqGuide.translationY = 0f
//					bd.tvSeqGuide.text = "안녕하세요!\n${getGuideText(mvm.currentSeq)}"
//					viewModel.setGuideTextFlag(true)
//				}
//			}
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

	// --------------------------------------# init #-----------------------------------------------
	private fun initFace() {
		Log.d("이닛", "initializing FaceLandmarker resources...")

		if (!::backgroundExecutor.isInitialized || backgroundExecutor.isShutdown) {
			backgroundExecutor = Executors.newSingleThreadExecutor()
		}

		backgroundExecutor.execute {
			try {
				// 1. 메모리 누수 방지를 위해 applicationContext 사용
				mvm.flh = FaceLandmarkerHelper(
					context = applicationContext,
					runningMode = RunningMode.LIVE_STREAM,
					minFaceDetectionConfidence = viewModel.currentMinFaceDetectionConfidence,
					minFaceTrackingConfidence = viewModel.currentMinFaceTrackingConfidence,
					minFacePresenceConfidence = viewModel.currentMinFacePresenceConfidence,
					maxNumFaces = viewModel.currentMaxFaces,
					currentDelegate = viewModel.currentDelegate,
					faceLandmarkerHelperListener = this@DepthCameraActivity
				)
				// 2. 생성이 완전히 완료된 시점에 백그라운드에서 로그 확인
				Log.d("이닛", "FaceLandmarker 실제 생성 완료: ${mvm.flh}")
			} catch (e: Exception) {
				// 3. 백그라운드 내부 에러를 여기서 잡아야 왜 null인지 원인이 나옵니다.
				Log.e("이닛", "FaceLandmarker 실제 생성 실패 원인: ${e.message}", e)
			}
		}
	}

	private fun initPose() {
		try {
			Log.d("이닛", "initializing PoseLandmarker resources...")
			// 이제 this가 완전히 초기화된 상태
			try {
				mvm.plh = PoseLandmarkerHelper(
					context = this@DepthCameraActivity,
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
			mvm.flh?.clearFaceLandmarker()

			Log.d("릴리즈", "FaceLandmarker released.")
		} catch (e: Exception) {
			Log.e("릴리즈", "Failed to release face landmarker: ${e.message}", e)
		}
	}

	private fun releasePose() {
		try {
			Log.d("릴리즈", "Releasing PoseLandmarker resources...")
			if (mvm.plh?.isClose() == false) {
				mvm.plh?.clearPoseLandmarker()
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

//		mvm.allSeqs = prefsUtil.getLastMeasureSeqs().sortedBy { it.ordinal }.toMutableList()
		mvm.currentSeq = mvm.allSeqs[0]
		mvm.isDepthCameraMeasure = true
//		bd.tvPrevious.apply {
//			visibility = View.INVISIBLE
//		}
//		val seqCount = "${mvm.getSeqIndex() + 1} / ${mvm.allSeqs.size}"
//		bd.tvSeqCount.text = seqCount
		mvm.mediaFileNames.clear()
		mvm.currentCoordinate = mutableListOf()
		ivm.nameValue.value = ""
		ivm.mobileValue.value = ""
		ivm.isShownBtn = false
		viewModel.comparisonDoubleItem = null

//		val currentUserItem = intent.getParcelableExtra<UserItem>("currentUserItem")
//		Log.v("currentUserItem", "is init? = $currentUserItem, ${currentUserItem == null}")
//		if (currentUserItem != null) {
//			ivm.mobileValue.value = currentUserItem.mobile
//			ivm.nameValue.value = currentUserItem.name
//			ivm.nationalityValue.value = currentUserItem.nationality
//			ivm.birthdayValue.value = currentUserItem.birthday
//			ivm.genderValue.value = currentUserItem.gender
//		}
		val key = getString(R.string.kekeke)
		mvm.currentUUID = generateCustomUUID(ivm.nameValue.value, ivm.mobileValue.value, ivm.birthdayValue.value,ivm.genderValue.value,key)

		Log.v("가져온게 있나요", "Out: ${ivm.mobileValue.value}, ${ivm.nameValue.value}, ${mvm.currentUserSn}")
		if (ivm.nameValue.value?.isNotEmpty() == true && ivm.mobileValue.value?.isNotEmpty() == true && mvm.currentUserSn != -1) {
			val onBoardingDialog = OnBoardingDialogFragment()
			onBoardingDialog.show(supportFragmentManager, "")
		} else {
			val userDialog = UserSetupDialogFragment.newInstance(0)
			userDialog.show(supportFragmentManager, "")
		}
		// flat 초기화
		viewModel.finishSequence()
		viewModel.setGuideTextFlag(false)
		viewModel.setCountDownFlag(false)
		viewModel.setTextAnimationFlag(false)
		mvm.staticJA.clearAll()
		mvm.coordinatesJA.clearAll()
		mvm.mediaFileNames.clear()
		mvm.plrJA.clearAll()
		mvm.hairLineJA.clearAll()
		mvm.inputWidth = 0
		mvm.inputHeight = 0
		mvm.overlayW = 0f
		mvm.overlayH = 0f
		// 화면 인물 프레임 init
//		bd.ivFrame.setImageDrawable(
//			ContextCompat.getDrawable(
//				this@DepthCameraActivity,
//				R.drawable.drawable_seq_0
//			)
//		)
	}
}