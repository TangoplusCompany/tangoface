// src/app/api/sessions/pair/route.ts
import { NextRequest } from 'next/server';
import { z } from 'zod';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import { generateSecureToken, hashToken, hashIp } from '@/lib/crypto';
import { checkPairRateLimit, recordPairAttempt } from '@/lib/rateLimiter';
import { assertTransition } from '@/lib/stateMachine';
import { jsonWithCors, handleOptionsCors } from '@/lib/cors';
import { PairSessionResponse } from '@/types/api';

export const runtime = 'nodejs';

export async function OPTIONS(request: NextRequest) {
  return handleOptionsCors(request);
}

const PairRequestSchema = z.object({
  code: z
    .string()
    .trim()
    .regex(/^\d{6}$/, { message: '6자리 숫자로 구성된 페어링 번호여야 합니다.' }),
});

/**
 * POST /api/sessions/pair
 * 폰에서 6자리 번호로 연결: 번호 즉시 무효화, pair_token 반환
 * IP별 실패 횟수 제한 (10분당 5회) 및 전역 급증 방어
 */
export async function POST(request: NextRequest) {
  try {
    const rawIp =
      request.headers.get('x-forwarded-for')?.split(',')[0].trim() ||
      request.headers.get('x-real-ip') ||
      '127.0.0.1';
    const ipHash = hashIp(rawIp);

    const supabase = getSupabaseAdmin();

    // 1. 레이트 리밋 검사 (10분 5회 및 전역 급증)
    const rateLimit = await checkPairRateLimit(supabase, ipHash);
    if (!rateLimit.allowed) {
      return jsonWithCors(
        request,
        {
          error:
            rateLimit.reason === 'GLOBAL_RATE_LIMITED'
              ? '단시간에 너무 많은 요청이 발생했습니다. 잠시 후 다시 시도해주세요.'
              : `페어링 실패 허용 횟수를 초과했습니다. ${rateLimit.retryAfterSeconds}초 후 다시 시도해주세요.`,
          retry_after_seconds: rateLimit.retryAfterSeconds,
        },
        { status: 429 }
      );
    }

    // 2. 요청 바디 zod 검증
    let bodyJson: any;
    try {
      bodyJson = await request.json();
    } catch {
      return jsonWithCors(request, { error: '올바른 JSON 요청 본문이 아닙니다.' }, { status: 400 });
    }

    const parseResult = PairRequestSchema.safeParse(bodyJson);
    if (!parseResult.success) {
      await recordPairAttempt(supabase, ipHash, false);
      return jsonWithCors(
        request,
        { error: parseResult.error.errors[0]?.message || '입력값이 올바르지 않습니다.' },
        { status: 400 }
      );
    }

    const { code } = parseResult.data;
    const now = Date.now();
    const tenMinutesAgo = new Date(now - 10 * 60 * 1000).toISOString();

    // 3. 10분 이내 & CREATED 상태인 세션 조회
    const { data: session, error: findError } = await supabase
      .from('sessions')
      .select('id, status, created_at')
      .eq('code', code)
      .eq('status', 'CREATED')
      .gte('created_at', tenMinutesAgo)
      .maybeSingle();

    if (findError || !session) {
      await recordPairAttempt(supabase, ipHash, false);
      const updatedCheck = await checkPairRateLimit(supabase, ipHash);
      return jsonWithCors(
        request,
        {
          error: '유효하지 않거나 이미 만료된 페어링 번호입니다.',
          remaining_attempts: updatedCheck.remaining,
        },
        { status: 400 }
      );
    }

    // 4. 상태 전이 검증 (CREATED -> PAIRED)
    assertTransition(session.status as any, 'PAIRED');

    // 5. 페어링 성공 처리: 토큰 발급 및 번호 즉시 NULL 처리 (일회용 무효화)
    const pairToken = generateSecureToken(32);
    const pairTokenHash = hashToken(pairToken);

    const { error: updateError } = await supabase
      .from('sessions')
      .update({
        code: null, // 즉시 일회용 무효화
        status: 'PAIRED',
        pair_token_hash: pairTokenHash,
        paired_at: new Date().toISOString(),
      })
      .eq('id', session.id)
      .eq('status', 'CREATED'); // 낙관적 락

    if (updateError) {
      console.error('페어링 상태 갱신 실패:', updateError);
      await recordPairAttempt(supabase, ipHash, false);
      return jsonWithCors(
        request,
        { error: '페어링 처리 중 충돌이 발생했습니다. 다시 시도해주세요.' },
        { status: 409 }
      );
    }

    // 성공 기록
    await recordPairAttempt(supabase, ipHash, true);

    const response: PairSessionResponse = {
      session_id: session.id,
      pair_token: pairToken,
    };

    return jsonWithCors(request, response, { status: 200 });
  } catch (err: any) {
    console.error('페어링 예외:', err);
    return jsonWithCors(request, { error: '서버 내부 오류가 발생했습니다.' }, { status: 500 });
  }
}
