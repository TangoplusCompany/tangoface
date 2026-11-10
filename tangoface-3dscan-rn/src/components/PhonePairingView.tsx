import React, { useState } from 'react';
import {
  StyleSheet,
  Text,
  View,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
} from 'react-native';
import { pairSession } from '../config/api';

interface PhonePairingViewProps {
  onPaired: (pairToken: string, sessionId: string) => void;
}

export const PhonePairingView: React.FC<PhonePairingViewProps> = ({ onPaired }) => {
  const [code, setCode] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handlePair = async () => {
    const trimmedCode = code.trim();
    if (trimmedCode.length !== 6) {
      setErrorMessage('6자리 숫자를 정확히 입력해주세요.');
      return;
    }

    // 💡 개발/테스트용 마스터 PIN (990999): 즉시 스캔 화면으로 통과
    if (trimmedCode === '990999') {
      onPaired('test_pair_token_990999', 'test-session-990999');
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);

    const result = await pairSession(trimmedCode);
    setIsLoading(false);

    if (result.success && result.pair_token && result.session_id) {
      onPaired(result.pair_token, result.session_id);
    } else {
      setErrorMessage(result.error || '페어링에 실패했습니다. 번호를 확인해주세요.');
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.card}>

        <Text style={styles.title}>TangoFace {'\n'}3D 얼굴 스캔</Text>
        <Text style={styles.subtitle}>
          화면에 표시된 <Text style={styles.highlightText}>6자리 번호</Text>를 입력해주세요.
        </Text>

        <View style={styles.inputContainer}>
          <TextInput
            style={styles.input}
            value={code}
            onChangeText={(text) => {
              const num = text.replace(/[^0-9]/g, '').slice(0, 6);
              setCode(num);
              if (errorMessage) setErrorMessage(null);
            }}
            placeholder="000000"
            placeholderTextColor="#475569"
            keyboardType="number-pad"
            maxLength={6}
            autoFocus
          />
        </View>

        {errorMessage && (
          <View style={styles.errorBox}>
            <Text style={styles.errorText}>{errorMessage}</Text>
          </View>
        )}

        <TouchableOpacity
          style={[
            styles.pairButton,
            code.length !== 6 || isLoading ? styles.disabledButton : null,
          ]}
          onPress={handlePair}
          disabled={code.length !== 6 || isLoading}
          activeOpacity={0.8}
        >
          {isLoading ? (
            <ActivityIndicator color="#ffffff" />
          ) : (
            <Text style={styles.pairButtonText}>연결하기</Text>
          )}
        </TouchableOpacity>

        <View style={styles.infoBox}>
          <Text style={styles.infoText}>
            페어링 번호는 1회용이며 발급 후 10분간 유효합니다.
          </Text>
        </View>
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
    backgroundColor: '#000000',
  },
  card: {
    width: '100%',
    maxWidth: 380,
    backgroundColor: 'rgba(28, 25, 26, 0.85)',
    borderRadius: 24,
    padding: 28,
    alignItems: 'center',
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
    marginBottom: 24,
  },
  highlightText: {
    color: '#FF516F',
    fontWeight: '700',
  },
  inputContainer: {
    width: '100%',
    marginBottom: 16,
  },
  input: {
    width: '100%',
    height: 64,
    backgroundColor: '#161214',
    borderRadius: 16,
    borderWidth: 2,
    borderColor: '#FF0055',
    color: '#ffffff',
    fontSize: 32,
    fontWeight: '800',
    textAlign: 'center',
    letterSpacing: 10,
  },
  errorBox: {
    width: '100%',
    backgroundColor: 'rgba(255, 84, 73, 0.15)',
    borderRadius: 10,
    padding: 10,
    marginBottom: 16,
  },
  errorText: {
    color: '#FF5449',
    fontSize: 13,
    textAlign: 'center',
    fontWeight: '600',
  },
  pairButton: {
    width: '100%',
    height: 52,
    backgroundColor: '#FF0055',
    borderRadius: 14,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 20,
  },
  disabledButton: {
    backgroundColor: '#30292B',
    opacity: 0.6,
  },
  pairButtonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '700',
  },
  infoBox: {
    paddingTop: 8,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255, 255, 255, 0.08)',
    width: '100%',
  },
  infoText: {
    fontSize: 12,
    color: '#9E9C9C',
    textAlign: 'center',
    lineHeight: 16,
  },
});
