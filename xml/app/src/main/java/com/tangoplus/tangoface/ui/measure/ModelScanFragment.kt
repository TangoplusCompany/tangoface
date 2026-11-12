package com.tangoplus.tangoface.ui.measure

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.databinding.FragmentModelScanBinding
import com.tangoplus.tangoface.domain.util.FileUtility
import com.tangoplus.tangoface.orbbec.application.GlbBakingLockManager
import com.tangoplus.tangoface.ui.main.MainActivity
import com.tangoplus.tangoface.ui.main.vm.MainViewModel
import com.tangoplus.tangoface.ui.measure.vm.InputViewModel
import com.tangoplus.tangoface.ui.measure.vm.MeasureViewModel
import com.tangoplus.tangoface.ui.measure.vm.ScanUiState
import com.tangoplus.tangoface.ui.measure.vm.ScanUserInfo
import com.tangoplus.tangoface.ui.measure.vm.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 3D 얼굴 스캔 프래그먼트:
 * - 화면 진입 시 QR 코드 안내 후 1초 뒤 새 PIN 번호 발급
 * - 3D 복원 진행 시작 3초 뒤 측정자 정보 입력창(UserSetupDialogFragment) 자동 노출
 * - 화면 이탈/종료 시 진행 상태 및 입력 정보 전체 초기화
 */
class ModelScanFragment : Fragment() {

    private var _binding: FragmentModelScanBinding? = null
    private val bd get() = _binding!!

    private val svm: ScanViewModel by activityViewModels()
    private val mainVm: MainViewModel by activityViewModels()
    private val ivm: InputViewModel by activityViewModels()
    private val mvm: MeasureViewModel by activityViewModels()

    private var errorDialog: AlertDialog? = null
    private var hasShownUserSetup = false
    private var hasStartedUserSetupCountdown = false
    private var userSetupDelayJob: Job? = null
    private var bakingTimeoutJob: Job? = null

    companion object {
        private const val TAG_USER_SETUP = "ModelScanUserSetupDialog"
        private const val TAG_DIRECT_CAMERA = "DirectScanCameraDialog"
        private const val BAKING_CANCEL_DELAY_MS = 100_000L
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openDirectCameraDialog()
        } else {
            Toast.makeText(requireContext(), "직접 촬영을 위해 카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private val videoFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            openDirectCameraDialog(initialVideoUri = uri.toString())
        }
    }

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

        hasShownUserSetup = false
        hasStartedUserSetupCountdown = false
        resetInputState()

        initViews()
        observeViewModel()
        observeUserSetupResult()

