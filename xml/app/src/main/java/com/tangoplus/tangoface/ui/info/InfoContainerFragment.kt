package com.tangoplus.tangoface.ui.info

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.FragmentInfoContainerBinding
import com.tangoplus.tangoface.domain.vo.MeasureType
import com.tangoplus.tangoface.domain.vo.Sequence
import com.tangoplus.tangoface.ui.info.vm.InformationViewModel
import com.tangoplus.tangoface.ui.info.vm.SelectViewModel
import com.tangoplus.tangoface.ui.info.vo.FacialPart
import com.tangoplus.tangoface.ui.main.vm.MainViewModel
import kotlin.getValue


class InfoContainerFragment : Fragment() {
  private lateinit var bd : FragmentInfoContainerBinding
  private val svm: SelectViewModel by activityViewModels()
  private val ivm: InformationViewModel by activityViewModels()
  private val mvm: MainViewModel by activityViewModels()

  companion object {
    private const val ARG_M_TYPE = "MEASURE_TYPE"
    fun newInstance(measure_type: Int) : InfoContainerFragment {
      val fragment = InfoContainerFragment()
      val args = Bundle()
      args.putInt(ARG_M_TYPE, measure_type)
      fragment.arguments = args
      return fragment
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    bd = FragmentInfoContainerBinding.inflate(inflater)
    return bd.root
  }

  override fun onDestroy() {
    super.onDestroy()
    mvm.currentResult.value = null
  }
  override fun onDestroyView() {
    super.onDestroyView()
    svm.updateFacialPart(FacialPart.DERIVE)
    svm.updateSequence(Sequence.COMFORT)

    if (ivm.isAngleModeActive.value) ivm.toggleAngleMode()
    if (ivm.getMoireOn()) ivm.switchedMoireOn()

    if (!ivm.getLineOn()) ivm.switchedLineOn()
    if (!ivm.getGridLineOn()) ivm.switchedGridLineOn()

    ivm.setSeq(Sequence.COMFORT)
    ivm.releaseUnitIdx()
    ivm.clearScanResult()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val measureTypeNum = arguments?.getInt(ARG_M_TYPE) ?: 0
    if (measureTypeNum == MeasureType.SCAN.idx) {
      ivm.setScanResultFromFaceResult(mvm.currentResult.value)
    }

    val frg = when (measureTypeNum) {
      0 -> INormalFragment()
      1 -> IExpertFragment()
      3 -> IImageFragment()
      4 -> IModelScanFragment()
      else -> INormalFragment()
    }

    requireActivity().supportFragmentManager.beginTransaction().apply {
      replace(R.id.flIC, frg)
      commit()
    }
    val measureType = MeasureType.fromIdx(mvm.currentResult.value?.measureType)
    if (measureType != null) svm.setAllSeqs(measureType)
  }

}