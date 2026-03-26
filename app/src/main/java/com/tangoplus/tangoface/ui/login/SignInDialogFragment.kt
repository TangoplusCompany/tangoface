package com.tangoplus.tangoface.ui.login

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.FragmentSignInDialogBinding
import com.tangoplus.tangoface.domain.util.AnimationUtility.setGuideAnimation
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import java.util.regex.Pattern

class SignInDialogFragment : DialogFragment() {
	private lateinit var bd: FragmentSignInDialogBinding
	private val lvm: LoginViewModel by activityViewModels()
	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentSignInDialogBinding.inflate(inflater)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		setObserver()
		setPattern()
		setClickListener()
	}

	private fun setPattern() {
		val emailPattern =
			"^[a-zA-Z0-9_+.-]{4,20}@([a-zA-Z0-9-]{1,63}\\.)+[a-zA-Z]{2,10}\$" // 영문, 숫자 4 ~ 16자 패턴
		val emailPatternCheck = Pattern.compile(emailPattern)
		val pwPattern = "^[\\s\\S]{4,20}$" // 영문, 특수문자, 숫자 8 ~ 20자 패턴 ^[a-zA-Z0-9]{6,20}$
		val pwPatternCheck = Pattern.compile(pwPattern)
		val regNumPattern = "$$/^[a-zA-Z0-9]{4,12}$/$$"
		val regNumPatternCheck = Pattern.compile(regNumPattern)
		bd.etSIDEmail.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				lvm.fullEmail.value = s.toString()
				lvm.currentEmailCon.value = emailPatternCheck.matcher(bd.etSIDEmail.text.toString()).find()
			}
		})
		bd.etSIDPassword0.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				lvm.fullPassword.value = s.toString()
				lvm.currentPwCon.value = pwPatternCheck.matcher(bd.etSIDPassword0.text.toString()).find()
			}
		})
		bd.etSIDPassword1.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				lvm.fullPassword.value = s.toString()
				lvm.currentPwCon.value = pwPatternCheck.matcher(bd.etSIDPassword0.text.toString()).find()
			}
		})

		bd.etSIDRegistrationNum.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				lvm.fullRegNum.value = s.toString()
				lvm.regNumCondition.value =
					regNumPatternCheck.matcher(bd.etSIDRegistrationNum.text.toString()).find()
			}
		})

		bd.etSIDRegistrationNum.setOnEditorActionListener { _, actionId, event ->
			if (actionId == EditorInfo.IME_ACTION_DONE ||
				(event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
			) {
				trySignIn()
				true  // 이벤트 처리가 완료되었음을 반환
			} else {
				false // 다른 동작들은 그대로 유지
			}
		}
	}

	private fun setObserver() {
		lvm.signInAllCondition.observe(viewLifecycleOwner) { access ->
			if (access) setGuideAnimation(bd.btnSIDSIgnIn, 1)
		}
	}

	private fun trySignIn() {

	}

	private fun setClickListener() {
		bd.btnSIDSendCode.setOnSingleClickListener {
			if (context != null) {
				MaterialAlertDialogBuilder(
					requireContext(),
					R.style.ThemeOverlay_App_MaterialAlertDialog
				).apply {
					setTitle("알림")
					setMessage("${lvm.fullEmail.value}로 인증번호를 전송하시겠습니까?")
					setPositiveButton("예") { _, _ ->
						// TODO 251124 여기다가 인증번호 대충 API + 회원가입 완료 절차 사용 후 넣기
					}
					setNegativeButton("아니오") { _, _ ->

					}
				}
			}
		}
	}
}