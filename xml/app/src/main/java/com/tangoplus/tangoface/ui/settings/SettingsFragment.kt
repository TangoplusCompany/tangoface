package com.tangoplus.tangoface.ui.settings

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.databinding.FragmentSettingsBinding
import com.tangoplus.tangoface.ui.adapter.SettingsRVAdapter

class SettingsFragment : Fragment() {
    private lateinit var bd : FragmentSettingsBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View {
        bd = FragmentSettingsBinding.inflate(inflater)
        return bd.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setAdapter()

    }

    private fun setAdapter() {
        val lLayoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        val sAdapter = SettingsRVAdapter(this@SettingsFragment)
        bd.rvSettings.apply {
            layoutManager =  lLayoutManager
            adapter = sAdapter
        }
    }
}