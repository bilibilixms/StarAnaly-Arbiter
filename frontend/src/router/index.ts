import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/tasks' },
    {
      path: '/tasks',
      name: 'task-list',
      meta: { space: '工作空间' },
      component: () => import('@/views/TaskListView.vue'),
    },
    {
      path: '/tasks/new',
      name: 'task-create',
      meta: { space: '工作空间' },
      component: () => import('@/views/TaskCreateView.vue'),
    },
    {
      path: '/tasks/:id',
      name: 'task-detail',
      meta: { space: '工作空间' },
      component: () => import('@/views/TaskDetailView.vue'),
    },
    {
      path: '/share/douyin',
      name: 'share-douyin',
      meta: { space: '分享空间' },
      component: () => import('@/views/SharePlatformView.vue'),
      props: { platform: 'douyin' },
    },
    {
      path: '/share/xiaohongshu',
      name: 'share-xiaohongshu',
      meta: { space: '分享空间' },
      component: () => import('@/views/XiaohongshuView.vue'),
    },
    { path: '/:pathMatch(.*)*', redirect: '/tasks' },
  ],
})

export default router
