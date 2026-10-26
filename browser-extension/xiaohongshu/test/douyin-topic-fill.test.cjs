const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const vm = require('node:vm')

function setup({ topics = ['蓝色'], suggestionName, firstSuggestionName, candidateDisplayName, commit = 'mousedown',
  rejectInput = false, partialInput = false, controlsText = '', prematureMarker = false,
  hashAsPseudo = false, replaceEditorOnCommit = false, endCaretOutsideTextAfterTopic = false,
  dropSubsequentInput = false, requireParagraphAfterTopic = false,
  lineWrapper = false, requireTextLineAfterTopic = false, dropNameAfterHash = false } = {}) {
  const markers = []
  const existingMarkers = prematureMarker
    ? [{ textContent: '#蓝色', outerHTML: '<span contenteditable="false">#蓝色</span>' }]
    : []
  const typed = []
  const actions = []
  let focusedEditor = null
  let hashTriggered = false
  let hashOnlyInserted = false
  let newParagraphAfterTopic = false
  let currentLine = null
  const selection = {
    anchorNode: null,
    removeAllRanges() { this.anchorNode = null },
    addRange(range) { this.anchorNode = range.afterTopic || range.editor },
  }
  function makeEditor() {
    const line = { get innerText() { return typed.join('') } }
    currentLine = line
    return {
      isConnected: true,
      focus() { focusedEditor = this },
      contains(node) { return node === this },
      querySelectorAll(selector) {
        if (!this.isConnected) return []
        if (selector === '[data-line-wrapper="true"]') return lineWrapper ? [line] : []
        return [...existingMarkers, ...markers]
      },
      get innerText() { return typed.join('') + controlsText },
    }
  }
  const editor = makeEditor()
  let currentEditor = editor
  function commitTopic(name = topics[markers.length]) {
    markers.push({ textContent: `#${name}` })
    hashTriggered = false
    if (replaceEditorOnCommit && markers.length === 1) {
      currentEditor.isConnected = false
      currentEditor = makeEditor()
    }
  }
  const candidate = {
    get textContent() { return `${hashAsPseudo ? '' : '# '}${candidateDisplayName || suggestionName || topics[markers.length]} 38.7亿` },
    parentElement: null,
    getClientRects() { return [1] },
    contains() { return false },
    closest() { return null },
    matches() { return true },
    scrollIntoView() {},
    dispatchEvent(event) {
      actions.push(event.type)
      if (commit === 'mousedown' && event.type === 'mousedown') {
        commitTopic()
      }
    },
    click() {
      actions.push('click')
      if (commit === 'click') {
        commitTopic()
      }
    },
  }
  if (firstSuggestionName) {
    const first = {
      textContent: `# ${firstSuggestionName} 99亿`,
      parentElement: null,
      getClientRects() { return [1] },
      contains() { return false },
      closest() { return null },
      scrollIntoView() {},
      dispatchEvent(event) {
        actions.push(`first:${event.type}`)
        if (commit === 'mousedown' && event.type === 'mousedown') commitTopic(firstSuggestionName)
      },
      click() {
        actions.push('first:click')
        if (commit === 'click') commitTopic(firstSuggestionName)
      },
    }
    const list = { children: [first, candidate], contains() { return false } }
    first.parentElement = list
    candidate.parentElement = list
  }
  const label = {
    get textContent() { return `${hashAsPseudo ? '' : '# '}${suggestionName || topics[markers.length]}` },
    parentElement: candidate,
    getClientRects() { return [1] },
    contains() { return false },
    closest() { return null },
    matches() { return false },
  }
  const leaf = {
    get textContent() { return suggestionName || topics[markers.length] },
    children: [],
    parentElement: label,
    getClientRects() { return [1] },
    contains() { return false },
    closest() { return null },
    matches() { return false },
  }
  const document = {
    createRange() { return {
      editor: null,
      afterTopic: null,
      selectNodeContents(editor) { this.editor = editor }, collapse() {},
      setStartAfter(node) { this.afterTopic = node },
    } },
    execCommand(command, _show, value) {
      actions.push(command === 'insertText' ? `type:${value}` : command)
      if (focusedEditor !== currentEditor || rejectInput) return false
      if (endCaretOutsideTextAfterTopic && markers.length && selection.anchorNode === currentEditor) return false
      if (requireTextLineAfterTopic && markers.length && selection.anchorNode !== currentLine) return false
      if (dropSubsequentInput && markers.length && command === 'insertText' && value !== ' ') return true
      if (dropNameAfterHash && markers.length && hashOnlyInserted && command === 'insertText' && !value?.startsWith('#')) return true
      if (command === 'insertParagraph') {
        typed.push('\n')
        if (markers.length) newParagraphAfterTopic = true
      }
      else if (value?.startsWith('#')) {
        if (!partialInput) typed.push(value)
        if (value === '#') hashOnlyInserted = true
        hashTriggered = !requireParagraphAfterTopic || !markers.length || newParagraphAfterTopic
      } else if (!partialInput) {
        typed.push(value)
      }
      return true
    },
    querySelectorAll() {
      const name = topics[markers.length]
      return hashTriggered && name && typed.join('').endsWith(`#${name}`) ? [leaf] : []
    },
  }
  const module = { exports: {} }
  const source = fs.readFileSync(path.join(__dirname, '..', 'douyin-topic-fill.js'), 'utf8')
  let now = 0
  vm.runInNewContext(source, {
    document, module, MouseEvent: class { constructor(type) { this.type = type } },
    window: { getSelection() { return selection } },
    setTimeout: (callback) => setImmediate(callback),
    Date: { now: () => (now += 100) },
  })
  return {
    ...module.exports, editor, getEditor: () => currentEditor, typed, actions, markers,
  }
}

