// src/app/api/webhooks/runpod/route.ts
import { NextRequest } from 'next/server';
import { z } from 'zod';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import { timingSafeMatch } from '@/lib/crypto';
import { assertTransition } from '@/lib/stateMachine';
import { jsonWithCors, handleOptionsCors } from '@/lib/cors';

export const runtime = 'nodejs';

export async function OPTIONS(request: NextRequest) {
  return handleOptionsCors(request);
}

const RunPodWebhookSchema = z.object({
  session_id: z.string().uuid({ message: '올바른 session_id (UUID)여야 합니다.' }),
  status: z.enum(['COMPLETED', 'FAILED']),
  result_path: z.string().optional(),
  fail_reason: z.string().optional(),
});

/**
 * POST /api/webhooks/runpod
 * RunPod 완료/실패 웹훅 수신 핸들러 (뼈대 및 멱등성 보장)
 * 인증: X-Runpod-Secret 또는 Authorization: Bearer {RUNPOD_WEBHOOK_SECRET}
 */
export async function POST(request: NextRequest) {
  try {
    const expectedSecret = process.env.RUNPOD_WEBHOOK_SECRET;
    if (!expectedSecret) {
      console.error('환경변수 RUNPOD_WEBHOOK_SECRET이 설정되지 않았습니다.');
      return jsonWithCors(request, { error: '서버 설정 오류입니다.' }, { status: 500 });
    }

    const authHeader = request.headers.get('authorization');
    const customHeader = request.headers.get('x-runpod-secret');
    const providedSecret =
      customHeader || (authHeader?.startsWith('Bearer ') ? authHeader.substring(7).trim() : '');

    if (!providedSecret || !timingSafeMatch(providedSecret, expectedSecret)) {
      return jsonWithCors(request, { error: '유효하지 않은 웹훅 시크릿입니다.' }, { status: 401 });
    }

    let bodyJson: any;
    try {
      bodyJson = await request.json();
    } catch {
      return jsonWithCors(request, { error: '올바른 JSON 요청 본문이 아닙니다.' }, { status: 400 });
    }

    const parseResult = RunPodWebhookSchema.safeParse(bodyJson);
    if (!parseResult.success) {
      return jsonWithCors(
        request,
        { error: parseResult.error.errors[0]?.message || '입력값이 올바르지 않습니다.' },
        { status: 400 }
      );
    }

    const { session_id, status: jobStatus, result_path, fail_reason } = parseResult.data;
    const supabase = getSupabaseAdmin();

    const { data: session, error: findError } = await supabase
      .from('sessions')
      .select('id, status')
      .eq('id', session_id)
      .maybeSingle();

    if (findError || !session) {
      return jsonWithCors(request, { error: '해당 세션을 찾을 수 없습니다.' }, { status: 404 });
    }

    // 멱등성 (Idempotency) 보장: 이미 DONE 또는 FAILED로 최종 완료된 경우 중복 호출 무시하고 200 반환
    if (session.status === 'DONE' || session.status === 'FAILED') {
      return jsonWithCors(
        request,
        {
          ok: true,
          message: '이미 처리 완료된 세션입니다. 멱등하게 요청을 완료합니다.',
          current_status: session.status,
        },
        { status: 200 }
      );
    }

    const targetStatus = jobStatus === 'COMPLETED' ? 'DONE' : 'FAILED';

    // 상태 전이 검증 (예: UPLOADED 또는 PROCESSING -> DONE/FAILED)
    assertTransition(session.status as any, targetStatus);

    const updatePayload: Record<string, any> = {
      status: targetStatus,
    };

    if (targetStatus === 'DONE') {
      updatePayload.result_path = result_path || `${session_id}/model.glb`;
    } else {
      updatePayload.fail_reason = fail_reason || 'RunPod 3D 처리 작업이 실패했습니다.';
    }

    const { error: updateError } = await supabase
      .from('sessions')
      .update(updatePayload)
      .eq('id', session.id);

    if (updateError) {
      console.error('웹훅 상태 갱신 실패:', updateError);
      return jsonWithCors(request, { error: '세션 상태 갱신에 실패했습니다.' }, { status: 500 });
    }

    return jsonWithCors(
      request,
      {
        ok: true,
        session_id,
        status: targetStatus,
      },
      { status: 200 }
    );
  } catch (err: any) {
    console.error('RunPod 웹훅 처리 예외:', err);
    return jsonWithCors(request, { error: '서버 내부 오류가 발생했습니다.' }, { status: 500 });
  }
}
