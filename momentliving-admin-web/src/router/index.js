import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由：/login 独立页，其余挂在 AdminLayout（深绿侧边栏布局）下。
 * 全局守卫：无 token 一律回 /login。
 */
const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { title: '登录' } },
  {
    path: '/',
    component: () => import('../layout/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '数据概览' } },
      { path: 'shops', name: 'shops', component: () => import('../views/Shops.vue'), meta: { title: '店铺管理' } },
      { path: 'shop-types', name: 'shopTypes', component: () => import('../views/ShopTypes.vue'), meta: { title: '分类管理' } },
      { path: 'vouchers', name: 'vouchers', component: () => import('../views/Vouchers.vue'), meta: { title: '优惠券管理' } },
      { path: 'applies', name: 'applies', component: () => import('../views/Applies.vue'), meta: { title: '入驻审核' } },
      { path: 'verify-records', name: 'verifyRecords', component: () => import('../views/VerifyRecords.vue'), meta: { title: '核销记录' } },
      { path: 'ai-knowledge', name: 'aiKnowledge', component: () => import('../views/AiKnowledge.vue'), meta: { title: 'AI 知识库' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  document.title = (to.meta.title ? to.meta.title + ' · ' : '') + '一刻生活管理后台'
  const token = localStorage.getItem('adminToken')
  if (to.path !== '/login' && !token) {
    return '/login'
  }
  if (to.path === '/login' && token) {
    return '/dashboard'
  }
  return true
})

export default router
