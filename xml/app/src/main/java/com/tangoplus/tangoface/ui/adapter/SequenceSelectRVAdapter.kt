package com.tangoplus.tangoface.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.RvFacialPartItemBinding
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.ui.info.vm.SelectViewModel
import com.tangoplus.tangoface.ui.listener.OnSequenceClickListener

class SequenceSelectRVAdapter(private val svm: SelectViewModel) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
  var onSequenceClickListener : OnSequenceClickListener? = null
  inner class FacialPartViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val clFHAI : ConstraintLayout = view.findViewById(R.id.clFHAI)
    val tvFHAI: TextView = view.findViewById(R.id.tvFHAI)

    fun bind(currentItem: Sequence) {
      tvFHAI.text = currentItem.displayName
      val isSelected = currentItem == svm.currentSequence.value

      // 2. Context 가져오기 (색상 리소스 참조용)
      val context = itemView.context
      val currentText = tvFHAI.text.toString()
      if (currentText.contains("가측 이동")) {
        tvFHAI.text = currentText
          .replace("가측 이동(왼쪽)", "왼쪽 이동")
          .replace("가측 이동(오른쪽)", "오른쪽 이동")
      }
      if (isSelected) {
        clFHAI.backgroundTintList = ContextCompat.getColorStateList(context, R.color.accent)
      } else {
        clFHAI.backgroundTintList = ContextCompat.getColorStateList(context, R.color.transparent)
      }
      clFHAI.setOnSingleClickListener {
        onSequenceClickListener?.onSequenceClick(currentItem)
      }
    }
  }

  override fun onCreateViewHolder(
    p0: ViewGroup,
    p1: Int
  ): RecyclerView.ViewHolder {
    val inflater = LayoutInflater.from(p0.context)
    val binding = RvFacialPartItemBinding.inflate(inflater, p0, false)
    return FacialPartViewHolder(binding.root)
  }

  override fun onBindViewHolder(
    holder: RecyclerView.ViewHolder,
    p1: Int
  ) {
    val currentItem = svm.getAllSeqs()[p1]
    if (holder is FacialPartViewHolder) holder.bind(currentItem)
  }

  override fun getItemCount(): Int {
    return svm.getAllSeqs().size
  }
}