import { StatusBar } from 'expo-status-bar';
import { useState, useEffect } from 'react';
import {
  Platform,
  SafeAreaView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { VideoUploadView } from './src/components/VideoUploadView';
import { PhonePairingView } from './src/components/PhonePairingView';
import { UploadSuccessView } from './src/components/UploadSuccessView';
import { ScanGuideModal } from './src/components/ScanGuideModal';

// 단계: 1) PIN 페어링 -> 2) 영상 촬영/선택 & 업로드 -> 3) 완료
type AppStep = 'pairing' | 'upload' | 'success';

const SESSION_STORAGE_KEY = 'tangoface_active_session_v2';

interface SavedSessionState {
  pairToken: string;
  sessionId: string;
  appStep: AppStep;
}

function loadSavedSession(): SavedSessionState | null {
  if (Platform.OS !== 'web' || typeof window === 'undefined' || !window.sessionStorage) {
    return null;
  }
  try {
    const raw = window.sessionStorage.getItem(SESSION_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed?.pairToken || !parsed?.sessionId) return null;
    const step: AppStep = parsed.appStep === 'upload' ? 'upload' : 'upload';
    return {
      pairToken: parsed.pairToken,
      sessionId: parsed.sessionId,
      appStep: step,
    };
  } catch {
    return null;
  }
}

function saveActiveSession(state: SavedSessionState) {
  if (Platform.OS !== 'web' || typeof window === 'undefined' || !window.sessionStorage) {
    return;
  }
  try {
    window.sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(state));
  } catch {
    // ignore storage quota errors
  }
}

function clearSavedSession() {
  if (Platform.OS !== 'web' || typeof window === 'undefined' || !window.sessionStorage) {
    return;
  }
  try {
    window.sessionStorage.removeItem(SESSION_STORAGE_KEY);
  } catch {
    // ignore
  }
}

export default function App() {
  const [restoredSession] = useState<SavedSessionState | null>(() => loadSavedSession());

  // 1. 세션 & 페어링 상태
  const [appStep, setAppStep] = useState<AppStep>(() => restoredSession?.appStep || 'pairing');
  const [pairToken, setPairToken] = useState<string | null>(() => restoredSession?.pairToken || null);
  const [sessionId, setSessionId] = useState<string | null>(() => restoredSession?.sessionId || null);
  const [isQueueFull, setIsQueueFull] = useState(false);

  // 2. 촬영 가이드 모달 상태 (PIN 통과 직후 자동 노출 및 상단 버튼으로 언제든 열람 가능)
  const [showGuideModal, setShowGuideModal] = useState<boolean>(false);

  // 세션 스토리지 동기화 (모바일 브라우저 파일 선택 중 탭 리로드 시에도 페어링 유지)
  useEffect(() => {
    if (pairToken && sessionId && appStep === 'upload') {
      saveActiveSession({
        pairToken,
        sessionId,
        appStep,
      });
    } else if (appStep === 'pairing' || appStep === 'success') {
      clearSavedSession();
    }
  }, [pairToken, sessionId, appStep]);

  // 1단계: PIN 번호 입력 성공 시 -> 촬영 가이드 먼저 노출 후 업로드 화면으로 전환
  const handlePaired = (token: string, sessId: string) => {
    setPairToken(token);
    setSessionId(sessId);
    setIsQueueFull(false);
    setShowGuideModal(true);
  };

  // 가이드 모달 닫기 -> 바로 영상 촬영/선택(업로드) 화면으로 이동
  const handleCloseGuideModal = () => {
    setShowGuideModal(false);
    if (appStep === 'pairing') {
      setAppStep('upload');
    }
  };

  // 전체 초기화 (PIN 입력 화면으로 복귀)
  const handleResetAll = () => {
    clearSavedSession();
    setPairToken(null);
    setSessionId(null);
    setIsQueueFull(false);
    setShowGuideModal(false);
    setAppStep('pairing');
  };

  return (
    <SafeAreaView style={styles.outerContainer}>
      <StatusBar style="light" />
      <View style={styles.mobileFrame}>
        {/* ========================================================= */}
        {/* 상단 헤더 바 (업로드 단계에서 상시 노출) */}
        {/* ========================================================= */}
        {appStep === 'upload' && (
          <View style={styles.header}>
            <View style={styles.headerTopRow}>
              <View>
                <Text style={styles.headerTitle}>TangoFace 3D Scan</Text>
                <Text style={styles.sessionBadge}>기기 페어링 완료</Text>
              </View>
              <TouchableOpacity
                style={styles.guideTriggerButton}
                onPress={() => setShowGuideModal(true)}
                activeOpacity={0.8}
              >
                <Text style={styles.guideTriggerText}>📖 촬영 가이드</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}

        {/* ========================================================= */}
        {/* 1단계: 6자리 PIN 페어링 번호 입력 */}
        {/* ========================================================= */}
        {appStep === 'pairing' && (
          <PhonePairingView onPaired={handlePaired} />
        )}

        {/* ========================================================= */}
        {/* 2단계: 카메라 촬영 또는 파일 선택 & 업로드 화면 */}
        {/* ========================================================= */}
        {appStep === 'upload' && pairToken && (
          <VideoUploadView
            key={pairToken}
            pairToken={pairToken}
            onUploadSuccess={(queueFull) => {
              setIsQueueFull(Boolean(queueFull));
              setAppStep('success');
            }}
            onResetSession={handleResetAll}
            onOpenGuide={() => setShowGuideModal(true)}
          />
        )}

        {/* ========================================================= */}
        {/* 3단계: 영상 전송 완료 안내 화면 */}
        {/* ========================================================= */}
        {appStep === 'success' && sessionId && (
          <UploadSuccessView
            key={sessionId}
            sessionId={sessionId}
            queueFull={isQueueFull}
            onReset={handleResetAll}
          />
        )}

        {/* 촬영 방법 및 3D 스캔 품질 향상 가이드 모달 */}
        <ScanGuideModal
          visible={showGuideModal}
          onClose={handleCloseGuideModal}
        />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  outerContainer: {
    flex: 1,
    backgroundColor: '#000000',
    alignItems: 'center',
    justifyContent: 'center',
  },
  mobileFrame: {
    width: '100%',
    maxWidth: 480,
    height: '100%',
    backgroundColor: '#161214',
    position: 'relative',
    overflow: 'hidden',
  },
  header: {
    paddingTop: Platform.OS === 'android' ? 36 : 14,
    paddingHorizontal: 18,
    paddingBottom: 12,
    backgroundColor: '#161214',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 255, 255, 0.08)',
    zIndex: 10,
  },
  headerTopRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  headerTitle: {
    color: '#ffffff',
    fontSize: 18,
    fontWeight: '800',
    letterSpacing: -0.3,
  },
  sessionBadge: {
    color: '#00FFCC',
    fontSize: 11,
    fontWeight: '600',
    marginTop: 2,
  },
  guideTriggerButton: {
    backgroundColor: 'rgba(255, 0, 85, 0.15)',
    borderWidth: 1,
    borderColor: 'rgba(255, 0, 85, 0.35)',
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 14,
  },
  guideTriggerText: {
    color: '#FF8794',
    fontSize: 12,
    fontWeight: '700',
  },
});
