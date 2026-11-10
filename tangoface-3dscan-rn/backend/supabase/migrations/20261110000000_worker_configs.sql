-- Supabase Migration: 20261110000000_worker_configs.sql
-- Description: Colab FastAPI 동적 터널 URL 저장용 워커 설정 테이블

CREATE TABLE IF NOT EXISTS public.worker_configs (
  worker_key VARCHAR(50) PRIMARY KEY,
  worker_url TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 보안: RLS 활성화 (service_role 키로만 접근 가능)
ALTER TABLE public.worker_configs ENABLE ROW LEVEL SECURITY;
