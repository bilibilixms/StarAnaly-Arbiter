<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { deleteXiaohongshuNote, generateXiaohongshuDraft, listXiaohongshuNotes,
  loadXiaohongshuImage, markXiaohongshuNotePublished, saveXiaohongshuNote,
  type XiaohongshuDraft, type XiaohongshuNote } from '@/api/sharing'

const maxImageBytes = 20_000_000
const supportedTypes = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])
const publishUrl = 'https://creator.xiaohongshu.com/publish/publish?target=image'
const appSource = 'staranaly-xhs-app'
const extensionSource = 'staranaly-xhs-extension'

const image = ref<File | null>(null)
const imageUrl = ref('')
const requirements = ref('')
const draft = ref<XiaohongshuDraft | null>(null)
const tagsText = ref('')
const generating = ref(false)
const error = ref('')
const copyStatus = ref('')
const publishStatus = ref('')
const extensionReady = ref(false)
const publicationSyncReady = ref(false)
const preparingPublish = ref(false)
const pendingRequestId = ref('')
const currentNote = ref<XiaohongshuNote | null>(null)
const imageChanged = ref(false)
const savedNotes = ref<XiaohongshuNote[]>([])
const publishedNotes = ref<XiaohongshuNote[]>([])
const activeTab = ref<'DRAFT' | 'PUBLISHED'>('DRAFT')
const loadingNotes = ref(false)
const loadingNote = ref(false)
const saving = ref(false)
const libraryError = ref('')
const saveStatus = ref('')
let publishTimeout = 0
const processingPublicationEvents = new Set<string>()

const imageSize = computed(() => image.value ? `${(image.value.size / 1_000_000).toFixed(1)} MB` : '')
const isPublished = computed(() => currentNote.value?.status === 'PUBLISHED')
const isDirty = computed(() => {
  const note = currentNote.value
  if (!note || !draft.value) return false
  return imageChanged.value || note.requirements !== requirements.value.trim()
    || note.title !== draft.value.title.trim() || note.body !== draft.value.body.trim()
    || note.tagsText !== tagsText.value.trim()
})
const visibleNotes = computed(() => activeTab.value === 'DRAFT' ? savedNotes.value : publishedNotes.value)
function formatNoteTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })
    .format(new Date(`${value}Z`))
}
const canShareImage = computed(() => {
  if (!image.value || typeof navigator.share !== 'function' || typeof navigator.canShare !== 'function') return false
  try {
    return navigator.canShare({ files: [image.value] })
  } catch {
    return false
  }
})

function selectImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  error.value = ''
  if (!supportedTypes.has(file.type)) {
    error.value = '请选择 JPEG、PNG、GIF 或 WebP 图片。'
    return
  }
  if (file.size === 0 || file.size > maxImageBytes) {
    error.value = '图片必须非空，且不能超过 20 MB。'
    return
  }
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  image.value = file
  imageUrl.value = URL.createObjectURL(file)
  imageChanged.value = true
  saveStatus.value = ''
}

function clearImage() {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  imageUrl.value = ''
  image.value = null
  draft.value = null
  copyStatus.value = ''
  publishStatus.value = ''
  currentNote.value = null
  imageChanged.value = false
}

function resetEditor() {
  clearImage()
  requirements.value = ''
  tagsText.value = ''
  error.value = ''
  saveStatus.value = ''
}

function startNew() {
  if (generating.value || preparingPublish.value || saving.value || loadingNote.value) return
  if ((isDirty.value || (draft.value && !currentNote.value))
      && !window.confirm('当前修改尚未保存，确定开始新创作吗？')) return
  resetEditor()
}

function edited() {
  copyStatus.value = ''
  publishStatus.value = ''
  saveStatus.value = ''
}

