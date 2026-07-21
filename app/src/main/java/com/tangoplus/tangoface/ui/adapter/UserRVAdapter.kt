package com.tangoplus.tangoface.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.RvBowItemBinding
import com.tangoplus.tangoface.databinding.RvUserItemBinding
import com.tangoplus.tangoface.ui.main.vo.UserItem
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.vo.MeasureType
import com.tangoplus.tangoface.ui.info.vm.InformationViewModel
import com.tangoplus.tangoface.ui.info.vo.BowItem
import com.tangoplus.tangoface.ui.listener.OnBowItemClickListener
import com.tangoplus.tangoface.ui.listener.OnUserClickListener
import com.tangoplus.tangoface.ui.main.vm.MainViewModel
import java.util.Calendar
import java.util.TimeZone
import kotlin.text.substring

class UserRVAdapter(
	private val context: Context,
	private val mvm: MainViewModel? = null,
	private val ivm: InformationViewModel? = null,
	private val fType : Int = 0,
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
		val tvMVIType : TextView = view.findViewById(R.id.tvMVIType)

		fun bind(currentItem : UserItem) {

			clMVI.background = ContextCompat.getDrawable(context, R.drawable.effect_20)
			tvMVIName.text = currentItem.name
			val circleInitial = currentItem.name?.let {it ->
				if ( it.length > 1) currentItem.name.substring(0, 1) else "G"
			}
			tvUICircle.text = circleInitial
			tvUICircle.background = ContextCompat.getDrawable(context, when (currentItem.gender) {
				0 -> R.drawable.color_gradation_accent_contrast_64
				1 -> R.drawable.color_gradation_accent_64
				else -> R.drawable.color_gradation_bg_64
			})
			tvUICircle.setTextColor(
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
			tvMVIGender.text = genderText
			tvMVIMobile.text = mobile
			val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
			val birthday = currentItem.birthday

			val regDate = "🗓️ ${currentItem.regDate.substring(0, 16)}"
			tvMVIRegDate.text = regDate
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

			tvMVIAge.text = age
			clMVI.setOnSingleClickListener {
				val isBow = currentItem.measureType == MeasureType.BOW
				onUserClickListener?.onUserClick(currentItem.tempServerSn, isBow)
			}
//
//			val typeText = when (currentItem.measureType) {
//				MeasureType.EXPERT ->
//			}
			tvMVIType.text = currentItem.measureType.displayName
		}
	}

	var onBowItemClickListener : OnBowItemClickListener? = null
	inner class BowItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val clBI : ConstraintLayout = view.findViewById(R.id.clBI)
		val tvBIName : TextView  = view.findViewById(R.id.tvBIName)
		val tvBIDate : TextView = view.findViewById(R.id.tvBIDate)
		fun bind(currentItem : BowItem) {
			tvBIName.text = currentItem.name
			val regDate = "🗓️ ${currentItem.regDate.substring(0, 16)}"
			tvBIDate.text = regDate

			clBI.setOnSingleClickListener {
				onBowItemClickListener?.onBowItemClick(currentItem.tempServerSn)
			}
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		val binding = when (fType) {
			0 -> RvUserItemBinding.inflate(inflater, parent, false)
			else -> RvBowItemBinding.inflate(inflater, parent, false)
		}
		return when (fType) {
			0 -> MeasureVerticalViewHolder(binding.root)
			else -> BowItemViewHolder(binding.root)
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (fType) {
			0 -> {
				if (holder is MeasureVerticalViewHolder && mvm != null) {
					val currentItem = mvm.currentUserItems[position]
					holder.bind(currentItem)
				}
			}
			1 -> {
				if (holder is BowItemViewHolder && ivm != null) {
					val currentItem = ivm.currentBowResults.value[position]
					holder.bind(currentItem)
				}
			}
		}


	}

	override fun getItemCount(): Int {
		return when (fType) {
			0 -> mvm?.currentUserItems?.size ?: 0
			1 -> ivm?.getCurrentBowResults()?.size ?: 0
			else -> 0
		}
	}

	override fun getItemViewType(position: Int): Int {
		return fType
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