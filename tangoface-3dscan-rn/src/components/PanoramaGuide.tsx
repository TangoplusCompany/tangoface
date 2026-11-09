import React, { useState } from 'react';
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
  const { deltaRoll, deltaPitch, deltaHeading, isLevel } = orientation;

  // 가이드 텍스트 및 상태 계산
  const getGuideFeedback = () => {
    if (isLevel) {
      return {
        text: '수평 유지됨 (완벽한 상태)',
        color: '#22c55e', // green
        bgColor: 'rgba(34, 197, 94, 0.25)',
        borderColor: '#22c55e',
      };
    }

    if (Math.abs(deltaRoll) > 3) {
      if (deltaRoll > 0) {
        return {
          text: `왼쪽으로 기울이세요 (${Math.abs(deltaRoll).toFixed(1)}°)`,
          color: '#f59e0b',
          bgColor: 'rgba(245, 158, 11, 0.25)',
          borderColor: '#f59e0b',
        };
      } else {
        return {
          text: `오른쪽으로 기울이세요 (${Math.abs(deltaRoll).toFixed(1)}°)`,
          color: '#f59e0b',
          bgColor: 'rgba(245, 158, 11, 0.25)',
          borderColor: '#f59e0b',
        };
      }
    }

    if (deltaPitch > 3) {
      return {
        text: `카메라를 아래로 내리세요 (${Math.abs(deltaPitch).toFixed(1)}°)`,
        color: '#f43f5e',
        bgColor: 'rgba(244, 63, 94, 0.25)',
        borderColor: '#f43f5e',
      };
    } else {
      return {
        text: `카메라를 위로 들어올리세요 (${Math.abs(deltaPitch).toFixed(1)}°)`,
        color: '#f43f5e',
        bgColor: 'rgba(244, 63, 94, 0.25)',
        borderColor: '#f43f5e',
      };
    }
  };

  const feedback = getGuideFeedback();

  // 상단 파노라마 트랙 인디케이터 좌표 계산
  // deltaPitch에 따른 Y축 오차 (-25px ~ +25px)
  const trackIndicatorY = Math.max(-24, Math.min(24, -deltaPitch * 2.5));

  // deltaHeading에 따른 X축 진행도 (0% ~ 100%, 0도 ~ 90도 회전을 트랙 전체 폭 260px에 매핑)
  const clampedHeading = Math.max(0, Math.min(90, Math.abs(deltaHeading)));
  const trackIndicatorX = (clampedHeading / 90) * 230; // 0 ~ 230px

  // 중앙 수평선 이동 및 회전
  // pitch에 따라 상하 -35px ~ 35px, roll에 따라 -45deg ~ 45deg
  const horizonTranslateY = Math.max(-35, Math.min(35, -deltaPitch * 2));
  const horizonRotateDeg = Math.max(-45, Math.min(45, deltaRoll));

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
      {/* 1. 상단 파노라마 가이드 트랙 (아이폰 파노라마 스타일) */}
      <View style={styles.topTrackSection}>
        <View style={styles.trackContainer}>
          {/* 수평 기준선 (골드/그린 점선) */}
          <View
            style={[
              styles.trackCenterLine,
              { backgroundColor: isLevel ? '#22c55e' : 'rgba(255, 255, 255, 0.4)' },
            ]}
          />

          {/* 패닝 진행 방향 가이드 화살표 */}
          <View style={styles.trackArrows}>
            <Text style={styles.arrowText}>▶</Text>
            <Text style={styles.arrowText}>▶</Text>
            <Text style={styles.arrowText}>▶</Text>
            <Text style={styles.arrowText}>▶</Text>
            <Text style={styles.arrowText}>▶</Text>
          </View>

          {/* 현재 카메라 위치 및 수평 포인터 */}
          <View
            style={[
              styles.trackPointer,
              {
                left: 14 + trackIndicatorX,
                transform: [{ translateY: trackIndicatorY }],
                borderColor: feedback.borderColor,
                backgroundColor: isLevel ? '#22c55e' : '#f59e0b',
              },
            ]}
          >
            <View style={styles.pointerInner} />
          </View>
        </View>

        <Text style={styles.trackSubText}>
          {isRecording
            ? '수평선을 따라 화살표 방향으로 천천히 회전하세요'
            : '수평 기준을 맞춘 후 촬영을 시작하세요'}
        </Text>
      </View>

      {/* 2. 중앙 실시간 수평계 (DSLR / 항공기 인공 수평선) */}
      <View style={styles.centerHorizonWrapper} pointerEvents="none">
        {/* 좌측 고정 날개선 */}
        <View
          style={[
            styles.fixedWingLeft,
            { backgroundColor: isLevel ? '#22c55e' : 'rgba(255, 255, 255, 0.8)' },
          ]}
        />

        {/* 중앙 움직이는 수평 바 (기기 롤/피치에 반응) */}
        <View
          style={[
            styles.movingHorizonLine,
            {
              transform: [
                { translateY: horizonTranslateY },
                { rotate: `${horizonRotateDeg}deg` },
              ],
              borderColor: isLevel ? '#22c55e' : '#f59e0b',
              backgroundColor: isLevel ? '#22c55e' : 'rgba(245, 158, 11, 0.8)',
            },
          ]}
        >
          {/* 중앙 조준점 원 */}
          <View
            style={[
              styles.horizonCenterDot,
              { borderColor: isLevel ? '#22c55e' : '#f59e0b' },
            ]}
          />
        </View>

        {/* 우측 고정 날개선 */}
        <View
          style={[
            styles.fixedWingRight,
            { backgroundColor: isLevel ? '#22c55e' : 'rgba(255, 255, 255, 0.8)' },
          ]}
        />
      </View>

      {/* 3. 수평 상태 뱃지 & 실시간 각도 표시 */}
      <View style={styles.badgeSection}>
        <View
          style={[
            styles.feedbackBadge,
            {
              backgroundColor: feedback.bgColor,
              borderColor: feedback.borderColor,
            },
          ]}
        >
          <View
            style={[
              styles.statusDot,
              { backgroundColor: feedback.color },
            ]}
          />
          <Text style={[styles.feedbackText, { color: feedback.color }]}>
            {feedback.text}
          </Text>
        </View>

      </View>

      {/* 5. PC 웹 브라우저용 시뮬레이션 제어 바 (센서 없는 환경 테스트) */}
      {Platform.OS === 'web' && (
        <View style={styles.simWrapper}>


          {isSimulated && (
            <View style={styles.simCard}>
              <Text style={styles.simCardTitle}>가상 기울기 조절 (PC 테스트용)</Text>

              {/* 좌우 틸트 프리셋 버튼 */}
              <View style={styles.simBtnRow}>
                <Text style={styles.simLabel}>좌우(Roll):</Text>
                <TouchableOpacity
                  style={styles.simTinyBtn}
                  onPress={() => updateSim(simRoll - 3, simPitch, simHeading)}
                >
                  <Text style={styles.simTinyBtnText}>◀ -3°</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[styles.simTinyBtn, styles.simActiveBtn]}
                  onPress={() => updateSim(0, simPitch, simHeading)}
                >
                  <Text style={styles.simTinyBtnText}>수평 0°</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={styles.simTinyBtn}
                  onPress={() => updateSim(simRoll + 3, simPitch, simHeading)}
                >
                  <Text style={styles.simTinyBtnText}>+3° ▶</Text>
                </TouchableOpacity>
              </View>

              {/* 상하 피치 프리셋 버튼 */}
              <View style={styles.simBtnRow}>
                <Text style={styles.simLabel}>상하(Pitch):</Text>
                <TouchableOpacity
                  style={styles.simTinyBtn}
                  onPress={() => updateSim(simRoll, simPitch - 3, simHeading)}
                >
                  <Text style={styles.simTinyBtnText}>▲ 위 3°</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[styles.simTinyBtn, styles.simActiveBtn]}
                  onPress={() => updateSim(simRoll, 0, simHeading)}
                >
                  <Text style={styles.simTinyBtnText}>중앙 0°</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={styles.simTinyBtn}
                  onPress={() => updateSim(simRoll, simPitch + 3, simHeading)}
                >
                  <Text style={styles.simTinyBtnText}>▼ 아래 3°</Text>
                </TouchableOpacity>
              </View>

              {/* 패닝 진행 회전 버튼 */}
              <View style={styles.simBtnRow}>
                <Text style={styles.simLabel}>패닝(Pan):</Text>
                <TouchableOpacity
                  style={styles.simTinyBtn}
                  onPress={() => updateSim(simRoll, simPitch, 0)}
                >
                  <Text style={styles.simTinyBtnText}>시작점</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={styles.simTinyBtn}
                  onPress={() => updateSim(simRoll, simPitch, Math.min(90, simHeading + 15))}
                >
                  <Text style={styles.simTinyBtnText}>+15° 회전 ▶</Text>
                </TouchableOpacity>
              </View>
            </View>
          )}
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
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingTop: 14,
    paddingBottom: 16,
    zIndex: 10,
  },
  topTrackSection: {
    alignItems: 'center',
  },
  trackContainer: {
    width: 270,
    height: 52,
    backgroundColor: 'rgba(15, 23, 42, 0.75)',
    borderRadius: 14,
    borderWidth: 1.5,
    borderColor: 'rgba(255, 255, 255, 0.25)',
    justifyContent: 'center',
    position: 'relative',
    overflow: 'hidden',
    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.4)',
  },
  trackCenterLine: {
    position: 'absolute',
    left: 10,
    right: 10,
    height: 2,
    borderRadius: 1,
  },
  trackArrows: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingHorizontal: 20,
    opacity: 0.25,
  },
  arrowText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '700',
  },
  trackPointer: {
    position: 'absolute',
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2.5,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 0 10px rgba(0, 0, 0, 0.6)',
  },
  pointerInner: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: '#ffffff',
  },
  trackSubText: {
    color: '#cbd5e1',
    fontSize: 12,
    fontWeight: '500',
    marginTop: 6,
    textAlign: 'center',
    textShadowColor: 'rgba(0, 0, 0, 0.75)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 3,
  },
  centerHorizonWrapper: {
    alignSelf: 'center',
    width: 280,
    height: 100,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    position: 'relative',
  },
  fixedWingLeft: {
    width: 44,
    height: 3,
    borderRadius: 2,
  },
  fixedWingRight: {
    width: 44,
    height: 3,
    borderRadius: 2,
  },
  movingHorizonLine: {
    width: 140,
    height: 3,
    borderRadius: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  horizonCenterDot: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    backgroundColor: 'transparent',
  },
  badgeSection: {
    alignItems: 'center',
    marginTop: -10,
  },
  feedbackBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 7,
    paddingHorizontal: 16,
    borderRadius: 24,
    borderWidth: 1.5,
  },
  statusDot: {
    width: 9,
    height: 9,
    borderRadius: 4.5,
    marginRight: 8,
  },
  feedbackText: {
    fontSize: 13,
    fontWeight: '700',
  },
  angleDetailRow: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 6,
  },
  angleChip: {
    backgroundColor: 'rgba(15, 23, 42, 0.75)',
    paddingVertical: 3,
    paddingHorizontal: 9,
    borderRadius: 10,
    color: '#94a3b8',
    fontSize: 11,
    fontWeight: '500',
  },
  angleValue: {
    color: '#f8fafc',
    fontWeight: '700',
  },
  controlRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    marginVertical: 4,
  },
  calibrateButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(30, 41, 59, 0.9)',
    borderWidth: 1,
    borderColor: '#38bdf8',
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 20,
  },
  calibrateIcon: {
    marginRight: 6,
    fontSize: 14,
  },
  calibrateButtonText: {
    color: '#38bdf8',
    fontSize: 12,
    fontWeight: '700',
  },
  resetButton: {
    backgroundColor: 'rgba(30, 41, 59, 0.9)',
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#475569',
  },
  resetButtonText: {
    color: '#94a3b8',
    fontSize: 12,
    fontWeight: '500',
  },
  simWrapper: {
    alignItems: 'center',
    marginTop: 2,
  },
  simToggleBtn: {
    backgroundColor: 'rgba(15, 23, 42, 0.85)',
    paddingVertical: 5,
    paddingHorizontal: 12,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#334155',
  },
  simToggleText: {
    color: '#94a3b8',
    fontSize: 11,
    fontWeight: '600',
  },
  simCard: {
    marginTop: 6,
    backgroundColor: 'rgba(15, 23, 42, 0.95)',
    padding: 10,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#334155',
    width: '100%',
    maxWidth: 320,
  },
  simCardTitle: {
    color: '#e2e8f0',
    fontSize: 11,
    fontWeight: '700',
    marginBottom: 6,
    textAlign: 'center',
  },
  simBtnRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginVertical: 3,
  },
  simLabel: {
    color: '#94a3b8',
    fontSize: 11,
    width: 80,
  },
  simTinyBtn: {
    backgroundColor: '#1e293b',
    paddingVertical: 4,
    paddingHorizontal: 8,
    borderRadius: 6,
  },
  simActiveBtn: {
    backgroundColor: '#0369a1',
  },
  simTinyBtnText: {
    color: '#f8fafc',
    fontSize: 10,
    fontWeight: '600',
  },
});
