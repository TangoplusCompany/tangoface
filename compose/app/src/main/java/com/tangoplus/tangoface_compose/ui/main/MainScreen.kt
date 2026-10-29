package com.tangoplus.tangoface_compose.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tangoplus.tangoface_compose.R
import com.tangoplus.tangoface_compose.domain.model.MeasureType
import com.tangoplus.tangoface_compose.ui.main.vm.MainViewModel
import com.tangoplus.tangoface_compose.ui.theme.SubColor100
import com.tangoplus.tangoface_compose.ui.theme.SubColor500
import com.tangoplus.tangoface_compose.ui.theme.TangoAccent
import com.tangoplus.tangoface_compose.ui.theme.TangoAccent100
import com.tangoplus.tangoface_compose.ui.theme.TangoBg
import com.tangoplus.tangoface_compose.ui.theme.TangoBg300
import com.tangoplus.tangoface_compose.ui.theme.TangoBg500
import com.tangoplus.tangoface_compose.ui.theme.TangoBg700
import com.tangoplus.tangoface_compose.ui.theme.TangoNormal
import com.tangoplus.tangoface_compose.ui.theme.TangoRed

/**
 * 탱고페이스 메인 화면 (XML fragment_main.xml & MainFragment.kt 100% 매핑)
 * - 상단 헤더: 로고 타이틀, USB Orbbec Gemini 336 연결 상태, 실시간 RAM 사용률, 슬라이드 버튼
 * - 가이드 안내 배너 (bg_gradation_sub_12 스타일 + 5초 주기 롤링 코멘트) -> 클릭 시 3D 스캔 시작
 * - 메인 2x2 카드 그리드 (rvM): 간편 검사, 정밀 검사, 사진 검사, 페이스 보우
 */
@Composable
fun MainScreen(
	onStartMeasure: (MeasureType) -> Unit,
	onOpenSlideDialog: () -> Unit = {},
	viewModel: MainViewModel = viewModel(),
	modifier: Modifier = Modifier
) {
	val context = LocalContext.current
	val isDepthConnected by viewModel.isDepthConnected.collectAsState()
	val ramUsage by viewModel.ramUsage.collectAsState()
	val currentGuide by viewModel.currentGuide.collectAsState()

	// 1. USB 실시간 연결 리시버 및 초기 상태 체크 (Orbbec Gemini 336)
	DisposableEffect(context) {
		val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
		viewModel.updateUsbDeviceList(usbManager.deviceList)

		val usbReceiver = object : BroadcastReceiver() {
			override fun onReceive(c: Context?, intent: Intent?) {
				when (intent?.action) {
					UsbManager.ACTION_USB_DEVICE_ATTACHED,
					UsbManager.ACTION_USB_DEVICE_DETACHED -> {
						val um = c?.getSystemService(Context.USB_SERVICE) as? UsbManager
						viewModel.updateUsbDeviceList(um?.deviceList)
					}
				}
			}
		}

		val filter = IntentFilter().apply {
			addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
			addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
		}
		context.registerReceiver(usbReceiver, filter)

		// 2. RAM 모니터링 및 가이드 코멘트 롤링 시작
		viewModel.startRamMonitoring(context.applicationContext)
		viewModel.startGuideRolling()

		onDispose {
			try {
				context.unregisterReceiver(usbReceiver)
			} catch (e: Exception) {
				// 이미 해제된 경우 무시
			}
		}
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(TangoBg)
	) {
		// 1. 최상단 64dp 헤더 바
		MainHeaderBar(
			isCameraConnected = isDepthConnected,
			ramUsage = ramUsage,
			onOpenSlide = onOpenSlideDialog
		)

		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 24.dp, vertical = 16.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			// 2. 안내 가이드 배너 (클릭 시 3D SCAN 모드 실행)
			MainNoticeBanner(
				guideText = currentGuide,
				onClick = { onStartMeasure(MeasureType.SCAN) }
			)

			// 3. 메인 2x2 카드 그리드 (간편 검사, 정밀 검사, 사진 검사, 페이스 보우)
			MainTabGrid(
				onTabSelected = onStartMeasure,
				modifier = Modifier.fillMaxSize()
			)
		}
	}
}

