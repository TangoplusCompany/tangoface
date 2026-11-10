package com.tangoplus.tangoface.ui.measure

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.data.db.FaceDao
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.data.db.FaceStatic.Companion.getCurrentDateTime
import com.tangoplus.tangoface.databinding.FragmentMExpertBinding
import com.tangoplus.tangoface.domain.util.AnimationUtility.startFadeIn
import com.tangoplus.tangoface.domain.util.ClassifyUtility.calculateHorizontalRatio
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.util.FileUtility.toFaceStatic
import com.tangoplus.tangoface.domain.util.FileUtility.toJsonString
import com.tangoplus.tangoface.domain.util.LandmarkUtility.calculateScreenX
import com.tangoplus.tangoface.domain.util.LandmarkUtility.calculateScreenY
import com.tangoplus.tangoface.domain.util.MeasureUtility.animationSet
import com.tangoplus.tangoface.domain.util.MeasureUtility.getGuideText
import com.tangoplus.tangoface.domain.util.MeasureUtility.showPreviousDialog
import com.tangoplus.tangoface.domain.util.PreferenceUtility
import com.tangoplus.tangoface.domain.util.UXUtility.playSound
import com.tangoplus.tangoface.domain.util.UXUtility.startLongVibrate
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.ui.main.MainActivity
import com.tangoplus.tangoface.ui.main.vm.MainViewModel
import com.tangoplus.tangoface.ui.measure.vm.InputViewModel
import com.tangoplus.tangoface.ui.measure.vm.MeasureViewModel
import com.tangoplus.tangoface.ui.settings.GyroDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.getValue
import kotlin.math.roundToInt

class MExpertFragment : Fragment() {
  private lateinit var bd : FragmentMExpertBinding
  private val mvm : MeasureViewModel by activityViewModels()
  private val viewModel: MainViewModel by activityViewModels()
  private val ivm : InputViewModel by activityViewModels()
  private var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
  private lateinit var fDao: FaceDao
  private lateinit var prefsUtil: PreferenceUtility
  private var isCountDown = false
  private var isJawOpeningSave = false

