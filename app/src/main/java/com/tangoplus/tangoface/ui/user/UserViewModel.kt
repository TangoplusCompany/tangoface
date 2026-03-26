package com.tangoplus.tangoface.ui.user

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.data.db.FaceFavorite
import com.tangoplus.tangoface.data.db.Memo
import com.tangoplus.tangoface.ui.main.vo.MeasureGridItem
import com.tangoplus.tangoface.ui.main.vo.UserItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class UserViewModel : ViewModel() {


	private var _selectedUserItem = MutableLiveData<UserItem>()
	val selectedUserItem: LiveData<UserItem> = _selectedUserItem

	fun setUserItem(fd: UserItem) {
		_selectedUserItem.value = fd
	}

	fun getUserItem(): UserItem {
		return _selectedUserItem.value ?: UserItem(userSn = -1, tempServerSn = listOf())
	}

	// user1명의 목록을 가져오는 함수
	private var _currentGridItems = MutableLiveData<List<MeasureGridItem>>()
	val currentGridItems: LiveData<List<MeasureGridItem>> = _currentGridItems
	val measureGridItemLoad = MutableLiveData<Boolean>()
	fun loadMeasureGridItemFromDB(context: Context) {
		viewModelScope.launch(Dispatchers.IO) {
			val dao = FaceDatabase.getDatabase(context).faceDao()

			val allData = dao.getMeasureDataFrom1User(getUserItem().userSn)
			_currentGridItems.postValue(allData.sortedByDescending { it.regDate })


			val memo = dao.getMemo(getUserItem().userSn)
			withContext(Dispatchers.Main) {
				setMemo(memo)
			}
			measureGridItemLoad.postValue(true)
		}
	}


	private var _tempMeasureItem = MutableLiveData<MeasureGridItem>()
	val tempMeasureItem: LiveData<MeasureGridItem> = _tempMeasureItem
	fun getMeasureItem(): MeasureGridItem {
		return _tempMeasureItem.value ?: MeasureGridItem(tempServerSn = -1)
	}

	fun setMeasureItem(newItem: MeasureGridItem) {
		_tempMeasureItem.value = newItem
	}

	private var _tempTitle = MutableLiveData<String>()
	fun getTempTitle(): String = _tempTitle.value ?: ""
	fun setTempTitle(newTitle: String) {
		_tempTitle.value = newTitle
	}

	var textInputCase = -1

	// 즐겨찾기 추가
	fun insertFavorite(context: Context, callback: () -> Unit) {
		val measureItem = getMeasureItem()
		val userItem = getUserItem()
		val favorite = FaceFavorite(
			temp_server_sn = measureItem.tempServerSn,
			user_sn = userItem.userSn,
			title = getTempTitle(),
			reg_date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
		)
		viewModelScope.launch(Dispatchers.IO) {
			val dao = FaceDatabase.getDatabase(context).faceDao()

			val currentCount = dao.getAllFavoriteSn().size
			if (currentCount < 20) {
				val insertDuration = dao.insertFavorite(favorite)
				Log.v("insert즐겨찾기", "insert시간: Long $insertDuration, $favorite")
			} else {
				Toast.makeText(context, "즐겨찾기는 최대 20개까지만 저장 가능합니다", Toast.LENGTH_LONG).show()
			}


			withContext(Dispatchers.Main) {
				// 초기화
				setTempTitle("")
				setMeasureItem(MeasureGridItem(tempServerSn = -1))
				callback()
			}
		}
	}

	fun deleteFavorite(context: Context) {
		val measureItem = getMeasureItem()

		viewModelScope.launch(Dispatchers.IO) {
			val dao = FaceDatabase.getDatabase(context).faceDao()
			Log.v("즐겨찾기삭제", "${measureItem.tempServerSn} -> 즐겨찾기 삭제되는  sn")
			dao.deleteFavoriteByServerSn(measureItem.tempServerSn)
			withContext(Dispatchers.Main) {
				// 초기화
				setTempTitle("")
				setMeasureItem(MeasureGridItem(tempServerSn = -1))
			}
		}
	}


	// 메모 만들기
	private var _currentMemo = MutableLiveData<MeasureGridItem>()
	val currentMemo: LiveData<MeasureGridItem> = _currentMemo

	fun setMemo(newMemo: MeasureGridItem?) {
		_currentMemo.value = newMemo ?: MeasureGridItem(tempServerSn = -1)
	}

	fun getMemo(): MeasureGridItem {
		return _currentMemo.value ?: MeasureGridItem(tempServerSn = -1)
	}

	private var _isShowMemo = MutableLiveData<Boolean>()
	val isShowMemo: LiveData<Boolean> = _isShowMemo

	fun setMemoVisibility(newMemo: Boolean) {
		_isShowMemo.value = newMemo
	}

	fun getMemoVisibility(): Boolean {
		return _isShowMemo.value ?: false
	}

	fun insertOrUpdateMemo(context: Context) {
		val userItem = getUserItem()
		val favorite = Memo(
			user_sn = userItem.userSn,
			memo = getTempTitle(),
			reg_date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
		)
		viewModelScope.launch(Dispatchers.IO) {
			val dao = FaceDatabase.getDatabase(context).faceDao()
			dao.insertOrUpdateMemo(favorite)

			withContext(Dispatchers.Main) {
//                setTempTitle("")
				val gridItem = MeasureGridItem(
					tempServerSn = getMeasureItem().tempServerSn,
					userTitle = favorite.memo,
					regDate = favorite.reg_date
				)
				setMemo(gridItem)
			}
		}
	}
}