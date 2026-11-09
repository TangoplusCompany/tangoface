// src/lib/rateLimiter.ts
import { SupabaseClient } from '@supabase/supabase-js';

const IP_WINDOW_MINUTES = 10;
const IP_MAX_FAILURES = 5;

const GLOBAL_WINDOW_SECONDS = 60;
const GLOBAL_MAX_FAILURES = 30; // 1분간 전역 실패 급증 임계치

export interface RateLimitCheckResult {
  allowed: boolean;
  remaining: number;
  retryAfterSeconds?: number;
  reason?: 'IP_RATE_LIMITED' | 'GLOBAL_RATE_LIMITED';
}

/**
 * IP별 실패 횟수(10분 5회) 및 전역 급증(1분 30회) 검증
 */
export async function checkPairRateLimit(
  supabase: SupabaseClient,
  ipHash: string
): Promise<RateLimitCheckResult> {
  const now = new Date();
  const ipWindowStart = new Date(now.getTime() - IP_WINDOW_MINUTES * 60 * 1000).toISOString();
  const globalWindowStart = new Date(now.getTime() - GLOBAL_WINDOW_SECONDS * 1000).toISOString();

  // 1. IP별 실패 횟수 조회
  const { count: ipFailures, error: ipError } = await supabase
    .from('pair_attempts')
    .select('id', { count: 'exact', head: true })
    .eq('ip_hash', ipHash)
    .eq('succeeded', false)
    .gte('attempted_at', ipWindowStart);

  if (ipError) {
    console.error('IP 레이트 리밋 조회 오류:', ipError);
  }

  const currentIpFails = ipFailures || 0;
  if (currentIpFails >= IP_MAX_FAILURES) {
    return {
      allowed: false,
      remaining: 0,
      retryAfterSeconds: IP_WINDOW_MINUTES * 60,
      reason: 'IP_RATE_LIMITED',
    };
  }

  // 2. 전역 실패 급증 공격 조회
  const { count: globalFailures, error: globalError } = await supabase
    .from('pair_attempts')
    .select('id', { count: 'exact', head: true })
    .eq('succeeded', false)
    .gte('attempted_at', globalWindowStart);

  if (globalError) {
    console.error('전역 레이트 리밋 조회 오류:', globalError);
  }

  const currentGlobalFails = globalFailures || 0;
  if (currentGlobalFails >= GLOBAL_MAX_FAILURES) {
    return {
      allowed: false,
      remaining: 0,
      retryAfterSeconds: 30,
      reason: 'GLOBAL_RATE_LIMITED',
    };
  }

  return {
    allowed: true,
    remaining: IP_MAX_FAILURES - currentIpFails,
  };
}

/**
 * 페어링 시도 결과 기록
 */
export async function recordPairAttempt(
  supabase: SupabaseClient,
  ipHash: string,
  succeeded: boolean
): Promise<void> {
  const { error } = await supabase.from('pair_attempts').insert({
    ip_hash: ipHash,
    succeeded,
    attempted_at: new Date().toISOString(),
  });

  if (error) {
    console.error('시도 기록 저장 오류:', error);
  }
}
