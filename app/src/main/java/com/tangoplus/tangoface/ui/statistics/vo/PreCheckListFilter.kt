package com.tangoplus.tangoface.ui.statistics.vo

enum class PreCheckListFilter(val displayName: String) {
	AGE_0_20("20세 미만"),
	AGE_20_25("20세~25세 미만"),
	AGE_25_30("25세~30세 미만"),
	AGE_30_35("30세~35세 미만"),
	AGE_35_40("35세~40세 미만"),
	AGE_40_50("40~50세 미만"),
	AGE_50_60("50~60세 미만"),
	AGE_60_("60세 이상"),
	NATIONALITY_KOR("대한민국"),
	NATIONALITY_EU("유럽"),
	NATIONALITY_ASIA("아시아"),
	NATIONALITY_AMERICA("아메리카"),
	NATIONALITY_ARAB_AFRICA("중동, 아프리카"),
	NATIONALITY_ETC("기타 국적"),
	GENDER_M("남성"),
	GENDER_F("여성");

	override fun toString(): String = displayName
	// TODO 여기 국적부터 string에 추가해야함.
}