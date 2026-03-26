package com.tangoplus.tangoface.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.RvUserItemBinding
import com.tangoplus.tangoface.ui.main.vo.UserItem
import com.tangoplus.tangoface.ui.listener.OnMeasureClickListener
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.ui.listener.OnUserClickListener
import com.tangoplus.tangoface.ui.main.MainViewModel
import java.util.Calendar
import java.util.TimeZone
import kotlin.text.substring

class UserRVAdapter(
	private val context: Context,
	private val faceResults: List<UserItem>,
	private val mvm: MainViewModel
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
	var measureClickListener: OnMeasureClickListener? = null
	var userClickListener: OnUserClickListener? = null
	private var isBlockedItemClick = false

	inner class MeasureVerticalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val clMVI: ConstraintLayout = view.findViewById(R.id.clMVI)
		val tvMVIName: TextView = view.findViewById(R.id.tvMVIName)
		val tvMVIMobile: TextView = view.findViewById(R.id.tvMVIMobile)
		val tvMVIAge: TextView = view.findViewById(R.id.tvMVIAge)
		val tvMVICount: TextView = view.findViewById(R.id.tvMVICount)
		val tvMVIGender: TextView = view.findViewById(R.id.tvMVIGender)
		val ivMVICheck: ImageView = view.findViewById(R.id.ivMVICheck)
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
			holder.ivMVICheck.visibility = View.INVISIBLE
			holder.tvMVIName.text = currentItem.userName
//            Log.v("mobiel", "${currentItem.userMobile.isNullOrEmpty()}, ${currentItem.userMobile}")
			val mobile = if (currentItem.userMobile?.length == 11) {
				currentItem.userMobile.substring(0, 3) + "-" + currentItem.userMobile.substring(
					3,
					7
				) + "-" + currentItem.userMobile.substring(7)
			} else {
				""
			}
			holder.tvMVIMobile.text = mobile

			holder.tvMVIGender.text = when (currentItem.userGender) {
				0 -> "남성"
				else -> "여성"
			}
			val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
			val birthday = currentItem.userBirthday

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
			holder.tvMVICount.text = "${currentItem.tempServerSn.size}건"

			holder.clMVI.setOnSingleClickListener {
				userClickListener?.onUserClick(currentItem.userSn)
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