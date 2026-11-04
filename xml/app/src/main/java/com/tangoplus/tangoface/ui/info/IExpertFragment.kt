package com.tangoplus.tangoface.ui.info

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.video.VideoSize
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.data.db.FaceDao
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.data.db.FaceInsight
import com.tangoplus.tangoface.data.db.FaceShape
import com.tangoplus.tangoface.data.db.FacialAxisShape
import com.tangoplus.tangoface.databinding.FragmentIExpertBinding
import com.tangoplus.tangoface.domain.util.AnimationUtility.startTypingAnimation
import com.tangoplus.tangoface.domain.util.BitmapUtility
import com.tangoplus.tangoface.domain.util.BitmapUtility.getCombineBitmap
import com.tangoplus.tangoface.domain.util.BitmapUtility.setSSIV
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.util.LLMUtility.getGeminiInsight
import com.tangoplus.tangoface.domain.util.MediaUtility.extractVideoCoordinates
import com.tangoplus.tangoface.domain.vision.face.FaceLandmarkResult.Companion.fromFaceCoordinates
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.orbbec.application.MyApplication
import com.tangoplus.tangoface.ui.adapter.FactorRVAdapter
import com.tangoplus.tangoface.ui.adapter.PartSelectRVAdapter
import com.tangoplus.tangoface.ui.adapter.SequenceSelectRVAdapter
import com.tangoplus.tangoface.ui.adapter.SurveyFactorRVAdapter
import com.tangoplus.tangoface.ui.adapter.SurveyRVAdapter
import com.tangoplus.tangoface.ui.adapter.TrajectoryRVAdapter
import com.tangoplus.tangoface.ui.info.vm.InformationViewModel
import com.tangoplus.tangoface.ui.info.vm.SelectViewModel
import com.tangoplus.tangoface.ui.info.vo.DrawLine
import com.tangoplus.tangoface.ui.info.vo.FacialPart
import com.tangoplus.tangoface.ui.info.vo.Factor
import com.tangoplus.tangoface.ui.listener.OnFacialPartClickListener
import com.tangoplus.tangoface.ui.listener.OnSequenceClickListener
import com.tangoplus.tangoface.ui.main.vm.MainViewModel
import com.tangoplus.tangoface.ui.view.OverlayView
import com.tangoplus.tangoface.ui.view.TextBoxOverlayView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.getValue

class IExpertFragment : Fragment(), OnFacialPartClickListener, OnSequenceClickListener  {
  private lateinit var bd : FragmentIExpertBinding
  private val svm: SelectViewModel by activityViewModels()
  private val ivm: InformationViewModel by activityViewModels()
  private val mvm: MainViewModel by activityViewModels()
  private lateinit var fDao: FaceDao
  private var simpleExoPlayer: SimpleExoPlayer? = null
  private var exoPlay: ImageButton? = null
  private var exoPause: ImageButton? = null
  private val onPlayerReady = MutableLiveData(false)
  private var playBackPosition = 0L

  override fun onDestroyView() {
    super.onDestroyView()

    // 1. 플레이어 완전 해제
    releasePlayer()
    bd.pvIE.player = null

    // 2. 고화질 비트맵 및 오버레이 정리
    bd.ssivIE.recycle()
    bd.ovIE.clear()

    // 3. 실행 중인 애니메이션/루프 Job 취소
    bounceJob?.cancel()
    typingJob?.cancel()
    bd.tvIEScrollHint.animate().cancel()

    // 4. Activity-scoped ViewModel 잔여 데이터 초기화
    ivm.clearExpertData()
    svm.updateFacialPart(FacialPart.DERIVE)
    svm.updateSequence(Sequence.COMFORT)
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    bd = FragmentIExpertBinding.inflate(inflater)
    return bd.root
  }
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val fd = FaceDatabase.getDatabase(requireContext())
    fDao = fd.faceDao()
    ivm.resetState(listOf(DrawLine.NECK, DrawLine.FACE, DrawLine.A_SAGGING_FACE, DrawLine.A_FACIAL_AXIS, DrawLine.A_HORIZON))
    setUserInfo()
    setResult()
    setRatioBtn()
    initAngleTool()
    setAdapter()
    setInsightBottomSheet()
    bd.tvIEClose.setOnSingleClickListener {
      val behavior = BottomSheetBehavior.from(bd.bsIE)
      behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    bd.tvIEBow.setOnSingleClickListener {
      val dialog = IBowSelectDialogFragment()
      dialog.show(requireActivity().supportFragmentManager, "")
    }
  }

