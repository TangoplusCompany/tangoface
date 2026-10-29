package com.tangoplus.tangoface_compose.ui.info.components

import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.tangoplus.tangoface_compose.domain.util.BitmapUtility
import com.tangoplus.tangoface_compose.ui.theme.SubColor100
import com.tangoplus.tangoface_compose.ui.theme.TangoBg700
import com.tangoplus.tangoface_compose.ui.view.AngleMeasureView
import com.tangoplus.tangoface_compose.ui.view.TextBoxOverlayView

/**
 * 3:4 고해상도 얼굴 분석 이미지 카드
 * - SubsamplingScaleImageView (핀치줌/팬)
 * - TextBoxOverlayView (랜드마크 거리 및 수치 말풍선 오버레이)
 * - AngleMeasureView (선분 생성 및 각도 측정기)
 * - 상단 사용자 정보 뱃지
 */
@Composable
fun ResultImageViewCard(
	bitmap: Bitmap?,
	textBoxList: List<BitmapUtility.TextBoxData> = emptyList(),
	faceRect: TextBoxOverlayView.FaceRect? = null,
	isGuideLineOn: Boolean = true,
	isAngleMeasureActive: Boolean = false,
	userName: String? = null,
	userMobile: String? = null,
	modifier: Modifier = Modifier
) {
	val context = LocalContext.current
	val formattedMobile = remember(userMobile) {
		if (userMobile != null && userMobile.length == 11 && userMobile.startsWith("010")) {
			"${userMobile.substring(0, 3)}-****-${userMobile.substring(7)}"
		} else ""
	}
	val displayName = userName ?: "GUEST"
	val infoText = if (formattedMobile.isNotEmpty()) "$displayName | $formattedMobile" else displayName

	Card(
		modifier = modifier
			.aspectRatio(3f / 4f)
			.fillMaxWidth(),
		shape = RoundedCornerShape(16.dp),
		colors = CardDefaults.cardColors(containerColor = Color.Black),
		elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
	) {
		Box(modifier = Modifier.fillMaxSize()) {
			// 복합 커스텀 뷰 계층 (AndroidView)
			AndroidView(
				modifier = Modifier.fillMaxSize(),
				factory = { ctx ->
					val frameLayout = FrameLayout(ctx).apply {
						layoutParams = FrameLayout.LayoutParams(
							FrameLayout.LayoutParams.MATCH_PARENT,
							FrameLayout.LayoutParams.MATCH_PARENT
						)
					}

					val ssiv = SubsamplingScaleImageView(ctx).apply {
						layoutParams = FrameLayout.LayoutParams(
							FrameLayout.LayoutParams.MATCH_PARENT,
							FrameLayout.LayoutParams.MATCH_PARENT
						)
						maxScale = 5.0f
						minScale = 0.5f
						setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE)
						setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
					}

					val tbov = TextBoxOverlayView(ctx).apply {
						layoutParams = FrameLayout.LayoutParams(
							FrameLayout.LayoutParams.MATCH_PARENT,
							FrameLayout.LayoutParams.MATCH_PARENT
						)
						setTabletUI()
						attachToImageView(ssiv)
					}

					val amv = AngleMeasureView(ctx).apply {
						layoutParams = FrameLayout.LayoutParams(
							FrameLayout.LayoutParams.MATCH_PARENT,
							FrameLayout.LayoutParams.MATCH_PARENT
						)
					}

					frameLayout.addView(ssiv)
					frameLayout.addView(tbov)
					frameLayout.addView(amv)
					frameLayout
				},
				update = { container ->
					val ssiv = container.getChildAt(0) as? SubsamplingScaleImageView
					val tbov = container.getChildAt(1) as? TextBoxOverlayView
					val amv = container.getChildAt(2) as? AngleMeasureView

					// 1. 비트맵 갱신
					if (bitmap != null && ssiv != null) {
						ssiv.setImage(ImageSource.bitmap(bitmap))
					}

					// 2. 텍스트 박스 오버레이 갱신
					tbov?.apply {
						setFaceRect(faceRect)
						setTextBoxes(textBoxList)
						visibility = if (isGuideLineOn) View.VISIBLE else View.GONE
					}

					// 3. 각도 측정 오버레이 및 터치 잠금
					if (isAngleMeasureActive) {
						ssiv?.apply {
							isPanEnabled = false
							isZoomEnabled = false
							isQuickScaleEnabled = false
						}
						amv?.visibility = View.VISIBLE
					} else {
						ssiv?.apply {
							isPanEnabled = true
							isZoomEnabled = true
							isQuickScaleEnabled = true
						}
						amv?.visibility = View.GONE
					}
				}
			)

			// 상단 사용자 정보 뱃지
			Surface(
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(12.dp),
				shape = RoundedCornerShape(8.dp),
				color = TangoBg700.copy(alpha = 0.8f)
			) {
				Text(
					text = infoText,
					color = SubColor100,
					fontSize = 14.sp,
					modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
				)
			}
		}
	}
}

