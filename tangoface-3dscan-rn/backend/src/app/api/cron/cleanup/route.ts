// src/app/api/cron/cleanup/route.ts
import { NextRequest } from 'next/server';
import { getSupabaseAdmin } from '@/lib/supabaseAdmin';
import { timingSafeMatch } from '@/lib/crypto';
import { VIDEOS_BUCKET } from '@/lib/storage';
import { jsonWithCors, handleOptionsCors } from '@/lib/cors';

export const runtime = 'nodejs';

export async function OPTIONS(request: NextRequest) {
  return handleOptionsCors(request);
}

/**
 * GET /api/cron/cleanup
 * Vercel Cron에 의해 호출되어 만료된 세션 및 24시간 지난 원본 영상을 자동 정리
 * 인증: Authorization: Bearer {CRON_SECRET}
 */
export async function GET(request: NextRequest) {
  try {
    const expectedSecret = process.env.CRON_SECRET;
    if (!expectedSecret) {
      console.error('환경변수 CRON_SECRET이 설정되지 않았습니다.');
      return jsonWithCors(request, { error: '서버 설정 오류입니다.' }, { status: 500 });
    }

    const authHeader = request.headers.get('authorization') || '';
    const providedSecret = authHeader.startsWith('Bearer ')
      ? authHeader.substring(7).trim()
      : '';

    if (!providedSecret || !timingSafeMatch(providedSecret, expectedSecret)) {
      return jsonWithCors(request, { error: '유효하지 않은 Cron 시크릿입니다.' }, { status: 401 });
    }

    const supabase = getSupabaseAdmin();
    const now = new Date();
    const nowIso = now.toISOString();
    const oneDayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    const oneDayAgoIso = oneDayAgo.toISOString();

    let deletedSessions = 0;
    let deletedVideos = 0;

    // 1. 만료된 세션 DB 레코드 삭제 (expires_at <= now)
    const { data: expiredSessions, error: sessionDeleteError } = await supabase
      .from('sessions')
      .delete()
      .lte('expires_at', nowIso)
      .select('id');

    if (sessionDeleteError) {
      console.error('만료 세션 DB 삭제 오류:', sessionDeleteError);
    } else {
      deletedSessions = expiredSessions?.length || 0;
    }

    // 2. 24시간 지난 pair_attempts 시도 로그 정리
    await supabase.from('pair_attempts').delete().lte('attempted_at', oneDayAgoIso);

    // 3. Storage 버킷에서 24시간 지난 원본 영상 삭제
    try {
      const { data: sessionFolders } = await supabase.storage
        .from(VIDEOS_BUCKET)
        .list();

      if (sessionFolders && sessionFolders.length > 0) {
        for (const folder of sessionFolders) {
          const { data: files } = await supabase.storage
            .from(VIDEOS_BUCKET)
            .list(folder.name);

          if (files && files.length > 0) {
            const expiredFiles = files
              .filter((f) => f.created_at && new Date(f.created_at) < oneDayAgo)
              .map((f) => `${folder.name}/${f.name}`);

            if (expiredFiles.length > 0) {
              await supabase.storage.from(VIDEOS_BUCKET).remove(expiredFiles);
              deletedVideos += expiredFiles.length;
            }
          }
        }
      }
    } catch (storageErr) {
      console.error('Storage 만료 영상 정리 오류:', storageErr);
    }

    return jsonWithCors(
      request,
      {
        ok: true,
        timestamp: nowIso,
        deleted_sessions: deletedSessions,
        deleted_videos: deletedVideos,
      },
      { status: 200 }
    );
  } catch (err: any) {
    console.error('Cron 정리 작업 예외:', err);
    return jsonWithCors(request, { error: '서버 내부 오류가 발생했습니다.' }, { status: 500 });
  }
}
