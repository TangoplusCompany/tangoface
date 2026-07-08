package com.tangoplus.facebeautyexpert.data.service

//import com.jcraft.jsch.JSch
//import com.jcraft.jsch.Session
//import com.jcraft.jsch.ChannelShell
//import java.io.BufferedReader
//import java.io.InputStreamReader
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import java.util.regex.Pattern
//import android.util.Log
//
//// 실무에서는 터널이 끊기지 않게 session 객체를 전역/클래스 변수로 유지해야 합니다.
//private var sshSession: Session? = null
//
//suspend fun startPinggyTunnel(localPort: Int): String? = withContext(Dispatchers.IO) {
//  try {
//    val jsch = JSch()
//
//    // Pinggy 무료 티어 접속 정보: free@a.pinggy.io (포트 443은 방화벽을 잘 통과합니다)
//    val session: Session = jsch.getSession("free", "a.pinggy.io", 443)
//
//    // 안드로이드 환경에서 호스트 키 검사 생략 설정
//    session.setConfig("StrictHostKeyChecking", "no")
//    session.connect()
//    sshSession = session
//
//    // 리버스 포트 포워딩 등록 (터미널의 -R 0:localhost:8080 옵션과 동일)
//    // 원격 포트를 0으로 지정하면 Pinggy가 고유한 public 도메인을 무작위로 할당합니다.
//    session.setPortForwardingR(0, "127.0.0.1", localPort)
//
//    // Pinggy가 화면에 출력해주는 할당 URL 로그를 읽기 위해 Shell 채널을 엽니다.
//    val channel = session.openChannel("shell") as ChannelShell
//    val inputStream = channel.inputStream
//    channel.connect()
//
//    val reader = BufferedReader(InputStreamReader(inputStream))
//    var line: String?
//    var publicUrl: String? = null
//
//    // Pinggy 서버가 전송하는 터미널 텍스트 로그를 한 줄씩 실시간으로 읽어 파싱합니다.
//    while (reader.readLine().also { line = it } != null) {
//      val currentLine = line ?: break
//
//      // 발급된 도메인 텍스트가 포함된 라인을 감지
//      if (currentLine.contains("https://") && currentLine.contains(".pinggy")) {
//        val pattern = Pattern.compile("https://[a-zA-Z0-9.-]+\\.pinggy\\.[a-zA-Z0-9./~_-]+")
//        val matcher = pattern.matcher(currentLine)
//        if (matcher.find()) {
//          publicUrl = matcher.group()
//          break // 주소를 성공적으로 가졌다면 로그 읽기 루프를 탈출합니다.
//        }
//      }
//    }
//
//    // 주소를 반환하되, 터널을 유지해야 하므로 session.disconnect()를 여기서 호출하면 안 됩니다!
//    return@withContext publicUrl
//  } catch (e: Exception) {
//    Log.e("PinggyTunnel", "터널 실행 중 오류 발생", e)
//    return@withContext null
//  }
//}