package com.tangoplus.facebeautyexpert.ui.measure

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.databinding.FragmentUserSetupDialogBinding
import com.tangoplus.facebeautyexpert.domain.util.AnimationUtility.setGuideAnimation
import com.tangoplus.facebeautyexpert.domain.util.AnimationUtility.startTypingAnimation
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.domain.util.PreferenceUtility
import com.tangoplus.facebeautyexpert.domain.util.SecurityUtility.generateCustomUUID
import com.tangoplus.facebeautyexpert.ui.measure.vm.InputViewModel
import com.tangoplus.facebeautyexpert.ui.measure.vm.MeasureViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.regex.Pattern

class UserSetupDialogFragment : DialogFragment() {
	private lateinit var bd: FragmentUserSetupDialogBinding
	private val ivm: InputViewModel by activityViewModels()
	private val mvm: MeasureViewModel by activityViewModels()
	private var touchX: Float = 0f
	private var touchY: Float = 0f


	// parent Type
	// 0 -> depthCameraActivity
	// 1 -> MeasureImageFragment

	companion object {
		private const val ARG_PARENT = "PARENT_TYPE"
		fun newInstance(parent: Int) : UserSetupDialogFragment {
			val fragment = UserSetupDialogFragment()
			val args = Bundle()
			args.putInt(ARG_PARENT, parent)
			fragment.arguments = args
			return fragment
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentUserSetupDialogBinding.inflate(inflater)
		isCancelable = false
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
		val parentCase = arguments?.getInt(ARG_PARENT) ?: 0
		bd.btnUSDProgress.text = when (parentCase) {
			0 -> "사전 체크리스트 시작"
			else -> "입력 완료"
		}

		// 초기 설정: 이름 입력창이 나오고, 키보드가 올라옴
		bd.etUSDMobile.visibility = View.INVISIBLE
//		bd.btnUSDProgress.visibility = View.INVISIBLE
		bd.clUSDGender.visibility = View.INVISIBLE
		bd.clUSDAgeRange.visibility = View.INVISIBLE
		setGuideAnimation(bd.etUSDName)
		lifecycleScope.launch {
			startTypingAnimation(bd.tvUSDGuide, "안녕하세요\n분석을 위해 성함을 입력해주세요")
		}

		// 버튼 클릭 위치 감지
		bd.btnUSDProgress.setOnTouchListener { _, event ->
			if (event.action == MotionEvent.ACTION_DOWN) {
				touchX = event.x
				touchY = event.y
			}
			false
		}

		bd.etUSDName.postDelayed({
			if (isAdded) { // Fragment가 Activity에 붙어 있는지 확인
				bd.etUSDName.requestFocus()
				val imm = requireActivity().getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
				imm.showSoftInput(bd.etUSDName, InputMethodManager.SHOW_IMPLICIT)
			}
		}, 500)

		// 이름
		val nameTextWatcher = namePatternCheck(ivm, bd.etUSDName)
		bd.etUSDName.addTextChangedListener(nameTextWatcher)
		bd.etUSDName.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
				if (ivm.nameCondition.value == true) {
					setGuideAnimation(bd.etUSDMobile)
					bd.etUSDMobile.requestFocus()
					lifecycleScope.launch {
						startTypingAnimation(bd.tvUSDGuide, "다음은 휴대폰 번호 입니다\n숫자만 입력해주세요")
					}
				}
				return@setOnEditorActionListener true
			}
			false
		}

