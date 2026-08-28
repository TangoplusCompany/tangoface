package com.tangoplus.tangoface.ui.main.vm

import android.app.ActivityManager
import android.content.Context
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangoplus.tangoface.data.db.FaceDao
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.data.db.FaceShape
import com.tangoplus.tangoface.data.db.FaceStatic
import com.tangoplus.tangoface.domain.util.FileUtility
import com.tangoplus.tangoface.domain.util.FileUtility.getImageUriFromFileName
import com.tangoplus.tangoface.domain.util.FileUtility.getVideoUriFromFileName
import com.tangoplus.tangoface.domain.vision.face.FaceLandmarkerHelper
import com.tangoplus.tangoface.domain.vo.FaceResult
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.ui.info.vo.DateDisplay
import com.tangoplus.tangoface.ui.info.vo.Factor
import com.tangoplus.tangoface.ui.info.vo.HistoryMappingItem
import com.tangoplus.tangoface.ui.main.vo.AlertItem
import com.tangoplus.tangoface.ui.main.vo.UserItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.lang.Math.random
import java.text.Collator
import java.util.Locale

class MainViewModel : ViewModel() {

	private var _delegate: Int = FaceLandmarkerHelper.Companion.DELEGATE_CPU
	private var _minFaceDetectionConfidence: Float =
		FaceLandmarkerHelper.Companion.DEFAULT_FACE_DETECTION_CONFIDENCE
	private var _minFaceTrackingConfidence: Float = FaceLandmarkerHelper.Companion
		.DEFAULT_FACE_TRACKING_CONFIDENCE
	private var _minFacePresenceConfidence: Float = FaceLandmarkerHelper.Companion
		.DEFAULT_FACE_PRESENCE_CONFIDENCE
	private var _maxFaces: Int = FaceLandmarkerHelper.Companion.DEFAULT_NUM_FACES

	val currentDelegate: Int get() = _delegate
	val currentMinFaceDetectionConfidence: Float
		get() =
			_minFaceDetectionConfidence
	val currentMinFaceTrackingConfidence: Float
		get() =
			_minFaceTrackingConfidence
	val currentMinFacePresenceConfidence: Float
		get() =
			_minFacePresenceConfidence
	val currentMaxFaces: Int get() = _maxFaces

	private var isStartCountDown = false

	private var isGuideTextAnimationFinished = false

	private var isGuideTextChanged = false



	private var isContourSave = false
	fun setContourSave(contourSave: Boolean) {
		isContourSave = contourSave
	}

//	fun getContourSave(): Boolean = isContourSave
//
//
//	fun setDelegate(delegate: Int) {
//		_delegate = delegate
//	}
//
//	fun setMinFaceDetectionConfidence(confidence: Float) {
//		_minFaceDetectionConfidence = confidence
//	}
//
//	fun setMinFaceTrackingConfidence(confidence: Float) {
//		_minFaceTrackingConfidence = confidence
//	}
//
//	fun setMinFacePresenceConfidence(confidence: Float) {
//		_minFacePresenceConfidence = confidence
//	}
//
//	fun setMaxFaces(maxResults: Int) {
//		_maxFaces = maxResults
//	}

	fun setCountDownFlag(isStart: Boolean) {
		isStartCountDown = isStart
	}

	fun getCountDownFlag(): Boolean {
		return isStartCountDown
	}

	fun setTextAnimationFlag(isStart: Boolean) {
		isGuideTextAnimationFinished = isStart
	}

	fun getTextAnimationFlag(): Boolean {
		return isGuideTextAnimationFinished
	}

	fun setGuideTextFlag(isStart: Boolean) {
		isGuideTextChanged = isStart
	}

	fun getGuideTextFlag(): Boolean {
		return isGuideTextChanged
	}

	private val _isScanning = MutableLiveData(false)
	val isScanning: LiveData<Boolean> get() = _isScanning
	// 외부(Fragment)에서는 읽기만 가능한 LiveData로 노출
	fun finishSequence() {
		// 백엔드 전송 완료 등의 로직 수행 후
		_isScanning.value = false
	}

	// 상태 초기화가 필요할 때
	fun startSequence() {
		_isScanning.value = true
	}

