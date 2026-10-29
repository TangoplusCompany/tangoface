package com.tangoplus.tangoface.data.service

//import android.util.Log
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//
//fun initializeSystem() {
//  // 1. 이미 작성하신 Ktor 웹서버 가동 (8080 포트)
//  startAndroidWebServer()
//
//  // 2. 비동기로 Pinggy 외부 공인 터널 개설 요청
//  CoroutineScope(Dispatchers.Main).launch {
//    Log.d("System", "Pinggy 외부 연결 터널 개설 중...")
//    val publicUrl = startPinggyTunnel(8080)
//
//    if (publicUrl != null) {
//      // 3. 터널 주소가 나왔다면 이를 독자적인 Next.js 웹앱의 쿼리 스트링으로 결합
//      val myNextJsWebUrl = "https://your-analysis-web.com/result?api=$publicUrl"
//
//      // 4. 이 최종 결합 주소를 화면에 QR 코드로 출력
//      Log.d("System", "모든 연결 준비 완료! 생성된 전체 주소: $myNextJsWebUrl")
//    } else {
//      Log.e("System", "외부 터널 개설 실패. 로컬 네트워크로만 접속 가능할 수 있습니다.")
//    }
//  }
//}