package com.tangoplus.tangoface.ui.measure

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tangoplus.tangoface.data.api.FaceScanRepository
import com.tangoplus.tangoface.data.api.FaceScanRepositoryImpl
import com.tangoplus.tangoface.databinding.DialogDirectScanCameraBinding
import com.tangoplus.tangoface.ui.measure.vm.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * 태블릿 일반 카메라 직접 3D 촬영/첨부 다이얼로그
 * - RN 웹 스캐너와 동일한 100° X-Z 수평 파노라마 가이드, Colab 서버 상태, 3:4 미리보기,
 *   로컬 저장 및 백엔드(pair -> presign -> PUT -> complete) 직접 업로드 지원
 */
class DirectScanCameraDialogFragment : DialogFragment(), SensorEventListener {

    private var _binding: DialogDirectScanCameraBinding? = null
    private val bd get() = _binding!!

    private val svm: ScanViewModel by activityViewModels()
    private val repository: FaceScanRepository = FaceScanRepositoryImpl()

    private var pairingCode: String = ""
    private var initialVideoUri: String? = null
    private var openedFromFileUpload: Boolean = false
    private var acquiredPairToken: String? = null

    // CameraX
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var canSwitchCamera: Boolean = false
    private var isRecording: Boolean = false
    private var isUploading: Boolean = false

    // 녹화 타이머 & 임시 파일
    private var timerJob: Job? = null
    private var recordSeconds: Int = 0
    private var tempVideoFile: File? = null
    private var isVideoSavedLocally: Boolean = false

    // 회전 센서(자이로) 상태
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private var hasRotationSensor: Boolean = false
    private val rotationMatrix = FloatArray(9)
    private var smoothHx: Float? = null
    private var smoothHy: Float? = null
    private var rawHeadingDeg: Float = 0f
    private var refHeadingDeg: Float = 0f

