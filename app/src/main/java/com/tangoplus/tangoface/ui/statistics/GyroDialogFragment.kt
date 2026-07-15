package com.tangoplus.tangoface.ui.statistics

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.FragmentGyroDialogBinding
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import kotlin.math.asin

class GyroDialogFragment : DialogFragment(), SensorEventListener {
	private lateinit var bd: FragmentGyroDialogBinding
	private lateinit var sensorManager: SensorManager
	private var accelerometer: Sensor? = null
	private var currentBiasZ = 0f
	private var currentBiasX = 0f
	private var filteredAngleZ = 0f
	private var filteredAngleX = 0f
	private val ALPHA = 0.1f
	private val INTERPOLATION_FACTOR = 0.1f
	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentGyroDialogBinding.inflate(inflater)
		return bd.root
	}

	override fun onDestroyView() {
		super.onDestroyView()
		sensorManager.unregisterListener(this)  // 혹시 남아있을 리스너 해제
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		dialog?.setCancelable(false)
		sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager
		accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

		accelerometer?.let {
			sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
		}
		bd.btnMSDConfirm1.setOnSingleClickListener {
			sensorManager.unregisterListener(this)
			dismiss()
		}
	}

	override fun onSensorChanged(event: SensorEvent?) {
		if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
			val rotation = (context?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
				?.defaultDisplay?.rotation ?: Surface.ROTATION_0

			var x = event.values[0]
			var y = event.values[1]
			var z = event.values[2]

			// 회전 상태에 따라 보정
			when (rotation) {
				Surface.ROTATION_0 -> {
					// portrait: 그대로 사용
				}

				Surface.ROTATION_90 -> {
					// landscape left
					val tmp = x
					x = -y
					y = tmp
				}

				Surface.ROTATION_180 -> {
					// 역방향 portrait
					x = -x
					y = -y
				}

				Surface.ROTATION_270 -> {
					// landscape right
					val tmp = x
					x = y
					y = -tmp
				}
			}

			// 수직 감지 (Z축)
			val clampedZ = z.coerceIn(-SensorManager.GRAVITY_EARTH, SensorManager.GRAVITY_EARTH)
			val angleZ = Math.toDegrees(asin((clampedZ / SensorManager.GRAVITY_EARTH).toDouble()))
			filteredAngleZ = lowPassFilterZ(angleZ.toFloat())
			val normalizedAngleZ = (filteredAngleZ + 90).coerceIn(0f, 180f)
			val targetBiasZ = 1 - (normalizedAngleZ / 180f)
			currentBiasZ = interpolateZ(targetBiasZ)

			// 수평 감지 (X축)
			val clampedX = x.coerceIn(-SensorManager.GRAVITY_EARTH, SensorManager.GRAVITY_EARTH)
			val angleX = Math.toDegrees(asin((clampedX / SensorManager.GRAVITY_EARTH).toDouble()))
			filteredAngleX = lowPassFilterX(angleX.toFloat())
			val normalizedAngleX = (filteredAngleX + 90).coerceIn(0f, 180f)
			val targetBiasX = normalizedAngleX / 180f
			currentBiasX = interpolateX(targetBiasX)

			// Bias 적용
			val layoutParams = bd.cvMTDGyro.layoutParams as ConstraintLayout.LayoutParams
			layoutParams.verticalBias = currentBiasZ
			layoutParams.horizontalBias = currentBiasX
			bd.cvMTDGyro.layoutParams = layoutParams
			bd.cvMTDGyro.requestLayout()

			// 배경색 설정
			val isVerticalCorrect = normalizedAngleZ in 88f..92f
			val isHorizontalCorrect = normalizedAngleX in 88f..92f
			// TODO 컬러 변경

			context?.let { safeContext ->
				val colorRes = if (isVerticalCorrect && isHorizontalCorrect)
					R.color.normal
				else
					R.color.red
				bd.cvMTDGyro.setCardBackgroundColor(ContextCompat.getColor(safeContext, colorRes))
			}
		}
	}

	override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

	// ------! 센서 시작 !------
	private fun lowPassFilterZ(input: Float): Float {
		filteredAngleZ += ALPHA * (input - filteredAngleZ)
		return filteredAngleZ
	}

	private fun lowPassFilterX(input: Float): Float {
		filteredAngleX += ALPHA * (input - filteredAngleX)
		return filteredAngleX
	}

	private fun interpolateZ(target: Float): Float {
		return currentBiasZ + (target - currentBiasZ) * INTERPOLATION_FACTOR
	}

	private fun interpolateX(target: Float): Float {
		return currentBiasX + (target - currentBiasX) * INTERPOLATION_FACTOR
	}
}