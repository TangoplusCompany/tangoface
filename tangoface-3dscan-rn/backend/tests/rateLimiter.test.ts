// tests/rateLimiter.test.ts
import { describe, it, expect } from 'vitest';
import { hashIp } from '../src/lib/crypto';
import { checkPairRateLimit } from '../src/lib/rateLimiter';

describe('Rate Limiter & IP Protection', () => {
  it('동일 IP라도 salt가 다르면 서로 다른 해시를 생성해야 한다', () => {
    const ip = '198.51.100.42';
    const hash1 = hashIp(ip, 'salt_alpha');
    const hash2 = hashIp(ip, 'salt_beta');

    expect(hash1).toHaveLength(64);
    expect(hash2).toHaveLength(64);
    expect(hash1).not.toBe(hash2);
    expect(hash1).toBe(hashIp(ip, 'salt_alpha')); // 동일 salt는 동일 해시
  });

  it('IP별 최근 10분 내 실패 횟수가 5회 미만이면 통과해야 한다', async () => {
    const mockSupabase = {
      from: (table: string) => ({
        select: () => ({
          eq: (field1: string, val1: any) => ({
            eq: (field2: string, val2: any) => ({
              gte: async () => ({ count: 3, error: null }),
            }),
            gte: async () => ({ count: 5, error: null }),
          }),
        }),
      }),
    } as any;

    const result = await checkPairRateLimit(mockSupabase, 'dummy_ip_hash');
    expect(result.allowed).toBe(true);
    expect(result.remaining).toBe(2); // 5 - 3
  });

  it('IP별 최근 10분 내 실패 횟수가 5회 이상이면 차단되어야 한다', async () => {
    const mockSupabase = {
      from: (table: string) => ({
        select: () => ({
          eq: (field1: string, val1: any) => ({
            eq: (field2: string, val2: any) => ({
              gte: async () => ({ count: 5, error: null }), // 5회 실패
            }),
            gte: async () => ({ count: 5, error: null }),
          }),
        }),
      }),
    } as any;

    const result = await checkPairRateLimit(mockSupabase, 'dummy_ip_hash');
    expect(result.allowed).toBe(false);
    expect(result.remaining).toBe(0);
    expect(result.reason).toBe('IP_RATE_LIMITED');
    expect(result.retryAfterSeconds).toBe(600); // 10분
  });

  it('전역 실패 횟수가 30회 이상 급증하면 일시 차단되어야 한다', async () => {
    const mockSupabase = {
      from: (table: string) => ({
        select: () => ({
          eq: (field1: string, val1: any) => {
            if (field1 === 'ip_hash') {
              return {
                eq: () => ({
                  gte: async () => ({ count: 1, error: null }), // 내 IP는 1회만 실패
                }),
              };
            }
            if (field1 === 'succeeded') {
              return {
                gte: async () => ({ count: 35, error: null }), // 전역은 35회 실패
              };
            }
            return { gte: async () => ({ count: 0, error: null }) };
          },
        }),
      }),
    } as any;

    const result = await checkPairRateLimit(mockSupabase, 'dummy_ip_hash');
    expect(result.allowed).toBe(false);
    expect(result.reason).toBe('GLOBAL_RATE_LIMITED');
    expect(result.retryAfterSeconds).toBe(30);
  });
});
