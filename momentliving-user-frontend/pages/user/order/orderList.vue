<template>
  <view class="order-list">
    <NavBar title="我的订单" />
    <!-- 状态筛选 tabs -->
    <view class="order-list__tabs">
      <view
        v-for="t in tabs"
        :key="t.label"
        class="order-list__tab"
        :class="{ 'is-active': currentStatus === t.value }"
        @click="switchTab(t.value)"
      >
        <text>{{ t.label }}</text>
      </view>
    </view>

    <!-- 订单卡片 -->
    <view class="order-item brand-card" v-for="o in orders" :key="o.id">
      <view class="order-item__head">
        <text class="order-item__no">订单号 NO.{{ o.id }}</text>
        <StatBadge :text="statusText(o.status)" :type="statusType(o.status)" />
      </view>
      <view class="order-item__body" @click="goDetail(o)">
        <image class="order-item__img" :src="voucherCover(o)" mode="aspectFill" />
        <view class="order-item__info">
          <text class="order-item__title ellipsis-1">{{ voucherTitle(o) }}</text>
          <text class="order-item__time">{{ formatTime(o.createTime) }}</text>
          <text class="order-item__price">¥{{ payValueText(o) }}</text>
        </view>
      </view>
      <view class="order-item__actions">
        <button
          v-if="o.status === 0"
          class="order-item__btn order-item__btn--primary"
          @click="goPay(o)"
        >去支付</button>
        <button
          v-if="o.status === 1"
          class="order-item__btn"
          @click="goDetail(o)"
        >查看核销码</button>
        <button
          v-if="o.status === 1"
          class="order-item__btn order-item__btn--plain"
          @click="handleRefund(o)"
        >申请退款</button>
        <button
          v-if="o.status === 2"
          class="order-item__btn"
          @click="goDetail(o)"
        >查看详情</button>
      </view>
    </view>

    <EmptyView v-if="!loading && orders.length === 0" text="暂无相关订单" />
    <view class="order-list__loading" v-if="loading"><text>加载中…</text></view>
    <view class="order-list__nomore" v-if="!loading && !hasMore && orders.length > 0"><text>— 没有更多了 —</text></view>
  </view>
</template>

<script>
import StatBadge from '@/components/StatBadge.vue'
import EmptyView from '@/components/EmptyView.vue'
import { listMyOrders, getVoucher, refund } from '@/api/voucher.js'

/**
 * 我的订单：状态筛选 tabs + 订单卡片
 * 状态码：0待支付 1已支付 2已核销 3已退款 4已关闭
 * 订单接口不返回券信息，按 voucherId 去重后批量拉取券详情做本地联表
 */
