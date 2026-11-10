// src/lib/runpod.ts
import { SupabaseClient } from '@supabase/supabase-js';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import {
  createVideoSignedDownloadUrl,
  createResultSignedUploadUrl,
  checkResultFileExists,
} from '@/lib/storage';

export interface StartProcessingResult {
  jobId?: string;
  status: 'SUBMITTED' | 'SKIPPED';
  reason?: string;
}

const COLAB_DONE_STATUSES = new Set(['done', 'completed', 'succeeded', 'success']);
const COLAB_FAILED_STATUSES = new Set(['failed', 'error']);

/**
 * DB(worker_configs) 또는 환경변수에서 최신 활성 Colab FastAPI URL 조회
 */
export async function getActiveColabWorkerUrl(
  supabase: SupabaseClient
): Promise<string | null> {
  try {
    const { data, error } = await supabase
      .from('worker_configs')
      .select('worker_url, updated_at')
      .eq('worker_key', 'colab')
      .maybeSingle();

    if (!error && data?.worker_url) {
      return data.worker_url.replace(/\/+$/, '');
    }
  } catch (err) {
    console.warn('[Colab Worker] worker_configs 조회 예외 (환경변수 폴백 확인):', err);
  }

  const envUrl = (process.env.COLAB_WORKER_URL || '').trim().replace(/\/+$/, '');
  return envUrl || null;
}

/**
 * 업로드 완료된 세션을 Colab FastAPI 워커(/jobs)로 전달하고 PROCESSING 상태로 전이
 */
export async function startProcessing(sessionId: string): Promise<StartProcessingResult> {
  try {
    const supabase = getSupabaseAdmin();

    const { data: session, error: findError } = await supabase
      .from('sessions')
      .select('id, status, upload_path')
      .eq('id', sessionId)
      .maybeSingle();

    if (findError || !session || !session.upload_path) {
      console.error(`[Colab Worker] 세션 또는 upload_path 없음: ${sessionId}`);
      return { status: 'SKIPPED', reason: '세션 또는 업로드 경로가 없습니다.' };
    }

    if (session.status !== 'UPLOADED') {
      return { status: 'SKIPPED', reason: `현재 상태(${session.status})는 UPLOADED가 아닙니다.` };
    }

    const workerUrl = await getActiveColabWorkerUrl(supabase);
    if (!workerUrl) {
      console.warn(
        `[Colab Worker] 등록된 Colab URL이 없어 대기 상태(UPLOADED)로 유지합니다: session_id=${sessionId}`
      );
      return { status: 'SKIPPED', reason: '활성 Colab 워커 URL이 등록되지 않았습니다.' };
    }

    const resultPath = `${sessionId}/model.glb`;
    const [videoUrl, glbUploadUrl] = await Promise.all([
      createVideoSignedDownloadUrl(supabase, session.upload_path, 3600),
      createResultSignedUploadUrl(supabase, resultPath),
    ]);

    const workerSecret = (process.env.WORKER_SECRET || '').trim();
    const res = await fetch(`${workerUrl}/jobs`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-worker-secret': workerSecret,
      },
      body: JSON.stringify({
        job_id: sessionId,
        video_url: videoUrl,
        glb_upload_url: glbUploadUrl,
      }),
      signal: AbortSignal.timeout(10000),
    });

    if (!res.ok) {
      const errText = await res.text().catch(() => '');
      console.error(
        `[Colab Worker] POST /jobs 실패 (${res.status}): ${errText} | session=${sessionId}`
      );
      return { status: 'SKIPPED', reason: `Colab 요청 실패 (${res.status})` };
    }

    // 작업 접수 성공 시 UPLOADED -> PROCESSING 전이
    const { error: updateError } = await supabase
      .from('sessions')
      .update({
        status: 'PROCESSING',
        result_path: resultPath,
      })
      .eq('id', sessionId)
      .eq('status', 'UPLOADED');

    if (updateError) {
      console.error(`[Colab Worker] PROCESSING 상태 업데이트 실패:`, updateError);
    } else {
      console.log(`[Colab Worker] 작업 전송 완료 -> PROCESSING: session=${sessionId}`);
    }

    return {
      jobId: sessionId,
      status: 'SUBMITTED',
    };
  } catch (err: any) {
    console.error(`[Colab Worker] startProcessing 예외 (session=${sessionId}):`, err?.message || err);
    return {
      status: 'SKIPPED',
      reason: err?.message || 'Colab 작업 요청 중 예외 발생',
    };
  }
}

