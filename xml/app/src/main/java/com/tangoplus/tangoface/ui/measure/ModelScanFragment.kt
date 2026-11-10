package com.tangoplus.tangoface.ui.measure

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.tangoplus.tangoface.data.api.ScanHistoryItem
import com.tangoplus.tangoface.databinding.FragmentModelScanBinding
import com.tangoplus.tangoface.ui.adapter.ScanHistoryAdapter
import com.tangoplus.tangoface.ui.measure.vm.ScanUiState
import com.tangoplus.tangoface.ui.measure.vm.ScanViewModel
import kotlinx.coroutines.launch

/**
 * 3D 얼굴 스캔 프래그먼트:
 * - 화면 진입 시 QR 코드 안내 후 1초 뒤 PIN 번호 표시
 * - 뒤로가기 시에도 백그라운드 3D 렌더링/폴링 유실 방지
 * - 스캔 내역 관리 목록 (실패 내역 삭제, 이전 세션 복원 등) 제공
 */
class ModelScanFragment : Fragment() {

    private var _binding: FragmentModelScanBinding? = null
    private val bd get() = _binding!!

    private val svm: ScanViewModel by activityViewModels()

    private lateinit var historyAdapter: ScanHistoryAdapter
    private var errorDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentModelScanBinding.inflate(inflater, container, false)
        return bd.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews()
        initHistoryRecyclerView()
        setupBackPressHandler()
        observeViewModel()

