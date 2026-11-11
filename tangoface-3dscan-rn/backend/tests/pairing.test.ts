// tests/pairing.test.ts
import { describe, it, expect } from 'vitest';
import {
  generatePairingCode,
  generateSecureToken,
  hashToken,
  timingSafeMatch,
} from '../src/lib/crypto';

describe('Pairing & Crypto Utilities', () => {
  it('페어링 번호는 항상 6자리 숫자(100000 ~ 999999)여야 한다', () => {
    for (let i = 0; i < 100; i++) {
      const code = generatePairingCode();
      expect(code).toMatch(/^\d{6}$/);
      const val = parseInt(code, 10);
      expect(val).toBeGreaterThanOrEqual(100000);
      expect(val).toBeLessThan(1000000);
    }
  });

  it('32바이트 보안 토큰을 고유한 64자 Hex 문자열로 생성해야 한다', () => {
    const token1 = generateSecureToken(32);
    const token2 = generateSecureToken(32);
    expect(token1).toHaveLength(64);
    expect(token2).toHaveLength(64);
    expect(token1).not.toBe(token2);
  });

  it('동일 토큰에 대해 동일한 SHA-256 해시를 반환해야 한다', () => {
    const token = 'my-super-secret-token-12345';
    const hash1 = hashToken(token);
    const hash2 = hashToken(token);
    expect(hash1).toHaveLength(64);
    expect(hash1).toBe(hash2);
    expect(hash1).not.toBe(hashToken('different-token'));
  });

  it('timingSafeMatch는 일치 시 true, 불일치 시 false를 반환해야 한다', () => {
    const secret = 'd3b07384d113edec49eaa6238ad5ff00';
    expect(timingSafeMatch(secret, secret)).toBe(true);
    expect(timingSafeMatch(secret, 'd3b07384d113edec49eaa6238ad5ff01')).toBe(false);
    expect(timingSafeMatch(secret, 'short')).toBe(false);
  });

  it('15분 만료 계산이 정확해야 한다', () => {
    const now = 1700000000000;
    const createdAt = new Date(now).toISOString();
    const fifteenMinutesMs = 15 * 60 * 1000;

    // 14분 경과: 유효
    const fourteenMinsLater = now + 14 * 60 * 1000;
    const isValidAt14Mins = new Date(createdAt).getTime() + fifteenMinutesMs > fourteenMinsLater;
    expect(isValidAt14Mins).toBe(true);

    // 15분 1초 경과: 만료
    const fifteenMinsOneSecLater = now + 15 * 60 * 1000 + 1000;
    const isValidAt15Mins = new Date(createdAt).getTime() + fifteenMinutesMs > fifteenMinsOneSecLater;
    expect(isValidAt15Mins).toBe(false);
  });
});
