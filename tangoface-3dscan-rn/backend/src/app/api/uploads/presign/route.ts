// src/app/api/uploads/presign/route.ts
import { NextRequest } from 'next/server';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import { hashToken, timingSafeMatch, generateSecureToken } from '@/lib/crypto';
import {
  createVideoUploadSignedUrl,
  MAX_VIDEO_SIZE_BYTES,
  ALLOWED_VIDEO_MIME_TYPES,
} from '@/lib/storage';
import { jsonWithCors, handleOptionsCors } from '@/lib/cors';
import { PresignUploadResponse } from '@/types/api';

export const runtime = 'nodejs';

export async function OPTIONS(request: NextRequest) {
  return handleOptionsCors(request);
}

/**
 * POST /api/uploads/presign
 * 폰에서 영상 직접 업로드용 Signed Upload URL 요청
 * 인증: Authorization: Bearer {pair_token}
 */
export async function POST(request: NextRequest) {
  try {
    const authHeader = request.headers.get('authorization') || '';
    if (!authHeader.startsWith('Bearer ')) {
      return jsonWithCors(
        request,
        { error: '인증 헤더가 올바르지 않습니다. (Authorization: Bearer <pair_token>)' },
        { status: 401 }
      );
    }

    const pairToken = authHeader.substring(7).trim();
    if (!pairToken) {
      return jsonWithCors(request, { error: 'pair_token이 제공되지 않았습니다.' }, { status: 401 });
    }

    const tokenHash = hashToken(pairToken);
    const supabase = getSupabaseAdmin();

    const { data: session, error: findError } = await supabase
      .from('sessions')
      .select('id, status, pair_token_hash')
      .eq('pair_token_hash', tokenHash)
      .maybeSingle();

    if (findError || !session || !timingSafeMatch(session.pair_token_hash || '', tokenHash)) {
      return jsonWithCors(request, { error: '유효하지 않은 pair_token입니다.' }, { status: 401 });
    }

    if (session.status !== 'PAIRED') {
      return jsonWithCors(
        request,
        {
          error: `업로드 URL을 발급할 수 없는 상태입니다. (현재: ${session.status}, 필요: PAIRED)`,
        },
        { status: 400 }
      );
    }

    const randomFileId = generateSecureToken(16);
    const { uploadPath, signedUrl, token } = await createVideoUploadSignedUrl(
      supabase,
      session.id,
      randomFileId
    );

    // 서버가 결정한 upload_path를 세션에 기록
    const { error: updateError } = await supabase
      .from('sessions')
      .update({ upload_path: uploadPath })
      .eq('id', session.id);

    if (updateError) {
      console.error('upload_path 저장 오류:', updateError);
      return jsonWithCors(request, { error: '업로드 경로 저장에 실패했습니다.' }, { status: 500 });
    }

    const response: PresignUploadResponse = {
      upload_path: uploadPath,
      signed_url: signedUrl,
      token,
      max_size_bytes: MAX_VIDEO_SIZE_BYTES,
      allowed_mime_types: ALLOWED_VIDEO_MIME_TYPES,
    };

    return jsonWithCors(request, response, { status: 200 });
  } catch (err: any) {
    console.error('Presign URL 생성 예외:', err);
    return jsonWithCors(request, { error: '서버 내부 오류가 발생했습니다.' }, { status: 500 });
  }
}
