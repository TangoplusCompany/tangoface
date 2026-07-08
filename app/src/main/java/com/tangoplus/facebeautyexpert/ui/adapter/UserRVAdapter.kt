package com.tangoplus.facebeautyexpert.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.databinding.RvUserItemBinding
import com.tangoplus.facebeautyexpert.ui.main.vo.UserItem
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.ui.listener.OnUserClickListener
import java.util.Calendar
import java.util.TimeZone
import kotlin.text.substring

class UserRVAdapter(
	private val context: Context,
	private val faceResults: List<UserItem>,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
	var onUserClickListener: OnUserClickListener? = null

	inner class MeasureVerticalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val clMVI: ConstraintLayout = view.findViewById(R.id.clMVI)
		val tvMVIName: TextView = view.findViewById(R.id.tvMVIName)
		val tvMVIRegDate: TextView = view.findViewById(R.id.tvMVIRegDate)
		val tvMVIAge: TextView = view.findViewById(R.id.tvMVIAge)
		val tvMVIGender: TextView = view.findViewById(R.id.tvMVIGender)
		val tvUICircle: TextView = view.findViewById(R.id.tvUICircle)
		val tvMVIMobile : TextView = view.findViewById(R.id.tvMVIMobile)
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		val binding = RvUserItemBinding.inflate(inflater, parent, false)
		return MeasureVerticalViewHolder(binding.root)
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		val currentItem = faceResults[position]

		if (holder is MeasureVerticalViewHolder) {
			holder.clMVI.background = ContextCompat.getDrawable(context, R.drawable.effect_20)
			holder.tvMVIName.text = currentItem.name
			val circleInitial = currentItem.name?.let {it ->
				if ( it.length > 1) currentItem.name.substring(0, 1) else "G"
			}
			holder.tvUICircle.text = circleInitial
			holder.tvUICircle.background = ContextCompat.getDrawable(context, when (currentItem.gender) {
				0 -> R.drawable.color_gradation_accent_contrast_64
				1 -> R.drawable.color_gradation_accent_64
				else -> R.drawable.color_gradation_bg_64
			})
			holder.tvUICircle.setTextColor(
				ContextCompat.getColor(context, when (currentItem.gender) {
					0 -> R.color.bg
					else -> R.color.subColor100
				})
			)
//            Log.v("mobiel", "${currentItem.userMobile.isNullOrEmpty()}, ${currentItem.userMobile}")
			val mobile = if (currentItem.mobile?.length == 11) {
				"📱 " + currentItem.mobile.substring(0, 3) + "-****-" + currentItem.mobile.substring(7)
			} else {
				""
			}

			val genderText = when (currentItem.gender) {
				0 -> "남성"
				1 -> "여성"
				else -> ""
			}
			holder.tvMVIGender.text = genderText
			holder.tvMVIMobile.text = mobile
			val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
			val birthday = currentItem.birthday

			val regDate = "🗓️ ${currentItem.regDate.substring(0, 16)}"
			holder.tvMVIRegDate.text = regDate
			val age = if (birthday?.length == 10) {
				try {
					val (birthYear, birthMonth, birthDay) = birthday.split("-").map { it.toInt() }

					val todayYear = c.get(Calendar.YEAR)
					val todayMonth = c.get(Calendar.MONTH) + 1  // Calendar.MONTH는 0부터 시작
					val todayDay = c.get(Calendar.DAY_OF_MONTH)

					var ageCalc = todayYear - birthYear

					// 생일 안 지났으면 -1
					if (todayMonth < birthMonth || (todayMonth == birthMonth && todayDay < birthDay)) {
						ageCalc -= 1
					}

					"만 ${ageCalc}세"
				} catch (e: Exception) {
					""
				}
			} else {
				""
			}

			holder.tvMVIAge.text = age
			holder.clMVI.setOnSingleClickListener {
				onUserClickListener?.onUserClick(currentItem.tempServerSn)
			}
		}
	}

	override fun getItemCount(): Int {
		return faceResults.size
	}

	fun getGenderBgColor(context:Context, gender: Int) : Int {
		return when (gender) {
			0 -> ContextCompat.getColor(context, R.color.maleBg)
			1 -> ContextCompat.getColor(context, R.color.femaleBg)
			else -> ContextCompat.getColor(context, R.color.bg15)
		}
	}
	fun getGenderAccentColor(gender: Int) : Int {
		return when (gender) {
			0 -> ContextCompat.getColor(context, R.color.maleAccent)
			1 -> ContextCompat.getColor(context, R.color.femaleAccent)
			else -> ContextCompat.getColor(context, R.color.bg15)
		}
	}
}