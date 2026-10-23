<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  generateCreativeBrief, getCreativeBrief, getTask, listTaskImages, listTasks,
  saveCreativeBrief, updateTaskPlan,
  type CreativeBrief, type CreativeBriefResponse, type Task, type TaskImage,
} from '@/api/tasks'
import { listTaskXiaohongshuNotes, type XiaohongshuNote } from '@/api/sharing'
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
const brief = ref<CreativeBriefResponse | null>(null)
const form = ref<CreativeBrief | null>(null)
const tasks = ref<Task[]>([])
const search = ref('')
const taskPage = ref(0)
const taskTotalPages = ref(0)
const loading = ref(false)
const generating = ref(false)
const saving = ref(false)
const posterBusy = ref(false)
const posterTheme = ref<'sky' | 'rose' | 'night'>('sky')
const error = ref('')
const notice = ref('')
let loadVersion = 0

const dirty = computed(() => {
  if (!brief.value || !form.value) return false
  const { objective, audience, coreMessage, tone, facts, unknowns, directions } = brief.value
  return JSON.stringify(form.value) !== JSON.stringify({ objective, audience, coreMessage, tone, facts, unknowns, directions })
})

function copyBrief(value: CreativeBriefResponse) {
  form.value = {
    objective: value.objective,
    audience: value.audience,
    coreMessage: value.coreMessage,
    tone: value.tone,
    facts: value.facts.map((fact) => ({ ...fact })),
    unknowns: [...value.unknowns],
    directions: value.directions.map((direction) => ({ ...direction })),
  }
}

async function load() {
  const version = ++loadVersion
  error.value = ''
  notice.value = ''
  task.value = null
  brief.value = null
  form.value = null
  images.value = []
  notes.value = []
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
    const [nextTask, nextBrief, nextImages, nextNotes] = await Promise.all([
      getTask(taskId.value), getCreativeBrief(taskId.value), listTaskImages(taskId.value),
      listTaskXiaohongshuNotes(taskId.value),
    ])
    if (version !== loadVersion) return
    task.value = nextTask
    brief.value = nextBrief
    images.value = nextImages
    notes.value = nextNotes
    if (nextBrief) copyBrief(nextBrief)
  } catch (cause) {
    if (version === loadVersion) error.value = cause instanceof Error ? cause.message : '加载工作台失败。'
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

function searchTasks() {
  taskPage.value = 0
  void load()
}

function changePage(next: number) {
  if (next < 0 || next >= taskTotalPages.value) return
  taskPage.value = next
  void load()
}

async function generate() {
  if (taskId.value === null || generating.value) return
  const id = taskId.value
  if (dirty.value && !window.confirm('当前简报有未保存修改，重新提炼会覆盖这些修改。确定继续吗？')) return
  generating.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await generateCreativeBrief(id)
    if (taskId.value !== id) return
    brief.value = result
    copyBrief(result)
    notice.value = '已根据当前需求材料提炼简报。请逐项核对事实与出处，再确认。'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '生成简报失败。'
  } finally {
    generating.value = false
  }
}

async function save(confirmed: boolean) {
  const id = taskId.value
  if (id === null || !form.value || saving.value) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await saveCreativeBrief(id, form.value, confirmed)
    if (taskId.value !== id) return
    brief.value = result
    copyBrief(result)
    if (confirmed && task.value?.workflowStage === 'PLANNING') {
      task.value = await updateTaskPlan(id, 'CREATING', task.value.discussionSummary ?? '')
    }
    notice.value = confirmed ? '简报已确认，可选择方向进入小红书创作。' : '简报草稿已保存。'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存简报失败。'
  } finally {
    saving.value = false
  }
}

function addFact() {
  if (form.value && form.value.facts.length < 20) form.value.facts.push({ text: '', source: '待核实' })
}

function addUnknown() {
  if (form.value && form.value.unknowns.length < 20) form.value.unknowns.push('')
}

