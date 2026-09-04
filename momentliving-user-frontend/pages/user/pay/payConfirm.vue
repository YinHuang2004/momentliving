<template>
  <view class="pay-confirm">
    <NavBar title="确认支付" class="page-nav" />
    <template v-if="order">
    <!-- 到期提示 -->
    <view class="pay-confirm__expire">
      <text class="pay-confirm__no">订单号 NO.{{ order.id }}</text>
    </view>

    <!-- 商品信息 -->
    <view class="pay-confirm__goods brand-card" v-if="voucher">
      <image class="pay-confirm__img" :src="cover" mode="aspectFill" />
      <view class="pay-confirm__info">
        <text class="pay-confirm__title">{{ voucher.title }}</text>
        <text class="pay-confirm__sub">数量 ×1 · 到店出示核销码使用</text>
        <text class="pay-confirm__price">¥{{ payValueText }}</text>
      </view>
    </view>

    <!-- 支付方式 -->
    <view class="pay-confirm__ways brand-card">
      <view
        class="pay-confirm__way"
        v-for="w in payWays"
        :key="w.type"
        :class="{ 'is-disabled': w.disabled }"
        @click="selectWay(w)"
      >
        <view class="pay-confirm__radio" :class="{ 'is-checked': payType === w.type }"></view>
        <text class="pay-confirm__way-name">{{ w.name }}</text>
        <text class="pay-confirm__way-tag" v-if="w.tag">{{ w.tag }}</text>
      </view>
    </view>

    <button class="brand-btn pay-confirm__submit" :class="{ 'is-disabled': paying }" @click="handlePay">
      {{ paying ? '支付处理中…' : `确认支付 ¥${payValueText}` }}
    </button>

    <text class="pay-confirm__tip">· 演示环境推荐使用「模拟支付」走通全流程</text>
  </template>
  </view>
</template>

<script>
import { getOrder, getVoucher, createPay, payStatus } from '@/api/voucher.js'

/**
 * 支付确认页：支付方式单选（支付宝=2 / 模拟支付=3；微信=1 后端未开通）
 * 发起支付后轮询 pay/status 直到已支付，然后跳订单详情看核销码
 */
import NavBar from '@/components/NavBar.vue'
export default {
  components: { NavBar },
  data() {
    return {
      order: null,
      voucher: null,
      payType: 3, // 默认模拟支付，演示环境可直接跑通
      paying: false,
      payWays: [
        { type: 1, name: '微信支付', disabled: true, tag: '未开通' },
        { type: 2, name: '支付宝', disabled: false, tag: '沙箱' },
        { type: 3, name: '模拟支付', disabled: false, tag: '演示' }
      ]
    }
  },
  computed: {
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
    this.orderId = options.orderId
    // 秒杀下单经 RabbitMQ 异步落库，订单可能尚未入库，带重试查询
    this.fetchOrder(5)
  },
  onShow() {
    // 从其他页面返回时刷新状态（首次加载由 onLoad 负责）
    if (this.order) {
      this.fetchOrder(0)
    }
  },
  onUnload() {
    this.stopPolling()
    if (this._retryTimer) clearTimeout(this._retryTimer)
  },
  methods: {
    /**
     * 查询订单并带出券信息；订单尚未落库（异步下单）时按 800ms 间隔重试
     */
    async fetchOrder(retry) {
      let order = null
      try {
        order = await getOrder(this.orderId)
      } catch (e) {
        if (retry > 0) {
          this._retryTimer = setTimeout(() => this.fetchOrder(retry - 1), 800)
        }
        return
      }
      this.order = order
      try {
        if (order.voucherId) {
          this.voucher = await getVoucher(order.voucherId)
        }
      } catch (e) {
        // 券信息缺失不阻塞支付页
      }
      // 已支付订单直接跳详情
      if (order.status !== 0) {
        this.goDetail()
      }
    },
    selectWay(way) {
      if (way.disabled) {
        uni.showToast({ title: `${way.name}暂未开通`, icon: 'none' })
        return
      }
      this.payType = way.type
    },
    async handlePay() {
      if (this.paying) return
      this.paying = true
      try {
        await createPay(this.order.id, this.payType)
        uni.showLoading({ title: '支付确认中…' })
        this.startPolling()
      } catch (e) {
        this.paying = false
      }
    },
    // 每 2s 轮询支付状态，最长 60s
    startPolling() {
      this.stopPolling()
      let times = 0
      this.pollTimer = setInterval(async () => {
        times++
        try {
          const res = await payStatus(this.order.id)
          if (res && res.orderStatus === 1) {
            this.stopPolling()
            uni.hideLoading()
            this.paying = false
            uni.showToast({ title: '支付成功', icon: 'success' })
            setTimeout(() => this.goDetail(), 600)
            return
          }
          if (times > 30) {
            this.stopPolling()
            uni.hideLoading()
            this.paying = false
            uni.showToast({ title: '支付确认超时，请稍后在订单页查看', icon: 'none' })
          }
        } catch (e) {
          // 轮询中单次失败忽略，继续等
        }
      }, 2000)
    },
    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    },
    goDetail() {
      uni.redirectTo({ url: `/pages/user/order/orderDetail?id=${this.order.id}` })
    }
  }
}
</script>

<style lang="scss" scoped>
.pay-confirm {
  min-height: 100vh;
  padding: 24rpx;
}

.pay-confirm__expire {
  padding: 8rpx 8rpx 24rpx;
}

.pay-confirm__no {
  color: $text-sub;
  font-size: 13px;
}

.pay-confirm__goods {
  display: flex;
  padding: 24rpx;
  margin-bottom: 24rpx;
}

.pay-confirm__img {
  width: 150rpx;
  height: 150rpx;
  border-radius: 16rpx;
  background: $brand-bg-2;
  flex-shrink: 0;
}

.pay-confirm__info {
  flex: 1;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.pay-confirm__title {
  font-size: 16px;
  font-weight: 600;
}

.pay-confirm__sub {
  color: $text-sub;
  font-size: 12px;
}

.pay-confirm__price {
  color: $brand-accent;
  font-size: 22px;
  font-weight: 800;
}

.pay-confirm__ways {
  padding: 12rpx 28rpx;
  margin-bottom: 40rpx;
}

.pay-confirm__way {
  display: flex;
  align-items: center;
  padding: 28rpx 0;
  border-bottom: 1px solid $brand-line;

  &:last-child {
    border-bottom: none;
  }

  &.is-disabled {
    opacity: 0.5;
  }
}

.pay-confirm__radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2px solid $brand-line;
  margin-right: 20rpx;

  &.is-checked {
    border: 10px solid $brand-primary;
    background: #ffffff;
  }
}

.pay-confirm__way-name {
  flex: 1;
  font-size: 15px;
}

.pay-confirm__way-tag {
  color: $brand-accent;
  font-size: 12px;
}

.pay-confirm__submit {
  margin-bottom: 20rpx;
}

.pay-confirm__tip {
  display: block;
  text-align: center;
  color: $text-sub;
  font-size: 12px;
}

/* NavBar 通栏：抵消根容器 padding */
.page-nav {
  margin: -24rpx -24rpx 0;
}
</style>
