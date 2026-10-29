package com.tangoplus.tangoface.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.skydoves.balloon.Balloon
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.data.db.FaceShape
import com.tangoplus.tangoface.databinding.RvFactorItemBinding
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.ui.info.vo.Factor

class FactorRVAdapter(private val fragment : Fragment, private val currentFactors: Factor) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
	private val info0 = """
	얼굴의 가상 중심선(Midline)을 기준으로 좌우 편차를 확인합니다.
  미간-코-턱을 기준으로 비대칭 정도를 확인합니다. 종류는 다음과 같습니다.
	
	정상: 모든 선이 수직에 가까운 상태
	C형 비대칭: 미간-코까지는 수직이지만, 턱만 한쪽으로 돌아간 경우
	S형 비대칭: 미간-코와 코-턱이 서로 반대 방향으로 돌아간 상태
	중심축 이탈: 전체적으로 얼굴의 요소들이 한쪽으로 쏠린 상태
	"""
	private val info1 = "안면의 외곽 라인이 얼마나 매끄럽고 팽팽하게 유지되고 있는지를 정량화한 지표입니다."
	private val info2 = "광대 크기, 턱의 너비, 볼살의 정도를 조합해 가장 유사한 얼굴형을 도출합니다.\n\n🥚 계란형, ♥️ 하트형, 🔶 마름모형, 🛡️ 육각형, 🟠 둥근형\n\n으로 나눠집니다."

	inner class FactorViewHolder(view: View) : RecyclerView.ViewHolder(view) {

		val tvFIValue0: TextView = view.findViewById(R.id.tvFIValue0)
		val tvFIValue10: TextView = view.findViewById(R.id.tvFIValue10)
		val tvFIValue11: TextView = view.findViewById(R.id.tvFIValue11)

		val tvFIValue2: TextView = view.findViewById(R.id.tvFIValue2)
		val llFIValue: LinearLayout = view.findViewById(R.id.llFIValue)
//		val ibtnFIInfo0: ImageButton = view.findViewById(R.id.ibtnFIInfo0)
//		val ibtnFIInfo1: ImageButton = view.findViewById(R.id.ibtnFIInfo1)
//		val ibtnFIInfo2: ImageButton = view.findViewById(R.id.ibtnFIInfo2)

		fun bind(currentItem: Factor) {
			tvFIValue0.text = currentItem.facialAxis.displayName
			val leftSagging = "%.1f".format(currentItem.saggingScale.first)
			val rightSagging = "%.1f".format(currentItem.saggingScale.second)
			val leftSaggingState = createState(currentItem.saggingScale.first)
			val rightSaggingState = createState(currentItem.saggingScale.second)
			tvFIValue10.text = "왼쪽\n\n${leftSaggingState}\n${leftSagging}"
			tvFIValue11.text = "오른쪽\n\n${rightSaggingState}\n${rightSagging}"
			val faceShapeString = buildString {
				append("1순위: ${getImoji(currentItem.primaryFaceShape)} ${currentItem.primaryFaceShape.displayName}")
				currentItem.secondaryFaceShape?.let { secondary ->
					append("\n2순위: ${getImoji(secondary)} ${secondary.displayName}")
				}
			}
			tvFIValue2.text = faceShapeString

			val balloon0 = createBalloon("비대칭 지수", info0)
			val balloon1 = createBalloon("피부처짐 지수", info1, R.drawable.drawable_sagging_standard)
			val balloon2 = createBalloon("얼굴형", info2 )

			tvFIValue0.setOnSingleClickListener { balloon0.showAlignTop(tvFIValue0) }
			llFIValue.setOnSingleClickListener { balloon1.showAlignTop(llFIValue) }
			tvFIValue2.setOnSingleClickListener { balloon2.showAlignTop(tvFIValue2) }
		}
	}

	override fun onCreateViewHolder(
		p0: ViewGroup,
		p1: Int
	): RecyclerView.ViewHolder {
		val layoutInflater = LayoutInflater.from(p0.context)
		val binding = RvFactorItemBinding.inflate(layoutInflater, p0, false)
		return FactorViewHolder(binding.root)
	}

	override fun onBindViewHolder(
		p0: RecyclerView.ViewHolder,
		p1: Int
	) {
		if (p0 is FactorViewHolder) p0.bind(currentFactors)
	}

	override fun getItemCount(): Int {
		return 1
	}

	private fun createBalloon(
		title: String,
		message: String,
		iconDrawable: Int? = null,  // 선택사항
	): Balloon {
		return Balloon.Builder(fragment.requireContext())
			.setLayout(R.layout.layout_custom_balloon)
			.setLifecycleOwner(fragment.viewLifecycleOwner)
			.build()
			.apply {
				val view = getContentView()

				// Title 설정
				view.findViewById<TextView>(R.id.balloon_title).text = title

				// Message 설정
				view.findViewById<TextView>(R.id.balloon_message).text = message

				// Icon 설정 (있을 경우만)
				val iconView = view.findViewById<ImageView>(R.id.balloon_image)
				if (iconDrawable != null) {
					iconView.setImageResource(iconDrawable)
					iconView.visibility = View.VISIBLE
				} else {
					iconView.visibility = View.GONE
				}
			}
	}

	private fun createState(value: Float): String {

		return when (value) {
			in 1f..1.25f -> "매우 우수"
			in 1.25f..1.4f -> "보통"
			in 1.41f..1.6f -> "탄력 저하"
			else -> "심한 처짐" // 10 이상(초과)인 모든 경우
		}
	}
	private fun getImoji(faceShape: FaceShape) :String {
		return when (faceShape) {
			FaceShape.EGG -> "🥚"
			FaceShape.RHOMBUS -> "🔶"
			FaceShape.HEART -> "♥️"
			FaceShape.PEANUT -> "🥜"
			FaceShape.HEXAGON -> "🛡️"
			FaceShape.CIRCLE -> "🟢"
			FaceShape.DEFAULT -> ""
		}
	}
}