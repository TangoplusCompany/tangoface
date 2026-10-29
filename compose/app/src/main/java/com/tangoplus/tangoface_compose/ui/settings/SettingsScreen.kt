package com.tangoplus.tangoface_compose.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tangoplus.tangoface_compose.R
import com.tangoplus.tangoface_compose.data.db.FaceDatabase
import com.tangoplus.tangoface_compose.domain.util.BackupManager
import com.tangoplus.tangoface_compose.domain.util.BackupStats
import com.tangoplus.tangoface_compose.domain.util.RestoreStats
import com.tangoplus.tangoface_compose.ui.theme.SubColor100
import com.tangoplus.tangoface_compose.ui.theme.SubColor300
import com.tangoplus.tangoface_compose.ui.theme.SubColor500
import com.tangoplus.tangoface_compose.ui.theme.TangoAccent
import com.tangoplus.tangoface_compose.ui.theme.TangoAccentContrast
import com.tangoplus.tangoface_compose.ui.theme.TangoBg
import com.tangoplus.tangoface_compose.ui.theme.TangoBg500
import com.tangoplus.tangoface_compose.ui.theme.TangoBg700
import kotlinx.coroutines.launch

/**
 * 설정 탭 화면 (XML SettingsFragment 일치 + 알파 테스트용 백업/복원 완벽 지원)
 * 1. 슬라이드 포스터 관리
 * 2. 사전 체크리스트 통계
 * 3. 측정 데이터 백업 및 복원 (Export & Import)
 * 4. 깊이 카메라 각도 조정
 */
