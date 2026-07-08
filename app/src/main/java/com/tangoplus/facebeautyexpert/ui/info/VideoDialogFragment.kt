package com.tangoplus.facebeautyexpert.ui.info

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.databinding.FragmentVideoDialogBinding

class VideoDialogFragment : DialogFragment() {
  private lateinit var bd : FragmentVideoDialogBinding

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    bd = FragmentVideoDialogBinding.inflate(inflater)
    return bd.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)


  }

}