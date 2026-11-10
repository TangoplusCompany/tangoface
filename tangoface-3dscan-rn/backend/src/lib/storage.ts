// src/lib/storage.ts
import { SupabaseClient } from '@supabase/supabase-js';

export const VIDEOS_BUCKET = 'videos';
export const RESULTS_BUCKET = 'results';
export const MAX_VIDEO_SIZE_BYTES = 200 * 1024 * 1024; // 200MB
export const ALLOWED_VIDEO_MIME_TYPES = ['video/mp4', 'video/quicktime'];
export const RESULT_DOWNLOAD_EXPIRY_SECONDS = 300; // 5분

/**
 * 폰의 직접 영상 업로드용 Supabase Signed Upload URL 발급
 * 경로: {session_id}/{random_uuid}.mp4
 * (공식 SDK의 createSignedUploadUrl 사용: URL 및 upload token 반환)
 */
export async function createVideoUploadSignedUrl(
  supabase: SupabaseClient,
  sessionId: string,
  randomId: string
): Promise<{ uploadPath: string; signedUrl: string; token: string }> {
  const uploadPath = `${sessionId}/${randomId}.mp4`;

  const { data, error } = await supabase.storage
    .from(VIDEOS_BUCKET)
    .createSignedUploadUrl(uploadPath);

  if (error || !data) {
    throw new Error(`서명 업로드 URL 생성 실패: ${error?.message || '알 수 없는 오류'}`);
  }

  // 상대 경로인 경우 Supabase 프로젝트 URL 결합
  const baseUrl = process.env.SUPABASE_URL || '';
  const fullSignedUrl = data.signedUrl.startsWith('http')
    ? data.signedUrl
    : `${baseUrl}/storage/v1${data.signedUrl}`;

  return {
    uploadPath,
    signedUrl: fullSignedUrl,
    token: data.token,
  };
}

export interface VerifyStorageFileResult {
  valid: boolean;
  reason?: string;
  size?: number;
  mimeType?: string;
}

/**
 * complete 단계: Storage에 파일이 실제 존재하는지 확인하고 200MB 초과 시 삭제
 */
export async function verifyAndInspectVideo(
  supabase: SupabaseClient,
  uploadPath: string
): Promise<VerifyStorageFileResult> {
  const lastSlashIndex = uploadPath.lastIndexOf('/');
  const folder = lastSlashIndex !== -1 ? uploadPath.substring(0, lastSlashIndex) : '';
  const fileName = lastSlashIndex !== -1 ? uploadPath.substring(lastSlashIndex + 1) : uploadPath;

  const { data: files, error } = await supabase.storage
    .from(VIDEOS_BUCKET)
    .list(folder, { search: fileName });

  if (error || !files || files.length === 0) {
    return {
      valid: false,
      reason: '스토리지에 업로드된 파일이 존재하지 않습니다.',
    };
  }

  const targetFile = files.find((f) => f.name === fileName) || files[0];
  const metadata = targetFile.metadata as Record<string, any> | undefined;
  const size = metadata?.size || 0;
  const mimeType = metadata?.mimetype || '';

  // 1. 크기 초과 검사 (200MB 초과 시 즉시 파일 삭제)
  if (size > MAX_VIDEO_SIZE_BYTES) {
    await supabase.storage.from(VIDEOS_BUCKET).remove([uploadPath]);
    return {
      valid: false,
      reason: `파일 크기(${Math.round(size / (1024 * 1024))}MB)가 최대 허용치(200MB)를 초과하여 자동 삭제되었습니다.`,
      size,
    };
  }

  // 2. MIME 타입 검사 (video/mp4, video/quicktime)
  if (mimeType && !ALLOWED_VIDEO_MIME_TYPES.includes(mimeType)) {
    await supabase.storage.from(VIDEOS_BUCKET).remove([uploadPath]);
    return {
      valid: false,
      reason: `지원되지 않는 형식(${mimeType})입니다. video/mp4 또는 video/quicktime만 지원됩니다.`,
      size,
      mimeType,
    };
  }

  return {
    valid: true,
    size,
    mimeType,
  };
}

/**
 * GLB 3D 결과 다운로드용 5분 만료 Signed URL 발급
 */
export async function createResultSignedDownloadUrl(
  supabase: SupabaseClient,
  resultPath: string
): Promise<string> {
  const { data, error } = await supabase.storage
    .from(RESULTS_BUCKET)
    .createSignedUrl(resultPath, RESULT_DOWNLOAD_EXPIRY_SECONDS);

  if (error || !data?.signedUrl) {
    throw new Error(`결과 다운로드 URL 생성 실패: ${error?.message || '알 수 없는 오류'}`);
  }

  return data.signedUrl;
}

/**
 * Colab 워커용: videos 버킷 영상 다운로드 Signed URL 발급 (기본 1시간 유효)
 */
export async function createVideoSignedDownloadUrl(
  supabase: SupabaseClient,
  uploadPath: string,
  expiresInSeconds = 3600
): Promise<string> {
  const { data, error } = await supabase.storage
    .from(VIDEOS_BUCKET)
    .createSignedUrl(uploadPath, expiresInSeconds);

  if (error || !data?.signedUrl) {
    throw new Error(`영상 다운로드 URL 생성 실패: ${error?.message || '알 수 없는 오류'}`);
  }

  const baseUrl = process.env.SUPABASE_URL || '';
  return data.signedUrl.startsWith('http')
    ? data.signedUrl
    : `${baseUrl}/storage/v1${data.signedUrl}`;
}

/**
 * Colab 워커용: results 버킷 GLB 직접 업로드 Signed URL 발급 (upsert 허용)
 */
export async function createResultSignedUploadUrl(
  supabase: SupabaseClient,
  resultPath: string
): Promise<string> {
  const { data, error } = await supabase.storage
    .from(RESULTS_BUCKET)
    .createSignedUploadUrl(resultPath, { upsert: true });

  if (error || !data?.signedUrl) {
    throw new Error(`결과 업로드 URL 생성 실패: ${error?.message || '알 수 없는 오류'}`);
  }

  const baseUrl = process.env.SUPABASE_URL || '';
  return data.signedUrl.startsWith('http')
    ? data.signedUrl
    : `${baseUrl}/storage/v1${data.signedUrl}`;
}

/**
 * results 버킷에 실제 GLB 파일이 존재하는지 확인
 */
export async function checkResultFileExists(
  supabase: SupabaseClient,
  resultPath: string
): Promise<boolean> {
  const lastSlashIndex = resultPath.lastIndexOf('/');
  const folder = lastSlashIndex !== -1 ? resultPath.substring(0, lastSlashIndex) : '';
  const fileName = lastSlashIndex !== -1 ? resultPath.substring(lastSlashIndex + 1) : resultPath;

  const { data: files, error } = await supabase.storage
    .from(RESULTS_BUCKET)
    .list(folder, { search: fileName });

  if (error || !files || files.length === 0) {
    return false;
  }

  return files.some((f) => f.name === fileName);
}
