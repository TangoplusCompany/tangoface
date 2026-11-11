// src/app/api/sessions/[id]/status/route.ts
import { NextRequest } from 'next/server';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import { hashToken, timingSafeMatch } from '@/lib/crypto';
import { jsonWithCors, handleOptionsCors } from '@/lib/cors';
import { calculateProcessingProgress } from '@/lib/progressEstimator';
import { syncColabJobStatusIfNeeded } from '@/lib/runpod';
import { mapFailReasonToMessage } from '@/lib/errorMapper';
import { ProcessingProgressInfo, SessionStatusResponse } from '@/types/api';
import { SessionStatus } from '@/types/session';

export const runtime = 'nodejs';

export async function OPTIONS(request: NextRequest) {
  return handleOptionsCors(request);
}

/**
 * GET /api/sessions/[id]/status
 * 태블릿이 세션 상태 및 진행률을 폴링 조회
 * 인증: X-Session-Secret 헤더
 */
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id: sessionId } = await params;
    const sessionSecret = request.headers.get('x-session-secret') || '';

    if (!sessionSecret) {
      return jsonWithCors(
        request,
        { error: 'X-Session-Secret 헤더가 누락되었습니다.' },
        { status: 401 }
      );
    }

    const secretHash = hashToken(sessionSecret);
    const supabase = getSupabaseAdmin();

    const { data: session, error } = await supabase
      .from('sessions')
      .select('*')
      .eq('id', sessionId)
      .maybeSingle();

    if (error || !session) {
      return jsonWithCors(request, { error: '세션을 찾을 수 없습니다.' }, { status: 404 });
    }

    // 타이밍 세이프 해시 비교
    if (!timingSafeMatch(session.session_secret_hash, secretHash)) {
      return jsonWithCors(
        request,
        { error: '인증 실패: 유효하지 않은 session_secret입니다.' },
        { status: 401 }
      );
    }

    // Colab 워커(/jobs/{id}) 및 Storage 상태 동기화
    const synced = await syncColabJobStatusIfNeeded(supabase, session);
    const effectiveStatus = synced.status as SessionStatus;
    const rawFailReason = synced.fail_reason;
    const friendlyFailReason = mapFailReasonToMessage(rawFailReason);

    // 상태에 따른 스마트 진행률 및 대기열 정보 산출
    let progress: ProcessingProgressInfo | null = null;
    if (effectiveStatus === 'PROCESSING') {
      if (synced.workerProgress) {
        progress = synced.workerProgress;
      } else {
        const startedAt =
          session.processing_started_at || session.uploaded_at || session.created_at;
        progress = calculateProcessingProgress(startedAt);
      }
    } else if (effectiveStatus === 'UPLOADED' && synced.queueFull) {
      progress = {
        elapsed_seconds: 0,
        estimated_total_seconds: 60,
        remaining_seconds: 60,
        progress_percent: 0,
        stage: '요청이 혼잡합니다. 잠시 후 자동으로 다시 시도됩니다...',
      };
    } else if (effectiveStatus === 'DONE') {
      progress = {
        elapsed_seconds: 0,
        estimated_total_seconds: 45,
        remaining_seconds: 0,
        progress_percent: 100,
        stage: '3D 모델 복원 완료!',
      };
    }

    const response: SessionStatusResponse = {
      session_id: session.id,
      status: effectiveStatus,
      fail_reason: friendlyFailReason,
      fail_code: rawFailReason || null,
      paired_at: session.paired_at,
      uploaded_at: session.uploaded_at,
      created_at: session.created_at,
      progress,
    };

    return jsonWithCors(request, response, { status: 200 });
  } catch (err: any) {
    console.error('세션 상태 조회 예외:', err);
    return jsonWithCors(request, { error: '서버 내부 오류가 발생했습니다.' }, { status: 500 });
  }
}