	// ------------------------------------# 유저 목록 가져오기 #-------------------------------------
	var userCount = 0
	var currentUserItems = listOf<UserItem>()
	private val staticDataLoad = MutableLiveData<Boolean>()

	private val _displayUserItemList = MutableLiveData<List<HistoryMappingItem>>()
	val displayUserItemList: LiveData<List<HistoryMappingItem>> get() = _displayUserItemList

	fun loadUserDataFromDB(context: Context) {
		viewModelScope.launch(Dispatchers.IO) {
			val dao = FaceDatabase.getDatabase(context).faceDao()
			val allData: List<UserItem> = dao.getAllUserItem()
			val getUserCount = dao.getUserCount()
			withContext(Dispatchers.Main) {
				userCount = getUserCount
			}
			currentUserItems = listOf()
			val collator = Collator.getInstance(Locale.KOREAN)
			val sorted = allData.sortedWith(compareByDescending(collator) { it.regDate })
			currentUserItems = sorted

			val mappedList = sorted
				.groupBy { item -> item.regDate.substring(0, 10).replace("-", ".") }
				.flatMap { (date, items) ->
					listOf(HistoryMappingItem.Header(date)) + items.map { HistoryMappingItem.Item(it) }
				}

			// 💡 LiveData로 결과 전달 (postValue 사용)
			_displayUserItemList.postValue(mappedList)
			staticDataLoad.postValue(true)
		}
	}

	val allDataLoaded = MediatorLiveData<Boolean>().apply {
		val update = {
			value = staticDataLoad.value == true
		}
		addSource(staticDataLoad) { update() }
	}

	fun clearLoadDataFlag() {
		staticDataLoad.value = false
	}

	//-------------------------------# info로 들어가는 dataClass 변환 #-------------------------------
	suspend fun convertToFaceResult(context: Context, fDao: FaceDao, serverSn: Int): FaceResult {

		val faceStatics = fDao.getStatic(serverSn)
		val faceInsight = fDao.getInsight(serverSn)

		val mediaUris = mutableListOf<Uri?>()
		val moireUris = mutableListOf<Uri?>()
		val jsonArray = JSONArray()
		val seqs = mutableSetOf<Sequence>()

		faceStatics.filter { it.local_sn != 0 }.forEachIndexed { indexx, faceStatic ->
			// 이미지 URI 변환
			try {
				if (faceStatic.seq in listOf(2, 3)) { // DB에 저장된 seq Int값
					val videoUri = getVideoUriFromFileName(context, faceStatic.media_file_name)
					mediaUris.add(videoUri)
					Log.v("비디오", "$indexx ${faceStatic.media_file_name}")
				} else {
					val imageUri = getImageUriFromFileName(context, faceStatic.media_file_name)
					mediaUris.add(imageUri)
					if (indexx == 0) {
						val baseStr = faceStatic.media_file_name.replaceFirst("-", "-moire-")
						val moireUri = getImageUriFromFileName(context, baseStr)
						moireUris.add(moireUri)
					}
					Log.v("이미지URI", "$indexx ${faceStatic.media_file_name}")
				}
			} catch (e: Exception) {
				mediaUris.add(null)
				Log.e("FaceResultConverter", "이미지 URI 파싱 실패: ${faceStatic.media_file_name}", e)
			}

			// JSON 읽기
			try {
				val jsonObject = FileUtility.readJsonFromFile(faceStatic.json_file_name) ?: JSONObject()
				Log.v("faceResult", "$indexx => ${faceStatic.json_file_name}")
				jsonArray.put(jsonObject)
			} catch (e: Exception) {
				Log.e("FaceResultConverter", "JSON 파일 읽기 실패", e)
				jsonArray.put(JSONObject())
			}

			// 시퀀스 처리
			val targetSequence = Sequence.fromId(faceStatic.seq)
			val seqIndex = targetSequence?.let { Sequence.entries.indexOf(it) } ?: 0
			val ordinal = Sequence.fromIndex(seqIndex)
			ordinal?.let { seqs.add(it) }
		}

		// 예외 방지를 위해 데이터가 비어있지 않은지 체크
		val validData = faceStatics.find { it.media_file_name.isNotBlank() } ?: faceStatics.getOrNull(0) ?: FaceStatic()
		val measureType = faceStatics.getOrNull(0)?.measure_type ?: 0

		val factors = Factor(
			facialAxis = validData.comfort_facial_axis_shape,
			primaryFaceShape = validData.comfort_face_shape,
			secondaryFaceShape = if (validData.comfort_face_shape_sub == FaceShape.DEFAULT) null else validData.comfort_face_shape_sub,
			saggingScale = Pair(validData.comfort_left_sagging_scale, validData.comfort_right_sagging_scale)
		)

		return FaceResult(
			tempServerSn = validData.temp_server_sn,
			userName = validData.name,
			userMobile = validData.mobile,
			mediaUri = mediaUris,
			moireUri = moireUris,
			results = jsonArray,
			depthResult = validData.depth_result,
			seqs = seqs.toSet(),
			factors = factors,
			insight = faceInsight?.insight ?: "",
			regDate = validData.reg_date,
			measureType = measureType,
		)
	}



