// src/lib/runpod.ts
import { SupabaseClient } from '@supabase/supabase-js';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import {
  createVideoSignedDownloadUrl,
  createResultSignedUploadUrl,
  checkResultFileExists,
} from '@/lib/storage';
import {
  calculateProcessingProgressFromElapsed,
  DEFAULT_ESTIMATED_DURATION_SECONDS,
} from '@/lib/progressEstimator';
import { ProcessingProgressInfo } from '@/types/api';

export interface StartProcessingResult {
  jobId?: string;
  status: 'SUBMITTED' | 'SKIPPED';
  queueFull?: boolean;
  reason?: string;
}

export interface SyncSessionResult {
  status: string;
  fail_reason: string | null;
  workerProgress?: ProcessingProgressInfo | null;
  queueFull?: boolean;
}

const COLAB_DONE_STATUSES = new Set(['done', 'completed', 'succeeded', 'success']);
const COLAB_FAILED_STATUSES = new Set(['failed', 'error']);
const COLAB_PROCESSING_STATUSES = new Set(['processing', 'running']);

const PROCESSING_TIMEOUT_SECONDS = 6 * 60; // 6분 (360초)
const MAX_CONSECUTIVE_NOT_FOUND = 3;

// DB 마이그레이션(20261111000000) 적용 전 환경에서도 동작하도록 보조 인메모리 캐시 유지
const memoryProcessingStartedAt = new Map<string, string>();
const memoryNotFoundCounts = new Map<string, number>();

/**
 * 확장 컬럼(processing_started_at, not_found_count)이 DB에 없을 경우 기본 컬럼만으로 폴백 업데이트
 */
async function safeUpdateSession(
  supabase: SupabaseClient,
  sessionId: string,
  payload: Record<string, any>,
  expectedStatus?: string
): Promise<void> {
  let query = supabase.from('sessions').update(payload).eq('id', sessionId);
  if (expectedStatus) {
    query = query.eq('status', expectedStatus);
  }
  const { error } = await query;
  if (!error) return;

  // 확장 컬럼 누락(schema cache / undefined column) 시 핵심 컬럼만으로 재시도
  const { processing_started_at: _p, not_found_count: _n, ...corePayload } = payload;
  if (Object.keys(corePayload).length === 0) return;

  let fallbackQuery = supabase.from('sessions').update(corePayload).eq('id', sessionId);
  if (expectedStatus) {
    fallbackQuery = fallbackQuery.eq('status', expectedStatus);
  }
  const { error: fbError } = await fallbackQuery;
  if (fbError) {
    console.error(`[Colab Worker] 세션 업데이트 실패 (${sessionId}):`, fbError.message);
  }
}

const BLOCKED_WORKER_HOSTS = new Set([
  'api.trycloudflare.com',
  'www.trycloudflare.com',
  'trycloudflare.com',
  'api.cloudflare.com',
  'dash.cloudflare.com',
]);

/**
 * 실제 Colab FastAPI 터널 URL인지 검증 (cloudflared 제어/API 도메인 오등록 차단)
 */
export function isValidColabWorkerUrl(rawUrl: string): boolean {
  try {
    const parsed = new URL(rawUrl.trim());
    if (parsed.protocol !== 'https:') return false;
    return !BLOCKED_WORKER_HOSTS.has(parsed.hostname.toLowerCase());
  } catch {
    return false;
  }
}

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
      const candidate = data.worker_url.trim().replace(/\/+$/, '');
      if (isValidColabWorkerUrl(candidate)) {
        return candidate;
      }
      console.warn(`[Colab Worker] 잘못된 worker_url 무시됨: ${candidate}`);
    }
  } catch (err) {
    console.warn('[Colab Worker] worker_configs 조회 예외 (환경변수 폴백 확인):', err);
  }

  const envUrl = (process.env.COLAB_WORKER_URL || '').trim().replace(/\/+$/, '');
  return envUrl && isValidColabWorkerUrl(envUrl) ? envUrl : null;
}

