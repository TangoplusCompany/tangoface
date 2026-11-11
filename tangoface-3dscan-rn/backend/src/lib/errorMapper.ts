// src/lib/errorMapper.ts

/**
 * Colab 워커 및 백엔드 에러 코드 -> 사용자 친화적 한국어 안내 문구 매핑
 * (모든 실패 상태는 클라이언트가 즉시 재촬영/재전송 가능)
 */
export const FAIL_REASON_MESSAGES: Record<string, string> = {
  FACE_NOT_FOUND:
    '영상에서 얼굴을 인식하지 못했습니다. 밝은 곳에서 정면을 바라보고 다시 촬영해 주세요.',
  TOO_BLURRY:
    '영상이 흔들리거나 흐리게 촬영되었습니다. 카메라를 고정하고 천천히 다시 촬영해 주세요.',
  ANGLE_RANGE_TOO_NARROW:
    '촬영 각도 범위가 부족합니다. 좌측(-60°)에서 우측(+60°)까지 곡선으로 천천히 회전해 주세요.',
  GPU_OOM:
    '서버 그래픽 처리 용량이 일시적으로 부족합니다. 잠시 후 다시 시도해 주세요.',
  TIMEOUT:
    '3D 모델 생성 시간이 제한(6분)을 초과했습니다. 잠시 후 다시 시도해 주세요.',
  VIDEO_DOWNLOAD_FAILED:
    '서버가 업로드된 영상을 불러오지 못했습니다. 다시 시도해 주세요.',
  UPLOAD_FAILED:
    '생성된 3D 모델 저장에 실패했습니다. 잠시 후 다시 시도해 주세요.',
  RECONSTRUCTION_FAILED:
    '3D 얼굴 모델 생성에 실패했습니다. 가이드를 확인한 뒤 다시 촬영해 주세요.',
  WORKER_RESTARTED:
    '3D 변환 서버가 재시작되어 작업이 중단되었습니다. 다시 시도해 주세요.',
};

/**
 * 원시 fail_reason(코드 또는 메시지)을 화면 표시에 적합한 안내 문구로 변환
 */
export function mapFailReasonToMessage(failReason: string | null | undefined): string | null {
  if (!failReason) return null;
  const trimmed = failReason.trim();
  if (!trimmed) return null;

  const upper = trimmed.toUpperCase();
  if (FAIL_REASON_MESSAGES[upper]) {
    return FAIL_REASON_MESSAGES[upper];
  }

  return trimmed;
}
