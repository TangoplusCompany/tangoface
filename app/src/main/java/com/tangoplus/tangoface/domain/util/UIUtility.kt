package com.tangoplus.tangoface.domain.util

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.tangoplus.tangoface.ui.info.InformationDialogFragment

object UIUtility {
	fun dpToPx(context: Context, dp: Int): Int {
		return (dp * context.resources.displayMetrics.density).toInt()
	}

	const val INFO_TAG = "information_d_f"
	fun showInfoDF(ma: FragmentActivity) {
		val sfm = ma.supportFragmentManager
		if (sfm.findFragmentByTag(INFO_TAG) != null) {
			Log.d(INFO_TAG, "Dialog is already Created, ignoring...")
			return
		}
		val dialog = InformationDialogFragment()
		dialog.show(sfm, INFO_TAG)
	}
}