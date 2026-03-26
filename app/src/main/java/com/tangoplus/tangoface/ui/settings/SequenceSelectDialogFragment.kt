package com.tangoplus.tangoface.ui.settings

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.FragmentSequenceSelectDialogBinding
import com.tangoplus.tangoface.domain.util.AnimationUtility.startTypingAnimation
import com.tangoplus.tangoface.domain.util.BitmapUtility.getScreenOrientation
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.ui.adapter.SelectRVAdapter
import com.tangoplus.tangoface.ui.listener.OnSeqCategoryClickListener
import com.tangoplus.tangoface.ui.measure.DepthCameraActivity
import com.tangoplus.tangoface.ui.settings.vm.SequenceSelectViewModel
import com.tangoplus.tangoface.ui.settings.vo.SeqCategory
import com.tangoplus.tangoface.ui.user.UserViewModel
import kotlinx.coroutines.launch

class SequenceSelectDialogFragment : DialogFragment(), OnSeqCategoryClickListener {
	private lateinit var bd: FragmentSequenceSelectDialogBinding
	private val ssvm: SequenceSelectViewModel by activityViewModels()
	private val uvm: UserViewModel by activityViewModels()
	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentSequenceSelectDialogBinding.inflate(inflater)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		dialog?.setCancelable(true)
		initSelectedSeq()
		setAdapter()
		setCategoryObserver()
		setItemObserver()
		bd.btnSSD.setOnSingleClickListener {
			ssvm.saveSelectedSeq()

			when (ssvm.fragmentCase) {
				0 -> {
					val intent = Intent(context, DepthCameraActivity::class.java)
					startActivity(intent)
					requireActivity().finishAffinity()
				}

				1 -> {
					val userItem = uvm.getUserItem()
					val intent = Intent(context, DepthCameraActivity::class.java)
					intent.putExtra("currentUserItem", userItem)

					Log.v("가져온게 있나요", "In: ${userItem.userName}, ${userItem.userMobile}, ${userItem.userSn}")

					startActivity(intent)
					requireActivity().finishAffinity()
				}

				2 -> {}
			}
			dismiss()
		}
		bd.ibtnSSDExIt.setOnSingleClickListener { dismiss() }

		lifecycleScope.launch {
			startTypingAnimation(bd.tvSSDExplain, "필요한 측정만 선택해주세요. 설정에서 언제든지 변경할 수 있습니다.")
		}
		lifecycleScope.launch {
			startTypingAnimation(bd.tvSSDBow, "페이스 보우는 뎁스카메라 연결 시 측정 화면에서 선택할 수 있습니다.")
		}
	}

	private fun initSelectedSeq() {
		val savedSeq = ssvm.getSelectedSeq()
		ssvm.setTempSelectedSeq(savedSeq)
	}

	private fun setCategoryObserver() {
		ssvm.selectedSeqCategory.observe(viewLifecycleOwner) { selected ->
			scAdapter.selectedSeqCategory = selected
			when (selected) {
				SeqCategory.CUSTOM -> {
					// 필요한 경우 처리
				}

				SeqCategory.TMJ_STATE -> {
					ssvm.setTempSelectedSeq(ssvm.getStandardSequences(SeqCategory.TMJ_STATE))
				}

				SeqCategory.PILATES -> {
					ssvm.setTempSelectedSeq(ssvm.getStandardSequences(SeqCategory.PILATES))
				}

				else -> {}
			}
		}
		// tempSelectedSeq 변경 감지하여 어댑터 갱신
		ssvm.tempSelectedSeq.observe(viewLifecycleOwner) { selectedSeqs ->
			seqAdapter.notifyDataSetChanged()
			// 또는 더 효율적으로 DiffUtil 사용
		}
	}

	private fun setItemObserver() {
		ssvm.tempSelectedSeq.observe(viewLifecycleOwner) {
			bd.btnSSD.isEnabled = it.isNotEmpty()
			bd.btnSSD.backgroundTintList = ColorStateList.valueOf(
				ContextCompat.getColor(
					requireContext(),
					if (it.isNotEmpty()) R.color.black else R.color.subColor500
				)
			)
		}
	}


	private lateinit var scAdapter: SelectRVAdapter
	private lateinit var seqAdapter: SelectRVAdapter

	private fun setAdapter() {
		scAdapter = SelectRVAdapter(this@SequenceSelectDialogFragment, ssvm, type = 2)
		scAdapter.onSeqCategoryClickListener = this
		bd.rvSSD1.apply {
			adapter = scAdapter
			layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
		}

		seqAdapter = SelectRVAdapter(this@SequenceSelectDialogFragment, ssvm, type = 0)

		val isPortrait = activity?.getScreenOrientation()?.contains("portrait")
		bd.rvSSD0.apply {
			adapter = seqAdapter
			layoutManager = GridLayoutManager(context, if (isPortrait == true) 2 else 3)
		}
	}

	override fun onSeqCategoryClick(item: SeqCategory) {
		ssvm.onCategoryCustomSelected(item)
	}
}