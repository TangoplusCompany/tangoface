# TangoFace Android 얼굴분석 앱 

## 개발 환경

| 항목                        | 버전                                  |
| --------------------------- | ------------------------------------- |
| Android Studio              | Android Studio Panda 2 | 2025.3.2 |
| AGP (Android Gradle Plugin) | 8.10.0                                |
| Kotlin                      | 2.0.21                                |
| NDK                         | 21.4.7075529                          |
| CMake                       | 3.18.1                                |
| JVM Target                  | Java 11                               |
| Min SDK                     | 29 (Android 10)                       |
| Target SDK                  | 35 (Android 15)                       |
| Compile SDK                 | 35                                    |

---

## 프로젝트 구조

```
project-root/
├── app/                        # 메인 앱 모듈
│   ├── src/main/cpp/           # Native C++ 코드 (CMake)
│   ├── libs/                   # 로컬 .aar 라이브러리
│   └── build.gradle.kts        # 앱 빌드 설정
├── obsensor_jni/               # OrbbecSDK JNI 라이브러리 모듈 (Orbbec SDK)
│   └── build.gradle            # JNI 모듈 빌드 설정
├── keystore/
│   └── signature.properties    # 서명 키 설정 (Git 제외)
├── gradle/
│   └── libs.versions.toml      # Version Catalog
└── build.gradle.kts            # 루트 빌드 설정
```

---

## 빌드 방법

### 1. 사전 요구사항

- Android Studio Narwhal Feature Drop 2025.1.2 Patch 2 이상
- NDK `21.4.7075529` 설치
  - Android Studio → SDK Manager → SDK Tools → NDK (Side by side)
- CMake `3.18.1` 설치
  - Android Studio → SDK Manager → SDK Tools → CMake

### 2. 빌드 실행

```bash
# Debug 빌드
./gradlew assembleDebug

# Release 빌드
./gradlew assembleRelease
```

---

## 주요 라이브러리

### UI / 애니메이션

| 라이브러리                | 버전   | 용도                 |
| ------------------------- | ------ | -------------------- |
| Lottie                    | 6.4.0  | 애니메이션           |
| Shimmer                   | 0.5.0  | 로딩 효과            |
| Balloon                   | 1.6.4  | 툴팁                 |
| ProgressView              | 1.1.3  | 프로그레스 바        |
| MPAndroidChart            | 3.1.0  | 차트                 |
| SubsamplingScaleImageView | 3.10.0 | 고해상도 이미지 뷰어 |

### 카메라 / 미디어

| 라이브러리       | 버전   | 용도                 |
| ---------------- | ------ | -------------------- |
| CameraX          | 1.5.0  | 카메라               |
| Media3 ExoPlayer | 1.5.1  | 동영상 재생          |
| ExoPlayer        | 2.19.1 | 동영상 재생 (레거시) |

### 네트워크

| 라이브러리      | 버전   | 용도          |
| --------------- | ------ | ------------- |
| Retrofit2       | 2.9.0  | REST API      |
| OkHttp3 Logging | 4.12.0 | 네트워크 로깅 |
| Gson            | 2.11.0 | JSON 파싱     |

### 데이터베이스

| 라이브러리 | 버전  | 용도          |
| ---------- | ----- | ------------- |
| Room       | 2.8.1 | 로컬 DB (ORM) |
| SQLCipher  | 4.5.4 | DB 암호화     |

### AI / ML

| 라이브러리             | 버전    | 용도           |
| ---------------------- | ------- | -------------- |
| MediaPipe Tasks Vision | 0.10.26 | 자세/신체 인식 |

### Firebase

| 라이브러리               | 용도          |
| ------------------------ | ------------- |
| Firebase BOM 34.1.0      | BOM 관리      |
| Firebase Analytics       | 사용자 분석   |
| Firebase Crashlytics NDK | 크래시 리포트 |

---

## ABI 지원

```
arm64-v8a    ← 64비트 ARM (최신 기기)
armeabi-v7a  ← 32비트 ARM (구형 기기 호환)
```

---

## 버전 정보

| 항목         | 값                  |
| ------------ | ------------------- |
| Version Name | 2.0.4               |
| Min SDK      | Android 10 (API 29) |
| Target SDK   | Android 15 (API 35) |