async function refreshNotes() {
  loadingNotes.value = true
  libraryError.value = ''
  try {
    const [drafts, published] = await Promise.all([
      listXiaohongshuNotes('DRAFT'), listXiaohongshuNotes('PUBLISHED'),
    ])
    savedNotes.value = drafts
    publishedNotes.value = published
  } catch (cause) {
    libraryError.value = cause instanceof Error ? cause.message : '读取文案列表失败。'
  } finally {
    loadingNotes.value = false
  }
}

async function openNote(note: XiaohongshuNote) {
  if (loadingNote.value || saving.value || generating.value || preparingPublish.value) return
  if ((isDirty.value || (draft.value && !currentNote.value))
      && !window.confirm('当前修改尚未保存，确定打开其他文案吗？')) return
  loadingNote.value = true
  libraryError.value = ''
  try {
    const file = await loadXiaohongshuImage(note)
    resetEditor()
    image.value = file
    imageUrl.value = URL.createObjectURL(file)
    requirements.value = note.requirements
    draft.value = { title: note.title, body: note.body, hashtags: [] }
    tagsText.value = note.tagsText
    currentNote.value = note
    imageChanged.value = false
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (cause) {
    libraryError.value = cause instanceof Error ? cause.message : '打开文案失败。'
  } finally {
    loadingNote.value = false
  }
}

async function saveNote(): Promise<XiaohongshuNote | null> {
  if (saving.value || generating.value || !draft.value || !image.value || isPublished.value) return null
  error.value = ''
  saveStatus.value = ''
  const title = draft.value.title.trim()
  const body = draft.value.body.trim()
  const tags = tagsText.value.trim()
  if (!requirements.value.trim() || requirements.value.trim().length > 2000) {
    error.value = '请填写不超过 2000 字的发布要求。'
    return null
  }
  if (!title || Array.from(title).length > 20) {
    error.value = '标题需为 1 至 20 字。'
    return null
  }
  if (!body || Array.from([body, tags].filter(Boolean).join('\n\n')).length > 1000) {
    error.value = '正文与话题合计不能超过 1000 字。'
    return null
  }
  saving.value = true
  try {
    const saved = await saveXiaohongshuNote({
      image: currentNote.value && !imageChanged.value ? null : image.value,
      requirements: requirements.value.trim(), title, body, tagsText: tags,
    }, currentNote.value?.id)
    currentNote.value = saved
    imageChanged.value = false
    activeTab.value = 'DRAFT'
    saveStatus.value = '文案已保存，可以稍后从草稿列表继续编辑或发布。'
    await refreshNotes()
    return saved
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存失败，请稍后重试。'
    return null
  } finally {
    saving.value = false
  }
}

async function markPublished() {
  const note = currentNote.value
  if (!note || isDirty.value || saving.value || preparingPublish.value) return
  if (!window.confirm('确认已经在小红书完成发布？确认后将移入已发布文案。')) return
  saving.value = true
  try {
    currentNote.value = await markXiaohongshuNotePublished(note.id)
    saveStatus.value = '已移入发布记录。'
    activeTab.value = 'PUBLISHED'
    await refreshNotes()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '标记失败。'
  } finally {
    saving.value = false
  }
}

async function removeNote(note: XiaohongshuNote) {
  if (!window.confirm(`确定删除草稿“${note.title}”吗？图片和文案将一并删除。`)) return
  libraryError.value = ''
  try {
    await deleteXiaohongshuNote(note.id)
    if (currentNote.value?.id === note.id) resetEditor()
    await refreshNotes()
  } catch (cause) {
    libraryError.value = cause instanceof Error ? cause.message : '删除失败。'
  }
}

async function generate() {
  if (generating.value || preparingPublish.value || isPublished.value) return
  error.value = ''
  copyStatus.value = ''
  publishStatus.value = ''
  if (!image.value) {
    error.value = '请先选择一张图片。'
    return
  }
  const request = requirements.value.trim()
  if (!request || request.length > 2000) {
    error.value = '请填写不超过 2000 字的发布要求。'
    return
  }
  generating.value = true
  try {
    const result = await generateXiaohongshuDraft(image.value, request)
    draft.value = result
    tagsText.value = result.hashtags.map((tag) => `#${tag.replace(/^#+/, '')}`).join(' ')
    saveStatus.value = ''
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '生成失败，请稍后重试。'
  } finally {
    generating.value = false
  }
}

async function copyText(content: string, successMessage: string) {
  try {
    await navigator.clipboard.writeText(content)
    copyStatus.value = successMessage
  } catch {
    copyStatus.value = '复制失败，请手动选中文案复制。'
  }
}

async function copyTitle() {
  if (!draft.value) return
  await copyText(draft.value.title.trim(), '标题已复制，可粘贴到小红书标题栏。')
}

async function copyBody() {
  if (!draft.value) return
  await copyText(bodyWithTags(), '正文与话题已复制，可粘贴到小红书正文栏。')
}

function bodyWithTags() {
  return [draft.value?.body.trim(), tagsText.value.trim()].filter(Boolean).join('\n\n')
}

async function recordAutomaticPublication(eventId: string, noteId: number) {
  if (processingPublicationEvents.has(eventId)) return
  processingPublicationEvents.add(eventId)
  try {
    const published = await markXiaohongshuNotePublished(noteId)
    if (currentNote.value?.id === noteId) currentNote.value = published
    activeTab.value = 'PUBLISHED'
    publishStatus.value = '已检测到小红书发布成功，文案已自动移入“已发布”。'
    await refreshNotes()
    window.postMessage({ source: appSource, kind: 'ACK_PUBLICATION', eventId }, location.origin)
  } catch (cause) {
    libraryError.value = cause instanceof Error ? cause.message : '自动更新发布记录失败；请刷新页面重试。'
  } finally {
    processingPublicationEvents.delete(eventId)
  }
}

function handleExtensionMessage(event: MessageEvent) {
  if (event.source !== window || event.origin !== location.origin) return
  const message = event.data
  if (!message || message.source !== extensionSource) return
  if (message.kind === 'READY') {
    extensionReady.value = true
    publicationSyncReady.value = message.publicationSync === true
    return
  }
  if (message.kind === 'PUBLISHED' && typeof message.requestId === 'string'
      && Number.isSafeInteger(message.noteId) && message.noteId > 0) {
    void recordAutomaticPublication(message.requestId, message.noteId)
    return
  }
  if (message.kind !== 'PREPARED' || message.requestId !== pendingRequestId.value) return
  window.clearTimeout(publishTimeout)
  pendingRequestId.value = ''
  preparingPublish.value = false
  publishStatus.value = message.ok
    ? '扩展已打开小红书发布页，正在自动填入图片和文案。请到新标签页检查。'
    : (typeof message.error === 'string' ? message.error : '扩展未能打开发布页，请重试。')
}

function readImage(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => typeof reader.result === 'string'
      ? resolve(reader.result)
      : reject(new Error('无法读取图片。'))
    reader.onerror = () => reject(new Error('无法读取图片。'))
    reader.readAsDataURL(file)
  })
}

