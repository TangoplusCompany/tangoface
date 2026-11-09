# TangoFace 3D Reconstruction Backend (Next.js + Supabase + Vercel)

얼굴 3D 복원 서비스의 **세션 관리, 태블릿-폰 페어링, 영상 직접 업로드(Signed Upload URL), 상태 관리**를 처리하는 Next.js(App Router) 기반 백엔드 API입니다.  
배포 타깃은 **Vercel**이며, **Supabase(Postgres + Storage)**를 데이터베이스 및 파일 스토리지로 사용합니다.

---

## 🛠 기술 스택
- **프레임워크**: Next.js 15 (App Router, Route Handlers, Node.js 런타임)
- **데이터베이스**: Supabase Postgres (Strict RLS 적용, `service_role` 전용 접근)
- **오브젝트 스토리지**: Supabase Storage (`createSignedUploadUrl` 직접 업로드)
- **보안**: Node.js `crypto.randomBytes`, `timingSafeEqual`, SHA-256 해시, IP 솔트 해싱, 2단계 Brute-force 방어
- **입력 검증**: Zod
- **스케줄러**: Vercel Cron (`vercel.json`)
- **테스트**: Vitest (상태 전이 머신, 페어링 만료, 레이트 리밋, 웹훅 멱등성)

---

## 📋 사용자가 직접 수행해야 하는 수동 작업 가이드 (순서대로 진행)

> ⚠️ **보안 주의**: 비밀 키(service_role 키, 시크릿 등)는 절대 채팅창이나 공개 저장소에 공유하지 마시고 개인 `.env.local` 및 Vercel 대시보드에만 안전하게 보관하세요.

---

