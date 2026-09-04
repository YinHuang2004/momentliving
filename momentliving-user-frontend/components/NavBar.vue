<template>
  <view class="nav-bar" :style="{ background: bgColor, color: color }">
    <!-- 状态栏避让 -->
    <view class="nav-bar__status" :style="{ background: bgColor }"></view>
    <view class="nav-bar__body">
      <view class="nav-bar__left" @click="handleBack">
        <text v-if="back" class="nav-bar__arrow">‹</text>
        <slot name="left"></slot>
      </view>
      <view class="nav-bar__title">
        <text class="nav-bar__title-text" :style="{ color }">{{ title }}</text>
      </view>
      <view class="nav-bar__right">
        <slot name="right"></slot>
      </view>
    </view>
  </view>
</template>

<script>
/**
 * 自定义导航栏（navigationStyle:custom 的页面使用）
 * 商家端页面统一 bgColor=$brand-dark 白字，用户端默认米白底
 * back 默认 true：所有非 tab 功能页左上角带返回箭头；tab 页需显式传 :back="false"
 */
export default {
  name: 'NavBar',
  emits: ['back'],
  props: {
    title: { type: String, default: '' },
    back: { type: Boolean, default: true },
    bgColor: { type: String, default: '#FAF7F0' },
    color: { type: String, default: '#1F2421' },
    /** 返回失败（无上级页面，如刷新/直达/reLaunch 进入）时的兜底跳转 */
    fallback: { type: String, default: '/pages/user/home/home' }
  },
  methods: {
    handleBack() {
      if (!this.back) return
      uni.navigateBack({
        fail: () => {
          // 无上级页面（刷新后直达/页面栈只有当前页）时回兜底页
          // reLaunch 同时兼容 tab 页与普通页（switchTab 只能跳 tab 页）
          uni.reLaunch({ url: this.fallback })
        }
      })
      this.$emit('back')
    }
  }
}
</script>

<style lang="scss" scoped>
.nav-bar {
  width: 100%;

  // 状态栏高度由系统 CSS 变量提供（H5 为 0）
  &__status {
    height: var(--status-bar-height);
  }

  &__body {
    height: 44px;
    display: flex;
    align-items: center;
    padding: 0 24rpx;
  }

  &__left,
  &__right {
    width: 120rpx;
    display: flex;
    align-items: center;
  }

  &__right {
    justify-content: flex-end;
  }

  &__arrow {
    font-size: 44px;
    line-height: 44px;
    margin-top: -6px;
    color: inherit;
  }

  &__title {
    flex: 1;
    text-align: center;
  }

  &__title-text {
    font-size: 17px;
    font-weight: 600;
  }
}
</style>
