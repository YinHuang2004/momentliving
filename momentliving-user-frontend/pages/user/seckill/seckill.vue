<template>
  <view class="coupon-page">
    <!-- 顶部 banner + 子页签 -->
    <view class="coupon-banner">
      <text class="coupon-banner__title">优惠券中心</text>
      <text class="coupon-banner__sub">· 每人限购 3 单</text>
    </view>
    <view class="coupon-tabs">
      <view
        v-for="t in tabs"
        :key="t.type"
        class="coupon-tabs__item"
        :class="{ 'is-active': activeType === t.type }"
        @click="switchTab(t.type)"
      >
        <text>{{ t.label }}</text>
      </view>
    </view>

    <!-- 秒杀券：倒计时 + 抢购按钮 -->
    <view class="coupon-list" v-if="activeType === 1 && vouchers.length > 0">
      <view class="coupon-item brand-card" v-for="v in vouchers" :key="v.id">
        <VoucherCard :voucher="v" mode="seckill" @click="goDetail(v)">
          <template #action>
            <button
              class="coupon-item__btn"
              :class="{ 'is-pending': phaseOf(v) === 'pending' }"
              :disabled="phaseOf(v) !== 'open'"
              @click.stop="handleBuy(v)"
            >
              {{ btnText(v) }}
            </button>
          </template>
        </VoucherCard>
        <view class="coupon-item__scope" @click="goDetail(v)">
          <text>{{ scopeText(v) }}</text>
        </view>
        <view class="coupon-item__time">
          <text v-if="phaseOf(v) === 'pending'">{{ formatTime(v.beginTime) }} 开抢</text>
          <text v-else-if="phaseOf(v) === 'open'">距结束 {{ remainText(v) }}</text>
          <text v-else class="is-ended">活动已结束</text>
        </view>
      </view>
    </view>

    <!-- 普通券：点击进详情（详情页购买） -->
    <view class="coupon-list" v-if="activeType === 0 && vouchers.length > 0">
      <view class="coupon-item brand-card" v-for="v in vouchers" :key="v.id" @click="goDetail(v)">
        <VoucherCard :voucher="v" mode="normal">
          <template #action>
            <button class="coupon-item__btn" @click.stop="goDetail(v)">去使用</button>
          </template>
        </VoucherCard>
        <view class="coupon-item__scope">
          <text>{{ scopeText(v) }}</text>
        </view>
      </view>
    </view>

    <EmptyView v-if="!loading && vouchers.length === 0"
               :text="activeType === 1 ? '暂无秒杀活动，看看普通优惠券吧' : '暂无普通优惠券'" />
    <view class="coupon-loading" v-if="loading"><text>加载中…</text></view>
  </view>
</template>

<script>
import VoucherCard from '@/components/VoucherCard.vue'
import EmptyView from '@/components/EmptyView.vue'
import { listPlatformVouchers, createSeckillOrder } from '@/api/voucher.js'
import { getToken } from '@/utils/request.js'

/**
 * Tab2 优惠券中心（原"秒杀"页升级）：
 *  - 普通优惠券子页：平台全部普通券，点击进券详情（含适用店铺）
 *  - 秒杀优惠券子页：全量秒杀券（GET /voucher/all?type=1），三态倒计时 + 直接抢购
 * 券卡上的适用范围行：全场通用 / N 家店铺可用 / 单店名（shopIds 由后端统一回填，空=全场）
 */