function addDirection() {
  if (form.value && form.value.directions.length < 3) {
    form.value.directions.push({ name: '', angle: '', headlineIdea: '' })
  }
}

function wrapText(context: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
  const lines: string[] = []
  let line = ''
  for (const char of Array.from(text)) {
    if (line && context.measureText(line + char).width > maxWidth) {
      lines.push(line)
      line = char
    } else {
      line += char
    }
  }
  if (line) lines.push(line)
  return lines
}

async function makeTextPoster(index: number) {
  const id = taskId.value
  const content = form.value
  if (id === null || !task.value || !content || posterBusy.value || !brief.value?.confirmed || dirty.value) return
  const direction = content.directions[index]
  if (!direction) return
  posterBusy.value = true
  error.value = ''
  try {
    const canvas = document.createElement('canvas')
    canvas.width = 1080
    canvas.height = 1350
    const context = canvas.getContext('2d')
    if (!context) throw new Error('浏览器无法绘制海报。')
    const palettes = {
      sky: { top: '#e4f5ff', bottom: '#fff7fc', ink: '#173f62', accent: '#3b91c7' },
      rose: { top: '#fff0f7', bottom: '#fffaf1', ink: '#63334c', accent: '#c75e93' },
      night: { top: '#142b49', bottom: '#36456c', ink: '#f8fbff', accent: '#a6d9ff' },
    }
    const palette = palettes[posterTheme.value]
    const gradient = context.createLinearGradient(0, 0, 1080, 1350)
    gradient.addColorStop(0, palette.top)
    gradient.addColorStop(1, palette.bottom)
    context.fillStyle = gradient
    context.fillRect(0, 0, 1080, 1350)
    context.fillStyle = palette.accent
    context.fillRect(88, 128, 84, 8)
    context.font = '600 32px "PingFang SC", "Microsoft YaHei", sans-serif'
    context.fillText(task.value.title.slice(0, 30), 88, 222, 900)
    context.font = '700 78px "PingFang SC", "Microsoft YaHei", sans-serif'
    context.fillStyle = palette.ink
    const title = direction.headlineIdea || direction.name
    const titleLines = wrapText(context, title, 900).slice(0, 5)
    titleLines.forEach((line, lineIndex) => context.fillText(line, 88, 388 + lineIndex * 104))
    const dividerY = Math.max(780, 440 + titleLines.length * 104)
    context.fillStyle = palette.accent
    context.fillRect(88, dividerY, 900, 3)
    context.font = '400 38px "PingFang SC", "Microsoft YaHei", sans-serif'
    context.fillStyle = palette.ink
    wrapText(context, content.coreMessage, 900).slice(0, 5)
      .forEach((line, lineIndex) => context.fillText(line, 88, dividerY + 96 + lineIndex * 62))
    const token = crypto.randomUUID()
    sessionStorage.setItem(`staranaly-poster:${token}`, JSON.stringify({
      name: `${task.value.title.slice(0, 24)}-方向${index + 1}.jpg`,
      dataUrl: canvas.toDataURL('image/jpeg', 0.88),
    }))
    await router.push({ name: 'share-xiaohongshu', query: { taskId: id, direction: index, poster: token } })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '生成文字海报失败。'
  } finally {
    posterBusy.value = false
  }
}

