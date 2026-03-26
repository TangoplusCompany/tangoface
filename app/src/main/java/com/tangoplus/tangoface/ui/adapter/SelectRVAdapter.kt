package com.tangoplus.tangoface.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.RvFaceTypeItemBinding
import com.tangoplus.tangoface.databinding.RvManageTabItemBinding
import com.tangoplus.tangoface.databinding.RvSeqCategoryItemBinding
import com.tangoplus.tangoface.databinding.RvSeqSelectItemBinding
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.vo.FaceType
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.ui.listener.OnFaceTypeClickListener
import com.tangoplus.tangoface.ui.listener.OnSeqCategoryClickListener
import com.tangoplus.tangoface.ui.settings.vo.SeqCategory
import com.tangoplus.tangoface.ui.settings.vm.SequenceSelectViewModel
import com.tangoplus.tangoface.ui.settings.vm.SettingsViewModel

class SelectRVAdapter(
	private val fragment: Fragment,
	private val ssvm: SequenceSelectViewModel,
	private val svm: SettingsViewModel? = null,
	private val type: Int
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	inner class SeqViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val clSSI: ConstraintLayout = view.findViewById(R.id.clSSI)

		//        val ivSSI : ImageView = view.findViewById(R.id.ivSSI)
		val tvSSI: TextView = view.findViewById(R.id.tvSSI)
		val ivSSICheck: ImageView = view.findViewById(R.id.ivSSICheck)

		@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
		fun bind(currentItem: Sequence) {

			// 제목 대응
			tvSSI.text = currentItem.displayName

			// 이미지 대응
//            when (currentItem.displayName) {
//                "편한 상태", "정면 상태", "페이스 보우" -> ivSSI.setImageDrawable(ContextCompat.getDrawable(fragment.context, R.drawable.ic_seq_1))
//                "교합 상태", "턱 가측 이동(왼쪽)", "턱 가측 이동(오른쪽)" -> ivSSI.setImageDrawable(ContextCompat.getDrawable(fragment.context, R.drawable.ic_seq_2))
//                "개구 상태" -> ivSSI.setImageDrawable(ContextCompat.getDrawable(fragment.context, R.drawable.ic_seq_5))
//                "목폄 상태" -> ivSSI.setImageDrawable(ContextCompat.getDrawable(fragment.context, R.drawable.ic_seq_6))
//                "왼쪽 상태" -> ivSSI.setImageDrawable(ContextCompat.getDrawable(fragment.context, R.drawable.ic_seq_7))
//                "오른쪽 상태" -> ivSSI.setImageDrawable(ContextCompat.getDrawable(fragment.context, R.drawable.ic_seq_8))
//            }
			if (ssvm.tempSelectedSeq.value != null) {
				if (currentItem in ssvm.tempSelectedSeq.value!!) {
					clSSI.background =
						ContextCompat.getDrawable(fragment.context, R.drawable.effect_stroke_20_2)
					ivSSICheck.visibility = View.VISIBLE
				} else {
					clSSI.background = ContextCompat.getDrawable(fragment.context, R.drawable.effect_20)
					ivSSICheck.visibility = View.INVISIBLE
				}
			}

			clSSI.setOnSingleClickListener {
				if (ssvm.tempSelectedSeq.value != null) {
					if (currentItem !in ssvm.tempSelectedSeq.value!!) {
						ssvm.addSeqWithLinked(currentItem)
					} else {
						ssvm.removeSeqWithLinked(currentItem)
					}
				}
				ssvm.onTempSelectionChanged()
				ssvm.onSeqCategorySelectionChanged()
				ssvm.checkAndRestoreCategory()
				notifyDataSetChanged()
			}
		}
	}

	// ------------# 얼굴 타입 선택 #--------------

	var selectedType: FaceType? = null
		set(value) {
			field = value
			notifyDataSetChanged()
		}

	var onFaceTypeClickListener: OnFaceTypeClickListener? = null

	inner class FaceTypeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val tvFTI: TextView = view.findViewById(R.id.tvFTI)
		val clFTI: ConstraintLayout = view.findViewById(R.id.clFTI)
		val ivFTI: ImageView = view.findViewById(R.id.ivFTI)
		val ivFTICheck: ImageView = view.findViewById(R.id.ivFTICheck)
		fun bind(currentItem: FaceType, position: Int) {
			tvFTI.text = currentItem.displayName
			val resourceId = fragment.context?.resources?.getIdentifier(
				"drawable_face_shape_$position", // 파일명 (확장자 제외)
				"drawable",   // 리소스 타입 (drawable, string, layout 등)
				fragment.context?.packageName // 앱 패키지명
			)

			val drawable = resourceId?.let {
				ContextCompat.getDrawable(
					fragment.context,
					it
				)
			}
			ivFTI.setImageDrawable(drawable)
			if (currentItem == selectedType) {
				clFTI.setBackgroundResource(R.drawable.effect_stroke_20_2)
				ivFTICheck.visibility = View.VISIBLE
			} else {
				clFTI.setBackgroundResource(R.drawable.effect_20) // 비선택 상태
				ivFTICheck.visibility = View.INVISIBLE
			}
			clFTI.setOnSingleClickListener {
				onFaceTypeClickListener?.onFaceTypeClick(currentItem)

			}
		}
	}

	var onSeqCategoryClickListener: OnSeqCategoryClickListener? = null
	var selectedSeqCategory: SeqCategory = SeqCategory.CUSTOM
		set(value) {
			field = value
			notifyDataSetChanged()     // 전체 새로 그리기
		}

	inner class SeqCategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val tvSCITitle: TextView = view.findViewById(R.id.tvSCITitle)
		val ivSCICheck: ImageView = view.findViewById(R.id.ivSCICheck)

		fun bind(currentItem: SeqCategory, position: Int) {
			tvSCITitle.text = currentItem.displayName

			if (currentItem == selectedSeqCategory) {
				tvSCITitle.setBackgroundResource(R.drawable.effect_stroke_20_2)
				ivSCICheck.visibility = View.VISIBLE
			} else {
				tvSCITitle.setBackgroundResource(R.drawable.effect_20) // 비선택 상태
				ivSCICheck.visibility = View.INVISIBLE
			}
			tvSCITitle.setOnSingleClickListener {
				onSeqCategoryClickListener?.onSeqCategoryClick(currentItem)
				ssvm.onSeqCategorySelectionChanged()
				ssvm.checkAndRestoreCategory() // 원상복귀 체크
			}
		}
	}


	var selectedTabIndex: Int = 0
		set(value) {
			field = value
			notifyDataSetChanged()     // 전체 새로 그리기
		}

	inner class ManageTabViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val tvMTI: TextView = view.findViewById(R.id.tvMTITitle)
		fun bind(currentItem: String, position: Int) {
			if (svm != null) {
				if (position == selectedTabIndex) {
					tvMTI.setBackgroundResource(R.drawable.effect_stroke_20_2)
				} else {
					tvMTI.setBackgroundResource(R.drawable.effect_20) // 비선택 상태
				}
				tvMTI.apply {
					text = currentItem
					setOnSingleClickListener {
						svm.currentTabIndex.value = position
						selectedTabIndex = svm.currentTabIndex.value
					}
				}
			}
		}
	}

	override fun onCreateViewHolder(
		parent: ViewGroup,
		viewType: Int
	): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		val binding = when (type) {
			0 -> RvSeqSelectItemBinding.inflate(inflater, parent, false)
			1 -> RvFaceTypeItemBinding.inflate(inflater, parent, false)
			2 -> RvSeqCategoryItemBinding.inflate(inflater, parent, false)
			3 -> RvManageTabItemBinding.inflate(inflater, parent, false)
			else -> throw IllegalStateException()
		}
		return when (type) {
			0 -> SeqViewHolder(binding.root)
			1 -> FaceTypeViewHolder(binding.root)
			2 -> SeqCategoryViewHolder(binding.root)
			3 -> ManageTabViewHolder(binding.root)
			else -> throw IllegalStateException()
		}
	}

	override fun onBindViewHolder(
		holder: RecyclerView.ViewHolder,
		position: Int
	) {
		when (holder) {
			is SeqViewHolder -> {
				holder.bind(ssvm.allSeq[position])
			}

			is FaceTypeViewHolder -> {
				holder.bind(ssvm.allFaceType[position], position)
			}

			is SeqCategoryViewHolder -> {
				holder.bind(ssvm.allSeqCategory[position], position)
			}

			is ManageTabViewHolder -> {
				holder.bind(listOf("측정 결과 정렬", "사용자 정렬")[position], position)
			}
		}
	}

	override fun getItemCount(): Int {
		return when (type) {
			0 -> ssvm.allSeq.size
			1 -> ssvm.allFaceType.size
			2 -> ssvm.allSeqCategory.size
			else -> 2
		}
	}

	override fun getItemViewType(position: Int): Int {
		return when (type) {
			0 -> TYPE_SEQ
			1 -> TYPE_FACE
			2 -> TYPE_SEQ_CATEGORY
			else -> TYPE_MANAGE_TAB
		}
	}

	companion object {
		const val TYPE_FACE = 0
		const val TYPE_SEQ = 1
		const val TYPE_SEQ_CATEGORY = 2
		const val TYPE_MANAGE_TAB = 3
	}

}
