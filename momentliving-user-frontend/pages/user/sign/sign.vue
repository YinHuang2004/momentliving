<template>
  <view class="sign-page">
    <NavBar title="签到" class="page-nav" />
    <!-- 签到卡 -->
    <view class="sign-card brand-card">
      <text class="sign-card__title">本月已签到</text>
      <view class="sign-card__count-row">
        <text class="sign-card__count">{{ count }}</text>
        <text class="sign-card__unit">天</text>
      </view>
      <button
        class="brand-btn sign-card__btn"
        :class="{ 'is-disabled': signed }"
        @click="handleSign"
      >{{ signed ? '今日已签到' : '立即签到' }}</button>
    </view>

    <!-- 每日积分卡：每天可领 10 积分，累计到账户（后续兑换/抵扣用） -->
    <view class="sign-card brand-card">
      <text class="sign-card__title">我的积分</text>
      <view class="sign-card__count-row">
        <text class="sign-card__count">{{ credits }}</text>
        <text class="sign-card__unit">分</text>
      </view>
      <button
        class="brand-btn sign-card__btn"
        :class="{ 'is-disabled': claimed }"
        @click="handleClaim"
      >{{ claimed ? '今日已领取' : '领取今日积分 +10' }}</button>
    </view>

    <!-- 日历（当前月） -->
    <view class="sign-calendar brand-card">
      <view class="sign-calendar__head">
        <text class="sign-calendar__month">{{ year }} 年 {{ month }} 月</text>
      </view>
      <view class="sign-calendar__week">
        <text class="sign-calendar__week-item" v-for="w in weeks" :key="w">{{ w }}</text>
      </view>
      <view class="sign-calendar__days">
        <view
          v-for="(day, i) in days"
          :key="i"
          class="sign-calendar__day"
          :class="{
            'is-empty': !day,
            'is-today': day === today,
            'is-signed': signedDays.includes(day)
          }"
        >
          <text v-if="day">{{ day }}</text>
        </view>
      </view>
      <text class="sign-calendar__tip">注：签到日历仅标注今天与累计天数，历史签到明细后端暂未提供</text>
    </view>
  </view>
</template>

<script>
import { sign, signCount, claimCredits, getCredits } from '@/api/user.js'

/**
 * 签到页：签到 + 本月签到天数 + 每日积分领取
 * 说明：后端仅提供 POST /user/sign 与 GET /user/sign/count（本月累计天数），
 * 无按日签到明细接口，日历中"历史签到打点"以累计天数近似展示（README 已知限制）；
 * 积分为独立功能：POST /user/credits/claim 每天领 10 分，GET /user/credits 查询余额与领取状态
 */
import NavBar from '@/components/NavBar.vue'
export default {
  components: { NavBar },
  data() {
    return {
      weeks: ['日', '一', '二', '三', '四', '五', '六'],
      count: 0,
      year: 0,
      month: 0,
      today: 0,
      signed: false,
      credits: 0,
      claimed: false
    }
  },
  computed: {
    days() {
      const firstDay = new Date(this.year, this.month - 1, 1).getDay()
      const dayCount = new Date(this.year, this.month, 0).getDate()
      const cells = Array(firstDay).fill(0)
      for (let d = 1; d <= dayCount; d++) cells.push(d)
      return cells
    },
    // 近似展示：本月已签 count 天，按从 1 号起连续标记
    signedDays() {
      return Array.from({ length: Math.min(this.count, 31) }, (_, i) => i + 1)
    }
  },
  onShow() {
    const now = new Date()
    this.year = now.getFullYear()
    this.month = now.getMonth() + 1
    this.today = now.getDate()
    this.loadCount()
    this.loadCredits()
  },
  methods: {
    async loadCount() {
      try {
        this.count = Number(await signCount()) || 0
      } catch (e) {
        // toast 已统一处理
      }
    },
    async loadCredits() {
      try {
        const data = await getCredits()
        this.credits = Number(data && data.credits) || 0
        this.claimed = !!(data && data.claimedToday)
      } catch (e) {
        // toast 已统一处理
      }
    },
    async handleClaim() {
      if (this.claimed) return
      try {
        this.credits = Number(await claimCredits()) || this.credits
        this.claimed = true
        uni.showToast({ title: '领取成功 +10 积分', icon: 'success' })
      } catch (e) {
        // 重复领取：request.js 已 toast 后端错误"今日积分已领取，明天再来吧"
        this.loadCredits()
      }
    },
    async handleSign() {
      try {
        await sign()
        this.signed = true
        uni.showToast({ title: '签到成功 +1 天', icon: 'success' })
        this.loadCount()
      } catch (e) {
        // 重复签到：request.js 已 toast 后端错误"今日已签到，明天再来吧"
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.sign-page {
  min-height: 100vh;
  padding: 24rpx;
}

.sign-card {
  padding: 48rpx;
  text-align: center;
  margin-bottom: 24rpx;
}

.sign-card__title {
  color: $text-sub;
  font-size: 14px;
}

.sign-card__count-row {
  margin: 16rpx 0 32rpx;
}

.sign-card__count {
  font-size: 56px;
  font-weight: 800;
  color: $brand-accent;
}

.sign-card__unit {
  font-size: 16px;
  color: $text-main;
  margin-left: 8rpx;
}

.sign-card__btn {
  width: 60%;
}

.sign-calendar {
  padding: 28rpx;
}

.sign-calendar__head {
  text-align: center;
  margin-bottom: 16rpx;
}

.sign-calendar__month {
  font-size: 17px;
  font-weight: 700;
}

.sign-calendar__week {
  display: flex;
  margin-bottom: 8rpx;
}

.sign-calendar__week-item {
  flex: 1;
  text-align: center;
  color: $text-sub;
  font-size: 12px;
}

.sign-calendar__days {
  display: flex;
  flex-wrap: wrap;
}

.sign-calendar__day {
  width: 14.28%;
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;

  &.is-today {
    text {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 56rpx;
      height: 56rpx;
      border-radius: 50%;
      border: 2px solid $brand-accent;
      color: $brand-accent;
      font-weight: 700;
    }
  }

  &.is-signed {
    text {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 56rpx;
      height: 56rpx;
      border-radius: 50%;
      background: $brand-primary;
      color: #ffffff;
    }
  }
}

.sign-calendar__tip {
  display: block;
  margin-top: 20rpx;
  color: $text-sub;
  font-size: 11px;
}

/* NavBar 通栏：抵消根容器 padding */
.page-nav {
  margin: -24rpx -24rpx 0;
}
</style>
