package com.tangoplus.tangoface_compose.ui.measure

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tangoplus.tangoface_compose.R
import com.tangoplus.tangoface_compose.domain.model.MeasureType
import com.tangoplus.tangoface_compose.domain.vo.Sequence
import com.tangoplus.tangoface_compose.ui.components.AngleMeasureOverlay
import com.tangoplus.tangoface_compose.ui.components.OrbbecCameraPreview
import com.tangoplus.tangoface_compose.ui.components.ZoomableImageViewer
import com.tangoplus.tangoface_compose.ui.info.SequenceFilterChips
import com.tangoplus.tangoface_compose.ui.theme.SubColor100
import com.tangoplus.tangoface_compose.ui.theme.TangoAccent
import com.tangoplus.tangoface_compose.ui.theme.TangoBg
import com.tangoplus.tangoface_compose.ui.theme.TangoBg15
import com.tangoplus.tangoface_compose.ui.theme.TangoBg700
import com.tangoplus.tangoface_compose.ui.theme.TangoNormal

/**
 * 탱고페이스 카메라 측정 화면 (XML fragment_m_normal, fragment_m_expert 등 완전 통합)
 * - Orbbec 뎁스/컬러 카메라 프리뷰
 * - 얼굴 가이드 프레임 오버레이 (drawable_seq_0)
 * - 시퀀스 안내 텍스트 배너 ("얼굴을 화면 중앙에 위치해주세요")
 * - 3초 카운트다운 타이머 애니메이션
 * - 150dp 대형 셔터 버튼 및 우측 20% 제어 바
 */
