# TangoFace — Android 얼굴분석 & 3D 얼굴 스캔 플랫폼

TangoFace는 **Android 태블릿 얼굴·체형 분석 앱**, **모바일/웹 3D 얼굴 파노라마 스캐너**, 그리고 **Next.js + Supabase + Colab GPU 3D 복원 백엔드**로 구성된 멀티 모듈 프로젝트입니다.

---

## 프로젝트 구조

```text
tangoface/
├── xml/                        # 메인 Android 태블릿 앱 (Kotlin + XML ViewBinding + C++ NDK + OrbbecSDK)
│   ├── app/                    # 메인 애플리케이션 모듈
│   │   ├── src/main/cpp/       # Native C++ 영상/포인트클라우드 처리 (CMake)
│   │   ├── src/main/assets/    # 온디바이스 AI 모델(.task, .onnx) 및 Lottie JSON
│   │   └── libs/               # 로컬 .aar 라이브러리
│   └── obsensor_jni/           # OrbbecSDK 3D 뎁스 센서 JNI 모듈
│
├── compose/                    # Android 앱 Jetpack Compose 버전 모듈
│   ├── app/
│   └── obsensor_jni/
│
└── tangoface-3dscan-rn/        # 3D 얼굴 스캔 웹/모바일 클라이언트 & 서버리스 백엔드
    ├── src/                    # Expo React Native Web 스캐너 (X-Z 평면 자이로 가이드, 영상 첨부)
    └── backend/                # Next.js App Router API (Vercel 배포, Supabase & Colab 워커 연동)
```

---

## 3D 얼굴 스캔 전체 아키텍처

```text
[Android 태블릿 (xml/)]
   │ 1. POST /api/sessions (6자리 일회용 PIN 발급)
   │ 5. GET /api/sessions/{id}/status (2초 폴링 + 네트워크 끊김 자동 재시도)
   │ 6. GET /api/sessions/{id}/result -> GLB 자동 다운로드 (filesDir/models/{id}.glb)
   ▼
[Next.js Backend (Vercel)] ◀── 30초 heartbeat ──▶ [Google Colab FastAPI GPU Worker]
   │ 2. POST /api/sessions/pair                        ▲ (trycloudflare.com 터널)
   │ 3. POST /api/uploads/presign                      │ POST /jobs (영상 다운로드 -> 3D 복원)
   │ 4. POST /api/uploads/complete                     │ PUT results/{id}/model.glb
   ▲                                                   ▼
[스마트폰 웹 스캐너 (tangoface-3dscan-rn/)] ──▶ [Supabase (PostgreSQL & Storage)]
  (X-Z 평면 100° 가이드 촬영 / 최대 30초·50MB 첨부)     - videos 버킷 (Private, max 200MB)
                                                         - results 버킷 (Private, max 100MB, model/gltf-binary)
```

---

## 모듈별 개발 환경

### 1. Android 메인 앱 (`xml/`)
| 항목 | 버전 / 설정 |
| :--- | :--- |
| **AGP (Android Gradle Plugin)** | `8.13.2` |
| **Kotlin** | `2.0.21` |
| **Compile / Target SDK** | `36` |
| **Min SDK** | `30` (Android 11) |
| **NDK / CMake** | `21.4.7075529` / `3.18.1` |
| **JVM Target** | Java `11` |
| **Version Name** | `2.0.4` |
| **지원 ABI** | `arm64-v8a`, `armeabi-v7a` |

### 2. Android Compose 모듈 (`compose/`)
| 항목 | 버전 / 설정 |
| :--- | :--- |
| **AGP / Kotlin** | `9.1.1` / `2.2.10` |
| **Compile / Target / Min SDK** | `36` / `36` / `31` |
| **NDK** | `21.4.7075529` |

### 3. 3D 스캔 웹 클라이언트 & 백엔드 (`tangoface-3dscan-rn/`)
| 영역 | 주요 기술 스택 |
| :--- | :--- |
| **Frontend (`tangoface-3dscan-rn/`)** | Expo `~58.0.23`, React `19.3.0`, React Native `0.88.1`, React Native Web `^0.21.4`, TypeScript `~6.0.3` |
| **Backend (`backend/`)** | Next.js `^15.1.7`, `@supabase/supabase-js` `^2.49.1`, Zod `^3.24.2`, Vitest `^3.0.7` |

---

## 온디바이스 AI / ML 모델 (`app/src/main/assets/`)

