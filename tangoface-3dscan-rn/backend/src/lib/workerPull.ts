// src/lib/workerPull.ts
import { SupabaseClient } from '@supabase/supabase-js';
import { timingSafeMatch } from '@/lib/crypto';
import {
  createVideoSignedDownloadUrl,
  createResultSignedUploadUrl,
  checkResultFileExists,
  VIDEOS_BUCKET,
} from '@/lib/storage';
import { calculateProcessingProgressFromElapsed } from '@/lib/progressEstimator';
import {
  WorkerClaimJob,
  WorkerClaimResponse,
  WorkerCompleteRequest,
  ProcessingProgressInfo,
} from '@/types/api';
import { SessionStatus } from '@/types/session';

export const WORKER_HEARTBEAT_TIMEOUT_SECONDS = 90; // 90초 초과 시 오프라인
export const PROCESSING_TIMEOUT_SECONDS = 6 * 60; // 6분
export const UPLOADED_TIMEOUT_SECONDS = 30 * 60; // 30분
export const DEFAULT_WORKER_AVG_SECONDS = 60;

// IP 단위 레이트 리밋 (1분 60회)
const workerIpRequests = new Map<string, number[]>();
const WORKER_RATE_LIMIT_WINDOW_MS = 60 * 1000;
const WORKER_RATE_LIMIT_MAX = 60;

export function checkWorkerRateLimit(request: Request): boolean {
  const ip =
    request.headers.get('x-forwarded-for')?.split(',')[0]?.trim() ||
    request.headers.get('cf-connecting-ip')?.trim() ||
    request.headers.get('x-real-ip')?.trim() ||
    '127.0.0.1';
  const now = Date.now();
  const timestamps = (workerIpRequests.get(ip) || []).filter(
    (t) => now - t < WORKER_RATE_LIMIT_WINDOW_MS
  );
  if (timestamps.length >= WORKER_RATE_LIMIT_MAX) {
    return false;
  }
  timestamps.push(now);
  workerIpRequests.set(ip, timestamps);
  return true;
}

export function resetWorkerRateLimitForTest(): void {
  workerIpRequests.clear();
}

/**
 * X-Worker-Secret 헤더 상수시간 검증
 */
export function verifyWorkerSecret(request: Request): boolean {
  const expectedSecret = (process.env.WORKER_SECRET || '').trim();
  if (!expectedSecret) {
    console.error('[Worker Pull] WORKER_SECRET 환경변수가 설정되지 않았습니다.');
    return false;
  }

  const providedSecret = (
    request.headers.get('x-worker-secret') ||
    request.headers.get('X-Worker-Secret') ||
    ''
  ).trim();

  if (!providedSecret) return false;
  return timingSafeMatch(providedSecret, expectedSecret);
}

/**
 * 워커 온라인 여부 확인 (last_seen이 90초 이내)
 */
export function isWorkerOnline(lastSeenIso: string | null | undefined, nowMs: number = Date.now()): boolean {
  if (!lastSeenIso) return false;
  const lastSeenMs = new Date(lastSeenIso).getTime();
  if (isNaN(lastSeenMs)) return false;
  return nowMs - lastSeenMs <= WORKER_HEARTBEAT_TIMEOUT_SECONDS * 1000;
}

/**
 * 타임아웃 및 워커 유실에 대한 안전장치 (지연 평가)
 */
