import assert from 'node:assert/strict'
import test from 'node:test'
import type { PosterJob, PosterJobImage } from '../api/posters.ts'
import { checks, restoredMaterialIds, reviewComplete } from './workflow.ts'

function savedJob(image: PosterJobImage): PosterJob {
  return {
    id: 42, status: 'SUCCEEDED', direction: '新品海报', advice: '', requestedCount: 1,
    materialSnapshot: '[{"id":8},{"id":9}]',
    checksJson: JSON.stringify({ requiredText: '10月26日', forbiddenContent: '永久免费',
      logoName: '品牌标识', logoIds: [8] }),
    error: null, selected: [0], images: [image], createdAt: '2026-10-26T10:00:00',
  }
}

test('刷新后恢复的海报仍需完成所有约束核对才能交给发布页', () => {
  const image: PosterJobImage = {
    id: 77, position: 0, name: 'poster.png', mime: 'image/png', logoApplied: false,
    reviewJson: JSON.stringify({ requiredText: true, forbiddenContent: true, logo: true }),
  }
  const batch = savedJob(image)
  assert.equal(reviewComplete(batch, image), false, '未叠加原始 Logo 时不能通过')
  image.logoApplied = true
  assert.equal(reviewComplete(batch, image), true)
  image.reviewJson = JSON.stringify({ requiredText: true, forbiddenContent: false, logo: true })
  assert.equal(reviewComplete(batch, image), false, '禁用内容未核对时不能通过')
})

test('历史版本保留要求，并且只恢复仍存在的素材选择', () => {
  const image: PosterJobImage = { id: 1, position: 0, name: 'a.png', mime: 'image/png',
    logoApplied: false, reviewJson: '{}' }
  const batch = savedJob(image)
  assert.equal(checks(batch).requiredText, '10月26日')
  assert.deepEqual(restoredMaterialIds(batch.materialSnapshot, [9, 10]), [9])
})
