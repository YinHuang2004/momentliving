<template>
  <view class="vc-detail">
    <!-- 券主体 -->
    <view class="vc-hero brand-card">
      <view class="vc-hero__top">
        <text class="vc-hero__title">{{ voucher.title || '加载中…' }}</text>
        <text class="vc-hero__tag" :class="{ 'is-seckill': isSeckill }">{{ isSeckill ? '秒杀券' : '普通券' }}</text>
      </view>
      <text class="vc-hero__sub" v-if="voucher.subTitle">{{ voucher.subTitle }}</text>
      <view class="vc-hero__price">
        <text class="vc-hero__pay">¥{{ fen2yuan(voucher.payValue) }}</text>
        <text class="vc-hero__actual" v-if="voucher.actualValue">面值 ¥{{ fen2yuan(voucher.actualValue) }}</text>
      </view>
      <view class="vc-hero__meta" v-if="isSeckill && voucher.beginTime">
        <text>{{ fmtTime(voucher.beginTime) }} ~ {{ fmtTime(voucher.endTime) }}</text>
        <text v-if="voucher.stock != null" class="vc-hero__stock">剩余 {{ voucher.stock }} 件</text>
      </view>
      <view class="vc-hero__rules" v-if="voucher.rules">
        <text class="vc-hero__rules-label">使用规则</text>
        <text class="vc-hero__rules-text">{{ voucher.rules }}</text>
      </view>
    </view>

    <!-- 购买按钮（秒杀券带三态） -->
    <button
      class="vc-buy"
      :class="{ 'is-pending': isSeckill && phaseOf !== 'open', 'is-seckill': isSeckill }"
      :disabled="isSeckill && phaseOf !== 'open'"
      @click="handleBuy"
    >
      {{ buyText }}
    </button>

    <!-- 适用店铺 -->
    <view class="vc-shops">
      <text class="vc-shops__title">适用店铺</text>
      <view class="vc-shops__all brand-card" v-if="isUniversal">
        <text class="vc-shops__all-icon">🌐</text>
        <view class="vc-shops__all-info">
          <text class="vc-shops__all-name">全场通用</text>
          <text class="vc-shops__all-sub">所有店铺均可购买并使用本券</text>
        </view>
      </view>
      <template v-else>
        <view class="vc-shop brand-card" v-for="s in voucher.scopeShops || []" :key="s.id" @click="goShop(s.id)">
          <image class="vc-shop__img" :src="coverOf(s)" mode="aspectFill" />
          <view class="vc-shop__info">
            <text class="vc-shop__name">{{ s.name }}</text>
            <text class="vc-shop__addr ellipsis-1">{{ s.address || '暂无地址' }}</text>
          </view>
          <text class="vc-shop__arrow">›</text>
        </view>
        <EmptyView v-if="!loading && (voucher.scopeShops || []).length === 0" text="暂无适用店铺信息" />
      </template>
    </view>
  </view>
</template>

<script>
import EmptyView from '@/components/EmptyView.vue'
import { getVoucher, createSeckillOrder, buyVoucher } from '@/api/voucher.js'
import { getToken } from '@/utils/request.js'

/**
 * 优惠券详情页：券信息 + 适用店铺列表。
 * - 适用范围：scopeShops 非空 = 逐店展示（点击跳店铺详情）；空 = 全场通用横幅
 * - 普通券直接购买（buyVoucher）；秒杀券按活动三态抢购（createSeckillOrder，异步落库）
 */
