/**
 * TangoFace 3D Scan - API Client
 * 백엔드(Next.js API) 및 Supabase Storage 직접 업로드 연동
 */
import { Platform } from 'react-native';

// 프로덕션 Vercel 백엔드 기본 주소
const PRODUCTION_API_URL = 'https://tangoface-backend.vercel.app';

// 백엔드 API 기본 주소 설정 (모바일 브라우저 / PC 브라우저 자동 감지)
export const getApiBaseUrl = (): string => {
  if (process.env.EXPO_PUBLIC_API_URL) {
    return process.env.EXPO_PUBLIC_API_URL;
  }
  if (Platform.OS === 'web' && typeof window !== 'undefined') {
    const hostname = window.location.hostname;
    // PC 로컬 개발 환경인 경우에만 localhost:3000 사용
    if (hostname === 'localhost' || hostname === '127.0.0.1') {
      return 'http://localhost:3000';
    }
    // IP 주소로 직접 접속한 로컬 테스트 환경 (예: 192.168.x.x)
    if (/^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(hostname)) {
      return `http://${hostname}:3000`;
    }
    // Vercel 등 실제 배포 도메인 환경인 경우 프로덕션 백엔드 URL 사용
    return PRODUCTION_API_URL;
  }
  return PRODUCTION_API_URL;
};

/**
 * 1. 6자리 페어링 코드로 세션 연결
 */
export async function pairSession(code: string): Promise<{
  success: boolean;
  pair_token?: string;
  session_id?: string;
  error?: string;
}> {
  try {
    const baseUrl = getApiBaseUrl();
    const res = await fetch(`${baseUrl}/api/sessions/pair`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ code }),
    });

    const data = await res.json();
    if (!res.ok) {
      return { success: false, error: data.error || '페어링에 실패했습니다.' };
    }

    return {
      success: true,
      pair_token: data.pair_token,
      session_id: data.session_id,
    };
  } catch (err: any) {
    return {
      success: false,
      error: `서버 연결 실패: ${err.message || '네트워크를 확인해주세요.'}`,
    };
  }
}

/**
 * 2. Supabase Storage Signed Upload URL 발급 요청
 */
export async function getPresignedUploadUrl(pairToken: string): Promise<{
  success: boolean;
  upload_url?: string;
  path?: string;
  error?: string;
}> {
  try {
    const baseUrl = getApiBaseUrl();
    const res = await fetch(`${baseUrl}/api/uploads/presign`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${pairToken}`,
      },
    });

    const data = await res.json();
    if (!res.ok) {
      return { success: false, error: data.error || '업로드 URL 발급 실패' };
    }

    return {
      success: true,
      upload_url: data.signed_url || data.upload_url,
      path: data.upload_path || data.path,
    };
  } catch (err: any) {
    return {
      success: false,
      error: `업로드 URL 발급 중 오류: ${err.message}`,
    };
  }
}

/**
 * 3. Supabase Storage에 직접 바이너리 PUT 전송 (진행률 지원)
 * Vercel 서버리스를 거치지 않고 브라우저/스마트폰 -> Supabase Storage로 직송
 */
export function uploadVideoDirectly(
  uploadUrl: string,
  fileOrBlob: Blob | File,
  onProgress?: (percent: number) => void
): Promise<{ success: boolean; error?: string }> {
  return new Promise((resolve) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', uploadUrl, true);

    // Supabase Storage Signed Upload URL 표준 Content-Type (코덱 파라미터 제거)
    const rawType = (fileOrBlob.type || 'video/mp4').split(';')[0].trim();
    const contentType = rawType || 'video/mp4';
    xhr.setRequestHeader('Content-Type', contentType);

    if (xhr.upload && onProgress) {
      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable) {
          const percent = Math.round((event.loaded / event.total) * 100);
          onProgress(percent);
        }
      };
    }

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve({ success: true });
      } else {
        resolve({
          success: false,
          error: `스토리지 업로드 실패 (HTTP ${xhr.status}): ${xhr.responseText || ''}`,
        });
      }
    };

    xhr.onerror = () => {
      resolve({
        success: false,
        error: '스토리지 업로드 중 네트워크 에러가 발생했습니다.',
      });
    };

    xhr.send(fileOrBlob);
  });
}

/**
 * 4. 업로드 완료 서버에 검증 및 상태 변경 요청
 */
export async function completeUpload(pairToken: string): Promise<{
  success: boolean;
  status?: string;
  queueFull?: boolean;
  message?: string;
  session?: any;
  error?: string;
}> {
  try {
    const baseUrl = getApiBaseUrl();
    const res = await fetch(`${baseUrl}/api/uploads/complete`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${pairToken}`,
      },
    });

    const data = await res.json();
    if (!res.ok) {
      return { success: false, error: data.error || '업로드 검증 실패' };
    }

    return {
      success: true,
      status: data.status,
      queueFull: Boolean(data.queue_full),
      message: data.message,
      session: data.session,
    };
  } catch (err: any) {
    return {
      success: false,
      error: `완료 보고 중 오류: ${err.message}`,
    };
  }
}

export interface ColabHealthStatus {
  alive: boolean;
  status: string;
  message: string;
  busy?: boolean;
  queue?: number;
}

/**
 * 5. Colab 3D 복원 서버 가동 상태 확인 (/api/worker/health)
 */
export async function checkColabHealth(): Promise<ColabHealthStatus> {
  try {
    const baseUrl = getApiBaseUrl();
    const res = await fetch(`${baseUrl}/api/worker/health`, {
      method: 'GET',
      cache: 'no-store',
    });

    if (!res.ok) {
      return {
        alive: false,
        status: 'ERROR',
        message: '서버 상태 조회에 실패했습니다.',
      };
    }

    const data = await res.json();
    return {
      alive: Boolean(data.alive),
      status: data.status || 'UNKNOWN',
      message: data.message || '',
      busy: typeof data.busy === 'boolean' ? data.busy : undefined,
      queue: typeof data.queue === 'number' ? data.queue : undefined,
    };
  } catch {
    return {
      alive: false,
      status: 'NETWORK_ERROR',
      message: '백엔드 서버에 연결할 수 없습니다.',
    };
  }
}
