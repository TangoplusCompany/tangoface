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
  token?: string;
  max_size_bytes: number;
  allowed_mime_types: string[];
}

export interface CompleteUploadResponse {
  status: 'UPLOADED';
  message: string;
}

export interface SessionStatusResponse {
  session_id: string;
  status: SessionStatus;
  fail_reason: string | null;
  paired_at: string | null;
  uploaded_at: string | null;
  created_at: string;
}

export interface SessionResultResponse {
  session_id: string;
  status: 'DONE';
  model_url: string;
  expires_in_seconds: number;
}