  private fun setAdapter() {
    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val layoutCount = if (MyApplication.isTablet() && isLandscape) 3 else 6
    val gLayoutManager6 = GridLayoutManager(context, layoutCount)
    val sAdapter = SequenceSelectRVAdapter(svm)
    sAdapter.onSequenceClickListener = this@IExpertFragment
    bd.rvIE1.apply {
      adapter = sAdapter
      layoutManager = gLayoutManager6
    }

    val currentSeq = ivm.getSeq()
    if (currentSeq == Sequence.COMFORT) {
      val gLayoutManager5 = GridLayoutManager(context, 5)
      val pAdapter = PartSelectRVAdapter(svm)
      pAdapter.onFacialPartClickListener = this@IExpertFragment
      bd.rvIE2.apply {
        adapter = pAdapter
        layoutManager = gLayoutManager5
      }
      enableFaceSplitBtns()
    } else {
      disableFaceSplitBtns()
    }

    // 좌우
    when (currentSeq) {
      Sequence.JAW_LEFT_TILT, Sequence.JAW_RIGHT_TILT -> {
        val gLayoutManager2 = GridLayoutManager(context, 2)
        val tAdapter = TrajectoryRVAdapter(ivm)
        bd.rvIE3.apply {
          adapter = tAdapter
          layoutManager = gLayoutManager2
        }
      }
      else -> {
        val fAdapter = FactorRVAdapter(this@IExpertFragment, mvm.currentResult.value?.factors ?: Factor(
          FacialAxisShape.DEFAULT, FaceShape.DEFAULT, null,Pair(0f, 0f)))
        val fLayoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        bd.rvIE3.apply {
          adapter = fAdapter
          layoutManager = fLayoutManager
        }
      }
    }
    startScrollHintAnimation()
  }
  // 1. 터치 차단 리스너 정의
  private val touchDisabler = object : RecyclerView.SimpleOnItemTouchListener() {
    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean = true
  }

  // 2. 비활성화 (내부 아이템 클릭 + 스크롤 완전 차단 및 반투명)
  fun disableFaceSplitBtns() {
    bd.rvIE2.apply {
      alpha = 0.25f
      removeOnItemTouchListener(touchDisabler) // 중복 등록 방지
      addOnItemTouchListener(touchDisabler)
    }
  }

  // 3. 활성화 (원상 복구)
  fun enableFaceSplitBtns() {
    bd.rvIE2.apply {
      alpha = 1.0f
      removeOnItemTouchListener(touchDisabler)
    }
  }

  private fun setRatioBtn() {
    bd.btnIEClean.setOnSingleClickListener {
      ivm.switchedLineOn()
      bd.btnIEClean.text = if (ivm.getLineOn()) "🔍 가이드라인 끄기" else "🔍 가이드라인 켜기"
      setImage()
    }

    bd.btnIEGrid.setOnSingleClickListener {
      ivm.switchedGridLineOn()
      bd.btnIEGrid.text = if (ivm.getGridLineOn()) "ꖛ 그리드 끄기" else "ꖛ 그리드 켜기 "
      setImage()
    }
    bd.btnIEMoire.setOnSingleClickListener {
      ivm.switchedMoireOn()
      bd.btnIEMoire.text = if (ivm.getMoireOn()) "🏳️‍🌈 Moire 끄기" else "🏳️‍🌈 Moire 보기"
      setImage()
    }
  }

