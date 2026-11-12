// src/app/api/worker/health/route.ts
import { NextRequest } from 'next/server';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import { jsonWithCors, handleOptionsCors } from '@/lib/cors';
import { isWorkerOnline } from '@/lib/workerPull';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function OPTIONS(request: NextRequest) {
  return handleOptionsCors(request);
}

/**
 * GET /api/worker/health
 * Colab 워커의 온라인 상태(last_seen 90초 이내), busy 여부, 대기열(queue) 수 확인
 */
export async function GET(request: NextRequest) {
  try {
    const supabase = getSupabaseAdmin();

    const { data: workerConfig } = await supabase
      .from('worker_configs')
      .select('last_seen, busy')
      .eq('worker_key', 'colab')
      .maybeSingle();

    const online = isWorkerOnline(workerConfig?.last_seen);

    // 대기열 건수 (status = 'UPLOADED')
    const { count: queueCount } = await supabase
      .from('sessions')
      .select('id', { count: 'exact', head: true })
      .eq('status', 'UPLOADED');

    const busy = online ? (workerConfig?.busy ?? false) : false;

    return jsonWithCors(
      request,
      {
        alive: online,
        status: online ? 'ONLINE' : 'OFFLINE',
        message: online
          ? 'Colab 서버가 켜져 있습니다.'
          : 'Colab 서버가 꺼져 있거나 연결이 끊어졌습니다 (90초 초과).',
        busy,
        queue: queueCount ?? 0,
      },
      { status: 200 }
    );
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
