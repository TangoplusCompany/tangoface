package com.tangoplus.tangoface.ui.main

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.tangoplus.tangoface.data.db.FaceDao
import com.tangoplus.tangoface.databinding.FragmentMainBinding
import com.tangoplus.tangoface.ui.adapter.UserRVAdapter
import com.tangoplus.tangoface.ui.listener.OnMeasureClickListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.skydoves.balloon.ArrowPositionRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.showAlignBottom
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.data.db.FaceUser
import com.tangoplus.tangoface.domain.util.BitmapUtility.getScreenOrientation
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.util.UIUtility.dpToPx
import com.tangoplus.tangoface.domain.util.UIUtility.showInfoDF
import com.tangoplus.tangoface.ui.measure.DepthCameraActivity
import com.tangoplus.tangoface.ui.LoadingDialogFragment
import com.tangoplus.tangoface.ui.settings.SettingsFragment
import com.tangoplus.tangoface.ui.user.UserFragment
import com.tangoplus.tangoface.ui.adapter.GridRVAdapter
import com.tangoplus.tangoface.ui.listener.OnUserClickListener
import com.tangoplus.tangoface.ui.main.vo.MeasureGridItem
import com.tangoplus.tangoface.ui.main.vo.UserItem
import com.tangoplus.tangoface.ui.settings.SequenceSelectDialogFragment
import com.tangoplus.tangoface.ui.settings.vm.SequenceSelectViewModel
import com.tangoplus.tangoface.ui.user.UserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class MainFragment : Fragment(), OnUserClickListener, OnMeasureClickListener {
	private lateinit var bd: FragmentMainBinding
	private val mvm: MainViewModel by activityViewModels()
	private val uvm: UserViewModel by activityViewModels()
	private val ssvm: SequenceSelectViewModel by activityViewModels()
	private lateinit var fDao: FaceDao
	private lateinit var favoriteAdapter: GridRVAdapter
	private lateinit var loadingDialog: LoadingDialogFragment
	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentMainBinding.inflate(layoutInflater)
		return bd.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// 토글 관찰을 통해서 UI 변경
		mvm.toggleState.observe(viewLifecycleOwner) { isGuest ->
			if (isGuest) initGuestUI() else initUserUI()
		}
		// seq Select를 위한
		context?.let { ssvm.createPrefs(it) }
		bd.btnMainStart.setOnSingleClickListener {
			ssvm.fragmentCase = 0
			if (ssvm.getSelectedSeq().isEmpty()) {
				val dialog = SequenceSelectDialogFragment()
				activity?.supportFragmentManager?.let { manager ->
					dialog.show(manager, "SequenceSelect")
				}
				return@setOnSingleClickListener
			}
			checkStoragePermission()
		}
		bd.tvMSettings.setOnSingleClickListener {
			requireActivity().supportFragmentManager.beginTransaction().apply {
				replace(R.id.flMain, SettingsFragment())
				addToBackStack("SettingsFragment")
				commit()
			}
		}


		loadingDialog = LoadingDialogFragment()
		// 초기 셋업
		val fd = FaceDatabase.getDatabase(requireContext())
		fDao = fd.faceDao()

		mvm.allDataLoaded.observe(viewLifecycleOwner) { isLoaded ->
			if (isLoaded == true) {
//                Log.v("displayList", "${mvm.displayList.value}")
				cancelShimmer()
				val countText = """
                    등록된 이용자: ${mvm.currentUserItems.size} 명
                    게스트 측정 건수: ${mvm.getGuestCount()} 건
                """.trimIndent()
				bd.tvMCount.text = countText

				if (mvm.getToggleState()) {
					setGuestAdapter(mvm.currentGuestItems)
				} else {
					setUserAdapter(mvm.currentUserItems)
					setFavoriteAdapter()
				}

				mvm.clearLoadDataFlag()
				when (mvm.getToggleState()) {
					true -> {
						if (mvm.currentGuestItems.isEmpty()) {
							bd.ivMEmpty.visibility = View.VISIBLE
						} else {
							bd.ivMEmpty.visibility = View.GONE
						}
					}

					false -> {
						if (mvm.currentUserItems.isEmpty()) {
							bd.ivMEmpty.visibility = View.VISIBLE
						} else {
							bd.ivMEmpty.visibility = View.GONE
						}
					}
				}

				mvm.isMeasureFinish = activity?.intent?.getBooleanExtra("isMeasureFinish", false) ?: false
				mvm.tempServerSnAfterMeasure = activity?.intent?.getIntExtra("serverSn", -1) ?: -1

				// 측정이 완료되고 바로 결과를 볼 때
				if (mvm.isMeasureFinish && mvm.tempServerSnAfterMeasure != -1) {
					lifecycleScope.launch(Dispatchers.IO) {
						val statics = fDao.getStaticWithPlaceholders(mvm.tempServerSnAfterMeasure)
						withContext(Dispatchers.Main) {
							mvm.currentResult.value = mvm.convertToFaceResult(requireContext(), statics)
							activity?.let { ma -> showInfoDF(ma) }
							mvm.isMeasureFinish = false
							mvm.tempServerSnAfterMeasure = -1
							activity?.intent?.removeExtra("isMeasureFinish")
							activity?.intent?.removeExtra("serverSn")
						}
					}
				}
			}
		}

		mvm.currentFaceFavorites.observe(viewLifecycleOwner) { faceFavorites ->
			setFavoriteAdapter()
		}



		lifecycleScope.launch(Dispatchers.IO) {
			val userTableCount = fDao.getUserCount()

			if (userTableCount == 0) {
				migrateStaticToUserTable(fDao)
			}
			withContext(Dispatchers.Main) {
				mvm.toggleState.value?.let { isGuest ->
					if (isGuest) initGuestUI() else initUserUI()
					bd.msMFilter.isChecked = isGuest
				}
				clearComparison()
			}
		}
		setGuestToggle()
	}

	private fun checkStoragePermission() {
		val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			arrayOf(
				Manifest.permission.READ_MEDIA_IMAGES,
				Manifest.permission.READ_MEDIA_VIDEO
			)
		} else {
			arrayOf(
				Manifest.permission.READ_EXTERNAL_STORAGE,
				Manifest.permission.WRITE_EXTERNAL_STORAGE
			)
		}

		val allGranted = permissions.all {
			ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
		}

		if (allGranted) {
			navigateToDepthCamera()
		} else {
			storagePermissionLauncher.launch(permissions)
		}
	}

	private val storagePermissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestMultiplePermissions()
	) { permissions ->
		val allGranted = permissions.values.all { it }

		if (allGranted) {
			Log.d("MainFragment", "저장소 권한 허용됨")
			navigateToDepthCamera()
		} else {
			Log.e("MainFragment", "저장소 권한 거부됨")
			showPermissionDeniedDialog()
		}
	}

	private fun navigateToDepthCamera() {
		val intent = Intent(context, DepthCameraActivity::class.java)
		startActivity(intent)
		requireActivity().finishAffinity()
	}

	// 권한 거부 시 다이얼로그
	private fun showPermissionDeniedDialog() {

		MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
			.setTitle("저장소 권한 필요")
			.setMessage("앱을 사용하려면 저장소 권한이 필요합니다.\n설정에서 권한을 허용해주세요.")
			.setPositiveButton("설정으로 이동") { _, _ ->
				// 앱 설정 화면으로 이동
				val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
					data = Uri.fromParts("package", requireContext().packageName, null)
				}
				startActivity(intent)
			}
			.setNegativeButton("취소", null)
			.show()
	}

	private fun initUserUI() {
		// Room DB 로딩 시작
		mvm.loadUserDataFromDB(requireContext())
		mvm.loadFavoriteDataFromDB(requireContext())
		initShimmer()
		setSearchInput()

		collapseView(bd.rvM1)
		bd.rvM2.visibility = View.VISIBLE
		bd.tvMFavcoriteTitle.visibility = View.VISIBLE
		bd.clMComparison.visibility = View.GONE
	}

	private fun initGuestUI() {
		mvm.loadGuestDataFromDB(requireContext())
		setSearchInput()
		setComparisonClick()
		setObserveComparisonState()

		expandView(bd.rvM1)
		bd.rvM2.visibility = View.GONE
		bd.tvMFavcoriteTitle.visibility = View.GONE
		bd.clMComparison.visibility = View.VISIBLE
	}

	private fun setUserAdapter(data: List<UserItem>) {
		bd.rvM1.apply {
			val measureAdapter = UserRVAdapter(
				requireContext(), data, mvm
			)
			measureAdapter.userClickListener = this@MainFragment
			// 페이드 아웃 애니메이션
			animate()
				.alpha(0f)
				.setDuration(250)
				.setListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
						adapter = measureAdapter
						// 페이드 인 애니메이션
						animate()
							.alpha(1f)
							.setDuration(250)
							.setListener(null)
							.start()
					}
				})
				.start()
			layoutParams = bd.rvM1.layoutParams.apply {
				val so = requireActivity().getScreenOrientation()
				if (so.contains("landscape")) height = dpToPx(context, 365)
			}
		}
	}

	private fun setFavoriteAdapter() {
		val so = requireActivity().getScreenOrientation()
		val orientSpanCount = 2 // if (so.contains("landscape")) 2 else 1


		bd.rvM2.apply {
			favoriteAdapter = GridRVAdapter(
				this@MainFragment, mvm.currentFaceFavorites.value ?: listOf(), mvm, uvm, null, 0
			)
			favoriteAdapter.measureClickListener = this@MainFragment
			layoutManager = GridLayoutManager(requireContext(), orientSpanCount)
			adapter = favoriteAdapter
		}
	}

	private fun setGuestAdapter(data: List<MeasureGridItem>) {
		val so = requireActivity().getScreenOrientation()
		val orientSpanCount = 2 //if (so.contains("landscape")) 2 else 1
		bd.rvM1.apply {
			val measureAdapter = GridRVAdapter(
				this@MainFragment, data, mvm, uvm, null, 0
			)
			measureAdapter.measureClickListener = this@MainFragment
			animate()
				.alpha(0f)
				.setDuration(250)
				.setListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						layoutManager = GridLayoutManager(context, orientSpanCount)
						adapter = measureAdapter
						// 페이드 인 애니메이션
						animate()
							.alpha(1f)
							.setDuration(250)
							.setListener(null)
							.start()
					}
				})
				.start()
		}
	}

	private fun setGuestToggle() {
		bd.msMFilter.setOnCheckedChangeListener { _, isChecked ->
			mvm.setToggleState(isChecked)
			mvm.clearLoadDataFlag()
			clearComparison()
			bd.etMSearch.clearFocus()
			bd.etMSearch.setText("")
			val inputMethodManager =
				view?.context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
			inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
		}
	}

	private fun setSearchInput() {
		val hangulSyllableRegex = Regex("^[가-힣]+$")
		bd.etMSearch.hint = if (mvm.getToggleState()) "${
			LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
		}" else ""
		bd.etMSearch.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				clearComparison()
				val afterText = s.toString()

				if (mvm.getToggleState()) {
					val filteredResult: List<MeasureGridItem>

					if (afterText.isNotEmpty()) {
						when {
							// 숫자만 입력된 경우
							afterText.all { it.isDigit() } -> {
								val number = afterText.toIntOrNull()
								if (number != null) {
									filteredResult = mvm.currentGuestItems.filter {
										it.regDate.contains(afterText)
									}
									setGuestAdapter(filteredResult)
								}
							}

							else -> {}
						}
					} else {
						setGuestAdapter(mvm.currentGuestItems)
						return
					}
					// user 검색 input
				} else {
					val filteredResult: List<UserItem>
					if (afterText.isNotEmpty()) {
						when {
							// 숫자만 입력된 경우
							afterText.all { it.isDigit() } -> {
								val number = afterText.toIntOrNull()
								if (number != null) {
									filteredResult = mvm.currentUserItems.filter {
										it.userMobile?.contains(afterText) == true
									}
									setUserAdapter(filteredResult)
								}
							}

							hangulSyllableRegex.matches(afterText) -> {
								filteredResult = mvm.currentUserItems.filter {
									it.userName?.contains(afterText) == true
								}
								setUserAdapter(filteredResult)
							}

							else -> {}
						}
					} else {
						setUserAdapter(mvm.currentUserItems)
						return
					}
				}
			}
		})
	}

	// -------# 게스트일때 비교하는 항목 #-------
	private fun setComparisonClick() {
		bd.btnMComparison.setOnSingleClickListener {

			if (mvm.currentGuestItems.size < 2) {
				val balloon = Balloon.Builder(requireContext())
					.setWidth(BalloonSizeSpec.WRAP)
					.setHeight(BalloonSizeSpec.WRAP)
					.setText("측정 기록이 최소 2개 이상 있어야 합니다!\n우측 하단의 버튼을 눌러 측정 해보세요")
					.setTextColorResource(R.color.black)
					.setTextSize(16f)
					.setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
					.setArrowSize(0)
					.setMargin(16)
					.setPadding(12)
					.setCornerRadius(12f)
					.setBackgroundColorResource(R.color.white)
					.setBalloonAnimation(BalloonAnimation.OVERSHOOT)
					.setLifecycleOwner(viewLifecycleOwner)
					.setOnBalloonDismissListener { }
					.build()
				bd.btnMComparison.showAlignBottom(balloon)
				balloon.dismissWithDelay(3000L)
				balloon.setOnBalloonClickListener { balloon.dismiss() }
				return@setOnSingleClickListener
			}
//			Log.v("디버깅", "결과 보기 전 ${mvm.tempComparisonItems.value?.size}")
			if (mvm.tempComparisonItems.value?.size == 2 && bd.btnMComparison.text == "결과 보기") {
				val sortedTempItems = mvm.tempComparisonItems.value?.sortedBy { it.regDate }
//				Log.v("디버깅", "결과 보기 실행됨")

				// 왼쪽 오른쪽 찾아서 DOUBLE 만들기
				lifecycleScope.launch(Dispatchers.IO) {
					val leftStatics =
						sortedTempItems?.get(0)?.tempServerSn?.let { it1 -> fDao.getStaticWithPlaceholders(it1) }
					val rightStatics =
						sortedTempItems?.get(1)?.tempServerSn?.let { it1 -> fDao.getStaticWithPlaceholders(it1) }
//					Log.v("디버깅", "$leftStatics $rightStatics")

					withContext(Dispatchers.Main) {
						val leftResult =
							leftStatics?.let { it1 -> mvm.convertToFaceResult(requireContext(), it1) }
						val rightResult =
							rightStatics?.let { it1 -> mvm.convertToFaceResult(requireContext(), it1) }
						mvm.comparisonDoubleItem = Pair(leftResult!!, rightResult!!)
						activity?.let { fa -> showInfoDF(fa) }
					}
				}
				// 비교해제하면서 전부 끄기
				Handler(Looper.getMainLooper()).postDelayed(
					{
						mvm.setComparisonState(false)
						bd.etMSearch.setText("")
						clearComparison()
					}, 250
				)
				return@setOnSingleClickListener
			}

			if (mvm.getComparisonState()) {
				bd.btnMComparison.text = "아이템 선택"
//                bd.btnMComparison.isEnabled = true
				mvm.setComparisonState(false)
				clearComparison()
			} else {
				bd.btnMComparison.text = "취소"
//                bd.btnMComparison.isEnabled = false
				mvm.setComparisonState(true)

				// 변경 시작 할 때 observe 설정
				if (!mvm.tempComparisonItems.hasObservers()) {
					setObserveComparisonItems()
				}
			}
		}
	}

	private fun clearComparison() {
		mvm.clearItems()
		bd.btnMComparison.text = "아이템 선택"
		mvm.setComparisonState(false)
		mvm.tempComparisonItems.removeObservers(viewLifecycleOwner)
	}

	private fun setObserveComparisonState() {
		mvm.comparisonState.observe(viewLifecycleOwner) { isComparison ->
			if (!isComparison) {
				// 비교 모드가 false가 되면 초기화
				mvm.clearItems()
				bd.btnMComparison.text = "아이템 선택"
				resetAllRecyclerViewItems()
			}
		}
	}

	private fun resetAllRecyclerViewItems() {
		val adapter0 = bd.rvM1.adapter as? GridRVAdapter
		adapter0?.resetAllItemsUI()

		val adapter1 = bd.rvM3.adapter as? GridRVAdapter
		adapter1?.resetAllItemsUI()
	}

	private fun setComparisonAdapter() {
		if (mvm.tempComparisonItems.value != null) {
			val adapter =
				GridRVAdapter(this@MainFragment, mvm.tempComparisonItems.value!!, mvm, uvm, null, 1)
			val layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
			bd.rvM3.layoutManager = layoutManager
			bd.rvM3.adapter = adapter
		}
	}

	private fun setObserveComparisonItems() {
		mvm.tempComparisonItems.observe(viewLifecycleOwner) {
			setComparisonAdapter()
			if (it.size == 2) {
				bd.btnMComparison.text = "결과 보기"
			} else {
				bd.btnMComparison.text = "취소"
			}
		}
	}

	// guest UI로 변경
	private fun expandView(view: View) {
		val layoutParams = view.layoutParams as ConstraintLayout.LayoutParams
//		layoutParams.height = ConstraintLayout.LayoutParams.WRAP_CONTENT
//		context?.let { layoutParams.bottomMargin = dpToPx(it, 80) }
		layoutParams.matchConstraintPercentHeight = 1f
		view.layoutParams = layoutParams
	}

	// User UI로 변경 (즐겨찾기 추가하기 위해 collapse)
	private fun collapseView(view: View) {
		val layoutParams = view.layoutParams as ConstraintLayout.LayoutParams
//		context?.let { layoutParams.height = dpToPx(it, 365) }
    layoutParams.matchConstraintPercentHeight = 0.55f
		view.layoutParams = layoutParams
	}

	private fun initShimmer() {
		bd.sflM.apply {
			visibility = View.VISIBLE
			startShimmer()
		}
	}

	private fun cancelShimmer() {
		bd.sflM.apply {
			visibility = View.GONE
			stopShimmer()
		}
	}

	override fun onUserClick(userSn: Int) {
		val selectedUserItem = mvm.currentUserItems.find { it.userSn == userSn }
		if (selectedUserItem != null) {
			uvm.setUserItem(selectedUserItem)
			requireActivity().supportFragmentManager.beginTransaction().apply {
				replace(R.id.flMain, UserFragment())
				addToBackStack("UserFragment")
				commit()
			}
		}
		clearComparison()
	}

	override fun onMeasureClick(tempServerSn: Int) {
		if (!loadingDialog.isAdded && !loadingDialog.isVisible) {
			loadingDialog.show(requireActivity().supportFragmentManager, "LoadingDialogFragment")
		}
		(bd.rvM2.adapter as GridRVAdapter) // .setBlockedItemClick(true)
		lifecycleScope.launch(Dispatchers.IO) {
			val statics = fDao.getStaticWithPlaceholders(tempServerSn)
			val result = mvm.convertToFaceResult(requireContext(), statics)
//        scrollToView(bd.ibtnMBack, bd.nsvM)
			withContext(Dispatchers.Main) {
				mvm.currentResult.value = result
				if (loadingDialog.isAdded) {
					loadingDialog.dismiss()
				}
				activity?.let { fa -> showInfoDF(fa) }
				(bd.rvM2.adapter as GridRVAdapter) // .setBlockedItemClick(false)
			}
		}
		clearComparison()
	}

	// 버전 업 하면서 기존에 없던 user테이블 만들기
	suspend fun migrateStaticToUserTable(fDao: FaceDao) {
		// 1. uuid 그룹 불러오기
		val seeds = fDao.getDistinctUsersFromStatic()

		// 2. 그룹별로 user 추가 & static 업데이트
		for ((index, seed) in seeds.withIndex()) {
			// FaceUser 생성 (autoIncrement라서 user_sn은 자동 생성됨)
			val faceUser = FaceUser(
				name = seed.name,
				mobile = seed.mobile,
				uuid = seed.uuid,
				birthday = "",
				nationality = "",
				userGender = 0
			)

			// 생성된 user_sn 받기
			val insertedUserSn = fDao.insertUser(faceUser).toInt()

			Log.v(
				"MigrationUser",
				"Migrated user $index/${seeds.size}: uuid=${seed.uuid}, user_sn=$insertedUserSn"
			)
			// 3. 해당 uuid를 가진 모든 t_face_static의 user_sn 업데이트
			fDao.updateUserSnForUuid(seed.uuid, insertedUserSn)
		}

		Log.v("MigrationUser", "All users migrated. Total: ${seeds.size}")
	}
}