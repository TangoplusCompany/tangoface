package com.tangoplus.facebeautyexpert.ui.info

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.data.db.FaceDao
import com.tangoplus.facebeautyexpert.databinding.FragmentInfoContainerBinding
import com.tangoplus.facebeautyexpert.domain.vo.MeasureType
import com.tangoplus.facebeautyexpert.domain.vo.Sequence
import com.tangoplus.facebeautyexpert.ui.info.vm.InformationViewModel
import com.tangoplus.facebeautyexpert.ui.info.vm.SelectViewModel
import com.tangoplus.facebeautyexpert.ui.info.vo.FacialPart
import com.tangoplus.facebeautyexpert.ui.main.vm.MainViewModel
import kotlin.getValue


class InfoContainerFragment : Fragment() {
  private lateinit var bd : FragmentInfoContainerBinding
  private val svm: SelectViewModel by activityViewModels()
  private val ivm: InformationViewModel by activityViewModels()
  private val mvm: MainViewModel by activityViewModels()
  private lateinit var fDao: FaceDao

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
    mvm.comparisonDoubleItem = null
    ivm.currentCheckedLines.clear()
    svm.updateFacialPart(FacialPart.DERIVE)
    svm.updateSequence(Sequence.COMFORT)
    if (ivm.isAngleModeActive.value) ivm.toggleAngleMode()

    ivm.setSeq(Sequence.COMFORT)
    ivm.releaseUnitIdx()
    if (!ivm.getLineOn()) ivm.switchedLineOn()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val measureTypeNum = arguments?.getInt(ARG_M_TYPE) ?: 0
    val frg = when (measureTypeNum) {
      0 -> INormalFragment()
      1 -> IExpertFragment()
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