| 모델 / 에셋 | 용도 | 비고 |
| :--- | :--- | :--- |
| `face_landmarker.task` | 얼굴 랜드마크 및 표정/대칭 추출 | [MediaPipe Face Landmarker](https://github.com/google-ai-edge/mediapipe-samples/blob/main/examples/face_landmarker/ios/FaceLandmarker/face_landmarker.task) |
| `pose_landmarker_full.task` | 전신·자세 랜드마크 인식 | [MediaPipe Pose Landmarker](https://github.com/google-ai-edge/mediapipe-samples/blob/main/examples/pose_landmarker/ios/PoseLandmarker/pose_landmarker_full.task) |
| `modnet.onnx` | 인물 배경 분리 (Matting / Segmentation) | ONNX Runtime (`1.17.0`) |
| `focusing_screen.json`, `jaw_guide_*.json` | 촬영 가이드 UI 애니메이션 | Lottie (`6.4.0`) |

---

## Android 주요 라이브러리 (`xml/`)

### UI / 시각화
| 라이브러리 | 버전 | 용도 |
| :--- | :--- | :--- |
| Material Components / AppCompat | `1.12.0` / `1.7.1` | Android UI 컴포넌트 |
| Lottie | `6.4.0` | 벡터 가이드 애니메이션 |
| Shimmer | `0.5.0` | 로딩 스켈레톤 효과 |
| Balloon | `1.6.4` | 툴팁 UI |
| ProgressView | `1.1.3` | 진행률 프로그레스 바 |
| MPAndroidChart | `v3.1.0` | 분석 차트 시각화 |
| SubsamplingScaleImageView | `3.10.0` | 고해상도 이미지 줌 뷰어 |
| BlurView | `3.2.0` | 실시간 블러 오버레이 |

### 카메라 / 미디어 / 3D 센서
| 라이브러리 | 버전 | 용도 |
| :--- | :--- | :--- |
| CameraX | `1.5.0` | 카메라 캡처 및 녹화 |
| Media3 ExoPlayer | `1.5.1` | 미디어/가이드 영상 재생 |
| OrbbecSDK (`obsensor_jni`) | Native JNI | 3D 뎁스/컬러 카메라 연동 |

### 네트워크 / DB / AI / Firebase
| 라이브러리 | 버전 | 용도 |
| :--- | :--- | :--- |
| Retrofit2 / OkHttp3 Logging / Gson | `2.9.0` / `4.12.0` / `2.11.0` | REST API 통신, 폴링 및 GLB 스트리밍 다운로드 |
| Room / SQLCipher | `2.8.1` / `4.5.4` | 로컬 ORM 데이터베이스 및 암호화 |
| MediaPipe Tasks Vision | `0.10.26` | 온디바이스 얼굴/자세 인식 |
| ONNX Runtime Android | `1.17.0` | `modnet.onnx` 세그멘테이션 추론 |
| Google Generative AI SDK | `0.9.0` | Gemini API 연동 |
| Firebase BOM (Analytics, Crashlytics NDK) | `34.1.0` | 분석 및 네이티브 크래시 모니터링 |

---

## 빌드 및 실행 방법

### 1. Android 앱 (`xml/`)
**사전 요구사항**
- Android Studio에서 NDK `21.4.7075529`, CMake `3.18.1` 설치 (SDK Manager → SDK Tools)
- `xml/local.properties`에 `GEMINI_API_KEY=...` 설정 및 `xml/keystore/` 서명 키 파일 확인

```bash
cd xml
./gradlew.bat compileDebugKotlin   # Kotlin 컴파일 검증
./gradlew.bat assembleDebug        # Debug APK 빌드
```

### 2. 3D 스캔 웹 클라이언트 (`tangoface-3dscan-rn/`)
```bash
cd tangoface-3dscan-rn
npm install
npm run web          # 로컬 개발 서버 실행
npm run build:web    # 웹 정적 번들 빌드 (dist/)
```

### 3. Next.js 백엔드 (`tangoface-3dscan-rn/backend/`)
환경변수 템플릿은 `tangoface-3dscan-rn/backend/.env.example`을 참고하세요. (Supabase 키, `TABLET_API_KEY`, `WORKER_SECRET`, `WORKER_REGISTER_SECRET` 등 필요)

```bash
cd tangoface-3dscan-rn/backend
npm install
npm run typecheck    # TypeScript 타입 검사
npm test             # Vitest 단위 테스트 실행
npm run build        # Next.js 프로덕션 빌드
```
