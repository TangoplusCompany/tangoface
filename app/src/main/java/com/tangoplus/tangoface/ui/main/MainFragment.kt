package com.tangoplus.tangoface.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.tangoplus.tangoface.databinding.FragmentMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.util.PreferenceUtility
import com.tangoplus.tangoface.domain.vo.MeasureType
import com.tangoplus.tangoface.ui.adapter.AdVPAdapter
import com.tangoplus.tangoface.ui.adapter.MainTabRVAdapter
import com.tangoplus.tangoface.ui.listener.OnMainTabClickListener
import com.tangoplus.tangoface.ui.measure.DepthCameraActivity
import com.tangoplus.tangoface.ui.main.vm.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import androidx.core.view.isGone
import com.tangoplus.tangoface.ui.measure.MeasureImageFragment


class MainFragment : Fragment(), OnMainTabClickListener {
	private lateinit var bd: FragmentMainBinding
	private val mvm: MainViewModel by activityViewModels()
	private var selectedMeasureType : MeasureType = MeasureType.NORMAL
	private val cameraResultLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			val serverSn = result.data?.getIntExtra("EXTRA_MEASURE_SN", -1) ?: -1
			if (serverSn != -1) {
				// DB 작업 실행
				lifecycleScope.launch(Dispatchers.IO) {
					val fd = FaceDatabase.getDatabase(requireContext())
					val result = mvm.convertToFaceResult(requireContext(), fd.faceDao(), serverSn)

					withContext(Dispatchers.Main) {
						mvm.currentResult.value = result
						// 2. 작업이 끝나면 MainActivity의 함수를 호출해서 화면/탭 전환!
						(requireActivity() as MainActivity).navigateToMeasureHistoryInformation(result.measureType)
					}
				}
			}
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentMainBinding.inflate(layoutInflater)
		return bd.root
	}
	private fun checkAndLogUsbDevices() {
		val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
		val deviceList = usbManager.deviceList

		if (deviceList.isEmpty()) {
			Log.d("USB_CHECK", "연결된 USB 장치가 없습니다.")
			return
		}

		Log.d("USB_CHECK", "--- 연결된 USB 장치 목록 (${deviceList.size}개) ---")
		for ((key, device) in deviceList) {
			Log.d("USB_CHECK", "Device Key: $key")
			Log.d("USB_CHECK", "Device Name (경로): ${device.deviceName}")
			Log.d("USB_CHECK", "Vendor ID: ${device.vendorId} (Hex: 0x${Integer.toHexString(device.vendorId)})")
			Log.d("USB_CHECK", "Product ID: ${device.productId} (Hex: 0x${Integer.toHexString(device.productId)})")
			Log.d("USB_CHECK", "Manufacturer Name: ${device.manufacturerName}")
			Log.d("USB_CHECK", "Product Name: ${device.productName}")
			Log.d("USB_CHECK", "-----------------------------------------")
		}
	}
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		checkAndLogUsbDevices()
		// seq Select를 위한
		setAdapter()
		setupViewPager()
		bd.clMStart.setOnSingleClickListener {
			val recyclerView = bd.rvM
			if (recyclerView.isGone) {
				recyclerView.alpha = 0f
				recyclerView.translationY = 30f
				recyclerView.visibility = View.VISIBLE
				recyclerView.animate()
					.translationY(0f)
					.alpha(1f)
					.setDuration(250)
					.setInterpolator(DecelerateInterpolator())
					.start()
			} else {
				recyclerView.animate()
					.translationY(30f)
					.alpha(0f)
					.setDuration(200)
					.setInterpolator(AccelerateInterpolator())
					.withEndAction { recyclerView.visibility = View.GONE }
					.start()
			}
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
		val intent = Intent(requireContext(), DepthCameraActivity::class.java)
		intent.putExtra("selectedMeasureType", selectedMeasureType)
//		val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
//		val deviceList = usbManager.deviceList
//
//		val isGeminiCameraConnected = deviceList.values.any { it.productName?.contains("Gemini 336") == true }
//		val intent = if (isGeminiCameraConnected) {
//			Intent(requireContext(), DepthCameraActivity::class.java)
//		} else {
//			Intent(requireContext(), NormalCameraActivity::class.java)
//		}
		cameraResultLauncher.launch(intent)
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
	private val promoImages = listOf(
		R.drawable.poster_main_0,
		R.drawable.poster_main_1,
		R.drawable.poster_main_2,
	)
	private val autoScrollHandler = Handler(Looper.getMainLooper())
	private var autoScrollRunnable: Runnable? = null
	var startX = 0f
	var startY = 0f
	@SuppressLint("ClickableViewAccessibility")
  private fun setupViewPager() {
		bd.clM.post {
			bd.vpM.layoutParams = bd.vpM.layoutParams.apply {
				height = bd.clM.height
			}
		}
		val viewPagerChild = bd.vpM.getChildAt(0) as? RecyclerView
		viewPagerChild?.setOnTouchListener { v, event ->
			when (event.action) {
				MotionEvent.ACTION_DOWN -> {
					startX = event.x
					startY = event.y
				}
				MotionEvent.ACTION_MOVE -> {
					val dx = abs(event.x - startX)
					val dy = abs(event.y - startY)
					v.parent.requestDisallowInterceptTouchEvent(dx > dy)
				}
			}
			false
		}
		val pPrefs = PreferenceUtility(requireContext())
		var savedPosters = pPrefs.getPosters()

		// 2. 데이터가 없을 경우를 대비한 예외 처리
		if (savedPosters.isEmpty()) {
			pPrefs.initDefaultPosters(requireContext())
		}
		savedPosters = pPrefs.getPosters()
		// 3. 어댑터 연결 (String 리스트를 받도록 AdVPAdapter가 수정되었다고 가정)
		val adapter = AdVPAdapter(savedPosters)
		bd.vpM.adapter = adapter

		// 4. 무한 스크롤 설정 (이미지가 2장 이상일 때만 의미가 있음)
		if (savedPosters.size > 1) {
			val startPosition = Int.MAX_VALUE / 2
			// 실제 인덱스에 맞춰서 중앙 위치 계산
			val jumpPosition = startPosition - (startPosition % savedPosters.size)
			bd.vpM.setCurrentItem(jumpPosition, false)

			// 자동 스크롤 시작
			startAutoScroll()
		}
		// 사용자가 터치하면 자동 스크롤 일시 중지 후 재시작
		bd.vpM.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
			override fun onPageScrollStateChanged(state: Int) {
				when (state) {
					ViewPager2.SCROLL_STATE_DRAGGING -> stopAutoScroll()
					ViewPager2.SCROLL_STATE_IDLE -> startAutoScroll()
				}
			}
		})
	}



	private fun setAdapter() {
		bd.rvM.visibility = View.GONE
		val mAdapter = MainTabRVAdapter(tabs)
		mAdapter.onMainTabClickListener = this@MainFragment
		val vLayoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL , false)
		bd.rvM.apply {
			adapter= mAdapter
			layoutManager = vLayoutManager
		}
	}
	private fun startAutoScroll() {
		stopAutoScroll() // 기존 것 제거
		val prefs = PreferenceUtility(requireContext())
		val setedVelo = prefs.getSlideVelocity()
		autoScrollRunnable = object : Runnable {
			override fun run() {
				bd.vpM.currentItem += 1

				autoScrollHandler.postDelayed(this, (setedVelo * 1000).toLong()) // 5초마다
			}
		}
		autoScrollHandler.postDelayed(autoScrollRunnable!!, (setedVelo * 1000).toLong())
	}

	private fun stopAutoScroll() {
		autoScrollRunnable?.let {
			autoScrollHandler.removeCallbacks(it)
		}
	}

	override fun onResume() {
		super.onResume()
		startAutoScroll()
	}

	override fun onPause() {
		super.onPause()
		stopAutoScroll()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		stopAutoScroll()
	}
	private val tabs = listOf(   "내 사진 분석", "페이스 보우", "비대칭 진단","얼굴 밸런스",)
	override fun onMainTabClick(tab: String) {
		when (tab) {
			tabs[3] -> { selectedMeasureType = MeasureType.NORMAL }
			tabs[2] -> { selectedMeasureType = MeasureType.EXPERT }
			tabs[1] -> { selectedMeasureType = MeasureType.BOW }
			tabs[0] -> {
				(requireActivity() as MainActivity).navigateToSubFragment(MeasureImageFragment())
				return
			}
		}
		checkStoragePermission()
	}

}