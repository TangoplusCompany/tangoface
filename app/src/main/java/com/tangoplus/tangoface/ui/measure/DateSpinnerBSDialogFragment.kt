package com.tangoplus.tangoface.ui.measure

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.activityViewModels
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tangoplus.tangoface.databinding.FragmentDateSpinnerBSDialogBinding
import com.tangoplus.tangoface.ui.measure.vm.InputViewModel
import java.util.Calendar

class DateSpinnerBSDialogFragment : BottomSheetDialogFragment() {
	private lateinit var bd: FragmentDateSpinnerBSDialogBinding
	private val ivm: InputViewModel by activityViewModels()
	private var onDateSelectedListener: ((Int, Int, Int) -> Unit)? = null

	// 날짜 선택 리스너 설정
	fun setOnDateSelectedListener(listener: (year: Int, month: Int, day: Int) -> Unit) {
		onDateSelectedListener = listener
	}


	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentDateSpinnerBSDialogBinding.inflate(inflater)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		dialog?.setOnShowListener { dialogInterface ->
			val bottomSheetDialog = dialogInterface as? BottomSheetDialog
			val bottomSheet = bottomSheetDialog?.findViewById<View>(
				R.id.design_bottom_sheet
			)
			bottomSheet?.let {
				val behavior = BottomSheetBehavior.from(it)
				behavior.state = BottomSheetBehavior.STATE_EXPANDED
				behavior.skipCollapsed = true // collapsed 상태 건너뛰기
			}
		}
		dialog?.window?.setFlags(
			WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
			WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
		)

		val currentYear = Calendar.getInstance().get(Calendar.YEAR)

		// 년도 설정
		bd.yearPicker.apply {
			minValue = 1900
			maxValue = currentYear
			value = currentYear - 25 // 기본값 20살
			wrapSelectorWheel = false
		}

		// 월 설정
		val months = arrayOf(
			"1월", "2월", "3월", "4월", "5월", "6월",
			"7월", "8월", "9월", "10월", "11월", "12월"
		)
		bd.monthPicker.apply {
			minValue = 1
			maxValue = 12
			value = 1
			displayedValues = months
			wrapSelectorWheel = true
		}

		// 일 설정
		bd.dayPicker.apply {
			minValue = 1
			maxValue = 31
			value = 1
			wrapSelectorWheel = true
		}

		// 월/년도 변경 시 일수 업데이트
		val updateDayPicker = {
			val daysInMonth = getDaysInMonth(bd.yearPicker.value, bd.monthPicker.value)
			bd.dayPicker.maxValue = daysInMonth
			if (bd.dayPicker.value > daysInMonth) {
				bd.dayPicker.value = daysInMonth
			}
		}

		bd.yearPicker.setOnValueChangedListener { _, _, _ -> updateDayPicker() }
		bd.monthPicker.setOnValueChangedListener { _, _, _ -> updateDayPicker() }

		// 확인 버튼
		bd.btnConfirm.setOnClickListener {
			val year = bd.yearPicker.value
			val month = bd.monthPicker.value.toString().padStart(2, '0')
			val day = bd.dayPicker.value.toString().padStart(2, '0')
			val birthdayString = "$year-$month-$day"
			onDateSelectedListener?.invoke(year, bd.monthPicker.value, bd.dayPicker.value)
			ivm.birthdayValue.value = birthdayString
			Log.v("생년월일", birthdayString)
			dismiss()
		}

		// 취소 버튼
		bd.btnCancel.setOnClickListener {
			dismiss()
		}
	}

	private fun getDaysInMonth(year: Int, month: Int): Int {
		val calendar = Calendar.getInstance()
		calendar.set(year, month - 1, 1)
		return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
	}

}