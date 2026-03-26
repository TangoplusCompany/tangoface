package com.tangoplus.tangoface.domain.util

import android.content.Context
import android.media.MediaPlayer
import android.media.SoundPool
import com.tangoplus.tangoface.R

object SoundManager {
	private val soundMap = mutableMapOf<Int, Int>()
	private var soundPool: SoundPool? = null
	private var mediaPlayer: MediaPlayer? = null

	fun init(context: Context) {
		if (soundPool == null) {
			soundPool = SoundPool.Builder()
				.setMaxStreams(3)
				.build()

			// ------! 이 부분에서 내가 원하는 사운드의 값을 중복없이 넣기 !------
			soundMap[R.raw.camera_shutter] = soundPool!!.load(context, R.raw.camera_shutter, 1)
			soundMap[R.raw.camera_countdown] = soundPool!!.load(context, R.raw.camera_countdown, 1)
		}
	}

	// R.raw.[사운드트랙] 을 가져와서 플레이만 하면 됨.
	fun playSound(soundResId: Int) {
		soundMap[soundResId]?.let { id ->
			soundPool?.play(id, 1f, 1f, 1, 0, 1f)
		}
	}

	fun release() {
		soundPool?.release()
		soundPool = null
		soundMap.clear()
	}

	fun stopBackgroundMusic() {
		mediaPlayer?.stop()
		mediaPlayer?.release()
		mediaPlayer = null
	}
}