package com.tangoplus.facebeautyexpert.domain.util

import android.Manifest
import android.os.Build

object PermissionUtility {
	fun getAllPermissions(): Array<String> {
		return when {
			Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
				arrayOf(
					Manifest.permission.READ_MEDIA_IMAGES,
					Manifest.permission.READ_MEDIA_VIDEO,
					Manifest.permission.READ_MEDIA_AUDIO,
					Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
					Manifest.permission.CAMERA,
					Manifest.permission.RECORD_AUDIO,
					Manifest.permission.READ_MEDIA_IMAGES,
					Manifest.permission.READ_MEDIA_VIDEO,
					Manifest.permission.READ_MEDIA_AUDIO,
					Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
				)
			}

			Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
				arrayOf(
					Manifest.permission.READ_MEDIA_IMAGES,
					Manifest.permission.READ_MEDIA_VIDEO,
					Manifest.permission.READ_MEDIA_AUDIO,
					Manifest.permission.CAMERA,
					Manifest.permission.RECORD_AUDIO,
					Manifest.permission.READ_MEDIA_IMAGES,
					Manifest.permission.READ_MEDIA_VIDEO,
					Manifest.permission.READ_MEDIA_AUDIO,
					Manifest.permission.POST_NOTIFICATIONS,
				)
			}

			else -> {
				arrayOf(
					Manifest.permission.READ_EXTERNAL_STORAGE,
					Manifest.permission.WRITE_EXTERNAL_STORAGE,
					Manifest.permission.CAMERA,
					Manifest.permission.RECORD_AUDIO,
					Manifest.permission.READ_EXTERNAL_STORAGE,
					Manifest.permission.WRITE_EXTERNAL_STORAGE
				)
			}
		}
	}

	fun getRequiredPermissions(): Array<String> {
		return when {
			Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
				arrayOf(
					Manifest.permission.CAMERA,
					Manifest.permission.RECORD_AUDIO,
					Manifest.permission.READ_MEDIA_IMAGES,
					Manifest.permission.READ_MEDIA_VIDEO,
					Manifest.permission.READ_MEDIA_AUDIO,
					Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
				)
			}

			Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
				arrayOf(
					Manifest.permission.CAMERA,
					Manifest.permission.RECORD_AUDIO,
					Manifest.permission.READ_MEDIA_IMAGES,
					Manifest.permission.READ_MEDIA_VIDEO,
					Manifest.permission.READ_MEDIA_AUDIO,
					Manifest.permission.POST_NOTIFICATIONS,
				)
			}

			else -> {
				arrayOf(
					Manifest.permission.CAMERA,
					Manifest.permission.RECORD_AUDIO,
					Manifest.permission.READ_EXTERNAL_STORAGE,
					Manifest.permission.WRITE_EXTERNAL_STORAGE
				)
			}
		}
	}
}