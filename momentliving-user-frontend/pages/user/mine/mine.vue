<template>
  <view class="mine-page">
    <!-- 个人信息卡 -->
    <view class="mine-head">
      <view class="mine-head__status"></view>
      <view class="mine-head__user" @click="goProfile">
        <image class="mine-head__avatar" :src="userInfo.images || '/static/logo.png'" mode="aspectFill" />
        <view class="mine-head__info">
          <view class="mine-head__name-row">
            <text class="mine-head__nick">{{ userInfo.nickName || '未登录' }}</text>
            <text class="mine-head__badge" v-if="isVip">探店达人</text>
          </view>
          <text class="mine-head__id">ID: {{ userInfo.id || '--' }}</text>
        </view>
      </view>
      <!-- 数据行：关注 / 粉丝 / 获赞 -->
      <view class="mine-head__stats">
        <view class="mine-head__stat" @click="goFollow(1)">
          <text class="mine-head__stat-num">{{ followeeCount }}</text>
          <text class="mine-head__stat-label">关注</text>
        </view>
        <view class="mine-head__stat" @click="goFollow(2)">
          <text class="mine-head__stat-num">{{ fansCount }}</text>
          <text class="mine-head__stat-label">粉丝</text>
        </view>
        <view class="mine-head__stat">
          <text class="mine-head__stat-num">{{ likesCountText }}</text>
          <text class="mine-head__stat-label">获赞</text>
        </view>
      </view>
    </view>

    <!-- 功能宫格（发布/我的博客入口在"博客"Tab 右上角与左上角头像） -->
    <view class="mine-grid brand-card">
      <view class="mine-grid__item" @click="goOrder()">
        <text class="mine-grid__icon">💳</text>
        <text class="mine-grid__label">我的订单</text>
      </view>
      <view class="mine-grid__item" @click="goMyVoucher">
        <text class="mine-grid__icon">🎫</text>
        <text class="mine-grid__label">优惠券</text>
      </view>
      <view class="mine-grid__item" @click="goOrder(1)">
        <text class="mine-grid__icon">✅</text>
        <text class="mine-grid__label">已核销</text>
      </view>
      <view class="mine-grid__item" @click="goSign">
        <text class="mine-grid__icon">📅</text>
        <text class="mine-grid__label">签到</text>
      </view>
      <view class="mine-grid__item" @click="goShopFavorite">
        <text class="mine-grid__icon">⭐</text>
        <text class="mine-grid__label">收藏店铺</text>
      </view>
      <view class="mine-grid__item" @click="goProfile">
        <text class="mine-grid__icon">⚙️</text>
        <text class="mine-grid__label">编辑资料</text>
      </view>
      <view class="mine-grid__item" @click="goAi">
        <text class="mine-grid__icon">🤖</text>
        <text class="mine-grid__label">AI 助手</text>
      </view>
      <view class="mine-grid__item" @click="handleLogout">
        <text class="mine-grid__icon">🚪</text>
        <text class="mine-grid__label">退出登录</text>
      </view>
    </view>
  </view>
</template>

<script>
import { getMe, followList, logout } from '@/api/user.js'
import { myBlogs } from '@/api/blog.js'
import { clearToken } from '@/utils/request.js'
import { closeWS } from '@/utils/websocket.js'

/**
 * Tab5 我的：资料卡 + 关注/粉丝/获赞 + 功能宫格
 * 说明：获赞数 = 我的博客列表 liked 求和（首页数据，后端暂无汇总接口）
 */
export default {
  data() {
    return {
      userInfo: {},
      followeeCount: 0,
      fansCount: 0,
      likesCount: 0
    }
  },
  computed: {
    isVip() {
      return this.fansCount >= 100
    },
    likesCountText() {
      const n = this.likesCount
      return n >= 1000 ? `${(n / 1000).toFixed(1)}k` : String(n)
    }
  },
  onShow() {
    this.loadAll()
  },
  methods: {
    async loadAll() {
      try {
        this.userInfo = (await getMe()) || {}
        // 关注/粉丝数量 = 列表长度；获赞 = 我的博客 liked 求和
        const [followees, fans, blogs] = await Promise.all([
          followList(1).catch(() => []),
          followList(2).catch(() => []),
          myBlogs(this.userInfo.id, 1).catch(() => [])
        ])
        this.followeeCount = (followees || []).length
        this.fansCount = (fans || []).length
        this.likesCount = (blogs || []).reduce((sum, b) => sum + (Number(b.liked) || 0), 0)
      } catch (e) {
        // toast 已统一处理
      }
    },
    goProfile() {
      uni.navigateTo({ url: '/pages/user/profile/profile' })
    },
    goFollow(type) {
      uni.navigateTo({ url: `/pages/user/follow/followList?type=${type}` })
    },
    goOrder(status) {
      uni.navigateTo({ url: '/pages/user/order/orderList' })
    },
    goMyVoucher() {
      uni.navigateTo({ url: '/pages/user/voucher/myVoucher' })
    },
    goSign() {
      uni.navigateTo({ url: '/pages/user/sign/sign' })
    },
    goShopFavorite() {
      uni.navigateTo({ url: '/pages/user/favorite/shopFavorite' })
    },
    goAi() {
      uni.navigateTo({ url: '/pages/user/ai/chat' })
    },
    async handleLogout() {
      try {
        await logout()
      } catch (e) {
        // 即使接口失败也清本地登录态
      }
      closeWS()   // 旧账号的 WS 连接必须断开，否则下个账号复用会被记成旧账号发的消息
      clearToken()
      uni.removeStorageSync('refreshToken')
      uni.removeStorageSync('userInfo')
      uni.reLaunch({ url: '/pages/user/login/login' })
    }
  }
}
</script>

<style lang="scss" scoped>
.mine-page {
  min-height: 100vh;
  padding-bottom: 60rpx;
}

.mine-head {
  background: $brand-primary;
  padding-bottom: 40rpx;

  &__status {
    height: var(--status-bar-height);
  }

  &__user {
    display: flex;
    align-items: center;
    padding: 40rpx 32rpx 24rpx;
  }

  &__avatar {
    width: 128rpx;
    height: 128rpx;
    border-radius: 50%;
    border: 3px solid rgba(255, 255, 255, 0.6);
    background: rgba(255, 255, 255, 0.3);
    margin-right: 24rpx;
  }

  &__info {
    flex: 1;
  }

  &__name-row {
    display: flex;
    align-items: center;
    gap: 16rpx;
  }

  &__nick {
    font-size: 20px;
    font-weight: 700;
    color: #ffffff;
  }

  &__badge {
    padding: 2rpx 16rpx;
    border-radius: $radius-btn;
    background: $brand-accent;
    color: $text-main;
    font-size: 11px;
  }

  &__id {
    margin-top: 8rpx;
    color: rgba(255, 255, 255, 0.8);
    font-size: 12px;
  }

  &__stats {
    display: flex;
    padding: 16rpx 48rpx 0;
  }

  &__stat {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  &__stat-num {
    font-size: 20px;
    font-weight: 800;
    color: #ffffff;
  }

  &__stat-label {
    margin-top: 4rpx;
    color: rgba(255, 255, 255, 0.85);
    font-size: 12px;
  }
}

.mine-grid {
  margin: 24rpx;
  padding: 24rpx 0;
  display: flex;
  flex-wrap: wrap;

  &__item {
    width: 25%;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 20rpx 0;
  }

  &__icon {
    font-size: 24px;
  }

  &__label {
    margin-top: 10rpx;
    font-size: 12px;
    color: $text-main;
  }
}
</style>
