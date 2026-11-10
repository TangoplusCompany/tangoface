import React, { useState, useMemo } from 'react';
import {
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  Platform,
} from 'react-native';
import { OrientationData } from '../hooks/useDeviceOrientation';

interface PanoramaGuideProps {
  orientation: OrientationData;
  isRecording: boolean;
  onCalibrate: () => void;
  onResetZero: () => void;
  isSimulated: boolean;
  onToggleSimulated: () => void;
  onSimulateChange: (roll: number, pitch: number, heading: number) => void;
  needsSensorPermission?: boolean;
  isSensorPermissionGranted?: boolean;
  onRequestSensorPermission?: () => void;
  isSensorActive?: boolean;
}

/**
 * 부채꼴 원테두리 스캔 목표 회전 범위 (도)
 * - 얼굴을 구(sphere)라고 할 때 좌측(-60°)에서 우측(+60°)까지 지평면(X-Z) 상에서 부채꼴 원호를 따라 이동
 * - 총 120° 원호 회전 기준, 100° 도달 시 100% 매핑
 */
const TARGET_SWEEP_DEGREES = 100.0;
const TRACK_WIDTH = 280; // 트랙 전체 너비 (px)
const POINTER_SIZE = 24; // 인디케이터 크기
const MAX_TRAVEL_X = TRACK_WIDTH - POINTER_SIZE - 12; // 이동 가능 최대 X (px)

