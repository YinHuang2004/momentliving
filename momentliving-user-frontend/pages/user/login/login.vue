<template>
  <view class="login-page">
    <!-- 顶部抹茶绿 hero 区（约占屏 1/3） -->
    <view class="login-hero">
      <view class="login-hero__logo">
        <text class="login-hero__logo-text">一刻生活</text>
      </view>
      <text class="login-hero__slogan">发现身边的美好</text>
    </view>

    <!-- 表单区 -->
    <view class="login-form">
      <!-- 登录方式切换 -->
      <view class="login-tabs">
        <view
          class="login-tabs__item"
          :class="{ 'is-active': loginType === 'phone' }"
          @click="switchType('phone')"
        >
          <text>手机号登录</text>
        </view>
        <view
          class="login-tabs__item"
          :class="{ 'is-active': loginType === 'email' }"
          @click="switchType('email')"
        >
          <text>邮箱登录</text>
        </view>
      </view>

      <!-- 手机号 / 邮箱 -->
      <view class="login-form__item">
        <input
          v-if="loginType === 'phone'"
          class="login-form__input"
          v-model="phone"
          type="number"
          maxlength="11"
          placeholder="请输入手机号"
          placeholder-class="login-form__placeholder"
        />
        <input
          v-else
          class="login-form__input"
          v-model="email"
          type="text"
          placeholder="请输入邮箱"
          placeholder-class="login-form__placeholder"
        />
      </view>

      <!-- 验证码 -->
      <view class="login-form__item login-form__item--code">
        <input
          class="login-form__input"
          v-model="code"
          type="number"
          maxlength="6"
          placeholder="请输入验证码"
          placeholder-class="login-form__placeholder"
        />
        <button
          class="login-form__code-btn"
          :class="{ 'is-disabled': countdown > 0 || !accountReady }"
          :disabled="countdown > 0"
          @click="handleSendCode"
        >
          {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
        </button>
      </view>

      <!-- 演示验证码回显（手机号登录：未接短信，后端直接返回验证码） -->
      <view class="login-demo" v-if="loginType === 'phone' && demoCode">
        <text class="login-demo__label">演示验证码</text>
        <text class="login-demo__code">{{ demoCode }}</text>
        <text class="login-demo__expire" :class="{ 'is-expired': demoExpireLeft <= 0 }">
          {{ demoExpireLeft > 0 ? `${formatExpire(demoExpireLeft)} 后过期` : '已过期，请重新获取' }}
        </text>
      </view>

      <button class="brand-btn login-form__submit" :class="{ 'is-disabled': !canSubmit }" @click="handleLogin">
        登 录
      </button>
      <text class="login-form__tip">登录即同意《用户协议》与《隐私政策》</text>

      <!-- 身份选择：商家从这进入商家端（登录页分流，避免商家误入用户端体系） -->
      <view class="login-merchant-entry" @click="goMerchant">
        <text class="login-merchant-entry__text">我是商家 · 进入商家工作台 ›</text>
      </view>
    </view>
  </view>
</template>

<script>
import { sendCode, loginByCode } from '@/api/user.js'

/**
 * 用户端登录页：手机号 / 邮箱 + 验证码
 * - 手机号：演示模式，验证码由后端直接返回并在页面回显，带过期倒计时（Redis TTL 2 分钟）
 * - 邮箱：验证码走 SMTP 发送，去邮箱查收
 */
export default {
  data() {
    return {
      loginType: 'phone', // phone | email
      phone: '',
      email: '',
      code: '',
      countdown: 0, // 60s 重发倒计时
      timer: null,
      demoCode: null, // 演示验证码（手机号登录）
      demoExpireLeft: 0, // 验证码剩余有效秒数
      expireTimer: null
    }
  },
  computed: {
    accountReady() {
      return this.loginType === 'phone'
        ? /^1[3-9]\d{9}$/.test(this.phone)
        : !!this.email
    },
    canSubmit() {
      return this.accountReady && !!this.code
    }
  },
  onUnload() {
    this.clearTimers()
  },
  methods: {
    clearTimers() {
      if (this.timer) clearInterval(this.timer)
      if (this.expireTimer) clearInterval(this.expireTimer)
    },
    switchType(type) {
      if (type === this.loginType) return
      this.loginType = type
      this.code = ''
      this.demoCode = null
      this.demoExpireLeft = 0
      if (this.expireTimer) clearInterval(this.expireTimer)
    },
    goMerchant() {
      // 身份分流：商家工作台独立登录（账号密码），与用户验证码登录互不影响
      uni.navigateTo({ url: '/pages/merchant/login/login' })
    },
    async handleSendCode() {
      if (this.countdown > 0) return
      if (!this.accountReady) {
        uni.showToast({ title: this.loginType === 'phone' ? '请输入正确的手机号' : '请先输入邮箱', icon: 'none' })
        return
      }
      try {
        const params = this.loginType === 'phone' ? { phone: this.phone } : { email: this.email }
        const res = await sendCode(params)
        if (this.loginType === 'phone' && res && res.code) {
          // 演示模式：验证码直接回显 + 过期倒计时，并自动填入输入框
          this.demoCode = res.code
          this.demoExpireLeft = Number(res.expireSeconds) || 120
          this.code = res.code
          this.startExpireCountdown()
        } else {
          uni.showToast({ title: '验证码已发送，请查收邮箱', icon: 'none' })
        }
        // 60s 重发倒计时防刷
        this.countdown = 60
        if (this.timer) clearInterval(this.timer)
        this.timer = setInterval(() => {
          this.countdown--
          if (this.countdown <= 0) clearInterval(this.timer)
        }, 1000)
      } catch (e) {
        // toast 已由 request.js 统一处理
      }
    },
    startExpireCountdown() {
      if (this.expireTimer) clearInterval(this.expireTimer)
      this.expireTimer = setInterval(() => {
        this.demoExpireLeft--
        if (this.demoExpireLeft <= 0) {
          clearInterval(this.expireTimer)
          // 验证码已过期：若用户未手动改动，自动清空输入框
          if (this.code === this.demoCode) this.code = ''
        }
      }, 1000)
    },
    formatExpire(seconds) {
      const m = String(Math.floor(seconds / 60)).padStart(2, '0')
      const s = String(seconds % 60).padStart(2, '0')
      return `${m}:${s}`
    },
    async handleLogin() {
      if (!this.canSubmit) return
      try {
        const params = this.loginType === 'phone'
          ? { phone: this.phone, code: this.code }
          : { email: this.email, code: this.code }
        const res = await loginByCode(params)
        uni.showToast({ title: `欢迎回来，${(res.userInfo && res.userInfo.nickName) || '用户'}`, icon: 'none' })
        setTimeout(() => {
          uni.switchTab({ url: '/pages/user/home/home' })
        }, 600)
      } catch (e) {
        // toast 已由 request.js 统一处理
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  background: $brand-bg;
}

.login-hero {
  height: 33vh;
  background: $brand-primary;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;

  &__logo {
    width: 160rpx;
    height: 160rpx;
    border-radius: 36rpx;
    background: rgba(255, 255, 255, 0.16);
    border: 2px solid rgba(255, 255, 255, 0.7);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__logo-text {
    color: #ffffff;
    font-size: 22px;
    font-weight: 700;
    letter-spacing: 2px;
  }

  &__slogan {
    margin-top: 24rpx;
    color: rgba(255, 255, 255, 0.92);
    font-size: 15px;
    letter-spacing: 4px;
  }
}

.login-form {
  padding: 48rpx 48rpx 0;
}

.login-tabs {
  display: flex;
  gap: 20rpx;
  margin-bottom: 40rpx;

  &__item {
    flex: 1;
    height: 72rpx;
    line-height: 68rpx;
    text-align: center;
    border-radius: $radius-btn;
    border: 1px solid $brand-line;
    color: $text-sub;
    font-size: 15px;

    &.is-active {
      background: $brand-primary;
      border-color: $brand-primary;
      color: #ffffff;
      font-weight: 600;
    }
  }
}

.login-form__item {
  display: flex;
  align-items: center;
  background: #ffffff;
  border: 1px solid $brand-line;
  border-radius: $radius-btn;
  height: 96rpx;
  padding: 0 32rpx;
  margin-bottom: 32rpx;
}

.login-form__input {
  flex: 1;
  font-size: 15px;
  color: $text-main;
}

.login-form__code-btn {
  flex-shrink: 0;
  height: 64rpx;
  line-height: 60rpx;
  padding: 0 24rpx;
  border-radius: $radius-btn;
  background: transparent;
  border: 1px solid $brand-primary;
  color: $brand-primary;
  font-size: 13px;

  &::after {
    border: none;
  }

  &.is-disabled {
    color: $text-sub;
    border-color: $brand-line;
  }
}

.login-demo {
  display: flex;
  align-items: center;
  gap: 16rpx;
  background: rgba(212, 165, 116, 0.15);
  border: 1px dashed $brand-accent;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  margin-bottom: 32rpx;

  &__label {
    color: $text-sub;
    font-size: 12px;
    flex-shrink: 0;
  }

  &__code {
    color: $brand-primary;
    font-size: 22px;
    font-weight: 800;
    letter-spacing: 4px;
    font-variant-numeric: tabular-nums;
  }

  &__expire {
    margin-left: auto;
    color: $brand-accent;
    font-size: 12px;
    font-variant-numeric: tabular-nums;

    &.is-expired {
      color: $text-sub;
    }
  }
}

.login-form__submit {
  margin-top: 16rpx;
}

.login-form__tip {
  display: block;
  text-align: center;
  margin-top: 32rpx;
  color: $text-sub;
  font-size: 12px;
}

.login-merchant-entry {
  display: flex;
  justify-content: center;
  margin-top: 20rpx;

  &__text {
    color: $brand-primary;
    font-size: 13px;
    text-decoration: underline;
  }
}

.login-form__placeholder {
  color: $text-sub;
}
</style>
