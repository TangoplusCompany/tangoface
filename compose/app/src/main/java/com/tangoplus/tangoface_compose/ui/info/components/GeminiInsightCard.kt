package com.tangoplus.tangoface_compose.ui.info.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tangoplus.tangoface_compose.ui.theme.SubColor100
import com.tangoplus.tangoface_compose.ui.theme.SubColor500
import com.tangoplus.tangoface_compose.ui.theme.TangoAccent
import com.tangoplus.tangoface_compose.ui.theme.TangoBg300
import com.tangoplus.tangoface_compose.ui.theme.TangoBg500
import com.tangoplus.tangoface_compose.ui.theme.TangoBg700

/**
 * Gemini AI 안면 분석 리포트 카드
 */
@Composable
fun GeminiInsightCard(
	insightText: String?,
	isLoading: Boolean = false,
	onRequestInsight: () -> Unit = {},
	modifier: Modifier = Modifier
) {
	var isExpanded by remember { mutableStateOf(false) }

	val gradientBrush = Brush.linearGradient(
		colors = listOf(TangoBg300, TangoBg700)
	)

	Card(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(16.dp),
		colors = CardDefaults.cardColors(containerColor = Color.Transparent),
		elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
	) {
		Column(
			modifier = Modifier
				.background(gradientBrush)
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp)
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = "✨ Gemini AI 안면 분석 리포트",
					fontSize = 16.sp,
					fontWeight = FontWeight.Bold,
					color = Color(0xFFD4AF37)
				)

				if (insightText != null && insightText.isNotEmpty()) {
					Button(
						onClick = { isExpanded = !isExpanded },
						colors = ButtonDefaults.buttonColors(containerColor = TangoBg500),
						shape = RoundedCornerShape(8.dp)
					) {
						Text(text = if (isExpanded) "접기" else "자세히 보기", fontSize = 12.sp, color = SubColor100)
					}
				}
			}

			if (isLoading) {
				Row(
					modifier = Modifier.fillMaxWidth().padding(12.dp),
					horizontalArrangement = Arrangement.Center,
					verticalAlignment = Alignment.CenterVertically
				) {
					CircularProgressIndicator(color = Color(0xFFD4AF37), modifier = Modifier.height(24.dp))
					Spacer(modifier = Modifier.padding(horizontal = 8.dp))
					Text(text = "AI가 얼굴 비대칭 및 처짐을 정밀 분석 중입니다...", fontSize = 13.sp, color = SubColor500)
				}
			} else if (insightText.isNullOrEmpty()) {
				Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
					Text(
						text = "얼굴 중심축 편차와 윤곽 탄력을 바탕으로 한 AI 종합 진단서를 확인할 수 있습니다.",
						fontSize = 13.sp,
						color = SubColor500
					)
					Spacer(modifier = Modifier.height(10.dp))
					Button(
						onClick = onRequestInsight,
						colors = ButtonDefaults.buttonColors(containerColor = TangoAccent),
						shape = RoundedCornerShape(10.dp)
					) {
						Text(text = "💡 AI 분석 생성하기", fontSize = 13.sp, color = Color.White)
					}
				}
			} else {
				Text(
					text = if (isExpanded) insightText else insightText.take(150) + if (insightText.length > 150) "..." else "",
					fontSize = 14.sp,
					lineHeight = 20.sp,
					color = SubColor100.copy(alpha = 0.9f)
				)
			}
		}
	}
}

