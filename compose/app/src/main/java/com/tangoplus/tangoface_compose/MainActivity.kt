package com.tangoplus.tangoface_compose

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tangoplus.tangoface_compose.R
import com.tangoplus.tangoface_compose.di.AppViewModelProvider
import com.tangoplus.tangoface_compose.domain.model.MeasureType
import com.tangoplus.tangoface_compose.domain.model.UserItem
import com.tangoplus.tangoface_compose.ui.info.InfoContainerScreen
import com.tangoplus.tangoface_compose.ui.info.MeasureHistoryScreen
import com.tangoplus.tangoface_compose.ui.main.MainScreen
import com.tangoplus.tangoface_compose.ui.measure.MeasureScreen
import com.tangoplus.tangoface_compose.ui.measure.MeasureViewModel
import com.tangoplus.tangoface_compose.ui.settings.SettingsScreen
import com.tangoplus.tangoface_compose.ui.theme.SubColor100
import com.tangoplus.tangoface_compose.ui.theme.SubColor500
import com.tangoplus.tangoface_compose.ui.theme.TangoAccent
import com.tangoplus.tangoface_compose.ui.theme.TangoBg
import com.tangoplus.tangoface_compose.ui.theme.TangoBg700
import com.tangoplus.tangoface_compose.ui.theme.TangoFaceComposeTheme

/**
 * 하단 / 좌측 네비게이션 3대 탭 (XML bottom_nav_menu.xml 완전 일치)
 * 1. nav_main: "홈 화면" (R.drawable.ic_measure)
 * 2. nav_measure_history: "결과 보기" (R.drawable.ic_record)
 * 3. nav_settings: "설정" (R.drawable.ic_settings)
 */
enum class MainNavTab(val title: String, val iconRes: Int) {
	HOME("홈 화면", R.drawable.ic_measure),
	HISTORY("결과 보기", R.drawable.ic_record),
	SETTINGS("설정", R.drawable.ic_settings)
}

