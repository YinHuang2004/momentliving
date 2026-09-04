<template>
  <view class="records-page">
    <NavBar title="核销记录" :back="false" bgColor="#4A6B4D" color="#FAF7F0" />

    <!-- 筛选 tabs（对应 voucher_verify.status：0未核销 1已核销 2已作废） -->
    <view class="records-tabs">
      <view
        v-for="t in tabs"
        :key="t.label"
        class="records-tabs__item"
        :class="{ 'is-active': currentStatus === t.value }"
        @click="switchTab(t.value)"
      >
        <text>{{ t.label }}</text>
      </view>
    </view>

    <!-- 记录列表 -->
    <view class="record-item brand-card" v-for="r in records" :key="r.orderId + '-' + r.verifyTime">
      <view class="record-item__left">
        <text class="record-item__voucher">{{ r.voucherTitle || '未知券' }}</text>
        <text class="record-item__meta">
          {{ formatTime(r.verifyTime) }} · 买家 {{ r.nickName || r.userId }} · 尾号 {{ r.verifyCodeTail || '----' }}
        </text>
      </view>
      <StatBadge :text="statusText(r.status)" :type="statusType(r.status)" />
    </view>
    <EmptyView v-if="!loading && records.length === 0" text="暂无相关核销记录" />
    <view class="records-page__loading" v-if="loading"><text>加载中…</text></view>
    <view class="records-page__nomore" v-if="!loading && !hasMore && records.length > 0">
      <text>— 共 {{ total }} 条，没有更多了 —</text>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/NavBar.vue'
import StatBadge from '@/components/StatBadge.vue'
import EmptyView from '@/components/EmptyView.vue'
import { verifyRecords } from '@/api/merchant.js'
import { getToken } from '@/utils/request.js'

/**
 * 商家核销记录：分页接口 GET /admin/verify/records（按登录商家绑定的店铺统计）
 * 状态为核销记录状态（voucher_verify.status）：0未核销 1已核销 2已作废
 */
export default {
  components: { NavBar, StatBadge, EmptyView },
  data() {
    return {
      tabs: [
        { label: '全部', value: null },
        { label: '已核销', value: 1 },
        { label: '未核销', value: 0 },
        { label: '已作废', value: 2 }
      ],
      currentStatus: null,
      records: [],
      total: 0,
      current: 1,
      hasMore: true,
      loading: false
    }
  },
  onShow() {
    if (!getToken()) {
      uni.reLaunch({ url: '/pages/merchant/login/login' })
      return
    }
    this.switchTab(this.currentStatus)
  },
  onReachBottom() {
    this.loadRecords()
  },
  methods: {
    switchTab(value) {
      this.currentStatus = value
      this.current = 1
      this.records = []
      this.hasMore = true
      this.loadRecords()
    },
    async loadRecords() {
      if (this.loading || !this.hasMore) return
      this.loading = true
      try {
        const res = await verifyRecords({
          status: this.currentStatus,
          current: this.current,
          pageSize: 10
        })
        const list = (res && res.list) || []
        this.total = (res && res.total) || 0
        this.records = this.current === 1 ? list : this.records.concat(list)
        this.hasMore = list.length >= 10
        this.current++
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    statusText(status) {
      const map = { 0: '未核销', 1: '已核销', 2: '已作废' }
      return map[status] || '未知'
    },
    statusType(status) {
      // 已核销=抹茶绿 / 未核销=米橘 / 已作废=灰
      if (status === 1) return 'primary'
      if (status === 0) return 'accent'
      return 'gray'
    },
    formatTime(t) {
      return (t || '').replace('T', ' ').slice(0, 16)
    }
  }
}
</script>

<style lang="scss" scoped>
.records-page {
  min-height: 100vh;
  background: $brand-bg;
  padding-bottom: 160rpx;
}

.records-tabs {
  display: flex;
  padding: 20rpx 24rpx 0;
  border-bottom: 1px solid $brand-line;

  &__item {
    flex: 1;
    text-align: center;
    padding: 20rpx 0;
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
}

.record-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20rpx 24rpx;
  padding: 24rpx 28rpx;
}

.record-item__left {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.record-item__voucher {
  font-size: 15px;
  font-weight: 600;
}

.record-item__meta {
  margin-top: 8rpx;
  color: $text-sub;
  font-size: 12px;
}

.records-page__loading,
.records-page__nomore {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;
  color: $text-sub;
  font-size: 13px;
}
</style>
