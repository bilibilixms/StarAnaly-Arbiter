(() => {
  if (location.pathname.replace(/\/$/, '') !== '/share/douyin') return

  const appSource = 'staranaly-douyin-app'
  const extensionSource = 'staranaly-douyin-extension'
  const extensionVersion = chrome.runtime.getManifest().version

  function reply(kind, requestId, extra = {}) {
    window.postMessage({ source: extensionSource, kind, requestId, ...extra }, location.origin)
  }

  window.addEventListener('message', async (event) => {
    if (event.source !== window || event.origin !== location.origin) return
    const message = event.data
    if (!message || message.source !== appSource) return

    if (message.kind === 'PING') {
      reply('READY', message.requestId, { extensionVersion })
      return
    }
    if (message.kind !== 'PREPARE' || typeof message.requestId !== 'string') return

    try {
      const result = await chrome.runtime.sendMessage({ kind: 'DY_PREPARE', draft: message.draft })
      reply('PREPARED', message.requestId, result || { ok: false, error: '扩展没有返回结果。' })
    } catch (error) {
      reply('PREPARED', message.requestId, {
        ok: false,
        error: error instanceof Error ? error.message : '无法连接抖音发布扩展。',
      })
    }
  })

  reply('READY', undefined, { extensionVersion })
})()
