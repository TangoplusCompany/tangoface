package com.tangoplus.facebeautyexpert.ui

import android.app.Dialog
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.tangoplus.facebeautyexpert.databinding.FragmentLoadingDialogBinding


class LoadingDialogFragment : DialogFragment() {
	private lateinit var bd: FragmentLoadingDialogBinding

	companion object {
		private const val ARG_CASE = "loading_case"
		fun newInstance(case: Int = 0): LoadingDialogFragment {
			val fragment = LoadingDialogFragment()
			val args = Bundle()
			args.putInt(ARG_CASE, case)
			fragment.arguments = args
			return fragment
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentLoadingDialogBinding.inflate(inflater)
		return bd.root
	}

	override fun dismiss() {
		dismissAllowingStateLoss()
	}

	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val dialog = super.onCreateDialog(savedInstanceState)

		dialog.window?.apply {
			setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
			addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
			setDimAmount(0.6f)
		}

		return dialog
	}

	override fun onResume() {
		super.onResume()
		isCancelable = false
		lockScreenOrientation()
	}

	override fun onDestroy() {
		super.onDestroy()
		unlockScreenOrientation()
	}

	private fun lockScreenOrientation() {
		activity?.let { act ->
			val display = act.windowManager.defaultDisplay
			val rotation = display.rotation
			val orientation = act.resources.configuration.orientation

			act.requestedOrientation = when (orientation) {
				Configuration.ORIENTATION_PORTRAIT -> when (rotation) {
					Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
					Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
					else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
				}

				Configuration.ORIENTATION_LANDSCAPE -> when (rotation) {
					Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
					Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
					else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
				}

				else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val loadingCase = arguments?.getInt(ARG_CASE) ?: 0
		bd.tvLD.text = when (loadingCase) {
			0 -> "결과 데이터를 가져오고 있습니다."
			1 -> "결과를 저장중입니다."
			else -> ""
		}
	}

	private fun unlockScreenOrientation() {
		activity?.let { act ->
			// orientation 잠금 해제
			act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
		}
	}

}