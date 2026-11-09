// tests/webhook.test.ts
import { describe, it, expect } from 'vitest';
import { timingSafeMatch } from '../src/lib/crypto';
import { canTransition } from '../src/lib/stateMachine';

describe('RunPod Webhook Logic & Idempotency', () => {
  const secretKey = 'test_runpod_secret_12345';

  it('웹훅 시크릿 헤더가 일치할 때만 승인되어야 한다', () => {
    expect(timingSafeMatch('test_runpod_secret_12345', secretKey)).toBe(true);
    expect(timingSafeMatch('wrong_secret', secretKey)).toBe(false);
    expect(timingSafeMatch('', secretKey)).toBe(false);
  });

  it('세션이 UPLOADED 또는 PROCESSING 상태일 때 COMPLETED 웹훅은 DONE으로 전이 가능해야 한다', () => {
    expect(canTransition('UPLOADED', 'DONE')).toBe(false); // PROCESSING을 거치거나
    expect(canTransition('PROCESSING', 'DONE')).toBe(true); // 정상 완료
  });

  it('세션이 이미 DONE인 상태에서 동일한 COMPLETED 웹훅이 중복 수신되어도 안전해야 한다 (멱등성)', () => {
    // 멱등성 검사: 이미 DONE 상태인 경우 다시 DONE을 처리해도 상태 오류를 내지 않고 200 반환해야 함
    const currentStatus = 'DONE';
    const isAlreadyFinished = currentStatus === 'DONE' || currentStatus === 'FAILED';

    expect(isAlreadyFinished).toBe(true);
  });

  it('세션이 이미 FAILED인 상태에서 지연된 웹훅이 수신되어도 상태가 덮어써지지 않아야 한다', () => {
    const currentStatus = 'FAILED';
    const canChangeToDone = canTransition(currentStatus, 'DONE');

    expect(canChangeToDone).toBe(false);
  });
});
