<template>
  <view class="m-login">
    <!-- 顶部深抹茶绿 hero（与用户端同品牌、语义区分：商家端=上班） -->
    <view class="m-login-hero">
      <view class="m-login-hero__logo">
        <text class="m-login-hero__logo-text">一刻生活 · 商家</text>
      </view>
      <text class="m-login-hero__slogan">到店核销工作台</text>
    </view>

    <view class="m-login-form">
      <view class="m-login-form__item">
        <input
          class="m-login-form__input"
          v-model="username"
          placeholder="请输入商家账号"
          placeholder-class="m-login-form__placeholder"
        />
      </view>
      <view class="m-login-form__item">
        <input
          class="m-login-form__input"
          v-model="password"
          :password="!showPwd"
          placeholder="请输入密码"
          placeholder-class="m-login-form__placeholder"
        />
        <text class="m-login-form__eye" @click="showPwd = !showPwd">{{ showPwd ? '🙈' : '👁' }}</text>
      </view>

      <button class="brand-btn m-login-form__submit" :class="{ 'is-disabled': !canSubmit }" @click="handleLogin">
        登 录
      </button>
      <text class="m-login-form__tip">仅限已入驻商家使用 · 账号由平台开通</text>
      <!-- 入驻申请：未入驻商家提交资料，平台管理员审核通过后自动开通账号 -->
      <view class="m-login-form__apply" @click="goApply">
        <text class="m-login-form__apply-text">还没有账号？申请入驻 ›</text>
      </view>
    </view>
  </view>
</template>

<script>
import { merchantLogin } from '@/api/merchant.js'

/**
 * 商家登录：账号密码（POST /admin/login，返回 AdminVO 含 token + shopId）
 */
export default {
  data() {
    return {
      username: '',
      password: '',
      showPwd: false
    }
  },
  computed: {
    canSubmit() {
      return !!this.username && !!this.password
    }
  },
  methods: {
    goApply() {
      uni.navigateTo({ url: '/pages/merchant/apply/apply' })
    },
    async handleLogin() {
      if (!this.canSubmit) return
      try {
        const res = await merchantLogin({ username: this.username, password: this.password })
        if (!res || !res.token) {
          uni.showToast({ title: '登录成功但未返回令牌，请联系平台', icon: 'none' })
          return
        }
        uni.showToast({ title: `欢迎，${res.name || res.username}`, icon: 'none' })
        setTimeout(() => {
          uni.reLaunch({ url: '/pages/merchant/workbench/workbench' })
        }, 600)
      } catch (e) {
        // toast 已统一处理
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.m-login {
  min-height: 100vh;
  background: $brand-bg;
}

.m-login-hero {
  height: 33vh;
  background: $brand-dark;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;

  &__logo {
    padding: 24rpx 40rpx;
    border-radius: 36rpx;
    background: rgba(255, 255, 255, 0.14);
    border: 2px solid rgba(255, 255, 255, 0.7);
  }

  &__logo-text {
    color: #ffffff;
    font-size: 22px;
    font-weight: 700;
    letter-spacing: 2px;
  }

  &__slogan {
    margin-top: 24rpx;
    color: rgba(255, 255, 255, 0.9);
    font-size: 14px;
    letter-spacing: 4px;
  }
}

.m-login-form {
  padding: 60rpx 48rpx;

  &__item {
    display: flex;
    align-items: center;
    background: #ffffff;
    border: 1px solid $brand-line;
    border-radius: $radius-btn;
    height: 96rpx;
    padding: 0 32rpx;
    margin-bottom: 32rpx;
  }

  &__input {
    flex: 1;
    font-size: 15px;
  }

  &__eye {
    font-size: 18px;
    padding: 0 8rpx;
  }

  &__submit {
    margin-top: 16rpx;
  }

  &__tip {
    display: block;
    text-align: center;
    margin-top: 32rpx;
    color: $text-sub;
    font-size: 12px;
  }

  &__apply {
    display: flex;
    justify-content: center;
    margin-top: 20rpx;
  }

  &__apply-text {
    color: $brand-primary;
    font-size: 13px;
    text-decoration: underline;
  }
}

.m-login-form__placeholder {
  color: $text-sub;
}
</style>
