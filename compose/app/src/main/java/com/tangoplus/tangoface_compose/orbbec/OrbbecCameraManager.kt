package com.tangoplus.tangoface_compose.orbbec

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.orbbec.obsensor.BuildConfig
import com.orbbec.obsensor.Config
import com.orbbec.obsensor.Device
import com.orbbec.obsensor.DeviceChangedCallback
import com.orbbec.obsensor.DeviceList
import com.orbbec.obsensor.HoleFillingFilter
import com.orbbec.obsensor.OBContext
import com.orbbec.obsensor.OBException
import com.orbbec.obsensor.Pipeline
import com.orbbec.obsensor.VideoStreamProfile
import com.orbbec.obsensor.property.DeviceProperty
import com.orbbec.obsensor.types.Format
import com.orbbec.obsensor.types.LogSeverity
import com.orbbec.obsensor.types.SensorType
import com.orbbec.obsensor.types.StreamType
import com.tangoplus.tangoface_compose.domain.util.PreferenceUtility
import com.tangoplus.tangoface_compose.orbbec.view.OBGLView
import java.nio.ByteBuffer

/**
 * tangoface 원본 DepthCameraActivity 및 BaseActivity의 연결 및 리소스 정리 로직을
 * 전적으로 이식한 Orbbec 뎁스 카메라 매니저.
 */
class OrbbecCameraManager(private val context: Context) {
	companion object {
		private const val TAG = "DepthCameraActivity"
		private const val ACTION_USB_PERMISSION = "com.tangoplus.tangoface_compose.USB_PERMISSION"
	}

	// BaseActivity 변수
	private var mOBContext: OBContext? = null

	// DepthCameraActivity 변수
	private var mDevice: Device? = null
	private var mPipeline: Pipeline? = null
	private var mHoleFillingFilter: HoleFillingFilter? = null
	private var mStreamThread: Thread? = null
	@Volatile private var mIsStreamRunning = false
	@Volatile private var mIsDeviceDetached = false
	private val cleanupLock = Any()
	@Volatile private var cleaningUp = false

	private val prefsUtil by lazy { PreferenceUtility(context) }
	private val mainHandler = Handler(Looper.getMainLooper())
	@Volatile private var targetView: OBGLView? = null

	private var isStarted = false

	fun attachView(glView: OBGLView) {
		this.targetView = glView
	}

	fun detachView() {
		this.targetView = null
	}

	/**
	 * 1. 카메라 시작 진입점
	 * - USB 권한 확인: 없으면 시스템 대화상자 요청 후 승인 시 initSDK()
	 * - 이미 권한이 있으면 즉시 initSDK()
	 */
	fun startCamera() {
		if (isStarted) return
		isStarted = true

		val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
		val orbbecDevice = usbManager.deviceList.values.find {
			it.vendorId == 0x2bc5 || it.vendorId == 11205 ||
				it.productName?.contains("Gemini", ignoreCase = true) == true ||
				it.productName?.contains("Orbbec", ignoreCase = true) == true
		}

		if (orbbecDevice != null && !usbManager.hasPermission(orbbecDevice)) {
			Log.i(TAG, "USB 장치 권한 없음 -> 안드로이드 시스템 권한 대화상자 호출: ${orbbecDevice.deviceName}")
			val permissionReceiver = object : BroadcastReceiver() {
				override fun onReceive(c: Context?, intent: Intent?) {
					if (intent?.action == ACTION_USB_PERMISSION) {
						val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
						Log.i(TAG, "USB 시스템 권한 대화상자 응답 결과: granted=$granted")
						try { context.unregisterReceiver(this) } catch (_: Exception) {}
						if (granted && isStarted) {
							initSDK()
						}
					}
				}
			}

			val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_EXPORTED else 0
			context.registerReceiver(permissionReceiver, IntentFilter(ACTION_USB_PERMISSION), flags)

			val pendingIntent = PendingIntent.getBroadcast(
				context,
				0,
				Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
				PendingIntent.FLAG_MUTABLE
			)
			usbManager.requestPermission(orbbecDevice, pendingIntent)
			return
		}

		initSDK()
	}

	/**
	 * [BaseActivity.initSDK] 완전 일치
	 */
	private fun initSDK() {
		try {
			if (BuildConfig.DEBUG) {
				Log.v("initSDK", "is initSDK DEBUG")
				OBContext.setLoggerSeverity(LogSeverity.DEBUG)
			}

			Log.v("initSDK", "is initSDK devicechangedCallback")
			mOBContext = OBContext(context.applicationContext, mDeviceChangedCallback)

			// 이미 연결된 디바이스가 있는 경우 수동 attach 호출
			val deviceList = mOBContext?.queryDevices()
			if (deviceList != null) {
				if (deviceList.deviceCount > 0 && mPipeline == null) {
					Log.v("initSDK", "queryDevices found: ${deviceList.deviceCount}")
					mDeviceChangedCallback.onDeviceAttach(deviceList)
				} else {
					deviceList.close()
				}
			}
		} catch (e: OBException) {
			Log.e(TAG, "initSDK: " + e.message)
		}
	}

	/**
	 * [BaseActivity.releaseSDK] 완전 일치
	 */
	private fun releaseSDK() {
		try {
			if (null != mOBContext) {
				mOBContext!!.close()
				mOBContext = null
			}
		} catch (e: OBException) {
			e.printStackTrace()
		}
	}

