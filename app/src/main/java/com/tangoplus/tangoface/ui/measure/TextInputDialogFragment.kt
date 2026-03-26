package com.tangoplus.tangoface.ui.measure

import android.content.Context.INPUT_METHOD_SERVICE
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.tangoplus.tangoface.databinding.FragmentTextInputDialogBinding
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.ui.main.MainViewModel
import com.tangoplus.tangoface.ui.user.UserViewModel
import kotlin.getValue

class TextInputDialogFragment : DialogFragment() {
	private lateinit var bd: FragmentTextInputDialogBinding
	private val uvm: UserViewModel by activityViewModels()
	private val mvm: MainViewModel by activityViewModels()
	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentTextInputDialogBinding.inflate(inflater)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		setTextWatcher()
		bd.etTID.setText(uvm.getTempTitle())
		setClickListener()
		setKeyboard()
	}

	private fun setKeyboard() {
		bd.etTID.postDelayed({
			if (isAdded) { // Fragment가 Activity에 붙어 있는지 확인
				bd.etTID.requestFocus()
				val imm = requireActivity().getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
				bd.etTID.setSelection(bd.etTID.length())
				imm.showSoftInput(bd.etTID, InputMethodManager.SHOW_IMPLICIT)
			}
		}, 500)
	}

	private fun setTextWatcher() {
		bd.etTID.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				Log.v("텍스트워처", uvm.getTempTitle())
				uvm.setTempTitle(s.toString())
			}
		})
	}

	private fun setClickListener() {
		bd.btnTIDCancel.setOnSingleClickListener { dismiss() }
		bd.btnTIDConfirm.setOnSingleClickListener {
			when (uvm.textInputCase) {
				0 -> context?.let { it1 ->
					uvm.insertFavorite(it1) {
						mvm.loadFavoriteDataFromDB(it1)
					}
				}

				1 -> context?.let { it1 -> uvm.insertOrUpdateMemo(it1) }
			}
			dismiss()
		}
		bd.ibtnTIDClear.setOnSingleClickListener {
			bd.etTID.setText("")
		}
	}
}