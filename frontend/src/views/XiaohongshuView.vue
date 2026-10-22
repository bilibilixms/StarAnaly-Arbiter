<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { generateXiaohongshuDraft, type XiaohongshuDraft } from '@/api/sharing'

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
const preparingPublish = ref(false)
const pendingRequestId = ref('')
let publishTimeout = 0

const imageSize = computed(() => image.value ? `${(image.value.size / 1_000_000).toFixed(1)} MB` : '')
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
  clearImage()
  image.value = file
  imageUrl.value = URL.createObjectURL(file)
}

function clearImage() {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  imageUrl.value = ''
  image.value = null
  draft.value = null
  copyStatus.value = ''
  publishStatus.value = ''
}

function invalidateDraft() {
  draft.value = null
  copyStatus.value = ''
  publishStatus.value = ''
}

async function generate() {
  if (generating.value || preparingPublish.value) return
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
  draft.value = null
  try {
    const result = await generateXiaohongshuDraft(image.value, request)
    draft.value = result
    tagsText.value = result.hashtags.map((tag) => `#${tag.replace(/^#+/, '')}`).join(' ')
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

function handleExtensionMessage(event: MessageEvent) {
  if (event.source !== window || event.origin !== location.origin) return
  const message = event.data
  if (!message || message.source !== extensionSource) return
  if (message.kind === 'READY') {
    extensionReady.value = true
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
  if (!draft.value || !image.value || preparingPublish.value) return
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
    const requestId = crypto.randomUUID()
    const payload = {
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
    preparingPublish.value = false
    publishStatus.value = cause instanceof Error ? cause.message : '无法把图片交给扩展。'
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
  </section>

  <div class="share-layout">
    <section class="panel share-compose" aria-label="生成发布文案">
      <h2>创作素材</h2>
      <form @submit.prevent="generate">
        <div class="field">
          <label for="share-image">上传图片 <span class="required">*</span></label>
          <input id="share-image" type="file" accept="image/jpeg,image/png,image/gif,image/webp"
            :disabled="generating || preparingPublish" @change="selectImage" />
          <p class="field-hint">支持 JPEG、PNG、GIF、WebP，单张最大 20 MB。</p>
        </div>
        <div v-if="image && imageUrl" class="share-image-preview">
          <img :src="imageUrl" :alt="`待创作图片：${image.name}`" />
          <div>
            <strong>{{ image.name }}</strong>
            <span class="muted">{{ imageSize }}</span>
            <button class="text-button" type="button" :disabled="generating || preparingPublish" @click="clearImage">移除图片</button>
          </div>
        </div>
        <div class="field">
          <label for="share-requirements">文字要求 <span class="required">*</span></label>
          <textarea id="share-requirements" v-model="requirements" rows="7" maxlength="2000"
            :disabled="generating || preparingPublish" placeholder="例如：根据图片写一篇周末探店笔记，语气轻松，突出环境和拍照氛围。"
            @input="invalidateDraft"></textarea>
          <p class="field-hint">模型会识别图片，再结合你的要求生成标题、正文和话题。最多 2000 字。</p>
        </div>
        <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
        <div class="form-actions">
          <button class="button" type="submit" :disabled="generating || preparingPublish">
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
        <span v-if="draft" class="status-badge">可编辑</span>
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
            <input id="share-title" v-model="draft.title" maxlength="20" :disabled="preparingPublish" />
            <label for="share-body">正文</label>
            <textarea id="share-body" v-model="draft.body" rows="10" maxlength="5000" :disabled="preparingPublish"></textarea>
            <label for="share-tags">话题标签</label>
            <input id="share-tags" v-model="tagsText" maxlength="300" placeholder="#话题1 #话题2" :disabled="preparingPublish" />
            <p class="field-hint">发布时标题最多 20 字，正文与话题合计最多 1000 字。</p>
          </div>
        </div>
        <div class="share-result__actions">
          <button class="button button--secondary" type="button" @click="copyTitle">复制标题</button>
          <button class="button button--secondary" type="button" @click="copyBody">复制正文与话题</button>
          <button v-if="extensionReady" class="button" type="button" :disabled="preparingPublish"
            @click="publishWithExtension">{{ preparingPublish ? '正在交给扩展…' : '去发布（自动填入）' }}</button>
          <button v-else-if="canShareImage" class="button" type="button" @click="shareViaDevice">去发布（系统分享）</button>
          <a v-else class="button" :href="publishUrl" target="_blank" rel="noopener noreferrer"
            @click="prepareWebPublish">复制正文并打开发布页</a>
          <a v-if="extensionReady || canShareImage" class="text-button share-result__web-link" :href="publishUrl"
            target="_blank" rel="noopener noreferrer" @click="prepareWebPublish">打开网页版</a>
        </div>
        <p v-if="copyStatus" class="field-hint" role="status">{{ copyStatus }}</p>
        <p v-if="publishStatus" class="field-hint" role="status">{{ publishStatus }}</p>
        <p class="field-hint">{{ extensionReady
          ? '扩展会打开小红书并尝试填入原图、标题和正文；请检查后亲自点击发布。'
          : canShareImage
            ? '系统分享会尝试发送原图、标题和正文；请选择小红书，并检查实际带入的内容。'
            : '网页版无法自动填入图片和文案；点击后会复制正文与话题并打开小红书，请手动上传原图、粘贴正文和标题。' }}</p>
        <details v-if="!extensionReady" class="share-extension-help">
          <summary>电脑自动填入：安装浏览器扩展</summary>
          <ol>
            <li>在 Chrome 或 Edge 的扩展管理页开启“开发者模式”。</li>
            <li>点击“加载已解压的扩展程序”，选择项目中的 <code>browser-extension/xiaohongshu</code> 文件夹。</li>
            <li>刷新星析页面；检测到扩展后，“去发布”会自动带入图片和文案。</li>
          </ol>
        </details>
      </template>
    </section>
  </div>
</template>
