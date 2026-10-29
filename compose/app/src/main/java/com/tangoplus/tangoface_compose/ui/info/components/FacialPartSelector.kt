package com.tangoplus.tangoface_compose.ui.info.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tangoplus.tangoface_compose.domain.model.FacialPart
import com.tangoplus.tangoface_compose.ui.theme.SubColor100
import com.tangoplus.tangoface_compose.ui.theme.TangoBg500
import com.tangoplus.tangoface_compose.ui.theme.TangoNormal

/**
 * 부위별 분석 필터 칩 목록 (얼굴분석, 상안부, 중안부, 하안부, 얼굴비율)
 */
@Composable
fun FacialPartSelector(
	selectedPart: FacialPart,
	onPartSelected: (FacialPart) -> Unit,
	modifier: Modifier = Modifier
) {
	val activeGreen = TangoNormal

	Row(
		modifier = modifier
			.fillMaxWidth()
			.horizontalScroll(rememberScrollState()),
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		FacialPart.entries.forEach { part ->
			val isSelected = (part == selectedPart)
			FilterChip(
				selected = isSelected,
				onClick = { onPartSelected(part) },
				shape = RoundedCornerShape(12.dp),
				label = {
					Text(
						text = part.displayName,
						fontSize = 14.sp,
						fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
					)
				},
				colors = FilterChipDefaults.filterChipColors(
					selectedContainerColor = activeGreen,
					selectedLabelColor = Color.Black,
					containerColor = TangoBg500,
					labelColor = SubColor100
				)
			)
		}
	}
}

