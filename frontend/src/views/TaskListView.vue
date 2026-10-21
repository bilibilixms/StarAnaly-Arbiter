<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { listTasks, type TaskPage, type TaskStatus } from '@/api/tasks'
import { formatCreatedAt, statusLabel } from '@/task/format'

const statuses: Array<{ value: TaskStatus | ''; label: string }> = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '等待处理' },
  { value: 'RUNNING', label: '执行中' },
  { value: 'SUCCEEDED', label: '已完成' },
  { value: 'FAILED', label: '执行失败' },
]
const page = ref(0)
const status = ref<TaskStatus | ''>('')
const searchInput = ref('')
const query = ref('')
const result = ref<TaskPage | null>(null)
const loading = ref(false)
const error = ref('')
let requestVersion = 0
let searchTimer: ReturnType<typeof setTimeout> | undefined

async function loadTasks() {
  const version = ++requestVersion
  loading.value = true
  error.value = ''
  result.value = null

  try {
    const response = await listTasks(page.value, status.value || undefined, query.value)
    if (version === requestVersion) result.value = response
  } catch (cause) {
    if (version === requestVersion) error.value = cause instanceof Error ? cause.message : '加载任务失败。'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function applySearch() {
  if (searchTimer !== undefined) clearTimeout(searchTimer)
  searchTimer = undefined
  const next = searchInput.value.trim()
  if (next === query.value) return
  query.value = next
  page.value = 0
  void loadTasks()
}

function selectStatus(next: TaskStatus | '') {
  if (status.value === next) return
  status.value = next
  query.value = searchInput.value.trim()
  if (searchTimer !== undefined) clearTimeout(searchTimer)
  searchTimer = undefined
  page.value = 0
  void loadTasks()
}

function clearFilters() {
  searchInput.value = ''
  query.value = ''
  status.value = ''
  page.value = 0
  if (searchTimer !== undefined) clearTimeout(searchTimer)
  searchTimer = undefined
  void loadTasks()
}

function changePage(nextPage: number) {
  if (loading.value || nextPage < 0 || nextPage >= (result.value?.totalPages ?? 0)) return
  page.value = nextPage
  void loadTasks()
}

watch(searchInput, () => {
  if (searchTimer !== undefined) clearTimeout(searchTimer)
  searchTimer = setTimeout(applySearch, 300)
})

onMounted(() => {
  void loadTasks()
})

onUnmounted(() => {
  requestVersion++
  if (searchTimer !== undefined) clearTimeout(searchTimer)
})
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="eyebrow">工作空间 / 任务</p>
      <h1>任务列表</h1>
      <p class="page-description">按状态浏览任务，或输入任务名查找；同名任务会分别列出。</p>
    </div>
    <RouterLink class="button" to="/tasks/new">新建任务</RouterLink>
  </section>

  <section class="panel" aria-label="任务列表">
    <div class="task-filters">
      <div class="task-filters__statuses" role="group" aria-label="按状态筛选任务">
        <button v-for="option in statuses" :key="option.value" type="button" class="task-filters__status"
          :class="{ 'task-filters__status--active': status === option.value }"
          :aria-pressed="status === option.value" @click="selectStatus(option.value)">
          {{ option.label }}
        </button>
      </div>
      <form class="task-filters__search" role="search" @submit.prevent="applySearch">
        <label for="task-title-search">查找任务</label>
        <input id="task-title-search" v-model="searchInput" type="search" maxlength="200"
          placeholder="输入任务名，支持同拼音汉字和少量错字" />
        <button class="button button--secondary button--small" type="submit">搜索</button>
      </form>
    </div>
    <div v-if="loading" class="message" role="status">正在加载任务…</div>
    <div v-else-if="error" class="message message--error" role="alert">
      <p>{{ error }}</p>
      <button class="button button--secondary" type="button" @click="loadTasks">重试</button>
    </div>
    <div v-else-if="result && result.content.length === 0" class="message message--empty">
      <template v-if="status || query">
        <h2>没有找到匹配的任务</h2>
        <p>试试缩短任务名，或切换任务状态。</p>
        <button class="button button--secondary" type="button" @click="clearFilters">清除筛选</button>
      </template>
      <template v-else>
        <h2>还没有任务</h2>
        <p>创建一个任务，开始记录你的工作。</p>
        <RouterLink class="button" to="/tasks/new">创建任务</RouterLink>
      </template>
    </div>
    <template v-else-if="result">
      <div class="table-wrap">
        <table class="task-table">
          <thead>
            <tr>
              <th scope="col">任务</th>
              <th scope="col">状态</th>
              <th scope="col">创建时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in result.content" :key="task.id">
              <td>
                <RouterLink class="task-link" :to="{ name: 'task-detail', params: { id: task.id } }">
                  {{ task.title }}
                </RouterLink>
                <span class="task-id">#{{ task.id }}</span>
              </td>
              <td><span class="status-badge">{{ statusLabel(task.status) }}</span></td>
              <td>
                <time :datetime="task.createdAt">{{ formatCreatedAt(task.createdAt) }}</time>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination">
        <span>共 {{ result.totalElements }} 个任务 · 第 {{ page + 1 }} / {{ result.totalPages }} 页</span>
        <div class="pagination__buttons">
          <button class="button button--secondary button--small" type="button" :disabled="page === 0" @click="changePage(page - 1)">
            上一页
          </button>
          <button class="button button--secondary button--small" type="button" :disabled="page + 1 >= result.totalPages" @click="changePage(page + 1)">
            下一页
          </button>
        </div>
      </div>
    </template>
  </section>
</template>
