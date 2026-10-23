import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/tasks' },
    {
      path: '/profile',
      name: 'personal-space',
      meta: { space: '个人空间' },
      component: () => import('@/views/PersonalSpaceView.vue'),
    },
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
    {
      path: '/share/xiaohongshu/records',
      name: 'share-xiaohongshu-records',
      meta: { space: '分享空间' },
      component: () => import('@/views/XiaohongshuView.vue'),
    },
    {
      path: '/share/xiaohongshu/materials',
      name: 'share-xiaohongshu-materials',
      meta: { space: '分享空间' },
      component: () => import('@/views/XiaohongshuView.vue'),
    },
    {
      path: '/workbench',
      name: 'creative-workbench',
      meta: { space: '工作台' },
      component: () => import('@/views/CreativeWorkbenchView.vue'),
    },
    {
      path: '/workbench/:id',
      name: 'creative-workbench-task',
      meta: { space: '工作台' },
      component: () => import('@/views/CreativeWorkbenchView.vue'),
    },
    { path: '/game', redirect: '/workbench' },
    { path: '/:pathMatch(.*)*', redirect: '/tasks' },
  ],
})

export default router
