<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const sectionLabel = computed(() => {
  if (route.name === 'task-create') return '创建任务'
  if (route.name === 'task-detail') return '任务详情'
  return '任务列表'
})
</script>

<template>
  <div class="app-shell">
    <aside class="site-rail" aria-label="工作台快捷入口">
      <RouterLink class="site-rail__logo" to="/tasks" aria-label="星析首页">✦</RouterLink>
      <RouterLink class="site-rail__workspace" to="/tasks" aria-label="任务工作台">星</RouterLink>
      <RouterLink class="site-rail__add" to="/tasks/new" aria-label="创建任务">+</RouterLink>
    </aside>

    <aside class="site-sidebar">
      <RouterLink class="brand" to="/tasks" aria-label="星析任务工作台首页">
        <span>星析 <small>StarAnaly</small></span>
      </RouterLink>
      <p class="site-nav__heading">工作空间</p>
      <nav class="site-nav" aria-label="主导航">
        <RouterLink class="site-nav__link" to="/tasks">
          <svg class="site-nav__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <rect x="4" y="4" width="16" height="16" rx="2" />
            <path d="M8 9h8M8 13h8M8 17h5" />
          </svg>
          <span>任务列表</span>
        </RouterLink>
        <RouterLink class="site-nav__link" to="/tasks/new">
          <svg class="site-nav__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true">
            <path d="M12 5v14M5 12h14" />
          </svg>
          <span>创建任务</span>
        </RouterLink>
      </nav>
    </aside>

    <div class="workspace-area">
      <header class="workspace-toolbar">
        <div class="workspace-toolbar__inner">
          <span class="workspace-toolbar__mark" aria-hidden="true">✦</span>
          <strong>任务工作台</strong>
          <span class="workspace-toolbar__section">{{ sectionLabel }}</span>
        </div>
      </header>
      <main class="main-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>
