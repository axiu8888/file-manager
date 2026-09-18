import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/signup', name: 'signup', component: () => import('@/views/SignupView.vue') },
    { path: '/video/:fileId', name: 'video', component: () => import('@/views/VideoView.vue') },
    { path: '/epub/:fileId', name: 'epub', component: () => import('@/views/EpubView.vue') },
    { path: '/pdf/:fileId', name: 'pdf', component: () => import('@/views/PdfView.vue') },
  ],
})

export default router
