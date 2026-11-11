-- Supabase Migration: 20261111000000_worker_queue_and_timeout.sql
-- Description: 워커 혼잡도(busy, queue) 및 세션별 processing 시작 시각 / 연속 not_found 횟수 추적

-- 1. worker_configs 테이블에 busy, queue 컬럼 추가
ALTER TABLE public.worker_configs
  ADD COLUMN IF NOT EXISTS busy BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS queue INTEGER NOT NULL DEFAULT 0;

-- 2. sessions 테이블에 processing_started_at, not_found_count 컬럼 추가
ALTER TABLE public.sessions
  ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS not_found_count INTEGER NOT NULL DEFAULT 0;
