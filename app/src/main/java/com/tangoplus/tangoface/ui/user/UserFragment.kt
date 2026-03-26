package com.tangoplus.tangoface.ui.user

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.skydoves.balloon.ArrowPositionRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.showAlignBottom
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.data.db.FaceDao
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.databinding.FragmentUserBinding
import com.tangoplus.tangoface.domain.util.BitmapUtility.getScreenOrientation
import com.tangoplus.tangoface.domain.util.FaceTypeUtility.getFaceTypeFromInt
import com.tangoplus.tangoface.domain.util.FaceTypeUtility.getIntFromFaceType
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.util.UIUtility.showInfoDF
import com.tangoplus.tangoface.domain.util.UserUtility.countryCodeToName
import com.tangoplus.tangoface.ui.measure.DepthCameraActivity
import com.tangoplus.tangoface.ui.adapter.GridRVAdapter
import com.tangoplus.tangoface.ui.listener.OnMeasureClickListener
import com.tangoplus.tangoface.ui.main.MainFragment
import com.tangoplus.tangoface.ui.main.MainViewModel
import com.tangoplus.tangoface.ui.measure.TextInputDialogFragment
import com.tangoplus.tangoface.ui.settings.SequenceSelectDialogFragment
import com.tangoplus.tangoface.ui.settings.vm.SequenceSelectViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.component3
import kotlin.text.split

