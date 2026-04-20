package com.tangoplus.facebeautyexpert.ui.info

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.data.db.FaceDao
import com.tangoplus.facebeautyexpert.data.db.FaceDatabase
import com.tangoplus.facebeautyexpert.databinding.FragmentMeasureHistoryBinding
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.ui.LoadingDialogFragment
import com.tangoplus.facebeautyexpert.ui.adapter.UserRVAdapter
import com.tangoplus.facebeautyexpert.ui.listener.OnUserClickListener
import com.tangoplus.facebeautyexpert.ui.main.MainActivity
import com.tangoplus.facebeautyexpert.ui.main.vm.MainViewModel
import com.tangoplus.facebeautyexpert.ui.measure.MeasureImageFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.getValue

class MeasureHistoryFragment : Fragment(), OnUserClickListener {
	private lateinit var bd: FragmentMeasureHistoryBinding
	private val mvm: MainViewModel by activityViewModels()
	private lateinit var fDao: FaceDao
	private lateinit var loadingDialog: LoadingDialogFragment

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
				setAdapter()
			}
		}

		lifecycleScope.launch(Dispatchers.Main) {
			// 초기 셋업
			val fd = FaceDatabase.getDatabase(requireContext())
			fDao = fd.faceDao()
			initUserUI()
			setAdapter()
			loadingDialog = LoadingDialogFragment()

			bd.btnMHImage.setOnSingleClickListener {
				(requireActivity() as MainActivity).navigateToSubFragment(MeasureImageFragment())
			}
		}
	}

	private fun initUserUI() {
		mvm.loadUserDataFromDB(requireContext())
	}

	private fun setAdapter() {
		val lm = GridLayoutManager(requireContext(), 2)
		val userAdapter = UserRVAdapter(requireContext(), mvm.currentUserItems)
		userAdapter.onUserClickListener = this@MeasureHistoryFragment
		bd.rvMH.apply {
			layoutManager = lm
			adapter = userAdapter
		}

		bd.llMHEmpty.visibility = if (mvm.currentUserItems.isEmpty()) View.VISIBLE else View.GONE
	}

	override fun onUserClick(tempServerSn: Int) {
		if (!loadingDialog.isAdded && !loadingDialog.isVisible) {
			loadingDialog.show(requireActivity().supportFragmentManager, "loading_tag")
		}

		lifecycleScope.launch(Dispatchers.IO) {
			val statics = fDao.getStatic(tempServerSn)
			val insight = fDao.getInsight(tempServerSn)
			val result = mvm.convertToFaceResult(requireContext(), statics, insight)
//        scrollToView(bd.ibtnMBack, bd.nsvM)
			withContext(Dispatchers.Main) {
				mvm.currentResult.value = result
				if (loadingDialog.isAdded) {
					loadingDialog.dismiss()
				}
				activity?.let { fa -> fa.supportFragmentManager.beginTransaction().apply {
					replace(R.id.flMain, InformationFragment())
					addToBackStack("information")
					commit()
				} }
			}
		}
	}


}