### 1단계: Supabase 프로젝트 생성 (카드 불필요 ⭐)
1. **[supabase.com](https://supabase.com/)** 에 로그인합니다 (무료 티어 이용 가능).
2. **[New Project]** (새 프로젝트)를 클릭합니다:
   - **Name**: `tangoface`
   - **Database Password**: 임의의 강력한 비밀번호 설정
   - **Region**: `Northeast Asia (Seoul)` 또는 `Tokyo` 선택
3. 프로젝트 생성이 완료될 때까지 약 1~2분 대기합니다.

---

### 2단계: 데이터베이스 마이그레이션 적용
1. Supabase 대시보드 좌측 메뉴에서 **[SQL Editor]** 아이콘을 클릭합니다.
2. **[New query]** 버튼을 누릅니다.
3. 프로젝트 내 **[`backend/supabase/migrations/20261109000000_init_schema.sql`](file:///c:/Users/tangoplus/Desktop/Project/tangoface/tangoface-3dscan-rn/backend/supabase/migrations/20261109000000_init_schema.sql)** 파일의 내용을 전체 복사하여 붙여넣습니다.
4. **[Run]** (실행) 버튼을 클릭합니다.
   - `sessions` 테이블, `pair_attempts` 테이블이 생성됩니다.
   - 모든 테이블에 **RLS가 켜지고 정책이 생성되지 않아** 외부의 익명(anon) 접근이 전면 차단됩니다.
   - `videos`(200MB 제한, mp4/mov 허용) 및 `results`(100MB 제한, glb 허용) 비공개(Private) 버킷이 자동 설정됩니다.

---

### 3단계: Supabase API 키 확인
1. 좌측 사이드바 맨 아래 **[Project Settings]** (톱니바퀴 아이콘) > **[API]** 메뉴로 이동합니다.
2. 아래 2가지 값을 확인합니다:
   - **Project URL**: `https://xxxx.supabase.co`
   - **Project API keys** 섹션의 **`service_role`** (Secret 키 - reveal 클릭)
   *(주의: 공개 키 `anon`이 아닌 **`service_role`** 키를 사용합니다)*

---

### 4단계: 로컬 개발 환경변수 설정 (`.env.local`)
`backend/` 폴더 내에 `.env.local` 파일을 생성하고 아래 내용을 입력합니다:

```bash
# backend/.env.local 파일

# 1. Supabase 설정 (3단계에서 확인한 값)
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your_service_role_secret_key

# 2. 보안 솔트 및 시크릿 (임의의 긴 랜덤 문자열 입력)
IP_HASH_SALT=generate_a_random_32_char_salt_for_ip_hashing
RUNPOD_WEBHOOK_SECRET=generate_a_random_runpod_webhook_secret
CRON_SECRET=generate_a_random_vercel_cron_secret

# 3. CORS 허용 도메인 (쉼표 구분, 와일드카드 * 금지)
ALLOWED_ORIGINS=http://localhost:8081,http://localhost:3000,https://tangoface.com
```

---

### 5단계: Vercel 배포 및 환경변수 등록

1. **GitHub에 코드 푸시** (※ `.env.local`은 `.gitignore`에 등록되어 있어 올라가지 않습니다).
2. **[vercel.com](https://vercel.com/)** 에 로그인 후 **[Add New...] > [Project]** 로 이동하여 저장소를 연결합니다.
3. **Root Directory**를 `backend`로 설정합니다.
4. **Environment Variables** (환경변수) 설정에 4단계에서 작성한 5가지 변수를 그대로 추가합니다:
   - `SUPABASE_URL`
   - `SUPABASE_SERVICE_ROLE_KEY`
   - `IP_HASH_SALT`
   - `RUNPOD_WEBHOOK_SECRET`
   - `CRON_SECRET`
   - `ALLOWED_ORIGINS`
5. **[Deploy]** (배포) 버튼을 누릅니다.

---

### ⏰ Vercel 플랜별 Cron 실행 빈도 확인 사항 (중요)

프로젝트 루트의 `vercel.json`에 `/api/cron/cleanup` 작업이 정의되어 있습니다:
```json
{
  "crons": [
    {
      "path": "/api/cron/cleanup",
      "schedule": "0 * * * *"
    }
  ]
}
```
- **Vercel Hobby (무료 플랜)**: Cron은 **하루 1회(Daily)** 까지만 실행이 지원됩니다. (예: `0 0 * * *` - 매일 자정 1회)
- **Vercel Pro (유료 플랜)**: 시간 단위 또는 분 단위 Cron(`0 * * * *`)이 지원됩니다.
> 무료 플랜을 사용 중이신 경우 `vercel.json`의 스케줄을 `"schedule": "0 0 * * *"`로 변경하시면 Vercel 배포 시 경고 없이 정상 반영됩니다.

---

## 📡 API 엔드포인트 명세서

| 메서드 | 경로 | 인증 헤더 | 설명 |
|---|---|---|---|
| `POST` | `/api/sessions` | 없음 | 태블릿용 세션 생성. 6자리 번호(만료 10분), `session_id`, `session_secret` 반환 |
| `POST` | `/api/sessions/pair` | 없음 (IP Rate Limit) | 폰에서 6자리 번호로 연결. `pair_token` 반환 및 페어링 번호 즉시 영구 무효화 |
| `POST` | `/api/uploads/presign` | `Authorization: Bearer <pair_token>` | 폰에서 5분 만료 서명 업로드 URL 발급 (`{session_id}/{rand}.mp4`) |
| `POST` | `/api/uploads/complete` | `Authorization: Bearer <pair_token>` | 폰에서 업로드 완료 알림. 스토리지 파일 존재/크기(200MB 이하) 검증 후 `UPLOADED` 전이 (RunPod 호출 인터페이스 트리거) |
| `GET` | `/api/sessions/[id]/status` | `X-Session-Secret: <secret>` | 태블릿이 세션 상태 폴링 (`CREATED` ~ `DONE` / `FAILED`) |
| `GET` | `/api/sessions/[id]/result` | `X-Session-Secret: <secret>` | 상태가 `DONE`일 때만 GLB 3D 결과 다운로드용 5분 만료 서명 URL 반환 |
| `POST` | `/api/webhooks/runpod` | `X-Runpod-Secret: <secret>` | RunPod 완료/실패 웹훅 수신 (`PROCESSING` -> `DONE` 또는 `FAILED`). 중복 호출 시 멱등 처리 |
| `GET` | `/api/cron/cleanup` | `Authorization: Bearer <CRON_SECRET>` | Vercel Cron에 의해 호출되어 만료 세션 및 24시간 지난 원본 영상 삭제 |

---

## 🧪 로컬 테스트 및 개발 서버 실행

```bash
cd backend

# 1. 타입 검사
npm run typecheck

# 2. Vitest 단위 테스트 실행 (19개 테스트)
npm test

# 3. 로컬 개발 서버 시작 (http://localhost:3000)
npm run dev
```
