package com.tangoplus.tangoface_compose.ui.main.vm

import android.app.ActivityManager
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 메인 화면 뷰모델
 * - Orbbec Gemini 336 3D 뎁스 센서 USB 실시간 연결 감지
 * - RAM 사용량 5초 주기 실시간 모니터링
 * - 안면 측정 가이드 코멘트 5초 주기 롤링
 */
class MainViewModel : ViewModel() {

	// 1. 헤더: 3D 뎁스 카메라 실시간 연결 상태
	private val _isDepthConnected = MutableStateFlow(false)
	val isDepthConnected: StateFlow<Boolean> = _isDepthConnected.asStateFlow()

	// 2. 헤더: 실시간 RAM 사용률 (%)
	private val _ramUsage = MutableStateFlow(0)
	val ramUsage: StateFlow<Int> = _ramUsage.asStateFlow()

	// 3. 중앙 상단: 1줄 가이드 슬라이드 롤링 텍스트
	val guideComments = listOf(
		"정밀한 턱관절 분석을 위해 정밀 검사 모드를 권장합니다.",
		"턱관절 비대칭 및 수평축 진단에는 페이스 보우가 효과적입니다.",
		"정확한 3D 뎁스 측정을 위해 카메라와 30~60cm 거리를 유지해 주세요.",
		"측정 시 안경이나 마스크를 벗으면 분석 정확도가 향상됩니다.",
		"빠른 윤곽 밸런스 체크가 필요할 땐 간편 검사를 활용해 보세요.",
		"이전 측정 데이터와 비교해 안면 비대칭 개선도를 확인할 수 있습니다.",
		"측정 중에는 정면을 응시하고 자연스러운 무표정을 유지해 주세요.",
		"3D 스캔 중 고개를 움직이지 않으면 오차 범위를 최소화할 수 있습니다.",
		"턱관절 개구 장애 의심 시 정밀 검사를 추천합니다.",
		"주기적인 검사는 턱관절 불균형 및 자세 변화 추적에 도움이 됩니다."
	)

	private val _currentGuide = MutableStateFlow(guideComments[0])
	val currentGuide: StateFlow<String> = _currentGuide.asStateFlow()

	private var guideIndex = 0
	private var guideJob: Job? = null
	private var ramJob: Job? = null

	/**
	 * USB 장치 목록 갱신 (Orbbec Gemini 336 / Vendor 0x2bc5 감지)
	 */
	fun updateUsbDeviceList(deviceList: HashMap<String, UsbDevice>?) {
		val isConnected = deviceList?.values?.any { device ->
			val isOrbbecVid = device.vendorId == 0x2bc5 || device.vendorId == 11205
			val nameMatches = device.productName?.contains("Gemini", ignoreCase = true) == true ||
				device.productName?.contains("Orbbec", ignoreCase = true) == true ||
				device.manufacturerName?.contains("Orbbec", ignoreCase = true) == true ||
				device.deviceName.contains("Orbbec", ignoreCase = true) ||
				device.deviceName.contains("Gemini", ignoreCase = true)

			android.util.Log.d("USB_CHECK", "Device: ${device.deviceName}, VID: ${device.vendorId}, PID: ${device.productId}, Prod: ${device.productName}, Match: ${isOrbbecVid || nameMatches}")
			isOrbbecVid || nameMatches
		} ?: false

		_isDepthConnected.value = isConnected
	}

	/**
	 * 5초 주기 RAM 점유율 모니터링
	 */
	fun startRamMonitoring(context: Context) {
		ramJob?.cancel()
		ramJob = viewModelScope.launch {
			val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
			val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
			val memInfo = ActivityManager.MemoryInfo()
			while (isActive) {
				actManager.getMemoryInfo(memInfo)
				val totalMem = memInfo.totalMem.toDouble()
				val availMem = memInfo.availMem.toDouble()
				if (totalMem > 0) {
					_ramUsage.value = (((totalMem - availMem) / totalMem) * 100).toInt()
				}
				if (usbManager != null) {
					updateUsbDeviceList(usbManager.deviceList)
				}
				delay(5000L)
			}
		}
	}

	/**
	 * 5초 주기 가이드 코멘트 롤링
	 */
	fun startGuideRolling() {
		guideJob?.cancel()
		guideJob = viewModelScope.launch {
			while (isActive) {
				_currentGuide.value = guideComments[guideIndex]
				guideIndex = (guideIndex + 1) % guideComments.size
				delay(5000L)
			}
		}
	}

	override fun onCleared() {
		super.onCleared()
		ramJob?.cancel()
		guideJob?.cancel()
	}
}

