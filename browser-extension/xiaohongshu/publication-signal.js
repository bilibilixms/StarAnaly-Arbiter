(() => {
  function normalize(value) {
    return typeof value === 'string' ? value.replace(/\s+/g, '').trim() : ''
  }

  function isPublishButtonText(value) {
    return /^(发布|立即发布|确认发布)$/.test(normalize(value))
  }

  function isSuccessText(value) {
    return /^(笔记|图文)?发布成功[！!。.]?$/.test(normalize(value))
  }

  globalThis.xiaohongshuPublishSignal = { isPublishButtonText, isSuccessText }
  if (typeof module !== 'undefined') module.exports = globalThis.xiaohongshuPublishSignal
})()
