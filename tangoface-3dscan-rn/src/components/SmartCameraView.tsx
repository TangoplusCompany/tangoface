import React, {
  useEffect,
  useRef,
  useState,
  useImperativeHandle,
  forwardRef,
} from 'react';
import { Platform, StyleSheet, View, Text } from 'react-native';
import { CameraType, CameraView } from 'expo-camera';

export interface SmartCameraHandle {
  startRecording: () => boolean;
  stopRecording: () => Promise<Blob | null>;
  isRecordingSupported: () => boolean;
}

interface SmartCameraViewProps {
  facing: CameraType;
  style?: any;
  children?: React.ReactNode;
}

export const SmartCameraView = forwardRef<SmartCameraHandle, SmartCameraViewProps>(
  ({ facing, style, children }, ref) => {
    // 웹(Chrome, Safari 등) 브라우저인 경우 직접 WebRTC Video 엘리먼트로 렌더링
    if (Platform.OS === 'web') {
      return (
        <WebDirectCamera ref={ref} facing={facing} style={style}>
          {children}
        </WebDirectCamera>
      );
    }

    // 모바일 네이티브(Android / iOS)인 경우 Expo CameraView 사용
    return (
      <CameraView facing={facing} style={style}>
        {children}
      </CameraView>
    );
  }
);

// 웹 브라우저 전용 직접 WebRTC 카메라 스트림 컴포넌트
const WebDirectCamera = forwardRef<SmartCameraHandle, SmartCameraViewProps>(
  ({ facing, style, children }, ref) => {
    const videoRef = useRef<HTMLVideoElement | null>(null);
    const [streamError, setStreamError] = useState<string | null>(null);
    const activeStreamRef = useRef<MediaStream | null>(null);
    const mediaRecorderRef = useRef<MediaRecorder | null>(null);
    const recordedChunksRef = useRef<Blob[]>([]);
    const stopPromiseResolveRef = useRef<((blob: Blob | null) => void) | null>(null);

    // 부모 컴포넌트에 녹화 시작 / 중지 제어 노출
    useImperativeHandle(ref, () => ({
      isRecordingSupported: () => {
        return typeof MediaRecorder !== 'undefined' && !!activeStreamRef.current;
      },
      startRecording: () => {
        if (!activeStreamRef.current) {
          console.warn('활성화된 카메라 스트림이 없습니다.');
          return false;
        }

        try {
          recordedChunksRef.current = [];
          
          // 지원하는 MIME 타입 우선순위 탐색
          const mimeTypes = [
            'video/mp4',
            'video/webm;codecs=vp9',
            'video/webm;codecs=vp8',
            'video/webm',
          ];
          const selectedMime = mimeTypes.find((type) =>
            MediaRecorder.isTypeSupported(type)
          );

          const options: MediaRecorderOptions = selectedMime
            ? { mimeType: selectedMime }
            : {};

          const recorder = new MediaRecorder(activeStreamRef.current, options);

          recorder.ondataavailable = (event) => {
            if (event.data && event.data.size > 0) {
              recordedChunksRef.current.push(event.data);
            }
          };

          recorder.onstop = () => {
            const finalMime = options.mimeType || 'video/mp4';
            const blob = new Blob(recordedChunksRef.current, {
              type: finalMime,
            });
            if (stopPromiseResolveRef.current) {
              stopPromiseResolveRef.current(blob);
              stopPromiseResolveRef.current = null;
            }
          };

          mediaRecorderRef.current = recorder;
          recorder.start(1000); // 1초 간격으로 데이터 청크 수집
          return true;
        } catch (e) {
          console.error('MediaRecorder 시작 실패:', e);
          return false;
        }
      },
      stopRecording: () => {
        return new Promise<Blob | null>((resolve) => {
          if (
            !mediaRecorderRef.current ||
            mediaRecorderRef.current.state === 'inactive'
          ) {
            resolve(null);
            return;
          }

          stopPromiseResolveRef.current = resolve;
          mediaRecorderRef.current.stop();
        });
      },
    }));

    useEffect(() => {
      let isCancelled = false;

      const startCamera = async () => {
        setStreamError(null);

        // 기존 스트림 정리
        if (activeStreamRef.current) {
          activeStreamRef.current.getTracks().forEach((track) => track.stop());
          activeStreamRef.current = null;
        }

        if (typeof navigator === 'undefined' || !navigator.mediaDevices?.getUserMedia) {
          setStreamError('이 브라우저에서는 카메라 스트림(getUserMedia)을 지원하지 않습니다.');
          return;
        }

        try {
          const constraints: MediaStreamConstraints = {
            audio: false,
            video: {
              facingMode: facing === 'back' ? { ideal: 'environment' } : 'user',
              width: { ideal: 1280 },
              height: { ideal: 720 },
            },
          };

          const stream = await navigator.mediaDevices.getUserMedia(constraints);

          if (isCancelled) {
            stream.getTracks().forEach((track) => track.stop());
            return;
          }

          activeStreamRef.current = stream;

          if (videoRef.current) {
            videoRef.current.srcObject = stream;
            videoRef.current.setAttribute('playsinline', 'true');
            videoRef.current.setAttribute('webkit-playsinline', 'true');
            videoRef.current.muted = true;
            try {
              await videoRef.current.play();
            } catch {
              // 사용자 제스처 후 재생
            }
          }
        } catch (err: any) {
          if (!isCancelled) {
            console.error('웹 카메라 스트림 시작 오류:', err);
            setStreamError(`카메라 스트림 오류: ${err.message || err.name}`);
          }
        }
      };

      startCamera();

      return () => {
        isCancelled = true;
        if (activeStreamRef.current) {
          activeStreamRef.current.getTracks().forEach((track) => track.stop());
          activeStreamRef.current = null;
        }
      };
    }, [facing]);

    return (
      <View style={[styles.container, style]}>
        {/* 웹 네이티브 비디오 엘리먼트 */}
        {/* @ts-ignore */}
        <video
          ref={videoRef}
          autoPlay
          playsInline
          webkit-playsinline="true"
          muted
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            objectFit: 'cover',
            backgroundColor: '#000',
            transform: facing === 'front' ? 'scaleX(-1)' : 'none',
          }}
        />

        {streamError && (
          <View style={styles.errorOverlay}>
            <Text style={styles.errorText}>{streamError}</Text>
          </View>
        )}

        {/* 가이드라인 및 오버레이 컴포넌트들 */}
        {children}
      </View>
    );
  }
);

const styles = StyleSheet.create({
  container: {
    position: 'relative',
    width: '100%',
    height: '100%',
    overflow: 'hidden',
    backgroundColor: '#000',
  },
  errorOverlay: {
    position: 'absolute',
    top: 20,
    left: 20,
    right: 20,
    backgroundColor: 'rgba(239, 68, 68, 0.85)',
    padding: 12,
    borderRadius: 10,
    zIndex: 99,
  },
  errorText: {
    color: '#ffffff',
    fontSize: 12,
    textAlign: 'center',
  },
});
