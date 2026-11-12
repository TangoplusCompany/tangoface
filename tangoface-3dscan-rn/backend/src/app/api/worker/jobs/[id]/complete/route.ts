// src/app/api/worker/jobs/[id]/complete/route.ts
import { NextRequest } from 'next/server';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import { jsonWithCors, handleOptionsCors } from '@/lib/cors';
import {
  verifyWorkerSecret,
  checkWorkerRateLimit,
  completeJob,
} from '@/lib/workerPull';
import { WorkerCompleteRequest } from '@/types/api';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function OPTIONS(request: NextRequest) {
  return handleOptionsCors(request);
}

/**
 * POST /api/worker/jobs/[id]/complete
 * Colab 워커가 3D 복원 성공 또는 실패 결과를 보고
 * 인증: x-worker-secret 헤더 (WORKER_SECRET 상수시간 검증)
 */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    // 1. IP 레이트 리밋 검사
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

    const { id: sessionId } = await params;
    if (!sessionId) {
      return jsonWithCors(request, { error: '세션 ID가 누락되었습니다.' }, { status: 400 });
    }

    // 3. 요청 본문 파싱
    let body: WorkerCompleteRequest;
    try {
      body = (await request.json()) as WorkerCompleteRequest;
    } catch {
      return jsonWithCors(request, { error: '올바른 JSON 요청 본문이 아닙니다.' }, { status: 400 });
    }

    if (!body || (body.status !== 'done' && body.status !== 'failed')) {
      return jsonWithCors(
        request,
        { error: 'status 필드는 "done" 또는 "failed"여야 합니다.' },
        { status: 400 }
      );
    }

    const supabase = getSupabaseAdmin();
    const result = await completeJob(supabase, sessionId, body);

    console.log('[Worker Complete]', {
      sessionId,
      reportedStatus: body.status,
      error: body.error,
      ok: result.ok,
    });

    if (!result.ok) {
      return jsonWithCors(
        request,
        { error: result.error || '완료 처리에 실패했습니다.' },
        { status: result.status }
      );
    }

    return jsonWithCors(request, { ok: true }, { status: 200 });
  } catch (err: any) {
    console.error('[Worker Complete] 처리 중 예외 발생:', err?.message || err);
    return jsonWithCors(
      request,
      { error: '서버 내부 오류가 발생했습니다.' },
      { status: 500 }
    );
  }
}