export const PanoramaGuide: React.FC<PanoramaGuideProps> = ({
  orientation,
  isRecording,
  onCalibrate,
  onResetZero,
  isSimulated,
  onToggleSimulated,
  onSimulateChange,
  needsSensorPermission = false,
  isSensorPermissionGranted = false,
  onRequestSensorPermission,
  isSensorActive = false,
}) => {
  const { deltaHeading } = orientation;

  // 1. 부채꼴 원테두리 진행도(Progress) 계산: 0 ~ 1.0 (0% ~ 100%)
  // 상하 기울기(Y축) 배제, X-Z 지평면 상에서의 순수 회전각(deltaHeading)을 기준으로 계산
  const progressRatio = useMemo(() => {
    if (!isRecording) return 0;
    const sweep = Math.abs(deltaHeading);
    return Math.min(1.0, Math.max(0, sweep / TARGET_SWEEP_DEGREES));
  }, [deltaHeading, isRecording]);

  const progressPercent = Math.round(progressRatio * 100);

  // 2. 상단 인디케이터 X 좌표 (0px ~ MAX_TRAVEL_X px)
  const trackIndicatorX = progressRatio * MAX_TRAVEL_X;

  // 3. 트랙 내부 채워지는 프로그레스 바 너비 (0px ~ TRACK_WIDTH px)
  const fillWidth = progressRatio * (TRACK_WIDTH - 6);

  // 시뮬레이터 슬라이더 제어용 로컬 상태 (PC 브라우저 테스트)
  const [simRoll, setSimRoll] = useState(0);
  const [simPitch, setSimPitch] = useState(0);
  const [simHeading, setSimHeading] = useState(0);

  const updateSim = (r: number, p: number, h: number) => {
    setSimRoll(r);
    setSimPitch(p);
    setSimHeading(h);
    onSimulateChange(r, p, h);
  };

  return (
    <View style={styles.container} pointerEvents="box-none">
      {/* ========================================================= */}
      {/* 상단 파노라마 트랙: X-Z 지평면 부채꼴 궤적 게이지 */}
      {/* ========================================================= */}
      <View style={styles.topTrackSection}>
        <View style={styles.trackContainer}>
          {/* 채워지는 프로그레스 게이지 바 (좌측 -> 우측) */}
          <View
            style={[
              styles.trackFillBar,
              {
                width: fillWidth,
                backgroundColor: progressPercent >= 100 ? '#00FFCC' : '#FF0055',
              },
            ]}
          />

          {/* 중앙 수평 기준선 */}
          <View style={styles.trackCenterLine} />

          {/* 패닝 진행 방향 가이드 화살표 */}
          <View style={styles.trackArrows}>
            <Text style={styles.arrowText}>▶</Text>
            <Text style={styles.arrowText}>▶</Text>
            <Text style={styles.arrowText}>▶</Text>
            <Text style={styles.arrowText}>▶</Text>
            <Text style={styles.arrowText}>▶</Text>
          </View>

          {/* 움직이는 인디케이터 포인터 (0% -> 100% 도달) */}
          <View
            style={[
              styles.trackPointer,
              {
                left: 6 + trackIndicatorX,
                borderColor: progressPercent >= 100 ? '#00FFCC' : '#FF0055',
                backgroundColor: progressPercent >= 100 ? '#00FFCC' : '#FF0055',
              },
            ]}
          >
            <View style={styles.pointerInner} />
          </View>
        </View>

        {/* 진행 상태 안내 문구 및 퍼센트 */}
        <View style={styles.statusRow}>
          <Text style={styles.trackSubText}>
            {!isRecording
              ? '얼굴 좌측(-60°)을 바라본 상태에서 촬영을 시작하세요'
              : progressPercent >= 100
              ? '100% 스캔 완료! 촬영 종료 버튼을 눌러주세요'
              : `진행률 ${progressPercent}% | 우측(+60°)으로 천천히 이동하세요`}
          </Text>
        </View>
      </View>

      {/* ========================================================= */}
      {/* PC 웹 브라우저용 시뮬레이션 제어 바 (센서 없는 환경 테스트) */}
      {/* ========================================================= */}
      {Platform.OS === 'web' && isSimulated && (
        <View style={styles.simWrapper}>
          <View style={styles.simCard}>
            <Text style={styles.simCardTitle}>가상 회전 조절 (PC 테스트용)</Text>
            <View style={styles.simBtnRow}>
              <TouchableOpacity
                style={styles.simTinyBtn}
                onPress={() => updateSim(0, 0, 0)}
              >
                <Text style={styles.simTinyBtnText}>시작점 (0%)</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={styles.simTinyBtn}
                onPress={() => updateSim(0, 0, TARGET_SWEEP_DEGREES * 0.5)}
              >
                <Text style={styles.simTinyBtnText}>중간 (50%)</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.simTinyBtn, styles.simActiveBtn]}
                onPress={() => updateSim(0, 0, TARGET_SWEEP_DEGREES)}
              >
                <Text style={styles.simTinyBtnText}>완료 (100%)</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    paddingHorizontal: 16,
    paddingTop: 16,
    zIndex: 10,
  },
  topTrackSection: {
    alignItems: 'center',
  },
  trackContainer: {
    width: TRACK_WIDTH,
    height: 48,
    backgroundColor: 'rgba(28, 25, 26, 0.88)',
    borderRadius: 24,
    justifyContent: 'center',
    position: 'relative',
    overflow: 'hidden',
  },
  trackFillBar: {
    position: 'absolute',
    left: 3,
    top: 3,
    bottom: 3,
    borderRadius: 20,
    opacity: 0.45,
  },
  trackCenterLine: {
    position: 'absolute',
    left: 12,
    right: 12,
    height: 2,
    backgroundColor: 'rgba(255, 255, 255, 0.25)',
    borderRadius: 1,
  },
  trackArrows: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingHorizontal: 24,
    opacity: 0.3,
  },
  arrowText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '700',
  },
  trackPointer: {
    position: 'absolute',
    width: POINTER_SIZE,
    height: POINTER_SIZE,
    borderRadius: POINTER_SIZE / 2,
    borderWidth: 2.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pointerInner: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#ffffff',
  },
  statusRow: {
    marginTop: 8,
    alignItems: 'center',
  },
  trackSubText: {
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '600',
    textAlign: 'center',
    backgroundColor: 'rgba(22, 18, 20, 0.85)',
    paddingVertical: 6,
    paddingHorizontal: 14,
    borderRadius: 16,
    overflow: 'hidden',
  },
  simWrapper: {
    position: 'absolute',
    bottom: 24,
    alignSelf: 'center',
  },
  simCard: {
    backgroundColor: 'rgba(28, 25, 26, 0.95)',
    padding: 12,
    borderRadius: 12,
  },
  simCardTitle: {
    color: '#9E9C9C',
    fontSize: 12,
    fontWeight: '600',
    marginBottom: 8,
    textAlign: 'center',
  },
  simBtnRow: {
    flexDirection: 'row',
    gap: 8,
  },
  simTinyBtn: {
    backgroundColor: '#30292B',
    paddingVertical: 6,
    paddingHorizontal: 10,
    borderRadius: 6,
  },
  simActiveBtn: {
    backgroundColor: '#FF0055',
  },
  simTinyBtnText: {
    color: '#ffffff',
    fontSize: 12,
    fontWeight: '600',
  },
});
