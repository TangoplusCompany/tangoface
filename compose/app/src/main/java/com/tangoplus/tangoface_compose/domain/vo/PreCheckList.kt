package com.tangoplus.tangoface_compose.domain.vo

enum class PreCheckList(val displayName: String) {
	FACE_ASYMMETRY("좌우 비대칭"),
	FACE_SIZE("얼굴 크기"),
	HEAD_SIZE("두상 크기"),
	CHEEKBONE_SIZE("광대 크기"),
	SQUARE_JAW("사각 턱"),
	PEANUT_FACE("땅콩형 얼굴"),
	FLAT_FACE("편평, 입체감 없는 얼굴"),

	UNEVEN_EYEBROWS("눈썹/눈 비대칭"),
	EYE_TILT("눈꼬리 모양"),
	TEAR_TROUGH("눈밑 꺼짐"),
	EYE_DISTANCE("눈 간격"),
	WIDE_FOREHEAD("넓은 이마"),
	NARROW_FOREHEAD("좁은 이마"),

	MIDFACE_LENGTH("중안부 길이"),
	CHEEK_ASYMMETRY("양쪽 볼/광대 비대칭"),
	CHEEK_HOLLOW("볼꺼짐"),
	NASOLABIAL_FOLD("팔자주름"),
	LOW_BRIDGE_NOSE("낮은 콧대"),
	BULBOUS_NOSE_TIP("퍼진 콧망울"),
	CROOKED_NOSE("코 비대칭"),

	UNEVEN_LIPS("입술 비대칭"),
	DROOPY_MOUTH_CORNER("쳐진 입꼬리"),
	LONG_PHILTRUM("인중 길이"),
	WEAK_JAWLINE("턱선 무너짐"),
	JAW_ASYMMETRY("턱 비대칭"),
	MOUTH_HOLLOW("입 주변부 함몰"),
	PROGNATHISM("주걱턱"),
	RETROGNATHIA("무턱");

	override fun toString(): String = displayName
}