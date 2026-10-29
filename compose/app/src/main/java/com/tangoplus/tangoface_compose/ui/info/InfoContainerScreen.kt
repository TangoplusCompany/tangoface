package com.tangoplus.tangoface_compose.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tangoplus.tangoface_compose.domain.model.MeasureType
import com.tangoplus.tangoface_compose.domain.vo.FaceResult
import com.tangoplus.tangoface_compose.ui.info.vm.InformationViewModel
import com.tangoplus.tangoface_compose.ui.info.vm.SelectViewModel
import com.tangoplus.tangoface_compose.ui.theme.SubColor100
import com.tangoplus.tangoface_compose.ui.theme.SubColor500
import com.tangoplus.tangoface_compose.ui.theme.TangoBg
import com.tangoplus.tangoface_compose.ui.theme.TangoBg300

/**
 * 측정 결과 전체 컨테이너 화면 (XML의 InfoContainerFragment 대체)
 * - MeasureType(NORMAL, EXPERT, BOW, IMAGE, SCAN)에 따라 전용 결과 컴포저블로 동적 라우팅
 * - 태블릿 가로(모니터) 및 세로 방향에 반응형 적응
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoContainerScreen(
	measureType: MeasureType,
	faceResult: FaceResult? = null,
	onNavigateBack: () -> Unit = {},
	ivm: InformationViewModel = viewModel(),
	svm: SelectViewModel = viewModel()
) {
	LaunchedEffect(measureType) {
		svm.setAllSeqs(measureType)
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = {
					Row(verticalAlignment = Alignment.CenterVertically) {
						Text(
							text = "[${measureType.displayName}] 분석 결과",
							fontSize = 18.sp,
							fontWeight = FontWeight.Bold,
							color = SubColor100
						)
						Spacer(modifier = Modifier.width(12.dp))
						Text(
							text = "탱고페이스 스마트 리포트",
							fontSize = 13.sp,
							color = SubColor500
						)
					}
				},
				navigationIcon = {
					IconButton(onClick = onNavigateBack) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBack,
							contentDescription = "뒤로가기",
							tint = SubColor100
						)
					}
				},
				colors = TopAppBarDefaults.topAppBarColors(containerColor = TangoBg300)
			)
		},
		containerColor = TangoBg
	) { paddingValues ->
		Box(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
				.background(TangoBg)
		) {
			when (measureType) {
				MeasureType.NORMAL -> {
					INormalScreen(
						faceResult = faceResult,
						ivm = ivm,
						svm = svm,
						modifier = Modifier.fillMaxSize()
					)
				}
				MeasureType.EXPERT -> {
					IExpertScreen(
						faceResult = faceResult,
						ivm = ivm,
						svm = svm,
						modifier = Modifier.fillMaxSize()
					)
				}
				MeasureType.BOW -> {
					IBowScreen(
						faceResult = faceResult,
						ivm = ivm,
						svm = svm,
						modifier = Modifier.fillMaxSize()
					)
				}
				MeasureType.IMAGE -> {
					IImageScreen(
						faceResult = faceResult,
						ivm = ivm,
						svm = svm,
						modifier = Modifier.fillMaxSize()
					)
				}
				MeasureType.SCAN -> {
					IScanScreen(
						faceResult = faceResult,
						ivm = ivm,
						svm = svm,
						modifier = Modifier.fillMaxSize()
					)
				}
			}
		}
	}
}
