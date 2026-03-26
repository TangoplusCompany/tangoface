package com.tangoplus.tangoface.ui.adapter

import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tangoplus.tangoface.R
import com.tangoplus.tangoface.data.db.FaceDatabase
import com.tangoplus.tangoface.databinding.RvMeasureComparisonItemBinding
import com.tangoplus.tangoface.databinding.RvMeasureGridItemBinding
import com.tangoplus.tangoface.databinding.RvMeasureManageItemBinding
import com.tangoplus.tangoface.domain.util.FileUtility.setOnSingleClickListener
import com.tangoplus.tangoface.domain.util.UIUtility.dpToPx
import com.tangoplus.tangoface.ui.measure.TextInputDialogFragment
import com.tangoplus.tangoface.ui.listener.OnMeasureClickListener
import com.tangoplus.tangoface.ui.main.MainViewModel
import com.tangoplus.tangoface.ui.user.UserViewModel
import com.tangoplus.tangoface.ui.main.vo.MeasureGridItem
import com.tangoplus.tangoface.ui.settings.vm.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.IllegalArgumentException

class GridRVAdapter(
	private val fragment: Fragment,
	private val mgi: List<MeasureGridItem>,
	private val mvm: MainViewModel?,
	private val uvm: UserViewModel?,
	private val svm: SettingsViewModel?,
	private val case: Int
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
	var measureClickListener: OnMeasureClickListener? = null

	inner class FavoriteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val clMGI: ConstraintLayout = view.findViewById(R.id.clMGI)
		val tvMGIDate: TextView = view.findViewById(R.id.tvMGIDate)
		val tvMGINameMobile: TextView = view.findViewById(R.id.tvMGINameMobile)
		val ivMGICheck: ImageView = view.findViewById(R.id.ivMGICheck)
		val ivMGIFavorite: ImageView = view.findViewById(R.id.ivMGIFavorite)
		fun bind(mgi: MeasureGridItem) {
			tvMGINameMobile.text = mgi.userTitle

			if (mgi.userTitle == "GUEST") {
				tvMGIDate.text = mgi.regDate
				tvMGIDate.visibility = View.VISIBLE
			} else {
				tvMGIDate.text = ""
				tvMGIDate.visibility = View.GONE
			}

			clMGI.background = ContextCompat.getDrawable(fragment.requireContext(), R.drawable.effect_20)
			ivMGICheck.visibility = View.INVISIBLE
			if (mvm != null && uvm != null) {
				clMGI.setOnSingleClickListener {
					when (mvm.getComparisonState()) {
						true -> {
							if (mvm.tempComparisonItems.value != null && mvm.tempComparisonItems.value?.size!! <= 2) {
								if (mgi in mvm.tempComparisonItems.value!!) {
									mvm.removeItem(mgi)
									clMGI.background =
										ContextCompat.getDrawable(fragment.requireContext(), R.drawable.effect_20)
									ivMGICheck.visibility = View.INVISIBLE
								} else if (mvm.tempComparisonItems.value?.size!! < 2) {
									mvm.addItem(mgi)
									clMGI.background = ContextCompat.getDrawable(
										fragment.requireContext(),
										R.drawable.bckgnd_stroke_2_20
									)
									ivMGICheck.visibility = View.VISIBLE
								}
							}
						}

						false -> {
							measureClickListener?.onMeasureClick(mgi.tempServerSn)
							clMGI.isEnabled = false
							Handler(Looper.getMainLooper()).postDelayed({ clMGI.isEnabled = true }, 2500)
						}
					}
				}

				// 즐겨찾기 추가/해제
				ivMGIFavorite.visibility = if (mgi.userTitle == "GUEST") View.GONE else View.VISIBLE

				val currentFavorites = mvm.currentFaceFavorites.value
				if (mvm.currentFaceFavoriteSns.value?.contains(mgi.tempServerSn) == true) {
					// 즐겨찾기 추가된 곳
					tvMGIDate.visibility = View.VISIBLE
					tvMGIDate.text =
						currentFavorites.find { item -> item.tempServerSn == mgi.tempServerSn }?.userTitle ?: ""

					// Main에는 "tvMGINameMobile" 과 "tvMGIDate"가 동일하게 나옴
					tvMGINameMobile.visibility =
						if (tvMGIDate.text == tvMGINameMobile.text) View.GONE else View.VISIBLE

					ivMGIFavorite.setImageDrawable(
						ContextCompat.getDrawable(
							fragment.context,
							R.drawable.ic_star_enabled
						)
					)
					ivMGIFavorite.setOnSingleClickListener {
						MaterialAlertDialogBuilder(
							fragment.requireContext(),
							R.style.ThemeOverlay_App_MaterialAlertDialog
						).apply {
							setTitle("알림")
							setMessage("즐겨찾기를 삭제하시겠습니까?")
							setPositiveButton("예") { _, _ ->
								uvm.setMeasureItem(mgi)
								fragment.lifecycleScope.launch {
									withContext(Dispatchers.IO) {
										uvm.deleteFavorite(fragment.requireContext())
									}
									// 삭제 완료 후에 호출
									mvm.loadFavoriteDataFromDB(fragment.requireContext())
									ivMGIFavorite.setImageDrawable(
										ContextCompat.getDrawable(fragment.context, R.drawable.ic_star_disabled)
									)
								}
							}
							setNegativeButton("아니오") { _, _ -> }
							show()
						}
					}
				} else {
					ivMGIFavorite.setOnSingleClickListener {
						uvm.setMeasureItem(mgi)
						uvm.textInputCase = 0
						uvm.setTempTitle(mgi.userTitle)
						val dialog = TextInputDialogFragment()
						dialog.show(fragment.requireActivity().supportFragmentManager, "")
					}
					ivMGIFavorite.setImageDrawable(
						ContextCompat.getDrawable(
							fragment.context,
							R.drawable.ic_star_disabled
						)
					)
				}
			}
		}
	}

	inner class ComparisonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val clMCI: ConstraintLayout = view.findViewById(R.id.clMCI)
		val tvMCINameMobile: TextView = view.findViewById(R.id.tvMCINameMobile)
		val tvMCIDate: TextView = view.findViewById(R.id.tvMCIDate)

		fun bind(mgi: MeasureGridItem) {
			val newRegDate = mgi.regDate.substring(0, mgi.regDate.length - 3)
			tvMCINameMobile.visibility =
				if (mgi.userTitle.contains(newRegDate)) View.GONE else View.VISIBLE
			tvMCINameMobile.text = mgi.userTitle
			tvMCIDate.text = newRegDate
			// LayoutManager 방향에 따른 마진 설정
			setupLayoutByOrientation()
		}

		private fun setupLayoutByOrientation() {
			itemView.post {
				val parent = itemView.parent

				val recyclerView = parent as? RecyclerView ?: return@post
				val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return@post

				val layoutParams = clMCI.layoutParams as? ViewGroup.MarginLayoutParams ?: return@post
				if (layoutManager.orientation == LinearLayoutManager.VERTICAL) {
					val horizonMargin = fragment.context?.let { dpToPx(it, 10) } ?: 0
					val vertiMargin = fragment.context?.let { dpToPx(it, 5) } ?: 0

					clMCI.setPadding(0, horizonMargin, 0, horizonMargin)
					layoutParams.setMargins(vertiMargin, horizonMargin, vertiMargin, horizonMargin)
					layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
					layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
				} else {
					val horizonMargin = fragment.context?.let { dpToPx(it, 15) } ?: 0
					val vertiMargin = fragment.context?.let { dpToPx(it, 25) } ?: 0
//                    clMCI.setPadding(horizonMargin, vertiMargin / 2, horizonMargin, vertiMargin / 2)
					layoutParams.setMargins(horizonMargin, vertiMargin, horizonMargin, vertiMargin)
					layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
					layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
				}

				clMCI.layoutParams = layoutParams
			}
		}
	}

	inner class MeasureManageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val clMMI: ConstraintLayout = view.findViewById(R.id.clMMI)
		val tvMMITitle: TextView = view.findViewById(R.id.tvMMITitle)
		val tvMMISub: TextView = view.findViewById(R.id.tvMMISub)
		val tvMMIRegDate: TextView = view.findViewById(R.id.tvMMIRegDate)
		val btnMMIDelete: AppCompatButton = view.findViewById(R.id.btnMMIDelete)
		fun bind(currentItem: MeasureGridItem, position: Int) {
			if (currentItem.tempServerSn != -1) {
				tvMMITitle.text = currentItem.userTitle
				tvMMIRegDate.text = currentItem.regDate

				val isActivated = currentItem.activated == 1
				fragment.context?.let { ct ->
					clMMI.backgroundTintList = ColorStateList.valueOf(
						ContextCompat.getColor(
							ct,
							if (isActivated) R.color.white else R.color.subColor150
						)
					)
					btnMMIDelete.apply {
						backgroundTintList = ColorStateList.valueOf(
							ContextCompat.getColor(
								ct,
								if (isActivated) R.color.black else R.color.subColor700
							)
						)
						text = if (isActivated) "삭제" else "복원"
					}

					val isMeasureItem = svm?.currentTabIndex?.value == 0

					tvMMISub.text = if (isMeasureItem) "님의 측정" else "님"

					btnMMIDelete.setOnSingleClickListener {
						val isDelete = btnMMIDelete.text == "삭제"
						MaterialAlertDialogBuilder(
							ct,
							R.style.ThemeOverlay_App_MaterialAlertDialog
						).apply {
							setTitle("알림")
							setMessage("기록을 ${if (isDelete) "삭제" else "복원"} 하시겠습니까?")
							setPositiveButton("예") { _, _ ->
								Toast.makeText(ct, "기록이 ${if (isDelete) "삭제" else "복원"} 됐습니다", Toast.LENGTH_SHORT)
									.show()

								when (isMeasureItem) {
									true -> {
										svm.allMeasureItem[position].activated = if (isDelete) 0 else 1
									}

									false -> {
										if (svm != null) {
											svm.allUserItem[position].activated = if (isDelete) 0 else 1
										}
									}
								}

								fragment.lifecycleScope.launch {
									val fDao = FaceDatabase.getDatabase(ct).faceDao()
									when (isMeasureItem) {
										true -> fDao.activatingStatics(currentItem.tempServerSn, if (isDelete) 0 else 1)
										false -> fDao.activatingUser(currentItem.tempServerSn, if (isDelete) 0 else 1)
									}
								}

								notifyItemChanged(position)
							}
							setNegativeButton("아니오") { _, _ -> }
							show()
						}
					}
				}
			}
		}
	}

	override fun onCreateViewHolder(
		parent: ViewGroup,
		viewType: Int
	): RecyclerView.ViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		return when (case) {
			0 -> {
				val binding = RvMeasureGridItemBinding.inflate(inflater, parent, false)
				FavoriteViewHolder(binding.root)
			}

			1 -> {
				val binding = RvMeasureComparisonItemBinding.inflate(inflater, parent, false)
				ComparisonViewHolder(binding.root)
			}

			2 -> {
				val bd = RvMeasureManageItemBinding.inflate(inflater, parent, false)
				return MeasureManageViewHolder(bd.root)
			}

			else -> throw IllegalArgumentException("invalid view type binding")
		}
	}

	override fun onBindViewHolder(
		holder: RecyclerView.ViewHolder,
		position: Int
	) {
		val currentItem = mgi[position]
		if (holder is FavoriteViewHolder) {
			holder.bind(currentItem)
		} else if (holder is ComparisonViewHolder) {
			holder.bind(currentItem)
		} else if (holder is MeasureManageViewHolder) {
			if (svm != null) {
				val currentItem = when (svm.currentTabIndex.value) {
					0 -> svm.allMeasureItem[position]
					1 -> svm.allUserItem[position]
					else -> MeasureGridItem(-1)
				}
				holder.bind(currentItem, position)
			}
		}
	}

	override fun getItemCount(): Int {
		return mgi.size
	}

	fun resetAllItemsUI() {
		notifyDataSetChanged()
	}

}