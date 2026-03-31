package com.tangoplus.facebeautyexpert.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.tangoplus.facebeautyexpert.databinding.FragmentMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.ui.measure.DepthCameraActivity
import com.tangoplus.facebeautyexpert.ui.main.vm.MainViewModel


class MainFragment : Fragment() {
	private lateinit var bd: FragmentMainBinding
	private val mvm: MainViewModel by activityViewModels()

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentMainBinding.inflate(layoutInflater)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// seq Select를 위한
		bd.btnMainStart.setOnSingleClickListener {
			checkStoragePermission()
		}


	}

	private fun checkStoragePermission() {
		val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			arrayOf(
				Manifest.permission.READ_MEDIA_IMAGES,
				Manifest.permission.READ_MEDIA_VIDEO
			)
		} else {
			arrayOf(
				Manifest.permission.READ_EXTERNAL_STORAGE,
				Manifest.permission.WRITE_EXTERNAL_STORAGE
			)
		}

		val allGranted = permissions.all {
			ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
		}

		if (allGranted) {
			navigateToDepthCamera()
		} else {
			storagePermissionLauncher.launch(permissions)
		}
	}

	private val storagePermissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestMultiplePermissions()
	) { permissions ->
		val allGranted = permissions.values.all { it }

		if (allGranted) {
			Log.d("MainFragment", "저장소 권한 허용됨")
			navigateToDepthCamera()
		} else {
			Log.e("MainFragment", "저장소 권한 거부됨")
			showPermissionDeniedDialog()
		}
	}

	private fun navigateToDepthCamera() {
		val intent = Intent(context, DepthCameraActivity::class.java)
		startActivity(intent)
		requireActivity().finishAffinity()
	}

	// 권한 거부 시 다이얼로그
	private fun showPermissionDeniedDialog() {

		MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
			.setTitle("저장소 권한 필요")
			.setMessage("앱을 사용하려면 저장소 권한이 필요합니다.\n설정에서 권한을 허용해주세요.")
			.setPositiveButton("설정으로 이동") { _, _ ->
				// 앱 설정 화면으로 이동
				val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
					data = Uri.fromParts("package", requireContext().packageName, null)
				}
				startActivity(intent)
			}
			.setNegativeButton("취소", null)
			.show()
	}

}