  private val mCountDown: CountDownTimer by lazy {
    object : CountDownTimer(3000, 1000) {
      @SuppressLint("SetTextI18n")
      override fun onTick(millisUntilFinished: Long) {
        requireActivity().runOnUiThread {
          bd.clMECountDown.visibility = View.VISIBLE
          bd.clMECountDown.alpha = 1f
          bd.tvMECountDown.text = "${(millisUntilFinished / 1000.0f).roundToInt()}"
          Log.v("count", "${bd.tvMECountDown.text}")
          if (millisUntilFinished > 500f) {
            playSound(R.raw.camera_countdown)
          }
        }
      }

      override fun onFinish() {
        Log.v("카운트 끝", "${mvm.face2DResult}, 현재 seq: ${mvm.currentSeq}")
        if (mvm.face2DResult?.landmarks?.isNotEmpty() == true) {
          timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
          hideViews()
          playSound(R.raw.camera_shutter)
          viewModel.startSequence()
          lifecycleScope.launch {
            viewModel.setContourSave(true)

            when (mvm.currentSeq) {
              Sequence.JAW_LEFT_TILT, Sequence.JAW_RIGHT_TILT -> {
                val tiltDrawable = if (mvm.currentSeq == Sequence.JAW_LEFT_TILT) R.drawable.drawable_seq_2 else R.drawable.drawable_seq_3
                setJawTiltGuideText(mvm.currentSeq)
                bd.ivMEFrame.setFramesAndStart(
                  drawableResIds = listOf(R.drawable.drawable_seq_1, tiltDrawable, R.drawable.drawable_seq_4, R.drawable.drawable_seq_23_done),
                  accentColor = ContextCompat.getColor(requireActivity(), R.color.accent),
                  onFinished = { } // bd.ivMEFrame.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.drawable_seq_1))
                )
                mvm.startVideoRecording(requireActivity()) {
//                  mvm.resultBundleToJson(mvm.currentSeq)
                  Log.v("동적 저장완료", "jawOpening: ${isJawOpeningSave},bitmap: ${mvm.maxJawOpeningBitmap}")
                  if (mvm.currentSeq == Sequence.JAW_RIGHT_TILT &&  !isJawOpeningSave && mvm.maxJawOpeningBitmap != null) {
                    requireActivity().lifecycleScope.launch {
//                      saveDebugBitmap(requireContext(), mvm.maxJawOpeningBitmap!!, "JAWOPENING")
                      mvm.processAndSaveFrameData(
                        Sequence.JAW_OPENING,
                        requireActivity(),
                        mvm.maxJawOpeningBitmap!!,
                        depthData = mvm.maxJawOpeningDepthData,
                        depthW = mvm.maxJawOpeningDepthW,
                        depthH = mvm.maxJawOpeningDepthH,
                        face2D = mvm.maxJawOpeningFace2D
                      )
                      mvm.resultBundleToJson(Sequence.JAW_OPENING, mvm.maxJawOpeningFace2D, mvm.maxJawOpening3D)
                    }
                    isJawOpeningSave = true
                  }
                  updateUI()
                }
              }

              else -> {
                mvm.startScan()
                playSound(R.raw.camera_shutter)
                lifecycleScope.launch {
                  viewModel.setContourSave(true) // 컨투어를 위한 shortArray 추출 및 저장
                  val success = mvm.captureImage(mvm.currentSeq, requireActivity())  // 여기서 saveMediaStorage까지 전부 완료
                  if (success) {
                    // hairline, pose 전부 끝난 시점
                    mvm.resultBundleToJson(mvm.currentSeq)
                    if (mvm.currentHairLine2DCoordinate.size == 7) {
                      val absoluteLandmarkPairs = mvm.face2DResult?.landmarks?.map {
                        Pair(calculateScreenX( it.x), calculateScreenY( it.y))
                      }?.toMutableList()
                      // 🕊️🕊️🕊️🕊️ 여기서 vertical 비율 넣기
                      absoluteLandmarkPairs?.let { pairs ->
                        val horizontalRatio = calculateHorizontalRatio(pairs, mvm.currentHairLine2DCoordinate)
                        val comfortJo = mvm.staticMap.getOrPut(Sequence.COMFORT) { JSONObject() }
                        comfortJo.put("horizontal_ratio", horizontalRatio)
                      }
                    }
                    mvm.currentHairLine3DCoordinate.clear()
                    startLongVibrate(requireContext())
                    // 이 블록 안의 코드는 애니메이션이 왕복을 마치고 완전히 멈췄을 때 실행됩니다.
                    viewModel.finishSequence()
                    updateUI()
                  }
                }
              }
            }
          }
        } else {
          Toast.makeText(requireContext(), "얼굴 인식에 문제가 발생했습니다. 다시 시도해주세요", Toast.LENGTH_SHORT).show()
          updateUI()
          setPreviousStep()
          mvm.stopScan()
        }
      }
    }
  } //  ------! 카운트 다운 끝 !-------


  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    bd = FragmentMExpertBinding.inflate(inflater)
    return bd.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    prefsUtil = PreferenceUtility(requireActivity())
    initFrameMarginControl()
    bd.btnMEShooting.setOnSingleClickListener {
      if (bd.btnMEShooting.text == "결과보기") {

        bd.btnMEShooting.isEnabled = false

        if (ivm.birthdayValue.value == "날짜 선택") {
          ivm.birthdayValue.value = ""
        }

        lifecycleScope.launch(Dispatchers.IO) {
          // 💡 [단계 2] 코루틴 내부 전체를 try-finally로 감쌉니다.
          try {
            val fd = FaceDatabase.getDatabase(requireActivity())
            fDao = fd.faceDao()

            Log.v("userSn설정잘됐는지", "is init? = ${mvm.currentUserSn}, ${mvm.allSeqs}")
            val allSeqs = mvm.allSeqs
            val staticJos = mutableListOf<JSONObject>()
            val usedSeqs = mutableListOf<Sequence>()   // staticJos와 같은 순서로 쌓을 시퀀스

            allSeqs.forEach { sequence ->
              val raw = mvm.staticMap[sequence] ?: return@forEach
              staticJos.add(mvm.setJo(raw, sequence, ivm, prefsUtil, true))
              usedSeqs.add(sequence)
            }

            val now = getCurrentDateTime()
            val transStatics = staticJos.map {
              it.toFaceStatic().copy(
                user_sn = mvm.currentUserSn,
                reg_date = now
              )
            }

            for (static in transStatics) {
              fDao.insertStatic(static)
            }

            val mergedJOBeforeFileName = mutableListOf<JSONObject>()
            for (i in 0 until staticJos.size) {
              val sequence = usedSeqs[i]
              val mergedJSON = JSONObject().apply {
                put("data", JSONObject(transStatics[i].toJsonString()))
                put("face_landmark", mvm.coordinatesMap[sequence])
                put("pose_landmark", mvm.plrMap[sequence])

                if (sequence == Sequence.COMFORT) {
                  put("hairline_landmark", mvm.hairLineMap[sequence])
                }
              }
              mergedJOBeforeFileName.add(mergedJSON)

              val fileName = mvm.mediaFileNameMap[sequence] ?: continue
              Log.v("저장매핑", "seq=$sequence, file=$fileName, " +
                "occ=${mergedJSON.optJSONObject("data")?.optDouble("occlusal_eye_horizontal_angle")}, " +
                "left=${mergedJSON.optJSONObject("data")?.optDouble("jaw_left_tilt_left_mandibular_distance")}")


              val jsonPath = mvm.saveJsonToStorage(
                requireActivity(),
                mergedJSON,
                fileName.replace(".webp", "").replace(".mp4", "")
              )

              mergedJSON.put("json_file_name", jsonPath)
              mvm.mergedJA.put(mergedJSON)
            }

            // 정상적으로 모든 헤비 연산이 끝난 시점
            withContext(Dispatchers.Main) {
              val lastTempServerSn = prefsUtil.getLastTempServerSn()
              prefsUtil.setNextTempServerSn()

              val intent = Intent(requireActivity(), MainActivity::class.java)
              intent.putExtra("serverSn", lastTempServerSn)
              startActivity(intent)
              requireActivity().finishAffinity()
            }

          } catch (e: Exception) {
            // 💡 연산 도중 에러(DB 풀, 용량 부족 등)가 났을 때 로그를 남깁니다.
            Log.e("ShootingClick", "❌ 데이터 처리 중 예외 발생: ${e.message}")
            e.printStackTrace()
          } finally {
            // 💡 [단계 3] 어떤 이유로든 작업이 종료되면 버튼 잠금을 해제합니다.
            // 성공 시엔 finishAffinity()로 죽지만, 실패 시 사용자가 재시도할 수 있어야 하므로 필수입니다.
            withContext(Dispatchers.Main) {
              bd.btnMEShooting.isEnabled = true
            }
          }
        }
      } else {
        if (!isCountDown) {
          startTimer()
        }
      }
    }