/**
 * 태블릿 폴링 시 UPLOADED 또는 PROCESSING 세션 상태를 Colab /jobs/{id} 및 Storage와 동기화
 * - Colab이 웹훅을 별도로 호출하지 않고 JOBS[job_id]에만 상태를 기록하는 경우에도 DONE/FAILED 자동 감지
 */
export async function syncColabJobStatusIfNeeded(
  supabase: SupabaseClient,
  session: {
    id: string;
    status: string;
    result_path?: string | null;
    fail_reason?: string | null;
  }
): Promise<{ status: string; fail_reason: string | null }> {
  // 아직 UPLOADED 상태에 머물러 있으면 (예: Colab이 뒤늦게 켜짐) 작업 전송 재시도
  if (session.status === 'UPLOADED') {
    const triggerRes = await startProcessing(session.id);
    if (triggerRes.status === 'SUBMITTED') {
      return { status: 'PROCESSING', fail_reason: null };
    }
    return { status: session.status, fail_reason: session.fail_reason || null };
  }

  if (session.status !== 'PROCESSING') {
    return { status: session.status, fail_reason: session.fail_reason || null };
  }

  const resultPath = session.result_path || `${session.id}/model.glb`;

  try {
    const workerUrl = await getActiveColabWorkerUrl(supabase);
    if (workerUrl) {
      const workerSecret = (process.env.WORKER_SECRET || '').trim();
      const res = await fetch(`${workerUrl}/jobs/${session.id}`, {
        method: 'GET',
        headers: {
          'x-worker-secret': workerSecret,
        },
        signal: AbortSignal.timeout(3000),
      });

      if (res.ok) {
        const jobData = (await res.json()) as Record<string, any>;
        const colabStatus = String(jobData?.status || '').toLowerCase();

        if (COLAB_DONE_STATUSES.has(colabStatus) || jobData?.ok === true) {
          const exists = await checkResultFileExists(supabase, resultPath);
          if (exists) {
            await supabase
              .from('sessions')
              .update({ status: 'DONE', result_path: resultPath })
              .eq('id', session.id)
              .eq('status', 'PROCESSING');
            return { status: 'DONE', fail_reason: null };
          }
        } else if (COLAB_FAILED_STATUSES.has(colabStatus)) {
          const reason =
            String(jobData?.error || jobData?.fail_reason || jobData?.message || '') ||
            '3D 모델 생성 중 오류가 발생했습니다.';
          await supabase
            .from('sessions')
            .update({ status: 'FAILED', fail_reason: reason })
            .eq('id', session.id)
            .eq('status', 'PROCESSING');
          return { status: 'FAILED', fail_reason: reason };
        }
      }
    }

    // 워커 조회와 무관하게 results 버킷에 이미 model.glb가 업로드된 경우 안전하게 DONE 처리
    const fileReady = await checkResultFileExists(supabase, resultPath);
    if (fileReady) {
      await supabase
        .from('sessions')
        .update({ status: 'DONE', result_path: resultPath })
        .eq('id', session.id)
        .eq('status', 'PROCESSING');
      return { status: 'DONE', fail_reason: null };
    }
  } catch (err: any) {
    console.warn(`[Colab Worker] 상태 동기화 조회 실패 (${session.id}):`, err?.message || err);
  }

  return { status: session.status, fail_reason: session.fail_reason || null };
}
