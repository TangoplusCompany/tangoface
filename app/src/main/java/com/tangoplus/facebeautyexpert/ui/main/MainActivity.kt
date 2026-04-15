package com.tangoplus.facebeautyexpert.ui.main

import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tangoplus.facebeautyexpert.R
import com.tangoplus.facebeautyexpert.data.db.FaceDao
import com.tangoplus.facebeautyexpert.data.db.FaceDatabase
import com.tangoplus.facebeautyexpert.databinding.ActivityMainBinding
import com.tangoplus.facebeautyexpert.domain.util.FileUtility.backupUserData
import com.tangoplus.facebeautyexpert.domain.util.PermissionUtility.getAllPermissions
import com.tangoplus.facebeautyexpert.domain.util.PreferenceUtility
import com.tangoplus.facebeautyexpert.domain.util.SoundManager
import com.tangoplus.facebeautyexpert.ui.info.InformationFragment
import com.tangoplus.facebeautyexpert.ui.info.MeasureHistoryFragment
import com.tangoplus.facebeautyexpert.ui.main.vm.FragmentViewModel
import com.tangoplus.facebeautyexpert.ui.main.vm.MainViewModel
import com.tangoplus.facebeautyexpert.ui.measure.MeasureImageFragment
import com.tangoplus.facebeautyexpert.ui.statistics.StatisticsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.getValue

class MainActivity : AppCompatActivity() {
	private val mvm: MainViewModel by viewModels()
	private val fvm: FragmentViewModel by viewModels()
	private lateinit var bd: ActivityMainBinding
	private lateinit var fDao: FaceDao
	private lateinit var prefs: PreferenceUtility
	private var selectedTabId = R.id.main
	companion object {

		private val STORAGE_PERMISSION_REQUEST_CODE = 1001

		fun hasPermissions(context: Context): Boolean {
			Log.d("PermissionCheck", "Context type: ${context::class.java.name}")
			return getAllPermissions().all {
				ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
			}
		}
	}

	fun requestAllFilesAccess(context: Context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			if (!Environment.isExternalStorageManager()) {
				try {
					val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
					intent.data = Uri.parse("package:${context.packageName}")
					context.startActivity(intent)
				} catch (e: Exception) {
					Log.e("PERMISSION", "설정 화면 열기 실패", e)
				}
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		bd = ActivityMainBinding.inflate(layoutInflater)
		WindowCompat.setDecorFitsSystemWindows(window, false)
		setContentView(bd.root)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
			insets
		}
		// 상단, 하단 상태표시줄 넣기
		val controller = WindowCompat.getInsetsController(window, window.decorView)
		controller.apply {
			hide(WindowInsetsCompat.Type.systemBars())
			systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		}
		checkMemoryUsage()
		SoundManager.init(this@MainActivity)

		onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
		if (savedInstanceState == null) {
			supportFragmentManager.beginTransaction()
				.replace(R.id.flMain, MainFragment())
				.commit()
		}

		bd.bnv.setOnItemSelectedListener {
			setCurrentFragment(it.itemId)
			true
		}
		bd.bnv.setOnItemReselectedListener { setCurrentFragment(it.itemId) }


		if (!hasPermissions(this)) {
			ActivityCompat.requestPermissions(
				this,
				getAllPermissions(),
				STORAGE_PERMISSION_REQUEST_CODE
			)
			requestAllFilesAccess(this@MainActivity)
		} else {
			lifecycleScope.launch(Dispatchers.IO) {
				fDao = FaceDatabase.getDatabase(this@MainActivity).faceDao()
				val dbDataCount = fDao.getDataCount()

				if (dbDataCount == 0) {
					Log.v("백업", "DB에 데이터 없음 - 백업 스킵")
					return@launch
				}

				prefs = PreferenceUtility(this@MainActivity)
				val lastBackupCount = prefs.getLastBackupDataCount()

				Log.v("백업", "DB 데이터: $dbDataCount 개, 마지막 백업: $lastBackupCount 개")

				if (lastBackupCount < dbDataCount) {
					// DB에 새 데이터가 있을 때만 백업
					val latestFileInfo = getLatestMeasurementFiles()

					if (latestFileInfo != null) {
						Log.v("백업", "백업 시작: ${latestFileInfo.allFiles.size}개 파일")
						backupLatestMeasurementData(this@MainActivity, latestFileInfo)
						prefs.setLastBackupDataCount(dbDataCount)
					} else {
						Log.w("백업", "DB에는 데이터가 있지만 파일을 찾을 수 없음")
					}

					exportedMeasureSettings() // 새로 측정을 했을 때나
				} else {
					Log.v("백업", "변경사항 없음 - 백업 스킵")
				}

			}
		}
		cacheDir.deleteRecursively()

		// 측정하고 왔는지 확인하기
		mvm.tempServerSnAfterMeasure = intent?.getIntExtra("serverSn", -1) ?: -1

		// 측정이 완료되고 바로 결과를 볼 때
		val fd = FaceDatabase.getDatabase(this)
		fDao = fd.faceDao()
		if (mvm.tempServerSnAfterMeasure != -1) {
			lifecycleScope.launch(Dispatchers.IO) {
				val statics = fDao.getStatic(mvm.tempServerSnAfterMeasure)
				val insight = fDao.getInsight(mvm.tempServerSnAfterMeasure)
				val result = mvm.convertToFaceResult(this@MainActivity, statics, insight)
//        scrollToView(bd.ibtnMBack, bd.nsvM)
				withContext(Dispatchers.Main) {
					mvm.currentResult.value = result
					navigateToMeasureHistoryInformation()
				}
			}
		}
	}

