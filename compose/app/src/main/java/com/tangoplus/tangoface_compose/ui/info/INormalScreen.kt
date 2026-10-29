package com.tangoplus.tangoface_compose.ui.info

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tangoplus.tangoface_compose.data.db.FaceShape
import com.tangoplus.tangoface_compose.data.db.FacialAxisShape
import com.tangoplus.tangoface_compose.domain.model.FacialPart
import com.tangoplus.tangoface_compose.domain.model.Factor
import com.tangoplus.tangoface_compose.domain.vo.FaceResult
import com.tangoplus.tangoface_compose.domain.vo.Sequence
import com.tangoplus.tangoface_compose.ui.info.components.FaceStaticDetailsTable
import com.tangoplus.tangoface_compose.ui.info.components.FacialPartSelector
import com.tangoplus.tangoface_compose.ui.info.components.FactorSummaryCard
import com.tangoplus.tangoface_compose.ui.info.components.GeminiInsightCard
import com.tangoplus.tangoface_compose.ui.info.components.ResultImageViewCard
import com.tangoplus.tangoface_compose.ui.info.components.ResultToolButtons
import com.tangoplus.tangoface_compose.ui.info.vm.InformationViewModel
import com.tangoplus.tangoface_compose.ui.info.vm.SelectViewModel

import com.tangoplus.tangoface_compose.ui.theme.SubColor100
import com.tangoplus.tangoface_compose.ui.theme.TangoBg500
import com.tangoplus.tangoface_compose.ui.theme.TangoNormal

/**
 * 간편 검사 (NORMAL) 결과 화면
 * - 태블릿 가로(sw600dp-land) 및 모니터 연결 시 2-Pane 좌우 분할
 * - 모바일 또는 세로(Portrait) 시 수직 1열 스크롤 레이아웃
 */
@Composable
fun INormalScreen(
	faceResult: FaceResult?,
	ivm: InformationViewModel,
	svm: SelectViewModel,
	modifier: Modifier = Modifier
) {
	val configuration = LocalConfiguration.current
	val isTabletLandscape = configuration.screenWidthDp >= 600 && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

	val currentSeq by svm.currentSequence.collectAsState()
	val currentPart by svm.currentFacialPart.collectAsState()
	val isAngleActive by ivm.isAngleModeActive.collectAsState()

	var isMoireOn by remember { mutableStateOf(ivm.getMoireOn()) }
	var isLineOn by remember { mutableStateOf(ivm.getLineOn()) }
	var isGridOn by remember { mutableStateOf(ivm.getGridLineOn()) }

	// 기본 팩터 (없을 경우 디폴트 제공)
	val factor = faceResult?.factors ?: Factor(
		facialAxis = FacialAxisShape.DEFAULT,
		primaryFaceShape = FaceShape.DEFAULT,
		secondaryFaceShape = null,
		saggingScale = Pair(1.2f, 1.3f)
	)

	val normalSeqs = listOf(Sequence.COMFORT, Sequence.NECK_EXTENTION)

	if (isTabletLandscape) {
		// 🖥️ 태블릿 가로 / 외부 모니터 연결 화면 (Two-Pane 좌우 2열 분할)
		Row(
			modifier = modifier
				.fillMaxSize()
				.padding(16.dp),
			horizontalArrangement = Arrangement.spacedBy(16.dp)
		) {
			// [좌측 열]: 3:4 안면 이미지 및 오버레이 (카드 너비 약 48%)
			Box(
				modifier = Modifier
					.weight(0.485f)
					.fillMaxHeight(),
				contentAlignment = Alignment.Center
			) {
				ResultImageViewCard(
					bitmap = null, // 비트맵 제공 시 렌더링
					isGuideLineOn = isLineOn,
					isAngleMeasureActive = isAngleActive,
					userName = faceResult?.userName,
					userMobile = faceResult?.userMobile,
					modifier = Modifier.fillMaxSize()
				)
			}

			// [우측 열]: 시퀀스 선택, 도구 버튼, 부위별 탭, 분석 지표 및 AI 인사이트
			Column(
				modifier = Modifier
					.weight(0.515f)
					.fillMaxHeight()
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				// 1. 시퀀스 선택 (정면 안정위, 목 신전)
				SequenceFilterChips(
					sequences = normalSeqs,
					selectedSeq = currentSeq,
					onSequenceSelected = {
						svm.updateSequence(it)
						ivm.setSeq(it)
					}
				)

				// 2. 도구 제어 버튼 (Moire, Guide, Grid, Angle)
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

				// 3. 얼굴 부위 선택 (얼굴분석, 상안부, 중안부, 하안부, 얼굴비율)
				FacialPartSelector(
					selectedPart = currentPart,
					onPartSelected = { svm.updateFacialPart(it) }
				)

				// 4. 주요 분석 지표 요약 카드 (비대칭, 피부처짐, 얼굴형)
				FactorSummaryCard(factor = factor)

				// 5. 부위별 세부 수치 (FaceStaticItem)
				FaceStaticDetailsTable(items = ivm.currentFaceStaticsItem)

				// 6. Gemini AI 리포트
				GeminiInsightCard(
					insightText = faceResult?.insight,
					onRequestInsight = { /* AI 리포트 호출 */ }
				)
			}
		}
	} else {
		// 📱 세로 모드 (Portrait): 수직 1열 스크롤
		Column(
			modifier = modifier
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			// 상단 3:4 비율 이미지
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

			// 시퀀스 칩
			SequenceFilterChips(
				sequences = normalSeqs,
				selectedSeq = currentSeq,
				onSequenceSelected = {
					svm.updateSequence(it)
					ivm.setSeq(it)
				}
			)

			// 제어 버튼
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

			// 부위 칩
			FacialPartSelector(
				selectedPart = currentPart,
				onPartSelected = { svm.updateFacialPart(it) }
			)

			// 요약 카드
			FactorSummaryCard(factor = factor)

			// 세부 수치
			FaceStaticDetailsTable(items = ivm.currentFaceStaticsItem)

			// AI 리포트
			GeminiInsightCard(
				insightText = faceResult?.insight,
				onRequestInsight = { }
			)
		}
	}
}

@Composable
fun SequenceFilterChips(
	sequences: List<Sequence>,
	selectedSeq: Sequence,
	onSequenceSelected: (Sequence) -> Unit,
	modifier: Modifier = Modifier
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.horizontalScroll(rememberScrollState()),
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		sequences.forEach { seq ->
			val isSelected = (seq == selectedSeq)
			FilterChip(
				selected = isSelected,
				onClick = { onSequenceSelected(seq) },
				shape = RoundedCornerShape(12.dp),
				label = {
					Text(
						text = seq.displayName,
						fontSize = 14.sp,
						fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
					)
				},
				colors = FilterChipDefaults.filterChipColors(
					selectedContainerColor = TangoNormal,
					selectedLabelColor = Color.Black,
					containerColor = TangoBg500,
					labelColor = SubColor100
				)
			)
		}
	}
}

