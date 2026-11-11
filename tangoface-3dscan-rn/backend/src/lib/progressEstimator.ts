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
 * 경과 초(elapsedSeconds)를 기준으로 예상 남은 시간, 진행률(%), 안내 단계를 계산
 */
export function calculateProcessingProgressFromElapsed(
  elapsedSecondsInput: number,
  estimatedTotalSeconds = DEFAULT_ESTIMATED_DURATION_SECONDS
): ProcessingProgressInfo {
  const elapsedSeconds = Math.max(0, Math.floor(elapsedSecondsInput));
  const ratio = elapsedSeconds / estimatedTotalSeconds;

  let progressPercent: number;

  if (ratio < 0.95) {
    progressPercent = Math.round(ratio * 100);
  } else {
    const overtime = elapsedSeconds - estimatedTotalSeconds * 0.95;
    const asymptoticBonus = (1 - Math.exp(-overtime / 15)) * 4;
    progressPercent = Math.min(99, Math.round(95 + asymptoticBonus));
  }

  const remainingSeconds = Math.max(2, Math.round(estimatedTotalSeconds - elapsedSeconds));

  const matchedStage = PROCESSING_STAGES.find(
    (s) => ratio >= s.minRatio && ratio < s.maxRatio
  );
  const stageText = matchedStage ? matchedStage.label : '마무리 검증 및 업로드 준비 중...';

  return {
    elapsed_seconds: elapsedSeconds,
    estimated_total_seconds: estimatedTotalSeconds,
    remaining_seconds: remainingSeconds,
    progress_percent: progressPercent,
    stage: stageText,
  };
}

/**
 * 시작 시각(ISO)을 기준으로 경과 시간, 예상 남은 시간, 진행률(%), 안내 단계를 계산
 */
export function calculateProcessingProgress(
  startedAtIso: string,
  estimatedTotalSeconds = DEFAULT_ESTIMATED_DURATION_SECONDS
): ProcessingProgressInfo {
  const startedAt = new Date(startedAtIso).getTime();
  const elapsedMs = Math.max(0, Date.now() - startedAt);
  return calculateProcessingProgressFromElapsed(
    Math.floor(elapsedMs / 1000),
    estimatedTotalSeconds
  );
}
