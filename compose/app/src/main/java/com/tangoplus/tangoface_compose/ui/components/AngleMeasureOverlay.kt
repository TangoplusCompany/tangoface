package com.tangoplus.tangoface_compose.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.tangoplus.tangoface_compose.ui.view.AngleMeasureView

/**
 * 이미지 상단 각도 측정/선분 그리기 오버레이 Compose 래퍼
 * 이 컴포저블이 활성화되면 아래 레이어의 터치가 자동으로 잠깁니다.
 */
@Composable
fun AngleMeasureOverlay(
	modifier: Modifier = Modifier,
	onViewCreated: (AngleMeasureView) -> Unit = {}
) {
	AndroidView(
		modifier = modifier.fillMaxSize(),
		factory = { context ->
			AngleMeasureView(context).apply {
				onViewCreated(this)
			}
		}
	)
}

