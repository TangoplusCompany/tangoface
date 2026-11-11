// src/types/api.ts
import { SessionStatus } from './session';

export interface CreateSessionResponse {
  session_id: string;
  code: string;
  session_secret: string;
  expires_in_seconds: number;
}

export interface PairSessionResponse {
  session_id: string;
  pair_token: string;
}

export interface PresignUploadResponse {
  upload_path: string;
  signed_url: string;
  upload_url?: string;
  token?: string;
  max_size_bytes: number;
  allowed_mime_types: string[];
}

export interface CompleteUploadResponse {
  status: 'UPLOADED' | 'PROCESSING';
  message: string;
  queue_full?: boolean;
}

// 3D 모델 복원 진행률 및 남은 시간 예측 정보
export interface ProcessingProgressInfo {
  elapsed_seconds: number;        // 현재까지 진행된 시간 (초)
  estimated_total_seconds: number;// 예상 총 소요 시간 (초, 예: 45초)
  remaining_seconds: number;      // 예상 남은 시간 (초)
  progress_percent: number;       // 0 ~ 99% 점근 진행률
  stage: string;                  // 현재 처리 단계 안내 문구
  queue_position?: number;        // 대기열 순번 (1부터 시작, 1이 다음 차례)
  eta_seconds?: number;           // 예상 대기 시간 (초)
}

export interface SessionStatusResponse {
  session_id: string;
  status: SessionStatus;
  fail_reason: string | null;
  fail_code?: string | null;
  paired_at: string | null;
  uploaded_at: string | null;
  created_at: string;
  // PROCESSING 상태일 때 실시간 계산되는 스마트 진행률 객체
  progress?: ProcessingProgressInfo | null;
}

export interface SessionResultResponse {
  session_id: string;
  status: 'DONE';
  model_url: string;
  download_url?: string;
  expires_in_seconds: number;
}
