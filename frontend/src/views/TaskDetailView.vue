<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getTask, type Task } from '@/api/tasks'
import { formatCreatedAt, statusLabel } from '@/task/format'

const route = useRoute()
const task = ref<Task | null>(null)
const loading = ref(false)
const error = ref('')
let latestRequest = 0

async function loadTask() {
  const requestId = ++latestRequest
  const id = Number(route.params.id)
  task.value = null
  error.value = ''
  loading.value = false

  if (!Number.isSafeInteger(id) || id <= 0) {
    error.value = '任务编号无效。'
    return
  }

  loading.value = true
  try {
    const result = await getTask(id)
    if (requestId === latestRequest) task.value = result
  } catch (cause) {
    if (requestId === latestRequest) {
      error.value = cause instanceof Error ? cause.message : '加载任务失败。'
    }
  } finally {
    if (requestId === latestRequest) loading.value = false
  }
}

watch(
  () => route.params.id,
  () => {
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
      <button class="button button--secondary" type="button" :disabled="loading" @click="loadTask">刷新状态</button>
    </template>
  </section>
</template>