import NavBar from '@/components/NavBar.vue'
export default {
  components: { NavBar, StatBadge, EmptyView },
  data() {
    return {
      tabs: [
        { label: '全部', value: null },
        { label: '待支付', value: 0 },
        { label: '已支付', value: 1 },
        { label: '已退款', value: 3 },
        { label: '已关闭', value: 4 }
      ],
      currentStatus: null,
      orders: [],
      voucherMap: {}, // voucherId -> VoucherVO
      current: 1,
      hasMore: true,
      loading: false
    }
  },
  onShow() {
    this.current = 1
    this.orders = []
    this.hasMore = true
    this.loadOrders()
  },
  onReachBottom() {
    this.loadOrders()
  },
  methods: {
    switchTab(value) {
      this.currentStatus = value
      this.current = 1
      this.orders = []
      this.hasMore = true
      this.loadOrders()
    },
    async loadOrders() {
      if (this.loading || !this.hasMore) return
      this.loading = true
      try {
        const list = await listMyOrders({ current: this.current, pageSize: 10, status: this.currentStatus })
        this.orders = this.current === 1 ? list || [] : this.orders.concat(list || [])
        this.hasMore = (list || []).length >= 10
        this.current++
        await this.fillVouchers(list || [])
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    // 批量补齐券信息（同一张券只请求一次）
    async fillVouchers(orders) {
      const ids = [...new Set(orders.map((o) => o.voucherId).filter((id) => id && !this.voucherMap[id]))]
      await Promise.all(
        ids.map(async (id) => {
          try {
            this.voucherMap[id] = await getVoucher(id)
          } catch (e) {
            // 单个券缺失不影响列表
          }
        })
      )
    },
    voucherTitle(order) {
      const v = this.voucherMap[order.voucherId]
      return (v && v.title) || `券 ${order.voucherId}`
    },
    voucherCover(order) {
      const v = this.voucherMap[order.voucherId]
      if (!v || !v.images) return '/static/logo.png'
      return v.images.split(',')[0] || '/static/logo.png'
    },
    payValueText(order) {
      const v = this.voucherMap[order.voucherId]
      if (!v || !v.payValue) return '--'
      return (v.payValue / 100).toFixed(2)
    },
    statusText(status) {
      const map = { 0: '待支付', 1: '已支付', 2: '已核销', 3: '已退款', 4: '已关闭' }
      return map[status] || '未知'
    },
    statusType(status) {
      // 0待支付=米橘 / 1已支付、2已核销=抹茶绿 / 3已退款=米橘 / 4已关闭=灰
      if (status === 1 || status === 2) return 'primary'
      if (status === 0 || status === 3) return 'accent'
      return 'gray'
    },
    goPay(order) {
      uni.navigateTo({ url: `/pages/user/pay/payConfirm?orderId=${order.id}` })
    },
    goDetail(order) {
      uni.navigateTo({ url: `/pages/user/order/orderDetail?id=${order.id}` })
    },
    handleRefund(order) {
      uni.showModal({
        title: '申请退款',
        content: '确认对该订单发起退款？退款将原路退回。',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await refund(order.id)
            uni.showToast({ title: '退款已受理', icon: 'none' })
            this.switchTab(this.currentStatus)
          } catch (e) {
            // toast 已统一处理
          }
        }
      })
    },
    formatTime(t) {
      return (t || '').replace('T', ' ').slice(0, 16)
    }
  }
}
</script>

<style lang="scss" scoped>
.order-list {
  min-height: 100vh;
  padding-bottom: 40rpx;
}

.order-list__tabs {
  display: flex;
  background: $brand-bg;
  position: sticky;
  top: 0;
  z-index: 10;
  padding: 0 12rpx;
  border-bottom: 1px solid $brand-line;
}

.order-list__tab {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  color: $text-sub;
  font-size: 15px;
  position: relative;

  &.is-active {
    color: $brand-primary;
    font-weight: 700;

    &::after {
      content: '';
      position: absolute;
      left: 50%;
      transform: translateX(-50%);
      bottom: 0;
      width: 48rpx;
      height: 4rpx;
      border-radius: 4rpx;
      background: $brand-primary;
    }
  }
}

.order-item {
  margin: 24rpx;
  padding: 24rpx 28rpx;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__no {
    color: $text-sub;
    font-size: 12px;
  }

  &__body {
    display: flex;
    margin-top: 20rpx;
  }

  &__img {
    width: 140rpx;
    height: 140rpx;
    border-radius: 16rpx;
    background: $brand-bg-2;
    flex-shrink: 0;
  }

  &__info {
    flex: 1;
    min-width: 0;
    margin-left: 20rpx;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }

  &__title {
    font-size: 15px;
    font-weight: 600;
  }

  &__time {
    color: $text-sub;
    font-size: 12px;
  }

  &__price {
    color: $brand-accent;
    font-size: 18px;
    font-weight: 700;
  }

  &__actions {
    display: flex;
    justify-content: flex-end;
    gap: 16rpx;
    margin-top: 20rpx;
  }

  &__btn {
    height: 60rpx;
    line-height: 56rpx;
    padding: 0 28rpx;
    border-radius: $radius-btn;
    background: transparent;
    border: 1px solid $brand-primary;
    color: $brand-primary;
    font-size: 13px;

    &::after {
      border: none;
    }

    &--primary {
      background: $brand-primary;
      color: #ffffff;
    }

    &--plain {
      border-color: $brand-line;
      color: $text-sub;
    }
  }
}

.order-list__loading,
.order-list__nomore {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;
  color: $text-sub;
  font-size: 13px;
}
</style>
