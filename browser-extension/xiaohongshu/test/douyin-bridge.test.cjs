const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const vm = require('node:vm')

test('Douyin bridge detects the extension and forwards only same-origin handoffs', async () => {
  const sentToPage = []
  const sentToBackground = []
  let listener
  const location = { origin: 'http://localhost:5173', pathname: '/share/douyin' }
  const window = {
    addEventListener(type, handler) { if (type === 'message') listener = handler },
    postMessage(message, targetOrigin) { sentToPage.push({ message, targetOrigin }) },
  }
  const chrome = {
    runtime: {
      getManifest() { return { version: '0.7.0' } },
      async sendMessage(message) { sentToBackground.push(message); return { ok: true } },
    },
  }
  const source = fs.readFileSync(path.join(__dirname, '..', 'douyin-bridge.js'), 'utf8')
  vm.runInNewContext(source, { window, location, chrome })

  assert.equal(sentToPage[0].message.kind, 'READY')
  assert.equal(sentToPage[0].message.extensionVersion, '0.7.0')
  await listener({ source: window, origin: location.origin,
    data: { source: 'staranaly-douyin-app', kind: 'PING', requestId: 'ping' } })
  assert.equal(sentToPage.at(-1).message.kind, 'READY')

  const draft = { title: '标题', body: '正文', images: [] }
  await listener({ source: window, origin: location.origin,
    data: { source: 'staranaly-douyin-app', kind: 'PREPARE', requestId: 'one', draft } })
  assert.equal(sentToBackground.length, 1)
  assert.equal(sentToBackground[0].kind, 'DY_PREPARE')
  assert.equal(sentToBackground[0].draft, draft)
  assert.equal(sentToPage.at(-1).message.kind, 'PREPARED')

  await listener({ source: window, origin: 'http://other.example',
    data: { source: 'staranaly-douyin-app', kind: 'PREPARE', requestId: 'forged', draft } })
  assert.equal(sentToBackground.length, 1)
})

test('Douyin bridge stays inactive on unrelated StarAnaly pages', () => {
  const source = fs.readFileSync(path.join(__dirname, '..', 'douyin-bridge.js'), 'utf8')
  let attached = false
  vm.runInNewContext(source, {
    location: { origin: 'http://localhost:5173', pathname: '/share/xiaohongshu' },
    window: { addEventListener() { attached = true } },
  })
  assert.equal(attached, false)
})
