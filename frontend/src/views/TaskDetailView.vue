<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  deleteKnowledgeDocument, deleteTaskImage, getTask,
  listKnowledgeDocuments, listTaskImages, updateTaskPlan, updateTaskRequirements,
  uploadKnowledgeDocument, uploadTaskImage,
  type KnowledgeDocument, type Task, type TaskImage,
} from '@/api/tasks'
import { listTaskXiaohongshuNotes, type XiaohongshuNote } from '@/api/sharing'
import { listTaskDouyinNotes, type DouyinNote } from '@/api/douyin'
import { formatCreatedAt, workflowStageLabel } from '@/task/format'

const route = useRoute()
const taskId = computed(() => {
  const id = Number(route.params.id)
  return Number.isSafeInteger(id) && id > 0 ? id : null
})
const task = ref<Task | null>(null)
const images = ref<TaskImage[]>([])
const documents = ref<KnowledgeDocument[]>([])
const notes = ref<XiaohongshuNote[]>([])
const douyinNotes = ref<DouyinNote[]>([])
const title = ref('')
const description = ref('')
const summary = ref('')
const loading = ref(false)
const savingRequirements = ref(false)
const savingSummary = ref(false)
const handlingFiles = ref(false)
const loadError = ref('')
const actionError = ref('')
const notice = ref('')
let loadedFormFor: number | null = null
let loadVersion = 0

const requirementsChanged = computed(() => !!task.value && (
  title.value.trim() !== task.value.title
  || description.value.trim() !== (task.value.description ?? '')
))
const summaryChanged = computed(() => !!task.value
  && summary.value.trim() !== (task.value.discussionSummary ?? ''))

function reason(cause: unknown, fallback: string): string {
  return cause instanceof Error ? cause.message : fallback
}

function formatSize(bytes: number): string {
  return bytes < 1024 ? String(bytes) + ' B' : (bytes / 1024).toFixed(1) + ' KiB'
}

async function load() {
  const id = taskId.value
  const version = ++loadVersion
  if (id === null) {
    loadError.value = '需求编号无效。'
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const [nextTask, nextImages, nextDocuments, nextNotes, nextDouyinNotes] = await Promise.all([
      getTask(id), listTaskImages(id), listKnowledgeDocuments(id),
      listTaskXiaohongshuNotes(id), listTaskDouyinNotes(id),
    ])
    if (version !== loadVersion) return
    task.value = nextTask
    images.value = nextImages
    documents.value = nextDocuments
    notes.value = nextNotes
    douyinNotes.value = nextDouyinNotes
    if (loadedFormFor !== id) {
      title.value = nextTask.title
      description.value = nextTask.description ?? ''
      summary.value = nextTask.discussionSummary ?? ''
      loadedFormFor = id
    }
  } catch (cause) {
    if (version === loadVersion) loadError.value = reason(cause, '读取需求失败。')
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

async function saveRequirements() {
  const id = taskId.value
  if (id === null || !task.value || savingRequirements.value) return
  actionError.value = ''
  notice.value = ''
  if (!title.value.trim()) {
    actionError.value = '请填写需求名称。'
    return
  }
  savingRequirements.value = true
  try {
    const updated = await updateTaskRequirements(id, title.value.trim(), description.value.trim())
    if (taskId.value !== id) return
    task.value = updated
    title.value = updated.title
    description.value = updated.description ?? ''
    await load()
    notice.value = '原始要求已保存。'
  } catch (cause) {
    if (taskId.value === id) actionError.value = reason(cause, '保存原始要求失败。')
  } finally {
    savingRequirements.value = false
  }
}

async function saveSummary() {
  const id = taskId.value
  const current = task.value
  if (id === null || !current || savingSummary.value) return
  actionError.value = ''
  notice.value = ''
  savingSummary.value = true
  try {
    const updated = await updateTaskPlan(id, current.workflowStage, summary.value.trim())
    if (taskId.value !== id) return
    task.value = updated
    summary.value = updated.discussionSummary ?? ''
    await load()
    notice.value = '补充说明已保存。'
  } catch (cause) {
    if (taskId.value === id) actionError.value = reason(cause, '保存补充说明失败。')
  } finally {
    savingSummary.value = false
  }
}

async function uploadFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  const id = taskId.value
  if (id === null || !files.length || handlingFiles.value) return
  actionError.value = ''
  notice.value = ''
  handlingFiles.value = true
  let imageCount = images.value.length
  let imageBytes = images.value.reduce((total, image) => total + image.byteSize, 0)
  let uploaded = 0
  const failures: string[] = []
  try {
    for (const file of files) {
      if (taskId.value !== id) break
      const image = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)
      const document = /\.(pdf|doc|docx|txt|md|markdown)$/i.test(file.name)
      if (!image && !document) {
        failures.push(file.name + '：只支持图片、PDF、Word 或文字文档')
        continue
      }
      if (file.size === 0) {
        failures.push(file.name + '：文件不能为空')
        continue
      }
      if (image && (file.size > 20_000_000 || imageCount >= 6
          || imageBytes + file.size > 30_000_000)) {
        failures.push(file.name + '：图片最多 6 张，单张 20 MB，总计 30 MB')
        continue
      }
      const textFile = /\.(txt|md|markdown)$/i.test(file.name)
      if (document && file.size > (textFile ? 1024 * 1024 : 5 * 1024 * 1024)) {
        failures.push(file.name + '：文档超过大小限制')
        continue
      }
      try {
        if (image) {
          const saved = await uploadTaskImage(id, file)
          imageCount++
          imageBytes += saved.byteSize
        } else {
          await uploadKnowledgeDocument(id, file)
        }
        uploaded++
      } catch (cause) {
        failures.push(file.name + '：' + reason(cause, '上传失败'))
      }
    }
    if (taskId.value === id) {
      if (uploaded) await load()
      notice.value = uploaded ? '已添加 ' + uploaded + ' 份材料。' : ''
      actionError.value = failures.join('；')
    }
  } finally {
    handlingFiles.value = false
  }
}

