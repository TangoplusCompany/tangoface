import React from 'react';
import { StyleSheet, Text, View, TouchableOpacity } from 'react-native';

interface UploadSuccessViewProps {
  sessionId: string;
  onReset: () => void;
}

export const UploadSuccessView: React.FC<UploadSuccessViewProps> = ({
  sessionId,
  onReset,
}) => {
  return (
    <View style={styles.container}>
      <View style={styles.card}>
        <View style={styles.iconCircle}>
          <Text style={styles.iconText}>✨</Text>
        </View>

        <Text style={styles.title}>영상 전송 완료!</Text>
        <Text style={styles.subtitle}>
          얼굴 3D 스캔 영상이{'\n'}
          성공적으로 전달되었습니다.
        </Text>

        <View style={styles.statusBox}>
          <View style={styles.statusRow}>
            <Text style={styles.statusLabel}>세션 ID</Text>
            <Text style={styles.statusValue} numberOfLines={1}>
              {sessionId.slice(0, 8)}...
            </Text>
          </View>
          <View style={styles.statusRow}>
            <Text style={styles.statusLabel}>상태</Text>
            <View style={styles.statusBadge}>
              <Text style={styles.statusBadgeText}>UPLOADED</Text>
            </View>
          </View>
        </View>

        <View style={styles.guideBox}>
          <Text style={styles.guideText}>
            👉 이제 <Text style={styles.boldText}>태블릿 화면</Text>을 확인해주세요!{'\n'}
            3D 모델 복원(RunPod)이 진행되며 결과가 태블릿에 표시됩니다.
          </Text>
        </View>

        <TouchableOpacity
          style={styles.resetButton}
          onPress={onReset}
          activeOpacity={0.8}
        >
          <Text style={styles.resetButtonText}>새로운 스캔 시작하기</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#090d16',
  },
  card: {
    width: '100%',
    maxWidth: 380,
    backgroundColor: 'rgba(30, 41, 59, 0.7)',
    borderRadius: 24,
    borderWidth: 1.5,
    borderColor: 'rgba(16, 185, 129, 0.3)',
    padding: 28,
    alignItems: 'center',
  },
  iconCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: 'rgba(16, 185, 129, 0.15)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
    borderWidth: 1,
    borderColor: 'rgba(16, 185, 129, 0.4)',
  },
  iconText: {
    fontSize: 34,
  },
  title: {
    fontSize: 22,
    fontWeight: '800',
    color: '#ffffff',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 14,
    color: '#94a3b8',
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 20,
  },
  statusBox: {
    width: '100%',
    backgroundColor: 'rgba(15, 23, 42, 0.6)',
    borderRadius: 14,
    padding: 16,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.06)',
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  statusLabel: {
    fontSize: 13,
    color: '#64748b',
  },
  statusValue: {
    fontSize: 13,
    color: '#e2e8f0',
    fontWeight: '600',
    fontFamily: 'monospace',
  },
  statusBadge: {
    backgroundColor: 'rgba(16, 185, 129, 0.2)',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  statusBadgeText: {
    color: '#34d399',
    fontSize: 12,
    fontWeight: '700',
  },
  guideBox: {
    backgroundColor: 'rgba(56, 189, 248, 0.1)',
    borderRadius: 12,
    padding: 14,
    width: '100%',
    marginBottom: 24,
    borderWidth: 1,
    borderColor: 'rgba(56, 189, 248, 0.2)',
  },
  guideText: {
    fontSize: 13,
    color: '#bae6fd',
    lineHeight: 18,
    textAlign: 'center',
  },
  boldText: {
    fontWeight: '700',
    color: '#38bdf8',
  },
  resetButton: {
    width: '100%',
    height: 50,
    backgroundColor: '#10b981',
    borderRadius: 14,
    justifyContent: 'center',
    alignItems: 'center',
  },
  resetButtonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '700',
  },
});