        // 화면 진입 시 기존 상태 초기화 후 신규 세션(QR + 새 PIN) 발급
        val tabletKey = com.tangoplus.tangoface.data.api.FaceScanApiClient.getTabletKey(requireContext())
        svm.onFragmentEnter(tabletKey)
    }

    private fun initViews() {
        // 우측 하단 영상 파일 업로드 버튼
        bd.btnUploadVideoFile.setOnClickListener {
            val state = svm.uiState.value
            if (state.status.uppercase() != "CREATED" || state.pairingCode.isBlank()) {
                Toast.makeText(requireContext(), "세션 PIN 발급이 완료된 후 이용할 수 있습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            videoFilePickerLauncher.launch("video/*")
        }

        // 우측 하단 직접 촬영 버튼
        bd.btnDirectCapture.setOnClickListener {
            val state = svm.uiState.value
            if (state.status.uppercase() != "CREATED" || state.pairingCode.isBlank()) {
                Toast.makeText(requireContext(), "세션 PIN 발급이 완료된 후 이용할 수 있습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val hasCam = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (hasCam) {
                openDirectCameraDialog()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // 저장된 3D GLB 모델 결과 보기 (IModelScanFragment로 이동)
        bd.btnViewScanResult.setOnClickListener {
            val savedSn = svm.uiState.value.savedTempServerSn ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    val dao = FaceDatabase.getDatabase(requireContext()).faceDao()
                    mainVm.convertToFaceResult(requireContext(), dao, savedSn)
                }
                mainVm.currentResult.value = result
                mainVm.loadUserDataFromDB(requireContext())
                (requireActivity() as? MainActivity)?.navigateToMeasureHistoryInformation(result.measureType)
            }
        }

        // 100초 경과 후 나타나는 대기 취소/초기화 버튼
        bd.btnCancelBaking.setOnClickListener {
            cancelBakingTimeout()
            cancelUserSetupCountdown()
            errorDialog?.dismiss()
            errorDialog = null
            (parentFragmentManager.findFragmentByTag(TAG_USER_SETUP) as? DialogFragment)?.dismissAllowingStateLoss()

            hasShownUserSetup = false
            hasStartedUserSetupCountdown = false
            resetInputState()
            GlbBakingLockManager.setAllowedTouchView(null)
            GlbBakingLockManager.setLocked(false)

            val tabletKey = com.tangoplus.tangoface.data.api.FaceScanApiClient.getTabletKey(requireContext())
            svm.onFragmentEnter(tabletKey)
            Toast.makeText(requireContext(), "3D 모델 생성 대기를 취소했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * UserSetupDialogFragment 입력 완료/건너뛰기 결과 수신 -> ScanViewModel 및 Room DB 저장
     */
    private fun observeUserSetupResult() {
        ivm.isFinishInput.observe(viewLifecycleOwner) { finished ->
            if (finished != true) return@observe

            val rawBirthday = ivm.birthdayValue.value.orEmpty()
            val sanitizedBirthday = if (rawBirthday == "날짜 선택") "" else rawBirthday
            val userInfo = ScanUserInfo(
                name = ivm.nameValue.value?.trim()?.takeIf { it.isNotEmpty() } ?: "GUEST",
                mobile = ivm.mobileValue.value.orEmpty(),
                birthday = sanitizedBirthday,
                gender = ivm.genderValue.value ?: -1,
                nationality = ivm.nationalityValue.value.orEmpty(),
                uuid = mvm.currentUUID.ifBlank { svm.uiState.value.sessionId }
            )
            svm.saveUserInfoAndPersistToDb(requireContext(), userInfo)
            ivm.isFinishInput.value = false
        }
    }

    private fun openDirectCameraDialog(initialVideoUri: String? = null) {
        if (!isAdded) return
        if (parentFragmentManager.findFragmentByTag(TAG_DIRECT_CAMERA) != null) return
        val code = svm.uiState.value.pairingCode
        if (code.isBlank()) return
        DirectScanCameraDialogFragment.newInstance(code, initialVideoUri)
            .show(parentFragmentManager, TAG_DIRECT_CAMERA)
    }

    private fun showUserSetupDialog() {
        if (!isAdded || hasShownUserSetup) return
        if (parentFragmentManager.findFragmentByTag(TAG_USER_SETUP) != null) return

        hasShownUserSetup = true
        resetInputState()
        UserSetupDialogFragment.newInstance(1).show(parentFragmentManager, TAG_USER_SETUP)
    }

    private fun resetInputState() {
        ivm.nameCondition.value = false
        ivm.nameValue.value = ""
        ivm.mobileCondition.value = false
        ivm.mobileValue.value = ""
        ivm.genderCondition.value = false
        ivm.genderValue.value = -1
        ivm.birthdayCondition.value = false
        ivm.birthdayValue.value = "날짜 선택"
        ivm.nationalityValue.value = ""
        ivm.isShownBtn = false
        ivm.isFinishInput.value = false
        mvm.currentUUID = ""
    }

    private fun startUserSetupCountdownIfNeeded() {
        if (hasShownUserSetup || hasStartedUserSetupCountdown) return
        hasStartedUserSetupCountdown = true

        userSetupDelayJob?.cancel()
        userSetupDelayJob = viewLifecycleOwner.lifecycleScope.launch {
            for (sec in 3 downTo 1) {
                _binding?.tvUserSetupHint?.apply {
                    isVisible = true
                    text = "${sec}초 뒤 측정자 정보 입력을 시작합니다..."
                }
                delay(1000L)
            }
            _binding?.tvUserSetupHint?.isVisible = false
            showUserSetupDialog()
        }
    }

    private fun cancelUserSetupCountdown() {
        userSetupDelayJob?.cancel()
        userSetupDelayJob = null
        _binding?.tvUserSetupHint?.isVisible = false
    }

    private fun syncBakingTimeoutButton(state: ScanUiState) {
        val upper = state.status.uppercase()
        if (upper != "UPLOADED" && upper != "PROCESSING") {
            cancelBakingTimeout()
            return
        }
        val startMs = state.bakingStartedElapsedMs ?: SystemClock.elapsedRealtime()
        val elapsedMs = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(0L)
        val remainingMs = (BAKING_CANCEL_DELAY_MS - elapsedMs).coerceAtLeast(0L)

        if (remainingMs == 0L) {
            showCancelBakingButton()
            return
        }
        if (bakingTimeoutJob?.isActive == true) return

        bd.btnCancelBaking.isVisible = false
        GlbBakingLockManager.setAllowedTouchView(null)
        bakingTimeoutJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(remainingMs)
            showCancelBakingButton()
        }
    }

    private fun showCancelBakingButton() {
        val binding = _binding ?: return
        binding.btnCancelBaking.isVisible = true
        GlbBakingLockManager.setAllowedTouchView(binding.btnCancelBaking)
    }

    private fun cancelBakingTimeout() {
        bakingTimeoutJob?.cancel()
        bakingTimeoutJob = null
        _binding?.btnCancelBaking?.isVisible = false
        GlbBakingLockManager.setAllowedTouchView(null)
    }

    /**
     * Lifecycle-aware (repeatOnLifecycle) 기반 실시간 UI 상태 수집
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                svm.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    /**
     * 세션 상태 및 3D 복원 진행률에 따른 화면 렌더링
     */
    private fun renderUiState(state: ScanUiState) {
        val upperStatus = state.status.uppercase()
        val showDirectActions = upperStatus in listOf("IDLE", "READY_TO_SCAN", "CREATING", "CREATED", "FAILED")
        val canDirectCapture = upperStatus == "CREATED" && state.pairingCode.isNotBlank()
        bd.llDirectScanActions.isVisible = showDirectActions
        bd.btnDirectCapture.isEnabled = canDirectCapture
        bd.btnUploadVideoFile.isEnabled = canDirectCapture
        bd.btnDirectCapture.alpha = if (canDirectCapture) 1f else 0.5f
        bd.btnUploadVideoFile.alpha = if (canDirectCapture) 1f else 0.5f

        syncBakingTimeoutButton(state)

        when (upperStatus) {
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
                bd.tvPairingSub.text = "스마트폰 화면에 위 6자리 번호를 입력해주세요\n모델링 서버 시동에 최대 5분이 소요됩니다\n모델 생성은 약 1분, 측정에 약 5분 소요예정입니다."
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

                bd.tvProgressStage.text = state.stageMessage.ifEmpty { "영상 업로드 완료! 3D 모델 생성을 준비 중입니다..." }
                bd.pbScanProgress.progress = 0
                bd.tvRemainingTime.text = "준비 중..."
            }

            "PROCESSING" -> {
                // 실시간 3D 복원 진행률 및 남은 시간 표시
                bd.clPairingContainer.isVisible = false
                bd.clProgressContainer.isVisible = true
                bd.clDoneContainer.isVisible = false

                bd.pbScanProgress.progress = state.progressPercent
                bd.tvRemainingTime.text = if (state.remainingSeconds > 0) {
                    "예상 남은 시간: 약 ${state.remainingSeconds}초"
                } else {
                    "잠시만 기다려주세요..."
                }
                bd.tvProgressStage.text = state.stageMessage.ifEmpty { "3D 얼굴 복원 진행 중..." }

                if (!state.isQueued && !state.isUserSetupCompleted) {
                    startUserSetupCountdownIfNeeded()
                }
            }

            "DONE" -> {
                // 복원 완료 안내 및 GLB 공용 폴더(Documents/TangoPlus/3DModels) 다운로드 -> Room DB 저장
                cancelUserSetupCountdown()
                bd.clPairingContainer.isVisible = false
                bd.clProgressContainer.isVisible = false
                bd.clDoneContainer.isVisible = true

                val downloadUrl = state.glbDownloadUrl
                val savedSn = state.savedTempServerSn

                if (state.isUserSetupCompleted && savedSn != null) {
                    val sizeKb = state.localGlbSizeBytes / 1024
                    bd.tvDoneTitle.text = "3D 얼굴 생성 완료!"
                    bd.tvDoneMessage.text = "3D 모델 저장이 완료되었습니다. (${sizeKb} KB)"
                    bd.btnViewScanResult.isVisible = true
                } else if (state.isUserSetupCompleted && state.localGlbFileName != null) {
                    bd.tvDoneTitle.text = "3D 얼굴 생성 완료!"
                    bd.tvDoneMessage.text = "3D 모델 파일을 저장하는 중입니다..."
                    bd.btnViewScanResult.isVisible = false
                } else if (!state.isUserSetupCompleted && state.localGlbFileName != null) {
                    bd.tvDoneTitle.text = "3D 얼굴 생성 완료!"
                    bd.tvDoneMessage.text = "사용자 정보를 입력해주세요."
                    bd.btnViewScanResult.isVisible = false
                    showUserSetupDialog()
                } else if (state.isDownloadingGlb) {
                    bd.tvDoneTitle.text = "3D 얼굴 생성 완료!"
                    bd.tvDoneMessage.text = "서버에서 파일을 내려받고 있습니다..."
                    bd.btnViewScanResult.isVisible = false
                } else if (downloadUrl != null && state.sessionId.isNotEmpty() && state.errorMessage == null) {
                    bd.tvDoneTitle.text = "3D 얼굴 생성 완료!"
                    bd.tvDoneMessage.text = "3D 얼굴 파일 다운로드를 시작합니다..."
                    bd.btnViewScanResult.isVisible = false
                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    val destFile = File(FileUtility.get3DModelsDir(), "scan_${timestamp}.glb")
                    svm.downloadGlbToLocal(requireContext(), state.sessionId, downloadUrl, destFile)
                } else {
                    bd.tvDoneTitle.text = "3D 얼굴 생성 완료!"
                    bd.tvDoneMessage.text = state.errorMessage ?: "3D 모델 복원이 완료되었습니다."
                    bd.btnViewScanResult.isVisible = false
                }
            }

            "FAILED" -> {
                cancelUserSetupCountdown()
                (parentFragmentManager.findFragmentByTag(TAG_USER_SETUP) as? DialogFragment)?.dismissAllowingStateLoss()

                bd.clPairingContainer.isVisible = true
                bd.clProgressContainer.isVisible = false
                bd.clDoneContainer.isVisible = false

                bd.pbPinLoading.isVisible = false
                bd.tvPairingCode.isVisible = false
                bd.tvPairingSub.text = state.errorMessage ?: "스캔에 실패했습니다. 다시 시도해주세요."

                state.errorMessage?.let { msg ->
                    showErrorDialog(msg)
                }
            }
        }
    }

    private fun showErrorDialog(message: String) {
        if (errorDialog?.isShowing == true) return

        errorDialog = AlertDialog.Builder(requireContext())
            .setTitle("3D 스캔 안내")
            .setMessage(message)
            .setPositiveButton("다시 시도") { dialog, _ ->
                dialog.dismiss()
                hasShownUserSetup = false
                hasStartedUserSetupCountdown = false
                resetInputState()
                val key = com.tangoplus.tangoface.data.api.FaceScanApiClient.getTabletKey(requireContext())
                svm.startNewScanFlow(delayMillis = 500L, tabletKey = key)
            }
            .setNegativeButton("닫기") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .create()
        errorDialog?.show()
    }

    override fun onDestroyView() {
        cancelBakingTimeout()
        cancelUserSetupCountdown()
        errorDialog?.dismiss()
        errorDialog = null
        (parentFragmentManager.findFragmentByTag(TAG_USER_SETUP) as? DialogFragment)?.dismissAllowingStateLoss()
        (parentFragmentManager.findFragmentByTag(TAG_DIRECT_CAMERA) as? DialogFragment)?.dismissAllowingStateLoss()

        if (activity?.isChangingConfigurations != true) {
            svm.resetAll()
            resetInputState()
        }
        GlbBakingLockManager.setAllowedTouchView(null)
        hasShownUserSetup = false
        hasStartedUserSetupCountdown = false
        _binding = null
        super.onDestroyView()
    }
}
