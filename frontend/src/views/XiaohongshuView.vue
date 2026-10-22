<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { generateXiaohongshuDraft, type XiaohongshuDraft } from '@/api/sharing'

const maxImageBytes = 20_000_000
const supportedTypes = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])
const publishUrl = 'https://creator.xiaohongshu.com/publish/publish?target=image'

const image = ref<File | null>(null)
const imageUrl = ref('')
const requirements = ref('')
const draft = ref<XiaohongshuDraft | null>(null)
const tagsText = ref('')
const generating = ref(false)
const error = ref('')
const copyStatus = ref('')

const imageSize = computed(() => image.value ? `${(image.value.size / 1_000_000).toFixed(1)} MB` : '')

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
}

function invalidateDraft() {
  draft.value = null
  copyStatus.value = ''
}

async function generate() {
  if (generating.value) return
  error.value = ''
  copyStatus.value = ''
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
  const content = [draft.value.body.trim(), tagsText.value.trim()]
    .filter(Boolean).join('\n\n')
  await copyText(content, '正文与话题已复制，可粘贴到小红书正文栏。')
}

onUnmounted(clearImage)
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
            :disabled="generating" @change="selectImage" />
          <p class="field-hint">支持 JPEG、PNG、GIF、WebP，单张最大 20 MB。</p>
        </div>
        <div v-if="image && imageUrl" class="share-image-preview">
          <img :src="imageUrl" :alt="`待创作图片：${image.name}`" />
          <div>
            <strong>{{ image.name }}</strong>
            <span class="muted">{{ imageSize }}</span>
            <button class="text-button" type="button" :disabled="generating" @click="clearImage">移除图片</button>
          </div>
        </div>
        <div class="field">
          <label for="share-requirements">文字要求 <span class="required">*</span></label>
          <textarea id="share-requirements" v-model="requirements" rows="7" maxlength="2000"
            :disabled="generating" placeholder="例如：根据图片写一篇周末探店笔记，语气轻松，突出环境和拍照氛围。"
            @input="invalidateDraft"></textarea>
          <p class="field-hint">模型会识别图片，再结合你的要求生成标题、正文和话题。最多 2000 字。</p>
        </div>
        <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
        <div class="form-actions">
          <button class="button" type="submit" :disabled="generating">
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
            <input id="share-title" v-model="draft.title" maxlength="100" />
            <label for="share-body">正文</label>
            <textarea id="share-body" v-model="draft.body" rows="10" maxlength="5000"></textarea>
            <label for="share-tags">话题标签</label>
            <input id="share-tags" v-model="tagsText" placeholder="#话题1 #话题2" />
          </div>
        </div>
        <div class="share-result__actions">
          <button class="button button--secondary" type="button" @click="copyTitle">复制标题</button>
          <button class="button button--secondary" type="button" @click="copyBody">复制正文与话题</button>
          <a class="button" :href="publishUrl" target="_blank" rel="noopener noreferrer">去发布</a>
        </div>
        <p v-if="copyStatus" class="field-hint" role="status">{{ copyStatus }}</p>
        <p class="field-hint">小红书发布页会在新标签打开；请上传原图，分别粘贴标题和正文，确认后发布。</p>
      </template>
    </section>
  </div>
</template>
