// src/app/api/uploads/complete/route.ts
import { NextRequest } from 'next/server';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import { hashToken, timingSafeMatch } from '@/lib/crypto';
import { verifyAndInspectVideo } from '@/lib/storage';
import { assertTransition } from '@/lib/stateMachine';
import { jsonWithCors, handleOptionsCors } from '@/lib/cors';
import { CompleteUploadResponse } from '@/types/api';

export const runtime = 'nodejs';

export async function OPTIONS(request: NextRequest) {
  return handleOptionsCors(request);
}

const MAX_UPLOADED_QUEUE_SIZE = 5;

/**
 * POST /api/uploads/complete
 * 폰에서 영상 업로드 완료 알림
 * Storage에서 파일 실존 및 크기(200MB 이하) 확인 후 상태를 UPLOADED로 변경
 * (대기 건수가 5건 이상이면 503 QUEUE_FULL을 반환하고 세션은 PAIRED 상태 유지하여 재시도 허용)
 * 인증: Authorization: Bearer {pair_token}
 */
export async function POST(request: NextRequest) {
  try {
    const authHeader = request.headers.get('authorization') || '';
    if (!authHeader.startsWith('Bearer ')) {
      return jsonWithCors(
        request,
        { error: '인증 헤더가 올바르지 않습니다. (Authorization: Bearer <pair_token>)' },
        { status: 401 }
      );
    }

    const pairToken = authHeader.substring(7).trim();
    if (!pairToken) {
      return jsonWithCors(request, { error: 'pair_token이 제공되지 않았습니다.' }, { status: 401 });
    }

    const tokenHash = hashToken(pairToken);
    const supabase = getSupabaseAdmin();

    const { data: session, error: findError } = await supabase
      .from('sessions')
      .select('id, status, upload_path, pair_token_hash')
      .eq('pair_token_hash', tokenHash)
      .maybeSingle();

    if (findError || !session || !timingSafeMatch(session.pair_token_hash || '', tokenHash)) {
      return jsonWithCors(request, { error: '유효하지 않은 pair_token입니다.' }, { status: 401 });
    }

    if (session.status !== 'PAIRED') {
      return jsonWithCors(
        request,
        {
          error: `업로드 완료를 처리할 수 없는 상태입니다. (현재: ${session.status}, 필요: PAIRED)`,
        },
        { status: 400 }
      );
    }

    if (!session.upload_path) {
      return jsonWithCors(
        request,
        { error: '발급된 업로드 경로가 없습니다. 먼저 /api/uploads/presign을 요청해주세요.' },
        { status: 400 }
      );
    }

    // 1. Storage에서 실제 파일 실존 및 크기/MIME 검증 (초과 시 자동 삭제)
    const inspection = await verifyAndInspectVideo(supabase, session.upload_path);

    if (!inspection.valid) {
      const failReason = inspection.reason || '업로드 검증에 실패했습니다.';
      await supabase
        .from('sessions')
        .update({
          status: 'FAILED',
          fail_reason: failReason,
        })
        .eq('id', session.id);

      return jsonWithCors(
        request,
        {
          error: failReason,
          status: 'FAILED',
        },
        { status: 400 }
      );
    }

    // 2. 대기열(UPLOADED) 5건 초과 여부 확인: 초과 시 503 반환 및 PAIRED 상태 유지
    const { count: waitingCount, error: countErr } = await supabase
      .from('sessions')
      .select('id', { count: 'exact', head: true })
      .eq('status', 'UPLOADED');

    if (!countErr && (waitingCount ?? 0) >= MAX_UPLOADED_QUEUE_SIZE) {
      return jsonWithCors(
        request,
        {
          error: 'QUEUE_FULL',
          message: '현재 처리 대기열이 가득 찼습니다. 잠시 후 다시 시도해 주세요.',
        },
        { status: 503 }
      );
    }

    // 3. 상태 전이 검증 (PAIRED -> UPLOADED)
    assertTransition(session.status as any, 'UPLOADED');

    // 4. 상태 업데이트
    const { error: updateError } = await supabase
      .from('sessions')
      .update({
        status: 'UPLOADED',
        uploaded_at: new Date().toISOString(),
      })
      .eq('id', session.id)
      .eq('status', 'PAIRED');

    if (updateError) {
      console.error('업로드 완료 상태 갱신 실패:', updateError);
      return jsonWithCors(request, { error: '세션 상태 갱신에 실패했습니다.' }, { status: 500 });
    }

    const response: CompleteUploadResponse = {
      status: 'UPLOADED',
      queue_full: false,
      message: '영상 업로드가 확인되어 대기열에 등록되었습니다.',
    };

    return jsonWithCors(request, response, { status: 200 });
  } catch (err: any) {
    console.error('업로드 완료 처리 예외:', err);
    return jsonWithCors(request, { error: '서버 내부 오류가 발생했습니다.' }, { status: 500 });
  }
}
