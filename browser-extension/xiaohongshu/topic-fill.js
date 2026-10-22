(() => {
  const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

  function topicNames(editor) {
    return [...editor.querySelectorAll('a.tiptap-topic[data-topic]')].map((node) => {
      try {
        return JSON.parse(node.getAttribute('data-topic')).name
      } catch {
        return null
      }
    }).filter((name) => typeof name === 'string')
  }

  function putCaretAtEnd(editor) {
    editor.focus()
    const selection = window.getSelection()
    const range = document.createRange()
    range.selectNodeContents(editor)
    range.collapse(false)
    selection.removeAllRanges()
    selection.addRange(range)
  }

  function insertText(value) {
    if (!document.execCommand('insertText', false, value)) {
      throw new Error('小红书正文编辑器未接受话题输入，请手动添加话题。')
    }
  }

  function matchingCandidate(name) {
    const container = document.querySelector('#creator-editor-topic-container')
    if (!container || container.getClientRects().length === 0) return null
    return [...container.querySelectorAll('.item')].find((item) =>
      item.getClientRects().length > 0
      && item.children[0]?.textContent?.trim() === `#${name}`) || null
  }

  async function waitForCandidate(name) {
    const deadline = Date.now() + 7000
    while (Date.now() < deadline) {
      const candidate = matchingCandidate(name)
      if (candidate) return candidate
      await pause(150)
    }
    throw new Error(`话题 #${name} 没有出现同名候选。请在小红书页面手动添加，或返回星析修改话题。`)
  }

  async function waitForTopic(editor, name, previousCount) {
    const deadline = Date.now() + 3500
    while (Date.now() < deadline) {
      const names = topicNames(editor)
      if (names.length > previousCount && names.includes(name)) return
      await pause(100)
    }
    throw new Error(`话题 #${name} 未生成小红书的有效话题节点，请在发布前手动添加。`)
  }

  async function fillTopics(editor, topics, onProgress = () => {}) {
    for (const [index, name] of topics.entries()) {
      onProgress(name, index, topics.length)
      const previousCount = topicNames(editor).length
      putCaretAtEnd(editor)
      if (index === 0) {
        if (!document.execCommand('insertParagraph')) {
          throw new Error('无法在正文后开始添加话题，请在小红书页面手动添加。')
        }
      } else {
        insertText(' ')
      }
      insertText('#')
      await pause(100)
      for (const character of name) {
        insertText(character)
        await pause(35)
      }
      const candidate = await waitForCandidate(name)
      candidate.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }))
      await pause(50)
      if (topicNames(editor).length === previousCount) candidate.click()
      await waitForTopic(editor, name, previousCount)
    }
    const actual = topicNames(editor)
    if (topics.some((name) => !actual.includes(name))) {
      throw new Error('部分话题未生成有效节点，请在发布前检查话题。')
    }
  }

  const api = { fillTopics, topicNames, matchingCandidate }
  if (typeof module !== 'undefined' && module.exports) module.exports = api
  else globalThis.xiaohongshuTopicFill = api
})()
