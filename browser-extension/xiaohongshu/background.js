const DRAFT_KEY = 'pendingXiaohongshuDraft'
const PUBLISH_URL = 'https://creator.xiaohongshu.com/publish/publish?target=image'
const EXPIRY_ALARM = 'pendingXiaohongshuDraftExpiry'
const MAX_AGE_MS = 30 * 60 * 1000
const MAX_IMAGE_BYTES = 20_000_000

function isAppSender(sender) {
  try {
    const url = new URL(sender.url)
    return (url.hostname === 'localhost' || url.hostname === '127.0.0.1')
      && url.pathname.replace(/\/$/, '') === '/share/xiaohongshu'
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
  if (!isAppSender(sender) || !validateDraft(draft)) {
    return { ok: false, error: '图片或文案无效，请返回星析重新选择。' }
  }

  const tab = await chrome.tabs.create({ url: 'about:blank', active: true })
  if (tab.id == null) return { ok: false, error: '无法打开小红书标签页。' }

  try {
    await chrome.storage.local.set({
      [DRAFT_KEY]: {
        ...draft,
        targetTabId: tab.id,
        createdAt: Date.now(),
      },
    })
    await chrome.alarms.create(EXPIRY_ALARM, { delayInMinutes: 30 })
    await chrome.tabs.update(tab.id, { url: PUBLISH_URL })
    return { ok: true }
  } catch (error) {
    await chrome.storage.local.remove(DRAFT_KEY)
    await chrome.alarms.clear(EXPIRY_ALARM)
    await chrome.tabs.remove(tab.id).catch(() => {})
    return { ok: false, error: error instanceof Error ? error.message : '无法打开小红书发布页。' }
  }
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
  if (stored[DRAFT_KEY]?.targetTabId !== tabId) return
  await chrome.storage.local.remove(DRAFT_KEY)
  await chrome.alarms.clear(EXPIRY_ALARM)
})

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  let action
  if (message?.kind === 'PREPARE') action = prepareDraft(message.draft, sender)
  else if (message?.kind === 'GET_DRAFT') action = getDraft(sender)
  else if (message?.kind === 'CLEAR_DRAFT') action = clearDraft(sender)
  else return false

  action.then(sendResponse).catch((error) => {
    sendResponse({ ok: false, error: error instanceof Error ? error.message : '扩展处理失败。' })
  })
  return true
})
