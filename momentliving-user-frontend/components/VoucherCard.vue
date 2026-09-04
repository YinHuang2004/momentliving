<template>
  <view class="voucher-card brand-card" :class="{ 'voucher-card--seckill': mode === 'seckill' }" @click="$emit('click', voucher)">
    <!-- 左侧券面：米橘点缀 -->
    <view class="voucher-card__face">
      <text class="voucher-card__unit">¥</text>
      <text class="voucher-card__value">{{ payValueText }}</text>
      <text class="voucher-card__tag" v-if="mode === 'seckill'">秒杀</text>
    </view>
    <!-- 右侧信息 -->
    <view class="voucher-card__info">
      <text class="voucher-card__title ellipsis-1">{{ voucher.title }}</text>
      <text class="voucher-card__sub ellipsis-1" v-if="subText">{{ subText }}</text>
      <view class="voucher-card__meta">
        <text class="voucher-card__original" v-if="actualValueText">面值 ¥{{ actualValueText }}</text>
        <text class="voucher-card__stock" v-if="mode === 'seckill' && stockText">{{ stockText }}</text>
      </view>
      <view class="voucher-card__bottom">
        <text class="voucher-card__rules ellipsis-1" v-if="rulesText">{{ rulesText }}</text>
        <slot name="action"></slot>
      </view>
    </view>
  </view>
</template>

<script>
/**
 * 优惠券卡片（店铺详情 / 秒杀页 / 我的券包通用）
 * voucher 字段来自后端 VoucherVO：title/subTitle/rules/payValue(分)/actualValue(分)/type/stock/beginTime/endTime
 * mode：normal 店铺券 | seckill 秒杀券（米橘徽标 + 库存提示）
 */
export default {
  name: 'VoucherCard',
  // 必须声明 click：Vue 3 下未声明时 @click 会同时作为原生事件透传到根元素，点击一次触发两次
  emits: ['click'],
  props: {
    voucher: { type: Object, required: true },
    mode: { type: String, default: 'normal' } // normal | seckill
  },
  computed: {
    // 后端金额单位为分
    payValueText() {
      const v = Number(this.voucher.payValue)
      return v ? (v / 100).toFixed(v % 100 === 0 ? 0 : 2) : '0'
    },
    // 面值（原价），划线展示
    actualValueText() {
      const v = Number(this.voucher.actualValue)
      return v ? (v / 100).toFixed(v % 100 === 0 ? 0 : 2) : ''
    },
    stockText() {
      const s = Number(this.voucher.stock)
      if (Number.isNaN(s)) return ''
      return s > 0 ? `仅剩 ${s} 份` : '已抢光'
    },
    // 副标题/规则压成单行：text 组件会把 \n 渲染成真实换行，ellipsis-1 拦不住，
    // 多行会把卡片撑高导致列表卡片高矮不一
    subText() {
      return this.oneLine(this.voucher.subTitle)
    },
    rulesText() {
      return this.oneLine(this.voucher.rules)
    }
  },
  methods: {
    oneLine(s) {
      const t = String(s || '').replace(/\s*[\r\n]+\s*/g, ' · ').trim()
      return t || ''
    }
  }
}
</script>

<style lang="scss" scoped>
.voucher-card {
  display: flex;
  overflow: hidden;
  // 统一卡高：内容少的卡（无副标题/无规则）也撑到同一高度，列表不再高矮不一
  min-height: 220rpx;

  &__face {
    width: 170rpx;
    flex-shrink: 0;
    background: $brand-bg-2;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 24rpx 0;
    position: relative;
  }

  &--seckill .voucher-card__face {
    background: rgba(212, 165, 116, 0.18);
  }

  &__unit {
    color: $brand-accent;
    font-size: 16px;
    font-weight: 700;
  }

  &__value {
    color: $brand-accent;
    font-size: 40rpx;
    font-weight: 800;
    line-height: 1.1;
  }

  &__tag {
    margin-top: 8rpx;
    padding: 0 12rpx;
    border-radius: $radius-btn;
    background: $brand-accent;
    color: $text-main;
    font-size: 11px;
    line-height: 28rpx;
  }

  &__info {
    flex: 1;
    min-width: 0;
    padding: 20rpx 24rpx;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }

  &__title {
    font-size: 16px;
    font-weight: 600;
    color: $text-main;
  }

  &__sub {
    color: $text-sub;
    font-size: 12px;
  }

  &__meta {
    display: flex;
    align-items: baseline;
    gap: 16rpx;
  }

  &__original {
    color: $text-sub;
    font-size: 12px;
    text-decoration: line-through;
  }

  &__stock {
    color: $brand-accent;
    font-size: 12px;
  }

  &__bottom {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16rpx;
  }

  &__rules {
    flex: 1;
    color: $text-sub;
    font-size: 11px;
  }
}
</style>
