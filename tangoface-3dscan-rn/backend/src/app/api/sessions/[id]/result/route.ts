// src/app/api/sessions/[id]/result/route.ts
import { NextRequest } from 'next/server';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import { hashToken, timingSafeMatch } from '@/lib/crypto';
import { createResultSignedDownloadUrl, RESULT_DOWNLOAD_EXPIRY_SECONDS } from '@/lib/storage';
import { jsonWithCors, handleOptionsCors } from '@/lib/cors';
import { SessionResultResponse } from '@/types/api';

export const runtime = 'nodejs';

export async function OPTIONS(request: NextRequest) {
  return handleOptionsCors(request);
}

/**
 * GET /api/sessions/[id]/result
 * 세션 상태가 DONE일 때만 GLB 모델용 5분 만료 서명 다운로드 URL 발급
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
      .select('id, status, result_path, session_secret_hash')
      .eq('id', sessionId)
      .maybeSingle();

    if (error || !session) {
      return jsonWithCors(request, { error: '세션을 찾을 수 없습니다.' }, { status: 404 });
    }

    if (!timingSafeMatch(session.session_secret_hash, secretHash)) {
      return jsonWithCors(
        request,
        { error: '인증 실패: 유효하지 않은 session_secret입니다.' },
        { status: 401 }
      );
    }

    // 상태가 DONE인지 엄격히 검사
    if (session.status !== 'DONE') {
      return jsonWithCors(
        request,
        {
          error: '3D 복원 결과가 아직 준비되지 않았습니다. 상태가 DONE일 때만 다운로드할 수 있습니다.',
          current_status: session.status,
        },
        { status: 400 }
      );
    }

    const resultPath = session.result_path || `${session.id}/model.glb`;
    const modelUrl = await createResultSignedDownloadUrl(supabase, resultPath);

    const response: SessionResultResponse = {
      session_id: session.id,
      status: 'DONE',
      model_url: modelUrl,
      expires_in_seconds: RESULT_DOWNLOAD_EXPIRY_SECONDS,
    };

    return jsonWithCors(request, response, { status: 200 });
  } catch (err: any) {
    console.error('결과 다운로드 URL 발급 예외:', err);
    return jsonWithCors(request, { error: '서버 내부 오류가 발생했습니다.' }, { status: 500 });
  }
}
