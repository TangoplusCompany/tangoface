package com.tangoplus.tangoface_compose.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private val TangoColorScheme = darkColorScheme(
	primary = TangoAccent,
	onPrimary = Color.White,
	primaryContainer = TangoAccent900,
	onPrimaryContainer = TangoAccent100,

	secondary = TangoNormal,
	onSecondary = Color.Black,
	secondaryContainer = TangoBg500,
	onSecondaryContainer = TangoNormal,

	tertiary = TangoAccentContrast,
	onTertiary = Color.Black,

	background = TangoBg,
	onBackground = SubColor100,

	surface = TangoBg700,
	onSurface = SubColor100,
	surfaceVariant = TangoBg500,
	onSurfaceVariant = SubColor300
)

@Composable
fun TangoFaceComposeTheme(
	darkTheme: Boolean = isSystemInDarkTheme(),
	content: @Composable () -> Unit
) {
	val colorScheme = TangoColorScheme
	val view = LocalView.current
	if (!view.isInEditMode) {
		SideEffect {
			val window = (view.context as? Activity)?.window
			if (window != null) {
				WindowCompat.setDecorFitsSystemWindows(window, false)
				val controller = WindowCompat.getInsetsController(window, view)
				controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
				controller.hide(WindowInsetsCompat.Type.systemBars())
			}
		}
	}

	MaterialTheme(
		colorScheme = colorScheme,
		typography = Typography,
		content = content
	)
}
