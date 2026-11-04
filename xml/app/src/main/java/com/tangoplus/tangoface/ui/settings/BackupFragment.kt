package com.tangoplus.tangoface.ui.settings

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.FragmentBackupBinding
import com.tangoplus.tangoface.ui.settings.vm.Mode
import com.tangoplus.tangoface.ui.settings.vm.SettingsViewModel
import com.tangoplus.tangoface.ui.settings.vm.UiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupFragment : Fragment() {
	private lateinit var bd : FragmentBackupBinding
	private val svm: SettingsViewModel by viewModels ()

	private val exportLauncher = registerForActivityResult(
		ActivityResultContracts.CreateDocument("application/zip")
	) { uri -> uri?.let { svm.runExport(it) } }

	private val importLauncher = registerForActivityResult(
		ActivityResultContracts.OpenDocument()
	) { uri -> uri?.let { svm.onImportFilePicked(it, queryDisplayName(it)) } }


	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentBackupBinding.inflate(inflater)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		bd.btnBExport.setOnClickListener { svm.setMode(Mode.EXPORT) }
		bd.btnBImport.setOnClickListener {
			if (svm.state.value is UiState.Running || svm.state.value is UiState.Loading) return@setOnClickListener
			// 이미 가져오기 미리보기 상태여도 다른 파일을 다시 고를 수 있게 항상 탐색기를 연다
			importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed"))
		}

		// 실행 버튼 (state에 따라 역할이 바뀜)
		bd.btnBStart.setOnClickListener {
			when (val s = svm.state.value) {
				is UiState.ExportReady -> if (s.preview != null) {
					val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
					exportLauncher.launch("TangoPlus_Export_$ts.zip")
				}
				is UiState.ImportReady -> svm.runImport()
				is UiState.Done, is UiState.Error -> svm.resetToInitial()
				else -> Unit
			}
		}

		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				launch { svm.mode.collect { renderMode(it) } }
				launch { svm.state.collect { render(it) } }
				launch { svm.toast.collect { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() } }
			}
		}
	}
	private fun renderMode(m: Mode) = with(bd) {
		val accent = ColorStateList.valueOf(requireContext().getColor(R.color.accent))
		val accent800 = ColorStateList.valueOf(requireContext().getColor(R.color.accent800))

		btnBExport.backgroundTintList = if (m == Mode.EXPORT) accent else accent800
		btnBImport.backgroundTintList = if (m == Mode.IMPORT) accent else accent800
	}

	private fun render(s: UiState) = with(bd) {
		val running = s is UiState.Running

		// 진행바
		pbB.isVisible = running
		tvBProgress.isVisible = running
		// 실행 중에는 모드 버튼 잠금
		btnBExport.isEnabled = !running
		btnBImport.isEnabled = !running

		when (s) {
			is UiState.Loading -> {
				tvBPreview.text = "불러오는 중…"
				btnBStart.text = "잠시만요"
				btnBStart.isEnabled = false
			}
			is UiState.ExportReady -> {
				val p = s.preview
				tvBPreview.text = p?.let {
					"측정 ${it.count}건\n기간 ${it.period}\n파일 ${it.files}개\n예상 용량 약 ${it.sizeMb}MB"
				} ?: "내보낼 측정이 없습니다"
				btnBStart.text = "내보내기"
				btnBStart.isEnabled = p != null
			}
			is UiState.ImportIdle -> {
				tvBPreview.text = "가져올 백업 파일을 선택하세요"
				btnBStart.text = "파일 선택"
				btnBStart.isEnabled = true
			}
			is UiState.ImportReady -> {
				val p = s.preview
				tvBPreview.text = "${p.fileName}\n내보낸 날짜 ${p.exportedDate}\n" +
					"새로 추가 ${p.newCount}건\n이미 있음 ${p.dupCount}건 (건너뜀)\n파일 ${p.fileCount}개"
				btnBStart.text = "가져오기 실행"
				btnBStart.isEnabled = p.newCount > 0
			}
			is UiState.Running -> {
				// 카드 텍스트는 이전 내용 유지
				pbB.setProgressCompat(s.percent, true)
				tvBProgress.text = "${s.percent}%"
				btnBStart.text = "진행 중…"
				btnBStart.isEnabled = false
			}
			is UiState.Done -> {
				tvBPreview.text = s.message
				btnBStart.text = "확인"
				btnBStart.isEnabled = true
			}
			is UiState.Error -> {
				tvBPreview.text = s.message
				btnBStart.text = "확인"
				btnBStart.isEnabled = true
			}
		}
	}

	/** SAF uri에서 파일명 조회 */
	private fun queryDisplayName(uri: Uri): String {
		requireContext().contentResolver.query(
			uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
		)?.use { c ->
			if (c.moveToFirst()) return c.getString(0) ?: "backup.zip"
		}
		return "backup.zip"
	}

	override fun onDestroyView() {
		super.onDestroyView()
	}
}