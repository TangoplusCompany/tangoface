package com.tangoplus.tangoface_compose.ui.info.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tangoplus.tangoface_compose.ui.theme.SubColor100
import com.tangoplus.tangoface_compose.ui.theme.TangoBg500
import com.tangoplus.tangoface_compose.ui.theme.TangoBg700
import com.tangoplus.tangoface_compose.ui.theme.TangoNormal

/**
 * 측정 결과 화면 툴 제어 버튼 그룹
 * - Moire 모아레 on/off
 * - 가이드라인 on/off
 * - 그리드 격자 on/off
 * - 각도계산 측정 on/off
 */
@Composable
fun ResultToolButtons(
	isMoireOn: Boolean,
	isLineOn: Boolean,
	isGridOn: Boolean,
	isAngleOn: Boolean,
	onToggleMoire: () -> Unit,
	onToggleLine: () -> Unit,
	onToggleGrid: () -> Unit,
	onToggleAngle: () -> Unit,
	modifier: Modifier = Modifier
) {
	val activeGreen = TangoNormal
	val darkSurface = TangoBg700

	Column(
		modifier = modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			// 1. Moire 버튼
			Button(
				onClick = onToggleMoire,
				modifier = Modifier.weight(1f),
				shape = RoundedCornerShape(12.dp),
				colors = ButtonDefaults.buttonColors(
					containerColor = if (isMoireOn) activeGreen else darkSurface,
					contentColor = if (isMoireOn) Color.Black else SubColor100
				)
			) {
				Text(
					text = if (isMoireOn) "🏳️‍🌈 Moire 끄기" else "🏳️‍🌈 Moire 보기",
					fontSize = 14.sp,
					fontWeight = FontWeight.SemiBold
				)
			}

			// 2. 가이드라인 버튼
			Button(
				onClick = onToggleLine,
				modifier = Modifier.weight(1f),
				shape = RoundedCornerShape(12.dp),
				colors = ButtonDefaults.buttonColors(
					containerColor = if (isLineOn) activeGreen else darkSurface,
					contentColor = if (isLineOn) Color.Black else SubColor100
				)
			) {
				Text(
					text = if (isLineOn) "🔍 가이드라인 끄기" else "🔍 가이드라인 켜기",
					fontSize = 14.sp,
					fontWeight = FontWeight.SemiBold
				)
			}
		}

		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			// 3. 그리드 버튼
			Button(
				onClick = onToggleGrid,
				modifier = Modifier.weight(1f),
				shape = RoundedCornerShape(12.dp),
				colors = ButtonDefaults.buttonColors(
					containerColor = if (isGridOn) activeGreen else darkSurface,
					contentColor = if (isGridOn) Color.Black else SubColor100
				)
			) {
				Text(
					text = if (isGridOn) "ꖛ 그리드 끄기" else "ꖛ 그리드 켜기",
					fontSize = 14.sp,
					fontWeight = FontWeight.SemiBold
				)
			}

			// 4. 각도계산 버튼
			Button(
				onClick = onToggleAngle,
				modifier = Modifier.weight(1f),
				shape = RoundedCornerShape(12.dp),
				colors = ButtonDefaults.buttonColors(
					containerColor = if (isAngleOn) activeGreen else darkSurface,
					contentColor = if (isAngleOn) Color.Black else SubColor100
				)
			) {
				Text(
					text = if (isAngleOn) "📐 각도계산 끄기" else "📐 각도계산 켜기",
					fontSize = 14.sp,
					fontWeight = FontWeight.SemiBold
				)
			}
		}
	}
}