	/**
	 * [DepthCameraActivity.setPowerLineFrequency] 완전 일치
	 */
	private fun setPowerLineFrequency(device: Device, value: Int) {
		try {
			device.setPropertyValueI(DeviceProperty.OB_PROP_COLOR_POWER_LINE_FREQUENCY_INT, value)
			val readback = device.getPropertyValueI(DeviceProperty.OB_PROP_COLOR_POWER_LINE_FREQUENCY_INT)
			Log.i("Flicker", "PowerLineFrequency set=$value, readback=$readback")
		} catch (e: Exception) {
			Log.e("Flicker", "Failed to set PowerLineFrequency($value)", e)
		}
	}

	/**
	 * [DepthCameraActivity.mDeviceChangedCallback] 완전 일치
	 */
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
						Log.e(TAG, "This device does not support Color sensor")
						mDevice!!.close()
						mDevice = null
						return
					}
					// 세팅에서 설정한 fps 값을 가져옴 default 60 (or 2)
					val fpsValue = prefsUtil.getFlickerType()
					Log.v("fpsValue", "$fpsValue")
					setPowerLineFrequency(mDevice!!, fpsValue)

					// 3. Create Device and initialize Pipeline through Device
					mPipeline = Pipeline(mDevice)

					val config = Config()
					mPipeline!!.getStreamProfileList(SensorType.COLOR).use { colorProfileList ->
						var colorProfile: VideoStreamProfile? = null
						try {
							colorProfile = colorProfileList.getVideoStreamProfile(1920, 1080, Format.RGB, 30)
						} catch (_: Exception) {
							for (i in 0 until colorProfileList.count) {
								val vp = colorProfileList.getProfile(i).`as`(StreamType.COLOR) as? VideoStreamProfile
								if (vp != null) {
									colorProfile = vp
									break
								}
							}
						}
						if (colorProfile != null) {
							config.enableStream(colorProfile)
							colorProfile.close()
						}
					}

					mPipeline!!.getStreamProfileList(SensorType.DEPTH).use { depthProfileList ->
						var depthProfile: VideoStreamProfile? = null
						try {
							depthProfile = depthProfileList.getVideoStreamProfile(1280, 720, Format.Y16, 30)
						} catch (_: Exception) {
							for (i in 0 until depthProfileList.count) {
								val vp = depthProfileList.getProfile(i).`as`(StreamType.DEPTH) as? VideoStreamProfile
								if (vp != null) {
									depthProfile = vp
									break
								}
							}
						}
						if (depthProfile != null) {
							config.enableStream(depthProfile)
							depthProfile.close()
						}
					}

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
				Log.e(TAG, "onDeviceAttach: " + e.message, e)
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
					mPipeline = null
					mDevice = null
				}
			} finally {
				deviceList.close()
			}
		}
	}

	/**
	 * [DepthCameraActivity.start] 완전 일치
	 */
	private fun start() {
		mIsStreamRunning = true

		if (mStreamThread == null) {
			mStreamThread = Thread(mStreamRunnable).apply {
				name = "DepthCameraStreamThread"
				start()
			}
		}
	}

	/**
	 * [DepthCameraActivity.mStreamRunnable] 완전 일치
	 */
	private val mStreamRunnable = Runnable {
		var buffer: ByteBuffer? = null
		while (mIsStreamRunning && !Thread.currentThread().isInterrupted) {
			if (mIsDeviceDetached) break
			try {
				mPipeline!!.waitForFrameSet(1000).use { frameSet ->
					if (frameSet == null) {
						if (!mIsStreamRunning || mIsDeviceDetached) return@Runnable
						return@use
					}

					val colorFrame = frameSet.getColorFrame()
					colorFrame?.let { frame ->
						val dataSize = frame.getDataSize()
						if (buffer == null || buffer!!.capacity() != dataSize) {
							buffer = ByteBuffer.allocateDirect(dataSize)
						}
						buffer?.clear()
						frame.getData(buffer)

						targetView?.let { glView ->
							buffer?.let { buf ->
								glView.setCenterCrop(true)
								glView.setRotation(90)
								glView.update(
									frame.getWidth(),
									frame.getHeight(),
									StreamType.COLOR,
									frame.getFormat(),
									buf,
									1.0f
								)
							}
						}
					}
				}
			} catch (e: OBException) {
				val msg = e.message.orEmpty()
				if (msg.contains("deactivated", true) || msg.contains("disconnected", true)) {
					break
				}
				break
			} catch (t: Throwable) {
				break
			}
		}
	}

	/**
	 * [DepthCameraActivity.stopAndJoinFrameLoop] 완전 일치
	 */
	private fun stopAndJoinFrameLoop(timeoutMs: Long = 1500L): Boolean {
		mIsStreamRunning = false
		val t = mStreamThread ?: return true

		try {
			t.interrupt()
		} catch (_: Throwable) {}

		return try {
			t.join(timeoutMs)
			val finished = !t.isAlive
			if (finished) {
				mStreamThread = null
			}
			finished
		} catch (_: InterruptedException) {
			false
		}
	}

	/**
	 * [DepthCameraActivity.requestCleanup] 완전 일치
	 */
	private fun requestCleanup(reason: String) {
		if (cleaningUp) return
		cleaningUp = true

		synchronized(cleanupLock) {
			try {
				Log.w(TAG, "Cleanup start: $reason")
				stopAndJoinFrameLoop(2000)

				if (reason == "USB_DETACHED") {
					mPipeline = null
					mDevice = null
					Log.w(TAG, "Detach cleanup: skip pipeline/device close to avoid native crash")
					return
				}

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

	/**
	 * 카메라 정지 및 리소스 완전 해제 ([DepthCameraActivity.onDestroy] 일치)
	 */
	fun stopCamera() {
		isStarted = false
		requestCleanup("ACTIVITY_DESTROY")
		releaseSDK()
		detachView()
		Log.d(TAG, "OrbbecCameraManager stopped completely")
	}
}