/**
 * 업로드 완료된 세션을 Colab FastAPI 워커(/jobs)로 전달
 * - 200 OK 시 PROCESSING 전이 (queued 상태는 6분 타임아웃에서 제외하므로 processing_started_at=null)
 * - 503(queue full) 시 FAILED로 두지 않고 UPLOADED 유지(재시도 가능)
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

    // 503 Queue Full: FAILED 처리하지 않고 UPLOADED 상태 유지하여 자동 재시도 허용
    if (res.status === 503) {
      console.warn(`[Colab Worker] 워커 대기열 가득 참(503), 재시도 대기 유지: session=${sessionId}`);
      return {
        status: 'SKIPPED',
        queueFull: true,
        reason: '현재 대기 요청이 많아 혼잡합니다. 잠시 후 다시 시도해 주세요.',
      };
    }

    if (!res.ok) {
      const errText = await res.text().catch(() => '');
      console.error(
        `[Colab Worker] POST /jobs 실패 (${res.status}): ${errText} | session=${sessionId}`
      );
      return { status: 'SKIPPED', reason: `Colab 요청 실패 (${res.status})` };
    }

    // 작업 접수 성공 시 UPLOADED -> PROCESSING 전이 (실제 processing 시작 전까지는 타이머 미시작)
    memoryNotFoundCounts.delete(sessionId);
    memoryProcessingStartedAt.delete(sessionId);

    await safeUpdateSession(
      supabase,
      sessionId,
      {
        status: 'PROCESSING',
        result_path: resultPath,
        not_found_count: 0,
        processing_started_at: null,
      },
      'UPLOADED'
    );

    console.log(`[Colab Worker] 작업 전송 완료 -> PROCESSING(queued 대기 포함): session=${sessionId}`);

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
 * - queued: "대기 중 (앞에 N명)" progress 반환, 6분 타임아웃 미계산
 * - processing: 진입 시점부터 6분 초과 시 FAILED(TIMEOUT)
 * - not_found: 3회 연속 반환 시 FAILED(WORKER_RESTARTED)
 */
