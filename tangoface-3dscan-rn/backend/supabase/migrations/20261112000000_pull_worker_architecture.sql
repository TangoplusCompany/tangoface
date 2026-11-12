-- Supabase Migration: 20261112000000_pull_worker_architecture.sql
-- Description: Colab Pull Worker Architecture (claim & complete, timeouts, queue)

-- 1. sessions 테이블 컬럼 보장
ALTER TABLE public.sessions
  ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMPTZ;

-- 2. worker_configs 테이블 컬럼 변경 (worker_url nullable, last_seen 및 busy 보장)
ALTER TABLE public.worker_configs
  ADD COLUMN IF NOT EXISTS last_seen TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS busy BOOLEAN NOT NULL DEFAULT false;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'worker_configs' AND column_name = 'worker_url' AND is_nullable = 'NO'
  ) THEN
    ALTER TABLE public.worker_configs ALTER COLUMN worker_url DROP NOT NULL;
  END IF;
END $$;

-- 3. worker_configs 기본 'colab' 행 보장
INSERT INTO public.worker_configs (worker_key, busy, last_seen)
VALUES ('colab', false, now())
ON CONFLICT (worker_key) DO UPDATE
SET updated_at = now();

-- 4. 원자적 작업 배정 함수 (FOR UPDATE SKIP LOCKED)
CREATE OR REPLACE FUNCTION public.claim_next_uploaded_session()
RETURNS TABLE (
  id UUID,
  upload_path TEXT
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  RETURN QUERY
  UPDATE public.sessions
  SET
    status = 'PROCESSING',
    processing_started_at = now(),
    result_path = sessions.id || '/model.glb'
  WHERE sessions.id = (
    SELECT s.id
    FROM public.sessions s
    WHERE s.status = 'UPLOADED'
    ORDER BY s.uploaded_at ASC NULLS LAST
    FOR UPDATE SKIP LOCKED
    LIMIT 1
  )
  RETURNING sessions.id, sessions.upload_path;
END;
$$;
