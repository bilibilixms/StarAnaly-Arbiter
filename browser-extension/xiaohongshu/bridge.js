(() => {
  const appSource = 'staranaly-xhs-app'
  const extensionSource = 'staranaly-xhs-extension'

  function isCreationPage() {
    return location.pathname.replace(/\/$/, '') === '/share/xiaohongshu'
  }

  function reply(kind, requestId, extra = {}) {
    window.postMessage({ source: extensionSource, kind, requestId, ...extra }, location.origin)
  }

  window.addEventListener('message', async (event) => {
    if (event.source !== window || event.origin !== location.origin || !isCreationPage()) return
    const message = event.data
    if (!message || message.source !== appSource) return

    if (message.kind === 'PING') {
      reply('READY', message.requestId)
      return
    }
    if (message.kind !== 'PREPARE' || typeof message.requestId !== 'string') return

    try {
      const result = await chrome.runtime.sendMessage({
        kind: 'PREPARE',
        requestId: message.requestId,
        draft: message.draft,
      })
      reply('PREPARED', message.requestId, result || { ok: false, error: '扩展没有返回结果。' })
    } catch (error) {
      reply('PREPARED', message.requestId, {
        ok: false,
        error: error instanceof Error ? error.message : '无法连接小红书发布扩展。',
      })
    }
  })

  if (isCreationPage()) reply('READY')
})()