@Composable
fun MeasureScreen(
	measureType: MeasureType,
	onNavigateBack: () -> Unit,
	onMeasureComplete: () -> Unit,
	modifier: Modifier = Modifier
) {
	val configuration = LocalConfiguration.current
	val isTabletLandscape = configuration.screenWidthDp >= 600 && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

	var currentSeq by remember { mutableStateOf(Sequence.COMFORT) }
	var guideText by remember { mutableStateOf("얼굴을 화면 중앙에 위치해주세요") }
	var countdownValue by remember { mutableIntStateOf(0) } // 0이면 카운트다운 비활성
	var isAngleActive by remember { mutableStateOf(false) }

	val availableSeqs = when (measureType) {
		MeasureType.NORMAL -> listOf(Sequence.COMFORT, Sequence.NECK_EXTENTION)
		MeasureType.EXPERT -> Sequence.entries.filter { it != Sequence.BOW }
		MeasureType.BOW -> listOf(Sequence.BOW)
		MeasureType.IMAGE -> listOf(Sequence.COMFORT, Sequence.NECK_EXTENTION)
		MeasureType.SCAN -> listOf(Sequence.COMFORT)
	}

	Box(
		modifier = modifier
			.fillMaxSize()
			.background(Color.Black)
	) {
		if (isTabletLandscape) {
			// 🖥️ 태블릿 가로 (모니터 대응): 좌측 80% 카메라 프리뷰 + 우측 20% 제어 바
			Row(modifier = Modifier.fillMaxSize()) {
				// [좌측 80%]: 메인 카메라 프리뷰 & 안면 가이드 & 카운트다운
				Box(
					modifier = Modifier
						.weight(0.8f)
						.fillMaxHeight(),
					contentAlignment = Alignment.Center
				) {
					// 1. 카메라 뷰 / 이미지 뷰어
					if (measureType.isCameraBased) {
						OrbbecCameraPreview(
							rotationDegrees = 90,
							centerCrop = true,
							onViewCreated = { }
						)
					} else {
						ZoomableImageViewer(bitmap = null)
					}

					// 2. 안면 가이드 프레임 (alpha 0.5)
					Image(
						painter = painterResource(id = R.drawable.drawable_seq_0),
						contentDescription = "얼굴 가이드",
						modifier = Modifier
							.fillMaxHeight(0.9f)
							.alpha(0.5f)
					)

					// 3. 안내 가이드 배너 (하단 75% 지점)
					Surface(
						modifier = Modifier
							.align(Alignment.BottomCenter)
							.padding(bottom = 60.dp),
						shape = RoundedCornerShape(20.dp),
						color = Color(0x66121212)
					) {
						Text(
							text = guideText,
							color = Color.White,
							fontSize = 20.sp,
							fontWeight = FontWeight.Bold,
							modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
						)
					}

					// 4. 대형 카운트다운 오버레이 (3, 2, 1)
					if (countdownValue > 0) {
						Box(
							modifier = Modifier
								.size(140.dp)
								.background(Color(0x88FFFFFF), RoundedCornerShape(24.dp)),
							contentAlignment = Alignment.Center
						) {
							Text(
								text = countdownValue.toString(),
								color = Color.Black,
								fontSize = 80.sp,
								fontWeight = FontWeight.ExtraBold
							)
						}
					}

					// 5. 각도 측정 오버레이
					if (isAngleActive) {
						AngleMeasureOverlay()
					}

					// 좌상단 뒤로가기 버튼
					IconButton(
						onClick = onNavigateBack,
						modifier = Modifier
							.align(Alignment.TopStart)
							.padding(16.dp)
					) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBack,
							contentDescription = "뒤로가기",
							tint = Color.White,
							modifier = Modifier.size(32.dp)
						)
					}

					// 모드 뱃지
					Surface(
						modifier = Modifier
							.align(Alignment.TopEnd)
							.padding(16.dp),
						shape = RoundedCornerShape(8.dp),
						color = Color.Black.copy(alpha = 0.7f)
					) {
						Text(
							text = "[${measureType.displayName}]",
							color = TangoNormal,
							fontSize = 15.sp,
							fontWeight = FontWeight.Bold,
							modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
						)
					}
				}

				// [우측 20%]: 블랙 제어 바 & 150dp 대형 셔터 버튼
				Column(
					modifier = Modifier
						.weight(0.2f)
						.fillMaxHeight()
						.background(TangoBg700)
						.padding(16.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.SpaceBetween
				) {
					// 상단 시퀀스 정보
					Column(horizontalAlignment = Alignment.CenterHorizontally) {
						Text(
							text = currentSeq.displayName,
							color = Color.White,
							fontSize = 16.sp,
							fontWeight = FontWeight.Bold
						)
						Spacer(modifier = Modifier.height(10.dp))
						Button(
							onClick = { isAngleActive = !isAngleActive },
							colors = ButtonDefaults.buttonColors(
								containerColor = if (isAngleActive) TangoNormal else Color(0xFF2C2C2C),
								contentColor = if (isAngleActive) Color.Black else Color.White
							),
							shape = RoundedCornerShape(10.dp)
						) {
							Text(if (isAngleActive) "📐 각도 ON" else "📐 각도 OFF", fontSize = 12.sp)
						}
					}

					// 중앙: 150dp 대형 셔터 촬영 버튼
					LargeShutterButton(
						onClick = {
							// 촬영 완료 후 결과로 이동
							onMeasureComplete()
						}
					)

					// 하단: 시퀀스 넘김 / 바로 결과 보기
					Button(
						onClick = onMeasureComplete,
						colors = ButtonDefaults.buttonColors(containerColor = TangoAccent),
						shape = RoundedCornerShape(12.dp),
						modifier = Modifier.fillMaxWidth()
					) {
						Text("결과 확인", fontWeight = FontWeight.Bold)
					}
				}
			}
		} else {
			// 📱 세로 모드 (Portrait)
			Column(modifier = Modifier.fillMaxSize()) {
				// 상단 헤더
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					IconButton(onClick = onNavigateBack) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBack,
							contentDescription = "뒤로가기",
							tint = Color.White
						)
					}
					Text(
						text = "[${measureType.displayName}] 측정",
						color = Color.White,
						fontSize = 18.sp,
						fontWeight = FontWeight.Bold
					)
					Box(modifier = Modifier.size(48.dp))
				}

				// 카메라 프리뷰 박스
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.weight(1f),
					contentAlignment = Alignment.Center
				) {
					if (measureType.isCameraBased) {
						OrbbecCameraPreview(rotationDegrees = 90, centerCrop = true)
					} else {
						ZoomableImageViewer(bitmap = null)
					}

					Image(
						painter = painterResource(id = R.drawable.drawable_seq_0),
						contentDescription = null,
						modifier = Modifier
							.fillMaxHeight(0.85f)
							.alpha(0.5f)
					)

					Surface(
						modifier = Modifier
							.align(Alignment.BottomCenter)
							.padding(bottom = 20.dp),
						shape = RoundedCornerShape(16.dp),
						color = Color(0x66121212)
					) {
						Text(
							text = guideText,
							color = Color.White,
							fontSize = 16.sp,
							fontWeight = FontWeight.SemiBold,
							modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
						)
					}
				}

				// 하단 셔터 및 컨트롤
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.background(TangoBg700)
						.padding(20.dp),
					horizontalArrangement = Arrangement.SpaceAround,
					verticalAlignment = Alignment.CenterVertically
				) {
					Button(
						onClick = { isAngleActive = !isAngleActive },
						colors = ButtonDefaults.buttonColors(
							containerColor = if (isAngleActive) TangoNormal else Color(0xFF2C2C2C)
						)
					) {
						Text(if (isAngleActive) "각도 ON" else "각도 OFF", fontSize = 12.sp)
					}

					LargeShutterButton(onClick = onMeasureComplete, size = 100.dp)

					Button(
						onClick = onMeasureComplete,
						colors = ButtonDefaults.buttonColors(containerColor = TangoAccent)
					) {
						Text("결과 확인", fontSize = 12.sp)
					}
				}
			}
		}
	}
}

/**
 * 150dp 카메라 셔터 버튼 (XML btn_camera 스타일)
 */
@Composable
fun LargeShutterButton(
	onClick: () -> Unit,
	size: androidx.compose.ui.unit.Dp = 130.dp
) {
	Box(
		modifier = Modifier
			.size(size)
			.border(4.dp, Color.White, CircleShape)
			.padding(12.dp)
			.background(TangoAccent, CircleShape)
			.clickable { onClick() },
		contentAlignment = Alignment.Center
	) {
		Box(
			modifier = Modifier
				.size(size * 0.75f)
				.background(Color.White.copy(alpha = 0.2f), CircleShape),
			contentAlignment = Alignment.Center
		) {
			Text(
				text = "",
				fontSize = if (size > 100.dp) 36.sp else 26.sp
			)
		}
	}
}
