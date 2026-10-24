<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { deleteDouyinMaterial, deleteDouyinNote, generateDouyinDraft,
  getDouyinNote,
  listDouyinMaterials, listDouyinNotes, saveDouyinMaterial,
  loadDouyinImages, markDouyinNotePublished, saveDouyinNote,
  type DouyinDraft, type DouyinMaterial, type DouyinNote } from '@/api/douyin'
import { getCreativeBrief, getTask, listTaskImages, loadTaskImage,
  type CreativeBriefResponse, type TaskImage } from '@/api/tasks'
import { loadPosterTransfer, removePosterTransfer } from '@/poster/transfer'

const maxImageBytes = 20_000_000
const supportedTypes = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])
const publishUrl = 'https://creator.douyin.com/'
const route = useRoute()
const router = useRouter()
const isRecordsPage = computed(() => route.name === 'share-douyin-records')
const isMaterialsPage = computed(() => route.name === 'share-douyin-materials')

const image = ref<File | null>(null)
const imageUrl = ref('')
const extraImages = ref<File[]>([])
const extraImageUrls = ref<string[]>([])
const requirements = ref('')
const draft = ref<DouyinDraft | null>(null)
const tagsText = ref('')
const generating = ref(false)
const error = ref('')
const copyStatus = ref('')
const publishStatus = ref('')
const preparingPublish = ref(false)
const currentNote = ref<DouyinNote | null>(null)
const imageChanged = ref(false)
const savedNotes = ref<DouyinNote[]>([])
const publishedNotes = ref<DouyinNote[]>([])
const materials = ref<DouyinMaterial[]>([])
const selectedMaterialIds = ref<number[]>([])
const linkedTaskId = ref<number | null>(null)
const linkedTaskTitle = ref('')
const linkedBrief = ref<CreativeBriefResponse | null>(null)
const linkedDirection = ref<number | null>(null)
const linkedImages = ref<TaskImage[]>([])
const loadingTaskContext = ref(false)
let routeContextVersion = 0
const materialEditorOpen = ref(false)
const materialEditingId = ref<number | null>(null)
const materialName = ref('')
const materialContent = ref('')
const materialFile = ref<File | null>(null)
const materialMode = ref<'text' | 'file'>('text')
const materialSaving = ref(false)
const materialError = ref('')
const materialStatus = ref('')
const materialEditorElement = ref<HTMLElement | null>(null)
const activeTab = ref<'DRAFT' | 'PUBLISHED'>('DRAFT')
const loadingNotes = ref(false)
const loadingNote = ref(false)
const saving = ref(false)
const libraryError = ref('')
const saveStatus = ref('')

const allImages = computed(() => image.value ? [image.value, ...extraImages.value] : [])
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
    return navigator.canShare({ files: allImages.value })
  } catch {
    return false
  }
})

function selectImage(event: Event) {
  const input = event.target as HTMLInputElement
  const files = [...(input.files ?? [])]
  input.value = ''
  if (!files.length) return
  error.value = ''
  if (files.length > 4) { error.value = '一次最多选择 4 张图片。'; return }
  if (files.some((file) => !supportedTypes.has(file.type))) {
    error.value = '请选择 JPEG、PNG、GIF 或 WebP 图片。'
    return
  }
  if (files.some((file) => file.size === 0 || file.size > maxImageBytes)) {
    error.value = '每张图片必须非空，且不能超过 20 MB。'
    return
  }
  useImages(files)
}

function useImage(file: File) {
  useImages([file])
}

function useImages(files: File[]) {
  if (!files.length || files.length > 4) throw new Error('请选择 1 至 4 张图片。')
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  extraImageUrls.value.forEach(URL.revokeObjectURL)
  image.value = files[0]!
  imageUrl.value = URL.createObjectURL(files[0]!)
  extraImages.value = files.slice(1)
  extraImageUrls.value = files.slice(1).map(URL.createObjectURL)
  imageChanged.value = true
  saveStatus.value = ''
}

