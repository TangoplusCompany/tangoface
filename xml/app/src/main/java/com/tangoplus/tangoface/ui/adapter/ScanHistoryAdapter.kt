package com.tangoplus.tangoface.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.data.api.ScanHistoryItem
import com.tangoplus.tangoface.databinding.ItemScanHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 3D 얼굴 스캔 내역 목록 RecyclerView 어댑터
 */
class ScanHistoryAdapter(
    private val onItemClick: (ScanHistoryItem) -> Unit,
    private val onDeleteClick: (ScanHistoryItem) -> Unit
) : ListAdapter<ScanHistoryItem, ScanHistoryAdapter.ViewHolder>(DiffCallback) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    inner class ViewHolder(private val binding: ItemScanHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ScanHistoryItem) {
            val context = itemView.context

            binding.tvItemPinCode.text = "PIN: ${item.pairingCode}"
            val timeStr = timeFormat.format(Date(item.createdAt))

            when (item.status.uppercase()) {
                "PROCESSING" -> {
                    binding.tvItemStatusBadge.text = "생성 중 (${item.progressPercent}%)"
                    binding.tvItemStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.subColor100))
                    val remainStr = if (item.remainingSeconds > 0) " | 예상 ${item.remainingSeconds}초 남음" else ""
                    binding.tvItemSubInfo.text = "[$timeStr] ${item.stageMessage}$remainStr"
                }

                "DONE" -> {
                    binding.tvItemStatusBadge.text = "생성 완료"
                    binding.tvItemStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.accentContrast))
                    binding.tvItemSubInfo.text = "[$timeStr] 3D 얼굴 생성이 완료되었습니다."
                }

                "FAILED" -> {
                    binding.tvItemStatusBadge.text = "실패"
                    binding.tvItemStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.red))
                    binding.tvItemSubInfo.text = "[$timeStr] ${item.failReason ?: "3D 얼굴 생성에 실패했습니다."}"
                }

                "PAIRED" -> {
                    binding.tvItemStatusBadge.text = "촬영 중"
                    binding.tvItemStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.subColor100))
                    binding.tvItemSubInfo.text = "[$timeStr] 스마트폰에서 얼굴 촬영 진행 중"
                }

                else -> {
                    binding.tvItemStatusBadge.text = "대기 중"
                    binding.tvItemStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.subColor500))
                    binding.tvItemSubInfo.text = "[$timeStr] 스마트폰 연동 대기 중"
                }
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }

            binding.btnItemDelete.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScanHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ScanHistoryItem>() {
            override fun areItemsTheSame(oldItem: ScanHistoryItem, newItem: ScanHistoryItem): Boolean {
                return oldItem.sessionId == newItem.sessionId
            }

            override fun areContentsTheSame(oldItem: ScanHistoryItem, newItem: ScanHistoryItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