export async function evaluateSafetyTimeouts(
  supabase: SupabaseClient,
  now: Date = new Date()
): Promise<void> {
  const nowMs = now.getTime();
  const sixMinutesAgoIso = new Date(nowMs - PROCESSING_TIMEOUT_SECONDS * 1000).toISOString();
  const thirtyMinutesAgoIso = new Date(nowMs - UPLOADED_TIMEOUT_SECONDS * 1000).toISOString();

  try {
    // 1. PROCESSING 6분 초과 -> TIMEOUT
    await supabase
      .from('sessions')
      .update({ status: 'FAILED', fail_reason: 'TIMEOUT' })
      .eq('status', 'PROCESSING')
      .lte('processing_started_at', sixMinutesAgoIso);

    // 2. UPLOADED 30분 초과 -> TIMEOUT
    await supabase
      .from('sessions')
      .update({ status: 'FAILED', fail_reason: 'TIMEOUT' })
      .eq('status', 'UPLOADED')
      .lte('uploaded_at', thirtyMinutesAgoIso);

    // 3. PROCESSING 중 워커 오프라인(last_seen 90초 초과) -> WORKER_LOST
    const { data: workerConfig } = await supabase
      .from('worker_configs')
      .select('last_seen')
      .eq('worker_key', 'colab')
      .maybeSingle();

    if (!isWorkerOnline(workerConfig?.last_seen, nowMs)) {
      await supabase
        .from('sessions')
        .update({ status: 'FAILED', fail_reason: 'WORKER_LOST' })
        .eq('status', 'PROCESSING');
    }
  } catch (err) {
    console.error('[Worker Safety] 지연 평가 실행 예외:', err);
  }
}

/**
 * 워커 작업 배정 (POST /api/worker/claim)
 */
export async function claimNextJob(
  supabase: SupabaseClient,
  isBusy: boolean
): Promise<WorkerClaimResponse> {
  const now = new Date();
  const nowIso = now.toISOString();

  // 1. worker_configs 하트비트 갱신 (last_seen=now, busy)
  await supabase
    .from('worker_configs')
    .upsert(
      {
        worker_key: 'colab',
        last_seen: nowIso,
        busy: isBusy,
        updated_at: nowIso,
      },
      { onConflict: 'worker_key' }
    );

  // 2. 만료/유실 세션 지연 평가
  await evaluateSafetyTimeouts(supabase, now);

  let job: WorkerClaimJob | null = null;

  // 3. 워커가 바쁘지 않을 때만 대기 작업 1건 배정 (UPLOADED -> PROCESSING)
  if (!isBusy) {
    let candidate: { id: string; upload_path: string } | null = null;

    // 3-1. Postgres claim_next_uploaded_session 함수 (FOR UPDATE SKIP LOCKED) 시도
    try {
      const { data: rpcData, error: rpcErr } = await supabase.rpc('claim_next_uploaded_session');
      if (!rpcErr && Array.isArray(rpcData) && rpcData.length > 0) {
        candidate = rpcData[0];
      }
    } catch {
      // RPC 미배포 환경 대비 폴백
    }

    // 3-2. RPC 미사용 시 조건부 UPDATE 기반 원자적 배정 (uploaded_at 오래된 순)
    if (!candidate) {
      const { data: candidates } = await supabase
        .from('sessions')
        .select('id, upload_path, uploaded_at')
        .eq('status', 'UPLOADED')
        .order('uploaded_at', { ascending: true })
        .limit(1);

      if (candidates && candidates.length > 0) {
        const c = candidates[0];
        const resultPath = `${c.id}/model.glb`;
        const { data: updated } = await supabase
          .from('sessions')
          .update({
            status: 'PROCESSING',
            processing_started_at: nowIso,
            result_path: resultPath,
          })
          .eq('id', c.id)
          .eq('status', 'UPLOADED')
          .select('id, upload_path')
          .maybeSingle();

        if (updated) {
          candidate = updated;
        }
      }
    }

    // 3-3. 작업이 배정된 경우 서명 URL 신규 발급 (video_url 1h GET, glb_upload_url 15분 이상 PUT upsert)
    if (candidate && candidate.upload_path) {
      const videoUrl = await createVideoSignedDownloadUrl(supabase, candidate.upload_path, 3600);
      const glbUploadUrl = await createResultSignedUploadUrl(supabase, `${candidate.id}/model.glb`);

      job = {
        job_id: candidate.id,
        video_url: videoUrl,
        glb_upload_url: glbUploadUrl,
      };

      // 워커 상태를 즉시 busy=true로 갱신
      await supabase
        .from('worker_configs')
        .update({ busy: true })
        .eq('worker_key', 'colab');
    }
  }

  // 4. next_poll_seconds 계산:
  // CREATED, PAIRED, UPLOADED, PROCESSING 중 만료 전 세션이 1건이라도 있으면 3초, 없으면 15초
  const { count: activeCount } = await supabase
    .from('sessions')
    .select('id', { count: 'exact', head: true })
    .in('status', ['CREATED', 'PAIRED', 'UPLOADED', 'PROCESSING'])
    .gt('expires_at', nowIso);

  const nextPollSeconds = activeCount && activeCount > 0 ? 3 : 15;

  return {
    job,
    next_poll_seconds: nextPollSeconds,
  };
}

