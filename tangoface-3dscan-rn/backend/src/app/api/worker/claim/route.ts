// src/app/api/worker/claim/route.ts
import { NextRequest } from 'next/server';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import { jsonWithCors, handleOptionsCors } from '@/lib/cors';
import {
  verifyWorkerSecret,
  checkWorkerRateLimit,
  claimNextJob,
} from '@/lib/workerPull';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function OPTIONS(request: NextRequest) {
  return handleOptionsCors(request);
}

/**
 * POST /api/worker/claim
 * Colab 워커가 주기적으로 작업을 가져가고(claim) 하트비트를 보고
 * 인증: x-worker-secret 헤더 (WORKER_SECRET 상수시간 검증)
 */
export async function POST(request: NextRequest) {
  try {
    // 1. IP 레이트 리밋 검사 (1분 60회 초과 시 429)
    if (!checkWorkerRateLimit(request)) {
      return jsonWithCors(
        request,
        { error: '요청 횟수 제한을 초과했습니다. 잠시 후 다시 시도해 주세요.' },
        { status: 429 }
      );
    }

    // 2. 워커 시크릿 인증
    if (!verifyWorkerSecret(request)) {
      return jsonWithCors(
        request,
        { error: '인증 실패: 유효하지 않은 x-worker-secret 헤더입니다.' },
        { status: 401 }
      );
    }

    // 3. 요청 본문 파싱 (busy 플래그)
    let isBusy = false;
    try {
      const body = await request.json();
      if (typeof body?.busy === 'boolean') {
        isBusy = body.busy;
      }
    } catch {
      // 본문이 없거나 JSON이 아니면 busy=false로 간주
    }

    const supabase = getSupabaseAdmin();
    const result = await claimNextJob(supabase, isBusy);

    // 보안: 응답에 signed URL이 포함되므로 URL 본문은 로그에 남기지 않음
    console.log('[Worker Claim]', {
      hasJob: !!result.job,
      jobId: result.job?.job_id ?? null,
      nextPollSeconds: result.next_poll_seconds,
      isBusy,
    });

    return jsonWithCors(request, result, { status: 200 });
  } catch (err: any) {
    console.error('[Worker Claim] 처리 중 예외 발생:', err?.message || err);
    return jsonWithCors(
      request,
      { error: '서버 내부 오류가 발생했습니다.' },
      { status: 500 }
    );
  }
}