async function removeImage(image: TaskImage) {
  const id = taskId.value
  if (id === null || handlingFiles.value || !window.confirm('删除图片“' + image.name + '”？')) return
  handlingFiles.value = true
  actionError.value = ''
  notice.value = ''
  try {
    await deleteTaskImage(id, image.id)
    if (taskId.value !== id) return
    await load()
    notice.value = '图片已删除。'
  } catch (cause) {
    if (taskId.value === id) actionError.value = reason(cause, '删除图片失败。')
  } finally {
    handlingFiles.value = false
  }
}

async function removeDocument(document: KnowledgeDocument) {
  const id = taskId.value
  if (id === null || handlingFiles.value || !window.confirm('删除文档“' + document.name + '”？')) return
  handlingFiles.value = true
  actionError.value = ''
  notice.value = ''
  try {
    await deleteKnowledgeDocument(id, document.id)
    if (taskId.value !== id) return
    await load()
    notice.value = '文档已删除。'
  } catch (cause) {
    if (taskId.value === id) actionError.value = reason(cause, '删除文档失败。')
  } finally {
    handlingFiles.value = false
  }
}

watch(taskId, () => {
  loadVersion++
  task.value = null
  images.value = []
  documents.value = []
  notes.value = []
  loadedFormFor = null
  title.value = ''
  description.value = ''
  summary.value = ''
  actionError.value = ''
  notice.value = ''
  void load()
}, { immediate: true })
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="eyebrow">工作空间 / 需求单</p>
      <h1>{{ task?.title ?? '需求详情' }}</h1>
      <p class="page-description">把收到的原始要求放在这里，再到工作台确定宣传方向。</p>
    </div>
    <RouterLink class="text-link" to="/tasks">返回需求列表</RouterLink>
  </section>

  <div v-if="loading && !task" class="panel message" role="status">正在读取需求…</div>
  <div v-else-if="loadError && !task" class="panel message message--error" role="alert">
    <p>{{ loadError }}</p>
    <button class="button button--secondary" type="button" @click="load">重试</button>
  </div>
  <template v-else-if="task">
    <p v-if="loadError" class="inline-error" role="alert">{{ loadError }}</p>
    <p v-if="actionError" class="inline-error" role="alert">{{ actionError }}</p>
    <p v-if="notice" class="inline-success" role="status">{{ notice }}</p>

    <div class="demand-layout">
      <div class="demand-layout__main">
        <section class="panel workspace-section" aria-labelledby="demand-requirements-title">
          <div class="section-heading">
            <div>
              <h2 id="demand-requirements-title">原始要求</h2>
              <p class="muted">保留客户的原话，工作台会以此为依据。</p>
            </div>
            <time class="muted" :datetime="task.createdAt">{{ formatCreatedAt(task.createdAt) }}</time>
          </div>
          <form class="demand-form" @submit.prevent="saveRequirements">
            <div class="field">
              <label for="demand-title">需求名称</label>
              <input id="demand-title" v-model="title" type="text" maxlength="200" required />
            </div>
            <div class="field">
              <label for="demand-description">客户说了什么</label>
              <textarea id="demand-description" v-model="description" rows="9" maxlength="5000"
                placeholder="直接粘贴收到的文字；图片和文档可以在下面添加。"></textarea>
            </div>
            <button class="button button--secondary" type="submit" :disabled="savingRequirements || !requirementsChanged">
              {{ savingRequirements ? '正在保存…' : '保存原始要求' }}
            </button>
          </form>
        </section>

        <section class="panel workspace-section" aria-labelledby="demand-materials-title">
          <div class="section-heading">
            <div>
              <h2 id="demand-materials-title">参考材料</h2>
              <p class="muted">图片与文档会作为海报创作的参考材料。</p>
            </div>
            <span class="demand-material-count">{{ images.length }} 张图片 · {{ documents.length }} 份文档</span>
          </div>
          <label class="upload-control">
            <input class="upload-control__input" type="file" multiple
              accept="image/jpeg,image/png,image/gif,image/webp,.pdf,.doc,.docx,.txt,.md,.markdown"
              :disabled="handlingFiles" @change="uploadFiles" />
            <span class="upload-control__button">{{ handlingFiles ? '正在处理…' : '添加图片或文档' }}</span>
          </label>
          <p class="field-hint">图片最多 6 张，单张 20 MB；PDF、Word 每份不超过 5 MiB。</p>
          <p v-if="images.length === 0 && documents.length === 0" class="empty-note">还没有添加材料。只用文字要求也可以生成海报。</p>

          <div v-if="images.length" class="demand-material-group">
            <h3>图片</h3>
            <ul class="task-images__list">
              <li v-for="image in images" :key="image.id">
                <a :href="'/api/tasks/' + task.id + '/images/' + image.id" target="_blank" rel="noopener noreferrer">
                  <img :src="'/api/tasks/' + task.id + '/images/' + image.id" :alt="image.name" />
                </a>
                <strong>{{ image.name }}</strong>
                <button class="text-button text-button--danger" type="button" :disabled="handlingFiles"
                  :aria-label="'删除图片 ' + image.name" @click="removeImage(image)">删除</button>
              </li>
            </ul>
          </div>
          <div v-if="documents.length" class="demand-material-group">
            <h3>文档</h3>
            <ul class="file-list">
              <li v-for="document in documents" :key="document.id">
                <span class="demand-document"><strong>{{ document.name }}</strong><small>{{ formatSize(document.byteSize) }}</small></span>
                <button class="text-button text-button--danger" type="button" :disabled="handlingFiles"
                  :aria-label="'删除文档 ' + document.name" @click="removeDocument(document)">删除</button>
              </li>
            </ul>
          </div>
        </section>
      </div>

      <aside class="demand-layout__aside">
        <section class="panel workspace-section demand-progress" aria-labelledby="demand-progress-title">
          <h2 id="demand-progress-title">下一步</h2>
          <p class="demand-progress__status">准备生成海报</p>
          <p class="muted">到工作台设置海报主题和张数，生成后预览、修改，再挑选图片去小红书或抖音创作。</p>
          <RouterLink class="button" :to="{ name: 'creative-workbench-task', params: { id: task.id } }">
            去工作台生成海报
          </RouterLink>
          <span class="demand-progress__stage">当前阶段：{{ workflowStageLabel(task.workflowStage) }}</span>
        </section>

        <section class="panel workspace-section" aria-labelledby="demand-summary-title">
          <h2 id="demand-summary-title">我的补充说明</h2>
          <p class="muted">写下原始要求之外需要考虑的重点，工作台也会参考。</p>
          <form class="demand-form" @submit.prevent="saveSummary">
            <div class="field">
              <label class="sr-only" for="demand-summary">补充说明</label>
              <textarea id="demand-summary" v-model="summary" rows="6" maxlength="5000"
                placeholder="例如：这次重点吸引新玩家，避免夸大游戏体验。"></textarea>
            </div>
            <button class="button button--secondary" type="submit" :disabled="savingSummary || !summaryChanged">
              {{ savingSummary ? '正在保存…' : '保存补充说明' }}
            </button>
          </form>
        </section>

        <section v-if="notes.length || douyinNotes.length" class="panel workspace-section" aria-labelledby="demand-outputs-title">
          <div class="section-heading"><h2 id="demand-outputs-title">已生成的作品</h2><span class="muted">{{ notes.length + douyinNotes.length }} 篇</span></div>
          <ul class="task-notes__list">
            <li v-for="note in notes" :key="`xhs-${note.id}`">
              <RouterLink class="task-notes__destination" :to="{ name: 'share-xiaohongshu', query: { noteId: note.id } }">
                <strong>{{ note.title }}</strong><span>小红书 · {{ note.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span>
              </RouterLink>
            </li>
            <li v-for="note in douyinNotes" :key="`dy-${note.id}`">
              <RouterLink class="task-notes__destination" :to="{ name: 'share-douyin', query: { noteId: note.id } }">
                <strong>{{ note.title }}</strong><span>抖音 · {{ note.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span>
              </RouterLink>
            </li>
          </ul>
        </section>
      </aside>
    </div>
  </template>
</template>
