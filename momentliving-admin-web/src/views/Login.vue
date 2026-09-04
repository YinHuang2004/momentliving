<template>
  <div class="login">
    <div class="login__card brand-card">
      <div class="login__logo">一刻生活 · 管理后台</div>
      <div class="login__sub">平台管理员入口（商家请走商家端 App）</div>
      <el-form :model="form" size="large" @keyup.enter="handleLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" autocomplete="username" />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button class="login__btn" type="primary" size="large" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../store/auth'

const router = useRouter()
const auth = useAuthStore()
const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    // 错误 toast 已在 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--brand-dark) 0%, var(--brand-primary) 100%);
}
.login__card {
  width: 380px;
  padding: 40px 36px;
}
.login__logo {
  text-align: center;
  font-size: 20px;
  font-weight: 700;
}
.login__sub {
  text-align: center;
  color: var(--text-sub);
  font-size: 12px;
  margin: 8px 0 28px;
}
.login__btn {
  width: 100%;
  margin-top: 4px;
  background: var(--brand-primary);
  border-color: var(--brand-primary);
}
</style>
