# TangoFace 3D Scan (React Native + Web)

모바일 화면 기준의 React Native(Expo SDK 58) 프로젝트입니다. 웹 브라우저 및 Android, iOS를 모두 지원하며, 카메라 접근 및 스캔 가이드라인 UI가 구성되어 있습니다.

## 🚀 주요 구성 및 특징
- **Expo SDK 58 (React Native 0.88 + React 19 + TypeScript)**
- **웹 모바일 뷰 최적화**: PC 웹 환경에서는 모바일 규격(최대 너비 440px)으로 중앙 정렬되며, 실제 모바일 기기 및 웹 브라우저에서는 100% 화면에 맞춰 반응형으로 동작
- **카메라 및 파노라마 수평 가이드 연동 (`expo-camera`, `expo-sensors`)**: 
  - **상단 파노라마 트랙 (Panorama Mini-Track)**: 아이폰/스마트폰 파노라마 모드 스타일의 가로 뷰파인더 레일과 수평 기준선 및 진행 방향 화살표
  - **중앙 인공 수평선 (Artificial Horizon Level Gauge)**: 전문가용 DSLR/항공기 수평계 스타일의 좌/우 고정 날개와 동적 회전/상하 수평선
  - **현재 위치 기준 영점 캘리브레이션 (Zero Calibration)**: 촬영 시작 시점 또는 사용자가 들고 있는 현재 각도를 0° 기준으로 자동 설정
  - **실시간 피드백 뱃지**: 수평 완벽(녹색), 좌우/상하 기울임 안내 텍스트 및 상세 각도(기울기, 상하, 회전) 표시
  - **PC 웹 테스트용 가상 시뮬레이터**: 자이로 센서가 없는 PC 웹 브라우저에서도 버튼 클릭으로 수평 가이드 UI를 직접 테스트 가능

---

## 🛠 실행 명령어

### 1. 웹 개발 서버 시작 (모바일 브라우저 / PC 브라우저)
```bash
npm run web
# 또는
npx expo start --web
```

### 2. 웹 정적 빌드 (배포용 dist 생성)
```bash
npm run build:web
# 또는
npx expo export -p web
```
> 빌드 결과물은 `dist/` 폴더에 생성됩니다.

### 3. 타입 검사
```bash
npm run typecheck
```

### 4. 향후 모바일 네이티브 실행 (Android / iOS)
```bash
# Android
npm run android

# iOS (macOS 환경)
npm run ios
```
> 네이티브 빌드가 필요할 때는 EAS Build(`npx eas-cli@latest build`) 또는 `npx expo prebuild`를 활용할 수 있습니다.