	private fun exportedMeasureSettings() {
		lifecycleScope.launch(Dispatchers.IO) {
			// 혹시 export된 databases 파일이 있으면, 해당 db에 맞게 넣기
			val latestSn = fDao.getLatestTempServerSn()
			val savedSn = prefs.getLastTempServerSn()
			if (latestSn > savedSn) {
				prefs.initExportTempServerSn(latestSn)
				Log.v(
					"exported",
					"$latestSn > $savedSn: export된 DB의 sn으로 저장합니다. get last temp_server_sn: ${prefs.getLastTempServerSn()}"
				)
			}
		}

		val folders = listOf(
			File("/storage/emulated/0/Pictures/Tangoplus"),
			File("/storage/emulated/0/Movies/Tangoplus")
		)
		val allFiles = folders
			.filter { it.exists() && it.isDirectory }
			.flatMap { it.listFiles()?.toList() ?: emptyList() }
			.map { it.absolutePath }
			.toTypedArray()

		if (allFiles.isEmpty()) {
			Log.w("MediaScanner", "Tangoplus 폴더에 스캔할 파일이 없습니다.")
			return
		}

		Log.v("MediaScanner", "Tangoplus 폴더 스캔 시작 (${allFiles.size}개 파일)")

		MediaScannerConnection.scanFile(
			this@MainActivity,
			allFiles,
			null
		) { path: String, uri: Uri? ->
//            Log.v("MediaScanner", "스캔 완료: $path -> $uri")
		}
	}


	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<out String>,
		grantResults: IntArray
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)

		if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
			if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
				Log.d("Permissions", "저장소 권한 허용됨")
				lifecycleScope.launch(Dispatchers.IO) {
					val fDao = FaceDatabase.getDatabase(this@MainActivity).faceDao()
					val dbDataCount = fDao.getDataCount() // 3
					withContext(Dispatchers.Main) {
						val prefs = PreferenceUtility(this@MainActivity)
						val saveDataCount = prefs.getLastDataCount() // 0
						Log.v("저장할까요?", "$saveDataCount < $dbDataCount DB저장된 갯수가 더 커야함")
						if (saveDataCount < dbDataCount) {
							backupUserData(this@MainActivity)
							Log.v("저장할까요?", "저장합니다.")
							prefs.setDataCount(dbDataCount)
						}
						exportedMeasureSettings()
					}
				}

			} else {
				Toast.makeText(this, "저장소 접근 권한이 필요합니다.", Toast.LENGTH_LONG).show()
			}
		}
	}

	private var backPressedOnce = false
	private val backPressHandler = Handler(Looper.getMainLooper())
	private val backPressRunnable = Runnable { backPressedOnce = false }

	private val onBackPressedCallback = object : OnBackPressedCallback(true) {
		override fun handleOnBackPressed() {
			val fragmentManager = supportFragmentManager

			// 현재 표시 중인 Fragment 확인
			val currentFragment = fragmentManager.findFragmentById(R.id.flMain)

			if (currentFragment is MainFragment) {
				// MainFragment일 때만 앱 종료 로직
				if (backPressedOnce) {
					FaceDatabase.closeDatabase()
					finishAffinity()
					SoundManager.release()
				} else {
					backPressedOnce = true
					Toast.makeText(this@MainActivity, "한 번 더 누르시면 앱이 종료됩니다.", Toast.LENGTH_SHORT).show()
					backPressHandler.postDelayed(backPressRunnable, 1000)
				}
			} else {
				// 그 외 Fragment일 경우 -> 기본 back stack 동작 실행
				fragmentManager.popBackStack()
			}
		}
	}

	data class MeasurementFileInfo(
		val filePrefix: String,  // "9-20251020145150"
		val allFiles: List<File> // jpg(9개) + json(9개) + mp4(2개)
	)


	private fun getLatestMeasurementFiles(): MeasurementFileInfo? {
		try {
			val allFiles = mutableListOf<File>()

			// 1. Pictures/TangoPlus에서 1~9번 각각의 최신 JPG
			val picturesDir = File(
				Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
				"TangoPlus"
			)
			if (picturesDir.exists()) {
				val jpgFiles = getLatestFilesForEachSequence(
					directory = picturesDir,
					extension = "jpg",
					sequenceRange = 1..9
				)
				allFiles.addAll(jpgFiles)
				Log.v("백업", "JPG 파일 ${jpgFiles.size}개: ${jpgFiles.map { it.name }}")
			}

			// 2. Documents/TangoPlus에서 1~9번 각각의 최신 JSON
			val documentsDir = File(
				Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
				"TangoPlus"
			)
			if (documentsDir.exists()) {
				val jsonFiles = getLatestFilesForEachSequence(
					directory = documentsDir,
					extension = "json",
					sequenceRange = 1..9
				)
				allFiles.addAll(jsonFiles)
				Log.v("백업", "JSON 파일 ${jsonFiles.size}개: ${jsonFiles.map { it.name }}")
			}

			// 3. Movies/TangoPlus에서 4~5번 각각의 최신 MP4
			val moviesDir = File(
				Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
				"TangoPlus"
			)
			if (moviesDir.exists()) {
				val mp4Files = getLatestFilesForEachSequence(
					directory = moviesDir,
					extension = "mp4",
					sequenceRange = 4..5
				)
				allFiles.addAll(mp4Files)
				Log.v("백업", "MP4 파일 ${mp4Files.size}개: ${mp4Files.map { it.name }}")
			}

			if (allFiles.isEmpty()) {
				Log.w("백업", "백업할 파일 없음")
				return null
			}

			// 가장 최신 파일의 prefix를 식별자로 사용 (9번 JSON 기준)
			val latestJsonFile = documentsDir.listFiles { file ->
				file.isFile && file.extension == "json" && file.name.startsWith("9-")
			}?.maxByOrNull { it.lastModified() }

			val latestPrefix = latestJsonFile?.nameWithoutExtension ?: "unknown"

			Log.v("백업", "총 ${allFiles.size}개 파일, 식별자: $latestPrefix")

			return MeasurementFileInfo(
				filePrefix = latestPrefix,
				allFiles = allFiles
			)

		} catch (e: Exception) {
			Log.e("백업", "최신 파일 조회 실패", e)
			return null
		}
	}

	private fun getLatestFilesForEachSequence(
		directory: File,
		extension: String,
		sequenceRange: IntRange
	): List<File> {
		val latestFiles = mutableListOf<File>()

		sequenceRange.forEach { sequence ->
			// 해당 시퀀스로 시작하는 파일들 중 가장 최신 것
			val latestFile = directory.listFiles { file ->
				file.isFile &&
					file.extension == extension &&
					file.name.startsWith("$sequence-")
			}?.maxByOrNull { it.lastModified() }

			latestFile?.let {
				latestFiles.add(it)
				Log.v("백업", "시퀀스 $sequence: ${it.name} (${it.lastModified()})")
			}
		}

		return latestFiles
	}

	// 최신 측정 데이터 백업
	private fun backupLatestMeasurementData(
		context: Context,
		fileInfo: MeasurementFileInfo
	) {
		val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
		val backupFileName = "FAE_${fileInfo.filePrefix}.zip"

		try {
			val contentValues = ContentValues().apply {
				put(MediaStore.MediaColumns.DISPLAY_NAME, backupFileName)
				put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
				put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Documents/TangoPlusBackup")
			}
			val uri = context.contentResolver.insert(
				MediaStore.Files.getContentUri("external"),
				contentValues
			)

			uri?.let { fileUri ->
				context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
					ZipOutputStream(outputStream).use { zipOut ->

						// 메타데이터
						val metadata = """
                        {
                            "backup_type": "measurement",
                            "measurement_id": "${fileInfo.filePrefix}",
                            "timestamp": ${System.currentTimeMillis()},
                            "date": "$timestamp",
                            "file_count": ${fileInfo.allFiles.size}
                        }
                    """.trimIndent()

						zipOut.putNextEntry(ZipEntry("backup_info.json"))
						zipOut.write(metadata.toByteArray())
						zipOut.closeEntry()

						// 데이터베이스 (항상 포함)
						val dbDir = File("/data/data/${context.packageName}/databases")
						if (dbDir.exists() && dbDir.canRead()) {
							addDirectoryToZip(zipOut, dbDir, "databases/")
							Log.d("Backup", "Database 파일 압축 완료")
						}

						// 파일들을 종류별로 분류해서 압축
						fileInfo.allFiles.forEach { file ->
							val prefix = when {
								file.absolutePath.contains("Pictures") -> "pictures/"
								file.absolutePath.contains("Documents") -> "documents/"
								file.absolutePath.contains("Movies") -> "movies/"
								else -> ""
							}

							addFileToZip(zipOut, file, prefix)
						}
					}
				}

				Log.d("Backup", "측정 백업 완료: $backupFileName")
				Log.d("Backup", "파일 개수: ${fileInfo.allFiles.size}개")
				Log.d("Backup", "측정 ID: ${fileInfo.filePrefix}")
			}
		} catch (e: Exception) {
			Log.e("Backup", "측정 백업 실패", e)
		}
	}

	// 단일 파일을 ZIP에 추가
	private fun addFileToZip(zipOut: ZipOutputStream, file: File, basePath: String) {
		try {
			val entryName = basePath + file.name
			zipOut.putNextEntry(ZipEntry(entryName))

			FileInputStream(file).use { fis ->
				val buffer = ByteArray(8192)
				var length: Int
				while (fis.read(buffer).also { length = it } > 0) {
					zipOut.write(buffer, 0, length)
				}
			}

			zipOut.closeEntry()
			Log.v("Backup", "파일 추가: ${file.name} (${file.length() / 1024}KB)")
		} catch (e: Exception) {
			Log.e("Backup", "파일 압축 실패: ${file.name}", e)
		}
	}

	// 전체 디렉토리를 ZIP에 추가 (데이터베이스용)
	private fun addDirectoryToZip(
		zipOut: ZipOutputStream,
		directory: File,
		basePath: String
	) {
		val files = directory.listFiles() ?: return

		files.forEach { file ->
			if (file.isDirectory) {
				addDirectoryToZip(zipOut, file, "$basePath${file.name}/")
			} else {
				try {
					val entryName = basePath + file.name
					zipOut.putNextEntry(ZipEntry(entryName))

					FileInputStream(file).use { fis ->
						val buffer = ByteArray(8192)
						var length: Int
						while (fis.read(buffer).also { length = it } > 0) {
							zipOut.write(buffer, 0, length)
						}
					}

					zipOut.closeEntry()
				} catch (e: Exception) {
					Log.e("Backup", "파일 압축 실패: ${file.name}", e)
				}
			}
		}
	}


	// 메모리 체크
	private fun checkMemoryUsage() {
		val activityManager = this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
		val memInfo = ActivityManager.MemoryInfo()
		activityManager.getMemoryInfo(memInfo)

		val usagePercent =
			((memInfo.totalMem - memInfo.availMem).toFloat() / memInfo.totalMem * 100).toInt()

		if (usagePercent >= 89) {
			MaterialAlertDialogBuilder(
				this@MainActivity,
				R.style.ThemeOverlay_App_MaterialAlertDialog
			).apply {
				setTitle("경고")
				setMessage("메모리 사용량이 90%가 넘었습니다\n원활한 사용을 위해 앱 종료 후 재실행을 권장드립니다\n앱을 종료하시겠습니까?")
				setPositiveButton("예") { _, _ ->
					FaceDatabase.closeDatabase()
					finishAffinity()
					SoundManager.release()
				}
				setNegativeButton("아니오") { _, _ -> }
			}
		}
	}
	private var previousTabId = R.id.nav_main

	fun navigateToSubFragment(fragment: Fragment) {
		supportFragmentManager.beginTransaction().apply {
			// 부드러운 Fade In / Fade Out 효과
			// 뒤로가기 했을 때의 애니메이션까지 4개를 적어주는 것이 좋습니다.
			setCustomAnimations(
				R.anim.fade_in,
				android.R.anim.fade_out,
			)
			replace(R.id.flMain, fragment)
			// 핵심: 뒤로 가기 버튼을 누르면 이전 화면(바텀 탭 화면)으로 돌아오게 만듦
			addToBackStack(null)
			commit()
		}
	}

	private fun setCurrentFragment(itemId: Int) {
		val tabOrder = mapOf(
			R.id.nav_main to 0,
			R.id.nav_measure_history to 1,
			R.id.nav_statistics to 2
		)

		val currentPosition = tabOrder[itemId] ?: 0
		val previousPosition = tabOrder[previousTabId] ?: 0

		// 방향 결정 (오른쪽으로 가는지, 왼쪽으로 가는지)
		val isMovingRight = currentPosition > previousPosition

		// 새로운 프래그먼트 생성
		val fragment = when (itemId) {
			R.id.nav_main -> MainFragment()
			R.id.nav_measure_history -> MeasureHistoryFragment()
			R.id.nav_statistics -> StatisticsFragment()
			else -> MainFragment()
		}

		when (fragment) {
			is MainFragment -> fvm.setCurrentFragment(FragmentViewModel.FragmentType.MAIN)
			is MeasureHistoryFragment -> fvm.setCurrentFragment(FragmentViewModel.FragmentType.MEASURE_HISTORY)
			is StatisticsFragment -> fvm.setCurrentFragment(FragmentViewModel.FragmentType.STATISTICS)
		}

		supportFragmentManager.beginTransaction().apply {
			// 방향에 따라 애니메이션 설정
			if (isMovingRight) {
				// 오른쪽으로 이동 (0->1, 1->2)
				setCustomAnimations(
					R.anim.fade_in_right,  // 새 프래그먼트 enter
					R.anim.fade_out_left,  // 기존 프래그먼트 exit
				)
			} else {
				// 왼쪽으로 이동 (2->1, 1->0)
				setCustomAnimations(
					R.anim.fade_in_left,   // 새 프래그먼트 enter
					R.anim.fade_out_right, // 기존 프래그먼트 exit
				)
			}

			replace(R.id.flMain, fragment)
			commit()
		}

		// 현재 상태 저장
		previousTabId = itemId
		selectedTabId = itemId
	}

	// informationFragment로 바로 가는 함수
	fun navigateToMeasureHistoryInformation() {
		// 1. 바텀 네비게이션의 탭을 먼저 Measure History로 변경합니다.
		// (이 코드가 실행되면 자동으로 기존에 만들어둔 setCurrentFragment가 호출되어
		// MeasureHistoryFragment가 바닥에 깔립니다.)
		bd.bnv.selectedItemId = R.id.nav_measure_history

		// 2. 프래그먼트 트랜잭션이 완전히 끝날 때까지 동기화 대기 (매우 중요!)
		// 1번의 뷰 교체가 끝나기도 전에 3번이 실행되어 꼬이는 것을 방지합니다.
		supportFragmentManager.executePendingTransactions()

		// 3. 방금 깔린 MeasureHistoryFragment 위에 InformationFragment를 덮어씌웁니다.
		supportFragmentManager.beginTransaction().apply {
			setCustomAnimations(
				android.R.anim.fade_in, android.R.anim.fade_out,
				android.R.anim.fade_in, android.R.anim.fade_out
			)
			replace(R.id.flMain, InformationFragment())

			// 백스택에 추가하여 뒤로 가기 시 MeasureHistoryFragment로 돌아오게 만듦
			addToBackStack(null)
			commit()
		}
	}
}