test('types a complete topic as one input and selects its matching suggestion', async () => {
  const helper = setup({ commit: 'click' })
  await helper.fillTopics(helper.editor, ['#蓝色'])
  assert.deepEqual(helper.typed, ['\n', '#蓝色'])
  assert.deepEqual(helper.actions, ['insertParagraph', 'type:#蓝色', 'mousedown', 'mouseup', 'click'])
  assert.deepEqual(Array.from(helper.topicNames(helper.editor)), ['蓝色'])
})

test('adds subsequent topics on a new line and verifies each selected node', async () => {
  const helper = setup({ topics: ['蓝色', '动漫人物'], commit: 'click' })
  await helper.fillTopics(helper.editor, ['#蓝色', '#动漫人物'])
  assert.deepEqual(helper.typed, ['\n', '#蓝色', '\n', '#动漫人物'])
  assert.deepEqual(helper.actions.filter((action) => action === 'click'), ['click', 'click'])
  assert.deepEqual(Array.from(helper.topicNames(helper.editor)), ['蓝色', '动漫人物'])
})

test('places the caret after the first selected topic instead of after Douyin editor controls', async () => {
  const helper = setup({ topics: ['蓝色', '动漫人物'], commit: 'click',
    controlsText: '\n#添加话题 @好友', endCaretOutsideTextAfterTopic: true,
    requireParagraphAfterTopic: true })
  await helper.fillTopics(helper.editor, ['#蓝色', '#动漫人物'])
  assert.deepEqual(Array.from(helper.topicNames(helper.editor)), ['蓝色', '动漫人物'])
  assert.deepEqual(helper.typed, ['\n', '#蓝色', '\n', '#动漫人物'])
})

test('continues in Douyin editable text line when the native topic node cannot accept text beside it', async () => {
  const helper = setup({ topics: ['蓝色', '二次元'], commit: 'click',
    lineWrapper: true, requireTextLineAfterTopic: true })
  await helper.fillTopics(helper.editor, ['#蓝色', '#二次元'])
  assert.deepEqual(Array.from(helper.topicNames(helper.editor)), ['蓝色', '二次元'])
  assert.deepEqual(helper.typed, ['\n', '#蓝色', '\n', '#二次元'])
})

test('does not leave a bare hash when Douyin drops the name typed after it', async () => {
  const helper = setup({ topics: ['蓝色', '二次元'], commit: 'click',
    lineWrapper: true, dropNameAfterHash: true })
  await helper.fillTopics(helper.editor, ['#蓝色', '#二次元'])
  assert.deepEqual(helper.typed, ['\n', '#蓝色', '\n', '#二次元'])
  assert.equal(helper.actions.includes('type:#'), false)
})

