const DRAFT_KEY = 'pendingXiaohongshuDraft'
const PUBLICATIONS_KEY = 'xiaohongshuPublications'
const COMPLETED_KEY = 'completedXiaohongshuPublications'
const PUBLISH_URL = 'https://creator.xiaohongshu.com/publish/publish?target=image'
const EXPIRY_ALARM = 'pendingXiaohongshuDraftExpiry'
const MAX_AGE_MS = 30 * 60 * 1000
const MAX_IMAGE_BYTES = 20_000_000
const MAX_PUBLICATION_AGE_MS = 24 * 60 * 60 * 1000

function isAppSender(sender) {
  try {
    const url = new URL(sender.url)
    return (url.hostname === 'localhost' || url.hostname === '127.0.0.1')
      && ['/share/xiaohongshu', '/share/xiaohongshu/records']
        .includes(url.pathname.replace(/\/$/, ''))
      && sender.tab?.id != null
  } catch {
    return false
  }
}

function isCreatorSender(sender) {
  try {
    return new URL(sender.url).hostname === 'creator.xiaohongshu.com'
      && sender.tab?.id != null
  } catch {
    return false
  }
}

function validateDraft(draft) {
  if (!draft || typeof draft !== 'object') return false
  if (!Number.isSafeInteger(draft.noteId) || draft.noteId <= 0) return false
  if (typeof draft.title !== 'string' || !draft.title.trim() || Array.from(draft.title.trim()).length > 20) return false
  if (typeof draft.body !== 'string' || !draft.body.trim() || Array.from(draft.body.trim()).length > 1000) return false
  if (typeof draft.imageName !== 'string' || !draft.imageName || draft.imageName.length > 255) return false
  if (typeof draft.imageType !== 'string' || !['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(draft.imageType)) return false
  if (typeof draft.imageData !== 'string') return false
  const prefix = `data:${draft.imageType};base64,`
  if (!draft.imageData.startsWith(prefix)) return false
  const encodedLength = draft.imageData.length - prefix.length
  if (encodedLength <= 0 || encodedLength % 4 !== 0) return false
  const padding = draft.imageData.endsWith('==') ? 2 : draft.imageData.endsWith('=') ? 1 : 0
  return encodedLength / 4 * 3 - padding <= MAX_IMAGE_BYTES
}

async function prepareDraft(draft, sender) {
  if (!isAppSender(sender)
      || new URL(sender.url).pathname.replace(/\/$/, '') !== '/share/xiaohongshu'
      || !validateDraft(draft)) {
    return { ok: false, error: '图片或文案无效，请返回星析重新选择。' }
  }

  const tab = await chrome.tabs.create({ url: 'about:blank', active: true })
  if (tab.id == null) return { ok: false, error: '无法打开小红书标签页。' }

  try {
    const appOrigin = new URL(sender.url).origin
    await chrome.storage.local.set({
      [DRAFT_KEY]: {
        ...draft,
        targetTabId: tab.id,
        createdAt: Date.now(),
      },
    })
    const stored = await chrome.storage.local.get(PUBLICATIONS_KEY)
    await chrome.storage.local.set({
      [PUBLICATIONS_KEY]: {
        ...stored[PUBLICATIONS_KEY],
        [tab.id]: { noteId: draft.noteId, appOrigin, appTabId: sender.tab.id, createdAt: Date.now() },
      },
    })
    await chrome.alarms.create(EXPIRY_ALARM, { delayInMinutes: 30 })
    await chrome.tabs.update(tab.id, { url: PUBLISH_URL })
    return { ok: true }
  } catch (error) {
    await chrome.storage.local.remove(DRAFT_KEY)
    await chrome.alarms.clear(EXPIRY_ALARM)
    await removePublication(tab.id)
    await chrome.tabs.remove(tab.id).catch(() => {})
    return { ok: false, error: error instanceof Error ? error.message : '无法打开小红书发布页。' }
  }
}

async function getPublication(sender) {
  if (!isCreatorSender(sender)) return null
  const stored = await chrome.storage.local.get(PUBLICATIONS_KEY)
  const publication = stored[PUBLICATIONS_KEY]?.[sender.tab.id]
  if (!publication) return null
  if (Date.now() - publication.createdAt > MAX_PUBLICATION_AGE_MS) {
    await removePublication(sender.tab.id)
    return null
  }
  return publication
}

async function removePublication(tabId) {
  const stored = await chrome.storage.local.get(PUBLICATIONS_KEY)
  const publications = { ...stored[PUBLICATIONS_KEY] }
  delete publications[tabId]
  await chrome.storage.local.set({ [PUBLICATIONS_KEY]: publications })
}

async function publishAttempted(sender) {
  const publication = await getPublication(sender)
  if (!publication) return { ok: false }
  const stored = await chrome.storage.local.get(PUBLICATIONS_KEY)
  await chrome.storage.local.set({
    [PUBLICATIONS_KEY]: {
      ...stored[PUBLICATIONS_KEY],
      [sender.tab.id]: { ...publication, attemptedAt: Date.now(), attemptUrl: sender.url },
    },
  })
  return { ok: true }
}

async function publishSucceeded(sender) {
  const publication = await getPublication(sender)
  if (!publication?.attemptedAt || Date.now() - publication.attemptedAt > 10 * 60 * 1000) {
    return { ok: false }
  }
  const id = crypto.randomUUID()
  const event = { id, noteId: publication.noteId, appOrigin: publication.appOrigin }
  const stored = await chrome.storage.local.get(COMPLETED_KEY)
  await chrome.storage.local.set({
    [COMPLETED_KEY]: { ...stored[COMPLETED_KEY], [id]: event },
  })
  await removePublication(sender.tab.id)
  let delivered = false
  try {
    const response = await chrome.tabs.sendMessage(publication.appTabId, { kind: 'PUBLISHED', event })
    delivered = response?.ok === true
  } catch {
    // The original tab may have been closed or navigated away.
  }
  if (delivered) {
    try {
      const appTab = await chrome.tabs.update(publication.appTabId, { active: true })
      await chrome.windows.update(appTab.windowId, { focused: true })
      return { ok: true }
    } catch {
      // The app tab may have disappeared while switching back.
    }
  }
  const newTab = { url: `${publication.appOrigin}/share/xiaohongshu`, active: true }
  if (Number.isInteger(sender.tab.windowId)) newTab.windowId = sender.tab.windowId
  try {
    const appTab = await chrome.tabs.create(newTab)
    await chrome.windows.update(appTab.windowId, { focused: true })
  } catch {
    // The publication event remains queued for the next visit to StarAnaly.
  }
  return { ok: true }
}

async function getCompleted(sender) {
  if (!isAppSender(sender)) return { ok: false }
  const appOrigin = new URL(sender.url).origin
  const stored = await chrome.storage.local.get(COMPLETED_KEY)
  return { ok: true, events: Object.values(stored[COMPLETED_KEY] || {})
    .filter((event) => event.appOrigin === appOrigin) }
}

async function acknowledgeCompleted(id, sender) {
  if (!isAppSender(sender) || typeof id !== 'string') return { ok: false }
  const stored = await chrome.storage.local.get(COMPLETED_KEY)
  const events = { ...stored[COMPLETED_KEY] }
  if (events[id]?.appOrigin !== new URL(sender.url).origin) return { ok: false }
  delete events[id]
  await chrome.storage.local.set({ [COMPLETED_KEY]: events })
  return { ok: true }
}

async function getDraft(sender) {
  if (!isCreatorSender(sender)) return { ok: false }
  const stored = await chrome.storage.local.get(DRAFT_KEY)
  const draft = stored[DRAFT_KEY]
  if (!draft || draft.targetTabId !== sender.tab.id) return { ok: false }
  if (Date.now() - draft.createdAt > MAX_AGE_MS) {
    await chrome.storage.local.remove(DRAFT_KEY)
    await chrome.alarms.clear(EXPIRY_ALARM)
    return { ok: false, error: '待发布内容已过期，请返回星析重新打开。' }
  }
  return { ok: true, draft }
}

async function clearDraft(sender) {
  if (!isCreatorSender(sender)) return { ok: false }
  const stored = await chrome.storage.local.get(DRAFT_KEY)
  if (stored[DRAFT_KEY]?.targetTabId !== sender.tab.id) return { ok: false }
  await chrome.storage.local.remove(DRAFT_KEY)
  await chrome.alarms.clear(EXPIRY_ALARM)
  return { ok: true }
}

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === EXPIRY_ALARM) void chrome.storage.local.remove(DRAFT_KEY)
})

