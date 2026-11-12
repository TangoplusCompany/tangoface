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
      // 일부 모바일 코덱(예: HEVC .mov)은 브라우저 <video> 태그 미리보기가 안 될 수 있으나 업로드는 정상 가능하므로 0초로 폴백
      resolve(0);
    };

    video.src = objectUrl;
    video.load();
  });
}

const VIDEO_EXT_REGEX = /\.(mp4|mov|m4v|webm|mkv|avi|3gp|3gpp)$/i;

/**
 * 웹 <input type="file"> 로 선택된 File 객체를 검증(형식/용량/길이)하고 SelectedVideoInfo 생성
 */
export async function inspectWebVideoFile(file: File): Promise<SelectedVideoInfo | null> {
  // 동영상 MIME 타입 또는 확장자 이중 체크
  const isVideoMime = !file.type || file.type.startsWith('video/');
  const isVideoExt = VIDEO_EXT_REGEX.test(file.name);
  if (!isVideoMime && !isVideoExt) {
    alert('동영상 파일(.mp4, .mov, .webm 등)만 선택할 수 있습니다.');
    return null;
  }

  const sizeMB = Math.round((file.size / (1024 * 1024)) * 10) / 10;
  if (sizeMB > MAX_SIZE_MB) {
    alert(`파일 용량이 초과되었습니다 (${sizeMB}MB).\n최대 ${MAX_SIZE_MB}MB 이내의 영상만 지원됩니다.`);
    return null;
  }

  try {
    const duration = await getWebVideoDuration(file);
    const durationSec = Math.round(duration * 10) / 10;

    if (durationSec > MAX_DURATION_SEC) {
      alert(
        `영상 길이가 ${MAX_DURATION_SEC}초를 초과했습니다.\n(선택된 영상: ${durationSec}초)\n${MAX_DURATION_SEC}초 이내의 영상 파일만 첨부해주세요.`
      );
      return null;
    }

    const uri = URL.createObjectURL(file);
    return {
      uri,
      name: file.name || 'video.mp4',
      sizeMB,
      durationSec,
      file,
    };
  } catch {
    alert('비디오 파일을 읽는 도중 오류가 발생했습니다.');
    return null;
  }
}

/**
 * 브라우저(Chrome, Safari, Firefox) 및 모바일 네이티브에서 동영상 파일만 선택하고 30초 제한 검증
 */
export async function pickVideoWithDurationLimit(): Promise<SelectedVideoInfo | null> {
  // 1. 웹(Web) 환경 처리
  if (Platform.OS === 'web' && typeof document !== 'undefined') {
    return new Promise((resolve) => {
      const input = document.createElement('input');
      input.type = 'file';
      input.accept = 'video/mp4,video/quicktime,video/webm,video/*';
      input.style.position = 'fixed';
      input.style.left = '-9999px';
      input.style.opacity = '0';
      document.body.appendChild(input);

      let settled = false;

      const finish = (result: SelectedVideoInfo | null) => {
        if (settled) return;
        settled = true;
        input.onchange = null;
        input.oncancel = null;
        input.remove();
        resolve(result);
      };

      input.oncancel = () => {
        finish(null);
      };

      input.onchange = async (e: Event) => {
        const target = e.target as HTMLInputElement;
        const file = target.files?.[0];
        if (!file) {
          finish(null);
          return;
        }
        const info = await inspectWebVideoFile(file);
        finish(info);
      };

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
