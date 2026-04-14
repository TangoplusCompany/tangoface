package com.tangoplus.facebeautyexpert.ui.info

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.tangoplus.facebeautyexpert.ui.statistics.StatisticsFragment
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
				mvm.isMeasureFinish = activity?.intent?.getBooleanExtra("isMeasureFinish", false) ?: false
				mvm.tempServerSnAfterMeasure = activity?.intent?.getIntExtra("serverSn", -1) ?: -1

				// 측정이 완료되고 바로 결과를 볼 때
				if (mvm.isMeasureFinish && mvm.tempServerSnAfterMeasure != -1) {
					lifecycleScope.launch(Dispatchers.IO) {
						val statics = fDao.getStatic(mvm.tempServerSnAfterMeasure)

						withContext(Dispatchers.Main) {
							mvm.currentResult.value = mvm.convertToFaceResult(requireContext(), statics)
							requireActivity().supportFragmentManager.beginTransaction().apply {
								replace(R.id.flMain, InformationFragment())
								commit()
							}
							mvm.isMeasureFinish = false
							mvm.tempServerSnAfterMeasure = -1
							activity?.intent?.removeExtra("isMeasureFinish")
							activity?.intent?.removeExtra("serverSn")
						}
					}
				}
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
			loadingDialog.show(requireActivity().supportFragmentManager, "LoadingDialogFragment")
		}

		lifecycleScope.launch(Dispatchers.IO) {
			val statics = fDao.getStatic(tempServerSn)
			val result = mvm.convertToFaceResult(requireContext(), statics)
//        scrollToView(bd.ibtnMBack, bd.nsvM)
			withContext(Dispatchers.Main) {
				mvm.currentResult.value = result
				if (loadingDialog.isAdded) {
					loadingDialog.dismiss()
				}
				activity?.let { fa -> fa.supportFragmentManager.beginTransaction().apply {
					replace(R.id.flMain, InformationFragment())
					commit()
				} }
			}
		}
	}


}