package com.tangoplus.tangoface_compose.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tangoplus.tangoface_compose.R
import com.tangoplus.tangoface_compose.domain.model.UserItem
import com.tangoplus.tangoface_compose.ui.theme.SubColor100
import com.tangoplus.tangoface_compose.ui.theme.SubColor500
import com.tangoplus.tangoface_compose.ui.theme.TangoAccent
import com.tangoplus.tangoface_compose.ui.theme.TangoAccent100
import com.tangoplus.tangoface_compose.ui.theme.TangoBg
import com.tangoplus.tangoface_compose.ui.theme.TangoBg300
import com.tangoplus.tangoface_compose.ui.theme.TangoBg500
import com.tangoplus.tangoface_compose.ui.theme.TangoBg700
import com.tangoplus.tangoface_compose.ui.theme.TangoNormal

/**
 * 측정 기록 / 결과 보기 탭 화면 (XML MeasureHistoryFragment 및 fragment_measure_history.xml 완전 이식)
 * - 상단: "검사 기록" 타이틀 & 검색창 (bg_stroke_2_20_accent 스타일)
 * - 본문: 검사 기록 카드 그리드 리스트 (UserItem)
 * - 항목 클릭 시 해당 고객의 결과 화면(InfoContainerScreen)으로 이동
 */
@Composable
fun MeasureHistoryScreen(
	historyList: List<UserItem>,
	onUserClick: (UserItem) -> Unit,
	modifier: Modifier = Modifier
) {
	var searchQuery by remember { mutableStateOf("") }

	val filteredList = remember(searchQuery, historyList) {
		if (searchQuery.isBlank()) historyList
		else historyList.filter {
			(it.name?.contains(searchQuery, ignoreCase = true) == true) ||
			(it.mobile?.contains(searchQuery) == true)
		}
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(TangoBg)
			.padding(horizontal = 24.dp, vertical = 20.dp)
	) {
		// 1. 상단 타이틀 & 검색 바
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = 20.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			Text(
				text = "검사 기록",
				fontSize = 24.sp,
				fontWeight = FontWeight.Bold,
				color = SubColor100
			)

			// 검색창 (XML etMHSearch 스타일)
			Box(
				modifier = Modifier
					.width(320.dp)
					.height(48.dp)
					.background(TangoBg300, RoundedCornerShape(20.dp))
					.border(1.5.dp, TangoAccent, RoundedCornerShape(20.dp))
					.padding(horizontal = 16.dp),
				contentAlignment = Alignment.CenterStart
			) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Icon(
						painter = painterResource(id = R.drawable.ic_search),
						contentDescription = "검색",
						tint = TangoAccent100,
						modifier = Modifier.size(20.dp)
					)
					Spacer(modifier = Modifier.width(10.dp))
					if (searchQuery.isEmpty()) {
						Text(
							text = "이름을 입력해주세요",
							fontSize = 14.sp,
							color = SubColor500
						)
					}
				}
				BasicTextField(
					value = searchQuery,
					onValueChange = { searchQuery = it },
					singleLine = true,
					textStyle = TextStyle(
						color = Color.White,
						fontSize = 14.sp
					),
					cursorBrush = SolidColor(TangoAccent),
					modifier = Modifier.fillMaxWidth().padding(start = 30.dp)
				)
			}
		}

		// 2. 기록 목록 그리드 (2열 그리드)
		if (filteredList.isEmpty()) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.padding(32.dp),
				contentAlignment = Alignment.Center
			) {
				Column(horizontalAlignment = Alignment.CenterHorizontally) {
					Text(text = "📢", fontSize = 42.sp)
					Spacer(modifier = Modifier.height(12.dp))
					Text(
						text = if (searchQuery.isNotEmpty()) "일치하는 고객 검색 결과가 없습니다." else "기록이 없습니다. 측정을 시작해보세요",
						fontSize = 18.sp,
						fontWeight = FontWeight.SemiBold,
						color = SubColor100
					)
				}
			}
		} else {
			LazyVerticalGrid(
				columns = GridCells.Adaptive(minSize = 340.dp),
				modifier = Modifier.fillMaxSize(),
				horizontalArrangement = Arrangement.spacedBy(16.dp),
				verticalArrangement = Arrangement.spacedBy(16.dp)
			) {
				items(filteredList) { item ->
					HistoryCardItem(
						user = item,
						onClick = { onUserClick(item) }
					)
				}
			}
		}
	}
}

/**
 * 개별 검사 기록 카드 (UserRVAdapter 스타일)
 */
@Composable
fun HistoryCardItem(
	user: UserItem,
	onClick: () -> Unit
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.clickable { onClick() },
		shape = RoundedCornerShape(16.dp),
		colors = CardDefaults.cardColors(containerColor = TangoBg700),
		elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(18.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text(
						text = user.name ?: "GUEST",
						fontSize = 18.sp,
						fontWeight = FontWeight.Bold,
						color = Color.White
					)
					Spacer(modifier = Modifier.width(10.dp))
					Surface(
						color = TangoNormal.copy(alpha = 0.2f),
						shape = RoundedCornerShape(6.dp)
					) {
						Text(
							text = user.measureType.displayName,
							fontSize = 12.sp,
							fontWeight = FontWeight.Bold,
							color = TangoNormal,
							modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
						)
					}
				}

				Text(
					text = user.regDate.take(10),
					fontSize = 13.sp,
					color = SubColor500
				)
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Column {
					Text(
						text = "연락처: ${user.mobile ?: "정보 없음"}",
						fontSize = 13.sp,
						color = SubColor500
					)
					if (!user.birthday.isNullOrEmpty()) {
						Text(
							text = "생년월일: ${user.birthday}",
							fontSize = 13.sp,
							color = SubColor500
						)
					}
				}

				Button(
					onClick = onClick,
					shape = RoundedCornerShape(10.dp),
					colors = ButtonDefaults.buttonColors(
						containerColor = TangoAccent,
						contentColor = Color.White
					)
				) {
					Text(text = "분석 결과 🔍", fontSize = 13.sp, fontWeight = FontWeight.Bold)
				}
			}
		}
	}
}

