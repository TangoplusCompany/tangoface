package com.tangoplus.tangoface.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.ActivityLoginBinding
import com.tangoplus.tangoface.domain.util.AnimationUtility.setGuideAnimation
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.ui.main.MainActivity
import java.util.regex.Pattern

class LoginActivity : AppCompatActivity() {
	private lateinit var bd: ActivityLoginBinding
	private val lvm: LoginViewModel by viewModels()
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		bd = ActivityLoginBinding.inflate(layoutInflater)
		setContentView(bd.root)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
			insets
		}


		bd.btnLogin.visibility = View.VISIBLE
		setObserver()
		setPattern()
		setClickListener()
	}

	private fun setObserver() {
		lvm.emailPwCondition.observe(this@LoginActivity) { access ->
			if (access) {
				setGuideAnimation(bd.btnLogin, 1)

			}

		}
	}

	private fun setPattern() {
		val emailPattern =
			"^[a-zA-Z0-9_+.-]{4,20}@([a-zA-Z0-9-]{1,63}\\.)+[a-zA-Z]{2,10}\$" // 영문, 숫자 4 ~ 16자 패턴
		val emailPatternCheck = Pattern.compile(emailPattern)
		val pwPattern = "^[\\s\\S]{4,20}$" // 영문, 특수문자, 숫자 8 ~ 20자 패턴 ^[a-zA-Z0-9]{6,20}$
		val pwPatternCheck = Pattern.compile(pwPattern)

		bd.etLEmail.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				lvm.fullEmail.value = s.toString()
				lvm.currentEmailCon.value = emailPatternCheck.matcher(bd.etLEmail.text.toString()).find()
			}
		})
		bd.etLPassword.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				lvm.fullPassword.value = s.toString()
				lvm.currentPwCon.value = pwPatternCheck.matcher(bd.etLPassword.text.toString()).find()
			}
		})
		bd.etLPassword.setOnEditorActionListener { _, actionId, event ->
			if (actionId == EditorInfo.IME_ACTION_DONE ||
				(event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
			) {
				tryLogin()
				true  // 이벤트 처리가 완료되었음을 반환
			} else {
				false // 다른 동작들은 그대로 유지
			}
		}
	}

	private fun tryLogin() {
		val intent = Intent(this@LoginActivity, MainActivity::class.java)
		startActivity(intent)
		finishAffinity()
	}

	private fun setClickListener() {
		bd.tvLSignin.setOnSingleClickListener {
			init()
			val signInDialog = SignInDialogFragment()
			signInDialog.show(supportFragmentManager, "signInDialogFragment")
		}

		bd.tvLFindAccount.setOnSingleClickListener {

		}
	}

	private fun init() {
		lvm.fullEmail.value = ""
		lvm.fullPassword.value = ""
		lvm.currentPwCon.value = false
		lvm.currentEmailCon.value = false
	}
}