async function publishWithExtension() {
  if (!draft.value || !image.value || preparingPublish.value || saving.value || generating.value) return
  publishStatus.value = ''
  if (!draft.value.title.trim() || Array.from(draft.value.title.trim()).length > 20) {
    publishStatus.value = '请将标题调整为 1 至 20 字后再去发布。'
    return
  }
  if (!bodyWithTags() || Array.from(bodyWithTags()).length > 1000) {
    publishStatus.value = '正文与话题合计不能超过 1000 字，请先精简文案。'
    return
  }
  preparingPublish.value = true
  try {
    const note = !currentNote.value || isDirty.value ? await saveNote() : currentNote.value
    if (!note) {
      publishStatus.value = '请先修正文案保存失败的问题，再去发布。'
      return
    }
    const requestId = crypto.randomUUID()
    const payload = {
      noteId: note.id,
      title: draft.value.title.trim(),
      body: bodyWithTags(),
      imageName: image.value.name,
      imageType: image.value.type,
      imageData: await readImage(image.value),
    }
    pendingRequestId.value = requestId
    window.postMessage({ source: appSource, kind: 'PREPARE', requestId, draft: payload }, location.origin)
    publishTimeout = window.setTimeout(() => {
      if (pendingRequestId.value !== requestId) return
      pendingRequestId.value = ''
      preparingPublish.value = false
      publishStatus.value = '扩展没有及时响应，请刷新页面并确认扩展已启用。'
    }, 45000)
  } catch (cause) {
    publishStatus.value = cause instanceof Error ? cause.message : '无法把图片交给扩展。'
  } finally {
    if (!pendingRequestId.value) preparingPublish.value = false
  }
}

