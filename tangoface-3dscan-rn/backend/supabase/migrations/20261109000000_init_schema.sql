-- Supabase Migration: 20261109000000_init_schema.sql
-- Description: Face 3D Reconstruction Sessions, Pair Attempts, Storage Buckets, and Strict RLS

-- 1. UUID 확장 활성화
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 2. Sessions 테이블 생성
CREATE TABLE IF NOT EXISTS public.sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code VARCHAR(6),                                      -- 6자리 일회용 페어링 번호 (연결 성공 시 즉시 NULL)
  status VARCHAR(20) NOT NULL DEFAULT 'CREATED' CHECK (
    status IN ('CREATED', 'PAIRED', 'UPLOADED', 'PROCESSING', 'DONE', 'FAILED')
  ),
  fail_reason TEXT,                                     -- 실패 사유
  session_secret_hash VARCHAR(64) NOT NULL,             -- 태블릿 인증용 SHA-256 해시
  pair_token_hash VARCHAR(64),                          -- 폰 인증용 SHA-256 해시 (페어링 시 저장)
  upload_path TEXT,                                     -- 업로드 상대 경로: {session_id}/{randomUUID}.mp4
  result_path TEXT,                                     -- 결과 모델 경로: {session_id}/model.glb
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,                      -- 세션 만료 시각 (기본 생성 + 24시간)
  paired_at TIMESTAMPTZ,                                -- 페어링 성공 시각
  uploaded_at TIMESTAMPTZ                               -- 업로드 확인 완료 시각
);

-- 인덱스 생성 (조회 속도 및 만료 세션 정리 최적화)
CREATE INDEX IF NOT EXISTS idx_sessions_active_code ON public.sessions (code) WHERE code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON public.sessions (expires_at);
CREATE INDEX IF NOT EXISTS idx_sessions_pair_token_hash ON public.sessions (pair_token_hash) WHERE pair_token_hash IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_sessions_status ON public.sessions (status);

-- 3. Pair Attempts 테이블 생성 (번호 대입 공격 방어용 감사 로그)
CREATE TABLE IF NOT EXISTS public.pair_attempts (
  id BIGSERIAL PRIMARY KEY,
  ip_hash VARCHAR(64) NOT NULL,                         -- SHA-256(IP + IP_HASH_SALT)
  succeeded BOOLEAN NOT NULL,                           -- 성공 여부 (true/false)
  attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()       -- 시도 시각
);

-- 인덱스 생성 (IP별 최근 시도 및 전역 시도 집계 최적화)
CREATE INDEX IF NOT EXISTS idx_pair_attempts_ip_time ON public.pair_attempts (ip_hash, attempted_at);
CREATE INDEX IF NOT EXISTS idx_pair_attempts_time ON public.pair_attempts (attempted_at);

-- 4. 보안: 모든 테이블에 RLS(Row Level Security) 강제 활성화 (정책 미생성 = anon/authenticated 전면 차단)
-- 모든 데이터 접근은 오직 서버의 service_role 키를 통해서만 가능합니다.
ALTER TABLE public.sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pair_attempts ENABLE ROW LEVEL SECURITY;

-- 5. Storage 버킷 설정 (videos: 200MB 제한, mp4/mov 허용 / results: 100MB, glb 허용)
-- 둘 다 private (public = false)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'videos',
  'videos',
  false,
  209715200, -- 200MB (200 * 1024 * 1024)
  ARRAY['video/mp4', 'video/quicktime']
)
ON CONFLICT (id) DO UPDATE SET
  public = false,
  file_size_limit = 209715200,
  allowed_mime_types = ARRAY['video/mp4', 'video/quicktime'];

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'results',
  'results',
  false,
  104857600, -- 100MB (100 * 1024 * 1024)
  ARRAY['model/gltf-binary']
)
ON CONFLICT (id) DO UPDATE SET
  public = false,
  file_size_limit = 104857600,
  allowed_mime_types = ARRAY['model/gltf-binary'];
