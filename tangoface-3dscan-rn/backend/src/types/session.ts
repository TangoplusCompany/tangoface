// src/types/session.ts

export type SessionStatus =
  | 'CREATED'
  | 'PAIRED'
  | 'UPLOADED'
  | 'PROCESSING'
  | 'DONE'
  | 'FAILED';

export interface SessionRow {
  id: string;
  code: string | null;
  status: SessionStatus;
  fail_reason: string | null;
  session_secret_hash: string;
  pair_token_hash: string | null;
  upload_path: string | null;
  result_path: string | null;
  created_at: string;
  expires_at: string;
  paired_at: string | null;
  uploaded_at: string | null;
  processing_started_at?: string | null;
  not_found_count?: number;
}

export interface PairAttemptRow {
  id: number;
  ip_hash: string;
  succeeded: boolean;
  attempted_at: string;
}
