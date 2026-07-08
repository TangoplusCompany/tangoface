package com.tangoplus.facebeautyexpert

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.tangoplus.facebeautyexpert.ui.main.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class FaceMeasureFlowTest {

    // 💡 메인 액티비티(또는 사진 선택이 있는 액티비티)를 실행합니다.
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        // 인텐트 가로채기 준비
        Intents.init()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun testFaceMeasurementFlow_and_LogResult() {
        // 1. 갤러리 호출(ACTION_PICK 또는 ACTION_GET_CONTENT)을 가로채서 가짜 결과(테스트 이미지)를 반환하도록 세팅
        val resultData = Intent().apply {
            // 에뮬레이터에 미리 넣어둔 사진의 경로
            data = Uri.parse("file:///storage/sdcard0/Pictures/TangoPlus/0-20260420085905.webp")
        }
        val result = Instrumentation.ActivityResult(Activity.RESULT_OK, resultData)

        // 갤러리 여는 인텐트가 발생하면 위에서 만든 result를 던져라!
        intending(hasAction(Intent.ACTION_PICK)).respondWith(result)
        // 만약 GET_CONTENT를 쓰신다면 아래 코드를 활성화하세요.
        // intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWith(result)


        // 2. Fragment에 있는 '사진 선택' 버튼 클릭 (아이디를 실제 앱에 맞게 수정하세요)
        // onView(withId(R.id.btn_select_photo)).perform(click())


        // 3. 사진이 선택되면 uCrop 화면으로 넘어갑니다.
        // uCrop의 기본 '완료(자르기)' 버튼 아이디는 보통 menu_crop 입니다.
        // 뷰가 뜰 때까지 UI Automator로 잠시 기다렸다가 클릭합니다.
        val cropButton = device.findObject(UiSelector().resourceId("com.yalantis.ucrop:id/menu_crop"))
        if (cropButton.waitForExists(5000)) { // 5초 대기
            cropButton.click()
        }


        // 4. uCrop이 완료되면 LoadingDialogFragment가 뜨고 얼굴 분석 로직(classifyFaceShape)이 돕니다.
        // 이 과정이 비동기(Coroutine)로 돌기 때문에 테스트 코드가 바로 종료되지 않도록 잠시 대기합니다.
        // (원래는 IdlingResource를 써야 하지만, 로그 확인용이므로 Thread.sleep 사용)
        Thread.sleep(10000) // 10초 대기 (분석 시간에 맞춰 조절)


        // 💡 5. 완료!
        // 이제 Logcat 창 필터에 "얼굴여백분석" 또는 "얼굴형최종결과"를 검색해두시면
        // 이 테스트를 Run 하는 것만으로 자동으로 사진을 고르고 자르고 로직이 돌아간 뒤 로그가 찍힙니다.
    }
}