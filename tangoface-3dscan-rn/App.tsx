import { StatusBar } from 'expo-status-bar';
import { CameraType } from 'expo-camera';
import { useState, useEffect, useRef } from 'react';
import {
  Platform,
  SafeAreaView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  ActivityIndicator,
} from 'react-native';
import { useDeviceOrientation } from './src/hooks/useDeviceOrientation';
import { PanoramaGuide } from './src/components/PanoramaGuide';
import { VideoUploadView } from './src/components/VideoUploadView';
import { useCameraManager } from './src/hooks/useCameraManager';
import {
  SmartCameraView,
  SmartCameraHandle,
} from './src/components/SmartCameraView';
import { PhonePairingView } from './src/components/PhonePairingView';
import { UploadSuccessView } from './src/components/UploadSuccessView';
import {
  getPresignedUploadUrl,
  uploadVideoDirectly,
  completeUpload,
} from './src/config/api';

// 단계: 1) PIN 입력 -> 2) 권한 허용 -> 3) 촬영/업로드 -> 4) 미리보기 -> 5) 완료
type AppStep = 'pairing' | 'permissions' | 'scanning' | 'recorded_preview' | 'success';
type ScanMode = 'panorama' | 'upload';

export default function App() {
  // 1. 세션 & 페어링 상태
  const [appStep, setAppStep] = useState<AppStep>('pairing');
  const [pairToken, setPairToken] = useState<string | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);

  // 2. 카메라 & 모드 상태
  const [facing, setFacing] = useState<CameraType>('back');
  const [scanMode, setScanMode] = useState<ScanMode>('panorama');
  const [isRecording, setIsRecording] = useState(false);
  const [recordSeconds, setRecordSeconds] = useState(0);
  const [levelDurationCount, setLevelDurationCount] = useState(0);

  // 3. 녹화된 비디오 Blob 및 업로드 진행 상태
  const [recordedBlob, setRecordedBlob] = useState<Blob | null>(null);
  const [recordedPreviewUrl, setRecordedPreviewUrl] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadStatusText, setUploadStatusText] = useState('');
  const [uploadError, setUploadError] = useState<string | null>(null);

  const cameraRef = useRef<SmartCameraHandle | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // 카메라 권한 및 웹 보안 컨텍스트 매니저
  const {
    hasPermission: hasCameraPermission,
    isLoading: isCameraLoading,
    errorMessage: cameraError,
    isSecureContext,
    requestPermission: requestCameraPermission,
  } = useCameraManager();

  // 디바이스 자이로/방향각 훅 (오차 허용치: 2.5도)
  const {
    orientation,
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
  } = useDeviceOrientation(2.5);

  // 1단계: PIN 번호 입력 성공 시 -> 2단계(권한 요청)로 이동
  const handlePaired = (token: string, sessId: string) => {
    setPairToken(token);
    setSessionId(sessId);
    // 이미 카메라 권한이 있다면 바로 scanning으로, 없으면 permissions 단계로
    if (hasCameraPermission) {
      setAppStep('scanning');
    } else {
      setAppStep('permissions');
    }
  };

  // 2단계: 카메라 + 자이로 센서 동시 권한 요청
  const handleAllowPermissions = async () => {
    try {
      const sensorPromise = requestSensorPermission();
      const cameraPromise = requestCameraPermission();
      const [cameraResult, sensorResult] = await Promise.all([
        cameraPromise,
        sensorPromise,
      ]);

      if (cameraResult) {
        setAppStep('scanning');
      }
    } catch (e) {
      console.error('권한 요청 중 오류:', e);
    }
  };

  // 녹화 타이머 및 수평 유지율 계산
  useEffect(() => {
    if (isRecording) {
      timerRef.current = setInterval(() => {
        setRecordSeconds((prev) => prev + 1);
        if (orientation.isLevel) {
          setLevelDurationCount((prev) => prev + 1);
        }
      }, 1000);
    } else {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
    }

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
    };
  }, [isRecording, orientation.isLevel]);

  // 촬영 토글 (시작 시 현재 위치를 자동으로 기준 영점으로 캘리브레이션!)
  const handleToggleRecord = async () => {
    if (!isRecording) {
      calibrateToCurrentPosition();
      setRecordSeconds(0);
      setLevelDurationCount(0);

      if (cameraRef.current) {
        cameraRef.current.startRecording();
      }
      setIsRecording(true);
    } else {
      setIsRecording(false);

      let blob: Blob | null = null;
      if (cameraRef.current) {
        blob = await cameraRef.current.stopRecording();
      }

      if (blob && blob.size > 0) {
        setRecordedBlob(blob);
        const url = URL.createObjectURL(blob);
        setRecordedPreviewUrl(url);
        setAppStep('recorded_preview');
      } else {
        alert('촬영된 영상 데이터가 없습니다. 다시 시도해주세요.');
      }
    }
  };

  // 촬영된 비디오 Supabase Storage로 직접 업로드 실행
  const handleUploadRecordedVideo = async () => {
    if (!recordedBlob || !pairToken) {
      alert('업로드할 영상 또는 페어링 정보가 없습니다.');
      return;
    }

    setIsUploading(true);
    setUploadError(null);
    setUploadProgress(0);

    try {
      setUploadStatusText('1/3 업로드 URL 발급 중...');
      const presignRes = await getPresignedUploadUrl(pairToken);
      if (!presignRes.success || !presignRes.upload_url) {
        throw new Error(presignRes.error || '업로드 URL 발급에 실패했습니다.');
      }

      setUploadStatusText('2/3 Supabase 스토리지로 영상 직접 전송 중...');
      const uploadRes = await uploadVideoDirectly(
        presignRes.upload_url,
        recordedBlob,
        (percent) => {
          setUploadProgress(percent);
        }
      );

      if (!uploadRes.success) {
        throw new Error(uploadRes.error || '영상 파일 전송에 실패했습니다.');
      }

      setUploadStatusText('3/3 파일 검증 및 세션 확정 중...');
      const completeRes = await completeUpload(pairToken);
      if (!completeRes.success) {
        throw new Error(completeRes.error || '업로드 완료 검증에 실패했습니다.');
      }

      setAppStep('success');
    } catch (err: any) {
      setUploadError(err.message || '업로드 중 오류가 발생했습니다.');
    } finally {
      setIsUploading(false);
      setUploadStatusText('');
    }
  };

  // 재촬영
  const handleRetake = () => {
    if (recordedPreviewUrl) {
      URL.revokeObjectURL(recordedPreviewUrl);
    }
    setRecordedBlob(null);
    setRecordedPreviewUrl(null);
    setUploadError(null);
    setUploadProgress(0);
    setAppStep('scanning');
  };

  // 전체 초기화
  const handleResetAll = () => {
    if (recordedPreviewUrl) {
      URL.revokeObjectURL(recordedPreviewUrl);
    }
    setPairToken(null);
    setSessionId(null);
    setRecordedBlob(null);
    setRecordedPreviewUrl(null);
    setIsRecording(false);
    setRecordSeconds(0);
    setLevelDurationCount(0);
    setUploadError(null);
    setUploadProgress(0);
    setAppStep('pairing');
  };

  const toggleCameraFacing = () => {
    setFacing((current) => (current === 'back' ? 'front' : 'back'));
  };

  const formatTime = (secs: number) => {
    const m = Math.floor(secs / 60)
      .toString()
      .padStart(2, '0');
    const s = (secs % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  };

  return (
    <SafeAreaView style={styles.outerContainer}>
      <StatusBar style="light" />
      <View style={styles.mobileFrame}>
        {/* ========================================================= */}
        {/* 1단계: 6자리 PIN 페어링 번호 입력 (틀리면 에러 경고 알림) */}
        {/* ========================================================= */}
        {appStep === 'pairing' && (
          <PhonePairingView onPaired={handlePaired} />
        )}

        {/* ========================================================= */}
        {/* 2단계: PIN 통과 후 카메라 + 센서 권한 동시 요청 화면 */}
        {/* ========================================================= */}
        {appStep === 'permissions' && (
          <View style={styles.permissionBox}>
            <View style={styles.permissionIconCircle}>
              <Text style={styles.permissionEmoji}>📸</Text>
            </View>

            <View style={styles.pairSuccessTag}>
              <Text style={styles.pairSuccessTagText}>
                ✅ PIN 확인 완료 (세션: {sessionId?.slice(0, 8)}...)
              </Text>
            </View>

            <Text style={styles.permissionTitle}>카메라 및 센서 권한 허용</Text>
            <Text style={styles.permissionDesc}>
              얼굴 3D 파노라마 스캔을 진행하려면{'\n'}
              <Text style={styles.highlightText}>카메라 영상</Text>과{' '}
              <Text style={styles.highlightText}>스마트폰 수평 센서</Text> 권한이 필요합니다.
            </Text>

            {cameraError && (
              <View style={styles.errorAlertBox}>
                <Text style={styles.errorAlertText}>{cameraError}</Text>
              </View>
            )}

            {!isSecureContext && Platform.OS === 'web' && (
              <View style={styles.warningBox}>
                <Text style={styles.warningText}>
                  💡 크롬 보안 정책: IP 주소 대신{' '}
                  <Text style={{ fontWeight: '700', color: '#38bdf8' }}>
                    http://localhost:8081
                  </Text>{' '}
                  또는 HTTPS 배포 도메인으로 접속하셔야 카메라가 활성화됩니다.
                </Text>
              </View>
            )}

            {/* 둘 다 허용 버튼 */}
            <TouchableOpacity
              style={styles.primaryButton}
              onPress={handleAllowPermissions}
              disabled={isCameraLoading}
              activeOpacity={0.8}
            >
              {isCameraLoading ? (
                <ActivityIndicator color="#ffffff" />
              ) : (
                <Text style={styles.primaryButtonText}>
                  카메라 및 센서 권한 허용하기
                </Text>
              )}
            </TouchableOpacity>

            {/* 또는 파일 첨부로 바로 가기 */}
            <TouchableOpacity
              style={styles.fallbackButton}
              onPress={() => {
                setScanMode('upload');
                setAppStep('scanning');
              }}
            >
              <Text style={styles.fallbackButtonText}>
                📁 카메라 대신 영상 파일 첨부하기
              </Text>
            </TouchableOpacity>
          </View>
        )}

        {/* ========================================================= */}
        {/* 3단계: 권한 허용 완료 후 - 촬영 또는 영상 첨부 화면 */}
        {/* ========================================================= */}
        {appStep === 'scanning' && (
          <>
            {/* 상단 바 & 모드 셀렉터 */}
            <View style={styles.header}>
              <View style={styles.headerTopRow}>
                <View>
                  <Text style={styles.headerTitle}>TangoFace 3D Scan</Text>
                  {sessionId && (
                    <Text style={styles.sessionBadge}>
                      연결 세션: {sessionId.slice(0, 8)}...
                    </Text>
                  )}
                </View>
                {isRecording && (
                  <View style={styles.recBadge}>
                    <View style={styles.recDot} />
                    <Text style={styles.recTimeText}>{formatTime(recordSeconds)}</Text>
                  </View>
                )}
              </View>

              {/* 모드 전환 탭 */}
              <View style={styles.modeTabs}>
                <TouchableOpacity
                  style={[
                    styles.modeTab,
                    scanMode === 'panorama' && styles.activeModeTab,
                  ]}
                  onPress={() => setScanMode('panorama')}
                >
                  <Text
                    style={[
                      styles.modeTabText,
                      scanMode === 'panorama' && styles.activeModeTabText,
                    ]}
                  >
                    파노라마 수평 스캔
                  </Text>
                </TouchableOpacity>

                <TouchableOpacity
                  style={[
                    styles.modeTab,
                    scanMode === 'upload' && styles.activeModeTab,
                  ]}
                  onPress={() => setScanMode('upload')}
                >
                  <Text
                    style={[
                      styles.modeTabText,
                      scanMode === 'upload' && styles.activeModeTabText,
                    ]}
                  >
                    영상 파일 첨부
                  </Text>
                </TouchableOpacity>
              </View>
            </View>

            {/* 메인 컨텐츠 영역 */}
            {scanMode === 'upload' && pairToken ? (
              <VideoUploadView
                pairToken={pairToken}
                onUploadSuccess={() => setAppStep('success')}
              />
            ) : !hasCameraPermission ? (
              // 카메라 권한이 아직 없는 경우 권한 안내 화면으로 이동
              <View style={styles.permissionBox}>
                <Text style={styles.permissionTitle}>카메라 권한 필요</Text>
                <TouchableOpacity
                  style={styles.primaryButton}
                  onPress={handleAllowPermissions}
                >
                  <Text style={styles.primaryButtonText}>권한 다시 요청하기</Text>
                </TouchableOpacity>
              </View>
            ) : (
              // 카메라 뷰
              <View style={styles.cameraContainer}>
                <SmartCameraView
                  ref={cameraRef}
                  style={styles.camera}
                  facing={facing}
                >
                  <PanoramaGuide
                    orientation={orientation}
                    isRecording={isRecording}
                    onCalibrate={calibrateToCurrentPosition}
                    onResetZero={resetToAbsoluteZero}
                    isSimulated={isSimulated}
                    onToggleSimulated={() => setIsSimulated(!isSimulated)}
                    onSimulateChange={setSimulatedValues}
                    needsSensorPermission={needsExplicitPermission}
                    isSensorPermissionGranted={isSensorPermissionGranted}
                    onRequestSensorPermission={requestSensorPermission}
                    isSensorActive={isSensorActive}
                  />
                </SmartCameraView>
              </View>
            )}

            {/* 하단 컨트롤 패널 */}
            {scanMode !== 'upload' && hasCameraPermission && (
              <View style={styles.bottomControls}>
                <TouchableOpacity
                  style={styles.iconButton}
                  onPress={toggleCameraFacing}
                  disabled={isRecording}
                >
                  <Text style={styles.iconButtonText}>🔄</Text>
                </TouchableOpacity>

                {/* 촬영 버튼 */}
                <TouchableOpacity
                  style={[
                    styles.recordButtonOuter,
                    isRecording && styles.recordingOuter,
                  ]}
                  onPress={handleToggleRecord}
                  activeOpacity={0.7}
                >
                  <View
                    style={[
                      styles.recordButtonInner,
                      isRecording && styles.recordingInner,
                    ]}
                  />
                </TouchableOpacity>

                <TouchableOpacity
                  style={styles.iconButton}
                  onPress={calibrateToCurrentPosition}
                >
                  <Text style={styles.iconButtonText}>🎯</Text>
                </TouchableOpacity>
              </View>
            )}
          </>
        )}

        {/* ========================================================= */}
        {/* 4단계: 촬영 직후 미리보기 및 업로드 확인 */}
        {/* ========================================================= */}
        {appStep === 'recorded_preview' && (
          <View style={styles.previewContainer}>
            <View style={styles.previewCard}>
              <Text style={styles.previewTitle}>촬영 영상 확인</Text>
              <Text style={styles.previewSubtitle}>
                수평 스캔이 정상적으로 완료되었습니다.{'\n'}
                영상을 확인하고 전송 버튼을 눌러주세요.
              </Text>

              <View style={styles.videoPlayerBox}>
                {Platform.OS === 'web' && recordedPreviewUrl ? (
                  // @ts-ignore
                  <video
                    src={recordedPreviewUrl}
                    controls
                    playsInline
                    style={{
                      width: '100%',
                      height: 220,
                      backgroundColor: '#000',
                      borderRadius: 12,
                      objectFit: 'contain',
                    }}
                  />
                ) : (
                  <View style={styles.placeholderBox}>
                    <Text style={styles.placeholderText}>촬영 영상 준비됨</Text>
                  </View>
                )}
              </View>

              <View style={styles.statRow}>
                <View style={styles.statItem}>
                  <Text style={styles.statLabel}>촬영 시간</Text>
                  <Text style={styles.statValue}>{recordSeconds}초</Text>
                </View>
                <View style={styles.statItem}>
                  <Text style={styles.statLabel}>수평 유지율</Text>
                  <Text style={styles.statHighlight}>
                    {recordSeconds > 0
                      ? Math.min(
                          100,
                          Math.round((levelDurationCount / recordSeconds) * 100)
                        )
                      : 100}
                    %
                  </Text>
                </View>
              </View>

              {uploadError && (
                <View style={styles.errorBox}>
                  <Text style={styles.errorText}>⚠️ {uploadError}</Text>
                </View>
              )}

              {isUploading && (
                <View style={styles.uploadingBox}>
                  <Text style={styles.uploadingText}>{uploadStatusText}</Text>
                  <View style={styles.progressBarBg}>
                    <View
                      style={[
                        styles.progressBarFill,
                        { width: `${uploadProgress}%` },
                      ]}
                    />
                  </View>
                  <Text style={styles.progressPercentText}>{uploadProgress}%</Text>
                </View>
              )}

              <View style={styles.previewButtonGroup}>
                <TouchableOpacity
                  style={[
                    styles.primaryButton,
                    isUploading ? styles.disabledButton : null,
                  ]}
                  onPress={handleUploadRecordedVideo}
                  disabled={isUploading}
                  activeOpacity={0.8}
                >
                  {isUploading ? (
                    <ActivityIndicator color="#ffffff" />
                  ) : (
                    <Text style={styles.primaryButtonText}>
                      ☁️ 3D 스캔 영상 전송
                    </Text>
                  )}
                </TouchableOpacity>

                {!isUploading && (
                  <TouchableOpacity
                    style={styles.retakeButton}
                    onPress={handleRetake}
                    activeOpacity={0.8}
                  >
                    <Text style={styles.retakeButtonText}>🔄 다시 촬영하기</Text>
                  </TouchableOpacity>
                )}
              </View>
            </View>
          </View>
        )}

        {/* ========================================================= */}
        {/* 5단계: 영상 전송 완료 안내 화면 */}
        {/* ========================================================= */}
        {appStep === 'success' && sessionId && (
          <UploadSuccessView
            sessionId={sessionId}
            onReset={handleResetAll}
          />
        )}
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
    backgroundColor: '#090d16',
    position: 'relative',
    overflow: 'hidden',
  },
  header: {
    paddingTop: Platform.OS === 'android' ? 36 : 14,
    paddingHorizontal: 16,
    paddingBottom: 10,
    backgroundColor: '#090d16',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 255, 255, 0.08)',
    zIndex: 10,
  },
  headerTopRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  headerTitle: {
    color: '#ffffff',
    fontSize: 18,
    fontWeight: '800',
    letterSpacing: -0.3,
  },
  sessionBadge: {
    color: '#38bdf8',
    fontSize: 11,
    fontWeight: '600',
    marginTop: 2,
  },
  recBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(239, 68, 68, 0.2)',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: 'rgba(239, 68, 68, 0.5)',
  },
  recDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#ef4444',
    marginRight: 6,
  },
  recTimeText: {
    color: '#ef4444',
    fontSize: 12,
    fontWeight: '700',
  },
  modeTabs: {
    flexDirection: 'row',
    backgroundColor: 'rgba(15, 23, 42, 0.8)',
    borderRadius: 10,
    padding: 3,
  },
  modeTab: {
    flex: 1,
    paddingVertical: 7,
    alignItems: 'center',
    borderRadius: 8,
  },
  activeModeTab: {
    backgroundColor: '#1e293b',
  },
  modeTabText: {
    fontSize: 13,
    color: '#64748b',
    fontWeight: '600',
  },
  activeModeTabText: {
    color: '#38bdf8',
    fontWeight: '700',
  },
  cameraContainer: {
    flex: 1,
    position: 'relative',
    backgroundColor: '#000',
  },
  camera: {
    flex: 1,
    width: '100%',
    height: '100%',
  },
  bottomControls: {
    position: 'absolute',
    bottom: 24,
    left: 0,
    right: 0,
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    paddingHorizontal: 24,
    zIndex: 20,
  },
  iconButton: {
    width: 46,
    height: 46,
    borderRadius: 23,
    backgroundColor: 'rgba(15, 23, 42, 0.75)',
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.1)',
  },
  iconButtonText: {
    fontSize: 20,
  },
  recordButtonOuter: {
    width: 74,
    height: 74,
    borderRadius: 37,
    borderWidth: 4,
    borderColor: '#ffffff',
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'transparent',
  },
  recordingOuter: {
    borderColor: '#ef4444',
  },
  recordButtonInner: {
    width: 58,
    height: 58,
    borderRadius: 29,
    backgroundColor: '#ef4444',
  },
  recordingInner: {
    width: 30,
    height: 30,
    borderRadius: 6,
  },
  permissionBox: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
    backgroundColor: '#090d16',
  },
  permissionIconCircle: {
    width: 68,
    height: 68,
    borderRadius: 34,
    backgroundColor: 'rgba(56, 189, 248, 0.12)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  permissionEmoji: {
    fontSize: 32,
  },
  pairSuccessTag: {
    backgroundColor: 'rgba(16, 185, 129, 0.15)',
    borderRadius: 20,
    paddingHorizontal: 12,
    paddingVertical: 4,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: 'rgba(16, 185, 129, 0.3)',
  },
  pairSuccessTagText: {
    color: '#34d399',
    fontSize: 12,
    fontWeight: '700',
  },
  permissionTitle: {
    fontSize: 20,
    fontWeight: '800',
    color: '#ffffff',
    marginBottom: 8,
  },
  permissionDesc: {
    fontSize: 13,
    color: '#94a3b8',
    textAlign: 'center',
    lineHeight: 18,
    marginBottom: 20,
  },
  highlightText: {
    color: '#38bdf8',
    fontWeight: '700',
  },
  errorAlertBox: {
    width: '100%',
    backgroundColor: 'rgba(239, 68, 68, 0.15)',
    borderRadius: 10,
    padding: 10,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: 'rgba(239, 68, 68, 0.3)',
  },
  errorAlertText: {
    color: '#f87171',
    fontSize: 12,
    textAlign: 'center',
  },
  warningBox: {
    backgroundColor: 'rgba(234, 179, 8, 0.1)',
    borderRadius: 10,
    padding: 10,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: 'rgba(234, 179, 8, 0.3)',
  },
  warningText: {
    color: '#fbbf24',
    fontSize: 12,
    lineHeight: 16,
    textAlign: 'center',
  },
  primaryButton: {
    width: '100%',
    height: 48,
    backgroundColor: '#0284c7',
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 10,
  },
  primaryButtonText: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '700',
  },
  fallbackButton: {
    width: '100%',
    height: 44,
    backgroundColor: 'transparent',
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.15)',
  },
  fallbackButtonText: {
    color: '#94a3b8',
    fontSize: 13,
    fontWeight: '600',
  },
  previewContainer: {
    flex: 1,
    padding: 20,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#090d16',
  },
  previewCard: {
    width: '100%',
    maxWidth: 380,
    backgroundColor: 'rgba(30, 41, 59, 0.85)',
    borderRadius: 20,
    borderWidth: 1.5,
    borderColor: 'rgba(56, 189, 248, 0.3)',
    padding: 20,
    alignItems: 'center',
  },
  previewTitle: {
    fontSize: 19,
    fontWeight: '800',
    color: '#ffffff',
    marginBottom: 6,
  },
  previewSubtitle: {
    fontSize: 13,
    color: '#94a3b8',
    textAlign: 'center',
    lineHeight: 18,
    marginBottom: 16,
  },
  videoPlayerBox: {
    width: '100%',
    borderRadius: 12,
    overflow: 'hidden',
    marginBottom: 14,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.1)',
  },
  placeholderBox: {
    width: '100%',
    height: 200,
    backgroundColor: '#000',
    justifyContent: 'center',
    alignItems: 'center',
  },
  placeholderText: {
    color: '#ffffff',
    fontSize: 14,
  },
  statRow: {
    flexDirection: 'row',
    width: '100%',
    backgroundColor: 'rgba(15, 23, 42, 0.6)',
    borderRadius: 12,
    padding: 12,
    marginBottom: 14,
  },
  statItem: {
    flex: 1,
    alignItems: 'center',
  },
  statLabel: {
    fontSize: 11,
    color: '#64748b',
    marginBottom: 2,
  },
  statValue: {
    fontSize: 14,
    color: '#e2e8f0',
    fontWeight: '700',
  },
  statHighlight: {
    fontSize: 14,
    color: '#34d399',
    fontWeight: '700',
  },
  errorBox: {
    width: '100%',
    backgroundColor: 'rgba(239, 68, 68, 0.15)',
    borderRadius: 8,
    padding: 8,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: 'rgba(239, 68, 68, 0.3)',
  },
  errorText: {
    color: '#f87171',
    fontSize: 12,
    textAlign: 'center',
  },
  uploadingBox: {
    width: '100%',
    marginBottom: 14,
    alignItems: 'center',
  },
  uploadingText: {
    color: '#38bdf8',
    fontSize: 12,
    fontWeight: '600',
    marginBottom: 6,
  },
  progressBarBg: {
    width: '100%',
    height: 8,
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
    borderRadius: 4,
    overflow: 'hidden',
    marginBottom: 4,
  },
  progressBarFill: {
    height: '100%',
    backgroundColor: '#38bdf8',
  },
  progressPercentText: {
    color: '#94a3b8',
    fontSize: 11,
    fontWeight: '700',
  },
  previewButtonGroup: {
    width: '100%',
    gap: 8,
  },
  disabledButton: {
    backgroundColor: '#334155',
    opacity: 0.6,
  },
  retakeButton: {
    width: '100%',
    height: 44,
    backgroundColor: 'transparent',
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.15)',
  },
  retakeButtonText: {
    color: '#94a3b8',
    fontSize: 14,
    fontWeight: '600',
  },
});