class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		hideSystemUI()
		checkAndRequestUsbPermission()

		setContent {
			TangoFaceComposeTheme {
				val viewModel: MeasureViewModel = viewModel(factory = AppViewModelProvider.Factory)
				val uiState by viewModel.uiState.collectAsState()

				val configuration = LocalConfiguration.current
				val isTabletLandscape = configuration.screenWidthDp >= 600 && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

				// 현재 선택된 네비게이션 탭 (기본: 홈 화면)
				var selectedTab by remember { mutableStateOf(MainNavTab.HOME) }

				// 전체 화면 오버레이 상태 (측정 중 or 상세 결과 확인 중)
				var isMeasuring by remember { mutableStateOf(false) }
				var isViewingDetailResult by remember { mutableStateOf(false) }
				var activeMeasureType by remember { mutableStateOf(MeasureType.NORMAL) }

				// 뒤로가기 제어
				BackHandler(enabled = isMeasuring || isViewingDetailResult) {
					if (isMeasuring) {
						isMeasuring = false
					} else if (isViewingDetailResult) {
						isViewingDetailResult = false
					}
				}

				when {
					// 1. 실시간 카메라 측정 화면 (전체 화면)
					isMeasuring -> {
						MeasureScreen(
							measureType = activeMeasureType,
							onNavigateBack = { isMeasuring = false },
							onMeasureComplete = {
								isMeasuring = false
								isViewingDetailResult = true
							},
							modifier = Modifier.fillMaxSize()
						)
					}

					// 2. 5대 결과 컨테이너 화면 (전체 화면)
					isViewingDetailResult -> {
						InfoContainerScreen(
							measureType = activeMeasureType,
							faceResult = null,
							onNavigateBack = { isViewingDetailResult = false }
						)
					}

					// 3. 메인 쉘 네비게이션 (태블릿 가로는 좌측 레일, 세로는 하단 바)
					else -> {
						if (isTabletLandscape) {
							// 🖥️ 태블릿 가로 (sw600dp-land): 좌측 NavigationRailView + 우측 flMain
							Row(
								modifier = Modifier
									.fillMaxSize()
									.background(TangoBg)
							) {
								NavigationRail(
									modifier = Modifier
										.width(96.dp)
										.fillMaxHeight(),
									containerColor = TangoBg700,
									contentColor = SubColor100
								) {
									Spacer(Modifier.weight(1f))
									MainNavTab.entries.forEach { tab ->
										val isSelected = (selectedTab == tab)
										NavigationRailItem(
											modifier = Modifier.padding(vertical = 8.dp),
											selected = isSelected,
											onClick = { selectedTab = tab },
											icon = {
												Icon(
													painter = painterResource(id = tab.iconRes),
													contentDescription = tab.title,
													modifier = Modifier.size(28.dp)
												)
											},
											label = {
												Text(
													text = tab.title,
													fontSize = 12.sp,
													fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
												)
											},
											colors = NavigationRailItemDefaults.colors(
												selectedIconColor = TangoAccent,
												selectedTextColor = TangoAccent,
												unselectedIconColor = SubColor500,
												unselectedTextColor = SubColor500,
												indicatorColor = TangoAccent.copy(alpha = 0.15f)
											)
										)
									}
									Spacer(Modifier.weight(1f))
								}

								// 우측 컨텐츠 영역 (flMain)
								Box(
									modifier = Modifier
										.weight(1f)
										.fillMaxHeight()
								) {
									when (selectedTab) {
										MainNavTab.HOME -> {
											MainScreen(
												onStartMeasure = { mode ->
													activeMeasureType = mode
													viewModel.selectMode(mode)
													isMeasuring = true
												},
												onOpenSlideDialog = {
													Toast.makeText(this@MainActivity, "슬라이드 룩북", Toast.LENGTH_SHORT).show()
												},
												modifier = Modifier.fillMaxSize()
											)
										}
										MainNavTab.HISTORY -> {
											MeasureHistoryScreen(
												historyList = uiState.historyList,
												onUserClick = { userItem ->
													activeMeasureType = userItem.measureType
													isViewingDetailResult = true
												},
												modifier = Modifier.fillMaxSize()
											)
										}
										MainNavTab.SETTINGS -> {
											SettingsScreen(
												onOptionClick = { option ->
													Toast.makeText(this@MainActivity, option, Toast.LENGTH_SHORT).show()
												},
												onRestoreSuccess = {
													viewModel.loadHistory()
													Toast.makeText(this@MainActivity, "측정 데이터가 성공적으로 갱신되었습니다.", Toast.LENGTH_SHORT).show()
												},
												modifier = Modifier.fillMaxSize()
											)
										}
									}
								}
							}
						} else {
							// 📱 세로 모드 (Portrait): 상단 flMain + 하단 BottomNavigationView (height 88dp)
							Scaffold(
								bottomBar = {
									NavigationBar(
										modifier = Modifier.height(88.dp),
										containerColor = TangoBg700,
										contentColor = SubColor100
									) {
										MainNavTab.entries.forEach { tab ->
											val isSelected = (selectedTab == tab)
											NavigationBarItem(
												selected = isSelected,
												onClick = { selectedTab = tab },
												icon = {
													Icon(
														painter = painterResource(id = tab.iconRes),
														contentDescription = tab.title,
														modifier = Modifier.size(26.dp)
													)
												},
												label = {
													Text(
														text = tab.title,
														fontSize = 12.sp,
														fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
													)
												},
												colors = NavigationBarItemDefaults.colors(
													selectedIconColor = TangoAccent,
													selectedTextColor = TangoAccent,
													unselectedIconColor = SubColor500,
													unselectedTextColor = SubColor500,
													indicatorColor = TangoAccent.copy(alpha = 0.15f)
												)
											)
										}
									}
								},
								containerColor = TangoBg
							) { innerPadding ->
								Box(
									modifier = Modifier
										.fillMaxSize()
										.padding(innerPadding)
								) {
									when (selectedTab) {
										MainNavTab.HOME -> {
											MainScreen(
												onStartMeasure = { mode ->
													activeMeasureType = mode
													viewModel.selectMode(mode)
													isMeasuring = true
												},
												onOpenSlideDialog = {
													Toast.makeText(this@MainActivity, "슬라이드 룩북", Toast.LENGTH_SHORT).show()
												},
												modifier = Modifier.fillMaxSize()
											)
										}
										MainNavTab.HISTORY -> {
											MeasureHistoryScreen(
												historyList = uiState.historyList,
												onUserClick = { userItem ->
													activeMeasureType = userItem.measureType
													isViewingDetailResult = true
												},
												modifier = Modifier.fillMaxSize()
											)
										}
										MainNavTab.SETTINGS -> {
											SettingsScreen(
												onOptionClick = { option ->
													Toast.makeText(this@MainActivity, option, Toast.LENGTH_SHORT).show()
												},
												onRestoreSuccess = {
													viewModel.loadHistory()
													Toast.makeText(this@MainActivity, "측정 데이터가 성공적으로 갱신되었습니다.", Toast.LENGTH_SHORT).show()
												},
												modifier = Modifier.fillMaxSize()
											)
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	override fun onWindowFocusChanged(hasFocus: Boolean) {
		super.onWindowFocusChanged(hasFocus)
		if (hasFocus) {
			hideSystemUI()
		}
	}

	override fun onResume() {
		super.onResume()
		hideSystemUI()
		checkAndRequestUsbPermission()
	}

	/**
	 * 태블릿 전체화면 몰입 모드 (상태 표시줄 및 하단 내비게이션 바 숨김)
	 */
	private fun hideSystemUI() {
		WindowCompat.setDecorFitsSystemWindows(window, false)
		val controller = WindowCompat.getInsetsController(window, window.decorView)
		controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		controller.hide(WindowInsetsCompat.Type.systemBars())
	}

	/**
	 * 안드로이드 시스템 USB 권한 대화상자 명시적 요청
	 */
	private fun checkAndRequestUsbPermission() {
		try {
			val usbManager = getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
			val orbbecDevice = usbManager.deviceList.values.find {
				it.vendorId == 0x2bc5 || it.vendorId == 11205 ||
					it.productName?.contains("Gemini", ignoreCase = true) == true ||
					it.productName?.contains("Orbbec", ignoreCase = true) == true
			}
			if (orbbecDevice != null && !usbManager.hasPermission(orbbecDevice)) {
				val permissionIntent = PendingIntent.getBroadcast(
					this,
					0,
					Intent("com.tangoplus.tangoface_compose.USB_PERMISSION").setPackage(packageName),
					PendingIntent.FLAG_MUTABLE
				)
				usbManager.requestPermission(orbbecDevice, permissionIntent)
			}
		} catch (e: Exception) {
			android.util.Log.e("MainActivity", "checkAndRequestUsbPermission error", e)
		}
	}
}
