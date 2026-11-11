import React, { useState } from 'react';
import {
  StyleSheet,
  Text,
  View,
  TouchableOpacity,
  Platform,
  ActivityIndicator,
} from 'react-native';
import {
  pickVideoWithDurationLimit,
  SelectedVideoInfo,
} from '../utils/videoPicker';
import {
  getPresignedUploadUrl,
  uploadVideoDirectly,
  completeUpload,
} from '../config/api';
import { ServerStatusBadge } from './ServerStatusBadge';

interface VideoUploadViewProps {
  pairToken: string;
  onUploadSuccess: (queueFull?: boolean) => void;
}

export const VideoUploadView: React.FC<VideoUploadViewProps> = ({
  pairToken,
  onUploadSuccess,
}) => {
  const [selectedVideo, setSelectedVideo] = useState<SelectedVideoInfo | null>(
    null
  );
  const [isLoading, setIsLoading] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadStatusText, setUploadStatusText] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handlePickVideo = async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const video = await pickVideoWithDurationLimit();
      if (video) {
        setSelectedVideo(video);
      }
    } catch {
      alert('영상 선택을 완료하지 못했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleReset = () => {
    setSelectedVideo(null);
    setErrorMessage(null);
    setUploadProgress(0);
  };

  // 영상 업로드 파이프라인 실행:
  // 1) getPresignedUploadUrl -> 2) uploadVideoDirectly (Storage 직접 PUT) -> 3) completeUpload
  const handleStartUpload = async () => {
    if (!selectedVideo || !selectedVideo.file) {
      alert('업로드할 영상 파일이 유효하지 않습니다.');
      return;
    }

    setIsUploading(true);
    setErrorMessage(null);
    setUploadProgress(0);

    try {
      // 1. Signed URL 요청
      setUploadStatusText('1/3 업로드 URL 발급 중...');
      const presignRes = await getPresignedUploadUrl(pairToken);
      if (!presignRes.success || !presignRes.upload_url) {
        throw new Error(presignRes.error || '서명된 업로드 URL을 받지 못했습니다.');
      }

      // 2. Supabase Storage로 직접 PUT 전송
      setUploadStatusText('2/3 Supabase 스토리지로 영상 직접 전송 중...');
      const uploadRes = await uploadVideoDirectly(
        presignRes.upload_url,
        selectedVideo.file,
        (percent) => {
          setUploadProgress(percent);
        }
      );

      if (!uploadRes.success) {
        throw new Error(uploadRes.error || '영상 파일 전송에 실패했습니다.');
      }

      // 3. 서버에 완료 보고 및 검증
      setUploadStatusText('3/3 파일 검증 및 상태 확정 중...');
      const completeRes = await completeUpload(pairToken);
      if (!completeRes.success) {
        throw new Error(completeRes.error || '업로드 검증에 실패했습니다.');
      }

      // 성공 완료 알림
      onUploadSuccess(completeRes.queueFull);
    } catch (err: any) {
      setErrorMessage(err.message || '업로드 처리 중 오류가 발생했습니다.');
    } finally {
      setIsUploading(false);
      setUploadStatusText('');
    }
  };

  return (
    <View style={styles.container}>
      {!selectedVideo ? (
        // 파일 선택 전 UI
        <View style={styles.emptyCard}>
          <Text style={styles.mainTitle}>3D 스캔 동영상 파일 첨부</Text>

          {/* 파일 제약조건 안내 태그 */}
          <View style={styles.badgeRow}>
            <View style={styles.conditionBadge}>
              <Text style={styles.badgeText}>최대 20초 이내</Text>
            </View>
            <View style={styles.conditionBadge}>
              <Text style={styles.badgeText}>최대 50MB 이내</Text>
            </View>
          </View>

          <TouchableOpacity
            style={styles.selectButton}
            onPress={handlePickVideo}
            disabled={isLoading}
            activeOpacity={0.8}
          >
            {isLoading ? (
              <ActivityIndicator color="#ffffff" />
            ) : (
              <Text style={styles.selectButtonText}>비디오 파일 선택하기</Text>
            )}
          </TouchableOpacity>
        </View>
      ) : (
        // 파일 선택 완료 후 미리보기 & 정보 UI
        <View style={styles.previewCard}>
          <Text style={styles.previewTitle}>첨부된 영상 확인</Text>

          {/* 웹 비디오 미리보기 */}
          <View style={styles.videoPlayerContainer}>
            {Platform.OS === 'web' ? (
              // @ts-ignore - React Native Web supports video element
              <video
                src={selectedVideo.uri}
                controls
                playsInline
                style={{
                  width: '100%',
                  height: 200,
                  backgroundColor: '#000',
                  borderRadius: 12,
                  objectFit: 'contain',
                }}
              />
            ) : (
              <View style={styles.nativeVideoPlaceholder}>
                <Text style={styles.nativeVideoText}>{selectedVideo.name}</Text>
              </View>
            )}
          </View>

          {/* 영상 메타데이터 정보 카드 */}
          <View style={styles.infoTable}>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>파일명</Text>
              <Text style={styles.infoValue} numberOfLines={1}>
                {selectedVideo.name}
              </Text>
            </View>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>재생 시간</Text>
              <Text style={styles.infoHighlight}>
                {selectedVideo.durationSec}초 (기준 충족)
              </Text>
            </View>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>파일 크기</Text>
              <Text style={styles.infoValue}>{selectedVideo.sizeMB} MB</Text>
            </View>
          </View>

          {/* Colab 3D 변환 서버 가동 상태 (10초 주기 자동 체크) */}
          <ServerStatusBadge paused={isUploading} />

          {/* 에러 메시지 */}
          {errorMessage && (
            <View style={styles.errorBox}>
              <Text style={styles.errorText}>{errorMessage}</Text>
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

          {/* 액션 버튼 */}
          <View style={styles.buttonGroup}>
            <TouchableOpacity
              style={[
                styles.actionPrimaryButton,
                isUploading ? styles.disabledButton : null,
              ]}
              onPress={handleStartUpload}
              disabled={isUploading}
              activeOpacity={0.8}
            >
              {isUploading ? (
                <ActivityIndicator color="#ffffff" />
              ) : (
                <Text style={styles.actionPrimaryText}>스토리지로 영상 전송</Text>
              )}
            </TouchableOpacity>

            {!isUploading && (
              <TouchableOpacity
                style={styles.actionSecondaryButton}
                onPress={handleReset}
              >
                <Text style={styles.actionSecondaryText}>다른 영상 선택</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>
      )}
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
  emptyCard: {
    width: '100%',
    maxWidth: 360,
    backgroundColor: 'rgba(28, 25, 26, 0.85)',
    borderRadius: 20,
    padding: 24,
    alignItems: 'center',
  },
  mainTitle: {
    fontSize: 20,
    fontWeight: '800',
    color: '#ffffff',
    marginBottom: 16,
    textAlign: 'center',
  },
  badgeRow: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 24,
  },
  conditionBadge: {
    backgroundColor: '#161214',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 8,
  },
  badgeText: {
    fontSize: 12,
    color: '#FF8794',
    fontWeight: '600',
  },
  selectButton: {
    width: '100%',
    height: 48,
    backgroundColor: '#FF0055',
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  selectButtonText: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '700',
  },
  previewCard: {
    width: '100%',
    maxWidth: 380,
    backgroundColor: 'rgba(28, 25, 26, 0.9)',
    borderRadius: 20,
    padding: 20,
    alignItems: 'center',
  },
  previewTitle: {
    fontSize: 18,
    fontWeight: '800',
    color: '#ffffff',
    marginBottom: 14,
  },
  videoPlayerContainer: {
    width: '100%',
    borderRadius: 12,
    overflow: 'hidden',
    marginBottom: 14,
  },
  nativeVideoPlaceholder: {
    width: '100%',
    height: 180,
    backgroundColor: '#000',
    justifyContent: 'center',
    alignItems: 'center',
  },
  nativeVideoText: {
    color: '#ffffff',
    fontSize: 14,
  },
  infoTable: {
    width: '100%',
    backgroundColor: '#161214',
    borderRadius: 12,
    padding: 12,
    marginBottom: 14,
  },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 5,
  },
  infoLabel: {
    fontSize: 12,
    color: '#9E9C9C',
  },
  infoValue: {
    fontSize: 12,
    color: '#DCD9D9',
    fontWeight: '600',
    maxWidth: '65%',
  },
  infoHighlight: {
    fontSize: 12,
    color: '#00FFCC',
    fontWeight: '700',
  },
  errorBox: {
    width: '100%',
    backgroundColor: 'rgba(255, 84, 73, 0.15)',
    borderRadius: 8,
    padding: 8,
    marginBottom: 12,
  },
  errorText: {
    color: '#FF5449',
    fontSize: 12,
    textAlign: 'center',
  },
  uploadingBox: {
    width: '100%',
    marginBottom: 14,
    alignItems: 'center',
  },
  uploadingText: {
    color: '#FF8794',
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
    backgroundColor: '#FF0055',
  },
  progressPercentText: {
    color: '#9E9C9C',
    fontSize: 11,
    fontWeight: '700',
  },
  buttonGroup: {
    width: '100%',
    gap: 8,
  },
  actionPrimaryButton: {
    width: '100%',
    height: 48,
    backgroundColor: '#FF0055',
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  actionPrimaryText: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '700',
  },
  disabledButton: {
    backgroundColor: '#30292B',
    opacity: 0.6,
  },
  actionSecondaryButton: {
    width: '100%',
    height: 44,
    backgroundColor: 'rgba(255, 255, 255, 0.08)',
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  actionSecondaryText: {
    color: '#9E9C9C',
    fontSize: 14,
    fontWeight: '600',
  },
});
