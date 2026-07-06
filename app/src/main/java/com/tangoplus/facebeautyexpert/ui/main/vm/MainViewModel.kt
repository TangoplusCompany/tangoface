package com.tangoplus.facebeautyexpert.ui.main.vm

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangoplus.facebeautyexpert.data.db.FaceDatabase
import com.tangoplus.facebeautyexpert.data.db.FaceInsight
import com.tangoplus.facebeautyexpert.data.db.FaceShape
import com.tangoplus.facebeautyexpert.data.db.FaceStatic
import com.tangoplus.facebeautyexpert.domain.util.FileUtility
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkerHelper
import com.tangoplus.facebeautyexpert.domain.vo.FaceResult
import com.tangoplus.facebeautyexpert.domain.vo.Sequence
import com.tangoplus.facebeautyexpert.ui.info.vo.DateDisplay
import com.tangoplus.facebeautyexpert.ui.info.vo.Factor
import com.tangoplus.facebeautyexpert.ui.main.vo.UserItem
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

	private var isSeqFinished = false

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

	fun setSeqFinishedFlag(isStart: Boolean) {
		isSeqFinished = isStart
	}

	fun getSeqFinishedFlag(): Boolean {
		return isSeqFinished
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
	fun convertToFaceResult(context: Context, faceStatics: List<FaceStatic>, faceInsight: FaceInsight?): FaceResult {

		val mediaUris = mutableListOf<Uri?>()
		val moireUris = mutableListOf<Uri?>()
		val jsonArray = JSONArray()
		val seqs = mutableSetOf<Sequence>()
		faceStatics.filter { it.local_sn != 0 }.forEachIndexed { indexx, faceStatic ->
			// 1. mediaFileUri를 Uri로 변환해서 추가
			Log.v("result확인", "0: ${faceStatic.media_file_name}")
			try {

				val imageUri = FileUtility.getImageUriFromFileName(context, faceStatic.media_file_name)
				val baseStr = faceStatic.media_file_name.replaceFirst("-", "-moire-")
				val moireUri = FileUtility.getImageUriFromFileName(context, baseStr)
				mediaUris.add(imageUri)
				moireUris.add(moireUri)

				Log.v("이미지URI", "1: $indexx ${faceStatic.media_file_name}")
			} catch (e: Exception) {
				mediaUris.add(null)
				Log.e("FaceResultConverter", "이미지 URI 파싱 실패: ${faceStatic.media_file_name}", e)
			}

			// 2. jsonFileUri에서 JSON 파일 읽어서 JSONObject로 변환
			try {
				val jsonObject = FileUtility.readJsonFromFile(faceStatic.json_file_name) ?: JSONObject()
				Log.v("jsonObject있나요?", "$jsonObject")
				jsonArray.put(jsonObject)
			} catch (e: Exception) {
				Log.e("FaceResultConverter", "JSON 파일 읽기 실패", e)
				jsonArray.put(JSONObject())
			}

			// ---------# seqs #---------

			val targetSequence = Sequence.fromId(faceStatic.seq)
			// 2. 현재 활성화된 리스트(entries) 안에서 그 객체의 인덱스(1)를 구함
			val seqIndex = targetSequence?.let { Sequence.entries.indexOf(it) } ?: 0

			val ordinal = Sequence.Companion.fromIndex(seqIndex)
			ordinal?.let { seqs.add(it) }
			Log.v("시퀀스", "$seqIndex, ordinal: ${ordinal}")
		}
		val validData = faceStatics.find { it.media_file_name.isNotBlank() } ?: faceStatics[0]
		val factors = Factor(
			facialAxis = validData.comfort_facial_axis_shape,
			primaryFaceShape = validData.comfort_face_shape,
			secondaryFaceShape = if (validData.comfort_face_shape_sub == FaceShape.DEFAULT) null else validData.comfort_face_shape_sub,
			saggingScale = Pair(validData.comfort_left_sagging_scale, validData.comfort_right_sagging_scale)
		)
		Log.v("목폄확인", "$seqs")
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