  private fun setResult() {
    val seq = ivm.getSeq()
    if (seq == Sequence.JAW_LEFT_TILT || seq == Sequence.JAW_RIGHT_TILT) {
      val faceStaticJson = mvm.currentResult.value?.results
      val seqList = mvm.currentResult.value?.seqs?.toList() ?: emptyList()

      val leftIdx = seqList.indexOf(Sequence.JAW_LEFT_TILT)
      val rightIdx = seqList.indexOf(Sequence.JAW_RIGHT_TILT)
      Log.v("영상좌표", "seq=$seq, idx=${seqList.indexOf(seq)}, left=$leftIdx, right=$rightIdx")
      ivm.videoCoordinates = faceStaticJson?.optJSONObject(seqList.indexOf(seq))?.let { extractVideoCoordinates(it) }

      ivm.coordinates1 =  faceStaticJson?.optJSONObject(leftIdx)?.let { extractVideoCoordinates(it) }
      ivm.coordinates2 =  faceStaticJson?.optJSONObject(rightIdx)?.let { extractVideoCoordinates(it) }
      ivm.leftTrajectory = ivm.coordinates1?.map { it[152] }
      ivm.rightTrajectory = ivm.coordinates2?.map { it[152] }
      setVideoUI()
      setPlayer()
    } else {
      bd.ssivIE.recycle()
      val width = bd.ssivIE.width
      val height = bd.ssivIE.height
      ivm.setCropRatio(width, height)
      setImageUI()
      setImage()
      releasePlayer()
    }

//    bd.rvIE2.apply {
//      layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
//    }
  }

  @SuppressLint("UseKtx")
  private fun setImage() {
    val currentSeq = ivm.getSeq()
    lifecycleScope.launch {
      mvm.currentResult.value?.let { result ->
        coroutineScope {
          val deferred = async { getCombineBitmap(this@IExpertFragment, result, currentSeq, ivm) }
          val awaitedResult = deferred.await()
          awaitedResult.first?.let { setSSIV(it, bd.ssivIE) }

          val drawInfos = awaitedResult.second
          val faceRect = awaitedResult.third
          setBoxInfos(drawInfos, faceRect)
          bd.ovIE.visibility = if (ivm.getLineOn()) View.VISIBLE else View.GONE
        }
      }
    }
  }



  private fun setUserInfo() {
    val name = mvm.currentResult.value?.userName ?: "GUEST"
    val mobile = mvm.currentResult.value?.userMobile ?: ""
    val transformMobile = if (mobile.length == 11 && mobile.startsWith("010")) {
      "${mobile.substring(0, 3)}-****-${mobile.substring(7)}"
    } else {
      ""
    }
    val info = "$name${if (mobile.length < 5) "" else " | $transformMobile"}"

    bd.tvIEInfo.visibility = View.VISIBLE
    bd.tvIEInfo.text = info
    bd.tvIEInfo.gravity = if (name == "GUEST") {
      Gravity.CENTER
    } else {
      Gravity.NO_GRAVITY
    }
  }

  private fun setBoxInfos(textInfos: List<BitmapUtility.TextBoxData>, faceRect : TextBoxOverlayView.FaceRect?) {
    val isTablet = MyApplication.isTablet()
    if (isTablet) bd.tbovIE.setTabletUI() else bd.tbovIE.setMobileUI()
    bd.tbovIE.setFaceRect(faceRect)
    bd.tbovIE.attachToImageView(bd.ssivIE)
    bd.tbovIE.setTextBoxes(textInfos)
  }

