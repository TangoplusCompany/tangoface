package com.tangoplus.tangoface.ui.statistics

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
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.tangoplus.tangoface.databinding.FragmentPosterManageBinding
import com.tangoplus.tangoface.domain.util.AnimationUtility.setupGuideBanner
import com.tangoplus.tangoface.domain.util.FileUtility.saveImageToInternalStorage
import com.tangoplus.tangoface.domain.util.PreferenceUtility
import com.tangoplus.tangoface.orbbec.application.MyApplication
import com.tangoplus.tangoface.ui.adapter.PosterRVAdapter
import com.tangoplus.tangoface.ui.listener.OnPosterClickListener
import com.tangoplus.tangoface.ui.main.MainActivity
import com.tangoplus.tangoface.ui.statistics.vm.PosterViewModel
import com.tangoplus.tangoface.ui.statistics.vm.PosterViewModelFactory
import com.yalantis.ucrop.UCrop
import java.io.File

class PosterManageFragment : Fragment(), OnPosterClickListener {
	private lateinit var bd: FragmentPosterManageBinding
	private lateinit var pmAdapter: PosterRVAdapter

	// 팩토리를 통해 PreferenceUtility를 주입받는 ViewModel
	private val pvm: PosterViewModel by viewModels {
		PosterViewModelFactory(PreferenceUtility(requireContext()))
	}

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentPosterManageBinding.inflate(inflater, container, false)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// 초기 기본 포스터 설정 (기존 로직 유지)
		PreferenceUtility(requireContext()).initDefaultPosters(requireContext())

		setAdapter()
		observeViewModel()

		setupGuideBanner(
			bannerView = bd.guideBanner0.root,
			closeButton = bd.guideBanner0.btnCloseGuide,
			textView = bd.guideBanner0.tvGuideText,
			message = "슬라이드는 최대 6장 등록 가능합니다!\n슬라이드 번호대로 포스터를 수정해서 원하는 순서대로 메인 화면을 변경해보세요 🖼️", // 메인 전용 문구
			prefKey = "posterManageGuide"
		)
		setVelocity()
	}

	private fun setAdapter() {
		// 어댑터에 VM을 전달하고, 이미지 피커를 여는 람다를 넘김
		pmAdapter = PosterRVAdapter(fragment = this@PosterManageFragment, viewModel = pvm) {
			pickImage.launch("image/*")
		}.apply {
			onPosterClickListener = this@PosterManageFragment
		}

		bd.rvPM.apply {
			layoutManager = GridLayoutManager(context, if (MyApplication.isTablet()) 3 else 2)
			adapter = pmAdapter
		}
	}

	private fun observeViewModel() {
		// VM의 데이터가 변경될 때마다 UI 자동 갱신
		pvm.posterList.observe(viewLifecycleOwner) { list ->
			pmAdapter.notifyDataSetChanged()
			bd.tvPMCount.text = "${list.size} / ${pvm.MAX_COUNT}"
		}
	}

	// --- 이미지 처리 로직 ---

	private val uCropActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			val resultUri = result.data?.let { UCrop.getOutput(it) }
			resultUri?.let { handleCroppedImage(it) }
		} else if (result.resultCode == UCrop.RESULT_ERROR) {
			val cropError = result.data?.let { UCrop.getError(it) }
			Toast.makeText(context, "크롭 실패: ${cropError?.message}", Toast.LENGTH_SHORT).show()
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
				setToolbarTitle("포스터 영역 선택")
			}
			val ratio = (activity as MainActivity).getFlMainSize()?.let { (width, height) ->
				if (MyApplication.isTablet()) Pair(25.6f, 38.9f) else Pair(width.toFloat(), height.toFloat())
			} ?: Pair(1f, 1f)

			UCrop.of(sourceUri, destinationUri)
				.withAspectRatio(ratio.first, ratio.second)
				.withOptions(options)
				.getIntent(requireContext())
				.let { uCropActivityResult.launch(it) }
		}
	}

	private fun handleCroppedImage(croppedUri: Uri) {
		val fileName = "poster_${System.currentTimeMillis()}.jpg"
		// 내부 저장소에 영구 저장 후 경로 반환
		val finalPath = saveImageToInternalStorage(requireContext(), croppedUri, fileName)

		// 임시 파일은 바로 삭제
		File(croppedUri.path ?: "").delete()

		finalPath?.let { newPath ->
			// 이제 리스트를 직접 만지지 않고 VM에 알림!
			pvm.handleSavedPath(newPath)
		}
	}

	// --- OnPosterClickListener 구현 (VM에 위임) ---

	override fun onAddClick() {
		pvm.setTarget(-1) // 추가 모드 설정
		pickImage.launch("image/*")
	}

	override fun onEditClick(position: Int) {
		pvm.setTarget(position) // 수정 모드 설정
		pickImage.launch("image/*")
	}

	override fun onDeleteClick(position: Int) {
		// 삭제 로직도 VM이 알아서 파일 지우고 리스트 갱신하게 함
		pvm.deletePoster(position)
	}

	private fun setVelocity() {
		val prefs = PreferenceUtility(requireContext())
		val defaultVelocity = prefs.getSlideVelocity()
		bd.tvPMVelocity.text = defaultVelocity.toString()

		bd.tvPMMinus.setOnClickListener {
			updateVelocity(prefs, -0.5f)
		}

		bd.tvPMPlus.setOnClickListener {
			updateVelocity(prefs, 0.5f)
		}
	}

	private fun updateVelocity(prefs: PreferenceUtility, delta: Float) {
		val currentVelocity = prefs.getSlideVelocity()
		val newVelocity = (currentVelocity + delta).coerceIn(1.5f, 10.0f) // 범위 제한 예시

		if (newVelocity != currentVelocity) {
			prefs.setSlideVelocity(newVelocity)
			bd.tvPMVelocity.text = newVelocity.toString()
		}
	}


}