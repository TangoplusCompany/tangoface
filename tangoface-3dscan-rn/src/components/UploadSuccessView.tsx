import React from 'react';
import { StyleSheet, Text, View, TouchableOpacity } from 'react-native';

interface UploadSuccessViewProps {
  sessionId: string;
  queueFull?: boolean;
  onReset: () => void;
}

export const UploadSuccessView: React.FC<UploadSuccessViewProps> = ({
  sessionId,
  queueFull = false,
  onReset,
}) => {
  return (
    <View style={styles.container}>
      <View style={styles.card}>
        

        <Text style={styles.title}>영상 전송 완료</Text>
        <Text style={styles.subtitle}>
          얼굴 3D 스캔 영상이{'\n'}
          성공적으로 전달되었습니다.
        </Text>

        <View style={styles.guideBox}>
          <Text style={styles.guideText}>
            {queueFull ? (
              <>
                현재 처리 요청이 혼잡합니다.{'\n'}
                잠시 후 자동으로 작업이 시작되며,{'\n'}
                진행 상황은 <Text style={styles.boldText}>태블릿 화면</Text>에서 확인할 수 있습니다.
              </>
            ) : (
              <>
                이제 <Text style={styles.boldText}>태블릿 화면</Text>을 확인해주세요.{'\n'}
                3D 모델 복원이 진행되며 결과가 태블릿에 표시됩니다.
              </>
            )}
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
    backgroundColor: '#161214',
  },
  card: {
    width: '100%',
    maxWidth: 380,
    backgroundColor: 'rgba(28, 25, 26, 0.85)',
    borderRadius: 24,
    padding: 28,
    alignItems: 'center',
  },
  iconCircle: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: 'rgba(0, 255, 204, 0.15)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  iconText: {
    fontSize: 14,
    fontWeight: '800',
    color: '#00FFCC',
    letterSpacing: 1,
  },
  title: {
    fontSize: 22,
    fontWeight: '800',
    color: '#ffffff',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 14,
    color: '#9E9C9C',
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 20,
  },
  statusBox: {
    width: '100%',
    backgroundColor: '#161214',
    borderRadius: 14,
    padding: 16,
    marginBottom: 16,
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  statusLabel: {
    fontSize: 13,
    color: '#9E9C9C',
  },
  statusValue: {
    fontSize: 13,
    color: '#DCD9D9',
    fontWeight: '600',
    fontFamily: 'monospace',
  },
  statusBadge: {
    backgroundColor: 'rgba(0, 255, 204, 0.15)',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  statusBadgeText: {
    color: '#00FFCC',
    fontSize: 12,
    fontWeight: '700',
  },
  guideBox: {
    backgroundColor: 'rgba(255, 0, 85, 0.12)',
    borderRadius: 12,
    padding: 14,
    width: '100%',
    marginBottom: 24,
  },
  guideText: {
    fontSize: 13,
    color: '#FFECED',
    lineHeight: 18,
    textAlign: 'center',
  },
  boldText: {
    fontWeight: '700',
    color: '#FF516F',
  },
  resetButton: {
    width: '100%',
    height: 50,
    backgroundColor: '#FF0055',
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
