<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { createTask } from '@/api/tasks'

const router = useRouter()
const title = ref('')
const description = ref('')
const submitting = ref(false)
const error = ref('')

async function submit() {
  if (submitting.value) return

  error.value = ''
  const normalizedTitle = title.value.trim()
  if (!normalizedTitle) {
    error.value = '请输入需求名称。'
    return
  }

  submitting.value = true
  try {
    const task = await createTask({
      title: normalizedTitle,
      description: description.value.trim() || null,
    })
    await router.push({ name: 'task-detail', params: { id: task.id } })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '创建任务失败。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="page-heading">
    <div>
      <p class="eyebrow">工作空间 / 需求单</p>
      <h1>新建需求</h1>
      <p class="page-description">先记录收到的文字要求，创建后可继续上传图片、PDF 和 Word。</p>
    </div>
    <RouterLink class="text-link" to="/tasks">返回需求列表</RouterLink>
  </section>

  <section class="panel panel--form">
    <form @submit.prevent="submit">
      <div class="field">
        <label for="task-title">需求名称 <span class="required">*</span></label>
        <input id="task-title" v-model="title" name="title" type="text" maxlength="200" required autofocus placeholder="例如：新产品首发宣传" />
        <p class="field-hint">最多 200 个字符</p>
      </div>

      <div class="field">
        <label for="task-description">原始文字要求</label>
        <textarea id="task-description" v-model="description" name="description" rows="7" maxlength="5000" placeholder="直接粘贴客户发来的文字要求"></textarea>
        <p class="field-hint">选填，最多 5000 个字符</p>
      </div>

      <p v-if="error" class="form-error" role="alert">{{ error }}</p>

      <div class="form-actions">
        <button class="button" type="submit" :disabled="submitting">
          {{ submitting ? '正在创建…' : '创建需求' }}
        </button>
        <RouterLink class="button button--secondary" to="/tasks">取消</RouterLink>
      </div>
    </form>
  </section>
</template>
