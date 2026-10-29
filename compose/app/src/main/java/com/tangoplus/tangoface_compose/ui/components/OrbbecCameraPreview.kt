package com.tangoplus.tangoface_compose.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.tangoplus.tangoface_compose.orbbec.OrbbecCameraManager
import com.tangoplus.tangoface_compose.orbbec.view.OBGLView

/**
 * Orbbec 3D/Color 센서 카메라 화면 렌더링용 Compose 래퍼
 * - 라이프사이클에 따라 OrbbecCameraManager 자동 시작/종료
 * - OBGLView에 실시간 카메라 프레임 파이프라인 연결
 */
@Composable
fun OrbbecCameraPreview(
	modifier: Modifier = Modifier,
	rotationDegrees: Int = 90,
	centerCrop: Boolean = true,
	onViewCreated: (OBGLView) -> Unit = {}
) {
	val context = LocalContext.current
	val cameraManager = remember { OrbbecCameraManager(context) }

	DisposableEffect(Unit) {
		cameraManager.startCamera()
		onDispose {
			cameraManager.stopCamera()
		}
	}

	AndroidView(
		modifier = modifier.fillMaxSize(),
		factory = { ctx ->
			OBGLView(ctx).apply {
				setZOrderMediaOverlay(true)
				setCenterCrop(centerCrop)
				setRotation(rotationDegrees)
				cameraManager.attachView(this)
				onViewCreated(this)
			}
		},
		update = { view ->
			view.setCenterCrop(centerCrop)
			view.setRotation(rotationDegrees)
			cameraManager.attachView(view)
		}
	)
}
