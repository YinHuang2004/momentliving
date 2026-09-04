<template>
  <view class="workbench">
    <!-- 深抹茶绿顶栏 -->
    <NavBar :title="greeting" :back="false" bgColor="#4A6B4D" color="#FAF7F0" />

    <!-- 三张数据卡 -->
    <view class="workbench__stats">
      <view class="stat-card">
        <text class="stat-card__num stat-card__num--accent">{{ stats.today.pendingVerify }}</text>
        <view class="stat-card__label-row">
          <text class="stat-card__label">今日待核销</text>
          <view class="stat-card__badge" v-if="stats.today.pendingVerify > 0">需处理</view>
        </view>
      </view>
      <view class="stat-card">
        <text class="stat-card__num">{{ stats.today.verified }}</text>
        <text class="stat-card__label">今日已核销</text>
      </view>
      <view class="stat-card">
        <text class="stat-card__num stat-card__num--accent">{{ revenueText }}</text>
        <text class="stat-card__label">今日营收</text>
      </view>
    </view>

    <!-- 功能宫格 -->
    <view class="workbench__grid brand-card">
      <view class="workbench__grid-item" @click="goVerify">
        <text class="workbench__grid-icon">📷</text>
        <text class="workbench__grid-label">扫码核销</text>
      </view>
      <view class="workbench__grid-item" @click="goVerify">
        <text class="workbench__grid-icon">⌨️</text>
        <text class="workbench__grid-label">输入核销码</text>
      </view>
      <view class="workbench__grid-item" @click="goRecords">
        <text class="workbench__grid-icon">📋</text>
        <text class="workbench__grid-label">核销记录</text>
      </view>
      <view class="workbench__grid-item" @click="goMine">
        <text class="workbench__grid-icon">🏪</text>
        <text class="workbench__grid-label">我的店铺</text>
      </view>
      <view class="workbench__grid-item" @click="goRecords">
        <text class="workbench__grid-icon">📈</text>
        <text class="workbench__grid-label">店铺数据</text>
      </view>
      <view class="workbench__grid-item" @click="goShopApply">
        <text class="workbench__grid-icon">📝</text>
        <text class="workbench__grid-label">开店申请</text>
      </view>
      <view class="workbench__grid-item" @click="goAi">
        <text class="workbench__grid-icon">🤖</text>
        <text class="workbench__grid-label">AI 经营助手</text>
      </view>
      <view class="workbench__grid-item" @click="handleHelp">
        <text class="workbench__grid-icon">❓</text>
        <text class="workbench__grid-label">帮助</text>
      </view>
    </view>

    <!-- 最近核销 -->
    <view class="workbench__recent">
      <text class="workbench__recent-title">最近核销</text>
      <view class="workbench__recent-item brand-card" v-for="r in stats.recent" :key="r.orderId + r.verifyCodeTail">
        <view class="workbench__recent-info">
          <text class="workbench__recent-voucher">{{ r.voucherTitle }}</text>
          <text class="workbench__recent-meta">{{ formatTime(r.verifyTime) }} · 买家 {{ r.nickName || r.userId }}</text>
        </view>
        <StatBadge :text="verifyStatusText(r.status)" :type="r.status === 1 ? 'primary' : 'gray'" />
      </view>
      <EmptyView v-if="stats.recent.length === 0" text="今天还没有核销记录" />
    </view>

    <CustomTabBar :active="0" />
  </view>
</template>

<script>
import NavBar from '@/components/NavBar.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import StatBadge from '@/components/StatBadge.vue'
import EmptyView from '@/components/EmptyView.vue'
import { merchantStats } from '@/api/merchant.js'
import { getToken } from '@/utils/request.js'

/**
 * 商家工作台：三张数据卡（待核销/已核销/营收）+ 功能宫格 + 最近核销
 * 数据来自 GET /admin/verify/stats（按 admin.shop_id 绑定的店铺统计）
 */
