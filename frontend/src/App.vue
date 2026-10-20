<template>
  <div class="app-shell">
    <header class="site-header">
      <div class="site-header__inner">
        <RouterLink class="brand" to="/tasks" aria-label="星析任务工作台首页">
          <span class="brand__mark">✦</span>
          <span>星析 <small>StarAnaly</small></span>
        </RouterLink>
        <nav class="site-nav" aria-label="主导航">
          <RouterLink to="/tasks">任务列表</RouterLink>
          <RouterLink class="button button--small" to="/tasks/new">创建任务</RouterLink>
        </nav>
      </div>
    </header>

    <main class="main-content">
      <RouterView />
    </main>
  </div>
</template>