/**
 * 상단 헤더 바 (XML constraintLayout 높이 64dp, bg300)
 */
@Composable
fun MainHeaderBar(
	isCameraConnected: Boolean,
	ramUsage: Int,
	onOpenSlide: () -> Unit
) {
	Surface(
		modifier = Modifier
			.fillMaxWidth()
			.height(64.dp),
		color = TangoBg300,
		shadowElevation = 4.dp
	) {
		Row(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 20.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			// 좌측: 로고 (ic_logo_transparent_long)
			Row(verticalAlignment = Alignment.CenterVertically) {
				Image(
					painter = painterResource(id = R.drawable.ic_logo_transparent_long),
					contentDescription = "TangoFace Logo",
					modifier = Modifier.height(38.dp)
				)
			}

			// 우측: 카메라 연결 상태 뱃지, RAM 점유율 뱃지, 슬라이드 버튼
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(10.dp)
			) {
				// 카메라 연결 상태 뱃지 (Orbbec Gemini 336 연결 여부)
				Surface(
					shape = RoundedCornerShape(32.dp),
					color = Color(0x33121212),
					border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF))
				) {
					Row(
						modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Box(
							modifier = Modifier
								.size(10.dp)
								.background(
									if (isCameraConnected) TangoNormal else TangoRed,
									CircleShape
								)
						)
						Spacer(modifier = Modifier.width(8.dp))
						Text(
							text = if (isCameraConnected) "카메라 연결됨" else "카메라 연결 없음",
							fontSize = 13.sp,
							fontWeight = FontWeight.SemiBold,
							color = if (isCameraConnected) TangoNormal else TangoRed
						)
					}
				}

				// RAM 점유율 뱃지 (90% 이상 시 빨간색 경고)
				Surface(
					shape = RoundedCornerShape(32.dp),
					color = Color(0x33121212),
					border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF))
				) {
					Text(
						text = "📊 램 사용량: ${ramUsage}%",
						fontSize = 13.sp,
						fontWeight = FontWeight.SemiBold,
						color = if (ramUsage >= 90) TangoAccent else TangoAccent100,
						modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
					)
				}

				// 슬라이드 버튼 (Waiting Lookbook Dialog)
				IconButton(
					onClick = onOpenSlide,
					modifier = Modifier
						.size(40.dp)
						.background(TangoBg, CircleShape)
				) {
					Icon(
						painter = painterResource(id = R.drawable.ic_slide),
						contentDescription = "슬라이드 포스터",
						tint = SubColor100,
						modifier = Modifier.size(20.dp)
					)
				}
			}
		}
	}
}

/**
 * 안내 가이드 배너 (XML clMNotice, 5초 주기 슬라이드 애니메이션)
 */