export async function syncColabJobStatusIfNeeded(
  supabase: SupabaseClient,
  session: {
    id: string;
    status: string;
    result_path?: string | null;
    fail_reason?: string | null;
    processing_started_at?: string | null;
    not_found_count?: number | null;
  }
): Promise<SyncSessionResult> {
  // 1. 아직 UPLOADED 상태면(503 혼잡 또는 뒤늦은 워커 기동) 자동 재시도
  if (session.status === 'UPLOADED') {
    const triggerRes = await startProcessing(session.id);
    if (triggerRes.status === 'SUBMITTED') {
      return { status: 'PROCESSING', fail_reason: null };
    }
    return {
      status: 'UPLOADED',
      fail_reason: session.fail_reason || null,
      queueFull: triggerRes.queueFull,
    };
  }

  if (session.status !== 'PROCESSING') {
    return { status: session.status, fail_reason: session.fail_reason || null };
  }

  const resultPath = session.result_path || `${session.id}/model.glb`;
  const effectiveProcStartIso =
    session.processing_started_at || memoryProcessingStartedAt.get(session.id) || null;

  // 2. processing 전이 후 6분(360초) 초과 여부 검사 (queued 대기 시간 제외)
  if (effectiveProcStartIso) {
    const elapsedSec = (Date.now() - new Date(effectiveProcStartIso).getTime()) / 1000;
    if (elapsedSec >= PROCESSING_TIMEOUT_SECONDS) {
      await safeUpdateSession(
        supabase,
        session.id,
        { status: 'FAILED', fail_reason: 'TIMEOUT' },
        'PROCESSING'
      );
      memoryProcessingStartedAt.delete(session.id);
      memoryNotFoundCounts.delete(session.id);
      return { status: 'FAILED', fail_reason: 'TIMEOUT' };
    }
  }

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

        // 2-1. 완료(done) 처리
        if (COLAB_DONE_STATUSES.has(colabStatus) || jobData?.ok === true) {
          const exists = await checkResultFileExists(supabase, resultPath);
          if (exists) {
            await safeUpdateSession(
              supabase,
              session.id,
              { status: 'DONE', result_path: resultPath, not_found_count: 0 },
              'PROCESSING'
            );
            memoryProcessingStartedAt.delete(session.id);
            memoryNotFoundCounts.delete(session.id);
            return { status: 'DONE', fail_reason: null };
          }
        }

        // 2-2. 실패(failed) 처리 (에러 코드 전달)
        if (COLAB_FAILED_STATUSES.has(colabStatus)) {
          const reason =
            String(jobData?.error || jobData?.fail_reason || jobData?.message || '').trim() ||
            'RECONSTRUCTION_FAILED';
          await safeUpdateSession(
            supabase,
            session.id,
            { status: 'FAILED', fail_reason: reason, not_found_count: 0 },
            'PROCESSING'
          );
          memoryProcessingStartedAt.delete(session.id);
          memoryNotFoundCounts.delete(session.id);
          return { status: 'FAILED', fail_reason: reason };
        }

        // 2-3. 대기열(queued) 상태 처리 -> 6분 타이머 미시작
        if (colabStatus === 'queued') {
          memoryNotFoundCounts.set(session.id, 0);
          if ((session.not_found_count ?? 0) !== 0) {
            await safeUpdateSession(supabase, session.id, { not_found_count: 0 });
          }

          const queuePos =
            typeof jobData.queue_position === 'number' && jobData.queue_position >= 1
              ? Math.floor(jobData.queue_position)
              : 1;
          const aheadCount = Math.max(0, queuePos - 1);
          const etaSec =
            typeof jobData.eta_seconds === 'number' && jobData.eta_seconds >= 0
              ? Math.round(jobData.eta_seconds)
              : undefined;

          const workerProgress: ProcessingProgressInfo = {
            elapsed_seconds: 0,
            estimated_total_seconds: etaSec ?? DEFAULT_ESTIMATED_DURATION_SECONDS,
            remaining_seconds: etaSec ?? DEFAULT_ESTIMATED_DURATION_SECONDS,
            progress_percent: 0,
            stage: `대기 중 (앞에 ${aheadCount}명)`,
            queue_position: queuePos,
            ...(etaSec !== undefined ? { eta_seconds: etaSec } : {}),
          };

          return {
            status: 'PROCESSING',
            fail_reason: null,
            workerProgress,
          };
        }

        // 2-4. 실제 처리 중(processing / running) -> 처음 감지된 시점부터 6분 타이머 시작
        if (COLAB_PROCESSING_STATUSES.has(colabStatus)) {
          const workerElapsed =
            typeof jobData.elapsed_seconds === 'number' && jobData.elapsed_seconds >= 0
              ? Math.floor(jobData.elapsed_seconds)
              : undefined;

          if (workerElapsed !== undefined && workerElapsed >= PROCESSING_TIMEOUT_SECONDS) {
            await safeUpdateSession(
              supabase,
              session.id,
              { status: 'FAILED', fail_reason: 'TIMEOUT' },
              'PROCESSING'
            );
            memoryProcessingStartedAt.delete(session.id);
            memoryNotFoundCounts.delete(session.id);
            return { status: 'FAILED', fail_reason: 'TIMEOUT' };
          }

          let procStartIso = effectiveProcStartIso;
          const updateFields: Record<string, any> = {};

          if (!procStartIso) {
            const startMs =
              workerElapsed !== undefined ? Date.now() - workerElapsed * 1000 : Date.now();
            procStartIso = new Date(startMs).toISOString();
            memoryProcessingStartedAt.set(session.id, procStartIso);
            updateFields.processing_started_at = procStartIso;
          }

          if ((session.not_found_count ?? 0) !== 0 || (memoryNotFoundCounts.get(session.id) ?? 0) !== 0) {
            memoryNotFoundCounts.set(session.id, 0);
            updateFields.not_found_count = 0;
          }

          if (Object.keys(updateFields).length > 0) {
            await safeUpdateSession(supabase, session.id, updateFields);
          }

          const effectiveElapsed =
            workerElapsed !== undefined
              ? workerElapsed
              : Math.floor((Date.now() - new Date(procStartIso).getTime()) / 1000);

          return {
            status: 'PROCESSING',
            fail_reason: null,
            workerProgress: calculateProcessingProgressFromElapsed(effectiveElapsed),
          };
        }

        // 2-5. not_found 상태 처리 -> 3회 연속 시 FAILED(WORKER_RESTARTED)
        if (colabStatus === 'not_found') {
          const exists = await checkResultFileExists(supabase, resultPath);
          if (exists) {
            await safeUpdateSession(
              supabase,
              session.id,
              { status: 'DONE', result_path: resultPath, not_found_count: 0 },
              'PROCESSING'
            );
            memoryProcessingStartedAt.delete(session.id);
            memoryNotFoundCounts.delete(session.id);
            return { status: 'DONE', fail_reason: null };
          }

          const prevCount = Math.max(
            session.not_found_count ?? 0,
            memoryNotFoundCounts.get(session.id) ?? 0
          );
          const nextCount = prevCount + 1;
          memoryNotFoundCounts.set(session.id, nextCount);

          if (nextCount >= MAX_CONSECUTIVE_NOT_FOUND) {
            await safeUpdateSession(
              supabase,
              session.id,
              { status: 'FAILED', fail_reason: 'WORKER_RESTARTED', not_found_count: nextCount },
              'PROCESSING'
            );
            memoryProcessingStartedAt.delete(session.id);
            memoryNotFoundCounts.delete(session.id);
            return { status: 'FAILED', fail_reason: 'WORKER_RESTARTED' };
          }

          await safeUpdateSession(supabase, session.id, { not_found_count: nextCount });
          return { status: 'PROCESSING', fail_reason: null };
        }
      }
    }

    // 워커 조회와 무관하게 results 버킷에 이미 model.glb가 업로드된 경우 안전하게 DONE 처리
    const fileReady = await checkResultFileExists(supabase, resultPath);
    if (fileReady) {
      await safeUpdateSession(
        supabase,
        session.id,
        { status: 'DONE', result_path: resultPath, not_found_count: 0 },
        'PROCESSING'
      );
      memoryProcessingStartedAt.delete(session.id);
      memoryNotFoundCounts.delete(session.id);
      return { status: 'DONE', fail_reason: null };
    }
  } catch (err: any) {
    console.warn(`[Colab Worker] 상태 동기화 조회 실패 (${session.id}):`, err?.message || err);
  }

  return { status: session.status, fail_reason: session.fail_reason || null };
}
