<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const spaceLabel = computed(() => typeof route.meta.space === 'string' ? route.meta.space : '工作空间')
</script>

<template>
  <div class="app-shell">
    <aside class="site-rail" aria-label="当前用户：个人用户">
      <span class="site-rail__avatar" aria-hidden="true">我</span>
      <span class="site-rail__name">个人用户</span>
      <span class="site-rail__hint">个人使用</span>
    </aside>

    <aside class="site-sidebar">
      <RouterLink class="brand" to="/tasks" aria-label="星析首页">
        <span>星析 <small>StarAnaly</small></span>
      </RouterLink>
      <p class="site-nav__heading">工作空间</p>
      <nav class="site-nav" aria-label="工作空间导航">
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
      <p class="site-nav__heading site-nav__heading--secondary">分享空间</p>
      <nav class="site-nav" aria-label="分享空间导航">
        <RouterLink class="site-nav__link" to="/share/douyin">
          <svg class="site-nav__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <rect x="3" y="3" width="18" height="18" rx="5" />
            <path d="m10 8 6 4-6 4V8Z" />
          </svg>
          <span>抖音</span>
        </RouterLink>
      </nav>
      <div class="site-nav__group" aria-label="小红书">
        <p class="site-nav__group-title">
          <svg class="site-nav__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <rect x="4" y="3" width="16" height="18" rx="3" />
            <path d="M8 8h8M8 12h8M8 16h5" />
          </svg>
          <span>小红书</span>
        </p>
        <nav class="site-nav site-nav--nested" aria-label="小红书导航">
          <RouterLink class="site-nav__link" to="/share/xiaohongshu" exact-active-class="router-link-active">图文创作</RouterLink>
          <RouterLink class="site-nav__link" to="/share/xiaohongshu/records">创作记录</RouterLink>
        </nav>
      </div>
    </aside>

    <div class="workspace-area">
      <header class="workspace-toolbar">
        <div class="workspace-toolbar__inner">
          <span class="workspace-toolbar__mark" aria-hidden="true">✦</span>
          <strong>星析工作台</strong>
          <span class="workspace-toolbar__section">{{ spaceLabel }}</span>
        </div>
      </header>
      <main class="main-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>
