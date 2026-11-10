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
import androidx.lifecycle.lifecycleScope
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.data.db.FaceDao
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.data.db.FaceStatic.Companion.getCurrentDateTime
import com.tangoplus.tangoface.databinding.FragmentMNormalBinding
import com.tangoplus.tangoface.domain.util.AnimationUtility.startFadeIn
import com.tangoplus.tangoface.domain.util.ClassifyUtility.calculateHorizontalRatio
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.util.FileUtility.toFaceStatic
import com.tangoplus.tangoface.domain.util.FileUtility.toJsonString
import com.tangoplus.tangoface.domain.util.LandmarkUtility.calculateScreenX
import com.tangoplus.tangoface.domain.util.LandmarkUtility.calculateScreenY
import com.tangoplus.tangoface.domain.util.MeasureUtility.animationSet
import com.tangoplus.tangoface.domain.util.MeasureUtility.getGuideText
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.getValue
import kotlin.math.roundToInt

class MNormalFragment : Fragment() {
  private lateinit var bd : FragmentMNormalBinding
  private val mvm : MeasureViewModel by activityViewModels()
  private val viewModel: MainViewModel by activityViewModels()
  private val ivm : InputViewModel by activityViewModels()
  private var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
  private lateinit var fDao: FaceDao
  private lateinit var prefsUtil: PreferenceUtility
  private var isCountDown = false

