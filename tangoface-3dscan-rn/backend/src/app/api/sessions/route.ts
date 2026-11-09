// src/app/api/sessions/route.ts
import { NextRequest } from 'next/server';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import { generatePairingCode, generateSecureToken, hashToken } from '@/lib/crypto';
import { jsonWithCors, handleOptionsCors } from '@/lib/cors';
import { CreateSessionResponse } from '@/types/api';

export const runtime = 'nodejs';

export async function OPTIONS(request: NextRequest) {
  return handleOptionsCors(request);
}

/**
 * GET /api/sessions
 * 브라우저 주소창 직접 접속 시 친절한 상태 안내 반환
 */
export async function GET(request: NextRequest) {
  return jsonWithCors(
    request,
    {
      status: 'ok',
      service: 'TangoFace 3D Reconstruction API',
      message: '세션을 생성하려면 POST 방식으로 요청해주세요.',
      hint: '브라우저에서 http://localhost:3000 으로 접속하시면 버튼 클릭으로 바로 테스트할 수 있습니다.',
    },
    { status: 200 }
  );
}

/**
 * POST /api/sessions
 * 태블릿이 세션을 생성: 6자리 번호(만료 10분), session_id, session_secret 반환
 */
export async function POST(request: NextRequest) {
  try {
    const supabase = getSupabaseAdmin();
    const now = new Date();
    const codeExpiresAt = new Date(now.getTime() + 10 * 60 * 1000); // 10분 만료
    const sessionExpiresAt = new Date(now.getTime() + 24 * 60 * 60 * 1000); // 24시간 만료

    // 활성 세션 간 6자리 번호 충돌 방지 루프 (최대 5회 재시도)
    let code = '';
    let isUnique = false;

    for (let i = 0; i < 5; i++) {
      const candidateCode = generatePairingCode();
      const { data: existing } = await supabase
        .from('sessions')
        .select('id')
        .eq('code', candidateCode)
        .gte('created_at', new Date(now.getTime() - 10 * 60 * 1000).toISOString())
        .maybeSingle();

      if (!existing) {
        code = candidateCode;
        isUnique = true;
        break;
      }
    }

    if (!isUnique) {
      code = generatePairingCode();
    }

    const sessionSecret = generateSecureToken(32);
    const sessionSecretHash = hashToken(sessionSecret);

    const { data: newSession, error } = await supabase
      .from('sessions')
      .insert({
        code,
        status: 'CREATED',
        session_secret_hash: sessionSecretHash,
        created_at: now.toISOString(),
        expires_at: sessionExpiresAt.toISOString(),
      })
      .select('id')
      .single();

    if (error || !newSession) {
      console.error('세션 생성 DB 오류:', error);
      return jsonWithCors(request, { error: '세션을 생성하지 못했습니다.' }, { status: 500 });
    }

    const response: CreateSessionResponse = {
      session_id: newSession.id,
      code,
      session_secret: sessionSecret,
      expires_in_seconds: 600,
    };

    return jsonWithCors(request, response, { status: 201 });
  } catch (err: any) {
    console.error('세션 생성 예외:', err);
    return jsonWithCors(request, { error: '서버 내부 오류가 발생했습니다.' }, { status: 500 });
  }
}
