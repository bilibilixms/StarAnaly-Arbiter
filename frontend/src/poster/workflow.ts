import type { PosterChecks, PosterJob, PosterJobImage, PosterReview } from '@/api/posters'

export function checks(batch: PosterJob): PosterChecks {
  try { return JSON.parse(batch.checksJson) as PosterChecks }
  catch { return { requiredText: '', forbiddenContent: '', logoName: '', logoIds: [] } }
}

export function review(image: PosterJobImage): PosterReview {
  try { return { requiredText: false, forbiddenContent: false, logo: false,
    ...JSON.parse(image.reviewJson) as Partial<PosterReview> } }
  catch { return { requiredText: false, forbiddenContent: false, logo: false } }
}

export function reviewComplete(batch: PosterJob, image: PosterJobImage): boolean {
  const state = review(image)
  const requirements = checks(batch)
  return state.requiredText && (!requirements.forbiddenContent || state.forbiddenContent)
    && (!requirements.logoName || (image.logoApplied && state.logo))
}

export function restoredMaterialIds(snapshotJson: string, availableIds: number[]): number[] {
  try {
    const snapshot = JSON.parse(snapshotJson) as Array<{ id: number }>
    return snapshot.map((item) => item.id).filter((id) => availableIds.includes(id))
  } catch { return [] }
}