	val currentResult = MutableLiveData<FaceResult>()
	val currentBowResult = MutableLiveData<FaceResult>()
	var tempServerSnAfterMeasure = 0

	private var alertJob: Job? = null
	private val _currentAlert = MutableStateFlow<AlertItem?>(null)
	val currentAlert: StateFlow<AlertItem?> = _currentAlert.asStateFlow()

	// 앱 실행 후 최초 1회 강조 연출 여부
	var isInitialAlertHighlighted = false
		private set

	fun markAlertHighlighted() {
		isInitialAlertHighlighted = true
	}

	private var alertIndex = 0
	private fun getMemoryUsage(context: Context): Int {
		val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
		val memInfo = ActivityManager.MemoryInfo()
		actManager.getMemoryInfo(memInfo)
		val totalMem = memInfo.totalMem.toDouble()
		val availMem = memInfo.availMem.toDouble()
		val usedMem = totalMem - availMem
		return ((usedMem / totalMem) * 100).toInt()
	}

	fun startAlertRolling(context: Context) {
		alertJob?.cancel()
		alertJob = viewModelScope.launch {
			while (isActive) {
				val memUsage = getMemoryUsage(context)
				val memGuide = if (memUsage >= 88) "RAM 사용량이 ${memUsage}%로 높습니다. 백그라운드 앱을 정리해 주세요."
				else "현재 RAM ${memUsage}%로 쾌적합니다. 측정을 시작해 보세요."
				val deviceConnect = if (isDepthConnected.value) "3D 뎁스 카메라 정상 연결 (측정 준비 완료)"
				else "연결된 3D 카메라 장치가 없습니다."
				val measureCountDesc = if (dailyMeasureCount == 0) "오늘 검사 기록이 없습니다. 하단에서 모드를 선택해 주세요."
				else "오늘 총 ${dailyMeasureCount}건의 분석이 완료되었습니다."

				val alertList = listOf(
					AlertItem("시스템 메모리", memGuide),
					AlertItem("장비 상태", deviceConnect),
					AlertItem("AI 가이드", guideComments.random()),
					AlertItem("오늘의 통계", measureCountDesc)
				)

				_currentAlert.value = alertList[alertIndex]
				alertIndex = (alertIndex + 1) % alertList.size

				// 최초 강조 중일 때는 텍스트를 읽을 수 있게 첫 주기만 살짝 길게(4.5초) 유지
				val delayTime = if (!isInitialAlertHighlighted) 4500L else 3500L
				delay(delayTime)
			}
		}
	}

	val isAlertShow = MutableLiveData(false)
	private val _isDepthConnected = MutableStateFlow(false)
	val isDepthConnected: StateFlow<Boolean> = _isDepthConnected.asStateFlow()

	fun updateUsbDeviceList(deviceList: HashMap<String, UsbDevice>?) {
		// 장치명 또는 제품명 검사
		val isConnected = deviceList?.values?.any { device ->
			device.deviceName.contains("Orbbec Gemini 336") ||
				device.productName?.contains("Orbbec Gemini 336") == true
		} ?: false

		_isDepthConnected.value = isConnected
	}
	var dailyMeasureCount = 0

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

}