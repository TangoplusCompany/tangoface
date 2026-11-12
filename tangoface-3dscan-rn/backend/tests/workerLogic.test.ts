// tests/workerLogic.test.ts
import { describe, it, expect } from 'vitest';
import { mapFailReasonToMessage, FAIL_REASON_MESSAGES } from '../src/lib/errorMapper';
import { calculateProcessingProgressFromElapsed } from '../src/lib/progressEstimator';
import { isValidColabWorkerUrl } from '../src/lib/runpod';

describe('Colab Worker Error Mapping & Progress Estimation', () => {
  it('모든 Colab 에러 코드가 사용자 친화적 한국어 안내 문구로 매핑되어야 한다', () => {
    const requiredCodes = [
      'FACE_NOT_FOUND',
      'TOO_BLURRY',
      'ANGLE_RANGE_TOO_NARROW',
      'FACE_TOO_CLOSE',
      'GPU_OOM',
      'TIMEOUT',
      'VIDEO_DOWNLOAD_FAILED',
      'UPLOAD_FAILED',
      'RECONSTRUCTION_FAILED',
      'WORKER_RESTARTED',
      'WORKER_LOST',
    ];

    for (const code of requiredCodes) {
      const msg = mapFailReasonToMessage(code);
      expect(msg).toBeTruthy();
      expect(msg).not.toBe(code);
      expect(msg).toBe(FAIL_REASON_MESSAGES[code]);
    }
  });

  it('미정의 문자열은 원문 그대로 반환하고 빈 값은 null을 반환해야 한다', () => {
    expect(mapFailReasonToMessage(null)).toBeNull();
    expect(mapFailReasonToMessage('')).toBeNull();
    expect(mapFailReasonToMessage('알 수 없는 오류')).toBe('알 수 없는 오류');
  });

  it('경과 시간 기준 진행률 계산이 0~99% 범위 내에서 정상 동작해야 한다', () => {
    const early = calculateProcessingProgressFromElapsed(10);
    expect(early.progress_percent).toBeGreaterThan(0);
    expect(early.progress_percent).toBeLessThan(100);

    const overtime = calculateProcessingProgressFromElapsed(200);
    expect(overtime.progress_percent).toBeLessThanOrEqual(99);
  });

  it('api.trycloudflare.com 같은 Cloudflare 제어 도메인은 거부하고 실제 터널 도메인만 허용해야 한다', () => {
    expect(isValidColabWorkerUrl('https://api.trycloudflare.com')).toBe(false);
    expect(isValidColabWorkerUrl('https://trycloudflare.com')).toBe(false);
    expect(isValidColabWorkerUrl('https://api.cloudflare.com')).toBe(false);
    expect(isValidColabWorkerUrl('http://some-tunnel.trycloudflare.com')).toBe(false);
    expect(isValidColabWorkerUrl('https://some-tunnel-abc.trycloudflare.com')).toBe(true);
  });
});
