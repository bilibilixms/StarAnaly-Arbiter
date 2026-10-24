<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTask, listTaskImages, listTasks, type Task, type TaskImage } from '@/api/tasks'
import { listTaskXiaohongshuNotes, type XiaohongshuNote } from '@/api/sharing'
import { listTaskDouyinNotes, type DouyinNote } from '@/api/douyin'
import { makeTemplatePosters } from '@/poster/template'
import { generateAiPosters, getPosterStatus } from '@/api/posters'
import { savePosterTransfer } from '@/poster/transfer'
import { formatCreatedAt } from '@/task/format'

interface Batch { revision: number; files: File[]; urls: string[] }
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
const handoffBusy = ref(false)
const count = ref(2)
const direction = ref('')
const revisionAdvice = ref('')
const referenceId = ref<number | null>(null)
const batches = ref<Batch[]>([])
const activeBatchIndex = ref(0)
const selected = ref<number[]>([])
const error = ref('')
const notice = ref('')
let loadVersion = 0
const activeBatch = computed(() => batches.value[activeBatchIndex.value] ?? null)

function clearBatches() {
  for (const batch of batches.value) batch.urls.forEach(URL.revokeObjectURL)
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
    const [nextTask, nextImages, nextNotes, nextDouyinNotes] = await Promise.all([
      getTask(taskId.value), listTaskImages(taskId.value), listTaskXiaohongshuNotes(taskId.value),
      listTaskDouyinNotes(taskId.value),
    ])
    if (version !== loadVersion) return
    task.value = nextTask
    images.value = nextImages
    notes.value = nextNotes
    douyinNotes.value = nextDouyinNotes
    aiConfigured.value = await getPosterStatus().catch(() => false)
    direction.value = nextTask.title
    referenceId.value = nextImages[0]?.id ?? null
  } catch (cause) {
    if (version === loadVersion) error.value = cause instanceof Error ? cause.message : '加载工作台失败。'
  } finally {
    if (version === loadVersion) loading.value = false
  }
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
  generating.value = true
  error.value = ''
  notice.value = ''
  try {
    const revision = batches.value.length + 1
    const reference = images.value.find((item) => item.id === referenceId.value)
    let files: File[]
    if (aiConfigured.value) {
      let referenceImage = advice && activeBatch.value
        ? activeBatch.value.files[selected.value[0] ?? 0]
        : undefined
      if (!referenceImage && reference) {
        const response = await fetch(`/api/tasks/${id}/images/${reference.id}`)
        if (!response.ok) throw new Error('无法读取参考图片。')
        referenceImage = new File([await response.blob()], reference.name, { type: reference.mime })
      }
      files = await generateAiPosters({ taskId: id, direction: goal, advice, count: amount, referenceImage })
    } else {
      files = await makeTemplatePosters({
        title: current.title,
        description: [current.description, current.discussionSummary].filter(Boolean).join('；'),
        direction: advice || goal,
        sourceImage: reference ? `/api/tasks/${id}/images/${reference.id}` : undefined,
        count: amount,
        revision,
      })
    }
    if (id !== taskId.value) return
    batches.value.push({ revision, files, urls: files.map((file) => URL.createObjectURL(file)) })
    activeBatchIndex.value = batches.value.length - 1
    selected.value = files.map((_, index) => index)
    revisionAdvice.value = ''
    notice.value = `第 ${revision} 版海报已生成。请检查图片，再选择要带去发布的海报。`
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '生成海报失败。'
  } finally { generating.value = false }
}

function selectBatch(index: number) {
  activeBatchIndex.value = index
  selected.value = batches.value[index]?.files.map((_, position) => position) ?? []
}
function togglePoster(index: number) {
  selected.value = selected.value.includes(index)
    ? selected.value.filter((item) => item !== index)
    : [...selected.value, index].sort((a, b) => a - b)
}
async function goToShare(platform: 'xiaohongshu' | 'douyin') {
  if (!activeBatch.value || !taskId.value || handoffBusy.value) return
  const files = selected.value.map((index) => activeBatch.value?.files[index]).filter((file): file is File => !!file)
  if (!files.length) { error.value = '请至少选择 1 张海报。'; return }
  handoffBusy.value = true
  error.value = ''
  try {
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
    <section class="panel workspace-section poster-studio" aria-label="生成海报">
      <div class="section-heading"><div><h2>生成海报</h2><p class="muted">{{ aiConfigured ? '千问图像 AI 已配置，可根据需求生成海报。' : '图像 AI 尚未配置地址与密钥；当前生成排版示意图。' }}</p></div><span class="status-badge">{{ aiConfigured ? 'AI 生成' : '排版示意' }}</span></div>
      <div class="poster-studio__controls">
        <div class="field"><label for="poster-direction">海报主题</label><textarea id="poster-direction" v-model="direction" rows="2" maxlength="300" placeholder="这张海报想表达什么？" /></div>
        <div class="field"><label for="poster-count">生成张数</label><select id="poster-count" v-model.number="count"><option v-for="number in 4" :key="number" :value="number">{{ number }} 张</option></select></div>
        <div v-if="images.length" class="field"><label for="poster-reference">参考图片</label><select id="poster-reference" v-model="referenceId"><option :value="null">不使用图片</option><option v-for="item in images" :key="item.id" :value="item.id">{{ item.name }}</option></select></div>
      </div>
      <div v-if="batches.length" class="field poster-studio__advice"><label for="poster-advice">下一版修改方向</label><textarea id="poster-advice" v-model="revisionAdvice" rows="2" maxlength="300" placeholder="例如：标题更醒目、整体偏浅粉、突出第一张参考图" /><p class="field-hint">保留前一版，生成后可切回查看。{{ aiConfigured ? '填写建议后，会以当前选中的第一张图片为参考继续生成。' : '当前示意会把建议作为下一版标题方向。' }}</p></div>
      <button class="button" type="button" :disabled="generating || handoffBusy" @click="generatePosters">{{ generating ? '正在生成海报…' : batches.length ? '按新方向再生成' : '生成海报' }}</button>
    </section>
    <section v-if="activeBatch" class="panel workspace-section poster-preview" aria-label="海报预览">
      <div class="section-heading"><div><h2>预览与挑选</h2><p class="muted">点击图片可选择或取消；发布时只带走选中的图片。</p></div><span class="status-badge">已选 {{ selected.length }} / {{ activeBatch.files.length }}</span></div>
      <div v-if="batches.length > 1" class="poster-preview__versions" aria-label="生成版本">
        <button v-for="(batch, index) in batches" :key="batch.revision" type="button" class="button button--quiet" :class="{ 'is-active': index === activeBatchIndex }" @click="selectBatch(index)">第 {{ batch.revision }} 版</button>
      </div>
      <div class="poster-preview__grid">
        <button v-for="(url, index) in activeBatch.urls" :key="url" type="button" class="poster-preview__card" :class="{ 'is-selected': selected.includes(index) }" :aria-pressed="selected.includes(index)" @click="togglePoster(index)">
          <img :src="url" :alt="`第 ${activeBatch.revision} 版海报 ${index + 1}`" />
          <span>{{ selected.includes(index) ? '✓ 已选择' : '点击选择' }} · 第 {{ index + 1 }} 张</span>
        </button>
      </div>
      <div class="poster-preview__actions">
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
