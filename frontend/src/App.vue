<template>
  <div class="app-shell">
    <aside class="site-sidebar">
      <RouterLink class="brand" to="/tasks" aria-label="星析任务工作台首页">
        <span class="brand__mark">✦</span>
        <span>星析 <small>StarAnaly</small></span>
      </RouterLink>
      <nav class="site-nav" aria-label="主导航">
        <RouterLink class="site-nav__link" to="/tasks">任务列表</RouterLink>
        <RouterLink class="site-nav__link" to="/tasks/new">创建任务</RouterLink>
      </nav>
    </aside>

    <main class="main-content">
      <RouterView />
    </main>
  </div>
</template>
