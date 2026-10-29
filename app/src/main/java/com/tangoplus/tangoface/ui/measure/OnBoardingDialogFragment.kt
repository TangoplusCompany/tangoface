package com.tangoplus.tangoface.ui.measure

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.domain.vo.PreCheckList
import com.tangoplus.tangoface.domain.util.AnimationUtility.startDismissWithRipple
import com.tangoplus.tangoface.data.db.FaceDao
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.databinding.FragmentOnBoardingDialogBinding
import com.tangoplus.tangoface.domain.util.AnimationUtility.startTypingAnimation
import com.tangoplus.tangoface.ui.adapter.PreCheckListRVAdapter
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.util.PreferenceUtility
import com.tangoplus.tangoface.ui.listener.PreCheckListCheckListener
import com.tangoplus.tangoface.ui.measure.vm.InputViewModel
import com.tangoplus.tangoface.ui.measure.vm.MeasureViewModel
import com.tangoplus.tangoface.ui.measure.vm.OnBoardingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OnBoardingDialogFragment : DialogFragment(), PreCheckListCheckListener {
	private lateinit var bd: FragmentOnBoardingDialogBinding
	private val obvm: OnBoardingViewModel by activityViewModels()
	private val ivm: InputViewModel by activityViewModels()
	private val mvm: MeasureViewModel by activityViewModels()
	private var touchX: Float = 0f
	private var touchY: Float = 0f
	private lateinit var db: FaceDatabase
	private lateinit var fDao: FaceDao
	private val adapters = mutableListOf<PreCheckListRVAdapter>()

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentOnBoardingDialogBinding.inflate(inflater)
		return bd.root
	}

	override fun onResume() {
		super.onResume()
		dialog?.window?.apply {
			setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
			setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
			clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

			// immersive fullscreen 설정
			decorView.systemUiVisibility =
				View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
					View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
					View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
					View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
					View.SYSTEM_UI_FLAG_FULLSCREEN or
					View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
		}
	}

	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val dialog = super.onCreateDialog(savedInstanceState)
		dialog.setCancelable(false)
		dialog.setCanceledOnTouchOutside(false) // 외부 터치 무시
		dialog.setOnKeyListener { _, keyCode, event ->
			if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
				showSkipDialog()
				true // 이벤트 소비
			} else {
				false
			}
		}
		return dialog
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		db = FaceDatabase.getDatabase(requireContext())
		fDao = db.faceDao()
		setAdapter()
		lifecycleScope.launch {
			startTypingAnimation(bd.tvOBDGuide, "평소 관심있었거나 고민이었던 내용이 있으면 체크해주세요!")
		}
		bd.btnOBDProgress.setOnTouchListener { _, event ->
			if (event.action == MotionEvent.ACTION_DOWN) {
				touchX = event.x
				touchY = event.y
			}
			false
		}

		bd.btnOBDProgress.setOnSingleClickListener {
			val checkedItem = obvm.getCheckedItem()
			if (checkedItem.isNotEmpty()) {
				val rawItem = obvm.toPreCheckListEntity(
					checkedItem,
					(PreferenceUtility(requireContext()).getLastTempServerSn() + 1),
					mvm.currentUUID,
					ivm.nameValue.value ?: "",
					ivm.mobileValue.value ?: "",
					ivm.genderValue.value ?: -1,
					ivm.birthdayValue.value ?: "",
					ivm.nationalityValue.value ?: "",
				)
				Log.v("변환된RAW", "$rawItem")
				CoroutineScope(Dispatchers.IO).launch {
					fDao.insertPreCheckListResult(rawItem)
				}
				Log.v("Insert", "insert Success")
			}
			startDismissWithRipple(bd.flOBD, touchX, touchY) {
				ivm.isFinishInput.value = true
				dismissAllowingStateLoss()
			}
		}

		bd.msbOBDFlag.setOnCheckedChangeListener { _, isChecked ->
			if (isChecked) {
				clearAllCheckLists()
				obvm.clearCheckedItem()
			}
		}
	}

	private fun setAdapter() {
		val rvs = listOf(bd.rvOBD0, bd.rvOBD1, bd.rvOBD2, bd.rvOBD3)
		rvs.forEachIndexed { i, rv ->
			val layoutManager = GridLayoutManager(requireContext(), 2)
			val adapter = PreCheckListRVAdapter(obvm, i)
			adapter.preChecklistCheckListener = this@OnBoardingDialogFragment
			rv.layoutManager = layoutManager
			rv.adapter = adapter
			adapters.add(adapter)
		}

	}

	private fun clearAllCheckLists() {
		adapters.forEach { it.setAllOff() }
	}

	override fun preCheckListCheck(
		item: PreCheckList,
		isChecked: Boolean
	) {
		if (isChecked) {
			obvm.addCheckedItem(item)
			bd.msbOBDFlag.isChecked = false
		} else obvm.removeCheckedItem(item)
	}

	private fun showSkipDialog() {

		MaterialAlertDialogBuilder(
			requireContext(),
			R.style.ThemeOverlay_App_MaterialAlertDialog
		).apply {
			setTitle("알림")
			setMessage("체크리스트 선택을 건너뛰시겠습니까?")
			setPositiveButton("예") { _, _ ->
				ivm.isFinishInput.value = true
				dismiss()
			}
			setNegativeButton("아니오") { _, _ -> }
			show()
		}
	}

}