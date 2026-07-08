package com.tangoplus.facebeautyexpert.data.service

//import io.ktor.server.engine.*
//import io.ktor.server.netty.*
//import io.ktor.server.plugins.cors.routing.*
//import io.ktor.server.plugins.contentnegotiation.*
//import io.ktor.serialization.kotlinx.json.*
//import io.ktor.server.routing.*
//import io.ktor.server.response.*
//import io.ktor.http.*
//import io.ktor.server.application.call
//import io.ktor.server.application.install
//
//fun startAndroidWebServer() {
//  embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
//    // 1. Next.js 웹앱의 접근을 허용하는 CORS 설정
//    install(CORS) {
//      anyHost() // 개발 단계 테스트용 (운영 시에는 Next.js 도메인만 지정 권장)
//      allowHeader(HttpHeaders.ContentType)
//      allowMethod(HttpMethod.Get)
//      allowMethod(HttpMethod.Post)
//    }
//
//    // 2. JSON 데이터 반환을 위한 설정
//    install(ContentNegotiation) {
//      json()
//    }
//
//    // 3. API 라우팅 (Room DB 연동부)
//    routing {
//      get("/api/analysis-result") {
//        // TODO: Room DB에서 결과를 조회하는 로직
//        val dummyResult = mapOf("status" to "success", "faceScore" to 95)
//        call.respond(dummyResult)
//      }
//    }
//  }.start(wait = false) // 앱이 멈추지 않게 비동기로 실행
//}