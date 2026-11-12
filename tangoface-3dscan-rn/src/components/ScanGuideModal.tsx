import React, { useState, useEffect, useRef } from 'react';
import {
  StyleSheet,
  Text,
  View,
  Modal,
  TouchableOpacity,
  ScrollView,
  Platform,
  NativeSyntheticEvent,
  NativeScrollEvent,
} from 'react-native';

interface ScanGuideModalProps {
  visible: boolean;
  onClose: () => void;
}

const guideVideoAsset = require('../../assets/3dModelScanGuide.mp4');
const guideVideoUri: string | undefined =
  typeof guideVideoAsset === 'string'
    ? guideVideoAsset
    : guideVideoAsset?.uri ?? guideVideoAsset?.default;

/**
 * 1번 일러스트: 카메라는 최대한 흔들리지 않게 곡선으로 천천히 움직여주세요
 * - 중앙 2.5D 동그란 얼굴 (심플한 눈코입 선)
 * - 좌측에 작은 직사각형 카메라
 * - 우측으로 약 120도 이동하는 완전한 원호 형태의 점선 화살표
 */
const CameraCurveGuideIcon = () => {
  if (Platform.OS === 'web') {
    return (
      // @ts-ignore
      <svg width="220" height="122" viewBox="0 0 220 122" fill="none">
        {/* 1. 중앙 2.5D 구형 얼굴 (중심 110, 42) */}
        {/* @ts-ignore */}
        <ellipse cx="110" cy="42" rx="22" ry="23" stroke="#FFFFFF" strokeWidth="2.5" fill="rgba(255, 255, 255, 0.04)" />
        {/* 2.5D 입체감을 주는 미세 경선/위도 곡선 */}
        {/* @ts-ignore */}
        <path d="M 110 19 Q 120 42 110 65" stroke="rgba(255, 255, 255, 0.2)" strokeWidth="1" strokeDasharray="2 2" />
        {/* @ts-ignore */}
        <path d="M 88 42 Q 110 48 132 42" stroke="rgba(255, 255, 255, 0.2)" strokeWidth="1" strokeDasharray="2 2" />

        {/* 심플한 눈, 코, 입 선 */}
        {/* @ts-ignore */}
        <circle cx="102" cy="38" r="2" fill="#FFFFFF" />
        {/* @ts-ignore */}
        <circle cx="118" cy="38" r="2" fill="#FFFFFF" />
        {/* @ts-ignore */}
        <path d="M 110 40 L 108 45 L 112 45" stroke="#FF8794" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        {/* @ts-ignore */}
        <path d="M 104 50 Q 110 54 116 50" stroke="#FFFFFF" strokeWidth="1.5" strokeLinecap="round" />

        {/* 2. 반지름 R=60 진원(Circle) 테두리 120도 원호 점선 (좌 -60° -> 우 +60°) */}
        {/* 중심 (110, 42), 반지름 60 => (58, 72) 에서 (162, 72) 까지 120° 완벽한 원호 */}
        {/* @ts-ignore */}
        <path
          d="M 58 72 A 60 60 0 0 0 162 72"
          stroke="#FF0055"
          strokeWidth="2.5"
          strokeDasharray="5 4"
          strokeLinecap="round"
        />

        {/* 원호 우측 끝 화살표 촉 (접선 방향) */}
        {/* @ts-ignore */}
        <path
          d="M 154 73 L 162 72 L 163 80"
          stroke="#FF0055"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="none"
        />

        {/* 3. 좌측 시작 지점의 작은 직사각형 카메라 (얼굴 중심을 향해 30도 회전) */}
        {/* @ts-ignore */}
        <g transform="translate(42, 54) rotate(30)">
          {/* 카메라 본체 직사각형 */}
          {/* @ts-ignore */}
          <rect x="0" y="0" width="16" height="24" rx="3" stroke="#FF0055" strokeWidth="2" fill="#1C191A" />
          {/* 렌즈 포인트 */}
          {/* @ts-ignore */}
          <circle cx="8" cy="6" r="3" stroke="#00FFCC" strokeWidth="1.5" fill="#161214" />
          {/* 셔터 라인 */}
          {/* @ts-ignore */}
          <line x1="3" y1="15" x2="13" y2="15" stroke="#FF516F" strokeWidth="1.5" opacity="0.6" />
        </g>

        {/* 4. 원호 하단 120° 라벨 배지 */}
        {/* @ts-ignore */}
        <g transform="translate(94, 94)">
          {/* @ts-ignore */}
          <rect x="0" y="0" width="32" height="16" rx="8" fill="#161214" stroke="#FF0055" strokeWidth="1" />
          {/* @ts-ignore */}
          <text x="16" y="11" fill="#FF8794" fontSize="9.5" fontWeight="700" textAnchor="middle" fontFamily="sans-serif">
            100°
          </text>
        </g>
      </svg>
    );
  }
  return <View style={styles.fallbackGraphicBox} />;
};

