<template>
  <view class="stat-badge" :class="`stat-badge--${type}`">{{ text }}</view>
</template>

<script>
/**
 * 状态徽标（三端统一色约定）：
 * - primary 抹茶绿底白字 = 已核销 / 上架 / 营业中 / 已支付
 * - accent  米橘底深字   = 待支付 / 秒杀 / 待核销 / 退款
 * - gray    灰色        = 已作废 / 已关闭 / 已下架
 */
export default {
  name: 'StatBadge',
  props: {
    text: { type: String, default: '' },
    type: { type: String, default: 'primary' } // primary | accent | gray
  }
}
</script>

<style lang="scss" scoped>
.stat-badge {
  display: inline-block;
  padding: 2px 12px;
  border-radius: $radius-btn;
  font-size: 12px;
  line-height: 20px;

  &--primary {
    background: $brand-primary;
    color: #ffffff;
  }

  &--accent {
    background: $brand-accent;
    color: $text-main;
  }

  &--gray {
    background: $brand-bg-2;
    color: $text-sub;
  }
}
</style>
