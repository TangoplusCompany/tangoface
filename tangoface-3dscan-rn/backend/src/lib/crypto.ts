// src/lib/crypto.ts
import crypto from 'node:crypto';

/**
 * 6자리 숫자 페어링 번호 생성 (100000 ~ 999999)
 * Node.js crypto.randomInt를 사용한 균등 난수
 */
export function generatePairingCode(): string {
  return crypto.randomInt(100000, 1000000).toString();
}

/**
 * 암호학적으로 안전한 32바이트 랜덤 토큰 생성 (64자 Hex 문자열)
 */
export function generateSecureToken(bytes = 32): string {
  return crypto.randomBytes(bytes).toString('hex');
}

/**
 * SHA-256 해시 생성
 */
export function hashToken(token: string): string {
  return crypto.createHash('sha256').update(token).digest('hex');
}

/**
 * IP 주소를 salt와 결합하여 SHA-256 해시 (개인정보 보호 및 대입 공격 방어용)
 */
export function hashIp(ip: string, salt = process.env.IP_HASH_SALT || 'default_tango_salt'): string {
  return crypto.createHash('sha256').update(`${ip}:${salt}`).digest('hex');
}

/**
 * 타이밍 공격 방지 문자열 비교 (Timing-Safe Equal)
 */
export function timingSafeMatch(a: string, b: string): boolean {
  if (typeof a !== 'string' || typeof b !== 'string') {
    return false;
  }
  const bufA = Buffer.from(a, 'utf8');
  const bufB = Buffer.from(b, 'utf8');

  if (bufA.length !== bufB.length) {
    return false;
  }

  return crypto.timingSafeEqual(bufA, bufB);
}
