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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tangoplus.tangoface_compose.domain.vo.FaceStaticItem
import com.tangoplus.tangoface_compose.ui.theme.SubColor100
import com.tangoplus.tangoface_compose.ui.theme.SubColor300
import com.tangoplus.tangoface_compose.ui.theme.TangoBg500
import com.tangoplus.tangoface_compose.ui.theme.TangoBg700
import com.tangoplus.tangoface_compose.ui.theme.TangoNormal

/**
 * 부위별 세부 측정 수치 테이블 컴포넌트
 */
@Composable
fun FaceStaticDetailsTable(
	items: List<FaceStaticItem>,
	modifier: Modifier = Modifier
) {
	if (items.isEmpty()) return

	Card(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(16.dp),
		colors = CardDefaults.cardColors(containerColor = TangoBg700),
		elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp)
		) {
			Text(
				text = "상세 수치 데이터",
				fontSize = 15.sp,
				fontWeight = FontWeight.Bold,
				color = SubColor100
			)

			items.forEachIndexed { index, item ->
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(vertical = 4.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = item.label,
						fontSize = 14.sp,
						color = SubColor300
					)

					val valueStr = when {
						item.value00 != null && item.value01 != null ->
							"L: ${String.format("%.1f", item.value00)} / R: ${String.format("%.1f", item.value01)}"
						item.value00 != null ->
							String.format("%.1f", item.value00)
						else -> "-"
					}

					Text(
						text = valueStr,
						fontSize = 14.sp,
						fontWeight = FontWeight.SemiBold,
						color = TangoNormal
					)
				}
				if (index < items.size - 1) {
					HorizontalDivider(color = TangoBg500, thickness = 0.5.dp)
				}
			}
		}
	}
}