/**
 * 2번 일러스트: 스캔 할 때는 눈을 깜빡이지 마세요!
 * 크게 뜬 또렷한 눈과 시선 고정 집중 라인
 */
const EyeGuideIcon = () => {
  if (Platform.OS === 'web') {
    return (
      // @ts-ignore
      <svg width="120" height="72" viewBox="0 0 120 72" fill="none">
        {/* 눈 주변 집중 타겟 가이드라인 */}
        {/* @ts-ignore */}
        <circle cx="60" cy="36" r="32" stroke="#FF516F" strokeWidth="1" strokeDasharray="3 3" opacity="0.4" />
        {/* 눈 위/아래 윤곽선 */}
        {/* @ts-ignore */}
        <path d="M 22 36 Q 60 12 98 36" stroke="#FFFFFF" strokeWidth="2.5" strokeLinecap="round" />
        {/* @ts-ignore */}
        <path d="M 22 36 Q 60 60 98 36" stroke="#FFFFFF" strokeWidth="2.5" strokeLinecap="round" />
        {/* 홍채 (accent neon) */}
        {/* @ts-ignore */}
        <circle cx="60" cy="36" r="14" stroke="#FF0055" strokeWidth="2.5" fill="rgba(255, 0, 85, 0.15)" />
        {/* 동공 */}
        {/* @ts-ignore */}
        <circle cx="60" cy="36" r="6.5" fill="#FFFFFF" />

      </svg>
    );
  }
  return <View style={styles.fallbackGraphicBox} />;
};

/**
 * 3번 일러스트: 몸을 최대한 움직이지 마세요
 * 정면 고정된 얼굴/상체 실루엣과 흔들림 방지 브래킷/락 프레임
 */
const BodyStillGuideIcon = () => {
  if (Platform.OS === 'web') {
    return (
      // @ts-ignore
      <svg width="120" height="72" viewBox="0 0 120 72" fill="none">
        {/* 포커스 고정 사각 코너 브래킷 (Locked Frame) */}
        {/* @ts-ignore */}
        
        {/* @ts-ignore */}
        <circle cx="60" cy="27" r="13" stroke="#FFFFFF" strokeWidth="2.5" fill="rgba(255, 255, 255, 0.05)" />
        {/* 어깨 / 상체 실루엣 */}
        {/* @ts-ignore */}
        <path d="M 38 60 Q 60 45 82 60" stroke="#FFFFFF" strokeWidth="2.5" strokeLinecap="round" />
        {/* 좌우 흔들림 방지 표시 */}
        {/* @ts-ignore */}
        <path d="M 16 36 L 22 36" stroke="#FF516F" strokeWidth="1.5" strokeLinecap="round" />
        {/* @ts-ignore */}
        <path d="M 18 33 L 15 36 L 18 39" stroke="#FF516F" strokeWidth="1.5" strokeLinecap="round" />
        {/* @ts-ignore */}
        <path d="M 104 36 L 98 36" stroke="#FF516F" strokeWidth="1.5" strokeLinecap="round" />
        {/* @ts-ignore */}
        <path d="M 102 33 L 105 36 L 102 39" stroke="#FF516F" strokeWidth="1.5" strokeLinecap="round" />
        {/* 중앙 수직 고정 기준선 */}
        {/* @ts-ignore */}
        <line x1="60" y1="10" x2="60" y2="62" stroke="#FF0055" strokeWidth="1" strokeDasharray="3 3" opacity="0.6" />
      </svg>
    );
  }
  return <View style={styles.fallbackGraphicBox} />;
};

