(() => {
  const publishPath = '/creator-micro/content/upload'
  const { fillTopics } = globalThis.douyinTopicFill
  let running = false
  let finished = false
  let waitingForPage = false
  let uploadTriggered = false
  let panel
  let statusText
  let retryButton

  function visible(element) {
    return element instanceof HTMLElement && element.getClientRects().length > 0
  }

  function showStatus(message, canRetry = false) {
    if (!panel) {
      panel = document.createElement('aside')
      panel.id = 'staranaly-douyin-status'
      panel.setAttribute('aria-live', 'polite')
      panel.style.cssText = 'position:fixed;top:20px;right:20px;z-index:2147483647;width:min(330px,calc(100vw - 40px));padding:16px;border:1px solid #a9d5dc;border-radius:14px;background:#f4fbfc;box-shadow:0 12px 35px #24314833;color:#243148;font:14px/1.5 system-ui,sans-serif;'
      const heading = document.createElement('strong')
      heading.textContent = '星析抖音发布助手'
      panel.append(heading)
      const closeButton = document.createElement('button')
      closeButton.type = 'button'
      closeButton.textContent = '×'
      closeButton.setAttribute('aria-label', '关闭发布助手提示')
      closeButton.style.cssText = 'float:right;padding:0 3px;border:0;background:transparent;color:#4f6d88;font-size:20px;line-height:1;cursor:pointer;'
      closeButton.addEventListener('click', () => { panel.remove(); panel = null })
      panel.append(closeButton)
      statusText = document.createElement('p')
      statusText.style.margin = '9px 0 0'
      panel.append(statusText)
      retryButton = document.createElement('button')
      retryButton.type = 'button'
      retryButton.textContent = '重试填入'
      retryButton.style.cssText = 'margin-top:12px;padding:7px 12px;border:0;border-radius:8px;background:#185b68;color:#fff;cursor:pointer;'
      retryButton.addEventListener('click', () => { void run() })
      panel.append(retryButton)
      document.documentElement.append(panel)
    }
    statusText.textContent = message
    retryButton.hidden = !canRetry
  }

  async function waitFor(find, timeoutMs, message) {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      const found = find()
      if (found) return found
      await new Promise((resolve) => setTimeout(resolve, 300))
    }
    throw new Error(message)
  }

  function chooseImageTab() {
    const candidates = [...document.querySelectorAll('[role="tab"],button,[class*="tab-item"]')]
    const tab = candidates.find((item) => visible(item) && /^(发布图文|图文)$/.test(item.textContent?.trim() || ''))
    tab?.click()
  }

  function findUploadInput() {
    const candidates = [...document.querySelectorAll('input[type="file"]')]
      .filter((input) => (input.accept || '').includes('image') && !(input.accept || '').includes('video'))
    return candidates.find((input) => input.multiple) || candidates[0] || null
  }

  function findTitleInput() {
    return [...document.querySelectorAll('input[placeholder*="标题"]')].find(visible) || null
  }

  function findEditor() {
    const preferred = [...document.querySelectorAll('[contenteditable="true"][data-placeholder*="描述"],.textarea-container[contenteditable="true"]')]
      .find(visible)
    if (preferred) return preferred
    const wrapper = [...document.querySelectorAll('[data-line-wrapper="true"]')].find(visible)
    if (wrapper) return wrapper.closest('[contenteditable="true"]') || wrapper
    return [...document.querySelectorAll('[contenteditable="true"]')].find(visible) || null
  }

  function imageFile(image) {
    const binary = atob(image.data.slice(image.data.indexOf(',') + 1))
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
    return new File([bytes], image.name, { type: image.type })
  }

  async function uploadImages(draft) {
    chooseImageTab()
    const input = await waitFor(findUploadInput, 30000,
      '没有找到抖音图文上传控件。请登录并确认已进入“发布图文”页面，然后重试。')
    const accept = input.accept || ''
    if (draft.images.some((image) => !accept.includes(image.type) && !accept.includes(image.type.split('/')[1]))) {
      throw new Error('所选图片格式不受当前抖音上传控件支持。请返回星析改用 JPEG、PNG 或 WebP 图片。')
    }
    const files = new DataTransfer()
    for (const image of draft.images) files.items.add(imageFile(image))
    input.files = files.files
    if (input.files.length !== draft.images.length) throw new Error('图片没有完整交给抖音上传控件，请重试。')
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
    uploadTriggered = true
  }

  async function fillTitle(input, title) {
    input.focus()
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set
    if (!setter) throw new Error('无法填写抖音标题。')
    setter.call(input, title)
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
    await new Promise((resolve) => setTimeout(resolve, 250))
    if (input.value !== title) throw new Error('抖音标题没有完整填入，请检查标题字数。')
  }

  function matchesEditor(editor, content) {
    const normalize = (value) => (value || '').replace(/[\s\u200b-\u200f\ufeff]+/g, ' ').trim()
    const expected = normalize(content)
    const lines = [...(editor.querySelectorAll?.('[data-line-wrapper="true"]') || [])]
    const actual = lines.length
      ? normalize(lines.map((line) => line.innerText || line.textContent).join(' '))
      : normalize(editor.innerText || editor.textContent)
    return actual.includes(expected)
  }

  async function fillEditor(editor, content) {
    editor.focus()
    const selection = window.getSelection()
    const range = document.createRange()
    range.selectNodeContents(editor)
    selection.removeAllRanges()
    selection.addRange(range)
    document.execCommand('insertText', false, content)
    await new Promise((resolve) => setTimeout(resolve, 350))
    if (!matchesEditor(editor, content)) {
      editor.focus()
      range.selectNodeContents(editor)
      selection.removeAllRanges()
      selection.addRange(range)
      const transfer = new DataTransfer()
      transfer.setData('text/plain', content)
      editor.dispatchEvent(new ClipboardEvent('paste', {
        bubbles: true, cancelable: true, clipboardData: transfer,
      }))
      await new Promise((resolve) => setTimeout(resolve, 350))
    }
    return matchesEditor(editor, content)
  }

  async function run() {
    if (running || finished) return
    running = true
    let fillingTopics = false
    try {
      const response = await chrome.runtime.sendMessage({ kind: 'DY_GET_DRAFT' })
      if (!response?.ok) {
        if (response?.error) showStatus(response.error)
        finished = true
        return
      }
      const draft = response.draft
      if (!location.pathname.startsWith(publishPath)) {
        waitingForPage = true
        showStatus('请先登录抖音并进入“发布图文”页面；扩展会继续填入。', true)
        return
      }
      waitingForPage = false
      showStatus('正在上传图片、填写文案并选择抖音话题。')
      if (!uploadTriggered) await uploadImages(draft)
      const titleInput = await waitFor(findTitleInput, 60000,
        '没有找到抖音标题输入框。请确认图片已上传，或点击“重试填入”。')
      const editor = await waitFor(findEditor, 15000,
        '没有找到抖音正文编辑框。请检查发布页，或点击“重试填入”。')
      await fillTitle(titleInput, draft.title)
      const bodyFilled = await fillEditor(editor, draft.body)
      if (!bodyFilled) throw new Error('抖音正文没有完整填入，请检查发布页后重试。')
      if (draft.topicTags?.length) {
        fillingTopics = true
        await fillTopics(editor, draft.topicTags, (tag, index, count) => {
          showStatus(`正在选择话题 ${index + 1}/${count}：${tag}`)
        }, findEditor)
      }
      const cleared = await chrome.runtime.sendMessage({ kind: 'DY_CLEAR_DRAFT' })
      if (!cleared?.ok) throw new Error('扩展未能清除临时图片，请重试填入。')
      draft.images = []
      finished = true
      showStatus('图片和文案已填入，话题已从抖音候选中选择。请检查图片数量、上传状态及话题，再由你点击发布。')
    } catch (error) {
      waitingForPage = false
      showStatus(error instanceof Error ? error.message : '自动填入失败，请检查页面后重试。', !fillingTopics)
    } finally {
      running = false
    }
  }

  void run()
  const navigationCheck = setInterval(() => {
    if (finished) clearInterval(navigationCheck)
    else if (waitingForPage && location.pathname.startsWith(publishPath) && !running) void run()
  }, 1500)
})()