async function shareViaDevice() {
  if (!draft.value || !image.value) return
  publishStatus.value = ''
  try {
    await navigator.share({
      files: [image.value],
      title: draft.value.title.trim(),
      text: bodyWithTags(),
    })
    publishStatus.value = '系统分享已打开。请在小红书发布页核对图片和文案，再确认发布。'
  } catch (cause) {
    publishStatus.value = cause instanceof DOMException && cause.name === 'AbortError'
      ? '已取消系统分享。'
      : '系统分享未能传送内容，请改用网页版发布。'
  }
}

function prepareWebPublish() {
  if (!draft.value) return
  publishStatus.value = ''
  if (typeof navigator.clipboard?.writeText !== 'function') {
    publishStatus.value = '浏览器无法自动复制文案，请返回此页使用复制按钮。'
    return
  }
  void navigator.clipboard.writeText(bodyWithTags())
    .then(() => { publishStatus.value = '正文与话题已复制。请在小红书上传原图并粘贴正文，标题可在此页单独复制。' })
    .catch(() => { publishStatus.value = '浏览器未能自动复制文案，请返回此页使用复制按钮。' })
}

onMounted(() => {
  window.addEventListener('message', handleExtensionMessage)
  window.postMessage({ source: appSource, kind: 'PING' }, location.origin)
  void refreshNotes()
})