async function chooseLinkedImage(item: TaskImage) {
  const id = linkedTaskId.value
  if (id === null || generating.value || saving.value) return
  loadingTaskContext.value = true
  error.value = ''
  try {
    const file = await loadTaskImage(id, item)
    if (linkedTaskId.value === id) useImage(file)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '读取需求图片失败。'
  } finally {
    loadingTaskContext.value = false
  }
}

async function loadRouteContext() {
  const version = ++routeContextVersion
  const noteId = Number(route.query.noteId)
  if (Number.isSafeInteger(noteId) && noteId > 0) {
    loadingTaskContext.value = true
    try {
      const note = await getDouyinNote(noteId)
      if (version !== routeContextVersion) return
      const opened = currentNote.value?.id === note.id || await openNote(note)
      if (!opened) return
      linkedTaskId.value = note.taskId
      if (note.taskId) {
        const [task, images, brief] = await Promise.all([getTask(note.taskId), listTaskImages(note.taskId), getCreativeBrief(note.taskId)])
        if (version === routeContextVersion) {
          linkedTaskTitle.value = task.title
          linkedImages.value = images
          linkedBrief.value = brief
          linkedDirection.value = note.directionIndex
        }
      }
    } catch (cause) {
      if (version === routeContextVersion) error.value = cause instanceof Error ? cause.message : '打开关联文案失败。'
    } finally {
      if (version === routeContextVersion) loadingTaskContext.value = false
    }
    return
  }
  const id = Number(route.query.taskId)
  if (!Number.isSafeInteger(id) || id <= 0) {
    if (currentNote.value) return
    linkedTaskId.value = null
    linkedTaskTitle.value = ''
    linkedBrief.value = null
    linkedDirection.value = null
    linkedImages.value = []
    return
  }
  const direction = Number(route.query.direction)
  loadingTaskContext.value = true
  error.value = ''
  try {
    const [task, brief, images] = await Promise.all([getTask(id), getCreativeBrief(id), listTaskImages(id)])
    if (version !== routeContextVersion) return
    if (route.query.direction !== undefined && (!brief || !Number.isInteger(direction)
        || direction < 0 || direction >= brief.directions.length)) {
      error.value = '宣传方向无效，请从工作台重新选择。'
      return
    }
    if (currentNote.value || draft.value) {
      if ((isDirty.value || (draft.value && !currentNote.value))
          && !window.confirm('当前文案有未保存修改，确定切换到另一份需求吗？')) return
      resetEditor()
    }
    linkedTaskId.value = id
    linkedTaskTitle.value = task.title
    linkedBrief.value = brief
    linkedDirection.value = route.query.direction !== undefined ? direction : null
    linkedImages.value = images
    const chosen = linkedDirection.value !== null ? brief?.directions[linkedDirection.value] : null
    requirements.value = [task.description, task.discussionSummary,
      chosen ? `宣传方向：${chosen.name}。切入点：${chosen.angle}。` : ''].filter(Boolean).join('\n').slice(0, 2000)
    const posterToken = typeof route.query.poster === 'string' ? route.query.poster : ''
    if (posterToken) {
      const posters = await loadPosterTransfer(posterToken)
      if (version !== routeContextVersion) return
      useImages(posters)
      return
    }
    const requestedImageId = Number(route.query.imageId)
    const initialImage = images.find((item) => item.id === requestedImageId) ?? images[0]
    if (initialImage) await chooseLinkedImage(initialImage)
  } catch (cause) {
    if (version === routeContextVersion) error.value = cause instanceof Error ? cause.message : '读取需求失败。'
  } finally {
    if (version === routeContextVersion) loadingTaskContext.value = false
  }
}

function clearImage() {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  extraImageUrls.value.forEach(URL.revokeObjectURL)
  imageUrl.value = ''
  image.value = null
  extraImages.value = []
  extraImageUrls.value = []
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
  if (!route.query.taskId) {
    linkedTaskId.value = null
    linkedTaskTitle.value = ''
    linkedBrief.value = null
    linkedDirection.value = null
    linkedImages.value = []
    if (route.query.noteId) void router.replace({ name: 'share-douyin' })
  }
}

