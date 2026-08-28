package com.tangoplus.tangoface.ui.info

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.data.db.FaceDao
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.databinding.FragmentIBowSelectDialogBinding
import com.tangoplus.tangoface.ui.adapter.UserRVAdapter
import com.tangoplus.tangoface.ui.info.vm.InformationViewModel
import com.tangoplus.tangoface.ui.listener.OnBowItemClickListener
import com.tangoplus.tangoface.ui.main.vm.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.graphics.drawable.toDrawable

class IBowSelectDialogFragment : DialogFragment(), OnBowItemClickListener {
	private lateinit var bd : FragmentIBowSelectDialogBinding
	private lateinit var fDao: FaceDao
	private val ivm : InformationViewModel by activityViewModels ()
	private val mvm : MainViewModel by activityViewModels ()
	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentIBowSelectDialogBinding.inflate(inflater)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val db = FaceDatabase.getDatabase(requireContext())
		fDao = db.faceDao()
		lifecycleScope.launch {
			val isGuest = mvm.currentResult.value?.userName == "GUEST"
			val result = ivm.getBowResults(fDao, if (isGuest) null else mvm.currentResult.value?.tempServerSn)
			Log.v("bow결과", "$isGuest = $result")
			ivm.setCurrentBowResults(result)

			setAdapter()
		}
	}

	private fun setAdapter() {
		val vLayoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
		val uAdapter = UserRVAdapter(requireContext(), ivm = ivm, fType = 1)
		uAdapter.onBowItemClickListener = this@IBowSelectDialogFragment
		bd.rvIBS.apply {
			layoutManager = vLayoutManager
			adapter = uAdapter
		}
	}

	override fun onBowItemClick(tempServerSn: Int) {
		lifecycleScope.launch(Dispatchers.IO) {

			val result = mvm.convertToFaceResult(requireContext(), fDao, tempServerSn)
			withContext(Dispatchers.Main) {
				mvm.currentBowResult.value = result
				val dialog = IBowDialogFragment()
				dialog.show(requireActivity().supportFragmentManager, "")
				dismiss()
			}
		}
	}

	override fun onStart() {
		super.onStart()

		dialog?.window?.let { window ->
//			window.setBackgroundDrawable(Color.parseColor("#80000000").toDrawable())
			// 2. 단말기 화면 크기(px) 구하기
			val displayMetrics = requireContext().resources.displayMetrics
			val screenWidth = displayMetrics.widthPixels
      val screenHeight = displayMetrics.heightPixels

			// 3. 원하는 퍼센트 설정 (예: 가로 85%, 세로 60%)
			val width = (screenWidth * 0.8).toInt() // ViewGroup.LayoutParams.WRAP_CONTENT
			val height = (screenHeight * 0.5).toInt() // ViewGroup.LayoutParams.WRAP_CONTENT //  💡 세로는 뷰 크기에 맞추려면 ViewGroup.LayoutParams.WRAP_CONTENT 사용

			// 4. 윈도우 크기 적용
			window.setLayout(width, height)
		}
	}
}