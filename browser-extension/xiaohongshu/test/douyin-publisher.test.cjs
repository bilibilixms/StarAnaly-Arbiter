const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const vm = require('node:vm')

test('Douyin publisher fills images and copy, then waits for the user to publish', async () => {
  let clicks = 0
  const selectedTopics = []
  class Element {
    constructor() { this.textContent = ''; this.innerText = ''; this.children = []; this.style = {} }
    getClientRects() { return [1] }
    setAttribute() {}
    append(...children) { this.children.push(...children) }
    remove() {}
    addEventListener() {}
    dispatchEvent() { return true }
    focus() {}
    click() { clicks++ }
  }
  class Input extends Element {
    get value() { return this.currentValue || '' }
    set value(next) { this.currentValue = next }
  }
  class Transfer {
    constructor() {
      this.files = []
      this.items = { add: (file) => this.files.push(file) }
    }
    setData() {}
  }
  class File {
    constructor(parts, name, options) { this.parts = parts; this.name = name; this.type = options.type }
  }
  const upload = new Input()
  upload.accept = 'image/png,image/jpeg,image/webp'
  const title = new Input()
  const editor = new Element()
  const root = new Element()
  const document = {
    documentElement: root,
    createElement() { return new Element() },
    querySelectorAll(selector) {
      if (selector === 'input[type="file"]') return [upload]
      if (selector === 'input[placeholder*="标题"]') return [title]
      if (selector.includes('[data-placeholder*="描述"]')) return [editor]
      return []
    },
    createRange() { return { selectNodeContents() {} } },
    execCommand(command, _show, content) {
      assert.equal(command, 'insertText')
      editor.innerText = `${content}\n#添加话题 @好友`
      return true
    },
  }
  const messages = []
  const chrome = {
    runtime: {
      async sendMessage(message) {
        messages.push(message.kind)
        if (message.kind === 'DY_GET_DRAFT') return { ok: true, draft: {
          noteId: 1, title: '抖音标题', body: '正文内容', topicTags: ['#话题'],
          images: [{ name: 'image.png', type: 'image/png', data: 'data:image/png;base64,AA==' }],
        } }
        return { ok: true }
      },
    },
  }
  const source = fs.readFileSync(path.join(__dirname, '..', 'douyin-publisher.js'), 'utf8')
  vm.runInNewContext(source, {
    globalThis: { douyinTopicFill: { async fillTopics(_editor, topicTags, _onProgress, getEditor) {
      assert.equal(getEditor(), editor)
      selectedTopics.push(...topicTags)
    } } },
    chrome, document, location: { pathname: '/creator-micro/content/upload' },
    window: { getSelection() { return { removeAllRanges() {}, addRange() {} } } },
    HTMLElement: Element, HTMLInputElement: Input, DataTransfer: Transfer, File,
    Event: class { constructor(type) { this.type = type } },
    ClipboardEvent: class {}, Uint8Array,
    atob: (value) => Buffer.from(value, 'base64').toString('binary'),
    setTimeout, setInterval: () => 1, clearInterval() {},
  })
  await new Promise((resolve) => setTimeout(resolve, 800))

  assert.equal(upload.files.length, 1)
  assert.equal(upload.files[0].name, 'image.png')
  assert.equal(title.value, '抖音标题')
  assert.equal(editor.innerText, '正文内容\n#添加话题 @好友')
  assert.deepEqual(selectedTopics, ['#话题'])
  assert.deepEqual(messages, ['DY_GET_DRAFT', 'DY_CLEAR_DRAFT'])
  assert.equal(clicks, 0)
})
