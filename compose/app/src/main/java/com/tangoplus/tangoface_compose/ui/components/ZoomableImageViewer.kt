package com.tangoplus.tangoface_compose.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

/**
 * 대용량 고해상도 이미지(갤러리 핀치줌/타일 서브샘플링) 전용 Compose 래퍼
 */
@Composable
fun ZoomableImageViewer(
	bitmap: Bitmap?,
	modifier: Modifier = Modifier,
	maxScale: Float = 5.0f,
	minScale: Float = 0.5f,
	onViewCreated: (SubsamplingScaleImageView) -> Unit = {}
) {
	AndroidView(
		modifier = modifier.fillMaxSize(),
		factory = { context ->
			SubsamplingScaleImageView(context).apply {
				this.maxScale = maxScale
				this.minScale = minScale
				setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
				onViewCreated(this)
			}
		},
		update = { view ->
			if (bitmap != null) {
				view.setImage(ImageSource.bitmap(bitmap))
			}
		}
	)
}

