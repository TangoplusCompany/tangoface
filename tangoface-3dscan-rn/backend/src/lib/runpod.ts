// src/lib/runpod.ts

export interface StartProcessingResult {
  jobId?: string;
  status: 'SUBMITTED' | 'SKIPPED';
}

/**
 * RunPod 3D 복원 작업 트리거 인터페이스
 *
 * TODO: RunPod 연동 구현
 * 1. RunPod Serverless API Key 및 Endpoint 설정
 * 2. Supabase Storage의 영상 경로(upload_path) 전달
 * 3. 완료 시 Webhook URL (/api/webhooks/runpod) 지정
 * 4. 세션 상태를 UPLOADED -> PROCESSING 으로 전이
 */
export async function startProcessing(sessionId: string): Promise<StartProcessingResult> {
  // 이번 스코프에서는 RunPod 실제 호출을 수행하지 않고 인터페이스만 제공합니다.
  console.log(`[RunPod TODO] startProcessing 호출됨: session_id = ${sessionId}`);

  return {
    status: 'SKIPPED',
  };
}
