<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  deleteKnowledgeDocument, getCsvPreview, getTask, listCsvFiles, listKnowledgeDocuments,
  listRunAnalyses, listRunCitations, listRunSteps, listTaskMessages, listTaskRuns,
  startTaskRun, uploadTableFile, uploadKnowledgeDocument,
  type CsvAnalysis, type CsvFile, type CsvPreview, type KnowledgeCitation,
  type KnowledgeDocument, type Task, type TaskMessage, type TaskRun, type TaskRunStep,
} from '@/api/tasks'
import { formatCreatedAt, statusLabel } from '@/task/format'

const route = useRoute()
const task = ref<Task | null>(null)
const messages = ref<TaskMessage[]>([])
const csvFiles = ref<CsvFile[]>([])
const documents = ref<KnowledgeDocument[]>([])
const runs = ref<TaskRun[]>([])
const activeRunId = ref<number | null>(null)
const steps = ref<TaskRunStep[]>([])
const analyses = ref<CsvAnalysis[]>([])
const citations = ref<KnowledgeCitation[]>([])
const preview = ref<CsvPreview | null>(null)
const previewFileId = ref<number | null>(null)
const draft = ref('')
const loading = ref(false)
const refreshing = ref(false)
const starting = ref(false)
const uploadBusy = ref(false)
const documentBusy = ref(false)
const previewLoading = ref(false)
const loadError = ref('')
const sendError = ref('')
const uploadError = ref('')
const previewError = ref('')
const notice = ref('')
let requestVersion = 0
let previewVersion = 0
let pollTimer: ReturnType<typeof setTimeout> | undefined
let pendingPrompt: { runId: number; content: string } | null = null

const activeRun = computed(() => runs.value.find((run) => run.id === activeRunId.value) ?? null)
const isRunning = computed(() => task.value?.status === 'RUNNING' || runs.value.some((run) => run.status === 'RUNNING'))

