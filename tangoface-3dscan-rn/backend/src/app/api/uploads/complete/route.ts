// src/app/api/uploads/complete/route.ts
import { NextRequest } from 'next/server';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import { hashToken, timingSafeMatch } from '@/lib/crypto';
import { verifyAndInspectVideo } from '@/lib/storage';
import { assertTransition } from '@/lib/stateMachine';
import { startProcessing } from '@/lib/runpod';
import { jsonWithCors, handleOptionsCors } from '@/lib/cors';
import { CompleteUploadResponse } from '@/types/api';

export const runtime = 'nodejs';

export async function OPTIONS(request: NextRequest) {
  return handleOptionsCors(request);
}

/**
 * POST /api/uploads/complete
 * 폰에서 영상 업로드 완료 알림
 * Storage에서 파일 실존 및 크기(200MB 이하) 확인 후 상태를 UPLOADED로 변경
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

    // 2. 상태 전이 검증 (PAIRED -> UPLOADED)
    assertTransition(session.status as any, 'UPLOADED');

    // 3. 상태 업데이트
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

    // 4. Colab FastAPI 작업 트리거 (Vercel 서버리스 종료 전에 요청이 전달되도록 await 처리)
    const processingRes = await startProcessing(session.id);

    const response: CompleteUploadResponse = {
      status: processingRes.status === 'SUBMITTED' ? 'PROCESSING' : 'UPLOADED',
      queue_full: processingRes.queueFull || false,
      message:
        processingRes.status === 'SUBMITTED'
          ? '영상 업로드가 완료되어 Colab 3D 복원 작업을 시작했습니다.'
          : processingRes.queueFull
          ? '현재 처리 요청이 혼잡합니다. 잠시 후 자동으로 다시 시도됩니다.'
          : '영상 파일이 확인되어 UPLOADED 상태로 변경되었습니다.',
    };

    return jsonWithCors(request, response, { status: 200 });
  } catch (err: any) {
    console.error('업로드 완료 처리 예외:', err);
    return jsonWithCors(request, { error: '서버 내부 오류가 발생했습니다.' }, { status: 500 });
  }
}
