package com.tangoplus.tangoface.ui.info

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.tangoplus.tangoface.data.db.FaceDao
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.databinding.FragmentIBowDialogBinding
import com.tangoplus.tangoface.domain.util.BitmapUtility
import com.tangoplus.tangoface.domain.util.BitmapUtility.getCombineBitmap
import com.tangoplus.tangoface.domain.util.BitmapUtility.setSSIV
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.orbbec.application.MyApplication
import com.tangoplus.tangoface.ui.info.vm.InformationViewModel
import com.tangoplus.tangoface.ui.info.vm.SelectViewModel
import com.tangoplus.tangoface.ui.info.vo.DrawLine
import com.tangoplus.tangoface.ui.main.vm.MainViewModel
import com.tangoplus.tangoface.ui.measure.vm.MeasureViewModel
import com.tangoplus.tangoface.ui.view.TextBoxOverlayView
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.getValue

class IBowDialogFragment : DialogFragment() {
    private lateinit var bd: FragmentIBowDialogBinding
    private val svm: SelectViewModel by activityViewModels()
    private val ivm: InformationViewModel by activityViewModels()
    private val mvm: MainViewModel by activityViewModels()

    private val msvm : MeasureViewModel by viewModels()
    private lateinit var fDao: FaceDao

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        bd = FragmentIBowDialogBinding.inflate(inflater)
        return bd.root
    }


    override fun onDestroyView() {
        super.onDestroyView()
        bd.ssivIB.recycle()
        mvm.currentBowResult.value = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setCanceledOnTouchOutside(false)
        val fd = FaceDatabase.getDatabase(requireContext())
        fDao = fd.faceDao()
        ivm.currentCheckedLines.addAll(listOf( DrawLine.A_BOW))

        setUserInfo()
        setResult()
        setRatioBtn()
        initAngleTool()


        bd.ibtnBDExit.setOnSingleClickListener {
            dismiss()
        }
    }


    private fun setRatioBtn() {
        bd.btnIBClean.setOnSingleClickListener {
            ivm.switchedLineOn()
            bd.btnIBClean.text = if (ivm.getLineOn()) "🔍 가이드라인 끄기" else "🔍 가이드라인 켜기"
            setImage()
        }

        bd.btnIBGrid.setOnSingleClickListener {
            ivm.switchedGridLineOn()
            bd.btnIBGrid.text = if (ivm.getGridLineOn()) "ꖛ 그리드 끄기" else "ꖛ 그리드 켜기 "
            setImage()
        }
    }

    private fun setResult() {
        bd.ssivIB.recycle()

        val width = bd.ssivIB.width
        val height = bd.ssivIB.height
        ivm.setCropRatio(width, height)

        setImage()
    }

    @SuppressLint("UseKtx")
    private fun setImage() {
        val currentSeq = Sequence.BOW
        lifecycleScope.launch {
            mvm.currentBowResult.value?.let { result ->
                coroutineScope {
                    val deferred = async { getCombineBitmap(this@IBowDialogFragment, result, currentSeq, ivm) }
                    val awaitedResult = deferred.await()
                    bd.ssivIB.setMinimumScaleType(2)
                    awaitedResult.first?.let { setSSIV(it, bd.ssivIB) }

                    val drawInfos = awaitedResult.second
                    val faceRect = awaitedResult.third
                    setBoxInfos(drawInfos, faceRect)
                    bd.tbovIN.visibility = if (ivm.getLineOn()) View.VISIBLE else View.GONE
                }
            }
        }
    }



    private fun setUserInfo() {
        Log.v("info", "${mvm.currentBowResult.value?.userName}, ${mvm.currentBowResult.value?.userMobile}}")
        val name = mvm.currentBowResult.value?.userName ?: "GUEST"
        val mobile = mvm.currentBowResult.value?.userMobile ?: ""
        val transformMobile = if (mobile.length == 11 && mobile.startsWith("010")) {
            "${mobile.substring(0, 3)}-****-${mobile.substring(7)}"
        } else {
            ""
        }
        val info = "$name${if (mobile.length < 5) "" else " | $transformMobile"}"

        bd.tvIBInfo.visibility = View.VISIBLE
        bd.tvIBInfo.text = info
        bd.tvIBInfo.gravity = if (name == "GUEST") {
            Gravity.CENTER
        } else {
            Gravity.NO_GRAVITY
        }
    }

    private fun setBoxInfos(textInfos: List<BitmapUtility.TextBoxData>, faceRect : TextBoxOverlayView.FaceRect?) {
        val isTablet = MyApplication.isTablet()
        if (isTablet) bd.tbovIN.setTabletUI() else bd.tbovIN.setMobileUI()
        bd.tbovIN.setFaceRect(faceRect)
        bd.tbovIN.attachToImageView(bd.ssivIB)
        bd.tbovIN.setTextBoxes(textInfos)
    }

    private fun initAngleTool() {
        viewLifecycleOwner.lifecycleScope.launch {
            ivm.isAngleModeActive.collect { isActive ->
                if (isActive) {
                    val savedScale = bd.ssivIB.scale
                    val savedCenter = bd.ssivIB.center?.let { PointF(it.x, it.y) }

                    Log.d("AngleDebug", "Saved - scale: $savedScale, center: $savedCenter")

                    bd.ssivIB.apply {
                        isPanEnabled = false
                        isZoomEnabled = false
                        isQuickScaleEnabled = false
                    }

                    bd.amvIB.visibility = View.VISIBLE
                    bd.amvIB.post {
                        if (savedCenter != null) {
                            bd.ssivIB.setScaleAndCenter(savedScale, savedCenter)
                        }

                        bd.amvIB.postDelayed({
                            bd.amvIB.resetSelectedLine()
                        }, 50)
                    }

                } else {
                    bd.amvIB.visibility = View.GONE
                    bd.ssivIB.apply {
                        isPanEnabled = true
                        isZoomEnabled = true
                        isQuickScaleEnabled = true
                    }
                }
            }
        }

        bd.btnIBAngle.setOnClickListener {
            ivm.toggleAngleMode()
            bd.btnIBAngle.text = if (bd.btnIBAngle.text.contains("끄기")) "📐 각도계산 켜기" else "📐 각도계산 끄기"
        }
    }

    override fun onStart() {
        super.onStart()

        dialog?.window?.let { window ->
            // 1. 기본 흰색/검은색 윈도우 배경을 투명하게 (커스텀 둥근 모서리 적용 시 필수)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            // 2. 단말기 화면 크기(px) 구하기
            val displayMetrics = requireContext().resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
//            val screenHeight = displayMetrics.heightPixels

            // 3. 원하는 퍼센트 설정 (예: 가로 85%, 세로 60%)
            val width = (screenWidth * 1.0).toInt()
            val height = ViewGroup.LayoutParams.WRAP_CONTENT// 💡 세로는 뷰 크기에 맞추려면 ViewGroup.LayoutParams.WRAP_CONTENT 사용

            // 4. 윈도우 크기 적용
            window.setLayout(width, height)
        }
    }

}