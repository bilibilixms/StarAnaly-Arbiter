const assert = require('node:assert/strict')
const test = require('node:test')
const { isPublishButtonText, isSuccessText } = require('../publication-signal.js')

test('recognizes the final publish action, not navigation or scheduling', () => {
  assert.equal(isPublishButtonText('发布'), true)
  assert.equal(isPublishButtonText(' 立即发布 '), true)
  assert.equal(isPublishButtonText('确认发布'), true)
  assert.equal(isPublishButtonText('发布笔记'), false)
  assert.equal(isPublishButtonText('定时发布'), false)
  assert.equal(isPublishButtonText('取消发布'), false)
})

test('accepts an explicit success message, not a generic publish hint', () => {
  assert.equal(isSuccessText('发布成功！'), true)
  assert.equal(isSuccessText('笔记发布成功'), true)
  assert.equal(isSuccessText('发布中'), false)
  assert.equal(isSuccessText('发布失败'), false)
  assert.equal(isSuccessText('发布成功后即可查看数据'), false)
})
