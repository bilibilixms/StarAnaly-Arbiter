<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTask, listTaskImages, listTasks, type Task, type TaskImage } from '@/api/tasks'
import { listTaskXiaohongshuNotes, type XiaohongshuNote } from '@/api/sharing'
import { listTaskDouyinNotes, type DouyinNote } from '@/api/douyin'
import { makeTemplatePosters } from '@/poster/template'
import { cancelPosterJob, deletePosterJob, deletePosterMaterial, getPosterStatus, importPosterJob, listPosterJobs, listPosterMaterials,
  posterImageUrl, replacePosterImage, savePosterMaterial, savePosterReview, savePosterSelection, submitPosterJob,
  type PosterJob, type PosterJobImage, type PosterMaterial, type PosterReview, type PosterStatus } from '@/api/posters'
import { overlayOriginalLogos } from '@/poster/logo'
import { checks, review, reviewComplete, restoredMaterialIds } from '@/poster/workflow'
import { savePosterTransfer } from '@/poster/transfer'
import { formatCreatedAt } from '@/task/format'

const route = useRoute()
const router = useRouter()
const taskId = computed(() => {
  const id = Number(route.params.id)
  return Number.isSafeInteger(id) && id > 0 ? id : null
})
const task = ref<Task | null>(null)
const images = ref<TaskImage[]>([])
const notes = ref<XiaohongshuNote[]>([])
const douyinNotes = ref<DouyinNote[]>([])
const tasks = ref<Task[]>([])
const search = ref('')
const taskPage = ref(0)
const taskTotalPages = ref(0)
const loading = ref(false)
const generating = ref(false)
const aiConfigured = ref(false)
const quota = ref<PosterStatus | null>(null)
const handoffBusy = ref(false)
const imageBusy = ref<number | null>(null)
const imageVersions = ref<Record<number, number>>({})
const count = ref(2)
const direction = ref('')
const revisionAdvice = ref('')
const requiredText = ref('')
const forbiddenContent = ref('')
const referenceId = ref<number | null>(null)
const batches = ref<PosterJob[]>([])
const activeBatchIndex = ref(0)
const selected = ref<number[]>([])
const materials = ref<PosterMaterial[]>([])
const selectedMaterialIds = ref<number[]>([])
const materialEditorOpen = ref(false)
const materialEditingId = ref<number | null>(null)
const materialType = ref<PosterMaterial['type']>('TEXT')
const materialMode = ref<'text' | 'file'>('text')
const materialName = ref('')
const materialContent = ref('')
const materialFile = ref<File | null>(null)
const materialSaving = ref(false)
const materialError = ref('')
const materialNotice = ref('')
const error = ref('')
const notice = ref('')
let loadVersion = 0
let pollTimer: ReturnType<typeof setTimeout> | undefined
let pendingRequestKey: string | null = null
let pendingRequestFingerprint: string | null = null
const activeBatch = computed(() => batches.value[activeBatchIndex.value] ?? null)

function clearBatches() {
  if (pollTimer) clearTimeout(pollTimer)
  batches.value = []
  activeBatchIndex.value = 0
  selected.value = []
}

