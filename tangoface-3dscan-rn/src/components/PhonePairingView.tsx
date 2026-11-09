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
        <View style={styles.iconCircle}>
          <Text style={styles.iconText}>📱</Text>
        </View>

        <Text style={styles.title}>스마트폰 촬영 연동</Text>
        <Text style={styles.subtitle}>
          태블릿 화면에 표시된{'\n'}
          <Text style={styles.highlightText}>6자리 페어링 번호</Text>를 입력해주세요.
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
            <Text style={styles.errorText}>⚠️ {errorMessage}</Text>
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
            <Text style={styles.pairButtonText}>세션 연결하기</Text>
          )}
        </TouchableOpacity>

        <View style={styles.infoBox}>
          <Text style={styles.infoText}>
            💡 페어링 번호는 1회용이며 발급 후 10분간 유효합니다.
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
    backgroundColor: '#090d16',
  },
  card: {
    width: '100%',
    maxWidth: 380,
    backgroundColor: 'rgba(30, 41, 59, 0.7)',
    borderRadius: 24,
    borderWidth: 1.5,
    borderColor: 'rgba(56, 189, 248, 0.25)',
    padding: 28,
    alignItems: 'center',
  },
  iconCircle: {
    width: 68,
    height: 68,
    borderRadius: 34,
    backgroundColor: 'rgba(56, 189, 248, 0.15)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
    borderWidth: 1,
    borderColor: 'rgba(56, 189, 248, 0.3)',
  },
  iconText: {
    fontSize: 32,
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
    marginBottom: 24,
  },
  highlightText: {
    color: '#38bdf8',
    fontWeight: '700',
  },
  inputContainer: {
    width: '100%',
    marginBottom: 16,
  },
  input: {
    width: '100%',
    height: 64,
    backgroundColor: 'rgba(15, 23, 42, 0.8)',
    borderRadius: 16,
    borderWidth: 2,
    borderColor: '#38bdf8',
    color: '#ffffff',
    fontSize: 32,
    fontWeight: '800',
    textAlign: 'center',
    letterSpacing: 10,
  },
  errorBox: {
    width: '100%',
    backgroundColor: 'rgba(239, 68, 68, 0.15)',
    borderRadius: 10,
    padding: 10,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: 'rgba(239, 68, 68, 0.3)',
  },
  errorText: {
    color: '#f87171',
    fontSize: 13,
    textAlign: 'center',
    fontWeight: '600',
  },
  pairButton: {
    width: '100%',
    height: 52,
    backgroundColor: '#0284c7',
    borderRadius: 14,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 20,
  },
  disabledButton: {
    backgroundColor: '#334155',
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
    color: '#64748b',
    textAlign: 'center',
    lineHeight: 16,
  },
});
