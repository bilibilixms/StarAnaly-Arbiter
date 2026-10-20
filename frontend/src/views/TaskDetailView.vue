<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getTask, listTaskMessages, sendTaskMessage, type Task, type TaskMessage } from '@/api/tasks'
import { formatCreatedAt, statusLabel } from '@/task/format'

const route = useRoute()
const task = ref<Task | null>(null)
const messages = ref<TaskMessage[]>([])
const loading = ref(false)
const error = ref('')
const draft = ref('')
const sending = ref(false)
const sendError = ref('')
let latestRequest = 0

async function loadTask() {
  const requestId = ++latestRequest
  const id = Number(route.params.id)
  task.value = null
  messages.value = []
  error.value = ''
  loading.value = false

  if (!Number.isSafeInteger(id) || id <= 0) {
    error.value = '任务编号无效。'
    return
  }

  loading.value = true
  try {
    const [taskResult, messageResult] = await Promise.all([getTask(id), listTaskMessages(id)])
    if (requestId === latestRequest) {
      task.value = taskResult
      messages.value = messageResult
      if (messageResult.length === 0 && !draft.value.trim()) {
        draft.value = taskResult.description
          ? `${taskResult.title}\n${taskResult.description}`
          : taskResult.title
      }
    }
  } catch (cause) {
    if (requestId === latestRequest) {
      error.value = cause instanceof Error ? cause.message : '加载任务失败。'
    }
  } finally {
    if (requestId === latestRequest) loading.value = false
  }
}

async function sendMessage() {
  if (!task.value || sending.value || task.value.status === 'RUNNING') return

  const taskId = task.value.id
  const content = draft.value.trim()
  sendError.value = ''
  if (!content) {
    sendError.value = '请输入消息内容。'
    return
  }

  sending.value = true
  try {
    await sendTaskMessage(taskId, content)
    if (Number(route.params.id) === taskId) draft.value = ''
  } catch (cause) {
    if (Number(route.params.id) === taskId) {
      sendError.value = cause instanceof Error ? cause.message : '发送消息失败。'
    }
  } finally {
    if (Number(route.params.id) === taskId) await loadTask()
    sending.value = false
  }
}

watch(
  () => route.params.id,
  () => {
    draft.value = ''
    sendError.value = ''
    void loadTask()
  },
  { immediate: true },
)
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="eyebrow">工作空间 / 任务详情</p>
      <h1>{{ task?.title ?? '任务详情' }}</h1>
      <p class="page-description">查看任务记录与当前状态。</p>
    </div>
    <RouterLink class="text-link" to="/tasks">返回任务列表</RouterLink>
  </section>

  <section class="panel panel--detail" aria-label="任务详情">
    <div v-if="loading" class="message" role="status">正在加载任务…</div>
    <div v-else-if="error" class="message message--error" role="alert">
      <p>{{ error }}</p>
      <button class="button button--secondary" type="button" @click="loadTask">重试</button>
    </div>
    <template v-else-if="task">
      <div class="detail-header">
        <div>
          <p class="eyebrow">任务 #{{ task.id }}</p>
          <h2>{{ task.title }}</h2>
        </div>
        <span class="status-badge">{{ statusLabel(task.status) }}</span>
      </div>
      <dl class="detail-meta">
        <div>
          <dt>创建时间</dt>
          <dd><time :datetime="task.createdAt">{{ formatCreatedAt(task.createdAt) }}</time></dd>
        </div>
        <div>
          <dt>当前状态</dt>
          <dd>{{ statusLabel(task.status) }}</dd>
        </div>
      </dl>
      <div class="detail-description">
        <h3>任务说明</h3>
        <p>{{ task.description || '暂无说明' }}</p>
      </div>
      <p v-if="task.lastError" class="form-error" role="alert">{{ task.lastError }}</p>
      <button class="button button--secondary" type="button" :disabled="loading" @click="loadTask">刷新状态</button>
    </template>
  </section>

  <section v-if="task" class="panel conversation" aria-label="任务对话">
    <div class="conversation__heading">
      <h2>任务对话</h2>
      <p>消息与回复会保存在当前任务中。</p>
    </div>
    <div v-if="messages.length === 0" class="conversation__empty">
      发送第一条消息，开始处理这个任务。
    </div>
    <ol v-else class="conversation__messages">
      <li v-for="message in messages" :key="message.id" :class="['conversation__message', message.role === 'USER' ? 'conversation__message--user' : 'conversation__message--assistant']">
        <div class="conversation__message-meta">
          <strong>{{ message.role === 'USER' ? '你' : '星析' }}</strong>
          <time :datetime="message.createdAt">{{ formatCreatedAt(message.createdAt) }}</time>
        </div>
        <p>{{ message.content }}</p>
      </li>
    </ol>
    <form class="conversation__form" @submit.prevent="sendMessage">
      <label for="task-message">{{ messages.length === 0 ? '开始任务' : '继续提问' }}</label>
      <textarea id="task-message" v-model="draft" rows="5" maxlength="5000" :disabled="sending || task.status === 'RUNNING'" placeholder="输入要交给模型处理的内容"></textarea>
      <p v-if="sendError" class="form-error" role="alert">{{ sendError }}</p>
      <div class="conversation__actions">
        <span>最多 5000 个字符</span>
        <button class="button" type="submit" :disabled="sending || task.status === 'RUNNING'">
          {{ sending ? '正在等待回复…' : '发送消息' }}
        </button>
      </div>
    </form>
  </section>
</template>
