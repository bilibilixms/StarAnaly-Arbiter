(() => {
  const appSource = 'staranaly-xhs-app'
  const extensionSource = 'staranaly-xhs-extension'
  const extensionVersion = chrome.runtime.getManifest().version

  function isWorkspacePage() {
    return ['/share/xiaohongshu', '/share/xiaohongshu/records']
      .includes(location.pathname.replace(/\/$/, ''))
  }

  function reply(kind, requestId, extra = {}) {
    window.postMessage({ source: extensionSource, kind, requestId, ...extra }, location.origin)
  }

  function forwardPublication(event) {
    if (!isWorkspacePage() || event?.appOrigin !== location.origin
        || typeof event.id !== 'string' || !Number.isSafeInteger(event.noteId)) return false
    reply('PUBLISHED', event.id, { noteId: event.noteId })
    return true
  }

  async function replayPublications() {
    const result = await chrome.runtime.sendMessage({ kind: 'GET_COMPLETED' }).catch(() => null)
    if (result?.ok) for (const event of result.events || []) forwardPublication(event)
  }

  chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (message?.kind === 'PUBLISHED') sendResponse({ ok: forwardPublication(message.event) })
  })

  window.addEventListener('message', async (event) => {
    if (event.source !== window || event.origin !== location.origin || !isWorkspacePage()) return
    const message = event.data
    if (!message || message.source !== appSource) return

    if (message.kind === 'PING') {
      reply('READY', message.requestId, { publicationSync: true, extensionVersion })
      await replayPublications()
      return
    }
    if (message.kind === 'ACK_PUBLICATION' && typeof message.eventId === 'string') {
      await chrome.runtime.sendMessage({ kind: 'ACK_COMPLETED', id: message.eventId })
      return
    }
    if (message.kind !== 'PREPARE' || typeof message.requestId !== 'string') return
    if (location.pathname.replace(/\/$/, '') !== '/share/xiaohongshu') return

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

  if (isWorkspacePage()) reply('READY', undefined, { publicationSync: true, extensionVersion })
})()
