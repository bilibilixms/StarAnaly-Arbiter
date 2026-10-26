(() => {
  const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
  const markerSelector = 'a[href*="topic"], a[href*="hashtag"], [data-topic], [data-topic-id], [data-type="topic"], [contenteditable="false"]'

  function topicNodes(editor, name) {
    return [...editor.querySelectorAll(markerSelector)]
      .filter((node) => (node.textContent || '').trim() === `#${name}`)
  }

  function topicNames(editor) {
    return [...editor.querySelectorAll(markerSelector)]
      .map((node) => (node.textContent || '').trim())
      .filter((value) => /^#[^#\s]+$/u.test(value))
      .map((value) => value.slice(1))
  }

  function putCaretAtEnd(editor) {
    editor.focus()
    const selection = window.getSelection()
    const range = document.createRange()
    const lines = [...editor.querySelectorAll('[data-line-wrapper="true"]')]
    range.selectNodeContents(lines.at(-1) || editor)
    range.collapse(false)
    selection.removeAllRanges()
    selection.addRange(range)
  }

  function putCaretAfterTopic(editor, name) {
    const marker = topicNodes(editor, name).at(-1)
    if (!marker) throw new Error(`找不到已选中的话题 #${name}，请在发布页手动添加后续话题。`)
    editor.focus()
    const selection = window.getSelection()
    const range = document.createRange()
    range.setStartAfter(marker)
    range.collapse(true)
    selection.removeAllRanges()
    selection.addRange(range)
  }

  function insertText(value) {
    if (!document.execCommand('insertText', false, value)) {
      throw new Error('抖音正文编辑器没有接受话题输入，请在发布页手动添加。')
    }
  }

  function editorText(editor) {
    const lines = [...editor.querySelectorAll('[data-line-wrapper="true"]')]
    return lines.length
      ? lines.map((line) => line.innerText || line.textContent || '').join('\n')
      : (editor.innerText || editor.textContent || '')
  }

  function occurrences(text, value) {
    const escaped = value.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&')
    return [...text.matchAll(new RegExp(`${escaped}(?![\\p{L}\\p{N}_])`, 'gu'))].length
  }

  async function waitForEditor(getEditor) {
    const deadline = Date.now() + 2500
    while (Date.now() < deadline) {
      const editor = getEditor()
      if (editor && editor.isConnected !== false) return editor
      await pause(80)
    }
    throw new Error('抖音正文编辑器已重新加载，但无法继续定位，请在发布页手动添加话题。')
  }

  async function waitForText(getEditor, value, before, timeoutMs) {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      const editor = getEditor()
      if (editor && occurrences(editorText(editor), value) > before) {
        await pause(120)
        const settled = getEditor()
        if (settled && occurrences(editorText(settled), value) > before) return true
      }
      await pause(80)
    }
    return false
  }

  async function insertTopic(getEditor, token, previousName) {
    let editor = await waitForEditor(getEditor)
    if (previousName) {
      if (editor.querySelectorAll('[data-line-wrapper="true"]').length) putCaretAtEnd(editor)
      else putCaretAfterTopic(editor, previousName)
      if (!document.execCommand('insertParagraph')) {
        throw new Error('无法在上一个抖音话题后换行，请在发布页手动添加后续话题。')
      }
    } else {
      putCaretAtEnd(editor)
      if (!document.execCommand('insertParagraph')) {
        throw new Error('无法在正文后开始添加话题，请在抖音页面手动添加。')
      }
    }
    const refreshedEditor = await waitForEditor(getEditor)
    if (refreshedEditor !== editor) {
      putCaretAtEnd(refreshedEditor)
    }
    editor = refreshedEditor
    const beforeTokenCount = occurrences(editorText(editor), token)
    insertText(token)
    return waitForText(getEditor, token, beforeTokenCount, 1000)
  }

  function matchingCandidate(editor, name) {
    const label = `#${name}`
    const leaves = [...document.querySelectorAll('span,div,li,button,p,a,[role="option"]')]
      .filter((node) => {
        const value = (node.textContent || '').trim().replace(/^#\s*/u, '#')
        return (value === label || value === name)
          && ![...node.children].some((child) => {
            const childValue = (child.textContent || '').trim().replace(/^#\s*/u, '#')
            return childValue === label || childValue === name
          })
      })
    for (const leaf of leaves) {
      if (editor.contains(leaf) || !leaf.getClientRects().length
          || leaf.closest('#staranaly-douyin-status')) continue
      let fallback = null
      for (let item = leaf, depth = 0; item && depth < 7; item = item.parentElement, depth++) {
        if (editor.contains(item) || item.contains(editor) || !item.getClientRects().length
            || item.closest('#staranaly-douyin-status')) break
        const value = (item.textContent || '').trim().replace(/\s+/gu, '')
        const hasHash = value.startsWith(label)
        const suffix = hasHash ? value.slice(label.length)
          : value.startsWith(name) ? value.slice(name.length) : null
        if (suffix === null) continue
        if (suffix.includes('#')) break
        if (/^\d[\d.,]*[万亿]?(?:次播放|次浏览|人参与|热度)?$/u.test(suffix)) return item
        if (!suffix && item.matches('[role="option"],li,button,[class*="item"],[class*="Item"]')) {
          fallback = item
        }
      }
      if (fallback) return fallback
    }
    return null
  }

  function selectCandidate(candidate) {
    candidate.scrollIntoView?.({ block: 'nearest' })
    const options = { bubbles: true, cancelable: true, view: window }
    if (typeof PointerEvent === 'function') {
      candidate.dispatchEvent(new PointerEvent('pointerdown', {
        ...options, pointerId: 1, pointerType: 'mouse', isPrimary: true,
      }))
    }
    candidate.dispatchEvent(new MouseEvent('mousedown', options))
    if (typeof PointerEvent === 'function') {
      candidate.dispatchEvent(new PointerEvent('pointerup', {
        ...options, pointerId: 1, pointerType: 'mouse', isPrimary: true,
      }))
    }
    candidate.dispatchEvent(new MouseEvent('mouseup', options))
    candidate.click()
  }

  async function waitForCandidate(getEditor, name) {
    const deadline = Date.now() + 7000
    while (Date.now() < deadline) {
      const editor = getEditor()
      const candidate = editor && matchingCandidate(editor, name)
      if (candidate) return candidate
      await pause(150)
    }
    throw new Error(`抖音没有给出话题 #${name} 的同名候选。请在发布页手动选择。`)
  }

  async function waitForTopic(getEditor, name, beforeNodes, beforeNames) {
    const deadline = Date.now() + 3500
    while (Date.now() < deadline) {
      const editor = getEditor()
      if (editor && topicNodes(editor, name).some((node) =>
        !beforeNodes.has(node) || beforeNodes.get(node) !== node.outerHTML)) return
      if (editor) {
        const actual = topicNames(editor)
        if (actual.length > beforeNames.length) {
          throw new Error(`抖音首条候选是 #${actual.at(-1)}，与输入的 #${name} 不同，请在发布前检查话题。`)
        }
      }
      await pause(100)
    }
    throw new Error(`话题 #${name} 没有变成抖音的有效话题，请在发布前手动选择。`)
  }

  async function fillTopics(editor, topicTags, onProgress = () => {}, getEditor = () => editor) {
    for (const [index, tag] of topicTags.entries()) {
      const name = tag.slice(1)
      onProgress(tag, index, topicTags.length)
      const textAppeared = await insertTopic(getEditor, tag, index ? topicTags[index - 1].slice(1) : null)
      let candidate
      try {
        candidate = await waitForCandidate(getEditor, name)
      } catch (error) {
        if (!textAppeared) {
          const activeEditor = getEditor()
          const selection = window.getSelection()
          const focusInEditor = !!activeEditor?.contains(document.activeElement)
          const caretInEditor = !!activeEditor?.contains(selection?.anchorNode)
          const anchor = selection?.anchorNode
          const anchorElement = anchor?.nodeType === 1 ? anchor : anchor?.parentElement
          const caretInTextLine = !!anchorElement?.closest?.('[data-line-wrapper="true"]')
          const editable = anchorElement?.closest?.('[contenteditable]')?.getAttribute('contenteditable') || '未知'
          throw new Error(`抖音没有把话题 ${tag} 留在正文中（焦点在正文：${focusInEditor ? '是' : '否'}；光标在正文：${caretInEditor ? '是' : '否'}；光标在正文行：${caretInTextLine ? '是' : '否'}；当前位置可编辑：${editable}）。请在发布页手动添加。`)
        }
        throw error
      }
      const currentEditor = await waitForEditor(getEditor)
      const beforeNodes = new Map(topicNodes(currentEditor, name).map((node) => [node, node.outerHTML]))
      const beforeNames = topicNames(currentEditor)
      selectCandidate(candidate)
      await waitForTopic(getEditor, name, beforeNodes, beforeNames)
      await pause(150)
    }
    const actual = topicNames(await waitForEditor(getEditor))
    if (topicTags.some((tag) => !actual.includes(tag.slice(1)))) {
      throw new Error('部分话题没有生成有效话题，请在发布前检查。')
    }
  }

  const api = { fillTopics, topicNames, matchingCandidate }
  if (typeof module !== 'undefined' && module.exports) module.exports = api
  else globalThis.douyinTopicFill = api
})()
