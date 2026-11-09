// tests/stateMachine.test.ts
import { describe, it, expect } from 'vitest';
import { canTransition, assertTransition } from '../src/lib/stateMachine';
import { SessionStatus } from '../src/types/session';

describe('State Machine & Transition Rules', () => {
  it('정상적인 순차 상태 전이를 허용해야 한다 (CREATED -> PAIRED -> UPLOADED -> PROCESSING -> DONE)', () => {
    expect(canTransition('CREATED', 'PAIRED')).toBe(true);
    expect(canTransition('PAIRED', 'UPLOADED')).toBe(true);
    expect(canTransition('UPLOADED', 'PROCESSING')).toBe(true);
    expect(canTransition('PROCESSING', 'DONE')).toBe(true);
  });

  it('어느 진행 상태에서든 FAILED로의 전이를 허용해야 한다', () => {
    expect(canTransition('CREATED', 'FAILED')).toBe(true);
    expect(canTransition('PAIRED', 'FAILED')).toBe(true);
    expect(canTransition('UPLOADED', 'FAILED')).toBe(true);
    expect(canTransition('PROCESSING', 'FAILED')).toBe(true);
  });

  it('비정상적이거나 단계를 건너뛴 전이는 엄격히 거부해야 한다', () => {
    expect(canTransition('CREATED', 'UPLOADED')).toBe(false);
    expect(canTransition('CREATED', 'DONE')).toBe(false);
    expect(canTransition('PAIRED', 'DONE')).toBe(false);
    expect(canTransition('UPLOADED', 'DONE')).toBe(false);
    expect(canTransition('UPLOADED', 'PAIRED')).toBe(false); // 역방향 불가
  });

  it('최종 완료 상태(DONE, FAILED)에서는 다른 상태로의 전이를 불허해야 한다', () => {
    expect(canTransition('DONE', 'PROCESSING')).toBe(false);
    expect(canTransition('DONE', 'FAILED')).toBe(false);
    expect(canTransition('FAILED', 'DONE')).toBe(false);
    expect(canTransition('FAILED', 'PROCESSING')).toBe(false);
  });

  it('동일 상태로의 전이는 멱등하게 허용해야 한다', () => {
    expect(canTransition('DONE', 'DONE')).toBe(true);
    expect(canTransition('FAILED', 'FAILED')).toBe(true);
    expect(canTransition('UPLOADED', 'UPLOADED')).toBe(true);
  });

  it('assertTransition은 허용되지 않은 전이 시 명확한 에러를 throw해야 한다', () => {
    expect(() => assertTransition('CREATED', 'DONE')).toThrowError(
      '허용되지 않는 상태 전이입니다: [CREATED] 상태에서 [DONE] 상태로 전이할 수 없습니다.'
    );
    expect(() => assertTransition('CREATED', 'PAIRED')).not.toThrow();
  });
});
