import { useState, useEffect, useRef, useCallback } from 'react';
import { Platform } from 'react-native';
import { DeviceMotion, DeviceMotionMeasurement } from 'expo-sensors';

export interface OrientationData {
  roll: number; // 좌우 기울기 각도 (도, -180 ~ 180)
  pitch: number; // 상하 기울기 각도 (도, -90 ~ 90)
  heading: number; // X-Z 지평면 기준 순수 수평 회전각 (도, 0 ~ 360)
  deltaRoll: number; // 기준점 대비 좌우 기울기 차이
  deltaPitch: number; // 기준점 대비 상하 기울기 차이
  deltaHeading: number; // 기준점 대비 X-Z 수평 부채꼴 회전량 (도)
  isLevel: boolean; // 수평 유지 여부
}

/**
 * 오일러 각(alpha, beta, gamma)에서 스마트폰 카메라 시선 벡터의
 * X-Z 지평면(수평면) 투영 단위 벡터(cx, cy)를 추출합니다.
 * 스마트폰이 앞으로 숙여지거나 뒤로 뉘어지는 피치(Y축 기울기)를 완전히 배제하고,
 * 부채꼴 원 테두리(지면과 수평인 회전각)만을 짐벌락 없이 계산합니다.
 */
function extractHorizontalVector(alphaDeg: number, betaDeg: number, gammaDeg: number) {
  const rad = Math.PI / 180;
  const a = alphaDeg * rad;
  const b = betaDeg * rad;
  const g = gammaDeg * rad;

  // 디바이스 후면 카메라 시선 벡터 (-Z축)를 지구 수평 평면으로 투영한 X, Y 성분
  const cx = Math.sin(a) * Math.sin(b) * Math.cos(g) + Math.cos(a) * Math.sin(g);
  const cy = -Math.cos(a) * Math.sin(b) * Math.cos(g) + Math.sin(a) * Math.sin(g);

  // 수평 투영 크기로 정규화하여 기울기 크기 변화로 인한 왜곡 방지
  const mag = Math.hypot(cx, cy);
  if (mag < 0.05) {
    // 스마트폰이 완전히 천장이나 바닥을 향해 수평 성분이 거의 없는 경우 null 반환
    return null;
  }
  return { cx: cx / mag, cy: cy / mag };
}

const SMOOTH_ALPHA = 0.4; // 지수이동평균(EMA) 스무딩 계수

export function useDeviceOrientation(toleranceDegrees = 2.5) {
  // 실제 센서로부터의 원시 각도
  const [rawRoll, setRawRoll] = useState(0);
  const [rawPitch, setRawPitch] = useState(0);
  const [rawHeading, setRawHeading] = useState(0);

  // 지평면 방향 단위 벡터 스무딩 레퍼런스 (0°/360° 경계 튐 방지)
  const smoothCxRef = useRef<number | null>(null);
  const smoothCyRef = useRef<number | null>(null);

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

  // 수평 벡터를 갱신하고 부드러운 수평 각도(도) 반환
  const updateHeadingFromEuler = useCallback(
    (alphaDeg: number, betaDeg: number, gammaDeg: number) => {
      const vec = extractHorizontalVector(alphaDeg, betaDeg, gammaDeg);
      if (!vec) return;

      if (smoothCxRef.current === null || smoothCyRef.current === null) {
        smoothCxRef.current = vec.cx;
        smoothCyRef.current = vec.cy;
      } else {
        smoothCxRef.current = SMOOTH_ALPHA * vec.cx + (1 - SMOOTH_ALPHA) * smoothCxRef.current;
        smoothCyRef.current = SMOOTH_ALPHA * vec.cy + (1 - SMOOTH_ALPHA) * smoothCyRef.current;
      }

      let angleDeg = (Math.atan2(smoothCyRef.current, smoothCxRef.current) * 180) / Math.PI;
      if (angleDeg < 0) angleDeg += 360;
      setRawHeading(Math.round(angleDeg * 10) / 10);
    },
    []
  );

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
      if (event.alpha === null && event.beta === null && event.gamma === null) return;

      setIsSensorActive(true);
      setIsSensorPermissionGranted(true);

      const currentRoll = event.gamma ?? 0;
      const currentPitch = (event.beta ?? 90) - 90;
      const currentAlpha = event.alpha ?? 0;
      const currentBeta = event.beta ?? 90;
      const currentGamma = event.gamma ?? 0;

      setRawRoll(Math.round(currentRoll * 10) / 10);
      setRawPitch(Math.round(currentPitch * 10) / 10);
      updateHeadingFromEuler(currentAlpha, currentBeta, currentGamma);
    };

    orientationHandlerRef.current = handleWebOrientation;
    window.addEventListener('deviceorientation', handleWebOrientation, true);
  }, [updateHeadingFromEuler]);

  // iOS Safari 등 사용자의 명시적 클릭 제스처가 필요한 센서 권한 요청 함수
  const requestSensorPermission = useCallback(async (): Promise<boolean> => {
    if (Platform.OS === 'web' && typeof window !== 'undefined') {
      const DeviceOrientationEventAny = window.DeviceOrientationEvent as any;

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
        startWebSensorListener();
        setIsSensorPermissionGranted(true);
        setNeedsExplicitPermission(false);
        return true;
      }
    }
    return false;
  }, [startWebSensorListener]);

  useEffect(() => {
    if (Platform.OS === 'web' && typeof window !== 'undefined') {
      const DeviceOrientationEventAny = window.DeviceOrientationEvent as any;

      if (!window.DeviceOrientationEvent) {
        setHasSensorSupport(false);
        setIsSimulated(true);
        return;
      }

      if (typeof DeviceOrientationEventAny?.requestPermission === 'function') {
        setNeedsExplicitPermission(true);
        setIsSensorPermissionGranted(false);
      } else {
        startWebSensorListener();
      }
    }

    let motionSubscription: ReturnType<typeof DeviceMotion.addListener> | null = null;
    const setupNativeMotion = async () => {
      try {
        const available = await DeviceMotion.isAvailableAsync();
        if (available) {
          setHasSensorSupport(true);
          setIsSensorPermissionGranted(true);
          setIsSensorActive(true);
          DeviceMotion.setUpdateInterval(30);
          motionSubscription = DeviceMotion.addListener((data: DeviceMotionMeasurement) => {
            if (!data.rotation) return;

            const degRoll = (data.rotation.gamma * 180) / Math.PI;
            const degPitch = (data.rotation.beta * 180) / Math.PI;
            const degAlpha = ((data.rotation.alpha * 180) / Math.PI + 360) % 360;

            setRawRoll(Math.round(degRoll * 10) / 10);
            setRawPitch(Math.round(degPitch * 10) / 10);
            updateHeadingFromEuler(degAlpha, degPitch, degRoll);
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
  }, [startWebSensorListener, updateHeadingFromEuler]);

  // 기준점 대비 상대 각도 계산
  const deltaRoll = Math.round((rawRoll - refRoll) * 10) / 10;
  const deltaPitch = Math.round((rawPitch - refPitch) * 10) / 10;

  // X-Z 지평면 상에서의 순수 회전 각도 차이
  let deltaHeading = (rawHeading - refHeading) % 360;
  if (deltaHeading < -180) deltaHeading += 360;
  if (deltaHeading > 180) deltaHeading -= 360;
  deltaHeading = Math.round(deltaHeading * 10) / 10;

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
