<template>
  <view class="empty-view">
    <view class="empty-view__icon">
      <view class="empty-view__circle"></view>
      <view class="empty-view__line"></view>
    </view>
    <text class="empty-view__text">{{ text }}</text>
  </view>
</template>

<script>
/** 列表空状态占位 */
export default {
  name: 'EmptyView',
  props: {
    text: { type: String, default: '暂无数据' },
    icon: { type: String, default: '' }
  }
}
</script>

<style lang="scss" scoped>
.empty-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80rpx 0;

  &__icon {
    position: relative;
    width: 120rpx;
    height: 120rpx;
    border-radius: 50%;
    background: $brand-bg-2;
    margin-bottom: 24rpx;
  }

  &__circle {
    position: absolute;
    top: 34rpx;
    left: 34rpx;
    width: 52rpx;
    height: 52rpx;
    border-radius: 50%;
    border: 4rpx solid $brand-line;
  }

  &__line {
    position: absolute;
    bottom: 22rpx;
    left: 42rpx;
    width: 36rpx;
    height: 4rpx;
    border-radius: 4rpx;
    background: $brand-line;
  }

  &__text {
    color: $text-sub;
    font-size: 14px;
  }
}
</style>
