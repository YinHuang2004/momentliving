<template>
  <div class="layout">
    <!-- 侧边栏 220px 深抹茶绿 -->
    <aside class="layout__aside">
      <div class="layout__logo">一刻生活 · 管理后台</div>
      <el-menu
        :default-active="activeMenu"
        router
        class="layout__menu"
        background-color="#4A6B4D"
        text-color="#CFDCD0"
        active-text-color="#FFFFFF"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon><span>数据概览</span>
        </el-menu-item>
        <el-menu-item index="/shops">
          <el-icon><Shop /></el-icon><span>店铺管理</span>
        </el-menu-item>
        <el-menu-item index="/shop-types">
          <el-icon><Menu /></el-icon><span>分类管理</span>
        </el-menu-item>
        <el-menu-item index="/vouchers">
          <el-icon><Ticket /></el-icon><span>优惠券管理</span>
        </el-menu-item>
        <el-menu-item index="/applies">
          <el-icon><Stamp /></el-icon><span>入驻审核</span>
          <el-badge v-if="pendingCount > 0" :value="pendingCount" class="layout__badge" />
        </el-menu-item>
        <el-menu-item index="/verify-records">
          <el-icon><List /></el-icon><span>核销记录</span>
        </el-menu-item>
        <el-menu-item index="/ai-knowledge">
          <el-icon><MagicStick /></el-icon><span>AI 知识库</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <div class="layout__main">
      <!-- 顶部 56px 白 nav -->
      <header class="layout__topbar">
        <div class="layout__topbar-left">
          <!-- 返回上一页（概览页为起点不显示；无历史时回数据概览） -->
          <el-button
            v-if="route.path !== '/dashboard'"
            class="layout__back"
            circle
            :icon="ArrowLeft"
            title="返回"
            @click="goBack"
          />
          <div class="layout__crumb">{{ route.meta.title || '' }}</div>
        </div>
        <el-dropdown @command="onCommand">
          <span class="layout__user">
            <el-avatar :size="30" style="background: var(--brand-primary)">
              {{ (auth.admin && auth.admin.name || 'A').slice(0, 1) }}
            </el-avatar>
            <span class="layout__user-name">{{ auth.admin && auth.admin.name || '管理员' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <!-- 内容区米白 -->
      <main class="layout__content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import { listApplies } from '../api/admin'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activeMenu = computed(() => route.path)
const pendingCount = ref(0)

/** 返回上一页；无历史（刷新后直达/新开标签）时回数据概览 */
function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/dashboard')
  }
}

onMounted(async () => {
  // token 在但内存里没有 admin（刷新场景）：回查 /admin/me 补全
  if (!auth.admin) {
    auth.fetchMe().catch(() => {})
  }
  // 侧边栏待审核角标
  try {
    const list = await listApplies({ status: 0, current: 1, pageSize: 1 })
    // 后端返回数组（分页 records），pending 数以审核页为准，这里仅做角标粗略展示
    pendingCount.value = Array.isArray(list) ? (list.length >= 10 ? '10+' : list.length) : 0
    if (typeof pendingCount.value === 'number' && pendingCount.value >= 10) pendingCount.value = 10
  } catch (e) { /* 忽略角标失败 */ }
})

async function onCommand(cmd) {
  if (cmd === 'logout') {
    await auth.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  display: flex;
  height: 100%;
}
.layout__aside {
  width: 220px;
  flex-shrink: 0;
  background: var(--brand-dark);
  display: flex;
  flex-direction: column;
}
.layout__logo {
  height: 56px;
  line-height: 56px;
  text-align: center;
  color: #ffffff;
  font-weight: 700;
  font-size: 16px;
  letter-spacing: 1px;
}
.layout__menu {
  border-right: none;
  flex: 1;
}
.layout__menu :deep(.el-menu-item) {
  height: 48px;
}
.layout__menu :deep(.el-menu-item.is-active) {
  background: var(--brand-primary) !important;
  border-radius: 12px;
  margin: 4px 10px;
  width: auto;
}
.layout__badge {
  margin-left: 8px;
}
.layout__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--brand-bg);
}
.layout__topbar {
  height: 56px;
  flex-shrink: 0;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 2px 8px rgba(74, 107, 77, 0.06);
}
.layout__topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.layout__back {
  border-color: var(--brand-line);
  color: var(--brand-primary);
}
.layout__crumb {
  font-size: 15px;
  font-weight: 600;
}
.layout__user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.layout__user-name {
  font-size: 14px;
}
.layout__content {
  flex: 1;
  overflow: auto;
}
</style>
