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
//            Log.v("mobiel", "${currentItem.userMobile.isNullOrEmpty()}, ${currentItem.userMobile}")
			val mobile = if (currentItem.mobile?.length == 11) {
				currentItem.mobile.substring(0, 3) + "-****-" + currentItem.mobile.substring(7) + ", "
			} else {
				""
			}

			val genderText = when (currentItem.gender) {
				0 -> "남성"
				1 -> "여성"
				else -> ""
			}
			holder.tvMVIGender.text = "${mobile}${genderText}"
			val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
			val birthday = currentItem.birthday


			holder.tvMVIRegDate.text = currentItem.regDate.substring(0, 11)
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

					"만 $ageCalc 세"
				} catch (e: Exception) {
					"미설정"
				}
			} else {
				"미설정"
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

//    fun resetAllItemsUI() {
//        notifyDataSetChanged()
//    }
//    fun setBlockedItemClick(block: Boolean) {
//        isBlockedItemClick = block
//        notifyDataSetChanged()
//    }
}