/**
 * 워커 작업 완료 보고 (POST /api/worker/jobs/{id}/complete)
 */
export async function completeJob(
  supabase: SupabaseClient,
  sessionId: string,
  req: WorkerCompleteRequest
): Promise<{ ok: boolean; status: number; error?: string }> {
  const nowIso = new Date().toISOString();

  const { data: session, error: findErr } = await supabase
    .from('sessions')
    .select('id, status, upload_path, result_path')
    .eq('id', sessionId)
    .maybeSingle();

  if (findErr || !session) {
    return { ok: false, status: 404, error: '세션을 찾을 수 없습니다.' };
  }

  // 멱등 처리: 이미 DONE이거나 FAILED면 200 반환 (워커 재시도 안전)
  if (session.status === 'DONE' || session.status === 'FAILED') {
    return { ok: true, status: 200 };
  }

  const resultPath = session.result_path || `${sessionId}/model.glb`;

  if (req.status === 'done') {
    // 1) results/{id}/model.glb 실존 확인
    const exists = await checkResultFileExists(supabase, resultPath);
    if (exists) {
      await supabase
        .from('sessions')
        .update({
          status: 'DONE',
          fail_reason: null,
        })
        .eq('id', sessionId);
    } else {
      await supabase
        .from('sessions')
        .update({
          status: 'FAILED',
          fail_reason: 'UPLOAD_FAILED',
        })
        .eq('id', sessionId);
    }
  } else {
    // 2) 실패 보고 수신
    const failReason = req.error?.trim() || 'RECONSTRUCTION_FAILED';
    await supabase
      .from('sessions')
      .update({
        status: 'FAILED',
        fail_reason: failReason,
      })
      .eq('id', sessionId);
  }

  // 3) 완료 시(성공/실패 모두) videos 버킷의 원본 영상 즉시 삭제 (개인정보 보호)
  if (session.upload_path) {
    try {
      await supabase.storage.from(VIDEOS_BUCKET).remove([session.upload_path]);
    } catch (removeErr) {
      console.warn(`[Worker Complete] 원본 영상 삭제 오류 (${session.upload_path}):`, removeErr);
    }
  }

  // 4) 워커 상태 갱신 (busy=false, last_seen=now)
  await supabase
    .from('worker_configs')
    .upsert(
      {
        worker_key: 'colab',
        busy: false,
        last_seen: nowIso,
        updated_at: nowIso,
      },
      { onConflict: 'worker_key' }
    );

  return { ok: true, status: 200 };
}

/**
 * 개별 세션 조회 시 상태 및 진행률 지연 계산 (GET /api/sessions/{id}/status)
 */
