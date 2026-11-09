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

		val sortedStatics = faceStatics
			.filter { it.local_sn != 0 }
			.sortedBy { fs ->
				val idx = Sequence.fromId(fs.seq)?.let { Sequence.entries.indexOf(it) } ?: -1
				if (idx == -1) Int.MAX_VALUE else idx
			}
		faceStatics.forEach {
			Log.v("정렬확인", "dbSeq=${it.seq}, fromId=${Sequence.fromId(it.seq)}, file=${it.json_file_name}")
		}
		sortedStatics.forEachIndexed { indexx, faceStatic ->
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

	private val _currentAlert = MutableStateFlow<AlertItem?>(null)
	val currentAlert: StateFlow<AlertItem?> = _currentAlert.asStateFlow()

	// 앱 실행 후 최초 1회 강조 연출 여부
	var isInitialAlertHighlighted = false
		private set
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

	// 1. 헤더용: RAM 사용량
	private val _ramUsage = MutableStateFlow(0)
	val ramUsage: StateFlow<Int> = _ramUsage.asStateFlow()

	// 2. 헤더용: USB 뎁스 카메라 연결 상태
	private val _isDepthConnected = MutableStateFlow(false)
	val isDepthConnected: StateFlow<Boolean> = _isDepthConnected.asStateFlow()

	// 3. 중앙 상단용: 도움말 가이드 코멘트 롤링
	private val _currentGuide = MutableStateFlow(guideComments[0])
	val currentGuide: StateFlow<String> = _currentGuide.asStateFlow()

	private var guideJob: Job? = null
	private var ramJob: Job? = null
	private var guideIndex = 0

	val isAlertShow = MutableLiveData(false)
	var dailyMeasureCount = 0

	// RAM 사용량 계산
	private fun getMemoryUsage(context: Context): Int {
		val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
		val memInfo = ActivityManager.MemoryInfo()
		actManager.getMemoryInfo(memInfo)
		val totalMem = memInfo.totalMem.toDouble()
		val availMem = memInfo.availMem.toDouble()
		return (((totalMem - availMem) / totalMem) * 100).toInt()
	}

	// RAM 5초 주기 모니터링
	fun startRamMonitoring(context: Context) {
		ramJob?.cancel()
		ramJob = viewModelScope.launch {
			while (isActive) {
				_ramUsage.value = getMemoryUsage(context)
				delay(5000L)
			}
		}
	}

	// 가이드 코멘트 4.5초 주기 순환 롤링
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

	// USB 장치 목록 갱신
	fun updateUsbDeviceList(deviceList: HashMap<String, UsbDevice>?) {
		val isConnected = deviceList?.values?.any { device ->
			device.deviceName.contains("Orbbec Gemini 336") ||
				device.productName?.contains("Orbbec Gemini 336") == true
		} ?: false

		_isDepthConnected.value = isConnected
	}
}