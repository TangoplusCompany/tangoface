import React, { useState, useEffect, useRef, useCallback } from 'react';
import { StyleSheet, Text, View, TouchableOpacity, ActivityIndicator } from 'react-native';
import { checkColabHealth, ColabHealthStatus } from '../config/api';

interface ServerStatusBadgeProps {
  /** 업로드 진행 중에는 불필요한 주기적 체크를 잠시 중단 */
  paused?: boolean;
  /** 체크 간격 (기본 10초) */
  intervalSeconds?: number;
}

export const ServerStatusBadge: React.FC<ServerStatusBadgeProps> = ({
  paused = false,
  intervalSeconds = 10,
}) => {
  const [health, setHealth] = useState<ColabHealthStatus | null>(null);
  const [isChecking, setIsChecking] = useState<boolean>(true);
  const [countdown, setCountdown] = useState<number>(intervalSeconds);

  const isMountedRef = useRef(true);
  const isCheckingRef = useRef(false);

  const runHealthCheck = useCallback(async () => {
    if (isCheckingRef.current) return;
    isCheckingRef.current = true;
    if (isMountedRef.current) {
      setIsChecking(true);
    }

    const res = await checkColabHealth();

    if (isMountedRef.current) {
      setHealth(res);
      setIsChecking(false);
      setCountdown(intervalSeconds);
    }
    isCheckingRef.current = false;
  }, [intervalSeconds]);

  // 마운트 시 최초 1회 즉시 확인
  useEffect(() => {
    isMountedRef.current = true;
    runHealthCheck();
    return () => {
      isMountedRef.current = false;
    };
  }, [runHealthCheck]);

  // 1초마다 카운트다운 (10 -> 9 -> 8 ... -> 1 -> 0 시 재확인)
  useEffect(() => {
    if (paused) return;

    const timer = setInterval(() => {
      if (isCheckingRef.current) return;

      setCountdown((prev) => {
        if (prev <= 1) {
          runHealthCheck();
          return intervalSeconds;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [paused, intervalSeconds, runHealthCheck]);

  const isOnline = health?.alive === true;
  const queueCount = typeof health?.queue === 'number' ? health.queue : 0;
  const isBusy = health?.busy === true;

  const statusLabel = isChecking
    ? '서버 상태 확인 중...'
    : isOnline
    ? queueCount > 0
      ? `서버 연결됨 (대기 ${queueCount}명)`
      : isBusy
      ? '서버 연결됨 (작업 중)'
      : '서버 연결됨'
    : '서버 꺼짐';

  return (
    <TouchableOpacity
      style={styles.container}
      onPress={() => {
        if (!paused && !isChecking) {
          runHealthCheck();
        }
      }}
      activeOpacity={0.75}
      disabled={paused || isChecking}
    >
      <View style={styles.leftRow}>
        {isChecking ? (
          <ActivityIndicator size="small" color="#FF8794" style={styles.spinner} />
        ) : (
          <View
            style={[
              styles.statusDot,
              isOnline ? styles.dotOnline : styles.dotOffline,
            ]}
          />
        )}
        <Text
          style={[
            styles.statusText,
            !isChecking && (isOnline ? styles.textOnline : styles.textOffline),
          ]}
        >
          {statusLabel}
        </Text>
      </View>

      {!isChecking && !paused && (
        <Text style={styles.countdownText}>{countdown}초 뒤 확인합니다</Text>
      )}
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  container: {
    width: '100%',
    backgroundColor: '#1C191A',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 10,
    marginBottom: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  leftRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  spinner: {
    marginRight: 8,
    transform: [{ scale: 0.75 }],
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 8,
  },
  dotOnline: {
    backgroundColor: '#00FFCC',
  },
  dotOffline: {
    backgroundColor: '#FF5449',
  },
  statusText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#DCD9D9',
  },
  textOnline: {
    color: '#00FFCC',
  },
  textOffline: {
    color: '#FF5449',
  },
  countdownText: {
    fontSize: 11,
    fontWeight: '600',
    color: '#9E9C9C',
  },
});
