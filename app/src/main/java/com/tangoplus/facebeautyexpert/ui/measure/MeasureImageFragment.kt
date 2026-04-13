package com.tangoplus.facebeautyexpert.ui.measure

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.replace
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.databinding.FragmentMeasureImageBinding
import com.tangoplus.facebeautyexpert.domain.util.FileUtility
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.saveImageToInternalStorage
import com.tangoplus.facebeautyexpert.ui.adapter.MeasureImageRVAdapter
import com.tangoplus.facebeautyexpert.ui.measure.vm.MeasureImageViewModel
import com.tangoplus.facebeautyexpert.ui.measure.vm.MeasureViewModel
import com.yalantis.ucrop.UCrop
import java.io.File


class MeasureImageFragment : Fragment() {
	private lateinit var bd : FragmentMeasureImageBinding
	private val mivm: MeasureImageViewModel by viewModels()
	private val mvm: MeasureViewModel by viewModels()
	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentMeasureImageBinding.inflate(inflater)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		setAdapter()
		mivm.imagePaths.observe(viewLifecycleOwner) { paths ->
			// 여기서 어댑터에 paths 전달해서 이미지 2개 표시
			// 예: adapter.submitList(paths)
		}

	}

	private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
		uri?.let { sourceUri ->
			val tempFile = File(requireContext().cacheDir, "temp_crop_${System.currentTimeMillis()}.jpg")
			val destinationUri = Uri.fromFile(tempFile)

			val options = UCrop.Options().apply {
				setCompressionFormat(Bitmap.CompressFormat.WEBP)
				setCompressionQuality(100)
				setHideBottomControls(false)
				setFreeStyleCropEnabled(false)
				setToolbarTitle("측정 이미지 선택")
			}

			UCrop.of(sourceUri, destinationUri)
				.withAspectRatio(1920f, 1080f)
				.withOptions(options)
				.getIntent(requireContext())
				.let { uCropActivityResult.launch(it) }
		}
	}

	private val uCropActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			val resultUri = result.data?.let { UCrop.getOutput(it) }
			resultUri?.let { handleCroppedImage(it) }
		} else if (result.resultCode == UCrop.RESULT_ERROR) {
			val cropError = result.data?.let { UCrop.getError(it) }
			Toast.makeText(context, "크롭 실패: ${cropError?.message}", Toast.LENGTH_SHORT).show()
		}
	}

	private fun handleCroppedImage(croppedUri: Uri) {

		// 이전 대화에서 만든 16:9 패딩 유틸리티 사용 추천
		val processedUri = mivm.make169WithPadding(requireContext(), croppedUri)

		processedUri?.let { uri ->
			val fileName = "measure_${mivm.targetIndex}.jpg" // 인덱스별로 파일명 고정
			val finalPath = saveImageToInternalStorage(requireContext(), uri, fileName)

			finalPath?.let { path ->
				// VM에 경로 저장 (여기서 LiveData가 업데이트됨)
				mivm.setImage(mivm.targetIndex, path)
			}
		}
	}


	private fun setAdapter() {
		val adapter = MeasureImageRVAdapter(
			fragment = this,
			uris = mivm.imagePaths.value ?: listOf(null, null)
		) { position ->
			// [클릭 시 동작]
			mivm.targetIndex = position // 어떤 슬롯을 바꾸는지 VM에 저장
			pickImage.launch("image/*") // 갤러리 열기
		}

		bd.rvMI.apply {
			layoutManager = GridLayoutManager(context, 2)
			this.adapter = adapter
		}
	}
}