    bd.tvMEPrevious.setOnSingleClickListener {
      showPreviousDialog(requireActivity(), { setPreviousStep() })
    }
    bd.tvMEGuide.setOnSingleClickListener {
      val dialog  = JawGuideDialogFragment()
      dialog.show(requireActivity().supportFragmentManager, "")
//      val gyro = GyroDialogFragment()
//      gyro.show(requireActivity().supportFragmentManager, "")
    }
  }

  private fun hideViews() {
    bd.clMECountDown.visibility = View.INVISIBLE
  }

  private var lookUpAnimator: ValueAnimator? = null
  fun startLookUpAnimation() {
    val iv = bd.ivMEFrame
    val container = bd.llMELookUpContainer
    val arrow = bd.tvMELookUpArrow

    iv.post {
      // 1. 컨테이너(눈+화살표 전체)의 위치를 75%, 30% 지점으로 이동
      val startX = iv.x + (iv.width * 0.8f) - (container.width / 2f)
      val startY = iv.y + (iv.height * 0.25f)

      container.x = startX
      container.y = startY
      container.visibility = View.VISIBLE

      lookUpAnimator?.cancel()

      lookUpAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1500 // 1.5초
        repeatCount = ValueAnimator.INFINITE // 무한 반복

        addUpdateListener { animator ->
          val fraction = animator.animatedValue as Float

          // 2. 화살표(arrow)만 위로 이동
          arrow.translationY = -100f * fraction

          // 3. 화살표(arrow)만 투명도 조절
          arrow.alpha = when {
            fraction < 0.2f -> fraction / 0.2f
            fraction > 0.7f -> (1f - fraction) / 0.3f
            else -> 1f
          }
        }
      }
      lookUpAnimator?.start()
    }
  }

  fun stopLookUpAnimation() {
    lookUpAnimator?.cancel()
    bd.llMELookUpContainer.visibility = View.INVISIBLE
  }
  private fun setPreviousStep() {
    if (mvm.currentSeq != Sequence.JAW_RIGHT_TILT) mvm.moveToPreviousSeq()

    // -----------# frame -> fdgv 변경하기 #--------
    Log.v("이전seq", "${mvm.progressSeqs}/${mvm.currentSeq} : ${mvm.allSeqs}")
    bd.ivMEFrame.setImageDrawable(
      ContextCompat.getDrawable(
        requireActivity(),
        when (mvm.currentSeq) {
          Sequence.COMFORT -> R.drawable.drawable_seq_0
          Sequence.JAW_LEFT_TILT, Sequence.JAW_RIGHT_TILT -> R.drawable.drawable_seq_1
          Sequence.NECK_EXTENTION -> R.drawable.drawable_seq_6
          else -> R.drawable.drawable_seq_6
        }
      )
    )
    bd.clMECountDown.visibility = View.GONE
    mCountDown.cancel()
    setGuideAnimation(mvm.currentSeq, false)

    bd.btnMEShooting.apply {
      text = "촬영"
      isEnabled = true
    }
    bd.tvMEPrevious.apply {
      visibility = if (mvm.currentSeq == mvm.allSeqs[0]) View.INVISIBLE else View.VISIBLE
      isEnabled = true
    }

    val seqCount = "${mvm.getSeqIndex() + 1}. ${mvm.currentSeq.displayName}"
    bd.tvMESeqCount.text = seqCount
    viewModel.setCountDownFlag(false)

    val ci =  mvm.getSeqIndex()
    val seqsToRemove = when (mvm.currentSeq) {
      Sequence.JAW_LEFT_TILT -> listOf(
        Sequence.JAW_LEFT_TILT, Sequence.OCCLUSAL, Sequence.JAW_OPENING
      )
      else -> listOf(mvm.currentSeq)
    }

    if (mvm.currentSeq == Sequence.JAW_LEFT_TILT) {
      mvm.isOcclusalSaved = false
    }

    seqsToRemove.forEach { seq ->
      mvm.staticMap.remove(seq)
      mvm.coordinatesMap.remove(seq)
      mvm.plrMap.remove(seq)
      mvm.hairLineMap.remove(seq)
      mvm.mediaFileNameMap.remove(seq)
    }

    Log.v("STATIC삭제", "삭제=$seqsToRemove, 남은키=${mvm.staticMap.keys}")
    isCountDown = false

  }

  private fun setGuideAnimation(seq: Sequence, isEnded: Boolean) {
    bd.btnMEShooting.isEnabled = false
    bd.tvMEPrevious.isEnabled = false
    bd.tvMESeqGuide.apply {
      clearAnimation()
      text = if (isEnded) {
        "수고하셨습니다\n버튼을 눌러 결과를 확인해보세요"
      } else {
        getGuideText(seq)
      }
      visibility = View.VISIBLE
      animation = animationSet
    }
  }

  private val guideHandler = Handler(Looper.getMainLooper())
  private val guideRunnables = mutableListOf<Runnable>()

  // ------! 타이머 control 시작 !------
  private fun startTimer() {
    // 시작 버튼 후 시작
    guideHandler.postDelayed({stopLookUpAnimation()}, 1000)
    bd.btnMEShooting.isEnabled = false
    bd.tvMEPrevious.isEnabled = false
    Log.v("startTimer", "${mvm.currentSeq} / ${mvm.allSeqs.size}")
    mCountDown.start()
    viewModel.setCountDownFlag(true)
    // ------! 타이머 control 끝 !------
  }

  private fun updateUI() {
    mvm.stopScan()
    val isProcessing = mvm.moveToNextSeq()
    if (!isProcessing) {
      setGuideAnimation(mvm.currentSeq, true)
      bd.clMECountDown.visibility = View.GONE
      bd.btnMEShooting.apply {
        text = "결과보기"
        isEnabled = true
        width = 200
        height = 120
        background = ContextCompat.getDrawable(context, R.drawable.color_gradation_accent_64)
      }

      mCountDown.cancel()
      Log.v("몇단계?", "Max repeats reached, stopping the loop")
    } else {
      setGuideAnimation(mvm.currentSeq, false)

      val seqCount = "${mvm.getSeqIndex() + 1}. ${mvm.currentSeq.displayName} / ${mvm.progressSeqs.size}"
      bd.tvMESeqCount.text = seqCount
      viewModel.setCountDownFlag(false)
      bd.ivMEFrame.visibility = View.VISIBLE
      startFadeIn(bd.ivMEFrame)

      if (mvm.currentSeq == Sequence.JAW_LEFT_TILT) {
        lifecycleScope.launch {
          delay(750)
          val dialog  = JawGuideDialogFragment()
          dialog.show(requireActivity().supportFragmentManager, "")
        }
      }
      when (mvm.currentSeq) {
        Sequence.JAW_LEFT_TILT, Sequence.JAW_RIGHT_TILT -> bd.ivMEFrame.setImageDrawable(
          ContextCompat.getDrawable(
            requireActivity(),
            R.drawable.drawable_seq_1
          )
        )
        Sequence.COMFORT -> bd.ivMEFrame.setImageDrawable(
          ContextCompat.getDrawable(
            requireActivity(),
            R.drawable.drawable_seq_0
          )
        )
//        Sequence.OCCLUSAL -> bd.ivMEFrame.setImageDrawable(
//          ContextCompat.getDrawable(
//            requireActivity(),
//            R.drawable.drawable_seq_1
//          )
//        )
        Sequence.NECK_EXTENTION -> {
          bd.ivMEFrame.setImageDrawable(
            ContextCompat.getDrawable(requireContext(), R.drawable.drawable_seq_6)
          )
          startLookUpAnimation()
        }
        else -> {}
      }
      bd.btnMEShooting.apply {
        width = 150
        height = 150
        background = ContextCompat.getDrawable(context, R.drawable.btn_camera)
      }
    }

    bd.btnMEShooting.apply {
      isEnabled = true
    }
    bd.tvMEPrevious.apply {
      visibility = View.VISIBLE
      isEnabled = true
    }
    Handler(Looper.getMainLooper()).postDelayed({
      viewModel.finishSequence()
    }, 1500)
    isCountDown = false
//    bd.tvGoGallery.isEnabled = true
  }


  private fun setJawTiltGuideText(step: Sequence) {
    val guideText = when (step) {
      Sequence.JAW_LEFT_TILT -> "1. 이빨을 맞물리려고 유지한 상태로\n아랫턱을 왼쪽으로 끝까지 움직여주세요|2. 입을 최대한 벌려주세요|3. 벌린 입을 앙다물면\n턱 가측 이동 단계가 종료됩니다."
      Sequence.JAW_RIGHT_TILT -> "1. 이빨을 맞물리려고 유지한 상태로\n아랫턱을 오른쪽으로 끝까지 움직여주세요|2. 입을 최대한 벌려주세요|3. 벌린 입을 앙다물면\n턱 가측 이동 단계가 종료됩니다."
      else -> return
    }

    cancelJawTiltGuideText()
    val lines = guideText.split("|")
    var accumulatedDelay = 0L

    lines.forEachIndexed { index, line ->
      val currentDelay = 2000L
      val runnable = Runnable {
        bd.tvMESeqGuide.text = line
      }

      // 등록
      guideRunnables.add(runnable)
      guideHandler.postDelayed(runnable, accumulatedDelay)
      accumulatedDelay += currentDelay
    }
  }
  fun cancelJawTiltGuideText() {
    guideRunnables.forEach { runnable -> guideHandler.removeCallbacks(runnable) }
    guideRunnables.clear()
  }

  /**
   * 가이드 프레임(ivMEFrame) 상단 마진 조절 (0 ~ 120dp, 5단위)
   */
  private fun initFrameMarginControl() {
    val initialMargin = prefsUtil.getGuideFrameMarginTop(120)
    applyFrameMargin(initialMargin)

    bd.tvMEFrameMarginUp.setOnClickListener {
      val currentMargin = prefsUtil.getGuideFrameMarginTop(120)
      val newMargin = (currentMargin - 10).coerceAtLeast(0) // 위로 누르면 5단위 축소
      applyFrameMargin(newMargin)
      prefsUtil.setGuideFrameMarginTop(newMargin)
    }

    bd.tvMEFrameMarginDown.setOnClickListener {
      val currentMargin = prefsUtil.getGuideFrameMarginTop(120)
      val newMargin = (currentMargin + 10).coerceAtMost(480) // 아래로 누르면 5단위 증가 (max 120)
      applyFrameMargin(newMargin)
      prefsUtil.setGuideFrameMarginTop(newMargin)
    }
  }

  private fun applyFrameMargin(marginDp: Int) {
    val density = resources.displayMetrics.density
    val marginPx = (marginDp * density).toInt()
    val params = bd.ivMEFrame.layoutParams as? ViewGroup.MarginLayoutParams
    params?.let {
      it.topMargin = marginPx
      bd.ivMEFrame.layoutParams = it
    }
  }

}