function edited() {
  copyStatus.value = ''
  publishStatus.value = ''
  saveStatus.value = ''
}

async function refreshMaterials() {
  try {
    materials.value = await listDouyinMaterials()
    const existing = new Set(materials.value.map((item) => item.id))
    selectedMaterialIds.value = selectedMaterialIds.value.filter((id) => existing.has(id))
  } catch (cause) {
    materialError.value = cause instanceof Error ? cause.message : '读取创作素材失败。'
  }
}

function toggleMaterial(id: number, event: Event) {
  const input = event.target as HTMLInputElement
  const checked = input.checked
  materialError.value = ''
  if (checked && selectedMaterialIds.value.length >= 3) {
    input.checked = false
    materialError.value = '每次最多选用 3 份创作素材。'
    return
  }
  selectedMaterialIds.value = checked
    ? [...selectedMaterialIds.value, id]
    : selectedMaterialIds.value.filter((item) => item !== id)
}

function openMaterialEditor(material?: DouyinMaterial) {
  materialEditingId.value = material?.id ?? null
  materialName.value = material?.name ?? ''
  materialContent.value = material?.content ?? ''
  materialFile.value = null
  materialMode.value = 'text'
  materialError.value = ''
  materialStatus.value = ''
  materialEditorOpen.value = true
  void nextTick(() => materialEditorElement.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function closeMaterialEditor() {
  if (materialSaving.value) return
  materialEditorOpen.value = false
  materialFile.value = null
  materialError.value = ''
}

function selectMaterialFile(event: Event) {
  const input = event.target as HTMLInputElement
  materialFile.value = input.files?.[0] ?? null
  materialError.value = ''
}

async function saveMaterial() {
  if (materialSaving.value) return
  materialError.value = ''
  const name = materialName.value.trim()
  if (!name || name.length > 60) {
    materialError.value = '素材名称需为 1 至 60 字。'
    return
  }
  if (materialMode.value === 'file' && (!materialFile.value || materialFile.value.size > 5 * 1024 * 1024)) {
    materialError.value = '请选择不超过 5 MiB 的 PDF、DOC 或 DOCX 文档。'
    return
  }
  if (materialMode.value === 'text' && (!materialContent.value.trim() || materialContent.value.trim().length > 12000)) {
    materialError.value = '请输入 1 至 12000 字的素材内容。'
    return
  }
  materialSaving.value = true
  try {
    const saved = await saveDouyinMaterial({
      name,
      ...(materialMode.value === 'file' ? { file: materialFile.value } : { content: materialContent.value.trim() }),
    }, materialEditingId.value ?? undefined)
    materialEditorOpen.value = false
    materialFile.value = null
    materialStatus.value = materialEditingId.value ? '素材已更新，下次生成会使用最新内容。' : '素材已保存，可在之后的创作中反复选用。'
    if (materialEditingId.value === null && selectedMaterialIds.value.length < 3) selectedMaterialIds.value.push(saved.id)
    await refreshMaterials()
  } catch (cause) {
    materialError.value = cause instanceof Error ? cause.message : '保存创作素材失败。'
  } finally {
    materialSaving.value = false
  }
}

async function removeMaterial(material: DouyinMaterial) {
  if (!window.confirm(`确定删除创作素材“${material.name}”吗？`)) return
  materialError.value = ''
  try {
    await deleteDouyinMaterial(material.id)
    if (materialEditingId.value === material.id) closeMaterialEditor()
    selectedMaterialIds.value = selectedMaterialIds.value.filter((id) => id !== material.id)
    materialStatus.value = '创作素材已删除。'
    await refreshMaterials()
  } catch (cause) {
    materialError.value = cause instanceof Error ? cause.message : '删除创作素材失败。'
  }
}

async function refreshNotes() {
  loadingNotes.value = true
  libraryError.value = ''
  try {
    const [drafts, published] = await Promise.all([
      listDouyinNotes('DRAFT'), listDouyinNotes('PUBLISHED'),
    ])
    savedNotes.value = drafts
    publishedNotes.value = published
  } catch (cause) {
    libraryError.value = cause instanceof Error ? cause.message : '读取文案列表失败。'
  } finally {
    loadingNotes.value = false
  }
}

async function openNote(note: DouyinNote): Promise<boolean> {
  if (loadingNote.value || saving.value || generating.value || preparingPublish.value) return false
  if ((isDirty.value || (draft.value && !currentNote.value))
      && !window.confirm('当前修改尚未保存，确定打开其他文案吗？')) return false
  loadingNote.value = true
  libraryError.value = ''
  try {
    const files = await loadDouyinImages(note)
    resetEditor()
    useImages(files)
    requirements.value = note.requirements
    draft.value = { title: note.title, body: note.body, hashtags: [] }
    tagsText.value = note.tagsText
    currentNote.value = note
    linkedTaskId.value = note.taskId
    linkedDirection.value = note.directionIndex
    imageChanged.value = false
    if (isRecordsPage.value) await router.push({ name: 'share-douyin', query: { noteId: note.id } })
    window.scrollTo({ top: 0, behavior: 'smooth' })
    return true
  } catch (cause) {
    libraryError.value = cause instanceof Error ? cause.message : '打开文案失败。'
    return false
  } finally {
    loadingNote.value = false
  }
}

async function saveNote(): Promise<DouyinNote | null> {
  if (saving.value || generating.value || !draft.value || !image.value || isPublished.value) return null
  error.value = ''
  saveStatus.value = ''
  const title = draft.value.title.trim()
  const body = draft.value.body.trim()
  const tags = tagsText.value.trim()
  if (requirements.value.trim().length > 2000) {
    error.value = '文字要求不能超过 2000 字。'
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
    const saved = await saveDouyinNote({
      image: currentNote.value && !imageChanged.value ? null : image.value,
      extraImages: currentNote.value && !imageChanged.value ? undefined : extraImages.value,
      requirements: requirements.value.trim(), title, body, tagsText: tags,
      taskId: linkedTaskId.value, directionIndex: linkedDirection.value,
    }, currentNote.value?.id)
    currentNote.value = saved
    imageChanged.value = false
    activeTab.value = 'DRAFT'
    saveStatus.value = '文案已保存，可以稍后从草稿列表继续编辑或发布。'
    if (route.name === 'share-douyin' && Number(route.query.noteId) !== saved.id) {
      const posterToken = typeof route.query.poster === 'string' ? route.query.poster : ''
      await router.replace({ name: 'share-douyin', query: { noteId: saved.id } })
      if (posterToken) await removePosterTransfer(posterToken).catch(() => {})
    }
    await refreshNotes()
    return saved
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存失败，请稍后重试。'
    return null
  } finally {
    saving.value = false
  }
}

async function saveAsNewVersion() {
  if (!draft.value || !image.value || saving.value || isPublished.value) return
  saving.value = true
  error.value = ''
  try {
    const saved = await saveDouyinNote({
      image: image.value, extraImages: extraImages.value, requirements: requirements.value.trim(),
      title: draft.value.title.trim(), body: draft.value.body.trim(),
      tagsText: tagsText.value.trim(), taskId: linkedTaskId.value, directionIndex: linkedDirection.value,
    })
    currentNote.value = saved
    imageChanged.value = false
    saveStatus.value = '已另存为新草稿，原版本保留在创作记录中。'
    if (route.name === 'share-douyin') {
      await router.replace({ name: 'share-douyin', query: { noteId: saved.id } })
    }
    await refreshNotes()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '另存为新版本失败。'
  } finally {
    saving.value = false
  }
}

async function markPublished() {
  const note = currentNote.value
  if (!note || isDirty.value || saving.value || preparingPublish.value) return
  if (!window.confirm('确认已经在抖音完成发布？确认后将移入已发布文案。')) return
  saving.value = true
  try {
    currentNote.value = await markDouyinNotePublished(note.id)
    saveStatus.value = '已移入发布记录。'
    activeTab.value = 'PUBLISHED'
    await refreshNotes()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '标记失败。'
  } finally {
    saving.value = false
  }
}

async function removeNote(note: DouyinNote) {
  if (!window.confirm(`确定删除草稿“${note.title}”吗？图片和文案将一并删除。`)) return
  libraryError.value = ''
  try {
    await deleteDouyinNote(note.id)
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
  if (request.length > 2000) {
    error.value = '文字要求不能超过 2000 字。'
    return
  }
  generating.value = true
  try {
    const result = await generateDouyinDraft(image.value, request, selectedMaterialIds.value, extraImages.value,
      linkedTaskId.value, linkedDirection.value)
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
  await copyText(draft.value.title.trim(), '标题已复制，可粘贴到抖音标题栏。')
}

async function copyBody() {
  if (!draft.value) return
  await copyText(bodyWithTags(), '正文与话题已复制，可粘贴到抖音正文栏。')
}

function bodyWithTags() {
  return [draft.value?.body.trim(), tagsText.value.trim()].filter(Boolean).join('\n\n')
}

async function shareViaDevice() {
  if (!draft.value || !image.value) return
  publishStatus.value = ''
  try {
    await navigator.share({
      files: allImages.value,
      title: draft.value.title.trim(),
      text: bodyWithTags(),
    })
    if (!currentNote.value || isDirty.value) await saveNote()
    publishStatus.value = '系统分享已打开。请在抖音发布页核对图片和文案，再确认发布。'
  } catch (cause) {
    publishStatus.value = cause instanceof DOMException && cause.name === 'AbortError'
      ? '已取消系统分享。'
      : '系统分享未能传送内容，请改用网页版发布。'
  }
}

function prepareWebPublish() {
  if (!draft.value) return
  publishStatus.value = ''
  if (!currentNote.value || isDirty.value) void saveNote()
  if (typeof navigator.clipboard?.writeText !== 'function') {
    publishStatus.value = '浏览器无法自动复制文案，请返回此页使用复制按钮。'
    return
  }
  void navigator.clipboard.writeText(bodyWithTags())
    .then(() => { publishStatus.value = '正文与话题已复制。请在抖音上传原图并粘贴正文，标题可在此页单独复制。' })
    .catch(() => { publishStatus.value = '浏览器未能自动复制文案，请返回此页使用复制按钮。' })
}

onMounted(() => {
  void refreshNotes()
  void refreshMaterials()
})

watch(() => [route.query.taskId, route.query.direction, route.query.noteId, route.query.imageId, route.query.poster], () => {
  if (!isRecordsPage.value && !isMaterialsPage.value) void loadRouteContext()
}, { immediate: true })

onUnmounted(() => {
  routeContextVersion++
  clearImage()
})
</script>

<template>
  <div class="xhs-page douyin-page">
  <section class="page-heading xhs-heading">
    <div>
      <p class="eyebrow">分享空间 / 抖音</p>
      <h1>{{ isMaterialsPage ? '素材管理' : isRecordsPage ? '创作记录' : '抖音图文创作' }}</h1>
      <p class="page-description">{{ isMaterialsPage ? '保存自己的资料和表达方式，创作时随时选用。' : isRecordsPage ? '保存的草稿与已发布的笔记，都在这里。' : '上传图片，选用自己的创作素材，让文案更像你写的。' }}</p>
    </div>
    <button v-if="!isRecordsPage && !isMaterialsPage" class="button button--secondary" type="button" :disabled="generating || preparingPublish || saving || loadingNote" @click="startNew">新建图文</button>
    <RouterLink v-else class="button button--secondary" to="/share/douyin">去图文创作</RouterLink>
  </section>

  <div v-if="!isRecordsPage && !isMaterialsPage" class="share-layout">
    <section class="panel share-compose" aria-label="生成发布文案">
      <h2>创作素材</h2>
      <div v-if="linkedTaskId" class="xhs-linked-task">
        <strong>来自需求：{{ linkedTaskTitle }}</strong>
        <RouterLink :to="{ name: 'creative-workbench-task', params: { id: linkedTaskId } }">返回海报工作台</RouterLink>
        <p>需求文字、选中的海报和下面的补充要求会参与文案创作。</p>
        <div v-if="linkedBrief?.directions.length && linkedDirection !== null" class="field">
          <label for="linked-direction">本次宣传方向</label>
          <select id="linked-direction" v-model="linkedDirection" :disabled="generating || saving || isPublished">
            <option v-for="(direction, index) in linkedBrief.directions" :key="index" :value="index">{{ direction.name }}</option>
          </select>
        </div>
        <div v-if="linkedImages.length" class="xhs-linked-task__images">
          <button v-for="item in linkedImages" :key="item.id" type="button" :disabled="loadingTaskContext || generating || saving"
            :class="{ 'is-active': image?.name === item.name }" @click="chooseLinkedImage(item)">
            <img :src="`/api/tasks/${linkedTaskId}/images/${item.id}`" :alt="item.name" />
            <span>{{ item.name }}</span>
          </button>
        </div>
        <p v-else class="field-hint">这份需求没有图片，请在下方上传一张本次要发布的图片。</p>
      </div>
      <form @submit.prevent="generate">
        <div class="field xhs-upload-field">
          <span class="field-label">上传图片（1–4 张） <span class="required">*</span></span>
          <input id="share-image" class="xhs-file-input" type="file" aria-label="上传图片" accept="image/jpeg,image/png,image/gif,image/webp" multiple
            :disabled="generating || preparingPublish || isPublished || saving" @change="selectImage" />
          <label for="share-image" class="xhs-upload-control">
            <span class="xhs-upload-control__icon" aria-hidden="true">✦</span>
            <span class="xhs-upload-control__button">{{ image ? '更换图片' : '选择图片' }}</span>
            <span class="xhs-upload-control__hint">支持 JPEG、PNG、GIF、WebP · 最多 4 张，每张最大 20 MB</span>
          </label>
        </div>
        <div v-if="image && imageUrl" class="share-image-preview">
          <img :src="imageUrl" :alt="`待创作图片：${image.name}`" />
          <div>
            <strong>{{ image.name }}</strong>
            <span class="muted">{{ allImages.length }} 张 · 首图 {{ imageSize }}</span>
          <button v-if="!isPublished" class="text-button" type="button" :disabled="generating || preparingPublish || saving" @click="startNew">清空当前创作</button>
          </div>
        </div>
        <div v-if="extraImageUrls.length" class="xhs-extra-images" aria-label="其余待发布图片">
          <img v-for="(url, index) in extraImageUrls" :key="url" :src="url" :alt="`待发布图片 ${index + 2}`" />
        </div>
        <section class="xhs-materials" aria-label="可复用的创作素材">
          <div class="xhs-materials__heading">
            <div>
              <h3>参考资料与表达风格 <span class="xhs-optional">选填</span></h3>
              <p>选择本次要参考的素材，生成时会优先考虑其中的内容。</p>
            </div>
            <RouterLink class="button button--secondary" to="/share/douyin/materials">素材管理</RouterLink>
          </div>
          <p v-if="materials.length === 0" class="xhs-materials__empty">暂无素材。前往素材管理添加文字、PDF 或 Word 文档。</p>
          <ul v-else class="xhs-materials__list">
            <li v-for="material in materials" :key="material.id" class="xhs-materials__item">
              <label class="xhs-materials__select">
                <input type="checkbox" :checked="selectedMaterialIds.includes(material.id)" :disabled="generating || isPublished"
                  @change="toggleMaterial(material.id, $event)" />
                <span>
                  <strong>{{ material.name }}</strong>
                  <small>{{ material.sourceName ? `来自 ${material.sourceName}` : '直接输入' }} · {{ material.content.length }} 字</small>
                </span>
              </label>
            </li>
          </ul>
          <p class="field-hint">每次最多选用 3 份。去素材管理修改后，重新生成即可使用新内容。</p>
          <p v-if="materialError" class="inline-error" role="alert">{{ materialError }}</p>
        </section>
        <div class="field">
          <label for="share-requirements">本次文字要求 <span class="xhs-optional">选填</span></label>
          <textarea id="share-requirements" v-model="requirements" rows="7" maxlength="2000"
            :disabled="generating || preparingPublish || isPublished || saving" placeholder="例如：这次重点写窗边的位置，别提价格。"
            @input="edited"></textarea>
          <p class="field-hint">可留空。已选素材会优先作为事实和语气参考，图片用于确认画面内容。最多 2000 字。</p>
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
          <h2>作品预览</h2>
        </div>
        <span v-if="draft" class="status-badge">{{ isPublished ? '已发布记录' : currentNote ? isDirty ? '有未保存修改' : '已保存草稿' : '未保存' }}</span>
      </div>
      <div v-if="!draft" class="share-result__empty">
        <span aria-hidden="true">✦</span>
        <p>{{ generating ? '正在识别图片并创作…' : '生成后将在这里看到图片、标题和正文。' }}</p>
      </div>
      <template v-else>
        <div class="share-note">
          <div class="share-note__images"><img :src="imageUrl" :alt="image?.name || '发布图片'" /><img v-for="(url, index) in extraImageUrls" :key="url" :src="url" :alt="`发布图片 ${index + 2}`" /></div>
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
          <button v-if="!isPublished" class="button button--secondary" type="button" :disabled="saving || generating || preparingPublish || !image || (!isDirty && !!currentNote)"
            @click="saveNote">{{ saving ? '正在保存…' : currentNote ? '保存修改' : '保存当前文案' }}</button>
          <button v-if="currentNote && !isPublished" class="button button--quiet" type="button" :disabled="saving || generating || preparingPublish" @click="saveAsNewVersion">另存为新版本</button>
          <button class="button button--quiet" type="button" @click="copyTitle">复制标题</button>
          <button class="button button--quiet" type="button" @click="copyBody">复制正文与话题</button>
          <button v-if="!isPublished && canShareImage" class="button" type="button" :disabled="saving" @click="shareViaDevice">去发布（系统分享）</button>
          <a v-else-if="!isPublished" class="button" :href="publishUrl" target="_blank" rel="noopener noreferrer"
            @click="prepareWebPublish">复制正文并打开抖音</a>
          <a v-if="!isPublished && canShareImage" class="text-button share-result__web-link" :href="publishUrl"
            target="_blank" rel="noopener noreferrer" @click="prepareWebPublish">打开网页版</a>
          <button v-if="currentNote && !isPublished" class="button button--secondary" type="button"
            :disabled="saving || preparingPublish || isDirty" @click="markPublished">已在抖音发布</button>
        </div>
        <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
        <p v-if="saveStatus" class="field-hint" role="status">{{ saveStatus }} <RouterLink to="/share/douyin/records" class="xhs-inline-link">查看创作记录</RouterLink></p>
        <p v-if="copyStatus" class="field-hint" role="status">{{ copyStatus }}</p>
        <p v-if="publishStatus" class="field-hint" role="status">{{ publishStatus }}</p>
        <p v-if="currentNote && isDirty" class="field-hint">修改后请先保存，再去发布。</p>
        <p v-if="!isPublished" class="field-hint">发布时请在抖音创作者中心上传原图、粘贴标题和正文，并亲自完成发布。发布成功后返回这里标记“已发布”。</p>
      </template>
    </section>
  </div>

  <section v-else-if="isMaterialsPage" class="panel xhs-material-library" aria-label="抖音素材管理">
    <div class="xhs-material-library__heading">
      <div>
        <p class="eyebrow">我的创作素材</p>
        <h2>素材管理</h2>
        <p>按主题保存事实、经历和表达风格。图文创作时选用，生成文案会优先参考这些内容。</p>
      </div>
      <button class="button" type="button" :disabled="materialSaving" @click="openMaterialEditor()">新增素材</button>
    </div>

    <form v-if="materialEditorOpen" ref="materialEditorElement" class="xhs-material-editor xhs-material-editor--page"
      aria-label="编辑创作素材" @submit.prevent="saveMaterial">
      <div class="xhs-material-editor__heading">
        <h3>{{ materialEditingId ? '修改创作素材' : '新增创作素材' }}</h3>
        <button class="text-button" type="button" :disabled="materialSaving" @click="closeMaterialEditor">收起</button>
      </div>
      <div class="field">
        <label for="material-name">素材名称</label>
        <input id="material-name" v-model="materialName" maxlength="60" placeholder="例如：我的日常语气、咖啡店真实体验" :disabled="materialSaving" />
      </div>
      <div class="xhs-material-editor__modes" role="group" aria-label="素材内容来源">
        <button class="button" :class="materialMode === 'text' ? '' : 'button--quiet'" type="button" :disabled="materialSaving"
          :aria-pressed="materialMode === 'text'" @click="materialMode = 'text'; materialFile = null">直接输入</button>
        <button class="button" :class="materialMode === 'file' ? '' : 'button--quiet'" type="button" :disabled="materialSaving"
          :aria-pressed="materialMode === 'file'" @click="materialMode = 'file'">上传文档</button>
      </div>
      <div v-if="materialMode === 'text'" class="field">
        <label for="material-content">素材内容</label>
        <textarea id="material-content" v-model="materialContent" rows="11" maxlength="12000" :disabled="materialSaving"
          placeholder="写下真实经历、准确资料，或贴一段你喜欢的表达方式。修改这里并保存，就会替换原内容。"></textarea>
        <p class="field-hint">最多 12000 字。修改文字后保存，即可替换原内容。</p>
      </div>
      <div v-else class="field">
        <label for="material-file">PDF 或 Word 文档</label>
        <input id="material-file" class="xhs-material-editor__file" type="file" accept=".pdf,.doc,.docx" :disabled="materialSaving"
          @change="selectMaterialFile" />
        <p class="field-hint">最大 5 MiB。上传后提取文字保存；扫描版 PDF 暂不支持。新文档会替换原内容。</p>
      </div>
      <p v-if="materialError" class="inline-error" role="alert">{{ materialError }}</p>
      <div class="xhs-material-editor__actions">
        <button class="button" type="submit" :disabled="materialSaving">{{ materialSaving ? '正在保存…' : materialEditingId ? '保存修改' : '保存素材' }}</button>
        <button class="button button--quiet" type="button" :disabled="materialSaving" @click="closeMaterialEditor">取消</button>
      </div>
    </form>

    <p v-if="materialStatus" class="xhs-material-library__status" role="status">{{ materialStatus }}</p>
    <p v-if="materialError && !materialEditorOpen" class="inline-error" role="alert">{{ materialError }}</p>
    <p v-if="materials.length === 0" class="xhs-material-library__empty">还没有素材。点击「新增素材」，直接输入文字，或上传 PDF、Word 文档。</p>
    <ul v-else class="xhs-material-library__list">
      <li v-for="material in materials" :key="material.id" class="xhs-material-library__item">
        <div class="xhs-material-library__meta">
          <div>
            <strong>{{ material.name }}</strong>
            <span>{{ material.sourceName ? `来自 ${material.sourceName}` : '直接输入' }} · {{ material.content.length }} 字 · 更新于 {{ formatNoteTime(material.updatedAt) }}</span>
          </div>
          <div class="xhs-material-library__actions">
            <button class="button button--secondary" type="button" :disabled="materialSaving" @click="openMaterialEditor(material)">查看 / 修改</button>
            <button class="text-button text-button--danger" type="button" :disabled="materialSaving" @click="removeMaterial(material)">删除</button>
          </div>
        </div>
        <p>{{ material.content.slice(0, 180) }}{{ material.content.length > 180 ? '…' : '' }}</p>
      </li>
    </ul>
  </section>

  <section v-else class="panel share-library" aria-label="我的抖音文案">
    <div class="share-library__heading">
      <div>
        <p class="eyebrow">我的文案</p>
        <h2>创作记录</h2>
      </div>
      <button class="text-button" type="button" :disabled="loadingNotes" @click="refreshNotes">刷新列表</button>
    </div>
    <p v-if="publishStatus" class="xhs-records-status" role="status">{{ publishStatus }}</p>
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
  </div>
</template>
