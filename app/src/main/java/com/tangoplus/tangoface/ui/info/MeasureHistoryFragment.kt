package com.tangoplus.tangoface.ui.info

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.replace
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
import com.tangoplus.tangoface.ui.listener.OnUserClickListener
import com.tangoplus.tangoface.ui.main.MainActivity
import com.tangoplus.tangoface.ui.main.vm.MainViewModel
import com.tangoplus.tangoface.ui.main.vo.UserItem
import com.tangoplus.tangoface.ui.measure.MeasureImageFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.getValue

class MeasureHistoryFragment : Fragment(), OnUserClickListener {
	private lateinit var bd: FragmentMeasureHistoryBinding
	private val mvm: MainViewModel by activityViewModels()
	private lateinit var fDao: FaceDao
	private val ivm: InformationViewModel by activityViewModels()
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
				setAdapter(mvm.currentUserItems)
			}
		}

		lifecycleScope.launch(Dispatchers.Main) {
			// 초기 셋업
			val fd = FaceDatabase.getDatabase(requireContext())
			fDao = fd.faceDao()
			initUserUI()
			setAdapter(mvm.currentUserItems)

			bd.btnMHImage.setOnSingleClickListener {
				(requireActivity() as MainActivity).navigateToSubFragment(MeasureImageFragment())
			}
		}
		setSearchText()
	}

	private fun initUserUI() {
		mvm.loadUserDataFromDB(requireContext())
	}

	private fun setAdapter(userItems: List<UserItem>) {
		val currentSpanCount = if (MyApplication.isTablet()) 2 else 1
		val lm = GridLayoutManager(requireContext(), currentSpanCount) // LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
		val userAdapter = UserRVAdapter(requireContext(), mvm)
		userAdapter.onUserClickListener = this@MeasureHistoryFragment
		bd.rvMH.apply {
			layoutManager = lm
			adapter = userAdapter
		}

		bd.llMHEmpty.visibility = if (mvm.currentUserItems.isEmpty()) View.VISIBLE else View.GONE
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

	private fun setSearchText() {
		bd.etMHSearch.addTextChangedListener(object: TextWatcher{
			override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
			override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
			override fun afterTextChanged(p0: Editable?) {
				val afterText = p0.toString()
				if (afterText.isNotEmpty()) {
					val filteredUserItems = mvm.currentUserItems.filter { it.name?.contains(afterText) == true || it.mobile?.contains(afterText) == true }
					setAdapter(filteredUserItems)
				} else {
					setAdapter(mvm.currentUserItems)
				}
			}
		})
	}
}