onUnmounted(() => {
  window.removeEventListener('message', handleExtensionMessage)
  window.clearTimeout(publishTimeout)
  clearImage()
})
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="eyebrow">分享空间 / 小红书</p>
      <h1>小红书图文创作</h1>
      <p class="page-description">上传图片并告诉星析你的想法，生成可编辑的发布文案。</p>
    </div>
    <button class="button button--secondary" type="button" :disabled="generating || preparingPublish || saving || loadingNote" @click="startNew">新建图文</button>
  </section>

  <div class="share-layout">
    <section class="panel share-compose" aria-label="生成发布文案">
      <h2>创作素材</h2>
      <form @submit.prevent="generate">
        <div class="field">
          <label for="share-image">上传图片 <span class="required">*</span></label>
          <input id="share-image" type="file" accept="image/jpeg,image/png,image/gif,image/webp"
            :disabled="generating || preparingPublish || isPublished || saving" @change="selectImage" />
          <p class="field-hint">支持 JPEG、PNG、GIF、WebP，单张最大 20 MB。</p>
        </div>
        <div v-if="image && imageUrl" class="share-image-preview">
          <img :src="imageUrl" :alt="`待创作图片：${image.name}`" />
          <div>
            <strong>{{ image.name }}</strong>
            <span class="muted">{{ imageSize }}</span>
          <button v-if="!isPublished" class="text-button" type="button" :disabled="generating || preparingPublish || saving" @click="startNew">清空当前创作</button>
          </div>
        </div>
        <div class="field">
          <label for="share-requirements">文字要求 <span class="required">*</span></label>
          <textarea id="share-requirements" v-model="requirements" rows="7" maxlength="2000"
            :disabled="generating || preparingPublish || isPublished || saving" placeholder="例如：根据图片写一篇周末探店笔记，语气轻松，突出环境和拍照氛围。"
            @input="edited"></textarea>
          <p class="field-hint">模型会识别图片，再结合你的要求生成标题、正文和话题。最多 2000 字。</p>
        </div>
        <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
        <div class="form-actions">
          <button v-if="!isPublished" class="button" type="submit" :disabled="generating || preparingPublish || saving">
            {{ generating ? '正在识别图片并生成…' : draft ? '重新生成' : '生成发布预览' }}
          </button>
        </div>
      </form>
    </section>

    <section class="panel share-result" aria-label="发布模板预览">
      <div class="share-result__heading">
        <div>
          <p class="eyebrow">发布模板</p>
          <h2>笔记预览</h2>
        </div>
        <span v-if="draft" class="status-badge">{{ isPublished ? '已发布记录' : currentNote ? isDirty ? '有未保存修改' : '已保存草稿' : '未保存' }}</span>
      </div>
      <div v-if="!draft" class="share-result__empty">
        <span aria-hidden="true">✦</span>
        <p>{{ generating ? '正在根据图片和文字要求创作…' : '生成后将在这里看到图片、标题和正文。' }}</p>
      </div>
      <template v-else>
        <div class="share-note">
          <img :src="imageUrl" :alt="image?.name || '发布图片'" />
          <div class="share-note__content">
            <label for="share-title">标题</label>
            <input id="share-title" v-model="draft.title" maxlength="20" :disabled="generating || preparingPublish || saving || isPublished" @input="edited" />
            <label for="share-body">正文</label>
            <textarea id="share-body" v-model="draft.body" rows="10" maxlength="5000" :disabled="generating || preparingPublish || saving || isPublished" @input="edited"></textarea>
            <label for="share-tags">话题标签</label>
            <input id="share-tags" v-model="tagsText" maxlength="300" placeholder="#话题1 #话题2" :disabled="generating || preparingPublish || saving || isPublished" @input="edited" />
            <p class="field-hint">发布时标题最多 20 字，正文与话题合计最多 1000 字。</p>
          </div>
        </div>
        <div class="share-result__actions">
          <button v-if="!isPublished" class="button" type="button" :disabled="saving || generating || preparingPublish || !image || (!isDirty && !!currentNote)"
            @click="saveNote">{{ saving ? '正在保存…' : currentNote ? '保存修改' : '保存当前文案' }}</button>
          <button class="button button--secondary" type="button" @click="copyTitle">复制标题</button>
          <button class="button button--secondary" type="button" @click="copyBody">复制正文与话题</button>
          <button v-if="!isPublished && extensionReady" class="button" type="button" :disabled="preparingPublish || saving"
            @click="publishWithExtension">{{ preparingPublish ? '正在交给扩展…' : '去发布（自动填入）' }}</button>
          <button v-else-if="!isPublished && canShareImage" class="button" type="button" :disabled="saving" @click="shareViaDevice">去发布（系统分享）</button>
          <a v-else-if="!isPublished" class="button" :href="publishUrl" target="_blank" rel="noopener noreferrer"
            @click="prepareWebPublish">复制正文并打开发布页</a>
          <a v-if="!isPublished && (extensionReady || canShareImage)" class="text-button share-result__web-link" :href="publishUrl"
            target="_blank" rel="noopener noreferrer" @click="prepareWebPublish">打开网页版</a>
          <button v-if="currentNote && !isPublished && !publicationSyncReady" class="button button--secondary" type="button"
            :disabled="saving || preparingPublish || isDirty" @click="markPublished">已在小红书发布</button>
        </div>
        <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
        <p v-if="saveStatus" class="field-hint" role="status">{{ saveStatus }}</p>
        <p v-if="copyStatus" class="field-hint" role="status">{{ copyStatus }}</p>
        <p v-if="publishStatus" class="field-hint" role="status">{{ publishStatus }}</p>
        <p v-if="currentNote && isDirty" class="field-hint">修改后请先保存，再去发布。</p>
        <p v-if="!isPublished" class="field-hint">{{ publicationSyncReady
          ? '去发布时会先保存当前文案。扩展填入内容后请亲自点击小红书的发布按钮；检测到发布成功提示后，文案会自动移入“已发布”。'
          : extensionReady
            ? '当前扩展版本尚未启用发布成功自动同步。请在扩展管理页重新加载扩展，再刷新星析页面。'
          : canShareImage
            ? '系统分享会尝试发送原图、标题和正文；请选择小红书，并检查实际带入的内容。'
            : '网页版无法自动填入图片和文案；点击后会复制正文与话题并打开小红书，请手动上传原图、粘贴正文和标题。' }}</p>
        <details v-if="!isPublished && !extensionReady" class="share-extension-help">
          <summary>电脑自动填入：安装浏览器扩展</summary>
          <ol>
            <li>在 Chrome 或 Edge 的扩展管理页开启“开发者模式”。</li>
            <li>点击“加载已解压的扩展程序”，选择项目中的 <code>browser-extension/xiaohongshu</code> 文件夹。</li>
            <li>刷新星析页面；检测到扩展后，“去发布”会自动带入图片和文案。</li>
          </ol>
        </details>
        <details v-if="currentNote && !isPublished && publicationSyncReady" class="share-extension-help">
          <summary>发布后没有自动同步？</summary>
          <p>小红书页面变化时，扩展可能无法识别成功提示。确认笔记已经发布后，可手动补记。</p>
          <button class="button button--secondary" type="button" :disabled="saving || preparingPublish || isDirty"
            @click="markPublished">手动标记为已发布</button>
        </details>
      </template>
    </section>
  </div>

  <section class="panel share-library" aria-label="我的小红书文案">
    <div class="share-library__heading">
      <div>
        <p class="eyebrow">我的文案</p>
        <h2>创作记录</h2>
      </div>
      <button class="text-button" type="button" :disabled="loadingNotes" @click="refreshNotes">刷新列表</button>
    </div>
    <div class="share-library__tabs" role="tablist" aria-label="文案状态">
      <button type="button" role="tab" :aria-selected="activeTab === 'DRAFT'" :class="{ 'is-active': activeTab === 'DRAFT' }"
        @click="activeTab = 'DRAFT'">已保存 {{ savedNotes.length }}</button>
      <button type="button" role="tab" :aria-selected="activeTab === 'PUBLISHED'" :class="{ 'is-active': activeTab === 'PUBLISHED' }"
        @click="activeTab = 'PUBLISHED'">已发布 {{ publishedNotes.length }}</button>
    </div>
    <p v-if="libraryError" class="inline-error" role="alert">{{ libraryError }}</p>
    <p v-if="loadingNotes" class="field-hint">正在读取文案…</p>
    <p v-else-if="visibleNotes.length === 0" class="share-library__empty">
      {{ activeTab === 'DRAFT' ? '还没有保存的文案。生成后点击“保存当前文案”。' : '还没有已发布的文案。发布成功后可从草稿中手动标记。' }}
    </p>
    <ul v-else class="share-library__list">
      <li v-for="note in visibleNotes" :key="note.id" class="share-library__item">
        <div>
          <strong>{{ note.title }}</strong>
          <p>{{ note.body }}</p>
          <span>{{ activeTab === 'DRAFT' ? '更新于' : '发布于' }} {{ formatNoteTime(note.publishedAt || note.updatedAt) }}</span>
        </div>
        <div class="share-library__actions">
          <button class="button button--secondary" type="button" :disabled="loadingNote || saving || generating || preparingPublish" @click="openNote(note)">
            {{ activeTab === 'DRAFT' ? '打开编辑 / 发布' : '查看文案' }}
          </button>
          <button v-if="note.status === 'DRAFT'" class="text-button text-button--danger" type="button"
            :disabled="loadingNote || saving || generating || preparingPublish" @click="removeNote(note)">删除</button>
        </div>
      </li>
    </ul>
  </section>
</template>