function taskId(): number | null {
  const id = Number(route.params.id)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

function clearPoll() {
  if (pollTimer !== undefined) clearTimeout(pollTimer)
  pollTimer = undefined
}

function reason(cause: unknown, fallback: string): string {
  return cause instanceof Error ? cause.message : fallback
}

function lineLabel(start: number, end: number): string {
  return start === end ? `第 ${start} 行` : `第 ${start}–${end} 行`
}

function stepLabel(kind: string): string {
  const labels: Record<string, string> = {
    MODEL_CALL: '模型请求', TOOL_CALL: '调用工具', TOOL_RESULT: '工具结果', FINAL_ANSWER: '最终回答',
  }
  return labels[kind] ?? kind
}

function formatSize(bytes: number): string {
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KiB`
}

async function loadWorkspace() {
  clearPoll()
  const id = taskId()
  const version = ++requestVersion
  loadError.value = ''
  if (id === null) {
    loadError.value = '任务编号无效。'
    return
  }
  loading.value = task.value === null
  refreshing.value = true
  try {
    const [nextTask, nextMessages, nextCsvFiles, nextDocuments, runPage] = await Promise.all([
      getTask(id), listTaskMessages(id), listCsvFiles(id), listKnowledgeDocuments(id), listTaskRuns(id),
    ])
    if (version !== requestVersion) return
    task.value = nextTask
    messages.value = nextMessages
    csvFiles.value = nextCsvFiles
    documents.value = nextDocuments
    runs.value = runPage.content
    if (previewFileId.value !== null && !nextCsvFiles.some((file) => file.id === previewFileId.value)) {
      closePreview()
    }
    const pendingRun = pendingPrompt
    if (pendingRun) {
      const pending = runPage.content.find((run) => run.id === pendingRun.runId)
      if (pending?.status === 'FAILED') {
        if (!draft.value.trim()) draft.value = pendingRun.content
        pendingPrompt = null
      } else if (pending?.status === 'SUCCEEDED') {
        pendingPrompt = null
      }
    }
    if (nextMessages.length === 0 && runPage.content.length === 0 && !draft.value.trim()) {
      draft.value = nextTask.description ? `${nextTask.title}\n${nextTask.description}` : nextTask.title
    }
    if (!runPage.content.some((run) => run.id === activeRunId.value)) {
      activeRunId.value = runPage.content[0]?.id ?? null
    }
    if (activeRunId.value !== null) {
      const [nextSteps, nextAnalyses, nextCitations] = await Promise.all([
        listRunSteps(id, activeRunId.value), listRunAnalyses(id, activeRunId.value),
        listRunCitations(id, activeRunId.value),
      ])
      if (version !== requestVersion) return
      steps.value = nextSteps
      analyses.value = nextAnalyses
      citations.value = nextCitations
    } else {
      steps.value = []
      analyses.value = []
      citations.value = []
    }
    if (nextTask.status === 'RUNNING' || runPage.content.some((run) => run.status === 'RUNNING')) {
      pollTimer = setTimeout(() => { void loadWorkspace() }, 2000)
    }
  } catch (cause) {
    if (version === requestVersion) {
      loadError.value = reason(cause, '加载工作空间失败。')
      if (task.value?.status === 'RUNNING') {
        pollTimer = setTimeout(() => { void loadWorkspace() }, 5000)
      }
    }
  } finally {
    if (version === requestVersion) {
      loading.value = false
      refreshing.value = false
    }
  }
}

async function submitMessage() {
  const id = taskId()
  if (id === null || starting.value || uploadBusy.value || documentBusy.value || isRunning.value) return
  const content = draft.value.trim()
  sendError.value = ''
  notice.value = ''
  if (!content) {
    sendError.value = '请输入消息内容。'
    return
  }
  starting.value = true
  try {
    const run = await startTaskRun(id, content)
    if (taskId() !== id) return
    pendingPrompt = { runId: run.id, content }
    draft.value = ''
    activeRunId.value = run.id
    steps.value = []
    analyses.value = []
    citations.value = []
    await loadWorkspace()
  } catch (cause) {
    if (taskId() === id) sendError.value = reason(cause, '提交任务失败。')
  } finally {
    starting.value = false
  }
}

async function uploadFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  const id = taskId()
  input.value = ''
  if (!file || id === null || starting.value || uploadBusy.value || documentBusy.value || isRunning.value) return
  uploadError.value = ''
  notice.value = ''
  const isTable = /\.(csv|xls|xlsx)$/i.test(file.name)
  const isText = /\.(txt|md|markdown)$/i.test(file.name)
  const isDocument = /\.(pdf|doc|docx)$/i.test(file.name)
  if (!isTable && !isText && !isDocument) {
    uploadError.value = '支持 CSV、Excel、TXT、Markdown、PDF 和 Word 文件。'
    return
  }
  const maxBytes = isText ? 1024 * 1024 : 5 * 1024 * 1024
  if (file.size === 0 || file.size > maxBytes) {
    uploadError.value = isText ? '文本资料必须非空且不超过 1 MiB。' : '文件必须非空且不超过 5 MiB。'
    return
  }
  uploadBusy.value = true
  try {
    if (isTable) await uploadTableFile(id, file)
    else await uploadKnowledgeDocument(id, file)
    if (taskId() !== id) return
    notice.value = `已上传${isTable ? '表格' : '资料'}：${file.name}`
    await loadWorkspace()
  } catch (cause) {
    if (taskId() === id) uploadError.value = reason(cause, '上传文件失败。')
  } finally {
    uploadBusy.value = false
  }
}

async function removeDocument(document: KnowledgeDocument) {
  const id = taskId()
  if (id === null || starting.value || uploadBusy.value || documentBusy.value || isRunning.value) return
  if (!window.confirm(`删除“${document.name}”？原文片段和执行引用也会清除，历史回答仍会保留。`)) return
  documentBusy.value = true
  uploadError.value = ''
  notice.value = ''
  try {
    await deleteKnowledgeDocument(id, document.id)
    if (taskId() !== id) return
    notice.value = `已删除资料：${document.name}`
    await loadWorkspace()
  } catch (cause) {
    if (taskId() === id) uploadError.value = reason(cause, '删除资料失败。')
  } finally {
    documentBusy.value = false
  }
}

async function togglePreview(fileId: number) {
  if (previewFileId.value === fileId) {
    closePreview()
    return
  }
  const id = taskId()
  if (id === null) return
  const version = ++previewVersion
  previewFileId.value = fileId
  preview.value = null
  previewError.value = ''
  previewLoading.value = true
  try {
    const result = await getCsvPreview(id, fileId)
    if (taskId() === id && version === previewVersion) preview.value = result
  } catch (cause) {
    if (taskId() === id && version === previewVersion) {
      previewError.value = reason(cause, '加载表格预览失败。')
    }
  } finally {
    if (taskId() === id && version === previewVersion) previewLoading.value = false
  }
}

function closePreview() {
  previewVersion++
  previewFileId.value = null
  preview.value = null
  previewError.value = ''
  previewLoading.value = false
}

function selectRun(runId: number) {
  if (activeRunId.value === runId) return
  activeRunId.value = runId
  steps.value = []
  analyses.value = []
  citations.value = []
  void loadWorkspace()
}

watch(() => route.params.id, () => {
  clearPoll()
  requestVersion++
  previewVersion++
  task.value = null
  messages.value = []
  csvFiles.value = []
  documents.value = []
  runs.value = []
  activeRunId.value = null
  steps.value = []
  analyses.value = []
  citations.value = []
  preview.value = null
  previewFileId.value = null
  previewLoading.value = false
  draft.value = ''
  loadError.value = ''
  sendError.value = ''
  uploadError.value = ''
  previewError.value = ''
  notice.value = ''
  pendingPrompt = null
  void loadWorkspace()
}, { immediate: true })

onUnmounted(() => {
  clearPoll()
  requestVersion++
})
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="eyebrow">工作空间 / 任务详情</p>
      <h1>{{ task?.title ?? '任务详情' }}</h1>
      <p class="page-description">上传文件、发起分析，查看执行过程和依据。</p>
    </div>
    <RouterLink class="text-link" to="/tasks">返回任务列表</RouterLink>
  </section>

  <div v-if="loading && !task" class="panel message" role="status">正在加载任务…</div>
  <div v-else-if="loadError && !task" class="panel message message--error" role="alert">
    <p>{{ loadError }}</p>
    <button class="button button--secondary" type="button" @click="loadWorkspace">重试</button>
  </div>
  <template v-else-if="task">
    <section class="panel workspace-section" aria-label="任务概览">
      <div class="section-heading">
        <div>
          <p class="eyebrow">任务 #{{ task.id }}</p>
          <h2>{{ task.title }}</h2>
        </div>
        <span class="status-badge">{{ statusLabel(task.status) }}</span>
      </div>
      <p class="workspace-description">{{ task.description || '暂无任务说明。' }}</p>
      <p class="muted">创建于 <time :datetime="task.createdAt">{{ formatCreatedAt(task.createdAt) }}</time></p>
      <p v-if="task.lastError" class="inline-error" role="alert">执行失败：{{ task.lastError }}。可修改下方问题后重新提交。</p>
      <div v-if="loadError" class="inline-error" role="alert">{{ loadError }}</div>
      <div class="section-actions">
        <button class="button button--secondary button--small" type="button" :disabled="refreshing" @click="loadWorkspace">
          {{ refreshing ? '正在刷新…' : '刷新工作空间' }}
        </button>
        <span v-if="isRunning" class="muted" role="status">任务执行中，页面会自动更新。</span>
      </div>
    </section>

    <section class="panel workspace-section" aria-label="上传文件">
      <div class="section-heading"><h2>上传文件</h2></div>
      <label class="upload-control">
        <span>{{ uploadBusy ? '正在上传…' : '选择文件' }}</span>
        <input type="file" accept=".csv,.xls,.xlsx,.txt,.md,.markdown,.pdf,.doc,.docx" :disabled="starting || uploadBusy || documentBusy || isRunning" @change="uploadFile" />
      </label>
      <p class="field-hint">CSV、Excel 用于表格分析，单文件最多 5 MiB；TXT、Markdown 用于资料问答，最多 1 MiB。</p>
      <p class="field-hint">PDF、Word 用于资料问答，文件最多 5 MiB、提取文字最多 1 MiB；扫描版 PDF 需先进行 OCR。</p>
    </section>
    <p v-if="uploadError" class="inline-error workspace-feedback" role="alert">{{ uploadError }}</p>
    <p v-if="notice" class="inline-success workspace-feedback" role="status">{{ notice }}</p>

    <section class="workspace-grid" aria-label="任务文件">
      <div class="panel workspace-section">
        <div class="section-heading"><h2>表格数据</h2><span class="muted">{{ csvFiles.length }} 个文件</span></div>
        <p v-if="csvFiles.length === 0" class="empty-note">暂无表格文件。上传后可预览前 20 行。</p>
        <ul v-else class="file-list">
          <li v-for="file in csvFiles" :key="file.id">
            <div class="file-list__info">
              <strong>{{ file.name }}</strong>
              <span>{{ file.rowCount }} 行 · {{ formatSize(file.byteSize) }} · ID {{ file.id }}</span>
              <span class="file-list__columns">字段：{{ file.headers.join('、') }}</span>
            </div>
            <button class="text-button" type="button" @click="togglePreview(file.id)">
              {{ previewFileId === file.id ? '收起' : '预览' }}
            </button>
          </li>
        </ul>
        <div v-if="previewFileId !== null" class="preview-area">
          <p v-if="previewLoading" role="status">正在读取预览…</p>
          <p v-else-if="previewError" class="inline-error" role="alert">{{ previewError }} <button class="text-button" type="button" @click="closePreview">关闭</button></p>
          <div v-else-if="preview" class="table-wrap">
            <table class="data-table">
              <thead><tr><th v-for="header in preview.file.headers" :key="header" scope="col">{{ header }}</th></tr></thead>
              <tbody>
                <tr v-for="(row, index) in preview.rows" :key="index">
                  <td v-for="header in preview.file.headers" :key="header">{{ row[header] }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="panel workspace-section">
        <div class="section-heading"><h2>知识资料</h2><span class="muted">{{ documents.length }} 份资料</span></div>
        <p v-if="documents.length === 0" class="empty-note">暂无资料。上传后可在提问时引用原文。</p>
        <ul v-else class="file-list">
          <li v-for="document in documents" :key="document.id">
            <div class="file-list__info">
              <strong>{{ document.name }}</strong>
              <span>{{ document.chunkCount }} 个片段 · {{ formatSize(document.byteSize) }}</span>
            </div>
            <button class="text-button text-button--danger" type="button" :disabled="starting || uploadBusy || documentBusy || isRunning" @click="removeDocument(document)">删除</button>
          </li>
        </ul>
      </div>
    </section>

    <section class="panel conversation workspace-section" aria-label="任务对话">
      <div class="section-heading"><h2>任务对话</h2><span class="muted">{{ messages.length }} 条消息</span></div>
      <p v-if="messages.length === 0" class="empty-note">输入问题，开始表格分析或资料问答。</p>
      <ol v-else class="conversation__messages">
        <li v-for="message in messages" :key="message.id" :class="['conversation__message', message.role === 'USER' ? 'conversation__message--user' : 'conversation__message--assistant']">
          <div class="conversation__message-meta">
            <strong>{{ message.role === 'USER' ? '你' : '星析' }}</strong>
            <time :datetime="message.createdAt">{{ formatCreatedAt(message.createdAt) }}</time>
          </div>
          <p>{{ message.content }}</p>
        </li>
      </ol>
      <form class="conversation__form" @submit.prevent="submitMessage">
        <label for="task-message">{{ messages.length === 0 ? '开始任务' : '继续提问' }}</label>
        <textarea id="task-message" v-model="draft" rows="5" maxlength="5000" :disabled="starting || uploadBusy || documentBusy || isRunning" placeholder="例如：请按月汇总表格销售额；或根据上传资料回答退货期限。"></textarea>
        <p v-if="sendError" class="form-error" role="alert">{{ sendError }}</p>
        <div class="conversation__actions">
          <span>最多 5000 个字符</span>
          <button class="button" type="submit" :disabled="starting || uploadBusy || documentBusy || isRunning">
            {{ starting ? '正在提交…' : isRunning ? '执行中…' : '开始执行' }}
          </button>
        </div>
      </form>
    </section>

    <section class="panel workspace-section" aria-label="执行记录">
      <div class="section-heading"><h2>执行记录</h2><span class="muted">最近 {{ runs.length }} 次</span></div>
      <p v-if="runs.length === 0" class="empty-note">提交问题后，这里会显示模型和工具的执行步骤。</p>
      <template v-else>
        <div class="run-selector" role="group" aria-label="选择执行记录">
          <button v-for="run in runs" :key="run.id" class="run-choice" :class="{ 'run-choice--active': activeRunId === run.id }" type="button" :aria-pressed="activeRunId === run.id" @click="selectRun(run.id)">
            <strong>#{{ run.id }}</strong> {{ statusLabel(run.status) }}
            <small>{{ formatCreatedAt(run.startedAt) }}</small>
          </button>
        </div>
        <div v-if="activeRun" class="run-detail">
          <p v-if="activeRun.lastError" class="inline-error" role="alert">{{ activeRun.lastError }}</p>
          <p v-else-if="activeRun.status === 'RUNNING'" class="muted" role="status">执行中，步骤会自动刷新。</p>
          <h3>执行步骤</h3>
          <p v-if="steps.length === 0" class="empty-note">尚无步骤记录。</p>
          <ol v-else class="step-list">
            <li v-for="step in steps" :key="step.id">
              <span class="step-list__number">{{ step.stepNumber }}</span>
              <div>
                <strong>{{ stepLabel(step.kind) }}<span v-if="step.toolName"> · {{ step.toolName }}</span></strong>
                <span class="step-list__status">{{ statusLabel(step.status) }}</span>
                <p>{{ step.inputSummary }}</p>
                <p v-if="step.outputSummary" class="muted">{{ step.outputSummary }}</p>
              </div>
            </li>
          </ol>

          <div class="result-grid">
            <div class="result-card">
              <h3>表格分析结果</h3>
              <p v-if="analyses.length === 0" class="empty-note">本次执行暂无结构化分析结果。</p>
              <article v-for="analysis in analyses" :key="analysis.id" class="result-entry">
                <p><strong>文件 #{{ analysis.result.fileId }}</strong> · 按 {{ analysis.result.groupBy }} 汇总 {{ analysis.result.sumColumn }}{{ analysis.result.byMonth ? '（按月）' : '' }}</p>
                <p>匹配 {{ analysis.result.matchedRows }} 行，总计 <strong>{{ analysis.result.total }}</strong></p>
                <div class="table-wrap">
                  <table class="data-table">
                    <thead><tr><th scope="col">分组</th><th scope="col">合计</th><th scope="col">行数</th></tr></thead>
                    <tbody><tr v-for="group in analysis.result.groups" :key="group.key"><td>{{ group.key }}</td><td>{{ group.sum }}</td><td>{{ group.rowCount }}</td></tr></tbody>
                  </table>
                </div>
              </article>
            </div>
            <div class="result-card">
              <h3>资料来源</h3>
              <p v-if="citations.length === 0" class="empty-note">本次执行暂无可查看的来源。删除资料后，其引用会清空。</p>
              <ol v-else class="citation-list">
                <li v-for="citation in citations" :key="citation.id">
                  <strong>{{ citation.documentName }} · {{ lineLabel(citation.startLine, citation.endLine) }}</strong>
                  <blockquote>{{ citation.excerpt }}</blockquote>
                </li>
              </ol>
            </div>
          </div>
        </div>
      </template>
    </section>
  </template>
</template>
