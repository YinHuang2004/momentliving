<template>
  <view class="order-detail">
    <NavBar title="订单详情" class="page-nav" />
    <template v-if="order">
    <!-- 状态区 -->
    <view class="order-detail__status">
      <StatBadge :text="statusText" :type="statusType" />
      <text class="order-detail__status-tip">{{ statusTip }}</text>
    </view>

    <!-- 券信息 -->
    <view class="order-detail__voucher brand-card" v-if="voucher">
      <image class="order-detail__img" :src="cover" mode="aspectFill" />
      <view class="order-detail__info">
        <text class="order-detail__title">{{ voucher.title }}</text>
        <text class="order-detail__price">¥{{ payValueText }}</text>
      </view>
    </view>

    <!-- 核销码（已支付未核销时展示） -->
    <view class="order-detail__code brand-card" v-if="order.status === 1">
      <text class="order-detail__code-label">核销码（到店出示给商家）</text>
      <view class="order-detail__code-groups" v-if="codeGroups.length === 4">
        <text class="order-detail__code-group" v-for="(g, i) in codeGroups" :key="i">{{ g }}</text>
      </view>
      <text class="order-detail__code-empty" v-else>核销码生成中，请稍后刷新</text>
      <text class="order-detail__code-tip" v-if="codeGroups.length === 4">16 位核销码 · 每单限用一次</text>
    </view>

    <!-- 订单信息 -->
    <view class="order-detail__meta brand-card">
      <view class="order-detail__row">
        <text class="order-detail__label">订单号</text>
        <text>NO.{{ order.id }}</text>
      </view>
      <view class="order-detail__row">
        <text class="order-detail__label">下单时间</text>
        <text>{{ formatTime(order.createTime) }}</text>
      </view>
      <view class="order-detail__row" v-if="order.payTime">
        <text class="order-detail__label">支付时间</text>
        <text>{{ formatTime(order.payTime) }}</text>
      </view>
      <view class="order-detail__row" v-if="order.useTime">
        <text class="order-detail__label">核销时间</text>
        <text>{{ formatTime(order.useTime) }}</text>
      </view>
      <view class="order-detail__row" v-if="order.status === 1">
        <text class="order-detail__label">操作</text>
        <text class="order-detail__refund" @click="handleRefund">申请退款</text>
      </view>
    </view>
  </template>
  </view>
</template>

<script>
import StatBadge from '@/components/StatBadge.vue'
import { getOrder, getVoucher, refund } from '@/api/voucher.js'

/**
 * 订单详情：状态 + 券信息 + 16 位核销码（订单接口 verifyCode 字段，4 段 × 4 位展示）
 */
import NavBar from '@/components/NavBar.vue'
export default {
  components: { NavBar, StatBadge },
  data() {
    return {
      order: null,
      voucher: null
    }
  },
  computed: {
    statusText() {
      const map = { 0: '待支付', 1: '已支付', 2: '已核销', 3: '已退款', 4: '已关闭' }
      return map[this.order && this.order.status] || '未知'
    },
    statusType() {
      const s = this.order && this.order.status
      if (s === 1 || s === 2) return 'primary'
      if (s === 0 || s === 3) return 'accent'
      return 'gray'
    },
    statusTip() {
      const map = {
        0: '订单未支付，请尽快完成支付',
        1: '支付成功，到店出示核销码即可使用',
        2: '券已核销，感谢使用',
        3: '订单已退款，金额原路退回',
        4: '订单已关闭'
      }
      return map[this.order && this.order.status] || ''
    },
    // 16 位核销码按 4 段 × 4 位分组展示
    codeGroups() {
      const code = (this.order && this.order.verifyCode) || ''
      return code.length === 16 ? [code.slice(0, 4), code.slice(4, 8), code.slice(8, 12), code.slice(12, 16)] : []
    },
    cover() {
      if (!this.voucher || !this.voucher.images) return '/static/logo.png'
      return this.voucher.images.split(',')[0] || '/static/logo.png'
    },
    payValueText() {
      return this.voucher && this.voucher.payValue
        ? (this.voucher.payValue / 100).toFixed(2)
        : '--'
    }
  },
  onLoad(options) {
    this.orderId = options.id
  },
  onShow() {
    // 支付完成跳回时刷新状态
    this.loadOrder()
  },
  methods: {
    async loadOrder() {
      try {
        this.order = await getOrder(this.orderId)
        if (this.order && this.order.voucherId) {
          this.voucher = await getVoucher(this.order.voucherId)
        }
      } catch (e) {
        // toast 已统一处理
      }
    },
    handleRefund() {
      uni.showModal({
        title: '申请退款',
        content: '确认对该订单发起退款？退款将原路退回。',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await refund(this.order.id)
            uni.showToast({ title: '退款已受理', icon: 'none' })
            this.loadOrder()
          } catch (e) {
            // toast 已统一处理
          }
        }
      })
    },
    formatTime(t) {
      return (t || '').replace('T', ' ').slice(0, 19)
    }
  }
}
</script>

<style lang="scss" scoped>
.order-detail {
  min-height: 100vh;
  padding: 24rpx;
}

.order-detail__status {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 8rpx 8rpx 28rpx;
}

.order-detail__status-tip {
  color: $text-sub;
  font-size: 13px;
}

.order-detail__voucher {
  display: flex;
  padding: 24rpx;
  margin-bottom: 24rpx;
}

.order-detail__img {
  width: 150rpx;
  height: 150rpx;
  border-radius: 16rpx;
  background: $brand-bg-2;
  flex-shrink: 0;
}

.order-detail__info {
  flex: 1;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.order-detail__title {
  font-size: 16px;
  font-weight: 600;
}

.order-detail__price {
  color: $brand-accent;
  font-size: 20px;
  font-weight: 800;
}

.order-detail__code {
  padding: 32rpx;
  margin-bottom: 24rpx;
  text-align: center;
}

.order-detail__code-label {
  color: $text-sub;
  font-size: 13px;
}

.order-detail__code-groups {
  display: flex;
  justify-content: center;
  gap: 16rpx;
  margin-top: 20rpx;
}

.order-detail__code-group {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 2px;
  color: $text-main;
  background: $brand-bg-2;
  border-radius: 12rpx;
  padding: 12rpx 16rpx;
  font-variant-numeric: tabular-nums;
}

.order-detail__code-empty {
  display: block;
  margin-top: 20rpx;
  color: $brand-accent;
  font-size: 15px;
}

.order-detail__code-tip {
  display: block;
  margin-top: 20rpx;
  color: $text-sub;
  font-size: 12px;
}

.order-detail__meta {
  padding: 12rpx 28rpx;
}

.order-detail__row {
  display: flex;
  justify-content: space-between;
  padding: 22rpx 0;
  border-bottom: 1px solid $brand-line;
  font-size: 14px;

  &:last-child {
    border-bottom: none;
  }
}

.order-detail__label {
  color: $text-sub;
}

.order-detail__refund {
  color: $brand-accent;
  font-weight: 600;
}

/* NavBar 通栏：抵消根容器 padding */
.page-nav {
  margin: -24rpx -24rpx 0;
}
</style>