  private fun initAngleTool() {
    viewLifecycleOwner.lifecycleScope.launch {
      ivm.isAngleModeActive.collect { isActive ->
        if (isActive) {
          val savedScale = bd.ssivIE.scale
          val savedCenter = bd.ssivIE.center?.let { PointF(it.x, it.y) }

          Log.d("AngleDebug", "Saved - scale: $savedScale, center: $savedCenter")

          bd.ssivIE.apply {
            isPanEnabled = false
            isZoomEnabled = false
            isQuickScaleEnabled = false
          }

          bd.amvIE.visibility = View.VISIBLE
          bd.amvIE.post {
            if (savedCenter != null) {
              bd.ssivIE.setScaleAndCenter(savedScale, savedCenter)
            }

            bd.amvIE.postDelayed({
              bd.amvIE.resetSelectedLine()
            }, 50)
          }

        } else {
          bd.amvIE.visibility = View.GONE
          bd.ssivIE.apply {
            isPanEnabled = true
            isZoomEnabled = true
            isQuickScaleEnabled = true
          }
        }
      }
    }

    bd.btnIEAngle.setOnClickListener {
      ivm.toggleAngleMode()
      bd.btnIEAngle.text = if (bd.btnIEAngle.text.contains("끄기")) "📐 각도계산 켜기" else "📐 각도계산 끄기"
    }
  }

//  private val scrollHandler = Handler(Looper.getMainLooper())
//  private var scrollRunnable: Runnable? = null
private fun setInsightBottomSheet() {
  val behavior = BottomSheetBehavior.from(bd.bsIE)

  // 💡 1. 숨김 가능 옵션을 활성화해야 STATE_HIDDEN이 먹힙니다.
  behavior.isHideable = true
  behavior.isDraggable = false
  behavior.state = BottomSheetBehavior.STATE_HIDDEN
  behavior.peekHeight = 0

  // 💡 2. 초기 로딩 시 어두운 배경 뷰도 숨겨진 상태로 시작하게 만듭니다.
  bd.vIEim.visibility = View.GONE
  bd.vIEim.alpha = 0f

  bd.clIEHint.setOnSingleClickListener {
    if (behavior.state == BottomSheetBehavior.STATE_HIDDEN) {
      tryInsight()
      behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
  }

  behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
    override fun onStateChanged(bottomSheet: View, newState: Int) {
      if (newState == BottomSheetBehavior.STATE_HIDDEN) {
        bd.vIEim.visibility = View.GONE
      } else {
        bd.vIEim.visibility = View.VISIBLE
      }
    }

    override fun onSlide(bottomSheet: View, slideOffset: Float) {
      val alpha = slideOffset * 0.6f
      bd.vIEim.alpha = if (alpha >= 0) alpha else 0f
    }
  })

