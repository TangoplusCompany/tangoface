package com.tangoplus.tangoface.ui.info

import android.annotation.SuppressLint
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.data.db.FaceDao
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.data.db.FaceInsight
import com.tangoplus.tangoface.data.db.FaceShape
import com.tangoplus.tangoface.data.db.FacialAxisShape
import com.tangoplus.tangoface.databinding.FragmentIImageBinding
import com.tangoplus.tangoface.databinding.FragmentINormalBinding
import com.tangoplus.tangoface.domain.util.AnimationUtility.startTypingAnimation
import com.tangoplus.tangoface.domain.util.BitmapUtility
import com.tangoplus.tangoface.domain.util.BitmapUtility.getCombineBitmap
import com.tangoplus.tangoface.domain.util.BitmapUtility.setSSIV
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.util.LLMUtility.getGeminiInsight
import com.tangoplus.tangoface.domain.vo.MeasureType
import com.tangoplus.tangoface.orbbec.application.MyApplication
import com.tangoplus.tangoface.ui.adapter.FactorRVAdapter
import com.tangoplus.tangoface.ui.adapter.PartSelectRVAdapter
import com.tangoplus.tangoface.ui.info.vm.InformationViewModel
import com.tangoplus.tangoface.ui.info.vm.SelectViewModel
import com.tangoplus.tangoface.ui.info.vo.DrawLine
import com.tangoplus.tangoface.ui.info.vo.FacialPart
import com.tangoplus.tangoface.ui.info.vo.Factor
import com.tangoplus.tangoface.ui.listener.OnFacialPartClickListener
import com.tangoplus.tangoface.ui.main.vm.MainViewModel
import com.tangoplus.tangoface.ui.measure.vm.MeasureViewModel
import com.tangoplus.tangoface.ui.view.TextBoxOverlayView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.getValue

class IImageFragment : Fragment(), OnFacialPartClickListener {
    private lateinit var bd: FragmentIImageBinding
    private val svm: SelectViewModel by activityViewModels()
    private val ivm: InformationViewModel by activityViewModels()
    private val mvm: MainViewModel by activityViewModels()

    private val msvm : MeasureViewModel by viewModels()
    private lateinit var fDao: FaceDao
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        bd = FragmentIImageBinding.inflate(inflater)
        return bd.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bd.ssivII.recycle()
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
        observeViewModel()

        bd.tvIIClose.setOnSingleClickListener {
            val behavior = BottomSheetBehavior.from(bd.bsII)
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun setAdapter() {
        val vLayoutManager = GridLayoutManager(context, 5)
        val sAdapter = PartSelectRVAdapter(svm)
        sAdapter.onFacialPartClickListener = this@IImageFragment
        bd.rvII1.apply {
            adapter = sAdapter
            layoutManager = vLayoutManager
        }

        val fAdapter = FactorRVAdapter(this@IImageFragment, mvm.currentResult.value?.factors ?: Factor(
            FacialAxisShape.DEFAULT, FaceShape.DEFAULT, null,Pair(0f, 0f)))
        val fLayoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        bd.rvII2.apply {
            adapter = fAdapter
            layoutManager = fLayoutManager
        }
        startScrollHintAnimation()
    }
    private fun setRatioBtn() {
        bd.btnIIClean.setOnSingleClickListener {
            ivm.switchedLineOn()
            bd.btnIIClean.text = if (ivm.getLineOn()) "🔍 가이드라인 끄기" else "🔍 가이드라인 켜기"
            setImage()
        }
        bd.btnIIGrid.setOnSingleClickListener {
            ivm.switchedGridLineOn()
            bd.btnIIGrid.text = if (ivm.getGridLineOn()) "ꖛ 그리드 끄기" else "ꖛ 그리드 켜기 "
            setImage()
        }
    }

    private fun setResult() {
        bd.ssivII.recycle()

        val width = bd.ssivII.width
        val height = bd.ssivII.height
        ivm.setCropRatio(width, height)

        setImage()
        bd.rvII2.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        }
        setupSequenceDropdown()
    }
    @SuppressLint("UseKtx")
    private fun setImage() {
        val currentSeq = ivm.getSeq()
        lifecycleScope.launch {
            mvm.currentResult.value?.let { result ->
                coroutineScope {
                    val deferred = async { getCombineBitmap(this@IImageFragment, result, currentSeq, ivm) }
                    val awaitedResult = deferred.await()
                    bd.ssivII.setMinimumScaleType(1)
                    awaitedResult.first?.let { setSSIV(it, bd.ssivII) }

                    val drawInfos = awaitedResult.second
                    val faceRect = awaitedResult.third
                    setBoxInfos(drawInfos, faceRect)
                    bd.tbovII.visibility = if (ivm.getLineOn()) View.VISIBLE else View.GONE
                }
            }
        }
    }



