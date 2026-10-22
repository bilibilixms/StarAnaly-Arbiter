(() => {
  const PUBLISH_PATH = '/publish/publish'
  let running = false
  let finished = false
  let waitingForPublish = false
  let imageUploaded = false
  let panel
  let statusText
  let retryButton
  let publicationAttemptAt = 0
  let publicationAttemptUrl = ''
  let reportingSuccess = false
  let attemptRegistration = Promise.resolve(true)
  const { isPublishButtonText, isSuccessText } = globalThis.xiaohongshuPublishSignal
  const { fillTopics } = globalThis.xiaohongshuTopicFill

  function isPublishPage() {
    return location.pathname.startsWith(PUBLISH_PATH)
  }

  function visible(element) {
    return element instanceof HTMLElement && element.getClientRects().length > 0
  }

  function hasSuccessSignal(root) {
    const element = root instanceof Element ? root : root.parentElement
    if (!element) return false
    const candidates = [element, ...element.querySelectorAll('*')]
    return candidates.some((candidate) => visible(candidate)
      && candidate.children.length === 0 && isSuccessText(candidate.textContent))
  }

  async function reportSuccess() {
    if (reportingSuccess) return
    reportingSuccess = true
    try {
      if (!await attemptRegistration) {
        reportingSuccess = false
        return
      }
      const response = await chrome.runtime.sendMessage({ kind: 'PUBLISH_SUCCEEDED' })
      if (response?.ok) showStatus('检测到小红书发布成功，已通知星析更新发布记录。')
      else reportingSuccess = false
    } catch {
      reportingSuccess = false
    }
  }

  async function watchPublication() {
    const session = await chrome.runtime.sendMessage({ kind: 'GET_PUBLICATION' }).catch(() => null)
    if (!session?.ok) return
    publicationAttemptAt = session.attemptedAt || 0
    publicationAttemptUrl = session.attemptUrl || ''
    document.addEventListener('click', (event) => {
      if (!isPublishPage()) return
      const button = event.target instanceof Element
        ? event.target.closest('button,[role="button"]') : null
      if (!button || !visible(button) || button.disabled || !isPublishButtonText(button.textContent)) return
      publicationAttemptAt = Date.now()
      publicationAttemptUrl = location.href
      attemptRegistration = chrome.runtime.sendMessage({ kind: 'PUBLISH_ATTEMPTED' })
        .then((response) => {
          if (!response?.ok) publicationAttemptAt = 0
          return !!response?.ok
        }).catch(() => {
          publicationAttemptAt = 0
          return false
        })
    }, true)
    const observer = new MutationObserver((mutations) => {
      if (!publicationAttemptAt || Date.now() - publicationAttemptAt > 10 * 60 * 1000 || reportingSuccess) return
      if (mutations.some((mutation) => mutation.type === 'characterData'
        ? hasSuccessSignal(mutation.target)
        : [...mutation.addedNodes].some(hasSuccessSignal))) void reportSuccess()
    })
    observer.observe(document.documentElement, { subtree: true, childList: true, characterData: true })
    if (publicationAttemptAt && publicationAttemptUrl && location.href !== publicationAttemptUrl
        && Date.now() - publicationAttemptAt <= 10 * 60 * 1000
        && hasSuccessSignal(document.documentElement)) void reportSuccess()
  }

  function showStatus(message, canRetry = false) {
    if (!panel) {
      panel = document.createElement('aside')
      panel.setAttribute('aria-live', 'polite')
      panel.style.cssText = 'position:fixed;top:20px;right:20px;z-index:2147483647;width:min(330px,calc(100vw - 40px));padding:16px;border:1px solid #b8dcf4;border-radius:14px;background:#f4faff;box-shadow:0 12px 35px #23415e33;color:#23415e;font:14px/1.5 system-ui,sans-serif;'
      const heading = document.createElement('strong')
      heading.textContent = '星析发布助手'
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
      retryButton.style.cssText = 'margin-top:12px;padding:7px 12px;border:0;border-radius:8px;background:#2679b6;color:#fff;cursor:pointer;'
      retryButton.addEventListener('click', () => { void run() })
      panel.append(retryButton)
      document.documentElement.append(panel)
    }
    statusText.textContent = message
    retryButton.hidden = !canRetry
  }

  function waitFor(find, timeoutMs) {
    return new Promise((resolve, reject) => {
      const deadline = Date.now() + timeoutMs
      const check = () => {
        const found = find()
        if (found) return resolve(found)
        if (Date.now() >= deadline) return reject(new Error('没有找到小红书的上传或编辑控件。请确认已登录并进入“上传图文”页面。'))
        setTimeout(check, 300)
      }
      check()
    })
  }

  function findUploadInput() {
    return document.querySelector('input[type="file"][accept*="image"]')
      || document.querySelector('input.upload-input[type="file"]:not([accept*="video"])')
      || document.querySelector('input[type="file"]:not([accept*="video"])')
  }

  function findTitleInput() {
    const preferred = [...document.querySelectorAll('input[placeholder*="标题"]')].find(visible)
    if (preferred) return preferred
    return [...document.querySelectorAll('input.d-text')].find(visible)
  }

  function findEditor() {
    const selectors = [
      'div.tiptap.ProseMirror[contenteditable="true"]',
      'div.ProseMirror[contenteditable="true"]',
      '[contenteditable="true"][data-placeholder*="正文"]',
    ]
    for (const selector of selectors) {
      const editor = [...document.querySelectorAll(selector)].find(visible)
      if (editor) return editor
    }
    return null
  }

  function imageFile(draft) {
    const encoded = draft.imageData.slice(draft.imageData.indexOf(',') + 1)
    const binary = atob(encoded)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
    return new File([bytes], draft.imageName, { type: draft.imageType })
  }

  function chooseImageTab() {
    const tabs = [...document.querySelectorAll('div.creator-tab,[role="tab"]')]
    const imageTab = tabs.find((tab) => visible(tab) && tab.textContent?.includes('上传图文'))
    imageTab?.click()
  }

  async function uploadImage(draft) {
    chooseImageTab()
    await new Promise((resolve) => setTimeout(resolve, 350))
    const input = await waitFor(findUploadInput, 15000)
    const files = new DataTransfer()
    files.items.add(imageFile(draft))
    input.files = files.files
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
  }

  async function fillTitle(input, title) {
    input.focus()
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set
    if (!setter) throw new Error('无法填写标题输入框。')
    setter.call(input, title)
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
    await new Promise((resolve) => setTimeout(resolve, 350))
    if (input.value !== title) throw new Error('标题没有完整填入，请检查小红书的标题字数限制。')
  }

  function selectEditor(editor) {
    editor.focus()
    const selection = window.getSelection()
    const range = document.createRange()
    range.selectNodeContents(editor)
    selection.removeAllRanges()
    selection.addRange(range)
  }

  function matchesEditor(editor, content) {
    const normalize = (value) => value.replace(/[\s\u200b]+/g, ' ').trim()
    return normalize(editor.innerText) === normalize(content)
  }

  async function fillEditor(editor, content) {
    selectEditor(editor)
    const transfer = new DataTransfer()
    transfer.setData('text/plain', content)
    editor.dispatchEvent(new ClipboardEvent('paste', {
      bubbles: true,
      cancelable: true,
      clipboardData: transfer,
    }))
    await new Promise((resolve) => setTimeout(resolve, 350))
    if (!matchesEditor(editor, content)) {
      selectEditor(editor)
      document.execCommand('insertText', false, content)
      await new Promise((resolve) => setTimeout(resolve, 350))
    }
    editor.blur()
    await new Promise((resolve) => setTimeout(resolve, 350))
    if (!matchesEditor(editor, content)) {
      throw new Error('正文没有完整填入。请返回星析复制正文，或点击“重试填入”。')
    }
  }

  async function run() {
    if (running || finished) return
    running = true
    try {
      const response = await chrome.runtime.sendMessage({ kind: 'GET_DRAFT' })
      if (!response?.ok) {
        if (response?.error) showStatus(response.error)
        finished = true
        return
      }
      const draft = response.draft
      if (!Array.isArray(draft.topics)) {
        throw new Error('当前草稿来自旧版扩展，请返回星析重新点击“去发布”。')
      }
      if (!isPublishPage()) {
        waitingForPublish = true
        showStatus('请先登录小红书并进入“上传图文”页面；扩展会在当前标签页继续填入。', true)
        return
      }

      waitingForPublish = false
      showStatus('正在上传图片并填写文案，请稍候。')
      if (!imageUploaded) await uploadImage(draft)
      const titleInput = await waitFor(findTitleInput, 45000)
      const editor = await waitFor(findEditor, 15000)
      imageUploaded = true
      await fillTitle(titleInput, draft.title)
      await fillEditor(editor, draft.body)
      if (draft.topics.length) {
        try {
          await fillTopics(editor, draft.topics, (name, index, count) => {
            showStatus(`正在添加话题 ${index + 1}/${count}：#${name}`)
          })
        } catch (error) {
          await fillEditor(editor, draft.body).catch(() => {})
          throw error
        }
      }
      await chrome.runtime.sendMessage({ kind: 'CLEAR_DRAFT' })
      draft.imageData = ''
      finished = true
      showStatus('图片、标题、正文和有效话题已填入。请逐项检查，然后由你点击小红书的发布按钮。')
    } catch (error) {
      waitingForPublish = false
      showStatus(error instanceof Error ? error.message : '自动填入失败，请检查页面后重试。', true)
    } finally {
      running = false
    }
  }

  void watchPublication()
  void run()
  const navigationCheck = setInterval(() => {
    if (finished) {
      clearInterval(navigationCheck)
    } else if (waitingForPublish && isPublishPage() && !running) {
      void run()
    }
  }, 1500)
})()
