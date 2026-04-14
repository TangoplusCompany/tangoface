package com.tangoplus.facebeautyexpert.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.databinding.RvPosterItemBinding
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.facebeautyexpert.ui.listener.OnPosterClickListener
import com.tangoplus.facebeautyexpert.ui.statistics.vm.PosterViewModel

class PosterRVAdapter(
	private val fragment: Fragment,
	private val viewModel: PosterViewModel,
	private val onPickImage: () -> Unit // 갤러리 여는 건 프래그먼트에서 실행
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
	var onPosterClickListener : OnPosterClickListener? = null
	private val VIEW_TYPE_ITEM = 0


	override fun getItemViewType(position: Int): Int =
		if (viewModel.posterList.value!!.size < viewModel.MAX_COUNT && position == viewModel.posterList.value!!.size) 1 else 0

	override fun getItemCount(): Int {
		val size = viewModel.posterList.value?.size ?: 0
		return if (size < viewModel.MAX_COUNT) size + 1 else viewModel.MAX_COUNT
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		return if (viewType == VIEW_TYPE_ITEM) {
			val bd = RvPosterItemBinding.inflate(inflater, parent, false)
			PosterViewHolder(bd)
		} else {
			// 추가 버튼용 레이아웃 (중앙에 +가 있는 XML이라 가정)
			val bd = RvPosterItemBinding.inflate(inflater, parent, false)
			AddViewHolder(bd)
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		val items = viewModel.posterList.value ?: return

		if (holder is PosterViewHolder) {
			holder.bind(items[position], position)
		} else if (holder is AddViewHolder) {
			holder.bind()
		}
	}

	// 일반 이미지 홀더
	inner class PosterViewHolder(val bd: RvPosterItemBinding) : RecyclerView.ViewHolder(bd.root) {
		fun bind(path: String, position: Int) {
			// Glide 등으로 이미지 로드 (path는 내부저장소 경로)
			Glide.with(bd.ivPI).load(path).into(bd.ivPI)
			bd.tvPI.text = "Page ${position + 1}"
			bd.ivPI.setOnSingleClickListener {
				onPosterClickListener?.onEditClick(position)
			}
			bd.ibtnPIDelete.setOnSingleClickListener {
				MaterialAlertDialogBuilder(fragment.requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog).apply {
					setTitle("⚠️ 알림")
					setMessage("해당 슬라이드를 삭제하시겠습니까?")
					setPositiveButton("예") { _, _ ->
						onPosterClickListener?.onDeleteClick(position)
					}
					setNegativeButton("아니오") {_, _ -> }
					show()
				}
			}
		}
	}

	// '+' 버튼 홀더
	inner class AddViewHolder(val bd: RvPosterItemBinding) : RecyclerView.ViewHolder(bd.root) {
		fun bind() {
			bd.ivPI.setImageResource(R.drawable.drawable_poster_add)
			bd.root.setOnClickListener { onPosterClickListener?.onAddClick() }
			bd.tvPI.visibility = View.INVISIBLE
			bd.ibtnPIDelete.visibility = View.INVISIBLE
		}
	}
}