    companion object {
        private const val ARG_PAIRING_CODE = "PAIRING_CODE"
        private const val ARG_INITIAL_VIDEO_URI = "INITIAL_VIDEO_URI"
        private const val TARGET_SWEEP_DEGREES = 100f
        private const val SMOOTH_ALPHA = 0.4f
        private const val MAX_RECORD_SECONDS = 30
        private const val MAX_VIDEO_SIZE_BYTES = 50L * 1024L * 1024L

        fun newInstance(
            pairingCode: String,
            initialVideoUri: String? = null
        ): DirectScanCameraDialogFragment {
            return DirectScanCameraDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PAIRING_CODE, pairingCode)
                    if (!initialVideoUri.isNullOrBlank()) {
                        putString(ARG_INITIAL_VIDEO_URI, initialVideoUri)
                    }
                }
            }
        }
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            handlePickedVideoUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingCode = arguments?.getString(ARG_PAIRING_CODE).orEmpty()
        initialVideoUri = arguments?.getString(ARG_INITIAL_VIDEO_URI)
        openedFromFileUpload = !initialVideoUri.isNullOrBlank()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogDirectScanCameraBinding.inflate(inflater, container, false)
        isCancelable = false
        return bd.root
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        sensorManager?.unregisterListener(this)
        super.onPause()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initListeners()
        startWorkerHealthPolling()

        val startUri = initialVideoUri
        if (!startUri.isNullOrBlank()) {
            bd.tvDirectTitle.text = "영상 파일 업로드"
            bd.btnDirectGuide.isVisible = false
            bd.clDirectCameraContainer.isVisible = false
            bd.clDirectPreviewContainer.isVisible = true
            bd.btnRetakeVideo.text = "다른 영상 선택"
            handlePickedVideoUri(Uri.parse(startUri), dismissOnFail = true)
        } else {
            initSensor()
            initCamera()
        }
    }

    private fun initSensor() {
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        hasRotationSensor = rotationSensor != null
        if (!hasRotationSensor) {
            bd.tvSweepGuide.text = "센서 미지원 기기 — 좌에서 우로 천천히 5~15초간 촬영하세요"
        }
    }

    private fun initListeners() {
        bd.btnConfirmDirectGuide.setOnClickListener {
            bd.clDirectGuideOverlay.isVisible = false
        }

        bd.btnDirectGuide.setOnClickListener {
            bd.clDirectGuideOverlay.isVisible = true
        }

        bd.btnCloseDirectScan.setOnClickListener {
            if (isUploading) {
                Toast.makeText(requireContext(), "영상 전송 중에는 닫을 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isRecording) {
                activeRecording?.stop()
                isRecording = false
            }
            dismissAllowingStateLoss()
        }

        bd.btnRecordToggle.setOnClickListener {
            if (!isRecording) {
                startRecording()
            } else {
                stopRecording()
            }
        }

        bd.btnSwitchCamera.setOnClickListener {
            if (isRecording || !canSwitchCamera) return@setOnClickListener
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            bindCameraUseCases()
        }

        bd.btnPickVideoFile.setOnClickListener {
            if (isRecording) return@setOnClickListener
            pickVideoLauncher.launch("video/*")
        }

        bd.vvDirectPreview.setOnClickListener {
            if (bd.vvDirectPreview.isPlaying) {
                bd.vvDirectPreview.pause()
            } else {
                bd.vvDirectPreview.start()
            }
        }

        bd.btnRetakeVideo.setOnClickListener {
            if (isUploading) return@setOnClickListener
            if (openedFromFileUpload) {
                pickVideoLauncher.launch("video/*")
            } else {
                returnToCameraMode()
            }
        }

        bd.btnSaveLocalVideo.setOnClickListener {
            val file = tempVideoFile ?: return@setOnClickListener
            saveVideoToPublicMovies(file)
        }

        bd.btnUploadVideo.setOnClickListener {
            if (isUploading) return@setOnClickListener
            startUploadPipeline()
        }
    }

    private fun initCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            if (!isAdded || _binding == null) return@addListener
            val provider = try {
                future.get()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "카메라를 초기화할 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@addListener
            }
            cameraProvider = provider

            val hasBack = runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
            val hasFront = runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
            canSwitchCamera = hasBack && hasFront
            bd.btnSwitchCamera.isVisible = canSwitchCamera

            lensFacing = when {
                hasBack -> CameraSelector.LENS_FACING_BACK
                hasFront -> CameraSelector.LENS_FACING_FRONT
                else -> {
                    Toast.makeText(requireContext(), "사용 가능한 카메라가 없습니다.", Toast.LENGTH_SHORT).show()
                    return@addListener
                }
            }
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = bd.pvDirectCamera.surfaceProvider
        }

        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HD,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
            )
            .build()
        val capture = VideoCapture.withOutput(recorder)
        videoCapture = capture

        try {
            provider.unbindAll()
            provider.bindToLifecycle(viewLifecycleOwner, selector, preview, capture)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "카메라 바인딩 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRecording() {
        val capture = videoCapture ?: return
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val outFile = File(requireContext().cacheDir, "direct_scan_${timestamp}.mp4")
        deleteTempFile()
        tempVideoFile = outFile
        isVideoSavedLocally = false

        // 시작 시 현재 수평 방향각을 영점(0%)으로 캘리브레이션
        refHeadingDeg = rawHeadingDeg
        bd.sweepTrackView.setProgress(0f)
        if (hasRotationSensor) {
            bd.tvSweepGuide.text = "진행률 0% | 우측(+60°)으로 천천히 이동하세요"
        }

        val outputOptions = FileOutputOptions.Builder(outFile).build()
        isRecording = true
        updateRecordingControlsUi(recording = true)
        startRecordTimer()

        activeRecording = capture.output
            .prepareRecording(requireContext(), outputOptions)
            .start(ContextCompat.getMainExecutor(requireContext())) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    isRecording = false
                    stopRecordTimer()
                    if (_binding == null) return@start
                    updateRecordingControlsUi(recording = false)

                    if (event.hasError() || !outFile.exists() || outFile.length() == 0L) {
                        Toast.makeText(requireContext(), "영상 녹화를 완료하지 못했습니다.", Toast.LENGTH_SHORT).show()
                        deleteTempFile()
                    } else {
                        val sizeMb = outFile.length() / (1024f * 1024f)
                        showPreviewMode(outFile, recordSeconds.toFloat(), sizeMb)
                    }
                }
            }
    }

    private fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    private fun startRecordTimer() {
        timerJob?.cancel()
        recordSeconds = 0
        bd.tvRecordTimer.isVisible = true
        bd.tvRecordTimer.text = "● REC 00:00 / 00:$MAX_RECORD_SECONDS"
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive && isRecording) {
                delay(1000L)
                recordSeconds++
                val secStr = recordSeconds.toString().padStart(2, '0')
                _binding?.tvRecordTimer?.text = "● REC 00:$secStr / 00:$MAX_RECORD_SECONDS"
                if (recordSeconds >= MAX_RECORD_SECONDS) {
                    stopRecording()
                    break
                }
            }
        }
    }

    private fun stopRecordTimer() {
        timerJob?.cancel()
        timerJob = null
        _binding?.tvRecordTimer?.isVisible = false
    }

    private fun updateRecordingControlsUi(recording: Boolean) {
        bd.btnRecordToggle.text = if (recording) "■ 녹화 종료" else "● 녹화 시작"
        bd.btnSwitchCamera.isEnabled = !recording
        bd.btnPickVideoFile.isEnabled = !recording
        bd.btnSwitchCamera.alpha = if (recording) 0.4f else 1f
        bd.btnPickVideoFile.alpha = if (recording) 0.4f else 1f
    }

    private fun handlePickedVideoUri(uri: Uri, dismissOnFail: Boolean = false) {
        viewLifecycleOwner.lifecycleScope.launch {
            val copyResult = withContext(Dispatchers.IO) {
                copyAndValidatePickedVideo(uri)
            }
            copyResult.onSuccess { (file, durationSec, sizeMb) ->
                deleteTempFile()
                tempVideoFile = file
                isVideoSavedLocally = false
                showPreviewMode(file, durationSec, sizeMb)
            }.onFailure { err ->
                Toast.makeText(requireContext(), err.message ?: "영상 파일을 불러오지 못했습니다.", Toast.LENGTH_LONG).show()
                if (dismissOnFail && tempVideoFile == null) {
                    dismissAllowingStateLoss()
                }
            }
        }
    }

    private fun copyAndValidatePickedVideo(uri: Uri): Result<Triple<File, Float, Float>> {
        val ctx = context ?: return Result.failure(IllegalStateException("Context 없음"))
        val resolver = ctx.contentResolver
        val outFile = File(ctx.cacheDir, "picked_scan_${System.currentTimeMillis()}.mp4")

        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        } ?: return Result.failure(IllegalStateException("선택한 영상 파일을 읽을 수 없습니다."))

        val sizeBytes = outFile.length()
        if (sizeBytes <= 0L) {
            outFile.delete()
            return Result.failure(IllegalStateException("비어 있는 영상 파일입니다."))
        }
        if (sizeBytes > MAX_VIDEO_SIZE_BYTES) {
            outFile.delete()
            val mb = String.format("%.1f", sizeBytes / (1024f * 1024f))
            return Result.failure(IllegalArgumentException("파일 용량이 50MB를 초과했습니다 (${mb}MB)."))
        }

        val retriever = MediaMetadataRetriever()
        val durationSec = try {
            retriever.setDataSource(outFile.absolutePath)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            ms / 1000f
        } catch (e: Exception) {
            0f
        } finally {
            runCatching { retriever.release() }
        }

        if (durationSec > MAX_RECORD_SECONDS) {
            outFile.delete()
            return Result.failure(IllegalArgumentException("영상 길이가 30초를 초과했습니다 (${String.format("%.1f", durationSec)}초)."))
        }

        return Result.success(Triple(outFile, durationSec, sizeBytes / (1024f * 1024f)))
    }

    private fun showPreviewMode(videoFile: File, durationSec: Float, sizeMb: Float) {
        bd.clDirectCameraContainer.isVisible = false
        bd.clDirectPreviewContainer.isVisible = true
        bd.llUploadProgress.isVisible = false
        bd.btnSaveLocalVideo.isEnabled = true
        bd.btnSaveLocalVideo.text = "영상 저장하기"

        bd.tvPreviewMeta.text = String.format("영상 길이: %.1f초 · 파일 크기: %.1f MB", durationSec, sizeMb)

        bd.vvDirectPreview.setVideoPath(videoFile.absolutePath)
        bd.vvDirectPreview.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.start()
        }
    }

    private fun returnToCameraMode() {
        bd.vvDirectPreview.stopPlayback()
        deleteTempFile()
        bd.sweepTrackView.setProgress(0f)
        if (hasRotationSensor) {
            bd.tvSweepGuide.text = "얼굴 좌측(-60°)을 바라본 상태에서 촬영을 시작하세요"
        }
        bd.clDirectPreviewContainer.isVisible = false
        bd.clDirectCameraContainer.isVisible = true
    }

    private fun saveVideoToPublicMovies(videoFile: File) {
        if (isVideoSavedLocally) return
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/TangoPlus")
                    }
                    val resolver = requireContext().contentResolver
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext false
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(videoFile).use { input ->
                            input.copyTo(out)
                        }
                    } ?: return@withContext false
                    true
                } catch (e: Exception) {
                    false
                }
            }
            if (saved) {
                isVideoSavedLocally = true
                bd.btnSaveLocalVideo.isEnabled = false
                bd.btnSaveLocalVideo.text = "저장 완료 ✓"
                Toast.makeText(requireContext(), "Movies/TangoPlus 폴더에 저장되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "영상 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startUploadPipeline() {
        val file = tempVideoFile ?: return
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(requireContext(), "업로드할 영상 파일이 유효하지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        if (pairingCode.isBlank() && acquiredPairToken.isNullOrBlank()) {
            Toast.makeText(requireContext(), "세션 PIN이 준비되지 않았습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        isUploading = true
        setPreviewButtonsEnabled(false)
        bd.llUploadProgress.isVisible = true
        bd.pbUploadProgress.progress = 0
        bd.tvUploadPercent.text = "0%"

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1. 페어링 토큰 확보 (일회용 PIN이므로 재시도 시 기존 토큰 재사용)
                val token = acquiredPairToken ?: run {
                    bd.tvUploadStatus.text = "1/3 세션 연결 중..."
                    val pairRes = repository.pairSession(pairingCode)
                    val pairBody = pairRes.getOrElse { throw it }
                    acquiredPairToken = pairBody.pairToken
                    pairBody.pairToken
                }

                // 2. Signed Upload URL 발급
                bd.tvUploadStatus.text = "2/3 업로드 URL 발급 중..."
                val presignRes = repository.presignUpload(token).getOrElse { throw it }

                // 3. Storage 직접 PUT 전송 (진행률 업데이트)
                bd.tvUploadStatus.text = "3/3 영상 전송 중..."
                repository.uploadVideoToSignedUrl(presignRes.signedUrl, file) { pct ->
                    activity?.runOnUiThread {
                        _binding?.let { b ->
                            b.pbUploadProgress.progress = pct
                            b.tvUploadPercent.text = "$pct%"
                        }
                    }
                }.getOrElse { throw it }

                // 4. 업로드 완료 및 Colab 트리거
                bd.tvUploadStatus.text = "검증 및 3D 모델 생성 요청 중..."
                val completeRes = repository.completeUpload(token).getOrElse { throw it }

                val msg = if (completeRes.queueFull) {
                    "요청이 혼잡합니다. 대기열에 등록되어 잠시 후 자동 시작됩니다."
                } else {
                    "영상 전송 완료! 3D 모델 생성을 시작합니다."
                }
                svm.markDirectUploadCompleted()
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                dismissAllowingStateLoss()
            } catch (e: Exception) {
                isUploading = false
                setPreviewButtonsEnabled(true)
                bd.tvUploadStatus.text = e.message ?: "업로드 중 오류가 발생했습니다."
                Toast.makeText(requireContext(), e.message ?: "영상 전송에 실패했습니다.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setPreviewButtonsEnabled(enabled: Boolean) {
        bd.btnUploadVideo.isEnabled = enabled
        bd.btnRetakeVideo.isEnabled = enabled
        bd.btnSaveLocalVideo.isEnabled = enabled && !isVideoSavedLocally
        bd.btnUploadVideo.alpha = if (enabled) 1f else 0.5f
        bd.btnRetakeVideo.alpha = if (enabled) 1f else 0.5f
    }

    private fun startWorkerHealthPolling() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    if (!isUploading) {
                        val health = repository.checkWorkerHealth().getOrNull()
                        _binding?.tvWorkerHealthBadge?.let { badge ->
                            when {
                                health == null || !health.alive -> {
                                    badge.text = "● Colab 연결 안됨"
                                    badge.setTextColor(Color.parseColor("#FF5449"))
                                }
                                health.busy == true -> {
                                    val q = health.queue ?: 0
                                    badge.text = if (q > 0) "● Colab 작업 중 (대기 $q)" else "● Colab 작업 중"
                                    badge.setTextColor(Color.parseColor("#FF8794"))
                                }
                                else -> {
                                    badge.text = "● Colab 서버 대기 중"
                                    badge.setTextColor(Color.parseColor("#00FFCC"))
                                }
                            }
                        }
                    }
                    delay(10_000L)
                }
            }
        }
    }

    // ------------------ 회전 센서(X-Z 수평 투영 + EMA 스무딩) ------------------
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || _binding == null) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // 카메라 시선 벡터의 수평면 투영 (상하 피치 기울기 영향 배제)
        val sign = if (lensFacing == CameraSelector.LENS_FACING_FRONT) 1f else -1f
        val rawHx = sign * rotationMatrix[2]
        val rawHy = sign * rotationMatrix[5]
        val mag = hypot(rawHx, rawHy)
        if (mag < 0.05f) return

        val nx = rawHx / mag
        val ny = rawHy / mag
        val prevX = smoothHx
        val prevY = smoothHy
        val sx: Float
        val sy: Float
        if (prevX == null || prevY == null) {
            sx = nx
            sy = ny
        } else {
            sx = SMOOTH_ALPHA * nx + (1f - SMOOTH_ALPHA) * prevX
            sy = SMOOTH_ALPHA * ny + (1f - SMOOTH_ALPHA) * prevY
        }
        smoothHx = sx
        smoothHy = sy

        var deg = Math.toDegrees(atan2(sy.toDouble(), sx.toDouble())).toFloat()
        if (deg < 0f) deg += 360f
        rawHeadingDeg = deg

        if (isRecording) {
            var delta = (rawHeadingDeg - refHeadingDeg) % 360f
            if (delta < -180f) delta += 360f
            if (delta > 180f) delta -= 360f
            val ratio = (abs(delta) / TARGET_SWEEP_DEGREES).coerceIn(0f, 1f)
            val pct = (ratio * 100f).toInt()
            bd.sweepTrackView.setProgress(ratio)
            bd.tvSweepGuide.text = if (pct >= 100) {
                "100% 스캔 완료! 촬영 종료 버튼을 눌러주세요"
            } else {
                "진행률 $pct% | 우측(+60°)으로 천천히 이동하세요"
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun deleteTempFile() {
        tempVideoFile?.let { f ->
            if (f.exists()) f.delete()
        }
        tempVideoFile = null
    }

    override fun onDestroyView() {
        stopRecordTimer()
        activeRecording?.stop()
        activeRecording = null
        runCatching { cameraProvider?.unbindAll() }
        bd.vvDirectPreview.stopPlayback()
        deleteTempFile()
        _binding = null
        super.onDestroyView()
    }
}