        // 화면 진입 시 신규 또는 기존 진행 중인 세션 복원 (strings.xml 태블릿 키 주입)
        val tabletKey = com.tangoplus.tangoface.data.api.FaceScanApiClient.getTabletKey(requireContext())
        Log.v("키가져와지나요?", "$tabletKey")
        svm.onFragmentEnter(tabletKey)
    }

    private fun initViews() {
        // 스캔 내역 버튼 클릭 시 오버레이 토글
        bd.btnHistoryToggle.setOnClickListener {
            bd.clHistoryOverlay.isVisible = true
        }

        // 스캔 내역 닫기
        bd.btnCloseHistory.setOnClickListener {
            bd.clHistoryOverlay.isVisible = false
        }

        // 실패한 내역 전체 삭제
        bd.btnDeleteAllFailed.setOnClickListener {
            svm.deleteFailedHistories()
            Toast.makeText(requireContext(), "실패한 내역이 모두 삭제되었습니다.", Toast.LENGTH_SHORT).show()
        }

        // 복원 완료 후 새로운 스캔 시작
        bd.btnNewScan.setOnClickListener {
            val key = com.tangoplus.tangoface.data.api.FaceScanApiClient.getTabletKey(requireContext())
            svm.startNewScanFlow(delayMillis = 1000L, tabletKey = key)
        }
    }

    private fun initHistoryRecyclerView() {
        historyAdapter = ScanHistoryAdapter(
            onItemClick = { item ->
                // 목록에서 세션 탭 시 해당 세션 상태 화면으로 복원
                svm.selectSession(item.sessionId)
                bd.clHistoryOverlay.isVisible = false
            },
            onDeleteClick = { item ->
                // 개별 내역 삭제
                svm.deleteHistory(item.sessionId)
                Toast.makeText(requireContext(), "내역이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            }
        )

        bd.rvScanHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    /**
     * 뒤로가기 키 처리: 내역 창이 열려있으면 닫기
     */
    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (bd.clHistoryOverlay.isVisible) {
                        bd.clHistoryOverlay.isVisible = false
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    /**
     * Lifecycle-aware (repeatOnLifecycle) 기반 실시간 UI 상태 수집
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    svm.uiState.collect { state ->
                        renderUiState(state)
                    }
                }

                launch {
                    svm.historyList.collect { list ->
                        renderHistoryList(list)
                    }
                }
            }
        }
    }

    /**
     * 세션 상태 및 3D 복원 진행률에 따른 화면 렌더링
     */
    private fun renderUiState(state: ScanUiState) {
        when (state.status.uppercase()) {
            "IDLE" -> {
                bd.clPairingContainer.isVisible = true
                bd.clProgressContainer.isVisible = false
                bd.clDoneContainer.isVisible = false

                bd.pbPinLoading.isVisible = false
                bd.tvPairingCode.isVisible = false
                bd.tvPairingSub.text = "스마트폰으로 QR 코드를 스캔하세요"
            }

            "READY_TO_SCAN" -> {
                // 화면 진입 직후 1초 동안: QR 스캔 유도 및 PIN 발급 대기
                bd.clPairingContainer.isVisible = true
                bd.clProgressContainer.isVisible = false
                bd.clDoneContainer.isVisible = false

                bd.pbPinLoading.isVisible = true
                bd.tvPairingCode.isVisible = false
                bd.tvPairingSub.text = "PIN 번호를 발급하고 있습니다..."
            }

            "CREATING" -> {
                bd.clPairingContainer.isVisible = true
                bd.clProgressContainer.isVisible = false
                bd.clDoneContainer.isVisible = false

                bd.pbPinLoading.isVisible = true
                bd.tvPairingCode.isVisible = false
                bd.tvPairingSub.text = "PIN 번호를 발급하고 있습니다..."
            }

            "CREATED" -> {
                // 1초 뒤 발급 완료된 6자리 PIN 번호 크게 표시
                bd.clPairingContainer.isVisible = true
                bd.clProgressContainer.isVisible = false
                bd.clDoneContainer.isVisible = false

                bd.pbPinLoading.isVisible = false
                bd.tvPairingCode.isVisible = true
                bd.tvPairingCode.text = state.pairingCode
                bd.tvPairingSub.text = "스마트폰 화면에 위 6자리 번호를 입력해주세요"
            }

            "PAIRED" -> {
                bd.clPairingContainer.isVisible = true
                bd.clProgressContainer.isVisible = false
                bd.clDoneContainer.isVisible = false

                bd.pbPinLoading.isVisible = false
                bd.tvPairingCode.isVisible = true
                bd.tvPairingCode.text = state.pairingCode
                bd.tvPairingSub.text = "스마트폰과 연결되었습니다! 스마트폰으로 얼굴을 촬영해주세요."
            }

            "UPLOADED" -> {
                // 영상 업로드 완료 -> 3D 복원 준비
                bd.clPairingContainer.isVisible = false
                bd.clProgressContainer.isVisible = true
                bd.clDoneContainer.isVisible = false

                bd.tvProgressStage.text = "영상 업로드 완료! 3D 모델 생성을 준비 중입니다..."
                bd.pbScanProgress.progress = 0
                bd.tvProgressPercent.text = "0%"
                bd.tvRemainingTime.text = "준비 중..."
            }

            "PROCESSING" -> {
                // 실시간 3D 복원 진행률 및 남은 시간 표시 (뒤로 가기 해도 계속 유지)
                bd.clPairingContainer.isVisible = false
                bd.clProgressContainer.isVisible = true
                bd.clDoneContainer.isVisible = false

                bd.pbScanProgress.progress = state.progressPercent
                bd.tvProgressPercent.text = "${state.progressPercent}%"
                bd.tvRemainingTime.text = if (state.remainingSeconds > 0) {
                    "예상 남은 시간: 약 ${state.remainingSeconds}초"
                } else {
                    "잠시만 기다려주세요..."
                }
                bd.tvProgressStage.text = state.stageMessage.ifEmpty { "3D 얼굴 복원 진행 중..." }
            }

            "DONE" -> {
                // 복원 완료 안내
                bd.clPairingContainer.isVisible = false
                bd.clProgressContainer.isVisible = false
                bd.clDoneContainer.isVisible = true

                bd.tvDoneTitle.text = "3D 얼굴 복원 완료!"
                bd.tvDoneMessage.text = "3D 모델 복원이 완료되었습니다.\nGLB 모델을 준비합니다."
            }

            "FAILED" -> {
                bd.clPairingContainer.isVisible = true
                bd.clProgressContainer.isVisible = false
                bd.clDoneContainer.isVisible = false

                bd.pbPinLoading.isVisible = false
                bd.tvPairingCode.isVisible = false
                bd.tvPairingSub.text = state.errorMessage ?: "스캔에 실패했습니다. 내역에서 삭제 후 다시 시도해주세요."

                state.errorMessage?.let { msg ->
                    showErrorDialog(msg)
                }
            }
        }
    }

    /**
     * 스캔 내역 목록 렌더링 및 뱃지 카운트 갱신
     */
    private fun renderHistoryList(list: List<ScanHistoryItem>) {
        historyAdapter.submitList(list)
        bd.btnHistoryToggle.text = "스캔 내역 (${list.size})"
        bd.tvEmptyHistory.isVisible = list.isEmpty()
    }

    private fun showErrorDialog(message: String) {
        if (errorDialog?.isShowing == true) return

        errorDialog = AlertDialog.Builder(requireContext())
            .setTitle("3D 스캔 안내")
            .setMessage(message)
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .create()
        errorDialog?.show()
    }

    override fun onDestroyView() {
        errorDialog?.dismiss()
        errorDialog = null
        _binding = null
        super.onDestroyView()
    }
}
