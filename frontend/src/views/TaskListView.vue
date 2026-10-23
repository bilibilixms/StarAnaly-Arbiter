<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { listTasks, type TaskPage, type TaskStatus, type TaskTimeOrder } from '@/api/tasks'
import { formatCreatedAt, statusLabel, workflowStageLabel } from '@/task/format'

const statuses: Array<{ value: TaskStatus | ''; label: string }> = [
  { value: '', label: '全部 AI 状态' },
  { value: 'PENDING', label: '未执行' },
  { value: 'RUNNING', label: '执行中' },
  { value: 'SUCCEEDED', label: '回复完成' },
  { value: 'FAILED', label: '执行失败' },
]
const page = ref(0)
const status = ref<TaskStatus | ''>('')
const timeOrder = ref<TaskTimeOrder>('newest')
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
    const response = await listTasks(page.value, status.value || undefined, query.value, timeOrder.value)
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

function changeTimeOrder() {
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
      <p class="eyebrow">工作空间 / 需求单</p>
      <h1>需求列表</h1>
      <p class="page-description">从原始材料开始，跟进每份宣传需求的讨论、创作和发布。</p>
    </div>
    <RouterLink class="button" to="/tasks/new">新建需求</RouterLink>
  </section>

  <section class="panel" aria-label="需求列表">
    <div class="task-filters">
      <div class="task-filters__statuses" role="group" aria-label="按 AI 执行状态筛选任务">
        <button v-for="option in statuses" :key="option.value" type="button" class="task-filters__status"
          :class="{ 'task-filters__status--active': status === option.value }"
          :aria-pressed="status === option.value" @click="selectStatus(option.value)">
          {{ option.label }}
        </button>
      </div>
      <div class="task-filters__controls">
        <form class="task-filters__search" role="search" @submit.prevent="applySearch">
          <label for="task-title-search">查找需求</label>
          <input id="task-title-search" v-model="searchInput" type="search" maxlength="200"
            placeholder="输入需求名称" />
          <button class="button button--secondary button--small" type="submit">搜索</button>
        </form>
        <div class="task-filters__sort">
          <label for="task-time-order">创建时间</label>
          <select id="task-time-order" v-model="timeOrder" @change="changeTimeOrder">
            <option value="newest">最新优先</option>
            <option value="oldest">最早优先</option>
          </select>
        </div>
      </div>
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
        <h2>还没有需求</h2>
        <p>创建一份需求，集中保存文字、图片和文档。</p>
        <RouterLink class="button" to="/tasks/new">新建需求</RouterLink>
      </template>
    </div>
    <template v-else-if="result">
      <div class="table-wrap">
        <table class="task-table">
          <thead>
            <tr>
              <th scope="col">需求</th>
              <th scope="col">当前阶段</th>
              <th scope="col">AI 执行</th>
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
              <td><span class="status-badge">{{ workflowStageLabel(task.workflowStage) }}</span></td>
              <td>{{ statusLabel(task.status) }}</td>
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
