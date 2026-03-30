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
import com.tangoplus.facebeautyexpert.data.db.FaceStatic
import com.tangoplus.facebeautyexpert.domain.util.FileUtility
import com.tangoplus.facebeautyexpert.domain.vision.face.FaceLandmarkerHelper
import com.tangoplus.facebeautyexpert.domain.vo.FaceResult
import com.tangoplus.facebeautyexpert.domain.vo.Sequence
import com.tangoplus.facebeautyexpert.ui.info.vo.DateDisplay
import com.tangoplus.facebeautyexpert.ui.main.vo.MeasureGridItem
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
	var currentUserItems = listOf<UserItem>()
	private val staticDataLoad = MutableLiveData<Boolean>()
	fun loadUserDataFromDB(context: Context) {
		viewModelScope.launch(Dispatchers.IO) {
			val dao = FaceDatabase.Companion.getDatabase(context).faceDao()
			val allData: List<UserItem> = dao.getFaceDisplayList().map { it.toUserItem() }
			val guestCount = dao.getGuestCount()
			withContext(Dispatchers.Main) {
				currentGuestCount = guestCount
			}
			currentUserItems = listOf()
			val collator = Collator.getInstance(Locale.KOREAN)
			val sorted = allData.sortedWith(compareBy(collator) { it.userName })
			currentUserItems = sorted
			staticDataLoad.postValue(true)
		}
	}

	// ------------------------------------# 게스트 목록 가져오기 #------------------------------------
	val currentGuestItems = mutableListOf<MeasureGridItem>()
	fun loadGuestDataFromDB(context: Context) {
		viewModelScope.launch(Dispatchers.IO) {
			val dao = FaceDatabase.Companion.getDatabase(context).faceDao()
			val allData = dao.getGuestData()

			currentGuestItems.clear()
			currentGuestItems.addAll(allData)
			currentGuestItems.sortByDescending { it.regDate }
			Log.v("allGuestItems", "$allData")
			staticDataLoad.postValue(true)
			// 게스트는 그냥 자동 true
			favoriteDataLoad.postValue(true)
		}
	}

	private var currentGuestCount = 0
	fun getGuestCount(): Int = currentGuestCount

	// -----------------------------------# 즐겨찾기 목록 가져오기 #-----------------------------------
	private val _currentFaceFavorites = MutableLiveData<List<MeasureGridItem>>(emptyList())
	val currentFaceFavorites: LiveData<List<MeasureGridItem>> = _currentFaceFavorites
	private val favoriteDataLoad = MutableLiveData<Boolean>()
	fun loadFavoriteDataFromDB(context: Context) {
		viewModelScope.launch(Dispatchers.IO) {
			val dao = FaceDatabase.Companion.getDatabase(context).faceDao()
			val allData = dao.getAllFavorite()
				.sortedByDescending { it.regDate }
			_currentFaceFavorites.postValue(allData)

			val snsData = dao.getAllFavoriteSn()
			withContext(Dispatchers.Main) {
				_currentFaceFavoriteSns.value = snsData
			}
			favoriteDataLoad.postValue(true)
		}
	}

	// 즐겨찾기 sn만 담기
	private var _currentFaceFavoriteSns = MutableLiveData<List<Int>>(emptyList())
	val currentFaceFavoriteSns: LiveData<List<Int>> = _currentFaceFavoriteSns

	// 사용자 데이터 + 즐겨찾기 데이터 전부 가져오기
	val allDataLoaded = MediatorLiveData<Boolean>().apply {
		val update = {
			value = staticDataLoad.value == true &&
				favoriteDataLoad.value == true
		}
		addSource(staticDataLoad) { update() }
		addSource(favoriteDataLoad) { update() }
	}

	fun clearLoadDataFlag() {
		staticDataLoad.value = false
		favoriteDataLoad.value = false
	}

	// 즐겨찾기 토글 보존 true -> 게스트, false -> 유저
	private var _toggleState = MutableLiveData<Boolean>(false)
	val toggleState: LiveData<Boolean> = _toggleState
	fun getToggleState(): Boolean {
		return _toggleState.value ?: false
	}

	fun setToggleState(newData: Boolean) {
		_toggleState.value = newData
	}

	//-------------------------------# info로 들어가는 dataClass 변환 #-------------------------------
	fun convertToFaceResult(context: Context, faceStatics: List<FaceStatic>): FaceResult {
		val mediaUri = mutableListOf<Uri?>()
		val jsonArray = JSONArray()
		val seqs = mutableSetOf<Sequence>()
		faceStatics.filter { it.local_sn != 0 }.forEachIndexed { indexx, faceStatic ->
			// 1. mediaFileUri를 Uri로 변환해서 추가
			Log.v("result확인", faceStatic.media_file_name)
			try {

				if (faceStatic.seq in listOf(3, 4)) {
					val videoUri = FileUtility.getVideoUriFromFileName(context, faceStatic.media_file_name)
					mediaUri.add(videoUri)
					Log.v("비디오", "$indexx ${faceStatic.media_file_name}")
				} else {
					val imageUri = FileUtility.getImageUriFromFileName(context, faceStatic.media_file_name)
					mediaUri.add(imageUri)
					Log.v("이미지URI", "$indexx ${faceStatic.media_file_name}")
				}
			} catch (e: Exception) {
				mediaUri.add(null)
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
			val seqIndex = faceStatic.seq
			val ordinal = Sequence.Companion.fromIndex(seqIndex)
			ordinal?.let { seqs.add(it) }
		}
		val validData = faceStatics.find { it.media_file_name.isNotBlank() } ?: faceStatics[0]
		return FaceResult(
			tempServerSn = validData.temp_server_sn,
			userName = validData.name,
			userMobile = validData.mobile,
			mediaUri = mediaUri,
			results = jsonArray,
			depthResult = validData.depth_result,
			seqs = seqs.toSet(),
			regDate = validData.reg_date
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

	private val _tempComparisonItems = MutableLiveData<MutableList<MeasureGridItem>>(mutableListOf())
	val tempComparisonItems: LiveData<MutableList<MeasureGridItem>> = _tempComparisonItems
	fun addItem(item: MeasureGridItem) {
		val currentList = _tempComparisonItems.value ?: mutableListOf()
		currentList.add(item)
		_tempComparisonItems.value = currentList.toMutableList()
	}

	fun removeItem(item: MeasureGridItem) {
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

	var isMeasureFinish = false
	var tempServerSnAfterMeasure = 0

	// TODO 여기서 DP에서 기록 가져오기 (data class 하나 필요할 듯 )
	var dateDisplayList = listOf<DateDisplay>()
	val leftDate = MutableLiveData<DateDisplay>()
	val rightDate = MutableLiveData<DateDisplay>()
	fun createDateDisplayListByUser(faceResult: UserItem): List<DateDisplay> {
		return faceResult.regDate?.map {
			DateDisplay(
				fullDateTime = it,
				displayDate = it.substring(0, 16)
			)
		} ?: listOf()
	}

	fun createDateDisplayListByGuest(mgis: List<MeasureGridItem>): List<DateDisplay> {
		return mgis.map {
			DateDisplay(
				fullDateTime = it.regDate,
				displayDate = it.regDate.substring(0, 16)
			)
		}
	}

	fun getFilteredDates(excludeDates: List<DateDisplay>): List<DateDisplay> {
		return dateDisplayList.filter { date ->
			excludeDates.none { it.fullDateTime == date.fullDateTime }
		}
	}


}