export default {
  components: { EmptyView },
  data() {
    return {
      voucher: {},
      loading: true,
      nowTs: Date.now(),
      timer: null,
      buying: false
    }
  },
  computed: {
    isSeckill() {
      return this.voucher.type === 1
    },
    isUniversal() {
      return (this.voucher.shopIds || []).length === 0
    },
    phaseOf() {
      const now = this.nowTs
      const begin = this.toTs(this.voucher.beginTime)
      const end = this.toTs(this.voucher.endTime)
      if (begin && now < begin) return 'pending'
      if (end && now > end) return 'ended'
      return 'open'
    },
    buyText() {
      if (!this.isSeckill) return `¥${this.fen2yuan(this.voucher.payValue)} 立即购买`
      if (this.phaseOf === 'pending') return `${this.fmtTime(this.voucher.beginTime)} 开抢`
      if (this.phaseOf === 'ended') return '活动已结束'
      return `¥${this.fen2yuan(this.voucher.payValue)} 立即抢购`
    }
  },
  onLoad(options) {
    this.id = options && options.id
  },
  onShow() {
    if (!getToken()) {
      uni.reLaunch({ url: '/pages/user/login/login' })
      return
    }
    this.loadDetail()
    if (this.isSeckill) {
      this.timer = setInterval(() => {
        this.nowTs = Date.now()
      }, 1000)
    }
  },
  onHide() {
    if (this.timer) clearInterval(this.timer)
  },
  onUnload() {
    if (this.timer) clearInterval(this.timer)
  },
  methods: {
    async loadDetail() {
      this.loading = true
      try {
        this.voucher = (await getVoucher(this.id)) || {}
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    goShop(id) {
      uni.navigateTo({ url: `/pages/user/shop/shopDetail?id=${id}` })
    },
    async handleBuy() {
      if (this.buying) return
      if (this.isSeckill && this.phaseOf !== 'open') return
      this.buying = true
      try {
        const orderId = this.isSeckill
          ? await createSeckillOrder(this.id)   // 秒杀：异步落库，支付页自带重试
          : await buyVoucher(this.id)           // 普通：同步落库
        uni.navigateTo({ url: `/pages/user/pay/payConfirm?orderId=${orderId}` })
      } catch (e) {
        // toast 已统一处理（库存不足/限购等）
      } finally {
        this.buying = false
      }
    },
    fen2yuan(v) {
      const n = Number(v)
      return n ? (n / 100).toFixed(n % 100 === 0 ? 0 : 2) : '0'
    },
    fmtTime(t) {
      return (t || '').replace('T', ' ').slice(5, 16)
    },
    toTs(t) {
      if (!t) return 0
      return new Date(String(t).replace(' ', 'T')).getTime() || 0
    },
    coverOf(s) {
      return (s.images || '').split(',').filter(Boolean)[0] || '/static/logo.png'
    }
  }
}
</script>

<style lang="scss" scoped>
.vc-detail {
  min-height: 100vh;
  background: $brand-bg;
  padding: 24rpx 24rpx 60rpx;
}

.vc-hero {
  padding: 32rpx;

  &__top {
    display: flex;
    align-items: center;
    gap: 16rpx;
  }

  &__title {
    flex: 1;
    font-size: 20px;
    font-weight: 800;
    color: $text-main;
  }

  &__tag {
    padding: 6rpx 18rpx;
    border-radius: 999rpx;
    font-size: 12px;
    background: $brand-bg;
    color: $text-sub;

    &.is-seckill {
      background: $brand-accent;
      color: #ffffff;
    }
  }

  &__sub {
    display: block;
    margin-top: 12rpx;
    color: $text-sub;
    font-size: 13px;
  }

  &__price {
    display: flex;
    align-items: baseline;
    gap: 20rpx;
    margin-top: 24rpx;
  }

  &__pay {
    font-size: 30px;
    font-weight: 800;
    color: $brand-accent;
  }

  &__actual {
    color: $text-sub;
    font-size: 13px;
    text-decoration: line-through;
  }

  &__meta {
    display: flex;
    justify-content: space-between;
    margin-top: 16rpx;
    color: $text-sub;
    font-size: 12px;
    font-variant-numeric: tabular-nums;
  }

  &__stock {
    color: $brand-accent;
  }

  &__rules {
    margin-top: 24rpx;
    padding-top: 24rpx;
    border-top: 1px dashed $brand-line;
  }

  &__rules-label {
    display: block;
    margin-bottom: 8rpx;
    color: $text-main;
    font-size: 13px;
    font-weight: 600;
  }

  &__rules-text {
    color: $text-sub;
    font-size: 12px;
    line-height: 1.7;
    white-space: pre-wrap;
  }
}

.vc-buy {
  margin: 24rpx 0;
  height: 88rpx;
  line-height: 84rpx;
  border-radius: $radius-btn;
  background: $brand-primary;
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;

  &::after {
    border: none;
  }

  &.is-seckill {
    background: $brand-accent;
  }

  &[disabled],
  &.is-pending {
    opacity: 0.6;
    color: #ffffff;
  }
}

.vc-shops {
  &__title {
    display: block;
    margin: 8rpx 8rpx 20rpx;
    color: $text-main;
    font-size: 16px;
    font-weight: 700;
  }

  &__all {
    display: flex;
    align-items: center;
    gap: 20rpx;
    padding: 28rpx;
  }

  &__all-icon {
    font-size: 30px;
  }

  &__all-name {
    display: block;
    color: $text-main;
    font-size: 15px;
    font-weight: 600;
  }

  &__all-sub {
    display: block;
    margin-top: 6rpx;
    color: $text-sub;
    font-size: 12px;
  }
}

.vc-shop {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx;
  margin-bottom: 20rpx;

  &__img {
    width: 110rpx;
    height: 110rpx;
    border-radius: $radius-card;
    background: $brand-bg;
  }

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__name {
    display: block;
    color: $text-main;
    font-size: 15px;
    font-weight: 600;
  }

  &__addr {
    display: block;
    margin-top: 8rpx;
    color: $text-sub;
    font-size: 12px;
  }

  &__arrow {
    color: $text-sub;
    font-size: 20px;
  }
}

.ellipsis-1 {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
