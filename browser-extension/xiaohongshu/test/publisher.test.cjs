const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const vm = require('node:vm')
const signal = require('../publication-signal.js')
const topicFill = require('../topic-fill.js')

class FakeElement {
  constructor(textContent = '') {
    this.textContent = textContent
    this.children = []
    this.disabled = false
  }
  closest() { return this }
  getClientRects() { return [{}] }
  querySelectorAll() { return [] }
}

test('reports success only after a final publish click and a new success signal', async () => {
  const messages = []
  let clickListener
  let mutationListener
  const document = {
    documentElement: new FakeElement(),
    addEventListener(kind, listener) { if (kind === 'click') clickListener = listener },
  }
  const chrome = {
    runtime: {
      async sendMessage(message) {
        messages.push(message.kind)
        if (message.kind === 'GET_PUBLICATION') return { ok: true, attemptedAt: null }
        if (message.kind === 'GET_DRAFT') return { ok: false }
        if (message.kind === 'PUBLISH_SUCCEEDED') return { ok: false }
        return { ok: true }
      },
    },
  }
  class FakeObserver {
    constructor(listener) { mutationListener = listener }
    observe() {}
  }
  const source = fs.readFileSync(path.join(__dirname, '..', 'publisher.js'), 'utf8')
  vm.runInNewContext(source, {
    chrome, document, Element: FakeElement, HTMLElement: FakeElement,
    MutationObserver: FakeObserver, location: { pathname: '/publish/publish', href: 'https://creator.xiaohongshu.com/publish/publish' },
    setInterval() {}, globalThis: { xiaohongshuPublishSignal: signal, xiaohongshuTopicFill: topicFill },
  })
  await new Promise(setImmediate)

  mutationListener([{ type: 'childList', addedNodes: [new FakeElement('发布成功')] }])
  assert.equal(messages.includes('PUBLISH_SUCCEEDED'), false)

  clickListener({ target: new FakeElement('发布') })
  await new Promise(setImmediate)
  assert.equal(messages.includes('PUBLISH_ATTEMPTED'), true)

  mutationListener([{ type: 'childList', addedNodes: [new FakeElement('发布失败')] }])
  await new Promise(setImmediate)
  assert.equal(messages.includes('PUBLISH_SUCCEEDED'), false)

  mutationListener([{ type: 'childList', addedNodes: [new FakeElement('发布成功')] }])
  await new Promise(setImmediate)
  assert.equal(messages.filter((kind) => kind === 'PUBLISH_SUCCEEDED').length, 1)
})
