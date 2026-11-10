// src/lib/progressEstimator.ts
import { ProcessingProgressInfo } from '@/types/api';

/**
 * 3D 얼굴 복원 처리의 기본 예상 소요 시간 (초 단위)
 * 영상 길이 15~30초 기준 일반적인 GPU 인퍼런스 시간
 */
export const DEFAULT_ESTIMATED_DURATION_SECONDS = 45;

/**
 * 단계별 안내 문구 매핑 (경과 비율 기준)
 */
const PROCESSING_STAGES = [
  { minRatio: 0.0, maxRatio: 0.3, label: '1단계: 얼굴 특징점 및 프레임 정렬 중...' },
  { minRatio: 0.3, maxRatio: 0.7, label: '2단계: 고밀도 3D 메쉬 및 포인트클라우드 복원 중...' },
  { minRatio: 0.7, maxRatio: 0.95, label: '3단계: 텍스처 매핑 및 GLB 3D 모델 패키징 중...' },
  { minRatio: 0.95, maxRatio: 1.0, label: '마무리 검증 및 업로드 준비 중...' },
];

/**
 * 시작 시각을 기준으로 경과 시간, 예상 남은 시간, 진행률(%), 안내 단계를 계산
 * - 완료 직전까지 멈춤 없이 부드럽게 98~99%까지 점근(asymptotic curve)하도록 처리하여
 *   예상 시간을 조금 넘어가도 앱이 멈춘 것처럼 보이지 않게 방어합니다.
 */
export function calculateProcessingProgress(
  startedAtIso: string,
  estimatedTotalSeconds = DEFAULT_ESTIMATED_DURATION_SECONDS
): ProcessingProgressInfo {
  const startedAt = new Date(startedAtIso).getTime();
  const now = Date.now();
  const elapsedMs = Math.max(0, now - startedAt);
  const elapsedSeconds = Math.floor(elapsedMs / 1000);

  // 예상 진행 비율 (0.0 ~ 1.0+)
  const ratio = elapsedSeconds / estimatedTotalSeconds;

  let progressPercent: number;
  let stageText: string;

  if (ratio < 0.95) {
    // 정상 구간: 시간에 비례하여 0% ~ 95%까지 선형 증가
    progressPercent = Math.round(ratio * 100);
  } else {
    // 예상 시간을 초과하거나 근접했을 때: 95% ~ 99% 구간을 지수 감쇠로 부드럽게 대기
    // elapsed가 더 커져도 99%를 넘지 않음 (DONE 웹훅이 와야 100%가 됨)
    const overtime = elapsedSeconds - (estimatedTotalSeconds * 0.95);
    const asymptoticBonus = (1 - Math.exp(-overtime / 15)) * 4; // 최대 4% 추가 (95 + 4 = 99%)
    progressPercent = Math.min(99, Math.round(95 + asymptoticBonus));
  }

  // 남은 시간 계산
  const remainingSeconds = Math.max(2, Math.round(estimatedTotalSeconds - elapsedSeconds));

  // 단계 라벨 선택
  const matchedStage = PROCESSING_STAGES.find(
    (s) => ratio >= s.minRatio && ratio < s.maxRatio
  );
  stageText = matchedStage ? matchedStage.label : '마무리 검증 및 업로드 준비 중...';

  return {
    elapsed_seconds: elapsedSeconds,
    estimated_total_seconds: estimatedTotalSeconds,
    remaining_seconds: remainingSeconds,
    progress_percent: progressPercent,
    stage: stageText,
  };
}
