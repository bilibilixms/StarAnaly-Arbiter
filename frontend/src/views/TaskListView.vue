<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listTasks, type TaskPage } from '@/api/tasks'
import { formatCreatedAt, statusLabel } from '@/task/format'

const page = ref(0)
const result = ref<TaskPage | null>(null)
const loading = ref(false)
const error = ref('')

async function loadTasks() {
  loading.value = true
  error.value = ''
  result.value = null

  try {
    result.value = await listTasks(page.value)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载任务失败。'
  } finally {
    loading.value = false
  }
}

function changePage(nextPage: number) {
  if (loading.value || nextPage < 0 || nextPage >= (result.value?.totalPages ?? 0)) return
  page.value = nextPage
  void loadTasks()
}

onMounted(() => {
  void loadTasks()
})
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="eyebrow">工作空间 / 任务</p>
      <h1>任务列表</h1>
      <p class="page-description">查看已创建的任务及其当前状态。</p>
    </div>
    <RouterLink class="button" to="/tasks/new">新建任务</RouterLink>
  </section>

  <section class="panel" aria-label="任务列表">
    <div v-if="loading" class="message" role="status">正在加载任务…</div>
    <div v-else-if="error" class="message message--error" role="alert">
      <p>{{ error }}</p>
      <button class="button button--secondary" type="button" @click="loadTasks">重试</button>
    </div>
    <div v-else-if="result && result.content.length === 0" class="message message--empty">
      <h2>还没有任务</h2>
      <p>创建一个任务，开始记录你的工作。</p>
      <RouterLink class="button" to="/tasks/new">创建任务</RouterLink>
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
