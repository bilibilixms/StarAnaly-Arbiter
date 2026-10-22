const assert = require('node:assert/strict')
const test = require('node:test')
const { isSuccessText, isSuccessUrl } = require('../publication-signal.js')

test('accepts an explicit success message, not a generic publish hint', () => {
  assert.equal(isSuccessText('发布成功！'), true)
  assert.equal(isSuccessText('笔记发布成功'), true)
  assert.equal(isSuccessText('发布中'), false)
  assert.equal(isSuccessText('发布失败'), false)
  assert.equal(isSuccessText('发布成功后即可查看数据'), false)
})

test('recognizes creator success routes and published URL parameters', () => {
  assert.equal(isSuccessUrl('https://creator.xiaohongshu.com/publish/success'), true)
  assert.equal(isSuccessUrl('https://creator.xiaohongshu.com/publish/success/'), true)
  assert.equal(isSuccessUrl('https://creator.xiaohongshu.com/publish/publish?published=true'), true)
  assert.equal(isSuccessUrl('https://creator.xiaohongshu.com/publish/publish?target=image'), false)
})