		// 휴대폰 번호 넣기
		val mobilePattern = "^010-\\d{4}-\\d{4}\$"
		val mobilePatternCheck = Pattern.compile(mobilePattern)
		bd.etUSDMobile.addTextChangedListener(object : TextWatcher {
			private var isFormatting = false
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {

				if (isFormatting) return
				isFormatting = true
				val cleaned = s.toString().replace("-", "")
				val maxDigits = 11
				val limited = if (cleaned.length > maxDigits) cleaned.substring(0, maxDigits) else cleaned

				val formatted = when {
					limited.length <= 3 -> limited
					limited.length <= 7 -> "${limited.substring(0, 3)}-${limited.substring(3)}"
					else -> "${limited.substring(0, 3)}-${limited.substring(3, 7)}-${limited.substring(7)}"
				}

				// 기존 입력과 다를 때만 업데이트
				if (s.toString() != formatted && s != null) {
					bd.etUSDMobile.setText(formatted) // setText를 사용하여 확실하게 변경
					bd.etUSDMobile.setSelection(formatted.length) // 커서를 마지막 위치로 이동
				}
				if (s.toString().length > 1) bd.tvUSDMobileGuide.visibility  =  View.VISIBLE

				isFormatting = false
				ivm.mobileCondition.value =
					mobilePatternCheck.matcher(bd.etUSDMobile.text.toString()).find()
				if (ivm.mobileCondition.value == true) {
					bd.etUSDMobile.backgroundTintList =
						ContextCompat.getColorStateList(requireContext(), R.color.black)
					bd.tvUSDMobileGuide.apply {
						text = "올바른 전화번호 형식입니다"
						setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.accentContrast))
					}
					ivm.mobileValue.value = bd.etUSDMobile.text.toString().replace("-", "")

					// 키보드 내리고 버튼 보이기
					val imm = requireActivity().getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
					imm.hideSoftInputFromWindow(bd.etUSDName.windowToken, 0)

					setGuideAnimation(bd.clUSDGender)
					setGuideAnimation(bd.clUSDAgeRange)
					setGenderButtons()
					setBirthdayButton()
					setNationalityButton()
					lifecycleScope.launch {
						startTypingAnimation(bd.tvUSDGuide, "감사합니다!\n마지막으로 성별과 연령대를 선택해주세요")
					}

				} else {
					bd.etUSDMobile.backgroundTintList =
						ContextCompat.getColorStateList(requireContext(), R.color.red)
					bd.tvUSDMobileGuide.apply {
						setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.accent))
						text = "휴대전화번호: 010으로 시작하는 휴대폰 번호를 정확히 입력해주세요"
					}
				}
			}
		})

		bd.etUSDMobile.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
				if (ivm.mobileCondition.value == true) {
					// 키보드 숨기기
					val imm = requireActivity().getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
					imm.hideSoftInputFromWindow(bd.etUSDName.windowToken, 0)

					setGuideAnimation(bd.clUSDGender)
					setGuideAnimation(bd.clUSDAgeRange)
					setGenderButtons()
					setBirthdayButton()
					setNationalityButton()
					lifecycleScope.launch {
						startTypingAnimation(bd.tvUSDGuide, "감사합니다!\n마지막으로 성별과 연령대를 선택해주세요")
					}
				}
				return@setOnEditorActionListener true
			}
			false
		}

		ivm.inputCondition.observe(viewLifecycleOwner) { allTrue ->
			if (allTrue) {
				if (!ivm.isShownBtn) {
					setGuideAnimation(bd.btnUSDProgress, 1)
					ivm.isShownBtn = true
				}
				bd.btnUSDProgress.isEnabled = true
			} else {
				bd.btnUSDProgress.isEnabled = false
			}
		}

		bd.btnUSDProgress.setOnSingleClickListener {
			val key = getString(R.string.kekeke)
			val uuid = generateCustomUUID(ivm.nameValue.value, ivm.mobileValue.value, ivm.birthdayValue.value,ivm.genderValue.value,key)
			Log.v("createdUUID", uuid)
			mvm.currentUUID = uuid

			when (parentCase) {
				0 -> {
					val onBoardingDialog = OnBoardingDialogFragment()
					onBoardingDialog.show(requireActivity().supportFragmentManager, "")
					Handler(Looper.getMainLooper()).postDelayed({
						dismissAllowingStateLoss()
					}, 300)
				}
				1 -> {
					ivm.isFinishInput.value = true
					dismiss()
				}
			}
		}

		bd.tvUSDSkip.setOnSingleClickListener {
			showSkipDialog()
		}
		bd.etUSDName.setText("")
		bd.etUSDMobile.setText("")
	}

	private fun namePatternCheck(ivm: InputViewModel, input: EditText): TextWatcher {
		val nameRegex = "^[가-힣]{2,5}$|^[a-zA-Z]{2,20}$"
		val namePatternCheck = Pattern.compile(nameRegex)
		return object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				if (s.toString().length > 1) bd.tvUSDNameGuide.visibility  =  View.VISIBLE
				ivm.nameCondition.value = namePatternCheck.matcher(input.text.toString()).find()
//                Log.v("이름컨디션", "${ivm.nameCondition.value}, ${input.text}")
//                Log.v("보스", "${ivm.inputCondition.value}, ${ivm.nameCondition.value}, ${ivm.mobileCondition.value}")
				if (ivm.nameCondition.value == true) {
					input.backgroundTintList =
						ContextCompat.getColorStateList(requireContext(), R.color.black)
					ivm.nameValue.value = input.text.toString().trim()
					bd.tvUSDNameGuide.apply {
						text = "사용가능한 이름 형식입니다"
						setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.accentContrast))
					}

				} else {
					input.backgroundTintList =
						ContextCompat.getColorStateList(requireContext(), R.color.red)
					bd.tvUSDNameGuide.apply {
						text = "이름: 한국어(2-5자), 영어(2-20자)만 사용가능합니다."
						setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.accent))
					}
				}
			}
		}
	}


	private fun showSkipDialog() {
		val key = getString(R.string.kekeke)
		MaterialAlertDialogBuilder(
			requireContext(),
			R.style.ThemeOverlay_App_MaterialAlertDialog
		).apply {
			setTitle("알림")
			setMessage("개인정보 입력을 건너뛰시겠습니까?")
			setPositiveButton("예") { _, _ ->

				ivm.nameValue.value = "GUEST"
				ivm.mobileValue.value =
					(PreferenceUtility(requireContext()).getLastTempServerSn() + 1).toString() // 측정 결과 저장 전이니 일단 +1 한 temp_server_sn을 mobile로 uuid생성

				val uuid = generateCustomUUID(ivm.nameValue.value, ivm.mobileValue.value, ivm.birthdayValue.value,ivm.genderValue.value,key)
				Log.v("createdUUID", uuid)
				mvm.currentUUID = uuid
				ivm.isFinishInput.value = true
				dismiss()
			}
			setNegativeButton("아니오") { _, _ -> }
			show()
		}
	}

	private fun setGenderButtons() {
		val btns = listOf(bd.tvUSDGender0, bd.tvUSDGender1)
		ivm.genderValue.observe(viewLifecycleOwner) {
			ivm.genderCondition.value = it > -1
		}
		btns.forEachIndexed { index, btn ->
			btn.setOnSingleClickListener {
				ivm.genderValue.value = index

				btns.forEachIndexed { indexx, tv ->
//					tv.setTextColor(
//						ContextCompat.getColor(
//							requireContext(),
//							if (indexx == index) R.color.white else R.color.black
//						)
//					)
					tv.backgroundTintList = ColorStateList.valueOf(
						ContextCompat.getColor(
							requireContext(),
							if (indexx == index) R.color.accent else R.color.bg500
						)
					)
				}
			}
		}
	}

	private fun setBirthdayButton() {
		bd.tvUSDBirthday.setOnSingleClickListener {
			val bottomSheet = DateSpinnerBSDialogFragment()

			bottomSheet.setOnDateSelectedListener { year, month, day ->
			}
			bottomSheet.show(requireActivity().supportFragmentManager, "")
		}

		ivm.birthdayValue.observe(viewLifecycleOwner) { bv ->
			Log.v("생년월일1", "${bv}")
			bd.tvUSDBirthday.text = bv

			ivm.birthdayCondition.value = if (bv != "날짜 선택") true else false
		}
	}

	private fun setNationalityButton() {
		bd.ccpUSD.setCountryForNameCode(Locale.getDefault().country)
		ivm.nationalityValue.value = bd.ccpUSD.selectedCountryCode
		Log.v("ivm.nationlityValue", "0 ${ivm.nationalityValue.value}")
		bd.ccpUSD.setOnCountryChangeListener {
			ivm.nationalityValue.value = bd.ccpUSD.selectedCountryCode
			Log.v("ivm.nationlityValue", "1 ${ivm.nationalityValue.value}")
		}
	}
}