    private fun setUserInfo() {
        Log.v("info", "${mvm.currentResult.value?.userName}, ${mvm.currentResult.value?.userMobile}}")
        val name = mvm.currentResult.value?.userName ?: "GUEST"
        val mobile = mvm.currentResult.value?.userMobile ?: ""
        val transformMobile = if (mobile.length == 11 && mobile.startsWith("010")) {
            "${mobile.substring(0, 3)}-****-${mobile.substring(7)}"
        } else {
            ""
        }
        val info = "$name${if (mobile.length < 5) "" else " | $transformMobile"}"

        bd.tvIIInfo.visibility = View.VISIBLE
        bd.tvIIInfo.text = info
        bd.tvIIInfo.gravity = if (name == "GUEST") {
            Gravity.CENTER
        } else {
            Gravity.NO_GRAVITY
        }
    }

    private fun setBoxInfos(textInfos: List<BitmapUtility.TextBoxData>, faceRect : TextBoxOverlayView.FaceRect?) {
        val isTablet = MyApplication.isTablet()
        if (isTablet) bd.tbovII.setTabletUI() else bd.tbovII.setMobileUI()
        bd.tbovII.setFaceRect(faceRect)
        bd.tbovII.attachToImageView(bd.ssivII)
        bd.tbovII.setTextBoxes(textInfos)
    }

    private fun initAngleTool() {
        viewLifecycleOwner.lifecycleScope.launch {
            ivm.isAngleModeActive.collect { isActive ->
                if (isActive) {
                    val savedScale = bd.ssivII.scale
                    val savedCenter = bd.ssivII.center?.let { PointF(it.x, it.y) }

                    Log.d("AngleDebug", "Saved - scale: $savedScale, center: $savedCenter")

                    bd.ssivII.apply {
                        isPanEnabled = false
                        isZoomEnabled = false
                        isQuickScaleEnabled = false
                    }

                    bd.amvII.visibility = View.VISIBLE
                    bd.amvII.post {
                        if (savedCenter != null) {
                            bd.ssivII.setScaleAndCenter(savedScale, savedCenter)
                        }

                        bd.amvII.postDelayed({
                            bd.amvII.resetSelectedLine()
                        }, 50)
                    }

                } else {
                    bd.amvII.visibility = View.GONE
                    bd.ssivII.apply {
                        isPanEnabled = true
                        isZoomEnabled = true
                        isQuickScaleEnabled = true
                    }
                }
            }
        }

        bd.btnIIAngle.setOnClickListener {
            ivm.toggleAngleMode()
            bd.btnIIAngle.text = if (bd.btnIIAngle.text.contains("끄기")) "📐 각도계산 켜기" else "📐 각도계산 끄기"
        }
    }


    override fun onFacialPartClick(part: FacialPart) {
        ivm.currentCheckedLines.clear()
        Log.v("클릭드", "${part}")
        val drawLines = when (part) {
            FacialPart.DERIVE -> listOf(DrawLine.NECK, DrawLine.FACE, DrawLine.A_SAGGING_FACE, DrawLine.A_FACIAL_AXIS, DrawLine.A_HORIZON,
                DrawLine.A_SHOULDER)
            FacialPart.RATIO -> listOf(DrawLine.A_RATIO_HORIZON, DrawLine.A_RATIO_VERTI)
            FacialPart.TOP -> listOf(DrawLine.EYE,  DrawLine.EYEBROW,  DrawLine.L_CORNEA)
            FacialPart.MIDDLE -> listOf(DrawLine.NOSE,DrawLine.A_ALAR_ORAL, DrawLine.A_SIDE_HEAD, DrawLine.E_CHEEKBONE, )
            FacialPart.BOTTOM -> listOf(DrawLine.E_CHEEKS, DrawLine.A_CANTHUS_ORAL, DrawLine.MOUSE)
        }

        // item 클릭 후 facialPart, Adapter전부 확인
        ivm.currentCheckedLines.addAll(drawLines)
        svm.updateFacialPart(part)
        if (ivm.isAngleModeActive.value) {
            ivm.toggleAngleMode()
            bd.btnIIAngle.text = "📐 각도계산 켜기"
        }
        bd.rvII1.adapter?.notifyDataSetChanged()
        setImage()
    }

