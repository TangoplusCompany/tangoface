package com.tangoplus.tangoface.ui.adapter

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.RvBowItemBinding
import com.tangoplus.tangoface.databinding.RvUserItemBinding
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.vo.MeasureType
import com.tangoplus.tangoface.ui.info.vm.InformationViewModel
import com.tangoplus.tangoface.ui.info.vo.BowItem
import com.tangoplus.tangoface.ui.info.vo.HistoryMappingItem
import com.tangoplus.tangoface.ui.listener.OnBowItemClickListener
import com.tangoplus.tangoface.ui.listener.OnUserClickListener
import com.tangoplus.tangoface.ui.main.vm.MainViewModel
import com.tangoplus.tangoface.ui.main.vo.UserItem
import java.util.Calendar
import java.util.TimeZone

class UserRVAdapter(
	private val context: Context,
	private val mvm: MainViewModel? = null,
	private val ivm: InformationViewModel? = null,
	private val fType: Int = 0,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	var onUserClickListener: OnUserClickListener? = null
	var onBowItemClickListener: OnBowItemClickListener? = null

	private var historyList: List<HistoryMappingItem> = emptyList()

	companion object {
		const val TYPE_HEADER = 0
		const val TYPE_ITEM = 1
		const val TYPE_BOW = 2
	}

	// 💡 ViewModel이나 Fragment에서 새로운 매핑 리스트 전달 시 사용
	fun submitHistoryList(newList: List<HistoryMappingItem>) {
		this.historyList = newList
		notifyDataSetChanged()
	}

	private fun getDisplayHistoryList(): List<HistoryMappingItem> {
		return if (historyList.isNotEmpty()) historyList
		else mvm?.displayUserItemList?.value ?: emptyList()
	}

	// 1. 날짜 헤더 ViewHolder
	inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		private val tvDate: TextView = view.findViewWithTag("tvHeaderDate")
		fun bind(date: String) {
			tvDate.text = date
		}
	}

	// 2. 유저 측정 기록 ViewHolder
	inner class MeasureVerticalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val clMVI: ConstraintLayout = view.findViewById(R.id.clMVI)
		val tvMVIName: TextView = view.findViewById(R.id.tvMVIName)
		val tvMVIRegDate: TextView = view.findViewById(R.id.tvMVIRegDate)