export async function calculateSessionProgress(
  supabase: SupabaseClient,
  session: {
    id: string;
    status: string;
    fail_reason?: string | null;
    processing_started_at?: string | null;
    uploaded_at?: string | null;
    created_at: string;
  }
): Promise<{
  status: SessionStatus;
  fail_reason: string | null;
  progress: ProcessingProgressInfo | null;
}> {
  const now = Date.now();
  let currentStatus = session.status as SessionStatus;
  let currentFailReason = session.fail_reason || null;

  // 1. 워커 온라인 상태 확인 (last_seen이 90초 이내)
  const { data: workerConfig } = await supabase
    .from('worker_configs')
    .select('last_seen')
    .eq('worker_key', 'colab')
    .maybeSingle();

  const workerIsAlive = isWorkerOnline(workerConfig?.last_seen, now);

  // 2. 단일 세션 지연 안전장치 검사
  if (currentStatus === 'PROCESSING') {
    const startedMs = session.processing_started_at
      ? new Date(session.processing_started_at).getTime()
      : new Date(session.uploaded_at || session.created_at).getTime();

    if (now - startedMs >= PROCESSING_TIMEOUT_SECONDS * 1000) {
      currentStatus = 'FAILED';
      currentFailReason = 'TIMEOUT';
      await supabase
        .from('sessions')
        .update({ status: 'FAILED', fail_reason: 'TIMEOUT' })
        .eq('id', session.id);
    } else if (!workerIsAlive) {
      currentStatus = 'FAILED';
      currentFailReason = 'WORKER_LOST';
      await supabase
        .from('sessions')
        .update({ status: 'FAILED', fail_reason: 'WORKER_LOST' })
        .eq('id', session.id);
    }
  } else if (currentStatus === 'UPLOADED') {
    const uploadedMs = session.uploaded_at
      ? new Date(session.uploaded_at).getTime()
      : new Date(session.created_at).getTime();

    if (now - uploadedMs >= UPLOADED_TIMEOUT_SECONDS * 1000) {
      currentStatus = 'FAILED';
      currentFailReason = 'TIMEOUT';
      await supabase
        .from('sessions')
        .update({ status: 'FAILED', fail_reason: 'TIMEOUT' })
        .eq('id', session.id);
    }
  }

  // 3. 상태별 진행률 객체 계산
  let progress: ProcessingProgressInfo | null = null;

  if (currentStatus === 'UPLOADED') {
    // 나보다 먼저 UPLOADED된 건수 계산
    const uploadedAtFilter = session.uploaded_at || session.created_at;
    const { count: aheadCount } = await supabase
      .from('sessions')
      .select('id', { count: 'exact', head: true })
      .eq('status', 'UPLOADED')
      .lt('uploaded_at', uploadedAtFilter);

    const queuePosition = (aheadCount ?? 0) + 1;

    // 현재 PROCESSING 중인 작업 존재 여부 확인
    const { count: procCount } = await supabase
      .from('sessions')
      .select('id', { count: 'exact', head: true })
      .eq('status', 'PROCESSING');

    const isProcessing = (procCount ?? 0) > 0;
    const workerAvgSec = parseInt(process.env.WORKER_AVG_SECONDS || '60', 10) || DEFAULT_WORKER_AVG_SECONDS;
    const etaSeconds = (queuePosition - 1 + (isProcessing ? 1 : 0)) * workerAvgSec;
    const ahead = Math.max(0, queuePosition - 1);
    const stage = `대기 중 (앞에 ${ahead}명)`;

    progress = {
      elapsed_seconds: 0,
      estimated_total_seconds: etaSeconds || workerAvgSec,
      remaining_seconds: etaSeconds || workerAvgSec,
      progress_percent: 0,
      stage,
      queue_position: queuePosition,
      eta_seconds: etaSeconds,
    };
  } else if (currentStatus === 'PROCESSING') {
    const startedMs = session.processing_started_at
      ? new Date(session.processing_started_at).getTime()
      : new Date(session.uploaded_at || session.created_at).getTime();

    const elapsedSeconds = Math.max(0, Math.floor((now - startedMs) / 1000));
    progress = calculateProcessingProgressFromElapsed(elapsedSeconds);
  } else if (currentStatus === 'DONE') {
    progress = {
      elapsed_seconds: 0,
      estimated_total_seconds: 45,
      remaining_seconds: 0,
      progress_percent: 100,
      stage: '3D 얼굴 생성 완료!',
    };
  } else {
    progress = null;
  }

  return {
    status: currentStatus,
    fail_reason: currentFailReason,
    progress,
  };
}
