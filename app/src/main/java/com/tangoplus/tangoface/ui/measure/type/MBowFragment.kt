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
import com.tangoplus.tangoface.databinding.FragmentMBowBinding
import com.tangoplus.tangoface.domain.util.AnimationUtility.startFadeIn
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.util.FileUtility.toFaceStatic
import com.tangoplus.tangoface.domain.util.FileUtility.toJsonString
import com.tangoplus.tangoface.domain.util.MeasureUtility.animationSet
import com.tangoplus.tangoface.domain.util.MeasureUtility.getGuideText
import com.tangoplus.tangoface.domain.util.MeasureUtility.showPreviousDialog
import com.tangoplus.tangoface.domain.util.PreferenceUtility
import com.tangoplus.tangoface.domain.util.UXUtility.playSound
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


class MBowFragment : Fragment() {
  private lateinit var bd : FragmentMBowBinding
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
          bd.clMBCountDown.visibility = View.VISIBLE
          bd.clMBCountDown.alpha = 1f
          bd.tvMBCountDown.text = "${(millisUntilFinished / 1000.0f).roundToInt()}"
          Log.v("count", "${bd.tvMBCountDown.text}")
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
          viewModel.setContourSave(true)

          hideViews()
          // 보우 측정 시 data가 3개 미만일 경우 혹은/ 1개의 y축 값이 튀었을 경우 다시 촬영
          val yValues = mvm.currentTarget.map { it.center.y.toFloat() }
          if (yValues.size < 3) {
            Log.d("보우측정", "데이터가 3개 미만 → 다시 촬영")
            Toast.makeText(requireContext(), "랜드마크가 3개 미만으로 추출됐습니다", Toast.LENGTH_LONG).show()
            isCountDown = false
            bd.btnMBShooting.apply {
              isEnabled = true
            }
            return
          } else {
            val maxY = yValues.maxOrNull() ?: 0f
            val minY = yValues.minOrNull() ?: 0f
            val diff = maxY - minY
            Log.v("diff 확인", "$diff")
            if (diff > 50f) {
              Log.d("보우측정", "Y값 차이 $diff → 튄 값 있음, 다시 촬영")
              Toast.makeText(requireContext(), "랜드마크가 페이스 보우와 다른 위치를 추출했습니다", Toast.LENGTH_LONG).show()
              isCountDown = false
              bd.btnMBShooting.apply {
                isEnabled = true
              }
              return
            }
          }
          playSound(R.raw.camera_shutter)

          lifecycleScope.launch {
            mvm.captureImage(mvm.currentSeq, requireActivity())
            mvm.face2DResult?.let { mvm.resultHsvToJson(it, mvm.currentTarget) }
            viewModel.finishSequence()
            updateUI()
          }
          setGuideAnimation(mvm.currentSeq, false)
          isCountDown = false
          bd.btnMBShooting.apply {
            isEnabled = true
          }
        }
      }
    }
  } //  ------! 카운트 다운 끝 !-------
  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    bd = FragmentMBowBinding.inflate(inflater)
    return bd.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    prefsUtil = PreferenceUtility(requireActivity())
    bd.btnMBShooting.setOnSingleClickListener {
      if (bd.btnMBShooting.text == "결과보기") {
        // ---------------# 데이터 갈무리해서 gallery DialogFragment로 넘기기 #-----------------
        // 각각의 static에 사용자 정보 넣기

        // GUEST일 경우 초기화
        if (ivm.birthdayValue.value == "날짜 선택") {
          ivm.birthdayValue.value = ""
        }

        lifecycleScope.launch(Dispatchers.IO) {
          val fd = FaceDatabase.getDatabase(requireActivity())
          fDao = fd.faceDao()
          // TODO hsvFLCoordinatesJA 와 StaticJA 갯수 맞춰서 1개 그냥 input 하면 됨.
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
              // TODO 여기다가 bow_landmark해서 넣기
              put("bow_landmark", mvm.hsvFLCoordinatesJA.optJSONArray(0))
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
            viewModel.comparisonDoubleItem = null
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

    bd.tvMBGyro.setOnSingleClickListener {
      val gyro = GyroDialogFragment()
      gyro.show(requireActivity().supportFragmentManager, "gyroDialogFragment")
    }
  }

  private fun hideViews() {
    bd.clMBCountDown.visibility = View.INVISIBLE

  }


  private fun setGuideAnimation(seq: Sequence, isEnded: Boolean) {
    bd.btnMBShooting.isEnabled = false
    bd.tvMBSeqGuide.apply {
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

  // ------! 타이머 control 시작 !------
  private fun startTimer() {
    // 시작 버튼 후 시작
    bd.btnMBShooting.isEnabled = false
    Log.v("startTimer", "${mvm.currentSeq} / ${mvm.allSeqs.size}")
    mCountDown.start()
    viewModel.setCountDownFlag(true)
    // ------! 타이머 control 끝 !------
  }

  private fun updateUI() {
    val isProcessing = mvm.moveToNextSeq()
    if (!isProcessing) {
      setGuideAnimation(mvm.currentSeq, true)
      bd.clMBCountDown.visibility = View.GONE
      bd.btnMBShooting.apply {
        text = "결과보기"
        isEnabled = true
      }

      mCountDown.cancel()
      Log.v("몇단계?", "Max repeats reached, stopping the loop")
    } else {
      setGuideAnimation(mvm.currentSeq, false)
      val seqCount = "${mvm.getSeqIndex() + 1}. ${mvm.currentSeq.displayName} / ${mvm.allSeqs.size}"
      bd.tvMBSeqCount.text = seqCount
      viewModel.setCountDownFlag(false)
      bd.ivMBFrame.visibility = View.VISIBLE
      startFadeIn(bd.ivMBFrame)
      bd.ivMBFrame.setImageDrawable(
        ContextCompat.getDrawable(requireContext(), R.drawable.drawable_seq_6)
      )
    }
    bd.btnMBShooting.apply {
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