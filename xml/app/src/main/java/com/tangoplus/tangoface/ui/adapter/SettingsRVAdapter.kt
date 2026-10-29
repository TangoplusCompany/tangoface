package com.tangoplus.tangoface.ui.adapter

import android.content.Intent
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.RvSettingsItemBinding
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.ui.main.MainActivity
import com.tangoplus.tangoface.ui.settings.DepthCameraSettingsActivity
import com.tangoplus.tangoface.ui.settings.PosterManageFragment
import com.tangoplus.tangoface.ui.settings.StatisticsFragment

class SettingsRVAdapter(private val fragment: Fragment) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	private val items = listOf(
		"슬라이드 포스터 관리",
		"사전 체크리스트 통계",
		"백업 파일 확인하기",
		"깊이 카메라 각도 조정"
	)

	inner class SettingsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val tvSITitle: TextView = view.findViewById(R.id.tvSITitle)
		val clSI : ConstraintLayout = view.findViewById(R.id.clSI)
		fun bind(currentItem : String) {

			tvSITitle.text = currentItem
			clSI.setOnSingleClickListener {
				when (currentItem) {
					"슬라이드 포스터 관리" -> (fragment.requireActivity() as MainActivity).navigateToSubFragment(PosterManageFragment())
					"사전 체크리스트 통계" -> (fragment.requireActivity() as MainActivity).navigateToSubFragment(StatisticsFragment())
					"백업 파일 확인하기" -> { openSpecificFolder() }
					"깊이 카메라 각도 조정" -> { startSettingsDepthCamera() }
				}
			}
		}
	}

	override fun onCreateViewHolder(
		p0: ViewGroup,
		p1: Int
	): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(p0.context)
		val bd = RvSettingsItemBinding.inflate(inflater, p0, false)
		return SettingsViewHolder(bd.root)
	}

	override fun onBindViewHolder(
		p0: RecyclerView.ViewHolder,
		p1: Int
	) {
		if (p0 is SettingsViewHolder) {
			p0.bind(items[p1])
		}
	}

	override fun getItemCount(): Int {
		return items.size
	}

	fun openSpecificFolder() {
		// primary:경로 형태로 지정 (기본 내부 저장소의 Documents/TangoPlusBackup)
		val docId = "primary:Documents/TangoPlusBackup"
		val uri = DocumentsContract.buildDocumentUri(
			"com.android.externalstorage.documents",
			docId
		)

		val intent = Intent(Intent.ACTION_VIEW).apply {
			setDataAndType(uri, "vnd.android.document/directory")
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}

		try {
			fragment.requireActivity().startActivity(intent)
		} catch (e: Exception) {
			Toast.makeText(fragment.requireContext(), "파일 탐색기를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
		}
	}
	fun startSettingsDepthCamera() {
		val intent = Intent(fragment.requireActivity(), DepthCameraSettingsActivity::class.java)
		fragment.requireActivity().startActivity(intent)
	}
}