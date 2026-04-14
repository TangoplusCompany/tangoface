package com.tangoplus.facebeautyexpert.ui.listener

interface OnPosterClickListener {
	fun onAddClick()              // 새 포스터 추가 (+)
	fun onEditClick(position: Int) // 기존 포스터 수정
	fun onDeleteClick(position: Int) // 포스터 삭제
}