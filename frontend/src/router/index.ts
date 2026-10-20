import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/tasks' },
    {
      path: '/tasks',
      name: 'task-list',
      component: () => import('@/views/TaskListView.vue'),
    },
    {
      path: '/tasks/new',
      name: 'task-create',
      component: () => import('@/views/TaskCreateView.vue'),
    },
    {
      path: '/tasks/:id',
      name: 'task-detail',
      component: () => import('@/views/TaskDetailView.vue'),
    },
    { path: '/:pathMatch(.*)*', redirect: '/tasks' },
  ],
})

export default router