  // 어두운 영역을 터치하면 바텀시트 닫기
  bd.vIEim.setOnClickListener {
    behavior.state = BottomSheetBehavior.STATE_HIDDEN
  }
}
  private var typingJob: Job? = null
  private val typingList = listOf(
    "✨ Gemini에서 인사이트를 생성 중입니다.",
    "📉 인사이트 요청은 1분 당 15회까지 가능합니다. 당일 한도는 1500회입니다.",
    "🌐 인터넷 환경에 따라 5초~30초까지 소요될 수 있습니다.",
  )
  private fun startTypingLoop() {
    typingJob?.cancel()
    typingJob = lifecycleScope.launch {
      while (this.isActive) {
        typingList.forEachIndexed { idx, text ->
          startTypingAnimation(bd.tvIEGeminiShimmer, text, 45)
          delay(1000)
        }
      }
    }
  }

  private fun startGeminiShimmer() {
    bd.clIEShimmer.visibility = View.VISIBLE
    bd.clIEResult.visibility = View.GONE
    startTypingLoop()
  }
  private fun stopTypingLoop() {
    typingJob?.cancel()
    // bd.tvIGeminiShimmer.text = "" // 필요시 텍스트 초기화
  }
  private fun stopGeminiShimmer() {
    // 1. 애니메이션 시작
    bd.sflIE.animate()
      .alpha(0f)          // 투명도를 0으로 (흐릿하게)
      .scaleX(1.1f)       // 살짝 커지면서 사라지면 더 자연스러워요
      .scaleY(1.1f)
      .setDuration(600)   // 0.6초 동안 진행
      .withEndAction {
        bd.sflIE.stopShimmer()
        stopTypingLoop()
        bd.clIEShimmer.visibility = View.GONE
        bd.clIEResult.visibility = View.VISIBLE
      }
      .start()
  }
  //
  private fun tryInsight() {
    val currentResultValue = mvm.currentResult.value

    // 1. 데이터가 이미 있거나, 분석에 필요한 기본 정보가 없으면 중단
    if (!currentResultValue?.insight.isNullOrEmpty()) {
      bd.tvIEInsight.text = currentResultValue.insight ?: ""
      Log.v("gemini", "기존 기록이 있나요?\n${currentResultValue.insight}")
      stopGeminiShimmer()
      return
    }

    startGeminiShimmer()
    lifecycleScope.launch {
      try {
        val currentResults = currentResultValue?.results
        val serverSn = currentResultValue?.tempServerSn

        if (currentResults != null && currentResults.length() > 0 && serverSn != null) {
          Log.v("gemini호출", "Gemini API 요청 후 응답을 기다리고 있습니다")

          // 최소 대기 시간 타이머
          val minDelayTask = async { delay(7635L) } // 45 속도로 안내 메시지 최소 1번 송출

          // 2. 동시에 API 호출 및 DB 저장을 시작합니다.
          val apiTask = async(Dispatchers.IO) {
            val apiResponse = getGeminiInsight(currentResults) // API 호출
            Log.v("gemini", "gemini insight: $apiResponse")
            if (!apiResponse.isNullOrBlank()) {
              val geminiInsight = FaceInsight(
                server_sn = serverSn,
                insight = apiResponse
              )
              fDao.insertInsight(geminiInsight) // DB 저장
            }
            apiResponse // 결과값 반환
          }

          // 3. ✨ 여기서 두 작업이 "모두" 끝날 때까지 대기합니다. ✨
          minDelayTask.await()
          val resultString = apiTask.await()

          // 4. UI 업데이트 및 ViewModel 상태 동기화
          if (!resultString.isNullOrBlank()) {
            bd.tvIEInsight.text = resultString
            currentResultValue.insight = resultString
          } else {
            bd.tvIEInsight.text = "인사이트를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
            currentResultValue.insight = "인사이트를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
            throw Exception("Empty Insight")
          }

        } else {
          Toast.makeText(context, "AI 인사이트를 가져오는데 실패했습니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_LONG).show()
        }
      } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "AI 인사이트를 가져오는데 실패했습니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_LONG).show()
      } finally {
        // 4. 성공하든 실패하든 쉬머는 무조건 멈춰야 합니다.
        stopGeminiShimmer()
      }
    }
  }

  private var bounceJob: Job? = null

  private fun startScrollHintAnimation() {
    bounceJob?.cancel()
    bounceJob = lifecycleScope.launch {
      while (isActive) {
        // 1. 위로 부드럽게 슉! 올라가기 (Y축 -30px 이동)
        bd.tvIEScrollHint.animate()
          .translationY(-30f)
          .setDuration(400)
          .setInterpolator(DecelerateInterpolator())
          .withEndAction {
            // 2. 올라간 뒤, 통통 튀며 제자리(0f)로 떨어지기
            bd.tvIEScrollHint.animate()
              .translationY(0f)
              .setDuration(600)
              .setInterpolator(BounceInterpolator()) // 핵심: 통통 튀는 물리 효과
              .start()
          }
          .start()
        delay(3500)
      }
    }
  }

  private fun setVideoUI() {
    bd.llIEBtns.apply {
      alpha = 0.25f
    }
    val imageOverlayBtns = listOf(bd.btnIEMoire, bd.btnIEGrid, bd.btnIEAngle, bd.btnIEClean)
    imageOverlayBtns.forEach { btn -> btn.isEnabled = false }

    bd.ssivIE.apply {
      recycle()
      visibility = View.GONE
    }

    bd.clIEVideo.visibility = View.VISIBLE
    bd.clIEVideo.visibility = View.VISIBLE

    exoPlay = bd.pvIE.findViewById(R.id.btnPlay)
    exoPause = bd.pvIE.findViewById(R.id.btnPause)


    exoPlay?.visibility = View.GONE
    exoPause?.visibility = View.VISIBLE
    exoPlay?.setOnClickListener {
      if (wasEnded) {
        simpleExoPlayer?.seekTo(0) // 처음부터
        wasEnded = false
      }
      simpleExoPlayer?.play()
      exoPlay?.visibility = View.GONE
      exoPause?.visibility = View.VISIBLE

    }
    exoPause?.setOnClickListener {
      simpleExoPlayer?.pause()
      exoPlay?.visibility = View.VISIBLE
      exoPause?.visibility = View.GONE
    }

    bd.ovIE.clear()
  }

  private fun setImageUI() {
    bd.clIEImage.visibility = View.VISIBLE
    bd.clIEVideo.visibility = View.GONE
    bd.llIEBtns.apply {
      alpha = 1.0f
    }
    val imageOverlayBtns = listOf(bd.btnIEMoire, bd.btnIEGrid, bd.btnIEAngle, bd.btnIEClean)
    imageOverlayBtns.forEach { btn -> btn.isEnabled = true }
    bd.ssivIE.apply {
      visibility = View.VISIBLE
    }

  }

  private var wasEnded = false
  private fun setPlayer() {
    simpleExoPlayer = SimpleExoPlayer.Builder(requireContext()).build()
    bd.pvIE.player = simpleExoPlayer
    bd.pvIE.controllerShowTimeoutMs = 1100
    val seqList = mvm.currentResult.value?.seqs?.toList() ?: emptyList()
    val uriIdx = seqList.indexOf(ivm.getSeq())
    val leftUri = mvm.currentResult.value?.mediaUri?.getOrNull(uriIdx)
    val mediaItem = leftUri?.let { MediaItem.fromUri(it) }
    val mediaSource = mediaItem?.let {
      ProgressiveMediaSource.Factory(DefaultDataSourceFactory(requireContext()))
        .createMediaSource(it)
    }

    mediaSource.let {
      if (it != null) {
        simpleExoPlayer?.prepare(it)
      }
      // 저장된 위치로 정확하게 이동
      simpleExoPlayer?.seekTo(0)
      onPlayerReady.value = true
      simpleExoPlayer?.playWhenReady = true

    }
    simpleExoPlayer?.addListener(object : Player.Listener {
      override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        if (playbackState == Player.STATE_READY) {
          if (wasEnded) {
            bd.ovIE.clear()
          }
          wasEnded = false

        } else if (playbackState == Player.STATE_ENDED) {
          playBackPosition = 0
          wasEnded = true
          exoPlay?.visibility = View.VISIBLE
          exoPause?.visibility = View.GONE
        }
      }

      override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        if (isPlaying) {
          // 재생 시작 (처음이든 일시정지 후든)
          lifecycleScope.launch {
            val videoDuration = simpleExoPlayer?.duration ?: 0L
            while (simpleExoPlayer?.isPlaying == true) {
              updateOverlay(videoDuration)
              delay(24)
            }
          }
        }
      }

      override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        Log.d("PlayerDebug", "🔍 [onVideoSizeChanged] width: ${videoSize.width}, height: ${videoSize.height}, rotation: ${videoSize.unappliedRotationDegrees}")
      }
    })
  }
  private fun updateOverlay(videoDuration: Long) {
    val currentPosition = simpleExoPlayer?.currentPosition ?: 0L
    val totalFrames = ivm.videoCoordinates?.size ?: 0
    val frameIndex = ((currentPosition.toFloat() / videoDuration) * totalFrames).toInt()

    if (frameIndex in 0 until totalFrames) {
      val faceLandmarkResult = fromFaceCoordinates(ivm.videoCoordinates?.get(frameIndex))
      val point152 = ivm.videoCoordinates?.get(frameIndex)?.get(152)

      val videoFormat = simpleExoPlayer?.videoFormat
      var videoW = videoFormat?.width ?: 0
      var videoH = videoFormat?.height ?: 0
      val rotation = videoFormat?.rotationDegrees ?: 0

      if (rotation == 90 || rotation == 270) {
        val temp = videoW
        videoW = videoH
        videoH = temp
      }

      activity?.runOnUiThread {
        if (!isAdded) return@runOnUiThread
        bd.ovIE.setResults(
          faceLandmarkResult,
          videoW,
          videoH,
          OverlayView.RunningMode.VIDEO
        )
        bd.ovIE.addChinTrajectoryPoint(point152?.first ?: 0f, point152?.second ?: 0f)
        bd.ovIE.invalidate()
      }
    }
  }

  override fun onSequenceClick(seq: Sequence) {
    ivm.setSeq(seq)
    svm.updateFacialPart(FacialPart.DERIVE)
    svm.updateSequence(seq)

    ivm.currentCheckedLines.clear()
    when (ivm.getSeq()) {
      Sequence.OCCLUSAL -> {
        ivm.currentCheckedLines.addAll(
          listOf(
            DrawLine.A_HORIZON,
          )
        )
      }
      Sequence.JAW_OPENING -> {
        ivm.currentCheckedLines.addAll(
          listOf(
            DrawLine.A_LIPS,
            DrawLine.D_LIPS,
          )
        )
      }
      else -> {
        ivm.currentCheckedLines.addAll(
          listOf(
            DrawLine.NECK,
            DrawLine.FACE,
            DrawLine.A_SAGGING_FACE,
            DrawLine.A_FACIAL_AXIS,
            DrawLine.A_HORIZON,
            DrawLine.A_SHOULDER
          )
        )
      }
    }

    setResult()
    setAdapter()
    bd.rvIE2.adapter?.notifyDataSetChanged()
  }

  override fun onFacialPartClick(part: FacialPart) {
    ivm.currentCheckedLines.clear()
    Log.v("클릭드", "${part}")
    val drawLines = when (part) {
      FacialPart.DERIVE -> listOf(DrawLine.NECK, DrawLine.FACE, DrawLine.A_SAGGING_FACE, DrawLine.A_FACIAL_AXIS, DrawLine.A_HORIZON, DrawLine.A_SHOULDER)
      FacialPart.RATIO -> listOf(DrawLine.A_RATIO_HORIZON, DrawLine.A_RATIO_VERTI)
      FacialPart.TOP -> listOf(DrawLine.EYE,  DrawLine.EYEBROW,  DrawLine.L_CORNEA)
      FacialPart.MIDDLE -> listOf(DrawLine.NOSE,DrawLine.A_ALAR_ORAL, DrawLine.A_SIDE_HEAD, DrawLine.E_CHEEKBONE, )
      FacialPart.BOTTOM -> listOf(DrawLine.E_CHEEKS, DrawLine.A_CANTHUS_ORAL, DrawLine.MOUSE)
    }

    ivm.currentCheckedLines.addAll(drawLines)
    svm.updateFacialPart(part)
    if (ivm.isAngleModeActive.value) {
      ivm.toggleAngleMode()
      bd.btnIEAngle.text = "📐 각도계산 켜기"
    }
    bd.rvIE2.adapter?.notifyDataSetChanged()
    setImage()
  }
  private fun releasePlayer() {
    simpleExoPlayer?.let {
      it.stop()
      it.release()
    }
    simpleExoPlayer = null

  }



}