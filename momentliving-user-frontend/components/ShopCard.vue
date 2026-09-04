<template>
  <view class="shop-card brand-card" @click="$emit('click', shop)">
    <image
      class="shop-card__img"
      :src="cover"
      mode="aspectFill"
      :lazy-load="true"
    />
    <view class="shop-card__info">
      <view class="shop-card__name-row">
        <text class="shop-card__name">{{ shop.name }}</text>
        <text class="shop-card__score" v-if="scoreText">★{{ scoreText }}</text>
      </view>
      <text class="shop-card__addr ellipsis-1" v-if="shop.address">{{ shop.area }} · {{ shop.address }}</text>
      <view class="shop-card__meta-row">
        <text class="shop-card__price" v-if="avgPriceText">人均 ¥{{ avgPriceText }}</text>
        <text class="shop-card__sold" v-if="shop.sold != null">已售 {{ shop.sold }}</text>
        <text class="shop-card__distance" v-if="distanceText">{{ distanceText }}</text>
      </view>
    </view>
  </view>
</template>

<script>
/**
 * 店铺卡片（首页/搜索列表通用）
 * shop 字段来自后端 ShopVO：name/score(Integer)/avgPrice(分)/sold/area/address/distance/images(逗号分隔)
 */
export default {
  name: 'ShopCard',
  // 必须声明 click：Vue 3 下未声明时 @click 会同时作为原生事件透传到根元素，
  // 点击一次触发两次（第一次参数是 MouseEvent，payload 丢失 → 跳转 ?id=undefined）
  emits: ['click'],
  props: {
    shop: { type: Object, required: true }
  },
  computed: {
    // images 为逗号分隔字符串，取第一张做封面
    cover() {
      const imgs = (this.shop.images || '').split(',').filter(Boolean)
      return imgs[0] || '/static/logo.png'
    },
    // 评分兼容两种口径：0-50（放大 10 倍）或 0-5
    scoreText() {
      const s = Number(this.shop.score)
      if (!s) return ''
      return s > 5 ? (s / 10).toFixed(1) : String(s)
    },
    // 后端金额单位为分，统一除以 100 展示
    avgPriceText() {
      const p = Number(this.shop.avgPrice)
      if (!p) return ''
      return (p / 100).toFixed(0)
    },
    distanceText() {
      const d = this.shop.distance
      if (d == null) return ''
      return d >= 1000 ? `${(d / 1000).toFixed(1)}km` : `${Math.round(d)}m`
    }
  }
}
</script>

<style lang="scss" scoped>
.shop-card {
  display: flex;
  padding: 20rpx;
  margin: 0 24rpx 24rpx;

  &__img {
    width: 180rpx;
    height: 180rpx;
    border-radius: 16rpx;
    flex-shrink: 0;
    background: $brand-bg-2;
  }

  &__info {
    flex: 1;
    min-width: 0;
    margin-left: 24rpx;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }

  &__name-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__name {
    font-size: 17px;
    font-weight: 600;
    color: $text-main;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__score {
    color: $brand-accent;
    font-size: 14px;
    font-weight: 600;
    margin-left: 12rpx;
    flex-shrink: 0;
  }

  &__addr {
    color: $text-sub;
    font-size: 13px;
  }

  &__meta-row {
    display: flex;
    align-items: baseline;
    gap: 24rpx;
  }

  &__price {
    color: $brand-accent;
    font-size: 16px;
    font-weight: 700;
  }

  &__sold,
  &__distance {
    color: $text-sub;
    font-size: 12px;
  }
}
</style>