async function load() {
  const version = ++loadVersion
  error.value = ''
  notice.value = ''
  task.value = null
  images.value = []
  notes.value = []
  douyinNotes.value = []
  selectedMaterialIds.value = []
  clearBatches()
  loading.value = true
  try {
    if (taskId.value === null) {
      const result = await listTasks(taskPage.value, undefined, search.value.trim())
      if (version === loadVersion) {
        tasks.value = result.content
        taskTotalPages.value = result.totalPages
      }
      return
    }
    const [nextTask, nextImages, nextNotes, nextDouyinNotes, nextMaterials, nextBatches] = await Promise.all([
      getTask(taskId.value), listTaskImages(taskId.value), listTaskXiaohongshuNotes(taskId.value),
      listTaskDouyinNotes(taskId.value), listPosterMaterials(), listPosterJobs(taskId.value),
    ])
    if (version !== loadVersion) return
    task.value = nextTask
    images.value = nextImages
    notes.value = nextNotes
    douyinNotes.value = nextDouyinNotes
    materials.value = nextMaterials
    batches.value = nextBatches
    activeBatchIndex.value = Math.max(0, nextBatches.length - 1)
    selected.value = activeBatch.value?.selected ?? []
    schedulePoll(version)
    quota.value = await getPosterStatus().catch(() => null)
    aiConfigured.value = quota.value?.configured ?? false
    direction.value = nextTask.title
    if (activeBatch.value) restoreSettings(activeBatch.value)
    referenceId.value = nextImages[0]?.id ?? null
  } catch (cause) {
    if (version === loadVersion) error.value = cause instanceof Error ? cause.message : '加载工作台失败。'
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

function restoreSettings(batch: PosterJob) {
  direction.value = batch.direction
  revisionAdvice.value = batch.advice
  count.value = batch.requestedCount
  const rules = checks(batch)
  requiredText.value = rules.requiredText
  forbiddenContent.value = rules.forbiddenContent
  selectedMaterialIds.value = restoredMaterialIds(batch.materialSnapshot, materials.value.map((item) => item.id))
}

async function retryJob(batch: PosterJob) {
  restoreSettings(batch)
  const previous = batches.value.slice(0, batches.value.findIndex((item) => item.id === batch.id))
    .reverse().find((item) => item.status === 'SUCCEEDED')
  if (batch.advice && previous) selectBatch(batches.value.findIndex((item) => item.id === previous.id))
  await generatePosters()
}

function schedulePoll(version: number) {
  if (pollTimer) clearTimeout(pollTimer)
  if (!batches.value.some((batch) => batch.status === 'PENDING' || batch.status === 'RUNNING')) return
  pollTimer = setTimeout(async () => {
    if (version !== loadVersion || taskId.value === null) return
    try {
      const activeId = activeBatch.value?.id
      const refreshed = await listPosterJobs(taskId.value)
      if (version !== loadVersion) return
      batches.value = refreshed
      const index = refreshed.findIndex((batch) => batch.id === activeId)
      activeBatchIndex.value = index < 0 ? Math.max(0, refreshed.length - 1) : index
      selected.value = activeBatch.value?.selected ?? []
      if (activeBatch.value?.status === 'FAILED') error.value = activeBatch.value.error || '生成海报失败。'
      if (activeBatch.value?.status === 'SUCCEEDED') notice.value = '海报已生成并保存，请逐张核对。'
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '查询生成状态失败。'
    }
    schedulePoll(version)
  }, 2500)
}

async function applyLogo(image: PosterJobImage) {
  const batch = activeBatch.value
  const id = taskId.value
  if (!batch || id === null || imageBusy.value !== null) return
  imageBusy.value = image.id
  error.value = ''
  try {
    const file = await overlayOriginalLogos(id, batch.id, image.id, checks(batch).logoIds ?? [])
    await replacePosterImage(id, batch.id, image.id, file)
    image.logoApplied = true
    image.mime = 'image/png'
    image.name = file.name
    image.reviewJson = '{}'
    imageVersions.value[image.id] = Date.now()
    notice.value = '原始 Logo 已叠加并保存，请查看图片后完成核对。'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '叠加 Logo 失败。'
  } finally { imageBusy.value = null }
}

async function cancelJob(batch: PosterJob) {
  if (taskId.value === null) return
  try {
    await cancelPosterJob(taskId.value, batch.id)
    batch.status = 'CANCELLED'
    batch.error = '已取消生成。'
    schedulePoll(loadVersion)
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '取消失败。' }
}

async function removeJob(batch: PosterJob) {
  if (taskId.value === null || !window.confirm('确定删除这个海报版本及全部图片吗？')) return
  try {
    await deletePosterJob(taskId.value, batch.id)
    batches.value = batches.value.filter((item) => item.id !== batch.id)
    activeBatchIndex.value = Math.max(0, batches.value.length - 1)
    selected.value = activeBatch.value?.selected ?? []
    notice.value = '海报版本已删除。'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '删除失败。' }
}

async function updateReview(image: PosterJobImage, field: keyof PosterReview, event: Event) {
  const batch = activeBatch.value
  const id = taskId.value
  if (!batch || id === null) return
  const original = image.reviewJson
  const next = { ...review(image), [field]: (event.target as HTMLInputElement).checked }
  image.reviewJson = JSON.stringify(next)
  try { await savePosterReview(id, batch.id, image.id, next) }
  catch (cause) {
    image.reviewJson = original
    error.value = cause instanceof Error ? cause.message : '无法保存核对结果。'
  }
}

async function imageFile(task: number, batch: PosterJob, image: PosterJobImage): Promise<File> {
  const response = await fetch(posterImageUrl(task, batch.id, image.id))
  if (!response.ok) throw new Error('无法读取已保存的海报。')
  return new File([await response.blob()], image.name, { type: image.mime })
}

function materialLabel(type: PosterMaterial['type']) {
  return type === 'TEXT' ? '文字 / 文档' : type === 'LOGO' ? 'Logo' : '参考图'
}

function openMaterialEditor(material?: PosterMaterial) {
  materialEditingId.value = material?.id ?? null
  materialType.value = material?.type ?? 'TEXT'
  materialMode.value = 'text'
  materialName.value = material?.name ?? ''
  materialContent.value = material?.content ?? ''
  materialFile.value = null
  materialError.value = ''
  materialNotice.value = ''
  materialEditorOpen.value = true
}

function selectMaterialFile(event: Event) {
  materialFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
  materialError.value = ''
}

async function saveMaterial() {
  if (materialSaving.value) return
  materialError.value = ''
  const name = materialName.value.trim()
  if (!name || name.length > 60) { materialError.value = '素材名称需为 1 至 60 字。'; return }
  if (materialType.value === 'TEXT' && materialMode.value === 'text'
      && (!materialContent.value.trim() || materialContent.value.length > 12000)) {
    materialError.value = '文字素材需为 1 至 12000 字。'
    return
  }
  if (materialType.value === 'TEXT' && materialMode.value === 'file' && !materialFile.value) {
    materialError.value = '请选择 PDF 或 Word 文档。'
    return
  }
  if (materialType.value !== 'TEXT' && !materialEditingId.value && !materialFile.value) {
    materialError.value = '请上传 Logo 或参考图。'
    return
  }
  materialSaving.value = true
  try {
    const saved = await savePosterMaterial({
      name, type: materialType.value,
      content: materialType.value === 'TEXT' && materialMode.value === 'file' ? undefined : materialContent.value.trim(),
      file: materialFile.value,
    }, materialEditingId.value ?? undefined)
    materials.value = await listPosterMaterials()
    materialEditorOpen.value = false
    materialNotice.value = `素材“${saved.name}”已保存。生成时勾选即可使用。`
  } catch (cause) {
    materialError.value = cause instanceof Error ? cause.message : '保存海报素材失败。'
  } finally { materialSaving.value = false }
}

async function removeMaterial(material: PosterMaterial) {
  if (!window.confirm(`确定删除海报素材“${material.name}”吗？`)) return
  materialError.value = ''
  try {
    await deletePosterMaterial(material.id)
    materials.value = materials.value.filter((item) => item.id !== material.id)
    selectedMaterialIds.value = selectedMaterialIds.value.filter((id) => id !== material.id)
    if (materialEditingId.value === material.id) materialEditorOpen.value = false
    materialNotice.value = '海报素材已删除。'
  } catch (cause) {
    materialError.value = cause instanceof Error ? cause.message : '删除海报素材失败。'
  }
}

function toggleMaterial(material: PosterMaterial, event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  materialError.value = ''
  if (!checked) {
    selectedMaterialIds.value = selectedMaterialIds.value.filter((id) => id !== material.id)
    return
  }
  if (selectedMaterialIds.value.length >= 6) {
    materialError.value = '每次最多选用 6 份海报素材。'
    return
  }
  if (material.type === 'REFERENCE_IMAGE' && materials.value.filter((item) => selectedMaterialIds.value.includes(item.id)
      && item.type === 'REFERENCE_IMAGE').length >= 3) {
    materialError.value = '每次最多选用 3 张视觉参考图。'
    return
  }
  selectedMaterialIds.value = [...selectedMaterialIds.value, material.id]
}

function searchTasks() { taskPage.value = 0; void load() }
function changePage(next: number) {
  if (next < 0 || next >= taskTotalPages.value) return
  taskPage.value = next
  void load()
}

async function generatePosters() {
  const current = task.value
  const id = taskId.value
  if (!current || id === null || generating.value) return
  const amount = Number(count.value)
  if (!Number.isInteger(amount) || amount < 1 || amount > 4) {
    error.value = '一次请选择生成 1 至 4 张海报。'
    return
  }
  const goal = direction.value.trim()
  const advice = revisionAdvice.value.trim()
  if (!goal) { error.value = '请填写海报主题。'; return }
  const selectedMaterials = materials.value.filter((item) => selectedMaterialIds.value.includes(item.id))
  const baseBatch = activeBatch.value?.status === 'SUCCEEDED' ? activeBatch.value
    : [...batches.value].reverse().find((item) => item.status === 'SUCCEEDED')
  const basePosition = baseBatch?.id === activeBatch.value?.id ? selected.value[0] : baseBatch?.selected[0]
  const baseImage = baseBatch?.images.find((item) => item.position === basePosition) ?? baseBatch?.images[0]
  if (aiConfigured.value) {
    const hasBaseReference = !!(advice && baseImage)
      || referenceId.value !== null
    if (selectedMaterials.filter((item) => item.type === 'REFERENCE_IMAGE').length + Number(hasBaseReference) > 3) {
      error.value = '千问图像每次最多使用 3 张参考图。请减少图片素材；若正在修改下一版，当前海报也算 1 张。'
      return
    }
    if (selectedMaterials.filter((item) => item.type === 'TEXT')
      .reduce((length, item) => length + item.content.length, 0) > 12000) {
      error.value = '选中文字素材合计不能超过 12000 字，请缩短或少选。'
      return
    }
  }
  generating.value = true
  error.value = ''
  notice.value = ''
  try {
    const revision = batches.value.length + 1
    const reference = images.value.find((item) => item.id === referenceId.value)
    const fingerprint = JSON.stringify({ id, goal, advice, amount, referenceId: referenceId.value,
      baseImageId: advice ? baseImage?.id : null, materialIds: selectedMaterialIds.value,
      requiredText: requiredText.value.trim(), forbiddenContent: forbiddenContent.value.trim() })
    const requestKey = pendingRequestFingerprint === fingerprint && pendingRequestKey
      ? pendingRequestKey : crypto.randomUUID()
    pendingRequestKey = requestKey
    pendingRequestFingerprint = fingerprint
    let batch: PosterJob
    if (aiConfigured.value) {
      let referenceImage = advice && baseBatch && baseImage
        ? await imageFile(id, baseBatch, baseImage)
        : undefined
      if (!referenceImage && reference) {
        const response = await fetch(`/api/tasks/${id}/images/${reference.id}`)
        if (!response.ok) throw new Error('无法读取参考图片。')
        referenceImage = new File([await response.blob()], reference.name, { type: reference.mime })
      }
      batch = await submitPosterJob({ taskId: id, requestKey, direction: goal, advice, count: amount,
        referenceImage, materialIds: selectedMaterialIds.value, requiredText: requiredText.value.trim(),
        forbiddenContent: forbiddenContent.value.trim() })
    } else {
      const files = await makeTemplatePosters({
        title: current.title,
        description: [current.description, current.discussionSummary].filter(Boolean).join('；'),
        direction: advice || goal,
        sourceImage: reference ? `/api/tasks/${id}/images/${reference.id}` : undefined,
        count: amount,
        revision,
      })
      batch = await importPosterJob({ taskId: id, requestKey, direction: goal, advice, files,
        materialIds: selectedMaterialIds.value, requiredText: requiredText.value.trim(),
        forbiddenContent: forbiddenContent.value.trim() })
    }
    if (id !== taskId.value) return
    pendingRequestKey = null
    pendingRequestFingerprint = null
    if (aiConfigured.value) quota.value = await getPosterStatus().catch(() => quota.value)
    if (!batches.value.some((item) => item.id === batch.id)) batches.value.push(batch)
    activeBatchIndex.value = batches.value.length - 1
    selected.value = batch.selected
    revisionAdvice.value = ''
    if (batch.status === 'FAILED') error.value = batch.error || '生成任务提交失败。'
    else notice.value = batch.status === 'SUCCEEDED' ? `第 ${revision} 版海报已保存。请逐张核对。`
      : `第 ${revision} 版已提交，正在后台生成。离开页面后仍可回来查看结果。`
    schedulePoll(loadVersion)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '生成海报失败。'
  } finally { generating.value = false }
}

function selectBatch(index: number) {
  activeBatchIndex.value = index
  selected.value = batches.value[index]?.selected ?? []
}
async function togglePoster(index: number) {
  const batch = activeBatch.value
  const id = taskId.value
  if (!batch || id === null || batch.status !== 'SUCCEEDED') return
  const previous = selected.value
  selected.value = selected.value.includes(index)
    ? selected.value.filter((item) => item !== index)
    : [...selected.value, index].sort((a, b) => a - b)
  try {
    await savePosterSelection(id, batch.id, selected.value)
    batch.selected = [...selected.value]
  } catch (cause) {
    selected.value = previous
    error.value = cause instanceof Error ? cause.message : '无法保存选择。'
  }
}
async function goToShare(platform: 'xiaohongshu' | 'douyin') {
  if (!activeBatch.value || !taskId.value || handoffBusy.value) return
  const batch = activeBatch.value
  const chosen = selected.value.map((index) => batch.images.find((image) => image.position === index))
    .filter((image): image is PosterJobImage => !!image)
  if (!chosen.length) { error.value = '请至少选择 1 张海报。'; return }
  if (chosen.some((image) => !reviewComplete(batch, image))) {
    error.value = '请先逐项核对所有选中的海报，再带去发布。'
    return
  }
  handoffBusy.value = true
  error.value = ''
  try {
    const files = await Promise.all(chosen.map((image) => imageFile(taskId.value!, batch, image)))
    const poster = await savePosterTransfer(files)
    await router.push({ name: `share-${platform}`, query: { taskId: taskId.value, poster } })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法交接海报。'
  } finally { handoffBusy.value = false }
}
watch(() => route.params.id, () => { void load() }, { immediate: true })
onUnmounted(clearBatches)
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="eyebrow">创作工作台</p>
      <h1>{{ task ? task.title : '选择一份需求开始创作' }}</h1>
      <p class="page-description">根据需求制作海报，预览后调整方向，再把选中的图片带去发布。</p>
    </div>
    <RouterLink v-if="task" class="button button--secondary" :to="`/tasks/${task.id}`">返回需求单</RouterLink>
    <RouterLink v-else class="button button--secondary" to="/tasks/new">新建需求</RouterLink>
  </section>
  <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
  <p v-if="notice" class="inline-success" role="status">{{ notice }}</p>
  <section v-if="taskId === null" class="panel workspace-section" aria-label="选择需求">
    <div class="section-heading"><h2>需求单</h2><span class="muted">选择后生成海报</span></div>
    <form class="workbench-pick__search" @submit.prevent="searchTasks">
      <label for="workbench-search">查找需求</label>
      <input id="workbench-search" v-model="search" type="search" maxlength="200" placeholder="输入需求名称" />
      <button class="button button--secondary" type="submit">搜索</button>
    </form>
    <p v-if="loading" role="status">正在加载需求…</p>
    <p v-else-if="tasks.length === 0" class="empty-note">还没有匹配的需求。先创建一份需求单。</p>
    <ul v-else class="workbench-pick__list">
      <li v-for="item in tasks" :key="item.id"><RouterLink :to="{ name: 'creative-workbench-task', params: { id: item.id } }">
        <strong>{{ item.title }}</strong><span>{{ formatCreatedAt(item.createdAt) }}</span>
      </RouterLink></li>
    </ul>
    <div v-if="taskTotalPages > 1" class="pagination">
      <button class="button button--secondary" type="button" :disabled="taskPage === 0 || loading" @click="changePage(taskPage - 1)">上一页</button>
      <span>第 {{ taskPage + 1 }} / {{ taskTotalPages }} 页</span>
      <button class="button button--secondary" type="button" :disabled="taskPage + 1 >= taskTotalPages || loading" @click="changePage(taskPage + 1)">下一页</button>
    </div>
  </section>
  <div v-else-if="loading" class="panel message" role="status">正在加载需求…</div>
  <template v-else-if="task">
    <section class="panel workspace-section workbench-source" aria-label="需求材料">
      <div class="section-heading"><h2>需求材料</h2><RouterLink class="text-link" :to="`/tasks/${task.id}`">修改需求</RouterLink></div>
      <p class="workspace-description">{{ task.description || '尚未填写文字需求。' }}</p>
      <p v-if="task.discussionSummary" class="muted">补充说明：{{ task.discussionSummary }}</p>
      <div v-if="images.length" class="workbench-source__images">
        <img v-for="item in images" :key="item.id" :src="`/api/tasks/${task.id}/images/${item.id}`" :alt="item.name" />
      </div>
      <p class="field-hint">海报生成会参考需求文字、补充说明、PDF 和 Word 的可提取文字，以及所选参考图片。</p>
    </section>
    <section class="panel workspace-section poster-materials" aria-label="海报素材管理">
      <div class="section-heading">
        <div><h2>海报素材</h2><p class="muted">保存品牌规范、文档、Logo 和参考图；勾选后用于本次 AI 生成和下一版修改。</p></div>
        <button class="button button--secondary" type="button" :disabled="materialSaving" @click="openMaterialEditor()">新增素材</button>
      </div>
      <form v-if="materialEditorOpen" class="poster-materials__editor" aria-label="编辑海报素材" @submit.prevent="saveMaterial">
        <div class="poster-materials__editor-heading"><h3>{{ materialEditingId ? '修改素材' : '新增素材' }}</h3>
          <button class="text-button" type="button" :disabled="materialSaving" @click="materialEditorOpen = false">收起</button></div>
        <div class="poster-materials__form-grid">
          <div class="field"><label for="poster-material-name">素材名称</label>
            <input id="poster-material-name" v-model="materialName" maxlength="60" placeholder="例如：品牌视觉规范" :disabled="materialSaving" /></div>
          <div class="field"><label for="poster-material-type">素材类型</label>
            <select id="poster-material-type" v-model="materialType" :disabled="materialSaving || materialEditingId !== null">
              <option value="TEXT">文字 / 文档</option><option value="LOGO">Logo</option><option value="REFERENCE_IMAGE">参考图</option>
            </select></div>
        </div>
        <template v-if="materialType === 'TEXT'">
          <div class="poster-materials__modes" role="group" aria-label="文字素材来源">
            <button class="button" :class="materialMode === 'text' ? '' : 'button--quiet'" type="button" :disabled="materialSaving" @click="materialMode = 'text'; materialFile = null">直接输入</button>
            <button class="button" :class="materialMode === 'file' ? '' : 'button--quiet'" type="button" :disabled="materialSaving" @click="materialMode = 'file'">上传文档</button>
          </div>
          <div v-if="materialMode === 'text'" class="field"><label for="poster-material-content">内容约束</label>
            <textarea id="poster-material-content" v-model="materialContent" rows="7" maxlength="12000" :disabled="materialSaving"
              placeholder="写明品牌色、必须出现的元素、禁止使用的表达、准确的产品信息等。" />
            <p class="field-hint">最多 12000 字；本次选中文字素材合计不超过 12000 字。</p></div>
          <div v-else class="field"><label for="poster-material-document">PDF 或 Word 文档</label>
            <input id="poster-material-document" type="file" accept=".pdf,.doc,.docx" :disabled="materialSaving" @change="selectMaterialFile" />
            <p class="field-hint">文件不超过 5 MiB；保存时提取文字，扫描版 PDF 需先识别文字。</p></div>
        </template>
        <template v-else>
          <div class="field"><label for="poster-material-image">{{ materialType === 'LOGO' ? 'Logo 图片' : '参考图片' }}</label>
            <input id="poster-material-image" type="file" accept="image/png,image/jpeg,image/webp,image/gif" :disabled="materialSaving" @change="selectMaterialFile" />
            <p class="field-hint">JPEG、PNG、WebP 或 GIF，最多 10 MB。修改已有素材时不选文件会保留原图。</p></div>
          <div class="field"><label for="poster-material-note">图片使用说明（选填）</label>
            <textarea id="poster-material-note" v-model="materialContent" rows="3" maxlength="1000" :disabled="materialSaving"
              placeholder="例如：Logo 周围留白，背景保持浅色；不要遮挡主体。" /></div>
        </template>
        <p v-if="materialError" class="inline-error" role="alert">{{ materialError }}</p>
        <div class="poster-materials__editor-actions">
          <button class="button" type="submit" :disabled="materialSaving">{{ materialSaving ? '正在保存…' : '保存素材' }}</button>
          <button class="button button--quiet" type="button" :disabled="materialSaving" @click="materialEditorOpen = false">取消</button>
        </div>
      </form>
      <p v-if="materialNotice" class="field-hint" role="status">{{ materialNotice }}</p>
      <p v-if="materialError && !materialEditorOpen" class="inline-error" role="alert">{{ materialError }}</p>
      <p v-if="!materials.length" class="field-hint">还没有海报素材。可先添加一份品牌规范，或上传 Logo 与参考图。</p>
      <ul v-else class="poster-materials__list">
        <li v-for="material in materials" :key="material.id" class="poster-materials__item">
          <label class="poster-materials__select"><input type="checkbox" :checked="selectedMaterialIds.includes(material.id)"
            :disabled="generating || materialSaving" @change="toggleMaterial(material, $event)" />
            <img v-if="material.type !== 'TEXT'" :src="`/api/posters/materials/${material.id}/image?v=${encodeURIComponent(material.updatedAt)}`" :alt="material.name" />
            <span><strong>{{ material.name }}</strong><small>{{ materialLabel(material.type) }}{{ material.sourceName ? ` · ${material.sourceName}` : '' }}</small></span>
          </label>
          <div class="poster-materials__item-actions">
            <button class="text-button" type="button" :disabled="materialSaving || generating" @click="openMaterialEditor(material)">查看 / 修改</button>
            <button class="text-button text-button--danger" type="button" :disabled="materialSaving || generating" @click="removeMaterial(material)">删除</button>
          </div>
          <p v-if="material.content" class="poster-materials__excerpt">{{ material.content.slice(0, 180) }}{{ material.content.length > 180 ? '…' : '' }}</p>
        </li>
      </ul>
      <p class="field-hint">已选 {{ selectedMaterialIds.length }} / 6 份素材。千问图像最多接收 3 张参考图，需求图片或上一版海报也计入其中。Logo 原图会固定叠加在顶部左侧，多个 Logo 从左到右排列；生成后请逐张操作并核对。</p>
    </section>
    <section class="panel workspace-section poster-studio" aria-label="生成海报">
      <div class="section-heading"><div><h2>生成海报</h2><p class="muted">{{ aiConfigured ? '千问图像 AI 已配置，可根据需求生成海报。' : '图像 AI 尚未配置地址与密钥；当前生成排版示意图。' }}</p></div><span class="status-badge">{{ aiConfigured ? 'AI 生成' : '排版示意' }}</span></div>
      <p v-if="aiConfigured && quota" class="field-hint">今日 AI 图片额度：已提交 {{ quota.usedToday }} / {{ quota.dailyLimit }} 张。失败或取消的请求也可能产生费用，因此计入额度。</p>
      <div class="poster-studio__controls">
        <div class="field"><label for="poster-direction">海报主题</label><textarea id="poster-direction" v-model="direction" rows="2" maxlength="300" placeholder="这张海报想表达什么？" /></div>
        <div class="field"><label for="poster-count">生成张数</label><select id="poster-count" v-model.number="count"><option v-for="number in 4" :key="number" :value="number">{{ number }} 张</option></select></div>
        <div v-if="images.length" class="field"><label for="poster-reference">参考图片</label><select id="poster-reference" v-model="referenceId"><option :value="null">不使用图片</option><option v-for="item in images" :key="item.id" :value="item.id">{{ item.name }}</option></select></div>
      </div>
      <div class="poster-studio__checks">
        <div class="field"><label for="poster-required-text">必须准确出现的文字（选填）</label>
          <textarea id="poster-required-text" v-model="requiredText" rows="2" maxlength="500" placeholder="例如：产品名称、活动日期、价格" />
        </div>
        <div class="field"><label for="poster-forbidden-content">不得出现的内容（选填）</label>
          <textarea id="poster-forbidden-content" v-model="forbiddenContent" rows="2" maxlength="500" placeholder="例如：未经确认的折扣、功效承诺" />
        </div>
      </div>
      <p class="field-hint">要求会随海报版本保存。生成后请逐张核对文字、禁用内容和 Logo；当前由你确认画面，系统不会代替人工识别图片中的文字。</p>
      <div v-if="batches.length" class="field poster-studio__advice"><label for="poster-advice">下一版修改方向</label><textarea id="poster-advice" v-model="revisionAdvice" rows="2" maxlength="300" placeholder="例如：标题更醒目、整体偏浅粉、突出第一张参考图" /><p class="field-hint">保留前一版，生成后可切回查看。{{ aiConfigured ? '填写建议后，会以当前选中的第一张图片为参考继续生成。' : '当前示意会把建议作为下一版标题方向。' }}</p></div>
      <button class="button" type="button" :disabled="generating || handoffBusy || batches.some((batch) => batch.status === 'PENDING' || batch.status === 'RUNNING')" @click="generatePosters">{{ generating ? '正在提交…' : batches.length ? '按新方向再生成' : '生成海报' }}</button>
      <p v-if="batches.some((batch) => batch.status === 'PENDING' || batch.status === 'RUNNING')" class="field-hint" role="status">生成任务正在后台进行，刷新页面后仍可查看进度和结果。</p>
    </section>
    <section v-if="activeBatch" class="panel workspace-section poster-preview" aria-label="海报预览">
      <div class="section-heading"><div><h2>预览与挑选</h2><p class="muted">点击图片可选择或取消；发布时只带走已核对的图片。</p></div><span class="status-badge">{{ activeBatch.status === 'SUCCEEDED' ? `已选 ${selected.length} / ${activeBatch.images.length}` : activeBatch.status === 'FAILED' ? '生成失败' : activeBatch.status === 'CANCELLED' ? '已取消' : '生成中' }}</span></div>
      <div v-if="batches.length > 1" class="poster-preview__versions" aria-label="生成版本">
        <button v-for="(batch, index) in batches" :key="batch.id" type="button" class="button button--quiet" :class="{ 'is-active': index === activeBatchIndex }" @click="selectBatch(index)">第 {{ index + 1 }} 版{{ batch.status === 'FAILED' ? ' · 失败' : batch.status === 'CANCELLED' ? ' · 已取消' : batch.status === 'SUCCEEDED' ? '' : ' · 生成中' }}</button>
      </div>
      <p v-if="activeBatch.status === 'FAILED'" class="inline-error">{{ activeBatch.error || '生成失败，请按当前设置重试。' }}</p>
      <p v-if="activeBatch.status === 'CANCELLED'" class="field-hint">生成任务已取消，可按当前设置再次生成。</p>
      <p v-else-if="activeBatch.status === 'PENDING' || activeBatch.status === 'RUNNING'" class="field-hint" role="status">任务已受理，正在等待图片结果。可以离开页面，稍后返回。</p>
      <div class="poster-preview__job-actions">
        <button v-if="activeBatch.status === 'PENDING' || activeBatch.status === 'RUNNING'" class="button button--secondary" type="button" @click="cancelJob(activeBatch)">取消生成</button>
        <template v-else>
          <button v-if="activeBatch.status === 'FAILED' || activeBatch.status === 'CANCELLED'" class="button button--secondary" type="button" :disabled="generating" @click="retryJob(activeBatch)">用这版参数重试</button>
          <button class="text-button text-button--danger" type="button" @click="removeJob(activeBatch)">删除这个版本</button>
        </template>
      </div>
      <p v-if="activeBatch.status === 'SUCCEEDED'" class="field-hint">本版主题：{{ activeBatch.direction }}<template v-if="activeBatch.advice"> · 修改方向：{{ activeBatch.advice }}</template></p>
      <div v-if="activeBatch.status === 'SUCCEEDED'" class="poster-preview__grid">
        <div v-for="image in activeBatch.images" :key="image.id" class="poster-preview__item">
          <button type="button" class="poster-preview__card" :class="{ 'is-selected': selected.includes(image.position) }" :aria-pressed="selected.includes(image.position)" @click="togglePoster(image.position)">
            <img :src="`${posterImageUrl(task.id, activeBatch.id, image.id)}?v=${imageVersions[image.id] ?? 0}`" :alt="`第 ${activeBatchIndex + 1} 版海报 ${image.position + 1}`" />
            <span>{{ selected.includes(image.position) ? '✓ 已选择' : '点击选择' }} · 第 {{ image.position + 1 }} 张</span>
          </button>
          <div class="poster-preview__checklist" :aria-label="`第 ${image.position + 1} 张海报核对`">
            <button v-if="checks(activeBatch).logoName && !image.logoApplied" class="button button--secondary" type="button"
              :disabled="imageBusy !== null" @click="applyLogo(image)">{{ imageBusy === image.id ? '正在叠加 Logo…' : '叠加原始 Logo' }}</button>
            <label><input type="checkbox" :checked="review(image).requiredText" @change="updateReview(image, 'requiredText', $event)" />
              {{ checks(activeBatch).requiredText ? `文字已核对：${checks(activeBatch).requiredText}` : '标题和主要信息已核对' }}</label>
            <label v-if="checks(activeBatch).forbiddenContent"><input type="checkbox" :checked="review(image).forbiddenContent" @change="updateReview(image, 'forbiddenContent', $event)" />
              未出现：{{ checks(activeBatch).forbiddenContent }}</label>
            <label v-if="checks(activeBatch).logoName"><input type="checkbox" :disabled="!image.logoApplied" :checked="review(image).logo" @change="updateReview(image, 'logo', $event)" />
              Logo 已核对：{{ checks(activeBatch).logoName }}</label>
            <small>{{ reviewComplete(activeBatch, image) ? '✓ 已完成核对' : '待核对' }}</small>
          </div>
        </div>
      </div>
      <div v-if="activeBatch.status === 'SUCCEEDED'" class="poster-preview__actions">
        <button class="button" type="button" :disabled="!selected.length || handoffBusy" @click="goToShare('xiaohongshu')">{{ handoffBusy ? '正在准备图片…' : '带图片去小红书创作' }}</button>
        <button class="button button--secondary" type="button" :disabled="!selected.length || handoffBusy" @click="goToShare('douyin')">{{ handoffBusy ? '正在准备图片…' : '带图片去抖音创作' }}</button>
      </div>
    </section>
    <section v-if="notes.length || douyinNotes.length" class="panel workspace-section" aria-label="关联作品">
      <div class="section-heading"><h2>这份需求的作品</h2><span class="muted">{{ notes.length + douyinNotes.length }} 篇</span></div>
      <ul class="workbench-pick__list">
        <li v-for="note in notes" :key="`xhs-${note.id}`"><RouterLink :to="{ name: 'share-xiaohongshu', query: { noteId: note.id } }"><strong>{{ note.title }}</strong><span>小红书 · {{ note.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span></RouterLink></li>
        <li v-for="note in douyinNotes" :key="`dy-${note.id}`"><RouterLink :to="{ name: 'share-douyin', query: { noteId: note.id } }"><strong>{{ note.title }}</strong><span>抖音 · {{ note.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span></RouterLink></li>
      </ul>
    </section>
  </template>
</template>
