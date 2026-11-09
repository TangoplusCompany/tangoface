import { useState, useEffect, useCallback } from 'react';
import { Platform } from 'react-native';
import { useCameraPermissions } from 'expo-camera';

export interface CameraStatus {
  hasPermission: boolean;
  isLoading: boolean;
  errorMessage: string | null;
  isSecureContext: boolean;
  requestPermission: () => Promise<boolean>;
}

export function useCameraManager(): CameraStatus {
  const [nativePermission, requestNativePermission] = useCameraPermissions();
  const [webPermissionGranted, setWebPermissionGranted] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const isWeb = Platform.OS === 'web';
  const isSecureContext =
    !isWeb ||
    (typeof window !== 'undefined' &&
      (window.isSecureContext ||
        window.location.hostname === 'localhost' ||
        window.location.hostname === '127.0.0.1'));

  // 초기 권한 상태 점검
  useEffect(() => {
    let isMounted = true;

    const checkInitialPermission = async () => {
      setIsLoading(true);
      setErrorMessage(null);

      if (isWeb && typeof navigator !== 'undefined') {
        // 크롬/브라우저 보안 컨텍스트 체크
        if (!isSecureContext) {
          setErrorMessage(
            '⚠️ 크롬 보안 정책: 카메라는 HTTPS 또는 http://localhost 주소에서만 동작합니다. 현재 주소가 IP(192.168.x.x)라면 주소창을 http://localhost:8081 로 변경해 접속해주세요.'
          );
          setIsLoading(false);
          return;
        }

        // 브라우저 Permissions API 조회
        if (navigator.permissions?.query) {
          try {
            const status = await navigator.permissions.query({ name: 'camera' as PermissionName });
            if (isMounted) {
              if (status.state === 'granted') {
                setWebPermissionGranted(true);
              } else if (status.state === 'denied') {
                setErrorMessage(
                  '🚫 브라우저에서 카메라 권한이 차단되었습니다. 주소창 좌측(설정 아이콘) 또는 우측 상단 🚫 아이콘을 눌러 카메라를 [허용]으로 변경 후 새로고침해주세요.'
                );
              }
            }
          } catch {
            // Firefox 등 일부 브라우저는 query('camera')를 지원하지 않음
          }
        }
      }

      if (isMounted) {
        setIsLoading(false);
      }
    };

    checkInitialPermission();

    return () => {
      isMounted = false;
    };
  }, [isWeb, isSecureContext]);

  // 권한 요청 함수
  const requestPermission = useCallback(async (): Promise<boolean> => {
    setIsLoading(true);
    setErrorMessage(null);

    // 1. 웹 브라우저 환경 (직접 getUserMedia 호출로 브라우저 팝업 트리거)
    if (isWeb && typeof navigator !== 'undefined') {
      if (!isSecureContext) {
        alert(
          '⚠️ 크롬 보안 정책 안내\n\n카메라 접근은 HTTPS 또는 http://localhost 환경에서만 허용됩니다.\n\n주소창에 192.168.x.x 대신 http://localhost:8081 로 접속해주세요!'
        );
        setIsLoading(false);
        return false;
      }

      if (!navigator.mediaDevices?.getUserMedia) {
        setErrorMessage('이 브라우저에서는 카메라 API(getUserMedia)를 지원하지 않거나 보안 컨텍스트가 아닙니다.');
        setIsLoading(false);
        return false;
      }

      try {
        // 크롬 브라우저의 권한 팝업을 직접 호출
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { width: { ideal: 1280 }, height: { ideal: 720 } },
        });

        // 팝업 허용 성공 시 스트림을 닫아두고 권한 부여 상태로 전환
        stream.getTracks().forEach((track) => track.stop());
        setWebPermissionGranted(true);
        setIsLoading(false);
        return true;
      } catch (err: any) {
        setIsLoading(false);
        if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
          const msg =
            '🚫 카메라 접근이 거부되었습니다.\n\n크롬 주소창 좌측(설정 아이콘)을 클릭해 카메라를 [허용]으로 바꾼 뒤 새로고침해주세요.';
          setErrorMessage(msg);
          alert(msg);
        } else if (err.name === 'NotFoundError' || err.name === 'DevicesNotFoundError') {
          const msg = '연결된 카메라(웹캠)를 찾을 수 없습니다. 장치를 확인해주세요.';
          setErrorMessage(msg);
          alert(msg);
        } else {
          const msg = `카메라 권한 오류: ${err.message || err.name}`;
          setErrorMessage(msg);
          alert(msg);
        }
        return false;
      }
    }

    // 2. 모바일 네이티브 환경 (Expo Go 또는 네이티브 앱)
    try {
      const res = await requestNativePermission();
      setIsLoading(false);
      return res.granted;
    } catch (err: any) {
      setIsLoading(false);
      setErrorMessage(`네이티브 권한 오류: ${err.message}`);
      return false;
    }
  }, [isWeb, isSecureContext, requestNativePermission]);

  const hasPermission = isWeb
    ? webPermissionGranted || !!nativePermission?.granted
    : !!nativePermission?.granted;

  return {
    hasPermission,
    isLoading,
    errorMessage,
    isSecureContext,
    requestPermission,
  };
}
