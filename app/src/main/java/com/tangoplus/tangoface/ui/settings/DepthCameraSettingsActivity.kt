package com.tangoplus.tangoface.ui.settings

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.orbbec.obsensor.AccelFrame
import com.orbbec.obsensor.AccelStreamProfile
import com.orbbec.obsensor.Config
import com.orbbec.obsensor.DepthFrame
import com.orbbec.obsensor.DeviceChangedCallback
import com.orbbec.obsensor.DeviceList
import com.orbbec.obsensor.HoleFillingFilter
import com.orbbec.obsensor.Pipeline
import com.orbbec.obsensor.VideoStreamProfile
import com.orbbec.obsensor.types.AccelFullScaleRange
import com.orbbec.obsensor.types.Format
import com.orbbec.obsensor.types.FrameType
import com.orbbec.obsensor.types.IMUSampleRate
import com.orbbec.obsensor.types.SensorType
import com.orbbec.obsensor.types.StreamType
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.ActivityDepthCameraBinding
import com.tangoplus.tangoface.databinding.ActivityDepthCameraSettingsBinding
import com.tangoplus.tangoface.domain.util.LandmarkUtility.scaleAndCropIntrinsicCenterCrop
import com.tangoplus.tangoface.orbbec.BaseActivity
import com.tangoplus.tangoface.ui.measure.vm.MeasureViewModel
import java.nio.ByteBuffer
import kotlin.getValue
import kotlin.math.atan2
import kotlin.math.sqrt

class DepthCameraSettingsActivity : BaseActivity() {
	private lateinit var bd : ActivityDepthCameraSettingsBinding
	private val mvm: MeasureViewModel by viewModels()
	private var isInit = false

	companion object {
		private const val TAG = "cameraSettings"
	}
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		bd = ActivityDepthCameraSettingsBinding.inflate(layoutInflater)
		WindowCompat.setDecorFitsSystemWindows(window, false)

		setContentView(bd.root)
		setTitle("Stream-Color")
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

