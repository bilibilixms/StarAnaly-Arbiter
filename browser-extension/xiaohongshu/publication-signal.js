(() => {
  function normalize(value) {
    return typeof value === 'string' ? value.replace(/\s+/g, '').trim() : ''
  }

  function isSuccessText(value) {
    return /^(笔记|图文)?发布成功[！!。.]?$/.test(normalize(value))
  }

  function isSuccessUrl(value) {
    try {
      const url = new URL(value)
      return /^\/publish\/success(?:\/|$)/.test(url.pathname)
        || (url.pathname.startsWith('/publish/') && url.searchParams.get('published') === 'true')
    } catch {
      return false
    }
  }

  globalThis.xiaohongshuPublishSignal = { isSuccessText, isSuccessUrl }
  if (typeof module !== 'undefined') module.exports = globalThis.xiaohongshuPublishSignal
})()
