<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const spaceLabel = computed(() => typeof route.meta.space === 'string' ? route.meta.space : '工作空间')
</script>

<template>
  <div class="app-shell">
    <aside class="site-rail" aria-label="空间快捷入口">
      <RouterLink class="site-rail__logo" to="/tasks" aria-label="星析首页">✦</RouterLink>
      <RouterLink class="site-rail__workspace" :class="{ 'site-rail__workspace--active': spaceLabel === '工作空间' }" to="/tasks" aria-label="工作空间">星</RouterLink>
      <RouterLink class="site-rail__share" :class="{ 'site-rail__share--active': spaceLabel === '分享空间' }" to="/share/douyin" aria-label="分享空间">享</RouterLink>
      <RouterLink class="site-rail__add" to="/tasks/new" aria-label="创建任务">+</RouterLink>
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
        <RouterLink class="site-nav__link" to="/share/xiaohongshu">
          <svg class="site-nav__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <rect x="4" y="3" width="16" height="18" rx="3" />
            <path d="M8 8h8M8 12h8M8 16h5" />
          </svg>
          <span>小红书</span>
        </RouterLink>
      </nav>
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