//		val tvMVIAge: TextView = view.findViewById(R.id.tvMVIAge)
//		val tvMVIGender: TextView = view.findViewById(R.id.tvMVIGender)
		val ivMVIType: ImageView = view.findViewById(R.id.ivMVIType)
		val tvMVIMobile: TextView = view.findViewById(R.id.tvMVIMobile)
		val tvMVIType: TextView = view.findViewById(R.id.tvMVIType)

		fun bind(currentItem: UserItem) {
			clMVI.background = ContextCompat.getDrawable(context, R.drawable.effect_20)
			tvMVIName.text = currentItem.name

			tvMVIType.text = currentItem.measureType.displayName
			val typeDrawable = ContextCompat.getDrawable(context,
				when (currentItem.measureType) {
					MeasureType.NORMAL -> R.drawable.ic_normal
					MeasureType.BOW ->  R.drawable.ic_bow
					MeasureType.EXPERT -> R.drawable.ic_expert
					MeasureType.IMAGE -> R.drawable.ic_image
					else -> R.drawable.ic_image
				}
			)
			ivMVIType.setImageDrawable(typeDrawable)
			val mobile = if (currentItem.mobile?.length == 11) {
				currentItem.mobile.substring(0, 3) + "-****-" + currentItem.mobile.substring(7)
			} else {
				""
			}
			tvMVIMobile.visibility = if (tvMVIName.text == "GUEST" && mobile == "") View.GONE else View.VISIBLE
			tvMVIMobile.text = mobile


//			val genderText = when (currentItem.gender) {
//				0 -> "남성"
//				1 -> "여성"
//				else -> ""
//			}
//			tvMVIGender.text = genderText


			val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
			val birthday = currentItem.birthday
			val regDate = "🕑 ${currentItem.regDate.substring(11, 16)}"
			tvMVIRegDate.text = regDate

			val age = if (birthday?.length == 10) {
				try {
					val (birthYear, birthMonth, birthDay) = birthday.split("-").map { it.toInt() }
					val todayYear = c.get(Calendar.YEAR)
					val todayMonth = c.get(Calendar.MONTH) + 1
					val todayDay = c.get(Calendar.DAY_OF_MONTH)

					var ageCalc = todayYear - birthYear
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

//			tvMVIAge.text = age
			clMVI.setOnSingleClickListener {
				val isBow = currentItem.measureType == MeasureType.BOW
				onUserClickListener?.onUserClick(currentItem.tempServerSn, isBow)
			}

		}
	}

	// 3. Bow 아이템 ViewHolder
	inner class BowItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val clBI: ConstraintLayout = view.findViewById(R.id.clBI)
		val tvBIName: TextView = view.findViewById(R.id.tvBIName)
		val tvBIDate: TextView = view.findViewById(R.id.tvBIDate)

		fun bind(currentItem: BowItem) {
			tvBIName.text = currentItem.name
			val regDate = "🗓️ ${currentItem.regDate.substring(0, 16)}"
			tvBIDate.text = regDate

			clBI.setOnSingleClickListener {
				onBowItemClickListener?.onBowItemClick(currentItem.tempServerSn)
			}
		}
	}

	override fun getItemViewType(position: Int): Int {
		return if (fType == 0) {
			when (getDisplayHistoryList().getOrNull(position)) {
				is HistoryMappingItem.Header -> TYPE_HEADER
				is HistoryMappingItem.Item -> TYPE_ITEM
				null -> TYPE_ITEM
			}
		} else {
			TYPE_BOW
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		return when (viewType) {
			TYPE_HEADER -> HeaderViewHolder(createHeaderView(parent.context))
			TYPE_ITEM -> {
				val binding = RvUserItemBinding.inflate(inflater, parent, false)
				MeasureVerticalViewHolder(binding.root)
			}
			else -> {
				val binding = RvBowItemBinding.inflate(inflater, parent, false)
				BowItemViewHolder(binding.root)
			}
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		if (fType == 0) {
			val item = getDisplayHistoryList().getOrNull(position) ?: return
			when {
				holder is HeaderViewHolder && item is HistoryMappingItem.Header -> {
					holder.bind(item.date)
				}
				holder is MeasureVerticalViewHolder && item is HistoryMappingItem.Item -> {
					holder.bind(item.data)
				}
			}
		} else if (fType == 1) {
			if (holder is BowItemViewHolder && ivm != null) {
				val currentItem = ivm.currentBowResults.value.getOrNull(position) ?: return
				holder.bind(currentItem)
			}
		}
	}

	override fun getItemCount(): Int {
		return if (fType == 0) {
			getDisplayHistoryList().size
		} else {
			ivm?.getCurrentBowResults()?.size ?: 0
		}
	}

	private fun createHeaderView(context: Context): View {
		val density = context.resources.displayMetrics.density

		val container = LinearLayout(context).apply {
			orientation = LinearLayout.HORIZONTAL
			gravity = Gravity.CENTER_VERTICAL
			layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
			)
			val padH = (16 * density).toInt()
			val padV = (12 * density).toInt()
			setPadding(padH, padV, padH, padV)
		}

		val textView = TextView(context).apply {
			tag = "tvHeaderDate"
			textSize = 18f
			setTypeface(null, Typeface.BOLD)
			setTextColor(ContextCompat.getColor(context, R.color.accent100))
		}

//		val line = View(context).apply {
//			layoutParams = LinearLayout.LayoutParams(
//				0,
//				(1 * density).toInt(),
//				1f
//			).apply {
//				marginStart = (12 * density).toInt()
//			}
//			setBackgroundColor(ContextCompat.getColor(context, R.color.bg200))
//		}

		container.addView(textView)
//		container.addView(line)
		return container
	}

}