  private val mCountDown: CountDownTimer by lazy {
    object : CountDownTimer(3000, 1000) {
      @SuppressLint("SetTextI18n")
      override fun onTick(millisUntilFinished: Long) {
        requireActivity().runOnUiThread {
          bd.clMNCountDown.visibility = View.VISIBLE
          bd.clMNCountDown.alpha = 1f
          bd.tvMNCountDown.text = "${(millisUntilFinished / 1000.0f).roundToInt()}"
          Log.v("count", "${bd.tvMNCountDown.text}")
          if (millisUntilFinished > 500f) {
            playSound(R.raw.camera_countdown)
          }
        }
      }

      override fun onFinish() {
        Log.v("카운트 끝", "${mvm.face2DResult}, 현재 seq: ${mvm.currentSeq}")
        if (mvm.face2DResult?.landmarks?.isNotEmpty() == true) {
          mvm.startScan()
          timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
          hideViews()
          playSound(R.raw.camera_shutter)

          lifecycleScope.launch {
            viewModel.setContourSave(true)

            val success = mvm.captureImage(mvm.currentSeq, requireActivity())  // 여기서 saveMediaStorage까지 전부 완료
            if (success) {
              // hairline, pose 전부 끝난 시점
              mvm.resultBundleToJson(mvm.currentSeq)
              if (mvm.currentHairLine3DCoordinate.size == 7) {
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
        } else {
          Toast.makeText(requireContext(), "얼굴 인식에 문제가 발생했습니다. 다시 시도해주세요", Toast.LENGTH_SHORT).show()
          updateUI()
          mvm.stopScan()
        }
      }
    }
  } //  ------! 카운트 다운 끝 !-------
  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    bd = FragmentMNormalBinding.inflate(inflater)
    return bd.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    prefsUtil = PreferenceUtility(requireContext())
    initFrameMarginControl()
    bd.btnMNShooting.setOnSingleClickListener {
      if (bd.btnMNShooting.text == "결과보기") {
        // ---------------# 데이터 갈무리해서 gallery DialogFragment로 넘기기 #-----------------
        // 각각의 static에 사용자 정보 넣기

        // GUEST일 경우 초기화
        if (ivm.birthdayValue.value == "날짜 선택") {
          ivm.birthdayValue.value = ""
        }

        lifecycleScope.launch(Dispatchers.IO) {
          val fd = FaceDatabase.getDatabase(requireContext())
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

          // 받아온 user_sn으로 수정
          val now = getCurrentDateTime()
          val transStatics = staticJos.map {
            it.toFaceStatic().copy(
              user_sn = mvm.currentUserSn,
              reg_date = now
            )
          }
          // 부여 받은 user_sn넣기
          for (static in transStatics) {
            fDao.insertStatic(static)
          }

          // data
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
            val jsonPath = mvm.saveJsonToStorage(
              requireActivity(),
              mergedJSON,
              fileName.replace(".webp", "").replace(".mp4", "")
            )

            mergedJSON.put("json_file_name", jsonPath)
            mvm.mergedJA.put(mergedJSON)
          }

          Log.v("mvm.mergedJson", "${mvm.mergedJA.length()}")

          withContext(Dispatchers.Main) {
            val lastTempServerSn = prefsUtil.getLastTempServerSn()
            // prefs에서 마지막 sn을 +1 하기
            prefsUtil.setNextTempServerSn()

//						Log.v("스태틱스Sn", "$lastTempServerSn, ${mvm.currentUserSn}: ${transStatics.map { it.user_sn }}")
            val intent = Intent(requireActivity(), MainActivity::class.java)
            intent.putExtra("serverSn", lastTempServerSn)
            startActivity(intent)
            requireActivity().finishAffinity()
          }
        }
      } else {
        if (!isCountDown) {
          startTimer()
        }
      }
    }
  }

  private fun hideViews() {
    bd.clMNCountDown.visibility = View.INVISIBLE

  }

  private var lookUpAnimator: ValueAnimator? = null
  fun startLookUpAnimation() {
    val iv = bd.ivMNFrame
    val container = bd.llMNLookUpContainer
    val arrow = bd.tvMNLookUpArrow

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
    bd.llMNLookUpContainer.visibility = View.INVISIBLE
  }
  private fun setPreviousStep() {
    mvm.moveToPreviousSeq()

    // -----------# frame -> fdgv 변경하기 #--------
    Log.v("이전seq", "${mvm.allSeqs}/${mvm.currentSeq}")
    bd.ivMNFrame.setImageDrawable(
      ContextCompat.getDrawable(
        requireActivity(),
        when (mvm.currentSeq) {
          Sequence.COMFORT -> R.drawable.drawable_seq_0
          else -> R.drawable.drawable_seq_6
        }
      )
    )
    bd.clMNCountDown.visibility = View.GONE
    mCountDown.cancel()
    setGuideAnimation(mvm.currentSeq, false)

    bd.btnMNShooting.apply {
      text = "촬영"
      isEnabled = true
    }
    viewModel.setCountDownFlag(false)

    val seq = mvm.currentSeq
    mvm.staticMap.remove(seq)
    mvm.coordinatesMap.remove(seq)
    mvm.plrMap.remove(seq)
    mvm.hairLineMap.remove(seq)
    mvm.mediaFileNameMap.remove(seq)
    Log.v("STATIC삭제", "삭제=$seq, 남은키=${mvm.staticMap.keys}")
    isCountDown = false
//    bd.tvGoGallery.isEnabled = true
  }

  private fun setGuideAnimation(seq: Sequence, isEnded: Boolean) {
    bd.btnMNShooting.isEnabled = false
    bd.tvMNSeqGuide.apply {
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
    bd.btnMNShooting.isEnabled = false
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
      bd.clMNCountDown.visibility = View.GONE
      bd.btnMNShooting.apply {
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
      val seqCount = "${mvm.getSeqIndex() + 1}. ${mvm.currentSeq.displayName} / ${mvm.allSeqs.size}"
      viewModel.setCountDownFlag(false)
      bd.ivMNFrame.visibility = View.VISIBLE
      startFadeIn(bd.ivMNFrame)
      bd.ivMNFrame.setImageDrawable(
        ContextCompat.getDrawable(requireContext(), R.drawable.drawable_seq_6)
      )
      startLookUpAnimation()
      bd.btnMNShooting.apply {
        width = 150
        height = 150
        background = ContextCompat.getDrawable(context, R.drawable.btn_camera)
      }
    }

    bd.btnMNShooting.apply {
      isEnabled = true
    }

    Handler(Looper.getMainLooper()).postDelayed({
      viewModel.finishSequence()
    }, 1500)
    isCountDown = false
  }

  /**
   * 가이드 프레임(ivMNFrame) 상단 마진 조절 (0 ~ 120dp, 5단위)
   */
  private fun initFrameMarginControl() {
    val initialMargin = prefsUtil.getGuideFrameMarginTop(120)
    applyFrameMargin(initialMargin)

    bd.tvMNFrameMarginUp.setOnClickListener {
      val currentMargin = prefsUtil.getGuideFrameMarginTop(120)
      val newMargin = (currentMargin - 10).coerceAtLeast(0) // 위로 누르면 5단위 축소
      applyFrameMargin(newMargin)
      prefsUtil.setGuideFrameMarginTop(newMargin)
    }

    bd.tvMNFrameMarginDown.setOnClickListener {
      val currentMargin = prefsUtil.getGuideFrameMarginTop(120)
      val newMargin = (currentMargin + 10).coerceAtMost(480) // 아래로 누르면 5단위 증가 (max 120)
      applyFrameMargin(newMargin)
      prefsUtil.setGuideFrameMarginTop(newMargin)
    }
  }

  private fun applyFrameMargin(marginDp: Int) {
    val density = resources.displayMetrics.density
    val marginPx = (marginDp * density).toInt()
    val params = bd.ivMNFrame.layoutParams as? ViewGroup.MarginLayoutParams
    params?.let {
      it.topMargin = marginPx
      bd.ivMNFrame.layoutParams = it
    }
  }
}