import { useState, useEffect, useRef, useCallback } from 'react';
import { Platform } from 'react-native';
import { DeviceMotion, DeviceMotionMeasurement } from 'expo-sensors';

export interface OrientationData {
  roll: number; // 좌우 기울기 각도 (도, -180 ~ 180)
  pitch: number; // 상하 기울기 각도 (도, -90 ~ 90)
  heading: number; // 나침반/패닝 각도 (도, 0 ~ 360)
  deltaRoll: number; // 기준점 대비 좌우 기울기 차이
  deltaPitch: number; // 기준점 대비 상하 기울기 차이
  deltaHeading: number; // 기준점 대비 패닝 회전량
  isLevel: boolean; // 수평 유지 여부 (오차 허용 범위 내)
}

export function useDeviceOrientation(toleranceDegrees = 2.5) {
  // 실제 센서로부터의 원시 각도
  const [rawRoll, setRawRoll] = useState(0);
  const [rawPitch, setRawPitch] = useState(0);
  const [rawHeading, setRawHeading] = useState(0);

  // 기준점 (촬영 시작 또는 캘리브레이션 시점의 각도)
  const [refRoll, setRefRoll] = useState(0);
  const [refPitch, setRefPitch] = useState(0);
  const [refHeading, setRefHeading] = useState(0);

  // 센서 하드웨어 지원 여부 및 권한 상태
  const [hasSensorSupport, setHasSensorSupport] = useState<boolean>(true);
  const [isSensorPermissionGranted, setIsSensorPermissionGranted] = useState<boolean>(false);
  const [needsExplicitPermission, setNeedsExplicitPermission] = useState<boolean>(false);
  const [isSensorActive, setIsSensorActive] = useState<boolean>(false);

  // PC 웹 브라우저용 시뮬레이션 모드
  const [isSimulated, setIsSimulated] = useState<boolean>(false);

  // 현재 각도를 기준점으로 설정 (현재 위치 기준 영점 캘리브레이션)
  const calibrateToCurrentPosition = useCallback(() => {
    setRefRoll(rawRoll);
    setRefPitch(rawPitch);
    setRefHeading(rawHeading);
  }, [rawRoll, rawPitch, rawHeading]);

  // 절대 수평(센서 0도)으로 기준 리셋
  const resetToAbsoluteZero = useCallback(() => {
    setRefRoll(0);
    setRefPitch(0);
    setRefHeading(0);
  }, []);

  // 수동 시뮬레이션 각도 조절 (PC 웹 테스트용)
  const setSimulatedValues = useCallback(
    (roll: number, pitch: number, heading: number) => {
      setRawRoll(roll);
      setRawPitch(pitch);
      setRawHeading(heading);
    },
    []
  );

  const orientationHandlerRef = useRef<((event: DeviceOrientationEvent) => void) | null>(null);

  // 웹 센서 이벤트 리스너 등록
  const startWebSensorListener = useCallback(() => {
    if (typeof window === 'undefined') return;

    const handleWebOrientation = (event: DeviceOrientationEvent) => {
      if (event.gamma !== null || event.beta !== null) {
        setIsSensorActive(true);
        setIsSensorPermissionGranted(true);

        const currentRoll = event.gamma ?? 0;
        const currentPitch = (event.beta ?? 0) - 90; // 직립 기준 0도
        const currentHeading = event.alpha ?? 0;

        setRawRoll(Math.round(currentRoll * 10) / 10);
        setRawPitch(Math.round(currentPitch * 10) / 10);
        setRawHeading(Math.round(currentHeading * 10) / 10);
      }
    };

    orientationHandlerRef.current = handleWebOrientation;
    window.addEventListener('deviceorientation', handleWebOrientation, true);
  }, []);

  // iOS Safari 등 사용자의 명시적 클릭 제스처가 필요한 센서 권한 요청 함수
  const requestSensorPermission = useCallback(async (): Promise<boolean> => {
    if (Platform.OS === 'web' && typeof window !== 'undefined') {
      const DeviceOrientationEventAny = window.DeviceOrientationEvent as any;

      // iOS 13+ Safari: requestPermission 함수가 존재하는 경우
      if (typeof DeviceOrientationEventAny?.requestPermission === 'function') {
        try {
          const response = await DeviceOrientationEventAny.requestPermission();
          if (response === 'granted') {
            setIsSensorPermissionGranted(true);
            setNeedsExplicitPermission(false);
            startWebSensorListener();
            return true;
          } else {
            alert('자이로/동작 센서 권한이 거부되었습니다.');
            return false;
          }
        } catch (err: any) {
          console.error('자이로 센서 권한 요청 오류:', err);
          alert(`센서 권한 오류: ${err.message}`);
          return false;
        }
      } else {
        // 안드로이드 크롬 또는 일반 브라우저
        startWebSensorListener();
        setIsSensorPermissionGranted(true);
        setNeedsExplicitPermission(false);
        return true;
      }
    }
    return false;
  }, [startWebSensorListener]);

  useEffect(() => {
    // 1. 웹 브라우저 환경 초기 검사
    if (Platform.OS === 'web' && typeof window !== 'undefined') {
      const DeviceOrientationEventAny = window.DeviceOrientationEvent as any;

      if (!window.DeviceOrientationEvent) {
        setHasSensorSupport(false);
        setIsSimulated(true);
        return;
      }

      // iOS Safari처럼 명시적 클릭 권한 요청이 필요한지 확인
      if (typeof DeviceOrientationEventAny?.requestPermission === 'function') {
        setNeedsExplicitPermission(true);
        setIsSensorPermissionGranted(false);
      } else {
        // 안드로이드 크롬 등은 자동 등록 시도
        startWebSensorListener();
      }
    }

    // 2. 모바일 네이티브 / Expo DeviceMotion 센서 구독
    let motionSubscription: ReturnType<typeof DeviceMotion.addListener> | null = null;
    const setupNativeMotion = async () => {
      try {
        const available = await DeviceMotion.isAvailableAsync();
        if (available) {
          setHasSensorSupport(true);
          setIsSensorPermissionGranted(true);
          setIsSensorActive(true);
          DeviceMotion.setUpdateInterval(50);
          motionSubscription = DeviceMotion.addListener((data: DeviceMotionMeasurement) => {
            if (!data.rotation) return;
            const degRoll = (data.rotation.gamma * 180) / Math.PI;
            const degPitch = (data.rotation.beta * 180) / Math.PI;
            const degHeading = ((data.rotation.alpha * 180) / Math.PI + 360) % 360;

            setRawRoll(Math.round(degRoll * 10) / 10);
            setRawPitch(Math.round(degPitch * 10) / 10);
            setRawHeading(Math.round(degHeading * 10) / 10);
          });
        }
      } catch {
        // 네이티브 센서 예외
      }
    };

    if (Platform.OS !== 'web') {
      setupNativeMotion();
    }

    return () => {
      if (motionSubscription) {
        motionSubscription.remove();
      }
      if (Platform.OS === 'web' && typeof window !== 'undefined' && orientationHandlerRef.current) {
        window.removeEventListener('deviceorientation', orientationHandlerRef.current);
      }
    };
  }, [startWebSensorListener]);

  // 기준점 대비 상대 각도 계산
  const deltaRoll = Math.round((rawRoll - refRoll) * 10) / 10;
  const deltaPitch = Math.round((rawPitch - refPitch) * 10) / 10;

  let deltaHeading = (rawHeading - refHeading) % 360;
  if (deltaHeading < -180) deltaHeading += 360;
  if (deltaHeading > 180) deltaHeading -= 360;
  deltaHeading = Math.round(deltaHeading * 10) / 10;

  // 수평 판정 (기준 위치 대비 롤 및 피치 오차가 toleranceDegrees 이내)
  const isLevel =
    Math.abs(deltaRoll) <= toleranceDegrees && Math.abs(deltaPitch) <= toleranceDegrees;

  return {
    orientation: {
      roll: rawRoll,
      pitch: rawPitch,
      heading: rawHeading,
      deltaRoll,
      deltaPitch,
      deltaHeading,
      isLevel,
    },
    hasSensorSupport,
    needsExplicitPermission,
    isSensorPermissionGranted,
    isSensorActive,
    requestSensorPermission,
    isSimulated,
    setIsSimulated,
    calibrateToCurrentPosition,
    resetToAbsoluteZero,
    setSimulatedValues,
  };
}