class UserFragment : Fragment(), OnMeasureClickListener {
	private lateinit var bd: FragmentUserBinding
	private val mvm: MainViewModel by activityViewModels()
	private val uvm: UserViewModel by activityViewModels()
	private val ssvm: SequenceSelectViewModel by activityViewModels()
	private lateinit var fDao: FaceDao

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		bd = FragmentUserBinding.inflate(layoutInflater)
		return bd.root
	}

	override fun onDestroyView() {
		super.onDestroyView()
		// 선택항목제거
		ssvm.originFaceType.value = null
		ssvm.tempSelectedFaceType.value = ssvm.originFaceType.value
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val fd = FaceDatabase.getDatabase(requireContext())
		fDao = fd.faceDao()

		uvm.measureGridItemLoad.observe(viewLifecycleOwner) { isLoaded ->
			if (isLoaded == true) {
				val countText = "총 기록 건수: ${uvm.getUserItem().tempServerSn.size} 건"
				bd.tvUCount.text = countText
				mvm.clearLoadDataFlag()
				setGridAdapter()
				setUserData()
				setMemoObserver()
				setClickListener()
				setObserveFaceType()
			}
		}

		mvm.currentFaceFavoriteSns.observe(viewLifecycleOwner) { faceFavorites ->
			Log.v("옵저버동작", "$faceFavorites")
			setGridAdapter()
		}
		uvm.loadMeasureGridItemFromDB(requireContext())
		setComparisonClick()
		setObserveComparisonState()
	}

	private fun setComparisonClick() {
		bd.btnUComparison.setOnSingleClickListener {
			if (uvm.getUserItem().tempServerSn.size < 2) {
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
				bd.btnUComparison.showAlignBottom(balloon)
				balloon.dismissWithDelay(3000L)
				balloon.setOnBalloonClickListener { balloon.dismiss() }
				return@setOnSingleClickListener
			}

			if (mvm.tempComparisonItems.value?.size == 2 && bd.btnUComparison.text == "결과 보기") {

				val sortedTempItems = mvm.tempComparisonItems.value?.sortedBy { it.regDate }
				Log.v("비교항목", "${sortedTempItems}")
				// 왼쪽 오른쪽 찾아서 DOUBLE 만들기
				lifecycleScope.launch(Dispatchers.IO) {
					val leftStatics =
						sortedTempItems?.get(0)?.tempServerSn?.let { it1 -> fDao.getStaticWithPlaceholders(it1) }
					val rightStatics =
						sortedTempItems?.get(1)?.tempServerSn?.let { it1 -> fDao.getStaticWithPlaceholders(it1) }

					withContext(Dispatchers.Main) {
						val leftResult =
							leftStatics?.let { it1 -> mvm.convertToFaceResult(requireContext(), it1) }
						val rightResult =
							rightStatics?.let { it1 -> mvm.convertToFaceResult(requireContext(), it1) }
						mvm.comparisonDoubleItem = Pair(leftResult!!, rightResult!!)
						activity?.let { fa -> showInfoDF(fa) }
					}
				}
				Handler(Looper.getMainLooper()).postDelayed({
					clearComparison()
				}, 250)

				return@setOnSingleClickListener
			}

			if (mvm.getComparisonState()) {
				bd.btnUComparison.text = "아이템 선택"
//                bd.btnUComparison.isEnabled = true
				mvm.setComparisonState(false)
				clearComparison()
			} else {
				bd.btnUComparison.text = "취소"
//                bd.btnUComparison.isEnabled = false
				mvm.setComparisonState(true)

				// 변경 시작 할 때 observe 설정
				if (!mvm.tempComparisonItems.hasObservers()) {
					setObserveComparisonItems()
				}
			}
		}
	}

	private fun setObserveFaceType() {
		ssvm.originFaceType.observe(viewLifecycleOwner) { changedFaceType ->
			if (changedFaceType != null) {
				uvm.selectedUserItem.value?.faceType = getIntFromFaceType(changedFaceType)
				setUserData()
			}
		}
	}

	private fun setObserveComparisonItems() {
		mvm.tempComparisonItems.observe(viewLifecycleOwner) {
			setComparisonAdapter()

			bd.btnUComparison.text = if (it.size == 2) {
				"결과 보기"
			} else {
				"취소"
			}
		}
	}

	private fun setComparisonAdapter() {
		if (mvm.tempComparisonItems.value != null) {
			val adapter =
				GridRVAdapter(this@UserFragment, mvm.tempComparisonItems.value!!, mvm, uvm, null, 1)
			val layoutManager =
				LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
			bd.rvU2.layoutManager = layoutManager
			bd.rvU2.adapter = adapter
		}
	}

	private fun setObserveComparisonState() {
		mvm.comparisonState.observe(viewLifecycleOwner) { isComparison ->
			if (!isComparison) {
				mvm.clearItems()
				bd.btnUComparison.text = "아이템 선택"
				resetAllRecyclerViewItems()
			}
		}
	}

	private fun clearComparison() {
		mvm.clearItems()
		bd.btnUComparison.text = "아이템 선택"
		mvm.setComparisonState(false)
		mvm.tempComparisonItems.removeObservers(viewLifecycleOwner)
	}


	private fun resetAllRecyclerViewItems() {
		val adapter0 = bd.rvU1.adapter as? GridRVAdapter
		adapter0?.resetAllItemsUI()

		val adapter1 = bd.rvU2.adapter as? GridRVAdapter
		adapter1?.resetAllItemsUI()
	}

	override fun onMeasureClick(tempServerSn: Int) {

		(bd.rvU1.adapter as GridRVAdapter)
		lifecycleScope.launch(Dispatchers.IO) {
			val statics = fDao.getStaticWithPlaceholders(tempServerSn)
			withContext(Dispatchers.Main) {
				Log.v("user측정클릭", "${statics.size}/ $statics")
				mvm.currentResult.value = mvm.convertToFaceResult(requireContext(), statics)

				activity?.let { fa -> showInfoDF(fa) }
			}
		}
	}

	private fun setGridAdapter() {
		val so = requireActivity().getScreenOrientation()
		val orientSpanCount = if (so.contains("landscape")) 3 else 2
		Log.v("user측정건수", "${uvm.currentGridItems.value}")
		bd.rvU1.apply {
			val gridAdapter =
				GridRVAdapter(this@UserFragment, uvm.currentGridItems.value ?: listOf(), mvm, uvm, null, 0)
			gridAdapter.measureClickListener = this@UserFragment
			layoutManager = GridLayoutManager(context, orientSpanCount)
			adapter = gridAdapter
		}
	}

	private fun setUserData() {
		val ud = uvm.selectedUserItem.value
		val mobile = ud?.userMobile?.substring(0, 3) + "-" + ud?.userMobile?.substring(
			3,
			7
		) + "-" + ud?.userMobile?.substring(7)

		val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
		val birthday = ud.userBirthday

		val age = if (birthday?.length == 10) {
			try {
				val (birthYear, birthMonth, birthDay) = birthday.split("-").map { it.toInt() }

				val todayYear = c.get(Calendar.YEAR)
				val todayMonth = c.get(Calendar.MONTH) + 1  // Calendar.MONTH는 0부터 시작
				val todayDay = c.get(Calendar.DAY_OF_MONTH)

				var ageCalc = todayYear - birthYear

				// 생일 안 지났으면 -1
				if (todayMonth < birthMonth || (todayMonth == birthMonth && todayDay < birthDay)) {
					ageCalc -= 1
				}

				"만 $ageCalc 세"
			} catch (e: Exception) {
				"미설정"
			}
		} else {
			"미설정"
		}

		val code = ud?.userNationality?.replace("+", "")?.toIntOrNull() ?: 0
		val nationality = countryCodeToName.getValue(code)
		val userProfile = """
            이름: ${ud?.userName ?: ""} 
            휴대폰: $mobile
            나이: $age
            국적: $nationality
            얼굴 타입: ${getFaceTypeFromInt(ud?.faceType)?.displayName ?: ""}
        """.trimIndent()
		bd.tvUProfile.text = userProfile


		// 얼굴형 변경 버튼
		bd.btnUShape.text = if (ud?.faceType == null) "선택" else "변경"
	}

	private fun setMemoObserver() {
		uvm.isShowMemo.observe(viewLifecycleOwner) { isShown ->
			bd.tvUMemo.apply {
				// 글자 정렬
				gravity = if (isShown) {
					Gravity.NO_GRAVITY
				} else {
					Gravity.CENTER
				}

				// 가이드 문구
				text = if (isShown) {
					uvm.getMemo().userTitle
				} else {
					"누르면 메모가 보입니다"
				}
			}
			uvm.currentMemo.observe(viewLifecycleOwner) { memo ->
				Log.v("메모", "${uvm.getMemo().userTitle}, ${uvm.getMemo()}")
				if (uvm.getMemoVisibility()) {
					bd.tvUMemo.text = uvm.getMemo().userTitle
				}
			}
			// 메모를 editText기본으로 넣어놓기
			uvm.setTempTitle(bd.tvUMemo.text.toString())
			bd.ivUMemo.visibility = if (isShown) View.VISIBLE else View.INVISIBLE
		}
		uvm.setMemoVisibility(false)
	}

	private fun setClickListener() {

		bd.ivUMemo.setOnSingleClickListener {
			uvm.setMemoVisibility(false)
		}
		bd.tvUMemo.setOnSingleClickListener {
			if (!uvm.getMemoVisibility()) {
				uvm.setMemoVisibility(true)
				return@setOnSingleClickListener
			}
			if (uvm.getMemoVisibility()) {
				uvm.textInputCase = 1
				val dialog = TextInputDialogFragment()
				dialog.show(requireActivity().supportFragmentManager, "")
			}
		}

		bd.ibtnUBack.setOnSingleClickListener {
			clearComparison()
			requireActivity().supportFragmentManager.beginTransaction().apply {
				replace(R.id.flMain, MainFragment())
				commit()
			}
		}

		bd.btnUStart.setOnSingleClickListener {
			val userItem = uvm.getUserItem()
			ssvm.fragmentCase = 1
			if (ssvm.getSelectedSeq().isEmpty()) {
				val dialog = SequenceSelectDialogFragment()
				activity?.supportFragmentManager?.let { manager -> dialog.show(manager, "SequenceSelect") }
				return@setOnSingleClickListener
			}

			val intent = Intent(context, DepthCameraActivity::class.java)
			intent.putExtra("currentUserItem", userItem)
			Log.v("가져온게 있나요", "In: ${userItem.userName}, ${userItem.userMobile}, ${userItem.userSn}")
			clearComparison()
			startActivity(intent)
			requireActivity().finishAffinity()
		}

		bd.btnUShape.setOnSingleClickListener {
			val dialog = FaceTypeDialogFragment()
			activity?.supportFragmentManager?.let { manager ->
				dialog.show(
					manager,
					"FaceTypeDialogFragment"
				)
			}
		}
	}
}