@Composable
fun MainNoticeBanner(
	guideText: String,
	onClick: () -> Unit
) {
	val gradientBrush = Brush.horizontalGradient(
		colors = listOf(Color(0xFF331A24), Color(0xFF1C191A))
	)

	Card(
		modifier = Modifier
			.fillMaxWidth()
			.clickable { onClick() },
		shape = RoundedCornerShape(14.dp),
		colors = CardDefaults.cardColors(containerColor = Color.Transparent)
	) {
		Column(
			modifier = Modifier
				.background(gradientBrush)
				.border(1.dp, TangoAccent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
				.padding(horizontal = 20.dp, vertical = 18.dp)
		) {
			Text(
				text = "가이드",
				fontSize = 14.sp,
				fontWeight = FontWeight.Bold,
				color = SubColor500
			)
			Spacer(modifier = Modifier.height(6.dp))
			AnimatedContent(
				targetState = guideText,
				transitionSpec = {
					(slideInVertically { height -> height } + fadeIn()).togetherWith(
						slideOutVertically { height -> -height } + fadeOut()
					)
				},
				label = "GuideRollingAnimation"
			) { text ->
				Text(
					text = text,
					fontSize = 18.sp,
					fontWeight = FontWeight.SemiBold,
					color = TangoAccent100,
					lineHeight = 24.sp
				)
			}
		}
	}
}

/**
 * 2x2 메인 탭 카드 그리드 (XML rvM + MainTabRVAdapter)
 * 1. 간편 검사
 * 2. 정밀 검사
 * 3. 페이스 보우
 * 4. 사진 검사
 */
@Composable
fun MainTabGrid(
	onTabSelected: (MeasureType) -> Unit,
	modifier: Modifier = Modifier
) {
	val tabItems = listOf(
		TabItemData(
			measureType = MeasureType.NORMAL,
			title = "간편 검사",
			desc = "3D 카메라로 정면과 목 자세를 빠르고 간편하게 분석해요.\n⏱️ 약 40초 · 정면 ➔ 목폄 2단계",
			iconRes = R.drawable.ic_normal
		),
		TabItemData(
			measureType = MeasureType.EXPERT,
			title = "정밀 검사",
			desc = "3D 다각도 스캔으로 턱관절과 안면 밸런스를 종합 진단해요.\n⏱️ 약 1분 · 정면 ➔ 교합 ➔ 턱 가측(좌/우) 4단계",
			iconRes = R.drawable.ic_expert
		),
		TabItemData(
			measureType = MeasureType.BOW,
			title = "페이스 보우",
			desc = "전용 페이스 보우를 착용해 턱관절 수평축과 비대칭을 측정해요.\n⏱️ 약 30초 · 장비 착용 후 정면 스캔",
			iconRes = R.drawable.ic_bow
		),
		TabItemData(
			measureType = MeasureType.IMAGE,
			title = "사진 검사",
			desc = "기존 사진 1장으로 정면 얼굴 비율을 가볍게 확인해요.\n⏱️ 약 15초 · 사진 1장 업로드",
			iconRes = R.drawable.ic_image
		)
	)

	LazyVerticalGrid(
		columns = GridCells.Fixed(2),
		modifier = modifier,
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		items(tabItems.size) { index ->
			val item = tabItems[index]
			MainGlassTabCard(
				item = item,
				onClick = { onTabSelected(item.measureType) }
			)
		}
	}
}

data class TabItemData(
	val measureType: MeasureType,
	val title: String,
	val desc: String,
	val iconRes: Int
)

/**
 * 개별 글래스 카드 (XML rv_main_tab_item.xml 및 effect_glass_card)
 */
@Composable
fun MainGlassTabCard(
	item: TabItemData,
	onClick: () -> Unit
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.clickable { onClick() },
		shape = RoundedCornerShape(18.dp),
		colors = CardDefaults.cardColors(containerColor = TangoBg700),
		elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(22.dp)
		) {
			// 상단 80dp 아이콘 박스 (XML ivMTI)
			Box(
				modifier = Modifier
					.size(76.dp)
					.background(TangoBg500, RoundedCornerShape(12.dp))
					.border(1.dp, TangoAccent.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
				contentAlignment = Alignment.Center
			) {
				Icon(
					painter = painterResource(id = item.iconRes),
					contentDescription = item.title,
					tint = TangoAccent100,
					modifier = Modifier.size(36.dp)
				)
			}

			Spacer(modifier = Modifier.height(18.dp))

			// 타이틀 (XML tvMTITitle: 24sp, White)
			Text(
				text = item.title,
				fontSize = 24.sp,
				fontWeight = FontWeight.Bold,
				color = Color.White
			)

			Spacer(modifier = Modifier.height(10.dp))

			// 설명 (XML tvMTIDesc: 15sp, SubColor500, line spacing 6dp)
			Text(
				text = item.desc,
				fontSize = 14.sp,
				lineHeight = 22.sp,
				color = SubColor500
			)
		}
	}
}
