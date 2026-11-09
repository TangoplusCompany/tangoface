// src/app/api/sessions/[id]/status/route.ts
import { NextRequest } from 'next/server';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import { hashToken, timingSafeMatch } from '@/lib/crypto';
import { jsonWithCors, handleOptionsCors } from '@/lib/cors';
import { SessionStatusResponse } from '@/types/api';

export const runtime = 'nodejs';

export async function OPTIONS(request: NextRequest) {
  return handleOptionsCors(request);
}

/**
 * GET /api/sessions/[id]/status
 * 태블릿이 세션 상태를 폴링 조회
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
      .select('id, status, fail_reason, session_secret_hash, paired_at, uploaded_at, created_at')
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

    const response: SessionStatusResponse = {
      session_id: session.id,
      status: session.status,
      fail_reason: session.fail_reason,
      paired_at: session.paired_at,
      uploaded_at: session.uploaded_at,
      created_at: session.created_at,
    };

    return jsonWithCors(request, response, { status: 200 });
  } catch (err: any) {
    console.error('세션 상태 조회 예외:', err);
    return jsonWithCors(request, { error: '서버 내부 오류가 발생했습니다.' }, { status: 500 });
  }
}
