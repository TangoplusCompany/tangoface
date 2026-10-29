package com.tangoplus.tangoface_compose.ui.info

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import com.tangoplus.tangoface_compose.ui.theme.TangoNormal
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tangoplus.tangoface_compose.data.db.FaceShape
import com.tangoplus.tangoface_compose.data.db.FacialAxisShape
import com.tangoplus.tangoface_compose.domain.model.Factor
import com.tangoplus.tangoface_compose.domain.vo.FaceResult
import com.tangoplus.tangoface_compose.domain.vo.Sequence
import com.tangoplus.tangoface_compose.ui.info.components.FaceStaticDetailsTable
import com.tangoplus.tangoface_compose.ui.info.components.FactorSummaryCard
import com.tangoplus.tangoface_compose.ui.info.components.ResultImageViewCard
import com.tangoplus.tangoface_compose.ui.info.components.ResultToolButtons
import com.tangoplus.tangoface_compose.ui.info.vm.InformationViewModel
import com.tangoplus.tangoface_compose.ui.info.vm.SelectViewModel

/**
 * 페이스 보우 (BOW) 측정 결과 화면
 */
@Composable
fun IBowScreen(
	faceResult: FaceResult?,
	ivm: InformationViewModel,
	svm: SelectViewModel,
	modifier: Modifier = Modifier
) {
	val configuration = LocalConfiguration.current
	val isTabletLandscape = configuration.screenWidthDp >= 600 && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

	val isAngleActive by ivm.isAngleModeActive.collectAsState()

	var isMoireOn by remember { mutableStateOf(ivm.getMoireOn()) }
	var isLineOn by remember { mutableStateOf(ivm.getLineOn()) }
	var isGridOn by remember { mutableStateOf(ivm.getGridLineOn()) }

	val factor = faceResult?.factors ?: Factor(
		facialAxis = FacialAxisShape.DEFAULT,
		primaryFaceShape = FaceShape.DEFAULT,
		secondaryFaceShape = null,
		saggingScale = Pair(1.1f, 1.15f)
	)

	if (isTabletLandscape) {
		Row(
			modifier = modifier
				.fillMaxSize()
				.padding(16.dp),
			horizontalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Box(
				modifier = Modifier
					.weight(0.485f)
					.fillMaxHeight(),
				contentAlignment = Alignment.Center
			) {
				ResultImageViewCard(
					bitmap = null,
					isGuideLineOn = isLineOn,
					isAngleMeasureActive = isAngleActive,
					userName = faceResult?.userName,
					userMobile = faceResult?.userMobile,
					modifier = Modifier.fillMaxSize()
				)
			}

			Column(
				modifier = Modifier
					.weight(0.515f)
					.fillMaxHeight()
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				Text(
					text = "🏹 페이스 보우 (Face Bow) 수평 교합 분석",
					fontSize = 18.sp,
					fontWeight = FontWeight.Bold,
					color = TangoNormal
				)

				ResultToolButtons(
					isMoireOn = isMoireOn,
					isLineOn = isLineOn,
					isGridOn = isGridOn,
					isAngleOn = isAngleActive,
					onToggleMoire = {
						ivm.switchedMoireOn()
						isMoireOn = ivm.getMoireOn()
					},
					onToggleLine = {
						ivm.switchedLineOn()
						isLineOn = ivm.getLineOn()
					},
					onToggleGrid = {
						ivm.switchedGridLineOn()
						isGridOn = ivm.getGridLineOn()
					},
					onToggleAngle = {
						ivm.toggleAngleMode()
					}
				)

				FactorSummaryCard(factor = factor)

				FaceStaticDetailsTable(items = ivm.currentFaceStaticsItem)
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
					isGuideLineOn = isLineOn,
					isAngleMeasureActive = isAngleActive,
					userName = faceResult?.userName,
					userMobile = faceResult?.userMobile,
					modifier = Modifier.fillMaxSize()
				)
			}

			Text(
				text = "🏹 페이스 보우 (Face Bow) 수평 교합 분석",
				fontSize = 18.sp,
				fontWeight = FontWeight.Bold,
				color = Color(0xFFB0FF00)
			)

			ResultToolButtons(
				isMoireOn = isMoireOn,
				isLineOn = isLineOn,
				isGridOn = isGridOn,
				isAngleOn = isAngleActive,
				onToggleMoire = {
					ivm.switchedMoireOn()
					isMoireOn = ivm.getMoireOn()
				},
				onToggleLine = {
					ivm.switchedLineOn()
					isLineOn = ivm.getLineOn()
				},
				onToggleGrid = {
					ivm.switchedGridLineOn()
					isGridOn = ivm.getGridLineOn()
				},
				onToggleAngle = {
					ivm.toggleAngleMode()
				}
			)

			FactorSummaryCard(factor = factor)

			FaceStaticDetailsTable(items = ivm.currentFaceStaticsItem)
		}
	}
}