watch(() => route.params.id, () => { void load() }, { immediate: true })
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="eyebrow">创作工作台</p>
      <h1>{{ task ? task.title : '选择一份需求开始创作' }}</h1>
      <p class="page-description">先提炼材料中的事实与方向，确认后再制作小红书宣传内容。</p>
    </div>
    <RouterLink v-if="task" class="button button--secondary" :to="`/tasks/${task.id}`">返回需求单</RouterLink>
    <RouterLink v-else class="button button--secondary" to="/tasks/new">新建需求</RouterLink>
  </section>

  <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
  <p v-if="notice" class="inline-success" role="status">{{ notice }}</p>
  <section v-if="taskId === null" class="panel workspace-section" aria-label="选择需求">
    <div class="section-heading"><h2>需求单</h2><span class="muted">选择后提炼创作简报</span></div>
    <form class="workbench-pick__search" @submit.prevent="searchTasks">
      <label for="workbench-search">查找需求</label>
      <input id="workbench-search" v-model="search" type="search" maxlength="200" placeholder="输入需求名称" />
      <button class="button button--secondary" type="submit">搜索</button>
    </form>
    <p v-if="loading" role="status">正在加载需求…</p>
    <p v-else-if="tasks.length === 0" class="empty-note">还没有匹配的需求。先创建一份需求单。</p>
    <ul v-else class="workbench-pick__list">
      <li v-for="item in tasks" :key="item.id">
        <RouterLink :to="{ name: 'creative-workbench-task', params: { id: item.id } }">
          <strong>{{ item.title }}</strong>
          <span>{{ formatCreatedAt(item.createdAt) }}</span>
        </RouterLink>
      </li>
    </ul>
    <div v-if="taskTotalPages > 1" class="pagination">
      <button class="button button--secondary" type="button" :disabled="taskPage === 0 || loading" @click="changePage(taskPage - 1)">上一页</button>
      <span>第 {{ taskPage + 1 }} / {{ taskTotalPages }} 页</span>
      <button class="button button--secondary" type="button" :disabled="taskPage + 1 >= taskTotalPages || loading" @click="changePage(taskPage + 1)">下一页</button>
    </div>
  </section>
  <div v-else-if="loading" class="panel message" role="status">正在加载需求…</div>
  <template v-else-if="task">
    <section class="panel workspace-section workbench-source" aria-label="原始需求">
      <div class="section-heading"><h2>需求材料</h2><RouterLink class="text-link" :to="`/tasks/${task.id}`">修改文字或上传材料</RouterLink></div>
      <p class="workspace-description">{{ task.description || '尚未填写文字需求。' }}</p>
      <p class="muted">已保存 {{ images.length }} 张图片。PDF、Word 和文字资料也会参与简报提炼。</p>
      <div v-if="images.length" class="workbench-source__images">
        <img v-for="item in images" :key="item.id" :src="`/api/tasks/${task.id}/images/${item.id}`" :alt="item.name" />
      </div>
      <button class="button" type="button" :disabled="generating || saving" @click="generate">
        {{ generating ? '正在识别材料并提炼…' : brief ? '重新提炼简报' : '提炼创作简报' }}
      </button>
    </section>

    <section v-if="brief && form" class="panel workspace-section workbench-brief" aria-label="创作简报">
      <div class="section-heading">
        <div><h2>创作简报</h2><p class="muted">AI 提炼结果需要你对照原始材料确认。</p></div>
        <span class="status-badge">{{ dirty ? '有未保存修改' : brief.sourcesChanged ? '材料已变化' : brief.confirmed ? '已确认' : '待确认' }}</span>
      </div>
      <p v-if="brief.sourcesChanged" class="inline-error" role="status">需求材料或讨论结论已改变。请重新提炼，或核对当前简报后重新确认。</p>
      <div class="workbench-brief__grid">
        <div class="field"><label for="brief-objective">宣传目标</label><textarea id="brief-objective" v-model="form.objective" rows="3" maxlength="500" /></div>
        <div class="field"><label for="brief-audience">目标受众</label><textarea id="brief-audience" v-model="form.audience" rows="3" maxlength="500" /></div>
        <div class="field"><label for="brief-message">核心信息</label><textarea id="brief-message" v-model="form.coreMessage" rows="3" maxlength="500" /></div>
        <div class="field"><label for="brief-tone">表达语气</label><textarea id="brief-tone" v-model="form.tone" rows="3" maxlength="300" /></div>
      </div>
      <div class="workbench-brief__section">
        <div class="section-heading"><h3>可确认的事实与出处</h3><button class="text-button" type="button" @click="addFact">添加事实</button></div>
        <p class="field-hint">出处为“待核实”的内容，请检查后修改或删除。</p>
        <div v-for="(fact, index) in form.facts" :key="index" class="workbench-brief__row">
          <input v-model="fact.text" type="text" maxlength="500" :aria-label="`事实 ${index + 1}`" />
          <input v-model="fact.source" type="text" maxlength="255" :aria-label="`事实 ${index + 1} 的出处`" />
          <button class="text-button text-button--danger" type="button" :aria-label="`移除事实 ${index + 1}`" @click="form.facts.splice(index, 1)">移除</button>
        </div>
      </div>
      <div class="workbench-brief__section">
        <div class="section-heading"><h3>尚待确认</h3><button class="text-button" type="button" @click="addUnknown">添加问题</button></div>
        <p v-if="form.unknowns.length === 0" class="empty-note">没有待确认信息。</p>
        <div v-for="(unknown, index) in form.unknowns" :key="index" class="workbench-brief__row workbench-brief__row--unknown">
          <input v-model="form.unknowns[index]" type="text" maxlength="500" :aria-label="`待确认问题 ${index + 1}`" />
          <button class="text-button text-button--danger" type="button" @click="form.unknowns.splice(index, 1)">移除</button>
        </div>
      </div>
      <div class="workbench-brief__section">
        <div class="section-heading"><h3>宣传方向</h3><button v-if="form.directions.length < 3" class="text-button" type="button" @click="addDirection">添加方向</button></div>
        <div class="workbench-poster-choice">
          <label for="poster-theme">文字海报配色</label>
          <select id="poster-theme" v-model="posterTheme">
            <option value="sky">晴空</option><option value="rose">柔粉</option><option value="night">深蓝</option>
          </select>
          <span>没有可发布图片时，可用确认后的文字生成一张海报。</span>
        </div>
        <div class="workbench-directions">
          <article v-for="(direction, index) in form.directions" :key="index" class="workbench-direction">
            <div class="section-heading"><strong>方向 {{ index + 1 }}</strong><button v-if="form.directions.length > 1" class="text-button text-button--danger" type="button" @click="form.directions.splice(index, 1)">移除</button></div>
            <label :for="`direction-name-${index}`">名称</label>
            <input :id="`direction-name-${index}`" v-model="direction.name" maxlength="80" />
            <label :for="`direction-angle-${index}`">切入点</label>
            <textarea :id="`direction-angle-${index}`" v-model="direction.angle" rows="4" maxlength="500" />
            <label :for="`direction-headline-${index}`">标题想法</label>
            <input :id="`direction-headline-${index}`" v-model="direction.headlineIdea" maxlength="120" />
            <RouterLink v-if="brief.confirmed && !brief.sourcesChanged && !dirty" class="button button--secondary"
              :to="{ name: 'share-xiaohongshu', query: { taskId: task.id, direction: index } }">用此方向创作小红书</RouterLink>
            <button v-if="brief.confirmed && !brief.sourcesChanged && !dirty" class="button button--quiet" type="button"
              :disabled="posterBusy" @click="makeTextPoster(index)">{{ posterBusy ? '正在制作…' : '制作文字海报并创作' }}</button>
          </article>
        </div>
      </div>
      <div class="workbench-brief__actions">
        <button class="button button--secondary" type="button" :disabled="saving" @click="save(false)">保存草稿</button>
        <button class="button" type="button" :disabled="saving" @click="save(true)">{{ saving ? '正在保存…' : '确认简报' }}</button>
      </div>
    </section>
    <section v-if="notes.length" class="panel workspace-section" aria-label="关联作品">
      <div class="section-heading"><h2>这份需求的作品</h2><span class="muted">{{ notes.length }} 篇</span></div>
      <ul class="workbench-pick__list">
        <li v-for="note in notes" :key="note.id"><RouterLink :to="{ name: 'share-xiaohongshu', query: { noteId: note.id } }">
          <strong>{{ note.title }}</strong><span>{{ note.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span>
        </RouterLink></li>
      </ul>
    </section>
  </template>
</template>
