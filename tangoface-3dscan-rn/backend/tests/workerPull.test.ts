// tests/workerPull.test.ts
import { describe, it, expect, beforeEach } from 'vitest';
import {
  isWorkerOnline,
  checkWorkerRateLimit,
  verifyWorkerSecret,
  resetWorkerRateLimitForTest,
  WORKER_HEARTBEAT_TIMEOUT_SECONDS,
} from '../src/lib/workerPull';

describe('Worker Pull Architecture Unit Tests', () => {
  beforeEach(() => {
    resetWorkerRateLimitForTest();
    process.env.WORKER_SECRET = 'test-secret-1234567890';
  });

  describe('isWorkerOnline', () => {
    it('last_seen이 null이거나 undefined이면 오프라인이어야 한다', () => {
      expect(isWorkerOnline(null)).toBe(false);
      expect(isWorkerOnline(undefined)).toBe(false);
      expect(isWorkerOnline('')).toBe(false);
    });

    it('last_seen이 90초 이내이면 온라인이어야 한다', () => {
      const now = 1000000000000;
      const recent = new Date(now - 30 * 1000).toISOString(); // 30초 전
      expect(isWorkerOnline(recent, now)).toBe(true);

      const boundary = new Date(now - WORKER_HEARTBEAT_TIMEOUT_SECONDS * 1000).toISOString(); // 정확히 90초 전
      expect(isWorkerOnline(boundary, now)).toBe(true);
    });

    it('last_seen이 90초를 초과하면 오프라인이어야 한다', () => {
      const now = 1000000000000;
      const expired = new Date(now - 91 * 1000).toISOString(); // 91초 전
      expect(isWorkerOnline(expired, now)).toBe(false);

      const longAgo = new Date(now - 300 * 1000).toISOString(); // 5분 전
      expect(isWorkerOnline(longAgo, now)).toBe(false);
    });
  });

  describe('verifyWorkerSecret', () => {
    it('정확한 x-worker-secret 헤더가 주어지면 통과해야 한다', () => {
      const req = new Request('http://localhost/api/worker/claim', {
        headers: { 'x-worker-secret': 'test-secret-1234567890' },
      });
      expect(verifyWorkerSecret(req)).toBe(true);
    });

    it('대소문자 X-Worker-Secret 헤더도 정상 인식해야 한다', () => {
      const req = new Request('http://localhost/api/worker/claim', {
        headers: { 'X-Worker-Secret': 'test-secret-1234567890' },
      });
      expect(verifyWorkerSecret(req)).toBe(true);
    });

    it('시크릿이 일치하지 않거나 누락되면 실패해야 한다', () => {
      const req1 = new Request('http://localhost/api/worker/claim', {
        headers: { 'x-worker-secret': 'wrong-secret' },
      });
      expect(verifyWorkerSecret(req1)).toBe(false);

      const req2 = new Request('http://localhost/api/worker/claim', {
        headers: {},
      });
      expect(verifyWorkerSecret(req2)).toBe(false);
    });
  });

  describe('checkWorkerRateLimit', () => {
    it('1분 내 60회까지는 허용하고 61회째부터 차단해야 한다', () => {
      const req = new Request('http://localhost/api/worker/claim', {
        headers: { 'x-forwarded-for': '10.20.30.40' },
      });

      for (let i = 0; i < 60; i++) {
        expect(checkWorkerRateLimit(req)).toBe(true);
      }

      // 61번째 요청은 차단
      expect(checkWorkerRateLimit(req)).toBe(false);
    });
  });
});
