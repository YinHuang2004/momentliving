<template>
  <view class="my-voucher">
    <NavBar title="我的优惠券" class="page-nav" />
    <view class="voucher-item" v-for="item in list" :key="item.order.id">
      <VoucherCard :voucher="item.voucher" :mode="item.voucher.type === 1 ? 'seckill' : 'normal'">
        <template #action>
          <StatBadge :text="statusText(item.order.status)" :type="item.order.status === 2 ? 'primary' : 'accent'" />
        </template>
      </VoucherCard>
      <view class="voucher-item__footer">
        <text class="voucher-item__time">下单时间 {{ formatTime(item.order.createTime) }}</text>
        <button class="voucher-item__btn" @click="goDetail(item.order)">
          {{ item.order.status === 1 ? '出示核销码' : '查看详情' }}
        </button>
      </view>
    </view>
    <EmptyView v-if="!loading && list.length === 0" text="暂无可用优惠券，去店铺详情领取吧" />
    <view class="my-voucher__loading" v-if="loading"><text>加载中…</text></view>
  </view>
</template>

<script>
import VoucherCard from '@/components/VoucherCard.vue'
import StatBadge from '@/components/StatBadge.vue'
import EmptyView from '@/components/EmptyView.vue'
import { listMyOrders, getVoucher } from '@/api/voucher.js'

/**
 * 我的优惠券：已支付(1)/已核销(2) 的订单 + 券信息联表展示
 */
import NavBar from '@/components/NavBar.vue'
export default {
  components: { NavBar, VoucherCard, StatBadge, EmptyView },
  data() {
    return {
      list: [],
      loading: false
    }
  },
  onShow() {
    this.loadList()
  },
  methods: {
    async loadList() {
      this.loading = true
      try {
        const orders = await listMyOrders({ current: 1, pageSize: 50 })
        // 只展示可用状态（已支付/已核销）
        const usable = (orders || []).filter((o) => o.status === 1 || o.status === 2)
        this.list = await Promise.all(
          usable.map(async (order) => {
            let voucher = null
            try {
              voucher = await getVoucher(order.voucherId)
            } catch (e) {
              voucher = { id: order.voucherId, title: `券 ${order.voucherId}` }
            }
            return { order, voucher }
          })
        )
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    statusText(status) {
      return status === 2 ? '已核销' : '待使用'
    },
    goDetail(order) {
      uni.navigateTo({ url: `/pages/user/order/orderDetail?id=${order.id}` })
    },
    formatTime(t) {
      return (t || '').replace('T', ' ').slice(0, 16)
    }
  }
}
</script>

<style lang="scss" scoped>
.my-voucher {
  min-height: 100vh;
  padding: 24rpx;
}

.voucher-item {
  margin-bottom: 24rpx;
}

.voucher-item__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12rpx;
}

.voucher-item__time {
  color: $text-sub;
  font-size: 12px;
}

.voucher-item__btn {
  height: 56rpx;
  line-height: 52rpx;
  padding: 0 28rpx;
  border-radius: $radius-btn;
  background: $brand-primary;
  color: #ffffff;
  font-size: 13px;

  &::after {
    border: none;
  }
}

.my-voucher__loading {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;
  color: $text-sub;
  font-size: 13px;
}

/* NavBar 通栏：抵消根容器 padding */
.page-nav {
  margin: -24rpx -24rpx 0;
}
</style>