    private fun setupSequenceDropdown() {
        // 1. 어댑터 설정 (표시할 이름들을 리스트로 추출)
        msvm.measureType = MeasureType.NORMAL
        val displayNames = msvm.progressSeqs.map { it.displayName }
        val adapter = ArrayAdapter(requireContext(), R.layout.list_item_dropdown, displayNames)
        bd.actvIISeq.setAdapter(adapter)

        // 2. 드롭다운 아이템 클릭 리스너
        bd.actvIISeq.setOnItemClickListener { _, _, position, _ ->
            val selectedSeq = svm.sequenceEntries[position]

            // ViewModel 업데이트h
            svm.updateSequence(selectedSeq)

            // 🕊️ 여기서 사진을 바꾸는 로직을 추가하시면 됩니다.
            ivm.setSeq(selectedSeq)
            setResult()
            bd.actvIISeq.clearFocus()
        }
        setAdapter()
        setInsightBottomSheet()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                svm.currentSequence.collect { seq ->
                    // 3. ViewModel 상태와 UI 텍스트 동기화 (필터링 방지를 위해 filter = false)
                    bd.actvIISeq.setText(seq.displayName, false)
                }
            }
        }
    }
    private val scrollHandler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null
    private fun setInsightBottomSheet() {
        val behavior = BottomSheetBehavior.from(bd.bsII)
        behavior.isDraggable = false
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        behavior.peekHeight = 0
        bd.clIIHint.setOnSingleClickListener {
            if (behavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                tryInsight()
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
//		bd.nsvI.setOnScrollChangeListener { _, _, scrollY, _, _ ->
//			scrollRunnable?.let { scrollHandler.removeCallbacks(it) }
//
//			val child = bd.nsvI.getChildAt(0)
//			if (child != null) {
//				val contentHeight = child.measuredHeight
//				val scrollViewHeight = bd.nsvI.measuredHeight
//
//				if (scrollY + scrollViewHeight >= contentHeight - 10) {
//					scrollRunnable = Runnable {
//						if (behavior.state == BottomSheetBehavior.STATE_HIDDEN) {
//							tryInsight()
//							behavior.state = BottomSheetBehavior.STATE_EXPANDED
//						}
//					}
//
//					scrollHandler.postDelayed(scrollRunnable!!, 200) // 200ms 대기시간
//				}
//			}
//		}

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    bd.vIIim.visibility = View.GONE
                } else {
                    bd.vIIim.visibility = View.VISIBLE
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val alpha = slideOffset * 0.6f
                bd.vIIim.alpha = if (alpha >= 0) alpha else 0f
            }
        })

// (선택사항) 어두운 영역을 터치하면 바텀시트 닫기
        bd.vIIim.setOnClickListener {
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
                    startTypingAnimation(bd.tvIIGeminiShimmer, text, 45)
                    delay(1000)
                }
            }
        }
    }

    private fun startGeminiShimmer() {
        bd.clIIShimmer.visibility = View.VISIBLE
        bd.clIIResult.visibility = View.GONE
        startTypingLoop()
    }
    private fun stopTypingLoop() {
        typingJob?.cancel()
        // bd.tvIGeminiShimmer.text = "" // 필요시 텍스트 초기화
    }
    private fun stopGeminiShimmer() {
        // 1. 애니메이션 시작
        bd.sflII.animate()
            .alpha(0f)          // 투명도를 0으로 (흐릿하게)
            .scaleX(1.1f)       // 살짝 커지면서 사라지면 더 자연스러워요
            .scaleY(1.1f)
            .setDuration(600)   // 0.6초 동안 진행
            .withEndAction {
                bd.sflII.stopShimmer()
                stopTypingLoop()
                bd.clIIShimmer.visibility = View.GONE
                bd.clIIResult.visibility = View.VISIBLE
            }
            .start()
    }
    //
    private fun tryInsight() {
        val currentResultValue = mvm.currentResult.value

        // 1. 데이터가 이미 있거나, 분석에 필요한 기본 정보가 없으면 중단
        if (!currentResultValue?.insight.isNullOrEmpty()) {
            bd.tvIIInsight.text = currentResultValue.insight ?: ""
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
                        bd.tvIIInsight.text = resultString
                        currentResultValue.insight = resultString
                    } else {
                        bd.tvIIInsight.text = "인사이트를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
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
                bd.tvINScrollHint.animate()
                    .translationY(-30f)
                    .setDuration(400)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        // 2. 올라간 뒤, 통통 튀며 제자리(0f)로 떨어지기
                        bd.tvINScrollHint.animate()
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
}