test('related recommendation text does not count as a topic typed into the editor', async () => {
  const helper = setup({ topics: ['蓝色', '二次元'], commit: 'click',
    controlsText: '\n#二次元少女', dropSubsequentInput: true })
  await assert.rejects(helper.fillTopics(helper.editor, ['#蓝色', '#二次元']), /没有把话题 #二次元 留在正文中/)
  assert.deepEqual(Array.from(helper.topicNames(helper.editor)), ['蓝色'])
})

test('continues with a fresh editor after Douyin replaces it on the first selection', async () => {
  const helper = setup({ topics: ['蓝色', '动漫人物'], commit: 'click', replaceEditorOnCommit: true })
  await helper.fillTopics(helper.editor, ['#蓝色', '#动漫人物'], () => {}, helper.getEditor)
  assert.deepEqual(Array.from(helper.topicNames(helper.getEditor())), ['蓝色', '动漫人物'])
  assert.equal(helper.editor.isConnected, false)
})

test('chooses the exact topic even when a similar topic is listed first', async () => {
  const helper = setup({ firstSuggestionName: '蓝色创作', commit: 'click' })
  await helper.fillTopics(helper.editor, ['#蓝色'])
  assert.equal(helper.actions.includes('first:click'), false)
  assert.equal(helper.actions.includes('click'), true)
  assert.deepEqual(Array.from(helper.topicNames(helper.editor)), ['蓝色'])
})

test('rejects a row whose text only starts with the requested topic', () => {
  const helper = setup({ candidateDisplayName: '蓝色创作' })
  assert.equal(helper.matchingCandidate(helper.editor, '蓝色'), null)
})

test('does not choose a prefix match from the suggestion list', async () => {
  const helper = setup({ suggestionName: '蓝色妖姬' })
  await assert.rejects(helper.fillTopics(helper.editor, ['#蓝色']), /同名候选/)
  assert.deepEqual(helper.actions.filter((action) => action === 'click'), [])
})

test('selects a suggestion whose hash is drawn as an icon', async () => {
  const helper = setup({ commit: 'click', hashAsPseudo: true })
  await helper.fillTopics(helper.editor, ['#蓝色'])
  assert.deepEqual(helper.actions.slice(-3), ['mousedown', 'mouseup', 'click'])
})

test('does not claim plain hashtag text is a selected topic', async () => {
  const helper = setup({ commit: 'none' })
  await assert.rejects(helper.fillTopics(helper.editor, ['#蓝色']), /有效话题/)
  assert.deepEqual(helper.actions.slice(-3), ['mousedown', 'mouseup', 'click'])
})

test('does not accept a marker that existed before clicking the suggestion', async () => {
  const helper = setup({ commit: 'none', prematureMarker: true })
  await assert.rejects(helper.fillTopics(helper.editor, ['#蓝色']), /有效话题/)
})

test('stops when the editor rejects topic input', async () => {
  const helper = setup({ rejectInput: true })
  await assert.rejects(helper.fillTopics(helper.editor, ['#蓝色']), /无法在正文后开始添加话题/)
  assert.deepEqual(helper.actions.filter((action) => action === 'click'), [])
})

test('preserves entered text and never undoes it when Douyin drops a topic name', async () => {
  const helper = setup({ partialInput: true, controlsText: '\n#添加话题 @好友' })
  await assert.rejects(helper.fillTopics(helper.editor, ['#蓝色']), /没有把话题 #蓝色 留在正文中/)
  assert.deepEqual(helper.typed, ['\n'])
  assert.equal(helper.actions.includes('undo'), false)
  assert.equal(helper.actions.includes('type:#蓝色'), true)
  assert.deepEqual(helper.actions.filter((action) => action === 'click'), [])
})

test('uses mousedown when Douyin selects only on pointer interaction', async () => {
  const helper = setup({ commit: 'mousedown' })
  await helper.fillTopics(helper.editor, ['#蓝色'])
  assert.deepEqual(helper.actions.slice(-3), ['mousedown', 'mouseup', 'click'])
  assert.deepEqual(Array.from(helper.topicNames(helper.editor)), ['蓝色'])
})