@Composable
fun SettingsScreen(
	onOptionClick: (String) -> Unit = {},
	onRestoreSuccess: () -> Unit = {},
	modifier: Modifier = Modifier
) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val faceDao = remember { FaceDatabase.getDatabase(context).faceDao() }

	// 다이얼로그 및 로딩 상태
	var showBackupDialog by remember { mutableStateOf(false) }
	var isLoading by remember { mutableStateOf(false) }
	var loadingMessage by remember { mutableStateOf("") }

	var backupResultInfo by remember { mutableStateOf<BackupStats?>(null) }
	var restoreResultInfo by remember { mutableStateOf<RestoreStats?>(null) }
	var errorMessage by remember { mutableStateOf<String?>(null) }

	// SAF ZIP 파일 선택 런처 (외부 백업본 불러오기)
	val zipPickerLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenDocument()
	) { uri: Uri? ->
		if (uri != null) {
			scope.launch {
				isLoading = true
				loadingMessage = "백업본을 복원하는 중입니다...\n(DB 데이터 삽입 및 이미지 압축 해제)"
				val result = BackupManager.restoreBackup(context, uri, faceDao)
				isLoading = false
				result.onSuccess { stats ->
					restoreResultInfo = stats
					onRestoreSuccess()
				}.onFailure { e ->
					errorMessage = "복원 실패: ${e.localizedMessage ?: "알 수 없는 오류"}"
				}
			}
		}
	}

	val settingsItems = listOf(
		Triple("슬라이드 포스터 관리", "대기 화면에 노출되는 홍보/가이드 포스터를 등록 및 관리합니다.", R.drawable.ic_image),
		Triple("사전 체크리스트 통계", "검사 전 설문조사 및 증상 체크리스트 통계를 확인합니다.", R.drawable.ic_stats),
		Triple("측정 데이터 백업 및 복원", "알파테스트용 DB(고객/인사이트/설문) 및 사진/영상을 ZIP으로 백업하거나 복원합니다.", R.drawable.ic_refresh),
		Triple("깊이 카메라 각도 조정", "Orbbec 3D 뎁스 카메라 센서의 자이로 수평 및 설치 각도를 교정합니다.", R.drawable.ic_measure)
	)

	Column(
		modifier = modifier
			.fillMaxSize()
			.background(TangoBg)
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 28.dp, vertical = 24.dp)
	) {
		Text(
			text = "설정",
			fontSize = 26.sp,
			fontWeight = FontWeight.Bold,
			color = SubColor100,
			modifier = Modifier.padding(bottom = 24.dp)
		)

		Column(
			modifier = Modifier.fillMaxWidth(),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			settingsItems.forEach { (title, description, iconRes) ->
				Card(
					modifier = Modifier
						.fillMaxWidth()
						.clickable {
							if (title == "측정 데이터 백업 및 복원") {
								showBackupDialog = true
							} else {
								onOptionClick(title)
							}
						},
					shape = RoundedCornerShape(16.dp),
					colors = CardDefaults.cardColors(containerColor = TangoBg700),
					elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(22.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween
					) {
						Row(
							modifier = Modifier.weight(1f),
							verticalAlignment = Alignment.CenterVertically
						) {
							Box(
								modifier = Modifier
									.size(48.dp)
									.background(TangoBg500, RoundedCornerShape(12.dp)),
								contentAlignment = Alignment.Center
							) {
								Icon(
									painter = painterResource(id = iconRes),
									contentDescription = title,
									tint = TangoAccent,
									modifier = Modifier.size(24.dp)
								)
							}

							Spacer(modifier = Modifier.width(18.dp))

							Column {
								Text(
									text = title,
									fontSize = 17.sp,
									fontWeight = FontWeight.Bold,
									color = Color.White
								)
								Spacer(modifier = Modifier.height(4.dp))
								Text(
									text = description,
									fontSize = 13.sp,
									color = SubColor500,
									lineHeight = 18.sp
								)
							}
						}

						Icon(
							painter = painterResource(id = R.drawable.ic_arrow_right),
							contentDescription = "이동",
							tint = TangoAccent,
							modifier = Modifier.size(22.dp)
						)
					}
				}
			}
		}
	}

	// 1. 백업/복원 메인 선택 다이얼로그
	if (showBackupDialog) {
		AlertDialog(
			onDismissRequest = { showBackupDialog = false },
			containerColor = TangoBg700,
			title = {
				Text(
					text = "측정 데이터 백업 & 복원",
					color = Color.White,
					fontSize = 19.sp,
					fontWeight = FontWeight.Bold
				)
			},
			text = {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(vertical = 8.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp)
				) {
					Text(
						text = "알파 테스트 단계에서 태블릿 간 데이터 이동 및 유실 방지를 위해 DB와 촬영된 사진을 안전하게 보존합니다.",
						color = SubColor300,
						fontSize = 13.sp,
						lineHeight = 18.sp
					)

					HorizontalDivider(color = TangoBg500, thickness = 1.dp)

					// 버튼 1: 새 백업본 생성
					Button(
						onClick = {
							showBackupDialog = false
							scope.launch {
								isLoading = true
								loadingMessage = "측정 데이터를 압축 백업 중입니다...\n(DB 내역 + 사진/영상)"
								val result = BackupManager.createBackup(context, faceDao)
								isLoading = false
								result.onSuccess { stats ->
									backupResultInfo = stats
								}.onFailure { e ->
									errorMessage = "백업 생성 실패: ${e.localizedMessage ?: "알 수 없는 오류"}"
								}
							}
						},
						colors = ButtonDefaults.buttonColors(containerColor = TangoAccent),
						shape = RoundedCornerShape(10.dp),
						modifier = Modifier.fillMaxWidth().height(48.dp)
					) {
						Text("💾 현재 데이터 전체 백업 생성 (.ZIP)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
					}

					// 버튼 2: 백업본 복원 (불러오기)
					Button(
						onClick = {
							showBackupDialog = false
							zipPickerLauncher.launch(
								arrayOf("application/zip", "application/x-zip-compressed", "*/*")
							)
						},
						colors = ButtonDefaults.buttonColors(containerColor = TangoBg500),
						shape = RoundedCornerShape(10.dp),
						modifier = Modifier.fillMaxWidth().height(48.dp)
					) {
						Text("📂 외부 백업본 불러오기 / 복원 (ZIP 선택)", color = Color.White, fontSize = 14.sp)
					}

					// 버튼 3: 백업 폴더 열기
					TextButton(
						onClick = {
							showBackupDialog = false
							BackupManager.openBackupFolder(context)
						},
						modifier = Modifier.fillMaxWidth()
					) {
						Text("📁 백업 저장소 폴더 열기 (Documents/TangoPlusBackup)", color = TangoAccentContrast, fontSize = 13.sp)
					}
				}
			},
			confirmButton = {
				TextButton(onClick = { showBackupDialog = false }) {
					Text("닫기", color = SubColor300)
				}
			}
		)
	}

	// 2. 진행 중 로딩 다이얼로그
	if (isLoading) {
		AlertDialog(
			onDismissRequest = { /* 차단 */ },
			containerColor = TangoBg700,
			title = {
				Text(text = "데이터 작업 진행 중", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
			},
			text = {
				Row(
					modifier = Modifier.fillMaxWidth().padding(16.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(20.dp)
				) {
					CircularProgressIndicator(color = TangoAccent, modifier = Modifier.size(40.dp))
					Text(
						text = loadingMessage,
						color = SubColor100,
						fontSize = 14.sp,
						lineHeight = 20.sp
					)
				}
			},
			confirmButton = {}
		)
	}

	// 3. 백업 생성 성공 결과 다이얼로그
	backupResultInfo?.let { stats ->
		AlertDialog(
			onDismissRequest = { backupResultInfo = null },
			containerColor = TangoBg700,
			title = {
				Text(text = "🎉 백업 완료", color = TangoAccentContrast, fontSize = 19.sp, fontWeight = FontWeight.Bold)
			},
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text("측정 데이터가 안전하게 압축 저장되었습니다.", color = Color.White, fontSize = 14.sp)
					Spacer(modifier = Modifier.height(4.dp))
					Text("• 파일명: ${stats.fileName}", color = SubColor300, fontSize = 13.sp)
					Text("• 파일 크기: ${stats.formattedSize}", color = SubColor300, fontSize = 13.sp)
					Text("• 측정 기록: ${stats.staticsCount}건", color = SubColor300, fontSize = 13.sp)
					Text("• AI 분석 리포트: ${stats.insightsCount}건", color = SubColor300, fontSize = 13.sp)
					Text("• 사전 체크리스트: ${stats.checklistsCount}건", color = SubColor300, fontSize = 13.sp)
					Text("• 보존된 미디어 파일: ${stats.mediaFilesCount}개", color = SubColor300, fontSize = 13.sp)
					Spacer(modifier = Modifier.height(4.dp))
					Text("저장 위치: Documents/TangoPlusBackup/", color = TangoAccent, fontSize = 12.sp)
				}
			},
			confirmButton = {
				Button(
					onClick = { backupResultInfo = null },
					colors = ButtonDefaults.buttonColors(containerColor = TangoAccent)
				) {
					Text("확인", color = Color.White)
				}
			}
		)
	}

	// 4. 백업 복원 성공 결과 다이얼로그
	restoreResultInfo?.let { stats ->
		AlertDialog(
			onDismissRequest = { restoreResultInfo = null },
			containerColor = TangoBg700,
			title = {
				Text(text = "✅ 데이터 복원 완료", color = TangoAccentContrast, fontSize = 19.sp, fontWeight = FontWeight.Bold)
			},
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text("외부 백업본의 데이터와 미디어가 모두 복원되었습니다.", color = Color.White, fontSize = 14.sp)
					Spacer(modifier = Modifier.height(4.dp))
					Text("• 복원된 측정 기록: ${stats.staticsCount}건", color = SubColor300, fontSize = 13.sp)
					Text("• 복원된 AI 분석: ${stats.insightsCount}건", color = SubColor300, fontSize = 13.sp)
					Text("• 복원된 사전 설문: ${stats.checklistsCount}건", color = SubColor300, fontSize = 13.sp)
					Text("• 복구된 미디어 파일: ${stats.mediaFilesCount}개", color = SubColor300, fontSize = 13.sp)
					Spacer(modifier = Modifier.height(4.dp))
					Text("※ '결과 보기' 탭에서 복원된 측정 내역을 바로 확인하실 수 있습니다.", color = TangoAccent, fontSize = 12.sp)
				}
			},
			confirmButton = {
				Button(
					onClick = { restoreResultInfo = null },
					colors = ButtonDefaults.buttonColors(containerColor = TangoAccent)
				) {
					Text("확인", color = Color.White)
				}
			}
		)
	}

	// 5. 오류 다이얼로그
	errorMessage?.let { err ->
		AlertDialog(
			onDismissRequest = { errorMessage = null },
			containerColor = TangoBg700,
			title = {
				Text(text = "오류 발생", color = Color.Red, fontSize = 18.sp, fontWeight = FontWeight.Bold)
			},
			text = {
				Text(text = err, color = SubColor100, fontSize = 14.sp)
			},
			confirmButton = {
				Button(
					onClick = { errorMessage = null },
					colors = ButtonDefaults.buttonColors(containerColor = TangoBg500)
				) {
					Text("닫기", color = Color.White)
				}
			}
		)
	}
}