chrome.tabs.onRemoved.addListener(async (tabId) => {
  const stored = await chrome.storage.local.get(DRAFT_KEY)
  if (stored[DRAFT_KEY]?.targetTabId === tabId) {
    await chrome.storage.local.remove(DRAFT_KEY)
    await chrome.alarms.clear(EXPIRY_ALARM)
  }
  await removePublication(tabId)
})

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  let action
  if (message?.kind === 'PREPARE') action = prepareDraft(message.draft, sender)
  else if (message?.kind === 'GET_DRAFT') action = getDraft(sender)
  else if (message?.kind === 'CLEAR_DRAFT') action = clearDraft(sender)
  else if (message?.kind === 'GET_PUBLICATION') action = getPublication(sender).then((publication) =>
    ({ ok: !!publication, attemptedAt: publication?.attemptedAt || null,
      attemptUrl: publication?.attemptUrl || null }))
  else if (message?.kind === 'PUBLISH_ATTEMPTED') action = publishAttempted(sender)
  else if (message?.kind === 'PUBLISH_SUCCEEDED') action = publishSucceeded(sender)
  else if (message?.kind === 'GET_COMPLETED') action = getCompleted(sender)
  else if (message?.kind === 'ACK_COMPLETED') action = acknowledgeCompleted(message.id, sender)
  else return false

  action.then(sendResponse).catch((error) => {
    sendResponse({ ok: false, error: error instanceof Error ? error.message : '扩展处理失败。' })
  })
  return true
})
