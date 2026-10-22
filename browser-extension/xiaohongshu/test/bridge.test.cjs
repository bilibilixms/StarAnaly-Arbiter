const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const vm = require('node:vm')

test('detects the extension and acknowledges a publish handoff from the creation page', async () => {
  const sentToPage = []
  const sentToBackground = []
  let messageListener
  const location = { origin: 'http://localhost:5173', pathname: '/share/xiaohongshu' }
  const window = {
    addEventListener(type, listener) { if (type === 'message') messageListener = listener },
    postMessage(message, targetOrigin) { sentToPage.push({ message, targetOrigin }) },
  }
  const chrome = {
    runtime: {
      async sendMessage(message) {
        sentToBackground.push(message)
        return { ok: true }
      },
    },
  }
  const source = fs.readFileSync(path.join(__dirname, '..', 'bridge.js'), 'utf8')
  vm.runInNewContext(source, { window, location, chrome })

  assert.equal(sentToPage[0].message.kind, 'READY')
  messageListener({
    source: window,
    origin: location.origin,
    data: { source: 'staranaly-xhs-app', kind: 'PING', requestId: 'ping' },
  })
  assert.equal(sentToPage.at(-1).message.kind, 'READY')

  const draft = { title: '标题', body: '正文', imageData: 'data:image/png;base64,AA==' }
  await messageListener({
    source: window,
    origin: location.origin,
    data: { source: 'staranaly-xhs-app', kind: 'PREPARE', requestId: 'draft-1', draft },
  })
  assert.equal(sentToBackground.length, 1)
  assert.equal(sentToBackground[0].draft, draft)
  assert.equal(sentToPage.at(-1).message.kind, 'PREPARED')
  assert.equal(sentToPage.at(-1).message.requestId, 'draft-1')
  assert.equal(sentToPage.at(-1).message.ok, true)

  await messageListener({
    source: window,
    origin: 'http://other.example',
    data: { source: 'staranaly-xhs-app', kind: 'PREPARE', requestId: 'forged', draft },
  })
  assert.equal(sentToBackground.length, 1)
})
