// src/app/api/worker/register/route.ts
import { NextRequest } from 'next/server';
import { z } from 'zod';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import { timingSafeMatch } from '@/lib/crypto';
import { jsonWithCors, handleOptionsCors } from '@/lib/cors';

export const runtime = 'nodejs';

export async function OPTIONS(request: NextRequest) {
  return handleOptionsCors(request);
}

const RegisterSchema = z.object({
  url: z
    .string()
    .trim()
    .url({ message: '올바른 URL 형식이어야 합니다.' })
    .refine((val) => val.startsWith('https://'), {
      message: '워커 주소는 https:// 로 시작해야 합니다.',
    }),
});

/**
 * POST /api/worker/register
 * Colab FastAPI 하트비트가 30초마다 호출하여 최신 터널 URL(trycloudflare.com)을 등록
 * 인증: X-Register-Secret 헤더 (WORKER_REGISTER_SECRET 일치 검증)
 */
export async function POST(request: NextRequest) {
  try {
    const expectedSecret = (process.env.WORKER_REGISTER_SECRET || '').trim();
    if (!expectedSecret) {
      console.error('환경변수 WORKER_REGISTER_SECRET이 설정되지 않았습니다.');
      return jsonWithCors(request, { error: '서버 설정 오류입니다.' }, { status: 500 });
    }

    const providedSecret = (
      request.headers.get('x-register-secret') ||
      request.headers.get('X-Register-Secret') ||
      ''
    ).trim();

    if (!providedSecret || !timingSafeMatch(providedSecret, expectedSecret)) {
      return jsonWithCors(
        request,
        { error: '유효하지 않은 x-register-secret 헤더입니다.' },
        { status: 401 }
      );
    }

    let bodyJson: unknown;
    try {
      bodyJson = await request.json();
    } catch {
      return jsonWithCors(request, { error: '올바른 JSON 요청 본문이 아닙니다.' }, { status: 400 });
    }

    const parseResult = RegisterSchema.safeParse(bodyJson);
    if (!parseResult.success) {
      return jsonWithCors(
        request,
        { error: parseResult.error.errors[0]?.message || '입력값이 올바르지 않습니다.' },
        { status: 400 }
      );
    }

    const cleanUrl = parseResult.data.url.replace(/\/+$/, '');
    const supabase = getSupabaseAdmin();

    const { error: upsertError } = await supabase.from('worker_configs').upsert(
      {
        worker_key: 'colab',
        worker_url: cleanUrl,
        updated_at: new Date().toISOString(),
      },
      { onConflict: 'worker_key' }
    );

    if (upsertError) {
      console.error('워커 URL DB 저장 실패:', upsertError);
      return jsonWithCors(
        request,
        { error: '워커 주소 저장에 실패했습니다. (worker_configs 테이블 확인 필요)' },
        { status: 500 }
      );
    }

    return jsonWithCors(
      request,
      {
        ok: true,
        worker_url: cleanUrl,
        updated_at: new Date().toISOString(),
      },
      { status: 200 }
    );
  } catch (err: any) {
    console.error('워커 등록 처리 예외:', err);
    return jsonWithCors(request, { error: '서버 내부 오류가 발생했습니다.' }, { status: 500 });
  }
}

/**
 * GET /api/worker/register
 * 등록된 워커 URL 상태 확인 (X-Register-Secret 인증 시 상세 반환)
 */
export async function GET(request: NextRequest) {
  try {
    const expectedSecret = (process.env.WORKER_REGISTER_SECRET || '').trim();
    const providedSecret = (request.headers.get('x-register-secret') || '').trim();

    if (!expectedSecret || !providedSecret || !timingSafeMatch(providedSecret, expectedSecret)) {
      return jsonWithCors(request, { status: 'ok', service: 'worker-registry' }, { status: 200 });
    }

    const supabase = getSupabaseAdmin();
    const { data } = await supabase
      .from('worker_configs')
      .select('worker_key, worker_url, updated_at')
      .eq('worker_key', 'colab')
      .maybeSingle();

    return jsonWithCors(request, { ok: true, worker: data || null }, { status: 200 });
  } catch (err: any) {
    console.error('워커 조회 예외:', err);
    return jsonWithCors(request, { error: '서버 내부 오류가 발생했습니다.' }, { status: 500 });
  }
}