export default {
  components: { NavBar, CustomTabBar, StatBadge, EmptyView },
  data() {
    return {
      stats: {
        today: { pendingVerify: 0, verified: 0, revenue: 0 },
        recent: []
      }
    }
  },
  computed: {
    greeting() {
      const hour = new Date().getHours()
      const word = hour < 12 ? '早上好' : hour < 18 ? '下午好' : '晚上好'
      const shopName = uni.getStorageSync('merchantInfo').name || ''
      return `${word}，${shopName || '商家'} 👋`
    },
    // 后端营收已是元（2 位小数），千分位展示
    revenueText() {
      const n = Number(this.stats.today.revenue) || 0
      return `¥${n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    }
  },
  onShow() {
    if (!getToken()) {
      uni.reLaunch({ url: '/pages/merchant/login/login' })
      return
    }
    this.loadStats()
  },
  methods: {
    async loadStats() {
      try {
        const res = await merchantStats()
        this.stats = {
          today: (res && res.today) || { pendingVerify: 0, verified: 0, revenue: 0 },
          recent: (res && res.recent) || []
        }
      } catch (e) {
        // toast 已统一处理（未绑定店铺会有明确提示）
      }
    },
    goVerify() {
      uni.redirectTo({ url: '/pages/merchant/verify/verify' })
    },
    goRecords() {
      uni.redirectTo({ url: '/pages/merchant/records/records' })
    },
    goMine() {
      uni.redirectTo({ url: '/pages/merchant/mine/mine' })
    },
    goShopApply() {
      uni.navigateTo({ url: '/pages/merchant/shopApply/apply' })
    },
    goAi() {
      uni.navigateTo({ url: '/pages/merchant/ai/assistant' })
    },
    handleHelp() {
      uni.showToast({ title: '用户出示 16 位核销码，扫码或手动输入即可核销', icon: 'none' })
    },
    verifyStatusText(status) {
      const map = { 0: '未核销', 1: '已核销', 2: '已作废' }
      return map[status] || '未知'
    },
    formatTime(t) {
      return (t || '').replace('T', ' ').slice(5, 16)
    }
  }
}
</script>

<style lang="scss" scoped>
.workbench {
  min-height: 100vh;
  background: $brand-bg;
  padding-bottom: 160rpx;
}

.workbench__stats {
  display: flex;
  gap: 20rpx;
  padding: 24rpx;
}

.stat-card {
  flex: 1;
  background: #ffffff;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
  padding: 28rpx 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;

  &__num {
    font-size: 26px;
    font-weight: 800;
    color: $text-main;

    &--accent {
      color: $brand-accent;
    }
  }

  &__label-row {
    display: flex;
    align-items: center;
    gap: 8rpx;
    margin-top: 8rpx;
  }

  &__label {
    color: $text-sub;
    font-size: 12px;
  }

  &__badge {
    padding: 0 10rpx;
    border-radius: $radius-btn;
    background: $brand-accent;
    color: $text-main;
    font-size: 10px;
    line-height: 28rpx;
  }
}

.workbench__grid {
  margin: 0 24rpx 24rpx;
  padding: 28rpx 0;
  display: flex;
  flex-wrap: wrap;

  &-item {
    width: 33.33%;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 20rpx 0;
  }

  &-icon {
    font-size: 26px;
  }

  &-label {
    margin-top: 10rpx;
    font-size: 13px;
    color: $text-main;
  }
}

.workbench__recent {
  padding: 0 24rpx;
}

.workbench__recent-title {
  font-size: 17px;
  font-weight: 700;
}

.workbench__recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 28rpx;
  margin-top: 16rpx;
}

.workbench__recent-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.workbench__recent-voucher {
  font-size: 14px;
  font-weight: 600;
}

.workbench__recent-meta {
  margin-top: 6rpx;
  color: $text-sub;
  font-size: 12px;
}
</style>
