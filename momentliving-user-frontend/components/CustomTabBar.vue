<template>
  <view class="custom-tabbar">
    <view
      v-for="(tab, index) in tabs"
      :key="tab.name"
      class="custom-tabbar__item"
      :class="{ 'is-active': active === index }"
      @click="handleTab(index)"
    >
      <view class="custom-tabbar__icon">
        <!-- 线性几何图标（与三端线性图标风格一致的简化占位） -->
        <view v-if="tab.shape === 'grid'" class="tab-shape tab-shape--grid">
          <view class="grid-dot"></view>
          <view class="grid-dot"></view>
          <view class="grid-dot"></view>
          <view class="grid-dot"></view>
        </view>
        <view v-else-if="tab.shape === 'scan'" class="tab-shape tab-shape--scan">
          <view class="scan-corner scan-corner--tl"></view>
          <view class="scan-corner scan-corner--tr"></view>
          <view class="scan-corner scan-corner--bl"></view>
          <view class="scan-corner scan-corner--br"></view>
          <view class="scan-line"></view>
        </view>
        <view v-else-if="tab.shape === 'list'" class="tab-shape tab-shape--list">
          <view class="list-line"></view>
          <view class="list-line"></view>
          <view class="list-line list-line--short"></view>
        </view>
        <view v-else class="tab-shape tab-shape--user">
          <view class="user-head"></view>
          <view class="user-body"></view>
        </view>
      </view>
      <text class="custom-tabbar__label">{{ tab.name }}</text>
      <view class="custom-tabbar__dot" v-if="active === index"></view>
    </view>
  </view>
</template>

<script>
/**
 * 商家端自定义底部 TabBar（工作台/核销/记录/我的）
 * 原因：uni-app pages.json 原生 tabBar 一套工程只能配一套，用户端已占用 5 个 tab。
 * 使用：每个商家页面内嵌 <CustomTabBar :active="n" @change="..." />，页面 padding-bottom 预留 140rpx，
 *      切换用 uni.redirectTo 避免页面堆栈增长。
 */
export default {
  name: 'CustomTabBar',
  emits: ['change'],
  props: {
    active: { type: Number, default: 0 } // 0 工作台 / 1 核销 / 2 记录 / 3 我的
  },
  data() {
    return {
      tabs: [
        { name: '工作台', path: '/pages/merchant/workbench/workbench', shape: 'grid' },
        { name: '核销', path: '/pages/merchant/verify/verify', shape: 'scan' },
        { name: '记录', path: '/pages/merchant/records/records', shape: 'list' },
        { name: '我的', path: '/pages/merchant/mine/mine', shape: 'user' }
      ]
    }
  },
  methods: {
    handleTab(index) {
      if (index === this.active) return
      this.$emit('change', index)
      uni.redirectTo({ url: this.tabs[index].path })
    }
  }
}
</script>

<style lang="scss" scoped>
.custom-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  background: $brand-bg;
  border-top: 1px solid $brand-line;
  padding-bottom: env(safe-area-inset-bottom);
  z-index: 999;

  &__item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 12rpx 0 8rpx;
    position: relative;
  }

  &__icon {
    height: 48rpx;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__label {
    font-size: 12px;
    color: $text-sub;
    margin-top: 2rpx;
  }

  &__item.is-active .custom-tabbar__label {
    color: $brand-primary;
    font-weight: 600;
  }

  &__dot {
    width: 8rpx;
    height: 8rpx;
    border-radius: 50%;
    background: $brand-primary;
    margin-top: 4rpx;
  }
}

/* --- 简化线性图标 --- */
.tab-shape {
  position: relative;
  width: 44rpx;
  height: 44rpx;
}

.tab-shape--grid {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8rpx;

  .grid-dot {
    width: 14rpx;
    height: 14rpx;
    border: 3rpx solid $text-sub;
    border-radius: 4rpx;
  }
}

.is-active .tab-shape--grid .grid-dot {
  border-color: $brand-primary;
}

.tab-shape--scan {
  .scan-corner {
    position: absolute;
    width: 14rpx;
    height: 14rpx;
    border: 3rpx solid $text-sub;
  }

  .scan-corner--tl { top: 0; left: 0; border-right: none; border-bottom: none; }
  .scan-corner--tr { top: 0; right: 0; border-left: none; border-bottom: none; }
  .scan-corner--bl { bottom: 0; left: 0; border-right: none; border-top: none; }
  .scan-corner--br { bottom: 0; right: 0; border-left: none; border-top: none; }

  .scan-line {
    position: absolute;
    top: 50%;
    left: 6rpx;
    right: 6rpx;
    height: 3rpx;
    background: $text-sub;
  }
}

.is-active .tab-shape--scan .scan-corner,
.is-active .tab-shape--scan .scan-line {
  border-color: $brand-primary;
  background-color: $brand-primary;
}

.tab-shape--list {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8rpx;
  padding: 0 4rpx;

  .list-line {
    height: 4rpx;
    border-radius: 4rpx;
    background: $text-sub;
  }

  .list-line--short {
    width: 60%;
  }
}

.is-active .tab-shape--list .list-line {
  background: $brand-primary;
}

.tab-shape--user {
  .user-head {
    width: 16rpx;
    height: 16rpx;
    border: 3rpx solid $text-sub;
    border-radius: 50%;
    margin: 0 auto;
  }

  .user-body {
    width: 26rpx;
    height: 12rpx;
    border: 3rpx solid $text-sub;
    border-radius: 8rpx 8rpx 0 0;
    border-bottom: none;
    margin: 2rpx auto 0;
  }
}

.is-active .tab-shape--user .user-head,
.is-active .tab-shape--user .user-body {
  border-color: $brand-primary;
}
</style>
