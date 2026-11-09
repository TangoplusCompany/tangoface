// src/lib/cors.ts
import { NextResponse } from 'next/server';

/**
 * 환경변수 ALLOWED_ORIGINS 기반의 엄격한 CORS 헤더 반환 (와일드카드 * 금지)
 */
export function getCorsHeaders(request: Request): Record<string, string> {
  const allowedOrigins = (process.env.ALLOWED_ORIGINS || '')
    .split(',')
    .map((o) => o.trim())
    .filter(Boolean);

  const requestOrigin = request.headers.get('origin');
  const headers: Record<string, string> = {
    'Vary': 'Origin',
  };

  if (requestOrigin && allowedOrigins.includes(requestOrigin)) {
    headers['Access-Control-Allow-Origin'] = requestOrigin;
    headers['Access-Control-Allow-Methods'] = 'GET, POST, OPTIONS';
    headers['Access-Control-Allow-Headers'] =
      'Content-Type, Authorization, X-Session-Secret, X-Runpod-Secret';
    headers['Access-Control-Max-Age'] = '86400';
  }

  return headers;
}

/**
 * OPTIONS 프리플라이트 요청 처리
 */
export function handleOptionsCors(request: Request): NextResponse {
  const corsHeaders = getCorsHeaders(request);
  return new NextResponse(null, {
    status: 204,
    headers: corsHeaders,
  });
}

/**
 * 에러 또는 정상 응답에 CORS 헤더를 병합하여 반환
 */
export function jsonWithCors(
  request: Request,
  data: any,
  init?: ResponseInit
): NextResponse {
  const corsHeaders = getCorsHeaders(request);
  const responseHeaders = new Headers(init?.headers);

  for (const [k, v] of Object.entries(corsHeaders)) {
    responseHeaders.set(k, v);
  }

  return NextResponse.json(data, {
    ...init,
    headers: responseHeaders,
  });
}
