// src/app/api/sessions/route.ts
import { NextRequest } from 'next/server';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import {
  generatePairingCode,
  generateSecureToken,
  hashToken,
  timingSafeMatch,
} from '@/lib/crypto';
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
 * 안드로이드 태블릿이 세션을 생성: 6자리 번호(만료 10분), session_id, session_secret 반환
 * 보안: TABLET_API_KEY 헤더(x-tablet-key)를 검증하여 인가된 태블릿 기기만 세션을 생성할 수 있도록 차단
 */
export async function POST(request: NextRequest) {
  try {
    // 1. 태블릿 기기 비밀키(X-Tablet-Key) 검증
    const configuredKey = process.env.TABLET_API_KEY;

    const tabletKeyHeader =
      request.headers.get('x-tablet-key') ||
      request.headers.get('X-Tablet-Key') ||
      '';

    const cleanHeaderKey = tabletKeyHeader.trim().replace(/^["']|["']$/g, '');
    const cleanConfiguredKey = (configuredKey || '').trim().replace(/^["']|["']$/g, '');

    console.log('[DEBUG /api/sessions POST]', {
      configuredKeyExists: !!configuredKey,
      configuredLength: cleanConfiguredKey.length,
      headerLength: cleanHeaderKey.length,
      headerSnippet: cleanHeaderKey.length > 8 ? `${cleanHeaderKey.slice(0, 4)}...${cleanHeaderKey.slice(-4)}` : cleanHeaderKey,
      configuredSnippet: cleanConfiguredKey.length > 8 ? `${cleanConfiguredKey.slice(0, 4)}...${cleanConfiguredKey.slice(-4)}` : cleanConfiguredKey,
      isMatch: cleanHeaderKey === cleanConfiguredKey,
    });

    if (configuredKey) {
      if (!cleanHeaderKey || !timingSafeMatch(cleanHeaderKey, cleanConfiguredKey)) {
        return jsonWithCors(
          request,
          {
            error:
              '인증되지 않은 태블릿 기기입니다. 올바른 X-Tablet-Key 헤더가 필요합니다.',
            debug: {
              configuredKeyExists: !!configuredKey,
              headerLength: cleanHeaderKey.length,
              configuredLength: cleanConfiguredKey.length,
              isMatch: cleanHeaderKey === cleanConfiguredKey,
            },
          },
          { status: 401 }
        );
      }
    }

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
