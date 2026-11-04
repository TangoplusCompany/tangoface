package com.tangoplus.tangoface.ui.info

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.data.db.FaceDao
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.databinding.FragmentMeasureHistoryBinding
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.orbbec.application.MyApplication
import com.tangoplus.tangoface.ui.adapter.UserRVAdapter
import com.tangoplus.tangoface.ui.info.vm.InformationViewModel
import com.tangoplus.tangoface.ui.info.vo.HistoryMappingItem
import com.tangoplus.tangoface.ui.listener.OnUserClickListener
import com.tangoplus.tangoface.ui.main.MainActivity
import com.tangoplus.tangoface.ui.main.vm.MainViewModel
import com.tangoplus.tangoface.ui.main.vo.UserItem
import com.tangoplus.tangoface.ui.measure.MImageFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.getValue

class MeasureHistoryFragment : Fragment(), OnUserClickListener {
	private lateinit var bd: FragmentMeasureHistoryBinding
	private val mvm: MainViewModel by activityViewModels()
	private lateinit var fDao: FaceDao
	private val ivm: InformationViewModel by activityViewModels()
	private lateinit var userAdapter: UserRVAdapter

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentMeasureHistoryBinding.inflate(inflater)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		mvm.allDataLoaded.observe(viewLifecycleOwner) { isLoaded ->
			if (isLoaded == true) {
				mvm.clearLoadDataFlag()
				initAdapter()
			}
		}

		lifecycleScope.launch(Dispatchers.Main) {
			// 초기 셋업
			val fd = FaceDatabase.getDatabase(requireContext())
			fDao = fd.faceDao()
			initUserUI()
			initAdapter()

			bd.btnMHImage.setOnSingleClickListener {
				(requireActivity() as MainActivity).navigateToSubFragment(MImageFragment())
			}
		}
		setSearchText()
	}

	private fun initUserUI() {
		mvm.loadUserDataFromDB(requireContext())
	}


	override fun onUserClick(tempServerSn: Int, isBow: Boolean) {

		lifecycleScope.launch(Dispatchers.IO) {

			if (isBow) {
				val result = mvm.convertToFaceResult(requireContext(), fDao, tempServerSn)
				withContext(Dispatchers.Main) {
					mvm.currentBowResult.value = result
					val dialog = IBowDialogFragment()
					dialog.show(requireActivity().supportFragmentManager, "")
				}
			} else {
				val result = mvm.convertToFaceResult(requireContext(), fDao, tempServerSn)

				val keys = listOf(
					"comfort_alar_horizontal_angle",
					"neck_extention_ear_horizontal_angle",
					"occlusal_eye_horizontal_angle",
					"jaw_left_tilt_left_mandibular_distance",
					"jaw_right_tilt_left_mandibular_distance",
					"jaw_opening_lips_distance"
				)

				val arr = result.results
				for (i in 0 until arr.length()) {
					val d = arr.getJSONObject(i).optJSONObject("data") ?: arr.getJSONObject(i)
					val nonZero = keys.filter { d.optDouble(it, 0.0) != 0.0 }
					Log.v("값확인", "i=$i, seq=${result.seqs.elementAtOrNull(i)}, 값 있는 키=$nonZero")
				}

				withContext(Dispatchers.Main) {
					mvm.currentResult.value = result
					activity?.let { fa -> fa.supportFragmentManager.beginTransaction().apply {
						replace(R.id.flMain, InfoContainerFragment.newInstance(result.measureType))
						addToBackStack("information")
						commit()
					} }
				}
			}
		}
	}

	private fun initAdapter() {
		val currentSpanCount = if (MyApplication.isTablet()) 2 else 1
		userAdapter = UserRVAdapter(requireContext(), mvm).apply {
			onUserClickListener = this@MeasureHistoryFragment
		}
		bd.llMHEmpty.visibility = if (mvm.currentUserItems.size > 0 ) View.GONE else View.VISIBLE
		val lm = GridLayoutManager(requireContext(), currentSpanCount).apply {
			spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
				override fun getSpanSize(p0: Int): Int {
					return when (userAdapter.getItemViewType(p0)) {
						UserRVAdapter.TYPE_HEADER -> currentSpanCount
						UserRVAdapter.TYPE_ITEM -> 1
						else -> 1
					}
				}
			}
		}

		bd.rvMH.apply {
			layoutManager = lm
			adapter = userAdapter
		}
	}

	// 2. 데이터 세팅 및 업데이트
	private fun updateAdapter(userItems: List<UserItem>) {
		// UserItem -> HistoryMappingItem (날짜별 그룹화) 변환
		val mappingList = userItems.toHistoryMapping()

		userAdapter.submitHistoryList(mappingList)
		bd.llMHEmpty.visibility = if (userItems.isEmpty()) View.VISIBLE else View.GONE
	}

	// 3. 검색 리스너
	private fun setSearchText() {
		bd.etMHSearch.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
			override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
			override fun afterTextChanged(p0: Editable?) {
				val afterText = p0.toString().trim()
				val filteredList = if (afterText.isNotEmpty()) {
					mvm.currentUserItems.filter {
						it.name?.contains(afterText, ignoreCase = true) == true ||
							it.mobile?.contains(afterText) == true
					}
				} else {
					mvm.currentUserItems
				}

				updateAdapter(filteredList)
			}
		})
	}

	// 💡 확장 함수: UserItem 리스트 -> HistoryMappingItem 리스트 변환
	private fun List<UserItem>.toHistoryMapping(): List<HistoryMappingItem> {
		return this.groupBy { item -> item.regDate.substring(0, 10).replace("-", ".") }
			.flatMap { (date, items) ->
				listOf(HistoryMappingItem.Header(date)) + items.map { HistoryMappingItem.Item(it) }
			}
	}
}