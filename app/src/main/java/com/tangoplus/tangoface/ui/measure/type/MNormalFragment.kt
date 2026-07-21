package com.tangoplus.tangoface.ui.measure.type

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
import com.tangoplus.tangoface.ui.statistics.GyroDialogFragment
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
                  val horizontalRatio = calculateHorizontalRatio(pairs, mvm.currentHairLine3DCoordinate)
                  if (mvm.staticJA.length() > 0) {
                    // 2. 첫 번째(인덱스 0) JSONObject를 꺼내서 값을 추가(또는 덮어쓰기)
                    val firstJsonObject = mvm.staticJA.getJSONObject(0)
                    firstJsonObject.put("horizontal_ratio", horizontalRatio)
                  } else {
                    // 3. 만약 배열이 비어있다면 새로 만들어서 첫 번째로 넣어줌
                    mvm.staticJA.put(JSONObject().apply {
                      put("horizontal_ratio", horizontalRatio)
                    })
                  }
//									Log.v("스태틱", "${mvm.staticJA}")
                }
              }
              mvm.currentHairLine3DCoordinate.clear()
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
          setPreviousStep()
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

          allSeqs.forEachIndexed { index, sequence ->
            val staticJSON = mvm.setJo(mvm.staticJA.getJSONObject(index), sequence, ivm, prefsUtil, true)
            staticJos.add(staticJSON)
//						Log.v("staticJSON", "$staticJSON")
          }
//					Log.v("staticJson까보기0", "${staticJos.size}")

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
          Log.v("mvm.plrJA", "${mvm.plrJA.length()}")
          val mergedJOBeforeFileName = mutableListOf<JSONObject>()
          for (i in 0 until staticJos.size) {
            val currentStatic = transStatics[i]
            val mergedJSON = JSONObject().apply {
              put("data", JSONObject(transStatics[i].toJsonString()))

              Log.v("LEN", "i=$i, coordinatesLen=${mvm.coordinatesJA.length()}, plrLen=${mvm.plrJA.length()}, mediaNames=${mvm.mediaFileNames.size}")
              put("face_landmark", mvm.coordinatesJA.optJSONArray(i))
              put("pose_landmark", mvm.plrJA.optJSONArray(i))

              if (i == 0) {
                put("hairline_landmark", mvm.hairLineJA.optJSONArray(i))
              }
            }
            mergedJOBeforeFileName.add(mergedJSON)
            Log.v("json저장 전", "seq: $i")
            val jsonPath = mvm.saveJsonToStorage(
              requireActivity(),
              mergedJSON,
              mvm.mediaFileNames[i].replace(".webp", "").replace(".mp4", "")
            )


            mergedJOBeforeFileName[i].put("json_file_name", jsonPath)
            mvm.mergedJA.put(mergedJOBeforeFileName[i])
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
    bd.tvMNGyro.setOnSingleClickListener {
      val gyro = GyroDialogFragment()
      gyro.show(requireActivity().supportFragmentManager, "gyroDialogFragment")
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
          com.tangoplus.tangoface.domain.vo.Sequence.COMFORT -> R.drawable.drawable_seq_0
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

    val ci =  mvm.getSeqIndex()
    mvm.tempStaticJo = null
    mvm.staticJA.remove(ci)
    mvm.coordinatesJA.remove(ci)
    mvm.plrJA.remove(ci)
    mvm.mediaFileNames.removeAt(ci)
    Log.v("그외STATIC삭제", "${mvm.tempStaticJo}, ${mvm.staticJA.length()}, ${mvm.coordinatesJA.length()}, ${mvm.plrJA.length()}, ${mvm.mediaFileNames}")
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
    val isProcessing = mvm.moveToNextSeq()
    if (!isProcessing) {
      setGuideAnimation(mvm.currentSeq, true)
      bd.clMNCountDown.visibility = View.GONE
      bd.btnMNShooting.apply {
        text = "결과보기"
        isEnabled = true
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
    }

    bd.btnMNShooting.apply {
      isEnabled = true
    }

    Handler(Looper.getMainLooper()).postDelayed({
      viewModel.finishSequence()
    }, 1500)
    isCountDown = false
//    bd.tvGoGallery.isEnabled = true
    Log.v("업데이트UI", "${mvm.plrJA.length()}")
  }


}