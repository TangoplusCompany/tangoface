// src/lib/stateMachine.ts
import { SessionStatus } from '../types/session';

/**
 * 허용된 세션 상태 전이 규칙 맵
 */
export const ALLOWED_TRANSITIONS: Record<SessionStatus, readonly SessionStatus[]> = {
  CREATED: ['PAIRED', 'FAILED'],
  PAIRED: ['UPLOADED', 'FAILED'],
  UPLOADED: ['PROCESSING', 'FAILED'],
  PROCESSING: ['DONE', 'FAILED'],
  DONE: [], // 최종 상태 (전이 불가)
  FAILED: [], // 최종 상태 (전이 불가)
};

/**
 * 상태 전이가 유효한지 검사
 */
export function canTransition(from: SessionStatus, to: SessionStatus): boolean {
  if (from === to) {
    return true; // 동일 상태 재설정(멱등성)은 허용
  }
  const allowed = ALLOWED_TRANSITIONS[from];
  return allowed ? allowed.includes(to) : false;
}

/**
 * 상태 전이 강제 검증 (유효하지 않을 경우 명확한 에러 발생)
 */
export function assertTransition(from: SessionStatus, to: SessionStatus): void {
  if (!canTransition(from, to)) {
    throw new Error(
      `허용되지 않는 상태 전이입니다: [${from}] 상태에서 [${to}] 상태로 전이할 수 없습니다.`
    );
  }
}
