const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const vm = require('node:vm')

test('detects the extension and acknowledges a publish handoff from the creation page', async () => {
  const sentToPage = []
  const sentToBackground = []
  let messageListener
  let runtimeListener
  let completedEvents = []
  const location = { origin: 'http://localhost:5173', pathname: '/share/xiaohongshu' }
  const window = {
    addEventListener(type, listener) { if (type === 'message') messageListener = listener },
    postMessage(message, targetOrigin) { sentToPage.push({ message, targetOrigin }) },
  }
  const chrome = {
    runtime: {
      getManifest() { return { version: '0.4.0' } },
      onMessage: { addListener(listener) { runtimeListener = listener } },
      async sendMessage(message) {
        sentToBackground.push(message)
        if (message.kind === 'GET_COMPLETED') return { ok: true, events: completedEvents }
        return { ok: true }
      },
    },
  }
  const source = fs.readFileSync(path.join(__dirname, '..', 'bridge.js'), 'utf8')
  vm.runInNewContext(source, { window, location, chrome })

  assert.equal(sentToPage[0].message.kind, 'READY')
  assert.equal(sentToPage[0].message.publicationSync, true)
  assert.equal(sentToPage[0].message.extensionVersion, '0.4.0')
  messageListener({
    source: window,
    origin: location.origin,
    data: { source: 'staranaly-xhs-app', kind: 'PING', requestId: 'ping' },
  })
  assert.equal(sentToPage.at(-1).message.kind, 'READY')
  assert.equal(sentToPage.at(-1).message.publicationSync, true)

  const draft = { title: '标题', body: '正文', imageData: 'data:image/png;base64,AA==' }
  await messageListener({
    source: window,
    origin: location.origin,
    data: { source: 'staranaly-xhs-app', kind: 'PREPARE', requestId: 'draft-1', draft },
  })
  assert.equal(sentToBackground.length, 2)
  assert.equal(sentToBackground[1].draft, draft)
  assert.equal(sentToPage.at(-1).message.kind, 'PREPARED')
  assert.equal(sentToPage.at(-1).message.requestId, 'draft-1')
  assert.equal(sentToPage.at(-1).message.ok, true)

  await messageListener({
    source: window,
    origin: 'http://other.example',
    data: { source: 'staranaly-xhs-app', kind: 'PREPARE', requestId: 'forged', draft },
  })
  assert.equal(sentToBackground.length, 2)

  let deliveryResponse
  runtimeListener({ kind: 'PUBLISHED', event: { id: 'event-1', noteId: 42, appOrigin: location.origin } },
    null, (response) => { deliveryResponse = response })
  assert.equal(deliveryResponse.ok, true)
  assert.equal(sentToPage.at(-1).message.kind, 'PUBLISHED')
  assert.equal(sentToPage.at(-1).message.noteId, 42)
  runtimeListener({ kind: 'PUBLISHED', event: { id: 'forged', noteId: 42, appOrigin: 'http://other.example' } },
    null, (response) => { deliveryResponse = response })
  assert.equal(deliveryResponse.ok, false)
  assert.equal(sentToPage.at(-1).message.requestId, 'event-1')

  await messageListener({
    source: window,
    origin: location.origin,
    data: { source: 'staranaly-xhs-app', kind: 'ACK_PUBLICATION', eventId: 'event-1' },
  })
  assert.equal(sentToBackground.at(-1).kind, 'ACK_COMPLETED')
  assert.equal(sentToBackground.at(-1).id, 'event-1')

  completedEvents = [{ id: 'queued-event', noteId: 43, appOrigin: location.origin }]
  await messageListener({
    source: window,
    origin: location.origin,
    data: { source: 'staranaly-xhs-app', kind: 'PING', requestId: 'reopen' },
  })
  assert.equal(sentToPage.at(-1).message.kind, 'PUBLISHED')
  assert.equal(sentToPage.at(-1).message.noteId, 43)

  location.pathname = '/share/xiaohongshu/records'
  runtimeListener({ kind: 'PUBLISHED', event: { id: 'event-2', noteId: 44, appOrigin: location.origin } },
    null, (response) => { deliveryResponse = response })
  assert.equal(deliveryResponse.ok, true)
  assert.equal(sentToPage.at(-1).message.noteId, 44)
  await messageListener({
    source: window,
    origin: location.origin,
    data: { source: 'staranaly-xhs-app', kind: 'ACK_PUBLICATION', eventId: 'event-2' },
  })
  assert.equal(sentToBackground.at(-1).kind, 'ACK_COMPLETED')
  const sentCount = sentToBackground.length
  await messageListener({
    source: window,
    origin: location.origin,
    data: { source: 'staranaly-xhs-app', kind: 'PREPARE', requestId: 'from-records', draft },
  })
  assert.equal(sentToBackground.length, sentCount)
})
