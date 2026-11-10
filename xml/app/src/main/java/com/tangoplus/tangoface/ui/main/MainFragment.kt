package com.tangoplus.tangoface.ui.main

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.skydoves.balloon.ArrowOrientation
import com.skydoves.balloon.ArrowPositionRules
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.createBalloon
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.databinding.FragmentMainBinding
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.util.PreferenceUtility
import com.tangoplus.tangoface.domain.vo.MeasureType
import com.tangoplus.tangoface.ui.adapter.MainTabRVAdapter
import com.tangoplus.tangoface.ui.listener.OnMainTabClickListener
import com.tangoplus.tangoface.ui.main.vm.MainViewModel
import com.tangoplus.tangoface.ui.measure.DepthCameraActivity
import com.tangoplus.tangoface.ui.measure.JawGuideDialogFragment
import com.tangoplus.tangoface.ui.measure.MImageFragment
import com.tangoplus.tangoface.ui.measure.ModelScanFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainFragment : Fragment(), OnMainTabClickListener {

	private var _bd: FragmentMainBinding? = null
	private val bd get() = _bd!!
	private val mvm: MainViewModel by activityViewModels()
	private var selectedMeasureType: MeasureType = MeasureType.NORMAL
	private val tabs = listOf("간편 검사", "정밀 검사", "사진 검사", "페이스 보우", )
	private lateinit var prefs : PreferenceUtility
	private val cameraResultLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			val serverSn = result.data?.getIntExtra("EXTRA_MEASURE_SN", -1) ?: -1
			if (serverSn != -1) {
				lifecycleScope.launch(Dispatchers.IO) {
					val fd = FaceDatabase.getDatabase(requireContext())
					val faceResult = mvm.convertToFaceResult(requireContext(), fd.faceDao(), serverSn)

					withContext(Dispatchers.Main) {
						mvm.currentResult.value = faceResult
						(requireActivity() as MainActivity).navigateToMeasureHistoryInformation(faceResult.measureType)
					}
				}
			}
		}
	}

	private val usbReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			when (intent?.action) {
				UsbManager.ACTION_USB_DEVICE_ATTACHED,
				UsbManager.ACTION_USB_DEVICE_DETACHED -> {
					checkAndLogUsbDevices()
				}
			}
		}
	}

	private fun checkAndLogUsbDevices() {
		val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
		mvm.updateUsbDeviceList(usbManager.deviceList)
		Log.d("USB_CHECK", "장치 갱신 완료: 연결 여부 = ${mvm.isDepthConnected.value}")
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_bd = FragmentMainBinding.inflate(inflater, container, false)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)


		prefs = PreferenceUtility(requireContext())
		// 1. USB 실시간 리시버 등록 및 초기 상태 체크
		val filter = IntentFilter().apply {
			addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
			addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
		}
		requireContext().registerReceiver(usbReceiver, filter)
		checkAndLogUsbDevices()

		// 2. 어댑터 연결 및 StateFlow 관찰
		setAdapter()
		observeViewModel()
		// 3. 최초 진입 시 상단 영역 슬라이드 다운 애니메이션 (1회)
		if (mvm.isAlertShow.value == false) {
			playAlertSlideDownAnimation()
			mvm.isAlertShow.value = true
		}

		// 4. RAM 모니터링 및 도움말 롤링 시작
		mvm.startRamMonitoring(requireContext().applicationContext)
		mvm.startGuideRolling()

		bd.ibtnMSlide.setOnClickListener {
			WaitingDialogFragment().show(childFragmentManager, "LookbookDialog")
		}
		bd.tvMRam.setOnClickListener {
			val balloon = createRamInfoBalloon()
			balloon.showAlignBottom(bd.tvMRam)
		}

		bd.tvMModelScan.setOnSingleClickListener {
			(requireActivity() as MainActivity).navigateToSubFragment(ModelScanFragment())
		}
	}

	private fun setAdapter() {
		val mAdapter = MainTabRVAdapter(requireContext(), tabs).apply {
			onMainTabClickListener = this@MainFragment
		}
		val gLayoutManager = GridLayoutManager(requireContext(), 2)
		bd.rvM.post {
			bd.rvM.adapter = mAdapter
			bd.rvM.layoutManager = gLayoutManager
		}
	}

	private var isFirstRamBalloonShown = false
	private fun observeViewModel() {
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

				// 헤더: RAM 사용량 (tvMRam)
				launch {
					mvm.ramUsage.collect { mem ->
						bd.tvMRam.text = "📊 램 사용량: ${mem}%"

						// 90% 이상 시 색상 변경
						val ramColor = if (mem >= 90) {
							ContextCompat.getColor(requireContext(), R.color.accent)
						} else {
							ContextCompat.getColor(requireContext(), R.color.accent100)
						}
						bd.tvMRam.setTextColor(ramColor)

						// 앱 설치 후 최초 1회만 자동 표시
						if (!prefs.isFirstRamBalloonShown() && mem > 0) {
							prefs.setFirstRamBalloonShown()
							bd.tvMRam.post {
								createRamInfoBalloon().showAlignBottom(bd.tvMRam)
							}
						}
					}
				}

				// 헤더: 3D 뎁스 카메라 상태 (tvMCameraConnect)
				launch {
					mvm.isDepthConnected.collect { isConnected ->
						bd.tvMCameraConnect.text = if (isConnected) "카메라 연결됨" else "카메라 연결 없음"
						val colorRes = if (isConnected) R.color.normal else R.color.red
						val colorStateList = ContextCompat.getColorStateList(requireContext(), colorRes)
						TextViewCompat.setCompoundDrawableTintList(bd.tvMCameraConnect, colorStateList)
					}
				}

				// 중앙 상단: 1줄 가이드 슬라이드 롤링 (tvMNoticeDesc)
				launch {
					mvm.currentGuide.collect { guideText ->
						updateGuideWithAnimation(guideText)
					}
				}
			}
		}
	}

	private fun updateGuideWithAnimation(text: String) {
		bd.tvMNoticeDesc.animate()
			.translationY(-12f).alpha(0f).setDuration(200)
			.withEndAction {
				bd.tvMNoticeDesc.text = text
				bd.tvMNoticeDesc.translationY = 12f
				bd.tvMNoticeDesc.animate().translationY(0f).alpha(1f).setDuration(200).start()
			}.start()
	}

	private fun playAlertSlideDownAnimation() {
		bd.clMNotice.post {
			bd.clMNotice.translationY = -40f
			bd.clMNotice.alpha = 0f

			bd.clMNotice.animate()
				.translationY(0f)
				.alpha(1f)
				.setDuration(450)
				.setInterpolator(android.view.animation.DecelerateInterpolator())
				.start()
		}
	}

	override fun onMainTabClick(tab: String) {
		when (tab) {
			tabs[0] -> selectedMeasureType = MeasureType.NORMAL
			tabs[1] -> selectedMeasureType = MeasureType.EXPERT
			tabs[2] -> {
				(requireActivity() as MainActivity).navigateToSubFragment(MImageFragment())
				return
			}
			tabs[3] -> selectedMeasureType = MeasureType.BOW

		}
		checkStoragePermission()
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
			navigateToCamera()
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
			navigateToCamera()
		} else {
			Log.e("MainFragment", "저장소 권한 거부됨")
			showPermissionDeniedDialog()
		}
	}

	private fun navigateToCamera() {
		val intent = Intent(requireContext(), DepthCameraActivity::class.java).apply {
			putExtra("selectedMeasureType", selectedMeasureType)
		}
		cameraResultLauncher.launch(intent)
	}

	private fun showPermissionDeniedDialog() {
		MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
			.setTitle("저장소 권한 필요")
			.setMessage("앱을 사용하려면 저장소 권한이 필요합니다.\n설정에서 권한을 허용해주세요.")
			.setPositiveButton("설정으로 이동") { _, _ ->
				val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
					data = Uri.fromParts("package", requireContext().packageName, null)
				}
				startActivity(intent)
			}
			.setNegativeButton("취소", null)
			.show()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		try {
			requireContext().unregisterReceiver(usbReceiver)
			bd.tvMNoticeDesc.animate().cancel()
			bd.clMNotice.animate().cancel()
		} catch (e: Exception) {
			// Unregistered 예외 방어
		}
		_bd = null
	}

	private fun createRamInfoBalloon(): com.skydoves.balloon.Balloon {
		return createBalloon(requireContext()) {
			setWidth(BalloonSizeSpec.WRAP)
			setHeight(BalloonSizeSpec.WRAP)
			setText("현재 시스템 RAM 점유율입니다.\n90% 이상 시 원활한 3D 측정을 위해\n백그라운드 앱 정리를 권장합니다.")
			setTextColor(ContextCompat.getColor(requireContext(), R.color.accent100))
			setTextSize(12f)
			setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
			setArrowOrientation(ArrowOrientation.BOTTOM)
			setArrowSize(8)
			setPadding(10)
			setCornerRadius(8f)
			setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.accent)) // 다크 와인/차콜 반투명
			setBalloonAnimation(BalloonAnimation.FADE)
			setLifecycleOwner(viewLifecycleOwner)
		}
	}

}