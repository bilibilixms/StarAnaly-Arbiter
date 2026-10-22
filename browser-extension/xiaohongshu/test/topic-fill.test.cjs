const assert = require('node:assert/strict')
const test = require('node:test')
const { fillTopics, matchingCandidate, topicNames } = require('../topic-fill.js')

test('selects exact suggestions and confirms native topic nodes', async () => {
  const inserted = []
  const selected = []
  const nodes = []
  const editor = {
    focus() {},
    querySelectorAll(selector) {
      assert.equal(selector, 'a.tiptap-topic[data-topic]')
      return nodes
    },
  }
  const candidates = ['周末记录', '散步日记'].map((name) => ({
    children: [{ textContent: `#${name}` }, { textContent: '浏览' }],
    getClientRects() { return [{}] },
    dispatchEvent() {},
    click() {
      selected.push(name)
      nodes.push({ getAttribute() { return JSON.stringify({ id: name, name }) } })
    },
  }))
  const container = {
    getClientRects() { return [{}] },
    querySelectorAll(selector) {
      assert.equal(selector, '.item')
      return candidates
    },
  }
  global.document = {
    querySelector() { return container },
    createRange() { return { selectNodeContents() {}, collapse() {} } },
    execCommand(command, _showUi, value) {
      inserted.push(command === 'insertParagraph' ? '\n' : value)
      return true
    },
  }
  global.window = { getSelection() { return { removeAllRanges() {}, addRange() {} } } }
  global.MouseEvent = class { constructor() {} }
  try {
    await fillTopics(editor, ['周末记录', '散步日记'])
    assert.deepEqual(selected, ['周末记录', '散步日记'])
    assert.deepEqual(inserted, ['\n', '#', ...'周末记录', ' ', '#', ...'散步日记'])
    assert.deepEqual(topicNames(editor), ['周末记录', '散步日记'])
    assert.equal(matchingCandidate('不存在'), null)
  } finally {
    delete global.document
    delete global.window
    delete global.MouseEvent
  }
})
