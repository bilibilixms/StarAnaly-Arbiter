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
  getClientRects() { return [{}] }
  querySelectorAll() { return [] }
}

test('reports success only after a visible success signal', async () => {
  const messages = []
  let mutationListener
  const document = {
    documentElement: new FakeElement(),
  }
  const chrome = {
    runtime: {
      async sendMessage(message) {
        messages.push(message.kind)
        if (message.kind === 'GET_PUBLICATION') return { ok: true, filledAt: Date.now() }
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

  mutationListener([{ type: 'childList', addedNodes: [new FakeElement('发布失败')] }])
  assert.equal(messages.includes('PUBLISH_SUCCEEDED'), false)

  mutationListener([{ type: 'childList', addedNodes: [new FakeElement('发布成功')] }])
  await new Promise(setImmediate)
  assert.equal(messages.filter((kind) => kind === 'PUBLISH_SUCCEEDED').length, 1)
})

test('detects a short-lived creator success route even when the publish button is inside closed shadow DOM', async () => {
  const messages = []
  const intervals = []
  const location = { pathname: '/publish/publish', href: 'https://creator.xiaohongshu.com/publish/publish' }
  const document = {
    documentElement: new FakeElement(),
    addEventListener() {},
  }
  const chrome = {
    runtime: {
      async sendMessage(message) {
        messages.push(message)
        if (message.kind === 'GET_PUBLICATION') return { ok: true, filledAt: Date.now() }
        if (message.kind === 'GET_DRAFT') return { ok: false }
        if (message.kind === 'PUBLISH_SUCCEEDED') return { ok: false }
        return { ok: true }
      },
    },
  }
  class FakeObserver {
    observe() {}
  }
  const source = fs.readFileSync(path.join(__dirname, '..', 'publisher.js'), 'utf8')
  vm.runInNewContext(source, {
    chrome, document, Element: FakeElement, HTMLElement: FakeElement,
    MutationObserver: FakeObserver, location,
    setInterval(callback) { intervals.push(callback); return intervals.length },
    clearInterval() {},
    globalThis: { xiaohongshuPublishSignal: signal, xiaohongshuTopicFill: topicFill },
  })
  await new Promise(setImmediate)
  location.pathname = '/publish/success'
  location.href = 'https://creator.xiaohongshu.com/publish/success'
  intervals.forEach((callback) => callback())
  await new Promise(setImmediate)
  assert.equal(messages.filter((message) => message.kind === 'PUBLISH_SUCCEEDED').length, 1)
  assert.equal(messages.find((message) => message.kind === 'PUBLISH_SUCCEEDED').signal, 'success_page')
})
