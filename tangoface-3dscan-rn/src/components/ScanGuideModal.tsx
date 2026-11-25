import React, { useState, useEffect, useRef } from 'react';
import {
  StyleSheet,
  Text,
  View,
  Modal,
  TouchableOpacity,
  ScrollView,
  Platform,
  Image,
  NativeSyntheticEvent,
  NativeScrollEvent,
} from 'react-native';

interface ScanGuideModalProps {
  visible: boolean;
  onClose: () => void;
}

const guideGifAsset = require('../../assets/3dModelScanGuide.gif');
const guideGifUri: string | undefined =
  typeof guideGifAsset === 'string'
    ? guideGifAsset
    : guideGifAsset?.uri ?? guideGifAsset?.default;

/**
 * 1번 일러스트: 뒤집어진 'ㄷ'자 모양 2패스 왕복 촬영 가이드
 * - 상단: 좌측 -> 우측 (1차: 눈높이 패스)
 * - 우측: 아래로 하강 전환
 * - 하단: 우측 -> 좌측 (2차: 턱선/목 패스)
 * 전체적으로 '뒤집어진 ㄷ자(⊐)' 모양의 왕복 궤적
 */
const CameraCurveGuideIcon = () => {
  if (Platform.OS === 'web') {
    return (
      // @ts-ignore
      <svg width="240" height="136" viewBox="0 0 240 136" fill="none">
        {/* 1. 중앙 2.5D 구형 얼굴 (중심 120, 54) */}
        {/* 머리 외곽 */}
        {/* @ts-ignore */}
        <ellipse cx="120" cy="54" rx="23" ry="25" stroke="#FFFFFF" strokeWidth="2.5" fill="rgba(255, 255, 255, 0.04)" />
        {/* 2.5D 입체감 경선/위도선 */}
        {/* @ts-ignore */}
        <path d="M 120 29 Q 131 54 120 79" stroke="rgba(255, 255, 255, 0.18)" strokeWidth="1" strokeDasharray="2 2" />
        {/* @ts-ignore */}
        <path d="M 97 54 Q 120 61 143 54" stroke="rgba(255, 255, 255, 0.18)" strokeWidth="1" strokeDasharray="2 2" />

        {/* 눈, 코, 입 선 */}
        {/* @ts-ignore */}
        <circle cx="112" cy="50" r="2" fill="#FFFFFF" />
        {/* @ts-ignore */}
        <circle cx="128" cy="50" r="2" fill="#FFFFFF" />
        {/* @ts-ignore */}
        <path d="M 120 52 L 118 57 L 122 57" stroke="#FF8794" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        {/* @ts-ignore */}
        <path d="M 114 62 Q 120 66 126 62" stroke="#FFFFFF" strokeWidth="1.5" strokeLinecap="round" />

        {/* 턱 & 목선 */}
        {/* @ts-ignore */}
        <path d="M 112 79 L 112 88 L 128 88 L 128 79" stroke="rgba(255, 255, 255, 0.3)" strokeWidth="1.5" strokeLinecap="round" />

        {/* 2. 뒤집어진 'ㄷ'자 왕복 궤적 (1차 상단: 좌 -> 우) */}
        {/* @ts-ignore */}
        <path
          d="M 52 38 Q 120 50 188 38"
          stroke="#FF0055"
          strokeWidth="2.5"
          strokeDasharray="5 3"
          strokeLinecap="round"
        />
        {/* 상단 우측 화살표 (오른쪽 방향) */}
        {/* @ts-ignore */}
        <path
          d="M 180 32 L 188 38 L 180 44"
          stroke="#FF0055"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="none"
        />

        {/* 3. 우측 회전 & 하강 연결 궤적 (상단 -> 하단) */}
        {/* @ts-ignore */}
        <path
          d="M 188 38 Q 204 65 188 92"
          stroke="#FF8794"
          strokeWidth="2.5"
          strokeDasharray="4 3"
          strokeLinecap="round"
        />
        {/* 우측 하강 화살표 (아래 방향) */}
        {/* @ts-ignore */}
        <path
          d="M 194 65 L 189 72 L 184 65"
          stroke="#FF8794"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="none"
        />

        {/* 4. 뒤집어진 'ㄷ'자 왕복 궤적 (2차 하단: 우 -> 좌) */}
        {/* @ts-ignore */}
        <path
          d="M 188 92 Q 120 104 52 92"
          stroke="#00FFCC"
          strokeWidth="2.5"
          strokeDasharray="5 3"
          strokeLinecap="round"
        />
        {/* 하단 좌측 화살표 (왼쪽 방향) */}
        {/* @ts-ignore */}
        <path
          d="M 60 86 L 52 92 L 60 98"
          stroke="#00FFCC"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="none"
        />

        {/* 5. 시작 스마트폰 아이콘 (좌측 상단, 36, 26) */}
        {/* @ts-ignore */}
        <g transform="translate(36, 26) rotate(20)">
          {/* @ts-ignore */}
          <rect x="0" y="0" width="14" height="22" rx="3" stroke="#FF0055" strokeWidth="1.8" fill="#1C191A" />
          {/* @ts-ignore */}
          <circle cx="7" cy="5" r="2.2" stroke="#FF0055" strokeWidth="1.2" fill="#161214" />
        </g>
        {/* @ts-ignore */}
        <text x="26" y="38" fill="#FF8794" fontSize="9" fontWeight="800" textAnchor="end" fontFamily="sans-serif">
          시작
        </text>

        {/* 6. 완료 지점 (좌측 하단, 36, 80) */}
        {/* @ts-ignore */}
        <g transform="translate(36, 80) rotate(-20)">
          {/* @ts-ignore */}
          <rect x="0" y="0" width="14" height="22" rx="3" stroke="#00FFCC" strokeWidth="1.8" fill="#1C191A" />
          {/* @ts-ignore */}
          <circle cx="7" cy="5" r="2.2" stroke="#00FFCC" strokeWidth="1.2" fill="#161214" />
        </g>
        {/* @ts-ignore */}
        <text x="26" y="94" fill="#00FFCC" fontSize="9" fontWeight="800" textAnchor="end" fontFamily="sans-serif">
          완료
        </text>

        {/* 7. 상단 안내 라벨 (1차 상단 좌→우) */}
        {/* @ts-ignore */}
        <g transform="translate(74, 8)">
          {/* @ts-ignore */}
          <rect x="0" y="0" width="92" height="17" rx="8.5" fill="#1C191A" stroke="#FF0055" strokeWidth="1" />
          {/* @ts-ignore */}
          <text x="46" y="12" fill="#FF8794" fontSize="9" fontWeight="700" textAnchor="middle" fontFamily="sans-serif">
            1차: 상단 (좌 → 우)
          </text>
        </g>

        {/* 8. 하단 안내 라벨 (2차 하단 우→좌) */}
        {/* @ts-ignore */}
        <g transform="translate(74, 114)">
          {/* @ts-ignore */}
          <rect x="0" y="0" width="92" height="17" rx="8.5" fill="#1C191A" stroke="#00FFCC" strokeWidth="1" />
          {/* @ts-ignore */}
          <text x="46" y="12" fill="#00FFCC" fontSize="9" fontWeight="700" textAnchor="middle" fontFamily="sans-serif">
            2차: 하단 (우 → 좌)
          </text>
        </g>

        {/* 9. 우측 중앙 '뒤집어진 ㄷ자' 배지 */}
        {/* @ts-ignore */}
        <g transform="translate(198, 56)">
          {/* @ts-ignore */}
          <text x="0" y="0" fill="#E0DADB" fontSize="8" fontWeight="700" fontFamily="sans-serif">
            뒤집어진 ㄷ자
          </text>
          {/* @ts-ignore */}
          <text x="0" y="10" fill="#FF8794" fontSize="7.5" fontWeight="600" fontFamily="sans-serif">
            (왕복 회전)
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
  const hasMediaStep = Boolean(guideGifUri);
  const totalSteps = hasMediaStep ? 4 : 3;

  const [currentStep, setCurrentStep] = useState(0);
  const [hasReachedEnd, setHasReachedEnd] = useState(false);
  const [cardWidth, setCardWidth] = useState(0);

  const scrollViewRef = useRef<React.ComponentRef<typeof ScrollView> | null>(null);

  const isLastStep = currentStep >= totalSteps - 1;

  // 모달이 열릴 때마다 첫 카드(GIF 가이드)로 초기화
  useEffect(() => {
    if (visible) {
      setCurrentStep(0);
      setHasReachedEnd(false);
      if (typeof scrollViewRef.current?.scrollTo === 'function') {
        scrollViewRef.current.scrollTo({ x: 0, animated: false });
      }
    }
  }, [visible]);

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
      onRequestClose={onClose}
    >
      <View style={styles.overlay}>
        <View style={styles.modalCard}>
          {/* 모달 헤더 */}
          <View style={styles.header}>
            <Text style={styles.mainTitle}>
              높은 품질의 3D 얼굴 스캔 결과를{'\n'}확인하고 싶다면?
            </Text>
            <TouchableOpacity
              style={styles.closeHeaderButton}
              onPress={onClose}
              hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}
              activeOpacity={0.7}
            >
              <Text style={styles.closeHeaderButtonText}>✕</Text>
            </TouchableOpacity>
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
              {/* 0번 카드: 가이드 GIF 애니메이션 */}
              {hasMediaStep ? (
                <View style={[styles.slidePage, cardWidth > 0 && { width: cardWidth }]}>
                  <View style={styles.videoCard}>
                    <View style={styles.videoWrapper}>
                      {Platform.OS === 'web' ? (
                        // @ts-ignore - React Native Web HTML5 img tag for animated GIF
                        <img
                          src={guideGifUri}
                          alt="3D 얼굴 스캔 가이드 예시"
                          style={{
                            width: '100%',
                            height: '100%',
                            backgroundColor: '#000000',
                            objectFit: 'contain',
                            display: 'block',
                          }}
                        />
                      ) : (
                        <Image
                          source={guideGifAsset}
                          style={{
                            width: '100%',
                            height: '100%',
                            resizeMode: 'contain',
                          }}
                        />
                      )}
                    </View>
                    <View style={styles.videoCaptionContainer}>
                      <Text style={styles.videoCaptionText}>
                        촬영 예시 가이드를 참고해 주세요
                      </Text>
                    </View>
                  </View>
                </View>
              ) : null}

              {/* 1번 카드: 뒤집어진 'ㄷ'자 왕복 곡선 이동 */}
              <View style={[styles.slidePage, cardWidth > 0 && { width: cardWidth }]}>
                <View style={styles.guideItemCard}>
                  <View style={styles.iconContainerWide}>
                    <CameraCurveGuideIcon />
                  </View>
                  <Text style={styles.guideItemText}>
                    1. 상단 좌→우 촬영 후, 하단으로 내려와 우→좌로{'\n'}
                    뒤집어진 'ㄷ'자 모양으로 천천히 왕복해주세요
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
    position: 'relative',
    justifyContent: 'center',
  },
  closeHeaderButton: {
    position: 'absolute',
    top: 0,
    right: 16,
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: 'rgba(255, 255, 255, 0.08)',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 10,
  },
  closeHeaderButtonText: {
    color: '#DCD9D9',
    fontSize: 16,
    fontWeight: '700',
    lineHeight: 18,
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
    height: 140,
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
