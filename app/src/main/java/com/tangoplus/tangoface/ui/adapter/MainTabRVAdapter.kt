package com.tangoplus.tangoface.ui.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.facebook.shimmer.ShimmerFrameLayout
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.RvMainTabItemBinding
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.vo.MeasureType
import com.tangoplus.tangoface.ui.listener.OnMainTabClickListener

class MainTabRVAdapter(private val context: Context, private val tabs : List<String>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
  var onMainTabClickListener: OnMainTabClickListener? = null
//  val imgs = listOf(R.drawable.poster_main_0)

  val descs = listOf(
    "사진 1장을 불러와\n" +
      "내 정면 얼굴을 쉽고 가볍게 확인",
    "전문 장비(페이스 보우) 기반\n" +
      "턱관절 수평축 및 비대칭 분석",
    "다각도 3D 스캔 (정면·개구·목폄)\n" +
      "관절 건강까지 포함한 종합 진단",
    "3D 카메라 실시간 측정\n" +
      "정면과 목 자세 중심의 빠른 분석"
  )
  val timeSeqs = listOf(
    // 1. 사진 검사
    "⏱️ : 약 15초\n" +
      "📋 : [1단계] 사진 업로드"
      ,

    // 2. 페이스 보우
    "⏱️ : 약 30초\n" +
      "📋 : [1단계] 페이스 보우 착용" ,


    // 3. 정밀 검사
    "⏱️ : 약 1분\n" +
      "📋 : [4단계] 정면 ➔ 교합 ➔ 턱 가측(좌) ➔ 턱 가측(우)" ,


    // 4. 간편 검사
    "⏱️ : 약 40초\n" +
      "📋 : [2단계] 정면 ➔ 목폄 자세"

  )
  inner class MainTabViewHoler(v: View) : RecyclerView.ViewHolder(v) {
    val clMTI : ConstraintLayout = v.findViewById(R.id.clMTI)
    val tvMTIDesc : TextView = v.findViewById(R.id.tvMTIDesc)
    val tvMTITitle : TextView = v.findViewById(R.id.tvMTITitle)
    val tvMTITimeSeq : TextView = v.findViewById(R.id.tvMTITimeSeq)
    val ivMTI : ImageView = v.findViewById(R.id.ivMTI)
    val sflMTI : ShimmerFrameLayout = v.findViewById(R.id.sflMTI)
    val btnMTI : TextView = v.findViewById(R.id.btnMTI)

    fun bind(currentItem : String, idx: Int) {
      val sizeInPx = (64 * context.resources.displayMetrics.density).toInt()

      ivMTI.layoutParams = ivMTI.layoutParams.apply {
        width = sizeInPx
        height = sizeInPx
      }

      tvMTITitle.text = currentItem
      val typeDrawable = ContextCompat.getDrawable(context,
        when (idx) {
          3 -> R.drawable.ic_normal
          2 -> R.drawable.ic_expert
          1 ->  R.drawable.ic_bow
          else -> R.drawable.ic_image
        }
      )
      tvMTIDesc.text = descs[idx]
      tvMTITimeSeq.text = timeSeqs[idx]
      ivMTI.setImageDrawable(typeDrawable)
      ImageViewCompat.setImageTintList(ivMTI, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent100)))
      clMTI.setOnSingleClickListener {
        onMainTabClickListener?.onMainTabClick(tabs[idx])
      }
    }
  }
  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): RecyclerView.ViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    val bd = RvMainTabItemBinding.inflate(inflater, parent, false)

    val totalAvailableHeight = parent.measuredHeight - parent.paddingTop - parent.paddingBottom
    val spacingBetweenRows = 64.dpToPx(parent.context) // 행 사이 마진이 있다면 제외

    // 2행이므로 높이를 정확히 절반으로 분할
    val itemHeight = (totalAvailableHeight - spacingBetweenRows) / 2

    bd.root.layoutParams = bd.root.layoutParams.apply {
      height = itemHeight
    }
    return MainTabViewHoler(bd.root)
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    if (holder is MainTabViewHoler) {
      val currentItem = tabs[position]
      holder.bind(currentItem, position)
    }
  }

  override fun getItemCount(): Int {
    return tabs.size
  }
  private fun Int.dpToPx(context: Context): Int =
    (this * context.resources.displayMetrics.density).toInt()
}