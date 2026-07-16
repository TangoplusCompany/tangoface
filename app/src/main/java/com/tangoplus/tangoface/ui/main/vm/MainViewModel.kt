package com.tangoplus.tangoface.ui.main.vm

import android.content.Context
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
import com.tangoplus.tangoface.ui.main.vo.UserItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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

		// 1. 함수 내부에서 DB 데이터 직접 조회
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

		// BOW 처리 (기존 작성중이시던 로직 유지 가능)
		val bowSns = if (measureType == 1) {
			fDao.getBowSns(faceStatics.getOrNull(0)?.uuid ?: "")
		} else { listOf() }

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
			bowSns = bowSns
		)
	}

	// 비교일 경우
	private var _comparisonState = MutableLiveData<Boolean>()
	val comparisonState: LiveData<Boolean> = _comparisonState

	fun setComparisonState(isComparison: Boolean) {
		_comparisonState.value = isComparison
	}

	fun getComparisonState(): Boolean {
		return _comparisonState.value ?: false
	}

	var comparisonDoubleItem: Pair<FaceResult, FaceResult>? =
		null // null 일경우 1개 선택 한 상황 not null일 경우 비교상황

	private val _tempComparisonItems = MutableLiveData<MutableList<UserItem>>(mutableListOf())
	val tempComparisonItems: LiveData<MutableList<UserItem>> = _tempComparisonItems
	fun addItem(item: UserItem) {
		val currentList = _tempComparisonItems.value ?: mutableListOf()
		currentList.add(item)
		_tempComparisonItems.value = currentList.toMutableList()
	}

	fun removeItem(item: UserItem) {
		val currentList = _tempComparisonItems.value ?: mutableListOf()
		currentList.remove(item)
		_tempComparisonItems.value = currentList.toMutableList()
	}

	fun clearItems() {
		val currentList = _tempComparisonItems.value ?: mutableListOf()
		currentList.clear()
		_tempComparisonItems.value = currentList.toMutableList()
	}

	val currentResult = MutableLiveData<FaceResult>()

	var tempServerSnAfterMeasure = 0

	// TODO 여기서 DP에서 기록 가져오기 (data class 하나 필요할 듯 )
	var dateDisplayList = listOf<DateDisplay>()



}