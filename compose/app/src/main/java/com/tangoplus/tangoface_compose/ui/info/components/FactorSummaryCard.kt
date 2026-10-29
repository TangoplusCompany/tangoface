package com.tangoplus.tangoface_compose.ui.info.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tangoplus.tangoface_compose.data.db.FaceShape
import com.tangoplus.tangoface_compose.domain.model.Factor
import com.tangoplus.tangoface_compose.ui.theme.SubColor100
import com.tangoplus.tangoface_compose.ui.theme.SubColor500
import com.tangoplus.tangoface_compose.ui.theme.TangoBg500
import com.tangoplus.tangoface_compose.ui.theme.TangoBg700
import com.tangoplus.tangoface_compose.ui.theme.TangoNormal
import com.tangoplus.tangoface_compose.ui.theme.TangoOrange
import com.tangoplus.tangoface_compose.ui.theme.TangoRed

/**
 * 안면 분석 주요 지표 요약 카드
 * - 비대칭 지수 (미간-코-턱 중심축)
 * - 피부처짐 지수 (좌/우 탄력도)
 * - 도출된 얼굴형 (1순위 / 2순위)
 */
@Composable
fun FactorSummaryCard(
	factor: Factor,
	modifier: Modifier = Modifier
) {
	val activeGreen = TangoNormal
	val cardBg = TangoBg700

	Card(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(16.dp),
		colors = CardDefaults.cardColors(containerColor = cardBg),
		elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			// 1. 비대칭 지수
			Column {
				Text(
					text = "비대칭 지수 (안면 중심축)",
					fontSize = 13.sp,
					color = SubColor500
				)
				Spacer(modifier = Modifier.height(4.dp))
				Text(
					text = factor.facialAxis.displayName,
					fontSize = 18.sp,
					fontWeight = FontWeight.Bold,
					color = activeGreen
				)
			}

			// 2. 피부처짐 지수
			Column {
				Text(
					text = "피부 처짐 지수",
					fontSize = 13.sp,
					color = SubColor500
				)
				Spacer(modifier = Modifier.height(6.dp))
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(12.dp)
				) {
					SaggingItem(
						label = "왼쪽",
						score = factor.saggingScale.first,
						modifier = Modifier.weight(1f)
					)
					SaggingItem(
						label = "오른쪽",
						score = factor.saggingScale.second,
						modifier = Modifier.weight(1f)
					)
				}
			}

			// 3. 얼굴형 판별
			Column {
				Text(
					text = "얼굴형 분석",
					fontSize = 13.sp,
					color = SubColor500
				)
				Spacer(modifier = Modifier.height(4.dp))
				val primaryShapeStr = "${getEmoji(factor.primaryFaceShape)} ${factor.primaryFaceShape.displayName}"
				Text(
					text = "1순위: $primaryShapeStr",
					fontSize = 16.sp,
					fontWeight = FontWeight.SemiBold,
					color = SubColor100
				)
				factor.secondaryFaceShape?.let { secondary ->
					if (secondary != FaceShape.DEFAULT) {
						val secondaryShapeStr = "${getEmoji(secondary)} ${secondary.displayName}"
						Text(
							text = "2순위: $secondaryShapeStr",
							fontSize = 14.sp,
							color = SubColor500
						)
					}
				}
			}
		}
	}
}

@Composable
private fun SaggingItem(
	label: String,
	score: Float,
	modifier: Modifier = Modifier
) {
	val stateText = when (score) {
		in 1f..1.25f -> "매우 우수"
		in 1.25f..1.4f -> "보통"
		in 1.41f..1.6f -> "탄력 저하"
		else -> "심한 처짐"
	}
	val stateColor = when (score) {
		in 1f..1.25f -> TangoNormal
		in 1.25f..1.4f -> Color(0xFF00E5FF)
		in 1.41f..1.6f -> TangoOrange
		else -> TangoRed
	}

	Column(
		modifier = modifier
			.background(TangoBg500, RoundedCornerShape(10.dp))
			.padding(10.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Text(text = label, fontSize = 12.sp, color = SubColor500)
		Spacer(modifier = Modifier.height(2.dp))
		Text(
			text = stateText,
			fontSize = 14.sp,
			fontWeight = FontWeight.Bold,
			color = stateColor
		)
		Text(
			text = String.format("%.2f", score),
			fontSize = 12.sp,
			color = SubColor100
		)
	}
}

private fun getEmoji(faceShape: FaceShape): String {
	return when (faceShape) {
		FaceShape.EGG -> "🥚"
		FaceShape.RHOMBUS -> "🔶"
		FaceShape.HEART -> "♥️"
		FaceShape.PEANUT -> "🥜"
		FaceShape.HEXAGON -> "🛡️"
		FaceShape.CIRCLE -> "🟢"
		FaceShape.DEFAULT -> "👤"
	}
}

