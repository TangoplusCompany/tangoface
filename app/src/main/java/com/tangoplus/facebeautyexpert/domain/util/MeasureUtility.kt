package com.tangoplus.facebeautyexpert.domain.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.domain.util.PermissionUtility.getRequiredPermissions
import com.tangoplus.facebeautyexpert.domain.vo.Sequence
import com.tangoplus.facebeautyexpert.ui.LoadingDialogFragment
import com.tangoplus.facebeautyexpert.ui.main.MainActivity

object MeasureUtility {
	fun hasPermissions(context: Context): Boolean {
		Log.d("PermissionCheck", "Context type: ${context::class.java.name}")
		return getRequiredPermissions().all {
			ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
		}
	}

	val slide = TranslateAnimation(0f, 0f, -100f, 0f).apply {
		duration = 800
		interpolator = AccelerateDecelerateInterpolator()
		fillAfter = true
	}
	val fadeIn = AlphaAnimation(0f, 1f).apply {
		duration = 800
	}
	val animationSet = AnimationSet(true).apply {
		addAnimation(slide)
		addAnimation(fadeIn)
		fillAfter = true
	}

	fun getGuideText(seq: Sequence): String {
		return when (seq) {
			Sequence.COMFORT -> "안녕하세요! 화면에 잘 보이게 위치해 주시고\n편한 상태로 입을 다물고 정면을 응시해주세요"
			Sequence.NECK_EXTENTION -> "목을 편 상태입니다. 버튼을 누르고\n코 끝을 하늘을 향하게 턱을 들어주세요"
			else -> ""
		}
	}

	fun showPreviousDialog(context: Context, callback: () -> Unit) {
		MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_App_MaterialAlertDialog).apply {
			setTitle("알림")
			setMessage("이전 단계로 돌아가시겠습니까?")
			setPositiveButton("예") { _, _ -> callback() }
			setNegativeButton("아니오") { _, _ -> }
			show()
		}
	}

	fun showGoMainDialog(currentActivity: AppCompatActivity) {
		MaterialAlertDialogBuilder(currentActivity, R.style.ThemeOverlay_App_MaterialAlertDialog)
			.setTitle("알림")
			.setMessage("측정을 종료하시겠습니까?")
			.setPositiveButton("예") { _, _ ->
				currentActivity.finish() // 또는 isEnabled = false 후 onBackPressedDispatcher.onBackPressed()
				val intent = Intent(currentActivity, MainActivity::class.java)
				currentActivity.startActivity(intent)
			}
			.setNegativeButton("아니요") { dialog, _ -> }
			.setCancelable(true)
			.show()
	}

}