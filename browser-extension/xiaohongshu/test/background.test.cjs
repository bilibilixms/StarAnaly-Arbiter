const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const vm = require('node:vm')

function createExtension() {
  const values = new Map()
  const openedTabs = []
  const tabMessages = []
  let messageListener
  let alarmListener
  let removedTabListener
  let nextTabId = 10
  const chrome = {
    storage: {
      local: {
        async set(entries) { for (const [key, value] of Object.entries(entries)) values.set(key, value) },
        async get(key) { return { [key]: values.get(key) } },
        async remove(key) { values.delete(key) },
      },
    },
    alarms: {
      async create() {},
      async clear() {},
      onAlarm: { addListener(listener) { alarmListener = listener } },
    },
    tabs: {
      async create(options) {
        const tab = { id: nextTabId++, url: options.url }
        openedTabs.push(tab)
        return tab
      },
      async update(id, options) {
        const tab = openedTabs.find((item) => item.id === id)
        tab.url = options.url
        return tab
      },
      async remove(id) {
        const index = openedTabs.findIndex((item) => item.id === id)
        if (index >= 0) openedTabs.splice(index, 1)
      },
      async sendMessage(id, message) { tabMessages.push({ id, message }) },
      onRemoved: { addListener(listener) { removedTabListener = listener } },
    },
    runtime: { onMessage: { addListener(listener) { messageListener = listener } } },
  }
  const source = fs.readFileSync(path.join(__dirname, '..', 'background.js'), 'utf8')
  vm.runInNewContext(source, { chrome, URL, crypto: require('node:crypto').webcrypto })

  function send(message, sender) {
    return new Promise((resolve, reject) => {
      if (messageListener(message, sender, resolve) !== true) reject(new Error('消息未处理'))
    })
  }
  return { send, openedTabs, tabMessages, values, expire: () => alarmListener({ name: 'pendingXiaohongshuDraftExpiry' }), closeTab: (id) => removedTabListener(id) }
}

const imageData = 'data:image/png;base64,AA=='
const draft = {
  noteId: 42,
  title: '周末随拍',
  body: '晴天出门走走。\n\n#周末记录',
  imageName: 'photo.png',
  imageType: 'image/png',
  imageData,
}
const app = { url: 'http://localhost:5173/share/xiaohongshu', tab: { id: 1 } }

test('hands the image and text only to the newly opened creator tab', async () => {
  const extension = createExtension()
  const prepared = await extension.send({ kind: 'PREPARE', draft }, app)
  assert.equal(prepared.ok, true)
  assert.equal(extension.openedTabs.length, 1)
  assert.match(extension.openedTabs[0].url, /^https:\/\/creator\.xiaohongshu\.com\/publish\/publish/)

  const wrongTab = await extension.send({ kind: 'GET_DRAFT' }, {
    url: extension.openedTabs[0].url,
    tab: { id: 99 },
  })
  assert.equal(wrongTab.ok, false)

  const creator = { url: extension.openedTabs[0].url, tab: { id: extension.openedTabs[0].id } }
  const pending = await extension.send({ kind: 'GET_DRAFT' }, creator)
  assert.equal(pending.ok, true)
  assert.equal(pending.draft.body, draft.body)
  assert.equal(pending.draft.imageData, imageData)

  assert.equal((await extension.send({ kind: 'CLEAR_DRAFT' }, creator)).ok, true)
  assert.equal((await extension.send({ kind: 'GET_DRAFT' }, creator)).ok, false)
})

test('rejects unrelated pages and invalid drafts', async () => {
  const extension = createExtension()
  const unrelatedPage = { url: 'http://localhost:5173/tasks', tab: { id: 1 } }
  assert.equal((await extension.send({ kind: 'PREPARE', draft }, unrelatedPage)).ok, false)
  assert.equal((await extension.send({ kind: 'PREPARE', draft: { ...draft, title: '字'.repeat(21) } }, app)).ok, false)
  assert.equal(extension.openedTabs.length, 0)
  assert.equal(extension.values.has('pendingXiaohongshuDraft'), false)
})

test('clears temporarily stored content when the target tab closes or the timer expires', async () => {
  const extension = createExtension()
  await extension.send({ kind: 'PREPARE', draft }, app)
  assert.equal(extension.values.has('pendingXiaohongshuDraft'), true)
  await extension.closeTab(extension.openedTabs[0].id)
  assert.equal(extension.values.has('pendingXiaohongshuDraft'), false)
  assert.equal(Object.keys(extension.values.get('xiaohongshuPublications')).length, 0)

  await extension.send({ kind: 'PREPARE', draft }, app)
  extension.expire()
  assert.equal(extension.values.has('pendingXiaohongshuDraft'), false)
})

test('records success only after the tracked creator tab attempts publication', async () => {
  const extension = createExtension()
  await extension.send({ kind: 'PREPARE', draft }, app)
  const creator = { url: extension.openedTabs[0].url, tab: { id: extension.openedTabs[0].id } }
  const wrongCreator = { url: creator.url, tab: { id: 999 } }

  assert.equal((await extension.send({ kind: 'PUBLISH_SUCCEEDED' }, creator)).ok, false)
  assert.equal((await extension.send({ kind: 'PUBLISH_ATTEMPTED' }, wrongCreator)).ok, false)
  assert.equal((await extension.send({ kind: 'CLEAR_DRAFT' }, creator)).ok, true)
  assert.equal((await extension.send({ kind: 'PUBLISH_ATTEMPTED' }, creator)).ok, true)
  assert.equal((await extension.send({ kind: 'PUBLISH_SUCCEEDED' }, creator)).ok, true)
  assert.equal((await extension.send({ kind: 'PUBLISH_SUCCEEDED' }, creator)).ok, false)

  const completed = await extension.send({ kind: 'GET_COMPLETED' }, app)
  assert.equal(completed.ok, true)
  assert.equal(completed.events.length, 1)
  assert.equal(completed.events[0].noteId, draft.noteId)
  assert.equal(extension.tabMessages[0].id, app.tab.id)
  assert.equal(extension.tabMessages[0].message.kind, 'PUBLISHED')
  const otherApp = { url: 'http://127.0.0.1:5173/share/xiaohongshu', tab: { id: 2 } }
  assert.equal((await extension.send({ kind: 'GET_COMPLETED' }, otherApp)).events.length, 0)
  assert.equal((await extension.send({ kind: 'ACK_COMPLETED', id: completed.events[0].id }, otherApp)).ok, false)
  assert.equal((await extension.send({ kind: 'ACK_COMPLETED', id: completed.events[0].id }, app)).ok, true)
  assert.equal((await extension.send({ kind: 'GET_COMPLETED' }, app)).events.length, 0)
})

test('closing the creator tab removes publication tracking', async () => {
  const extension = createExtension()
  await extension.send({ kind: 'PREPARE', draft }, app)
  const creator = { url: extension.openedTabs[0].url, tab: { id: extension.openedTabs[0].id } }
  await extension.closeTab(creator.tab.id)
  assert.equal((await extension.send({ kind: 'PUBLISH_ATTEMPTED' }, creator)).ok, false)
  assert.equal((await extension.send({ kind: 'GET_COMPLETED' }, app)).events.length, 0)
})
