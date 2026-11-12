// src/app/api/worker/register/route.ts
import { NextRequest } from 'next/server';
import { jsonWithCors, handleOptionsCors } from '@/lib/cors';

export const runtime = 'nodejs';

export async function OPTIONS(request: NextRequest) {
  return handleOptionsCors(request);
}

/**
 * /api/worker/register (사용 중단 - 410 Gone)
 * Colab 워커가 Pull 방식으로 전환되어 더 이상 워커 URL을 등록하지 않습니다.
 * 대신 POST /api/worker/claim 을 사용하여 작업을 가져옵니다.
 */
export async function POST(request: NextRequest) {
  return jsonWithCors(
    request,
    {
      error: '이 엔드포인트는 사용이 중단되었습니다 (410 Gone). Colab 워커는 POST /api/worker/claim 으로 작업을 수신합니다.',
      deprecated: true,
    },
    { status: 410 }
  );
}

export async function GET(request: NextRequest) {
  return jsonWithCors(
    request,
    {
      error: '이 엔드포인트는 사용이 중단되었습니다 (410 Gone). Colab 워커는 POST /api/worker/claim 으로 작업을 수신합니다.',
      deprecated: true,
    },
    { status: 410 }
  );
}