export const ScanGuideModal: React.FC<ScanGuideModalProps> = ({
  visible,
  onClose,
}) => {
  const hasVideoStep = Platform.OS === 'web' && Boolean(guideVideoUri);
  const totalSteps = hasVideoStep ? 4 : 3;

  const [currentStep, setCurrentStep] = useState(0);
  const [hasReachedEnd, setHasReachedEnd] = useState(false);
  const [cardWidth, setCardWidth] = useState(0);

  const scrollViewRef = useRef<React.ComponentRef<typeof ScrollView> | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);

  const isLastStep = currentStep >= totalSteps - 1;

  // 모달이 열릴 때마다 첫 카드(영상)로 초기화
  useEffect(() => {
    if (visible) {
      setCurrentStep(0);
      setHasReachedEnd(false);
      if (typeof scrollViewRef.current?.scrollTo === 'function') {
        scrollViewRef.current.scrollTo({ x: 0, animated: false });
      }
    } else if (Platform.OS === 'web' && videoRef.current) {
      videoRef.current.pause();
    }
  }, [visible]);

  // 영상 카드(0번)가 보일 때만 자동 재생, 벗어나면 일시정지
  useEffect(() => {
    if (Platform.OS !== 'web' || !videoRef.current) return;
    if (visible && hasVideoStep && currentStep === 0) {
      videoRef.current.currentTime = 0;
      videoRef.current.muted = true;
      videoRef.current.play().catch(() => {});
    } else {
      videoRef.current.pause();
    }
  }, [visible, hasVideoStep, currentStep]);

  const goToStep = (nextStep: number) => {
    const clamped = Math.max(0, Math.min(totalSteps - 1, nextStep));
    setCurrentStep(clamped);
    if (clamped >= totalSteps - 1) {
      setHasReachedEnd(true);
    }
    if (cardWidth > 0 && typeof scrollViewRef.current?.scrollTo === 'function') {
      scrollViewRef.current.scrollTo({ x: clamped * cardWidth, animated: true });
    }
  };

  const handleHorizontalScroll = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    if (cardWidth <= 0) return;
    const offsetX = event.nativeEvent.contentOffset.x;
    const index = Math.round(offsetX / cardWidth);
    const clamped = Math.max(0, Math.min(totalSteps - 1, index));
    if (clamped !== currentStep) {
      setCurrentStep(clamped);
    }
    if (clamped >= totalSteps - 1 && !hasReachedEnd) {
      setHasReachedEnd(true);
    }
  };

  const showConfirmButton = isLastStep || hasReachedEnd;

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={() => {
        if (showConfirmButton) onClose();
      }}
    >
      <View style={styles.overlay}>
        <View style={styles.modalCard}>
          {/* 모달 헤더 */}
          <View style={styles.header}>
            <Text style={styles.mainTitle}>
              높은 품질의 3D 얼굴 스캔 결과를{'\n'}확인하고 싶다면?
            </Text>
          </View>

          {/* 가로 스와이프 / 버튼 넘김 카드 영역 */}
          <View
            style={styles.carouselWrapper}
            onLayout={(e) => {
              const w = Math.round(e.nativeEvent.layout.width);
              if (w > 0 && w !== cardWidth) {
                setCardWidth(w);
              }
            }}
          >
            <ScrollView
              ref={scrollViewRef}
              horizontal
              pagingEnabled
              showsHorizontalScrollIndicator={false}
              scrollEventThrottle={16}
              onScroll={handleHorizontalScroll}
              onMomentumScrollEnd={handleHorizontalScroll}
              style={styles.carouselScroll}
              contentContainerStyle={styles.carouselContent}
            >
              {/* 0번 카드: 가이드 영상 자동 재생 */}
              {hasVideoStep ? (
                <View style={[styles.slidePage, cardWidth > 0 && { width: cardWidth }]}>
                  <View style={styles.videoCard}>
                    <View style={styles.videoWrapper}>
                      {/* @ts-ignore - React Native Web HTML5 video */}
                      <video
                        ref={videoRef}
                        src={guideVideoUri}
                        autoPlay
                        loop
                        muted
                        playsInline
                        controls
                        preload="auto"
                        style={{
                          width: '100%',
                          height: '100%',
                          backgroundColor: '#000000',
                          objectFit: 'contain',
                          display: 'block',
                        }}
                      />
                    </View>
                    <View style={styles.videoCaptionContainer}>
                      <Text style={styles.videoCaptionText}>
                        촬영 예시 영상을 참고해 주세요
                      </Text>
                    </View>
                  </View>
                </View>
              ) : null}

              {/* 1번 카드: 카메라 곡선 이동 */}
              <View style={[styles.slidePage, cardWidth > 0 && { width: cardWidth }]}>
                <View style={styles.guideItemCard}>
                  <View style={styles.iconContainerWide}>
                    <CameraCurveGuideIcon />
                  </View>
                  <Text style={styles.guideItemText}>
                    1. 카메라는 최대한 흔들리지 않게{'\n'}곡선으로 천천히 움직여주세요
                  </Text>
                </View>
              </View>

              {/* 2번 카드: 눈 깜빡임 방지 */}
              <View style={[styles.slidePage, cardWidth > 0 && { width: cardWidth }]}>
                <View style={styles.guideItemCard}>
                  <View style={styles.iconContainer}>
                    <EyeGuideIcon />
                  </View>
                  <Text style={styles.guideItemText}>
                    2. 스캔 할 때는 눈을 깜빡이지 마세요!
                  </Text>
                </View>
              </View>

              {/* 3번 카드: 몸 움직임 방지 */}
              <View style={[styles.slidePage, cardWidth > 0 && { width: cardWidth }]}>
                <View style={styles.guideItemCard}>
                  <View style={styles.iconContainer}>
                    <BodyStillGuideIcon />
                  </View>
                  <Text style={styles.guideItemText}>
                    3. 몸을 최대한 움직이지 마세요
                  </Text>
                </View>
              </View>
            </ScrollView>
          </View>

          {/* 페이지 인디케이터 점 */}
          <View style={styles.paginationRow}>
            {Array.from({ length: totalSteps }).map((_, idx) => {
              const active = idx === currentStep;
              return (
                <TouchableOpacity
                  key={idx}
                  onPress={() => goToStep(idx)}
                  hitSlop={{ top: 8, bottom: 8, left: 6, right: 6 }}
                  style={[styles.dot, active && styles.dotActive]}
                />
              );
            })}
          </View>

          {/* 마지막 카드(3번)까지 넘기면 바로 확인했습니다 버튼 표시 */}
          <View style={styles.bottomActions}>
            {showConfirmButton ? (
              <TouchableOpacity
                style={styles.confirmButton}
                onPress={onClose}
                activeOpacity={0.85}
              >
                <Text style={styles.confirmButtonText}>확인했습니다</Text>
              </TouchableOpacity>
            ) : (
              <View style={styles.navRow}>
                {currentStep > 0 ? (
                  <TouchableOpacity
                    style={styles.prevButton}
                    onPress={() => goToStep(currentStep - 1)}
                    activeOpacity={0.85}
                  >
                    <Text style={styles.prevButtonText}>이전</Text>
                  </TouchableOpacity>
                ) : null}
                <TouchableOpacity
                  style={styles.nextButton}
                  onPress={() => goToStep(currentStep + 1)}
                  activeOpacity={0.85}
                >
                  <Text style={styles.nextButtonText}>
                    다음 ({currentStep + 1}/{totalSteps})
                  </Text>
                </TouchableOpacity>
              </View>
            )}
          </View>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: '#161214',
    zIndex: 9999,
  },
  modalCard: {
    flex: 1,
    width: '100%',
    height: '100%',
    backgroundColor: '#161214',
    paddingTop: 16,
    paddingBottom: 20,
    alignItems: 'center',
  },
  header: {
    width: '100%',
    paddingHorizontal: 20,
    marginBottom: 12,
    alignItems: 'center',
  },
  mainTitle: {
    fontSize: 18,
    fontWeight: '800',
    color: '#ffffff',
    textAlign: 'center',
    lineHeight: 25,
    letterSpacing: -0.3,
  },
  carouselWrapper: {
    flex: 1,
    width: '100%',
  },
  carouselScroll: {
    flex: 1,
    width: '100%',
  },
  carouselContent: {
    height: '100%',
  },
  slidePage: {
    width: '100%',
    height: '100%',
    paddingHorizontal: 16,
    justifyContent: 'center',
    alignItems: 'center',
  },
  videoCard: {
    flex: 1,
    width: '100%',
    maxWidth: 440,
    backgroundColor: '#000000',
    borderRadius: 20,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.1)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  videoWrapper: {
    flex: 1,
    width: '100%',
    backgroundColor: '#000000',
    alignItems: 'center',
    justifyContent: 'center',
  },
  videoCaptionContainer: {
    width: '100%',
    backgroundColor: '#1E191C',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255, 255, 255, 0.06)',
  },
  videoCaptionText: {
    fontSize: 14,
    fontWeight: '700',
    color: '#FFFFFF',
    textAlign: 'center',
    letterSpacing: -0.2,
  },
  guideItemCard: {
    flex: 1,
    width: '100%',
    maxWidth: 440,
    backgroundColor: '#1C191A',
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.1)',
    paddingVertical: 24,
    paddingHorizontal: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  paginationRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    marginTop: 14,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#3A3134',
  },
  dotActive: {
    width: 20,
    backgroundColor: '#FF0055',
  },
  iconContainer: {
    height: 76,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 8,
  },
  iconContainerWide: {
    height: 124,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 8,
  },
  guideItemText: {
    fontSize: 14,
    fontWeight: '700',
    color: '#FFFFFF',
    textAlign: 'center',
    lineHeight: 20,
  },
  fallbackGraphicBox: {
    width: 60,
    height: 40,
    backgroundColor: 'rgba(255, 0, 85, 0.1)',
    borderRadius: 8,
  },
  bottomActions: {
    width: '100%',
    paddingHorizontal: 16,
    marginTop: 14,
  },
  confirmButton: {
    width: '100%',
    height: 48,
    backgroundColor: '#FF0055',
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  confirmButtonText: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '700',
  },
  navRow: {
    width: '100%',
    flexDirection: 'row',
    gap: 10,
  },
  prevButton: {
    flex: 1,
    height: 48,
    backgroundColor: '#2B2326',
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  prevButtonText: {
    color: '#CFC6C9',
    fontSize: 15,
    fontWeight: '600',
  },
  nextButton: {
    flex: 2,
    height: 48,
    backgroundColor: '#FF0055',
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  nextButtonText: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '700',
  },
});