export default {
  components: { VoucherCard, EmptyView },
  data() {
    return {
      tabs: [
        { type: 0, label: '普通优惠券' },
        { type: 1, label: '秒杀优惠券' }
      ],
      activeType: 0,
      vouchers: [],
      nowTs: Date.now(), // 每秒刷新，驱动秒杀券倒计时/状态响应式更新
      loading: false,
      timer: null
    }
  },
  onLoad(options) {
    if (options && options.type === 'seckill') {
      this.activeType = 1
    }
    this.loadVouchers()
  },
  onShow() {
    if (!getToken()) {
      uni.reLaunch({ url: '/pages/user/login/login' })
      return
    }
    if (this.activeType === 1) this.startTimer()
  },
  onHide() {
    this.stopTimer()
  },
  onUnload() {
    this.stopTimer()
  },
  methods: {
    switchTab(type) {
      if (this.activeType === type) return
      this.activeType = type
      this.vouchers = []
      if (type === 1) this.startTimer()
      else this.stopTimer()
      this.loadVouchers()
    },
    async loadVouchers() {
      this.loading = true
      try {
        this.vouchers = (await listPlatformVouchers(this.activeType)) || []
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    scopeText(v) {
      const ids = v.shopIds || []
      if (ids.length === 0) return '全场通用'
      if (ids.length === 1) return '1 家店铺可用 · 点击查看'
      return `${ids.length} 家店铺可用 · 点击查看`
    },
    goDetail(v) {
      uni.navigateTo({ url: `/pages/user/voucher/couponDetail?id=${v.id}` })
    },
    startTimer() {
      this.stopTimer()
      this.nowTs = Date.now()
      this.timer = setInterval(() => {
        this.nowTs = Date.now()
      }, 1000)
    },
    stopTimer() {
      if (this.timer) clearInterval(this.timer)
      this.timer = null
    },
    // 后端 LocalDateTime 序列化为 "yyyy-MM-ddTHH:mm:ss"，按本地时区解析
    toTs(t) {
      if (!t) return 0
      return new Date(String(t).replace(' ', 'T')).getTime() || 0
    },
    phaseOf(v) {
      const now = this.nowTs
      const begin = this.toTs(v.beginTime)
      const end = this.toTs(v.endTime)
      if (begin && now < begin) return 'pending'
      if (end && now > end) return 'ended'
      return 'open'
    },
    btnText(v) {
      const phase = this.phaseOf(v)
      if (phase === 'pending') return '未开始'
      if (phase === 'ended') return '已结束'
      return '立即抢购'
    },
    remainText(v) {
      const s = Math.max(0, Math.floor((this.toTs(v.endTime) - this.nowTs) / 1000))
      const h = String(Math.floor(s / 3600)).padStart(2, '0')
      const m = String(Math.floor((s % 3600) / 60)).padStart(2, '0')
      const sec = String(s % 60).padStart(2, '0')
      return `${h}:${m}:${sec}`
    },
    formatTime(t) {
      return (t || '').replace('T', ' ').slice(5, 16)
    },
    async handleBuy(v) {
      if (this.phaseOf(v) !== 'open') return
      try {
        // 秒杀下单异步落库，支付页自带订单查询重试
        const orderId = await createSeckillOrder(v.id)
        uni.navigateTo({ url: `/pages/user/pay/payConfirm?orderId=${orderId}` })
      } catch (e) {
        // toast 已统一处理（库存不足/限购 3 单等）
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.coupon-page {
  min-height: 100vh;
  padding: 24rpx 24rpx 60rpx;
}

.coupon-banner {
  background: #ffffff;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
  padding: 36rpx 32rpx;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 20rpx;

  &__title {
    font-size: 26px;
    font-weight: 800;
    color: $brand-primary;
  }

  &__sub {
    color: $text-sub;
    font-size: 12px;
  }
}

.coupon-tabs {
  display: flex;
  background: #ffffff;
  border-radius: 999rpx;
  padding: 8rpx;
  margin-bottom: 24rpx;

  &__item {
    flex: 1;
    text-align: center;
    padding: 16rpx 0;
    border-radius: 999rpx;
    color: $text-sub;
    font-size: 14px;

    &.is-active {
      background: $brand-primary;
      color: #ffffff;
      font-weight: 600;
    }
  }
}

.coupon-item {
  padding: 16rpx;
  margin-bottom: 20rpx;

  &__scope {
    padding: 4rpx 12rpx 0;
    color: $brand-primary;
    font-size: 12px;
  }

  &__time {
    display: flex;
    justify-content: flex-end;
    padding: 8rpx 12rpx 4rpx;
    color: $brand-accent;
    font-size: 12px;
    font-variant-numeric: tabular-nums;

    .is-ended {
      color: $text-sub;
    }
  }

  &__btn {
    height: 64rpx;
    line-height: 60rpx;
    padding: 0 28rpx;
    border-radius: $radius-btn;
    background: $brand-primary;
    color: #ffffff;
    font-size: 14px;

    &::after {
      border: none;
    }

    &[disabled] {
      opacity: 0.55;
      color: #ffffff;
      background: $brand-primary;
    }
  }
}

.coupon-loading {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;
  color: $text-sub;
  font-size: 13px;
}
</style>
