package com.tangoplus.tangoface_compose.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.tangoplus.tangoface_compose.ui.view.OverlayView

/**
 * 실시간 얼굴 랜드마크, 얼굴 축, 자세 가이드라인을 그리는 오버레이 Compose 래퍼
 */
@Composable
fun CameraOverlay(
	modifier: Modifier = Modifier,
	onViewCreated: (OverlayView) -> Unit = {}
) {
	AndroidView(
		modifier = modifier.fillMaxSize(),
		factory = { context ->
			OverlayView(context).apply {
				onViewCreated(this)
			}
		}
	)
}

