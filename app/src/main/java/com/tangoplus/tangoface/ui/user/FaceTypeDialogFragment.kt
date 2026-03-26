package com.tangoplus.tangoface.ui.user

import android.content.DialogInterface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.databinding.FragmentFaceTypeDialogBinding
import com.tangoplus.tangoface.domain.util.BitmapUtility.getScreenOrientation
import com.tangoplus.tangoface.domain.util.FaceTypeUtility.getIntFromFaceType
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.vo.FaceType
import com.tangoplus.tangoface.ui.adapter.SelectRVAdapter
import com.tangoplus.tangoface.ui.listener.OnFaceTypeClickListener
import com.tangoplus.tangoface.ui.settings.vm.SequenceSelectViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class FaceTypeDialogFragment : DialogFragment(), OnFaceTypeClickListener {
	private lateinit var bd: FragmentFaceTypeDialogBinding
	private val ssvm: SequenceSelectViewModel by activityViewModels()
	private val uvm: UserViewModel by activityViewModels()
	private lateinit var rvAdapter: SelectRVAdapter
	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentFaceTypeDialogBinding.inflate(inflater)
		return bd.root
	}

	override fun onStart() {
		super.onStart()
		dialog?.setCancelable(false)

		// 설정값 — 필요시 조정
		val spanCount = 6                       // Grid span 수
		val itemDp = 200f                       // 각 아이템 고정 dp
		val spacingDp = 10f                     // itemDecoration 간격 dp (left/right 포함 계산)
		// dp -> px
		val dm = resources.displayMetrics
		val itemPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, itemDp, dm).toInt()
		val spacingPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, spacingDp, dm).toInt()


		// 총 너비 계산:
		// 예: spanCount * itemWidth + (spanCount + 1) * spacing (오른쪽/왼쪽 여백 고려) + dialog padding
		val totalSpacing = (spanCount + 1) * spacingPx
		var desiredWidth = spanCount * itemPx + totalSpacing * 2

		// 화면 너비보다 커지면 화면 너비의 95%로 제한 (안 보이는 문제 방지)
		val screenWidth = dm.widthPixels
		val maxAllowed = (screenWidth * 1).toInt()
		if (desiredWidth > maxAllowed) desiredWidth = maxAllowed

		// 실제로 다이얼로그 윈도우에 적용
		dialog?.window?.setLayout(desiredWidth, WindowManager.LayoutParams.WRAP_CONTENT)
	}

	override fun onDismiss(dialog: DialogInterface) {
		super.onDismiss(dialog)
		ssvm.tempSelectedFaceType.value = ssvm.originFaceType.value
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		initSettings()
		setAdapter()
		setObserver()
		setClickListener()
	}

	private fun setObserver() {
		ssvm.tempSelectedFaceType.observe(viewLifecycleOwner) { selected ->
			rvAdapter.selectedType = selected
		}
	}

	private fun initSettings() {

		ssvm.tempSelectedFaceType.value = when (uvm.getUserItem().faceType) {
			0 -> FaceType.EGG
			1 -> FaceType.RHOMBUS
			2 -> FaceType.HEART
			3 -> FaceType.PEANUT
			4 -> FaceType.HEXAGON
			5 -> FaceType.CIRCLE
			else -> null
		}
		ssvm.originFaceType.value = ssvm.tempSelectedFaceType.value
	}


	private fun setAdapter() {
		val isPortrait = activity?.getScreenOrientation()?.contains("portrait")
		val spanCount = if (isPortrait == true) 3 else 6
		val layoutManager =
			GridLayoutManager(context, spanCount)
		rvAdapter = SelectRVAdapter(this@FaceTypeDialogFragment, ssvm, type = 1)
		rvAdapter.onFaceTypeClickListener = this

		bd.rvFSD.layoutManager = layoutManager
		bd.rvFSD.adapter = rvAdapter
	}

	private fun setClickListener() {
		bd.btnFSDConfirm.setOnSingleClickListener {

			val transFaceType = getIntFromFaceType(ssvm.tempSelectedFaceType.value)

			lifecycleScope.launch {
				val fdb = context?.let { it1 -> FaceDatabase.getDatabase(it1) }
				val fDao = fdb?.faceDao()

				val userSn = uvm.getUserItem().userSn
				fDao?.updateFaceType(
					transFaceType, userSn
				)
				withContext(Dispatchers.Main) {
					ssvm.originFaceType.value = ssvm.tempSelectedFaceType.value
					dismiss()
				}
			}

		}

		bd.btnFSDCancel.setOnSingleClickListener { dismiss() }
	}

	override fun onFaceTypeClick(type: FaceType) {
		ssvm.tempSelectedFaceType.value = type
	}
}