		val controller = WindowCompat.getInsetsController(window, window.decorView)
		controller.apply {
			hide(WindowInsetsCompat.Type.systemBars())
			systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
	override fun onDestroy() {
		super.onDestroy()
		requestCleanup("ACTIVITY_DESTROY")
		releaseSDK()
	}

	override fun getDeviceChangedCallback(): DeviceChangedCallback {
		return mDeviceChangedCallback
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
						mvm.mDevice!!.close()
						mvm.mDevice = null
						return
					}
					mvm.mPipeline = Pipeline(mvm.mDevice)

//					Log.v("리스트", "${mvm.mDevice?.getSupportedPropertyList()?.map { "${it.propertyName}, ${it.property.value()}, ${it.propertyType}" }}")

					val config = Config()
//					mDevice?.setPropertyValueB(DeviceProperty.OB_PROP_DEPTH_ALIGN_HARDWARE_BOOL, true)
					mvm.mPipeline!!.getStreamProfileList(SensorType.COLOR).use { colorProfileList ->
						for (i in 0 until colorProfileList.count) {
							val p = colorProfileList.getProfile(i)
//							Log.v("Profiles", "type=${p.type}")
//							val vp = p.`as`(StreamType.COLOR) as? VideoStreamProfile
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
//							val vp = p.`as`(StreamType.DEPTH) as? VideoStreamProfile
//							Log.v("Profiles", "${vp?.width}x${vp?.height}")
//							Log.v("DepthProfiles", "depth: ${vp?.width}x${vp?.height} fmt=${vp?.format} fps=${vp?.fps}")
						}
						val depthProfile = depthProfileList.getVideoStreamProfile(1280, 720, Format.Y16, 30)
						config.enableStream(depthProfile)
						depthProfile.close()
					}
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
				MaterialAlertDialogBuilder(this@DepthCameraSettingsActivity, R.style.MaterialAlertDialog_App).apply {
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

	private var isIntrinsicInitialized = false
	private val mainHandler = Handler(Looper.getMainLooper())
	private val mStreamRunnable = Runnable {
		var buffer: ByteBuffer? = null
		while (mvm.mIsStreamRunning && !Thread.currentThread().isInterrupted) {

			if (mvm.mIsDeviceDetached) break
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

					try {
						colorFrame?.let { frame ->
							// ByteBuffer 처리
							val dataSize = frame.getDataSize()
							if (buffer == null || buffer!!.capacity() != dataSize) {
								buffer = ByteBuffer.allocateDirect(dataSize)
							}
							buffer?.clear()
							frame.getData(buffer)
							bd.obglvDCS.setCenterCrop(true)
							bd.obglvDCS.setRotation(90)
							bd.obglvDCS.update(
								frame.getWidth(),
								frame.getHeight(),
								StreamType.COLOR,
								frame.getFormat(),
								buffer,
								1.0f
							)
//							bitmap = colorFrameToBitmapSafe(frame, 90)
//							bitmap?.let { bmp ->
//								// 1️⃣ MPImage 생성 (Face Detection용)
//
//								mpImage = BitmapImageBuilder(bmp).build()
//
//								val timestamp = System.currentTimeMillis()
//								mainHandler.post {
////									Log.v("결과가 나오긴함?", "${bitmap} | ${mvm.flh} ")
//									if (mvm.flh != null && !mvm.flh?.isClose()!!) {
//										mvm.flh!!.detectAsync(mpImage!!, timestamp) // 270도 각도 전달 안 함!
//									}
//								}
//							}
						}
					} finally {
						colorFrame?.close()  // Frame 해제
					}

					// ------! Depth Frame 처리 !------
//					val originalDepthFrame = frameSet.getDepthFrame()
//					var depthFrame: DepthFrame? = null
//
//					try {
//						depthFrame = if (originalDepthFrame != null && mvm.mHoleFillingFilter != null) {
//							try {
//								val filtered = mvm.mHoleFillingFilter?.process(originalDepthFrame)
//								val filteredDepth = filtered?.`as`(FrameType.DEPTH) as DepthFrame
//								originalDepthFrame.close()
//								filteredDepth
//							} catch (e: Exception) {
//								Log.e(TAG, "Filter failed: ${e.message}")
//								originalDepthFrame
//							}
//						} else {
//							originalDepthFrame
//						}
//					} finally {
//						depthFrame?.close()
//					}
					// ------! Accel !-------
					val accelSensor = mvm.mDevice?.getSensor(SensorType.ACCEL)
					if (accelSensor != null) {
						val accelProfileList = accelSensor.streamProfileList
						val accelProfile = accelProfileList.getAccelStreamProfile(
							AccelFullScaleRange.FS_UNKNOWN,
							IMUSampleRate.ODR_50_HZ
						)

						accelSensor.start(accelProfile) { frame ->
							val accelFrame = frame.`as`<AccelFrame>(FrameType.ACCEL)
							if (accelFrame != null) {
								val currentTime = System.currentTimeMillis()

								// 25Hz 주기 (40ms마다 1회 연산 및 UI 업데이트)
								if (currentTime - lastUpdateTime >= 80) {
									lastUpdateTime = currentTime

									val accelData = accelFrame.getAccelData()
									val ax = accelData[0]
									val ay = accelData[1]
									val az = accelData[2]

									val pitchRad = atan2(-ax.toDouble(), sqrt((ay * ay + az * az).toDouble()))
									val pitchDeg = Math.toDegrees(pitchRad)

									val rollRad = atan2(ay.toDouble(), az.toDouble())
									var rollDeg = Math.toDegrees(rollRad)

									rollDeg -= 90.0
									if (rollDeg > 180) rollDeg -= 360
									if (rollDeg < -180) rollDeg += 360

									bd.tvDCSPitch.post {
										bd.tvDCSPitch.text = String.format("Pitch: %.1f°", pitchDeg )
									}
								}
								accelFrame.close() // 프레임 메모리 해제 필수
							}
						}
					}

//					accelFrame?.let { frame ->
//						val accelData = frame.getAccelData() // [x, y, z] 크기 3짜리 float 배열 반환
//
//						if (accelData != null && accelData.size >= 3) {
//							val ax = accelData[0] // X축
//							val ay = accelData[1] // Y축
//							val az = accelData[2] // Z축
//
//							// 90도 회전(세로 모드) 기준 Pitch / Roll 계산
//							val pitchRad = atan2(-ax.toDouble(), sqrt((ay * ay + az * az).toDouble()))
//							val pitchDeg = Math.toDegrees(pitchRad)
//
//							val rollRad = atan2(ay.toDouble(), az.toDouble())
//							val rollDeg = Math.toDegrees(rollRad)
//							bd.tvDCSGoGallery.text = "Pitch(위아래): $pitchDeg , Roll(수평): $rollDeg"
////							Log.v("IMU", "Pitch(위아래): $pitchDeg , Roll(수평): $rollDeg")
//						}
//					}
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
	private var lastUpdateTime = 0L
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

	private val cleanupLock = Any()
	@Volatile private var cleaningUp = false
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

	private fun start() {
		mvm.mIsStreamRunning = true

		if (mvm.mStreamThread == null) {
			mvm.mStreamThread = Thread(mStreamRunnable)
			mvm.mStreamThread!!.start()
		}
	}

}