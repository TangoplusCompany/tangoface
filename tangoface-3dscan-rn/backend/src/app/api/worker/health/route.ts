// src/app/api/worker/health/route.ts
import { NextRequest } from 'next/server';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import { getActiveColabWorkerUrl } from '@/lib/runpod';
import { jsonWithCors, handleOptionsCors } from '@/lib/cors';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function OPTIONS(request: NextRequest) {
  return handleOptionsCors(request);
}

/**
 * GET /api/worker/health
 * 등록된 Colab FastAPI 서버(/health)가 실제로 응답하는지 확인
 * 응답 확장: {"ok":true,"busy":true,"queue":1} 수용
 */
export async function GET(request: NextRequest) {
  try {
    const supabase = getSupabaseAdmin();
    const workerUrl = await getActiveColabWorkerUrl(supabase);

    if (!workerUrl) {
      return jsonWithCors(
        request,
        {
          alive: false,
          status: 'NOT_REGISTERED',
          message: 'Colab 서버가 연결되어 있지 않습니다.',
        },
        { status: 200 }
      );
    }

    try {
      const res = await fetch(`${workerUrl}/health`, {
        method: 'GET',
        cache: 'no-store',
        signal: AbortSignal.timeout(3500),
      });

      if (!res.ok) {
        return jsonWithCors(
          request,
          {
            alive: false,
            status: 'OFFLINE',
            message: `Colab 서버가 응답하지 않습니다. (${res.status})`,
          },
          { status: 200 }
        );
      }

      const data = await res.json().catch(() => null);
      const isOk = data === null || data?.ok !== false;
      const busy = typeof data?.busy === 'boolean' ? data.busy : undefined;
      const queue = typeof data?.queue === 'number' ? data.queue : undefined;

      return jsonWithCors(
        request,
        {
          alive: isOk,
          status: isOk ? 'ONLINE' : 'OFFLINE',
          message: isOk ? 'Colab 서버가 켜져 있습니다.' : 'Colab 서버 상태를 확인할 수 없습니다.',
          ...(busy !== undefined ? { busy } : {}),
          ...(queue !== undefined ? { queue } : {}),
        },
        { status: 200 }
      );
    } catch {
      return jsonWithCors(
        request,
        {
          alive: false,
          status: 'OFFLINE',
          message: 'Colab 서버가 꺼져 있거나 연결할 수 없습니다.',
        },
        { status: 200 }
      );
    }
  } catch (err: any) {
    console.error('[Worker Health] 확인 예외:', err?.message || err);
    return jsonWithCors(
      request,
      {
        alive: false,
        status: 'ERROR',
        message: '서버 상태 확인 중 오류가 발생했습니다.',
      },
      { status: 200 }
    );
  }
}
