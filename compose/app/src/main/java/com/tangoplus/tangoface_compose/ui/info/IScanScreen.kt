package com.tangoplus.tangoface_compose.ui.info

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tangoplus.tangoface_compose.domain.vo.FaceResult
import com.tangoplus.tangoface_compose.ui.info.components.ResultImageViewCard
import com.tangoplus.tangoface_compose.ui.info.vm.InformationViewModel
import com.tangoplus.tangoface_compose.ui.info.vm.SelectViewModel
import com.tangoplus.tangoface_compose.ui.theme.SubColor100
import com.tangoplus.tangoface_compose.ui.theme.TangoBg700
import com.tangoplus.tangoface_compose.ui.theme.TangoNormal

/**
 * 3D 스캔 (SCAN) 결과 및 포인트 클라우드 / 뎁스 뷰어 화면
 */
@Composable
fun IScanScreen(
	faceResult: FaceResult?,
	ivm: InformationViewModel,
	svm: SelectViewModel,
	modifier: Modifier = Modifier
) {
	val configuration = LocalConfiguration.current
	val isTabletLandscape = configuration.screenWidthDp >= 600 && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

	if (isTabletLandscape) {
		Row(
			modifier = modifier
				.fillMaxSize()
				.padding(16.dp),
			horizontalArrangement = Arrangement.spacedBy(16.dp)
		) {
			// 좌측: 뎁스/3D 스캔 결과 뷰어
			Box(
				modifier = Modifier
					.weight(0.485f)
					.fillMaxHeight(),
				contentAlignment = Alignment.Center
			) {
				ResultImageViewCard(
					bitmap = null,
					userName = faceResult?.userName,
					userMobile = faceResult?.userMobile,
					modifier = Modifier.fillMaxSize()
				)
			}

			// 우측: 3D 포인트 클라우드 및 뎁스 메트릭 정보
			Column(
				modifier = Modifier
					.weight(0.515f)
					.fillMaxHeight()
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(14.dp)
			) {
				Text(
					text = "🧊 3D 안면 정밀 스캔 결과",
					fontSize = 18.sp,
					fontWeight = FontWeight.Bold,
					color = TangoNormal
				)

				Card(
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(16.dp),
					colors = CardDefaults.cardColors(containerColor = TangoBg700)
				) {
					Column(modifier = Modifier.padding(16.dp)) {
						Text(text = "Orbbec 3D Depth 센서 데이터", fontSize = 14.sp, color = SubColor100, fontWeight = FontWeight.Bold)
						Spacer(modifier = Modifier.height(8.dp))
						Text(text = "• 깊이 해상도: 640 x 400 (WFOV)", fontSize = 13.sp, color = Color.LightGray)
						Text(text = "• 안면 입체 굴곡 편차(Z-Depth): ±0.8mm 정밀도", fontSize = 13.sp, color = Color.LightGray)
						Text(text = "• 3D 포인트 클라우드 좌표 정합 완료", fontSize = 13.sp, color = Color.LightGray)
					}
				}
			}
		}
	} else {
		Column(
			modifier = modifier
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.aspectRatio(3f / 4f)
			) {
				ResultImageViewCard(
					bitmap = null,
					userName = faceResult?.userName,
					userMobile = faceResult?.userMobile,
					modifier = Modifier.fillMaxSize()
				)
			}

			Text(
				text = "🧊 3D 안면 정밀 스캔 결과",
				fontSize = 18.sp,
				fontWeight = FontWeight.Bold,
				color = TangoNormal
			)

			Card(
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(16.dp),
				colors = CardDefaults.cardColors(containerColor = TangoBg700)
			) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(text = "Orbbec 3D Depth 센서 데이터", fontSize = 14.sp, color = SubColor100, fontWeight = FontWeight.Bold)
					Spacer(modifier = Modifier.height(8.dp))
					Text(text = "• 깊이 해상도: 640 x 400 (WFOV)", fontSize = 13.sp, color = Color.LightGray)
					Text(text = "• 안면 입체 굴곡 편차(Z-Depth): ±0.8mm 정밀도", fontSize = 13.sp, color = Color.LightGray)
					Text(text = "• 3D 포인트 클라우드 좌표 정합 완료", fontSize = 13.sp, color = Color.LightGray)
				}
			}
		}
	}
}

