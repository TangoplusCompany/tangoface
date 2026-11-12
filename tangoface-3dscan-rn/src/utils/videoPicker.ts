import { Platform } from 'react-native';
import * as ImagePicker from 'expo-image-picker';

export interface SelectedVideoInfo {
  uri: string;
  name: string;
  sizeMB: number;
  durationSec: number;
  file?: File;
}

const MAX_DURATION_SEC = 30;
const MAX_SIZE_MB = 50;

/**
 * 웹 브라우저에서 Video 엘리먼트를 통해 실제 비디오 duration(초) 측정
 */
function getWebVideoDuration(file: File): Promise<number> {
  return new Promise((resolve, reject) => {
    const video = document.createElement('video');
    video.preload = 'metadata';
    video.muted = true;
    video.playsInline = true;
    const objectUrl = URL.createObjectURL(file);
    let done = false;

    const cleanup = () => {
      clearTimeout(timeoutId);
      video.onloadedmetadata = null;
      video.onerror = null;
      URL.revokeObjectURL(objectUrl);
    };

    const timeoutId = setTimeout(() => {
      if (done) return;
      done = true;
      cleanup();
      resolve(0);
    }, 6000);

    video.onloadedmetadata = () => {
      if (done) return;
      done = true;
      const duration = Number.isFinite(video.duration) ? video.duration : 0;
      cleanup();
      resolve(duration);
    };

    video.onerror = () => {
      if (done) return;
      done = true;
      cleanup();
      reject(new Error('비디오 메타데이터를 불러올 수 없습니다.'));
    };

    video.src = objectUrl;
    video.load();
  });
}

/**
 * 브라우저(Chrome, Safari, Firefox) 및 모바일 네이티브에서 동영상 파일만 선택하고 30초 제한 검증
 */
export async function pickVideoWithDurationLimit(): Promise<SelectedVideoInfo | null> {
  // 1. 웹(Web) 환경 처리 (모든 주요 브라우저에서 확실한 video/* 필터링 및 duration 측정)
  if (Platform.OS === 'web' && typeof document !== 'undefined') {
    return new Promise((resolve) => {
      const input = document.createElement('input');
      input.type = 'file';
      // Chrome, Safari, Firefox 등 모든 브라우저에서 동영상만 선택되도록 mime types 명시
      input.accept = 'video/mp4,video/quicktime,video/webm,video/*';
      input.style.display = 'none';
      document.body.appendChild(input);

      let settled = false;
      let filePicked = false;
      let focusTimer: ReturnType<typeof setTimeout> | null = null;

      const finish = (result: SelectedVideoInfo | null) => {
        if (settled) return;
        settled = true;
        if (focusTimer) {
          clearTimeout(focusTimer);
          focusTimer = null;
        }
        window.removeEventListener('focus', onWindowFocus);
        input.onchange = null;
        input.oncancel = null;
        input.remove();
        resolve(result);
      };

      const onWindowFocus = () => {
        // iOS Safari 구버전 등에서 oncancel이 발생하지 않을 때 피커 닫힘 감지 fallback
        if (focusTimer) clearTimeout(focusTimer);
        focusTimer = setTimeout(() => {
          if (!filePicked && !settled) {
            finish(null);
          }
        }, 500);
      };

      input.oncancel = () => {
        finish(null);
      };

      input.onchange = async (e: Event) => {
        filePicked = true;
        if (focusTimer) {
          clearTimeout(focusTimer);
          focusTimer = null;
        }
        const target = e.target as HTMLInputElement;
        const file = target.files?.[0];

        if (!file) {
          finish(null);
          return;
        }

        // 동영상 MIME 타입 이중 체크 (일부 브라우저는 확장자만 있고 type이 비어 있을 수 있음)
        if (file.type && !file.type.startsWith('video/')) {
          alert('동영상 파일(.mp4, .mov, .webm 등)만 선택할 수 있습니다.');
          finish(null);
          return;
        }

        const sizeMB = Math.round((file.size / (1024 * 1024)) * 10) / 10;
        if (sizeMB > MAX_SIZE_MB) {
          alert(`파일 용량이 초과되었습니다 (${sizeMB}MB).\n최대 ${MAX_SIZE_MB}MB 이내의 영상만 지원됩니다.`);
          finish(null);
          return;
        }

        try {
          const duration = await getWebVideoDuration(file);
          const durationSec = Math.round(duration * 10) / 10;

          if (durationSec > MAX_DURATION_SEC) {
            alert(
              `영상 길이가 30초를 초과했습니다.\n(선택된 영상: ${durationSec}초)\n30초 이내의 영상 파일만 첨부해주세요.`
            );
            finish(null);
            return;
          }

          const uri = URL.createObjectURL(file);
          finish({
            uri,
            name: file.name,
            sizeMB,
            durationSec,
            file,
          });
        } catch (err) {
          alert('비디오 파일을 읽는 도중 오류가 발생했습니다.');
          finish(null);
        }
      };

      window.addEventListener('focus', onWindowFocus);
      input.click();
    });
  }

  // 2. 모바일 네이티브 (Android / iOS) 환경 처리
  try {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['videos'],
      videoMaxDuration: MAX_DURATION_SEC, // 30초 제한
      quality: 1,
    });

    if (result.canceled || !result.assets || result.assets.length === 0) {
      return null;
    }

    const asset = result.assets[0];
    const durationSec = asset.duration ? Math.round((asset.duration > 1000 ? asset.duration / 1000 : asset.duration) * 10) / 10 : 0;

    if (durationSec > MAX_DURATION_SEC) {
      alert(`영상 길이가 30초를 초과했습니다. (${durationSec}초)`);
      return null;
    }

    const sizeMB = asset.fileSize ? Math.round((asset.fileSize / (1024 * 1024)) * 10) / 10 : 0;

    return {
      uri: asset.uri,
      name: asset.fileName || 'video.mp4',
      sizeMB,
      durationSec,
    };
  } catch (error) {
    console.error('Video pick error:', error);
    alert('비디오 선택 중 오류가 발생했습니다.');
    return null;
  }
}
