<template>
  <view class="verify-page">
    <NavBar title="核销台" :back="false" bgColor="#4A6B4D" color="#FAF7F0" />

    <!-- 扫码取景框 -->
    <view class="scan-box">
      <view class="scan-frame">
        <view class="scan-corner scan-corner--tl"></view>
        <view class="scan-corner scan-corner--tr"></view>
        <view class="scan-corner scan-corner--bl"></view>
        <view class="scan-corner scan-corner--br"></view>
        <view class="scan-line"></view>
      </view>
      <text class="scan-box__tip">将用户出示的核销码对准框内</text>
      <!-- #ifndef H5 -->
      <button class="brand-btn-plain scan-box__btn" @click="handleScan">打开扫码</button>
      <!-- #endif -->
    </view>

    <!-- 手动输入卡片：16 位核销码，分 4 段 × 4 位 -->
    <view class="input-card brand-card">
      <text class="input-card__title">手动输入核销码</text>
      <view class="input-card__groups">
        <input
          v-for="(_, i) in 4"
          :key="i"
          class="input-card__seg"
          v-model="segments[i]"
          type="text"
          maxlength="4"
          :focus="focusIndex === i"
          @input="onSegInput(i)"
          placeholder="0000"
        />
      </view>
      <button
        class="brand-btn input-card__submit"
        :class="{ 'is-disabled': !codeComplete }"
        @click="handleCheck"
      >核对订单</button>
    </view>

    <!-- 核对卡：先核对订单信息，确认后才真正核销 -->
    <view class="check-mask" v-if="preview" @click="preview = null">
      <view class="check-panel brand-card" @click.stop>
        <text class="check-panel__title">请核对订单信息</text>
        <view class="check-panel__row">
          <text class="check-panel__label">券名</text>
          <text class="check-panel__value">{{ preview.voucherTitle || '—' }}</text>
        </view>
        <view class="check-panel__row">
          <text class="check-panel__label">买家</text>
          <text class="check-panel__value">{{ preview.nickName || ('用户' + preview.userId) }}</text>
        </view>
        <view class="check-panel__row">
          <text class="check-panel__label">金额</text>
          <text class="check-panel__value check-panel__value--accent">¥{{ payText }}</text>
        </view>
        <view class="check-panel__row">
          <text class="check-panel__label">状态</text>
          <text class="check-panel__value">{{ statusText }}</text>
        </view>
        <button class="brand-btn check-panel__confirm" :disabled="verifying" @click="doVerify">确认核销</button>
        <button class="brand-btn-plain check-panel__cancel" @click="cancelCheck">取消</button>
      </view>
    </view>

    <!-- 最近核销快捷记录 -->
    <view class="recent brand-card" v-if="recent.length > 0">
      <text class="recent__title">最近核销</text>
      <view class="recent__item" v-for="(r, i) in recent" :key="i">
        <text class="recent__voucher">{{ r.voucherTitle }}</text>
        <text class="recent__time">{{ formatTime(r.verifyTime) }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/NavBar.vue'
import { verifyByCode, getVerifyPreview, merchantStats } from '@/api/merchant.js'
import { getToken } from '@/utils/request.js'

/**
 * 核销台：扫码（App/小程序，H5 提示）+ 手动输入 4 段 × 4 位 16 位核销码
 * 两步核销：输入/扫码得码 → 按码查订单预览（券名/买家/金额/状态）→ 商家确认才真正核销
 */
export default {
  components: { NavBar },
  data() {
    return {
      segments: ['', '', '', ''],
      focusIndex: 0,
      recent: [],
      preview: null,
      verifying: false
    }
  },
  computed: {
    codeComplete() {
      return this.segments.every((s) => s.length === 4)
    },
    payText() {
      const v = Number(this.preview && this.preview.payValue)
      return v ? (v / 100).toFixed(v % 100 === 0 ? 0 : 2) : '0'
    },
    statusText() {
      const p = this.preview
      if (!p) return ''
      if (p.verifyStatus === 1) return '已核销（不可重复核销）'
      if (p.verifyStatus === 2) return '已作废（无法核销）'
      const map = { 0: '待支付', 1: '已支付（可核销）', 2: '已核销', 3: '已退款', 4: '已关闭' }
      return map[p.status] || `未知状态(${p.status})`
    }
  },
  onShow() {
    if (!getToken()) {
      uni.reLaunch({ url: '/pages/merchant/login/login' })
      return
    }
    this.loadRecent()
  },
  methods: {
    async loadRecent() {
      try {
        const res = await merchantStats()
        this.recent = ((res && res.recent) || []).slice(0, 3)
      } catch (e) {
        // toast 已统一处理
      }
    },
    // 单段输满 4 位自动跳下一段
    onSegInput(i) {
      if (this.segments[i].length >= 4 && i < 3) {
        this.focusIndex = i + 1
      }
    },
    handleScan() {
      // #ifndef H5
      uni.scanCode({
        success: (res) => {
          const code = (res.result || '').trim()
          if (code.length === 16) {
            this.checkCode(code)
          } else {
            uni.showToast({ title: '识别结果不是 16 位核销码', icon: 'none' })
          }
        }
      })
      // #endif
      // #ifdef H5
      uni.showToast({ title: 'H5 端不支持扫码，请手动输入', icon: 'none' })
      // #endif
    },
    handleCheck() {
      if (!this.codeComplete) {
        uni.showToast({ title: '请输入完整的 16 位核销码', icon: 'none' })
        return
      }
      this.checkCode(this.segments.join('').toUpperCase())
    },
    /** 第一步：按码查订单预览，弹核对卡（不改状态） */
    async checkCode(code) {
      try {
        this.preview = await getVerifyPreview(code)
      } catch (e) {
        // 码无效/订单缺失：toast 已统一处理
      }
    },
    cancelCheck() {
      this.preview = null
      this.resetInput()
    },
    async doVerify() {
      const code = this.segments.join('').toUpperCase()
      this.verifying = true
      try {
        await verifyByCode(code)
        // 成功弹层
        uni.showModal({
          title: '核销成功 ✅',
          content: `核销码 ${code.slice(0, 4)} · ${code.slice(4, 8)} · ${code.slice(8, 12)} · ${code.slice(12, 16)} 已完成核销`,
          showCancel: false,
          confirmText: '完成',
          confirmColor: '#6B8E5A',
          success: () => {
            this.preview = null
            this.resetInput()
            this.loadRecent()
          }
        })
      } catch (e) {
        // 失败弹层（已被核销/核销码错误等原因由 toast 显示）
        uni.showModal({
          title: '核销失败 ❌',
          content: e.message || '该券已被核销或核销码错误',
          showCancel: false,
          confirmText: '重新核销',
          confirmColor: '#D4A574',
          success: () => {
            this.preview = null
            this.resetInput()
          }
        })
      } finally {
        this.verifying = false
      }
    },
    resetInput() {
      this.segments = ['', '', '', '']
      this.focusIndex = 0
    },
    formatTime(t) {
      return (t || '').replace('T', ' ').slice(5, 16)
    }
  }
}
</script>

<style lang="scss" scoped>
.verify-page {
  min-height: 100vh;
  background: $brand-bg;
  padding-bottom: 40rpx;
}

.scan-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 0 24rpx;

  &__tip {
    margin-top: 24rpx;
    color: $text-sub;
    font-size: 13px;
  }

  &__btn {
    margin-top: 24rpx;
    height: 44px;
    line-height: 42px;
    padding: 0 48rpx;
  }
}

.scan-frame {
  position: relative;
  width: 320rpx;
  height: 320rpx;

  .scan-corner {
    position: absolute;
    width: 56rpx;
    height: 56rpx;
    border: 6rpx solid $brand-primary;
  }

  .scan-corner--tl { top: 0; left: 0; border-right: none; border-bottom: none; border-radius: 12rpx 0 0 0; }
  .scan-corner--tr { top: 0; right: 0; border-left: none; border-bottom: none; border-radius: 0 12rpx 0 0; }
  .scan-corner--bl { bottom: 0; left: 0; border-right: none; border-top: none; border-radius: 0 0 0 12rpx; }
  .scan-corner--br { bottom: 0; right: 0; border-left: none; border-top: none; border-radius: 0 0 12rpx 0; }

  .scan-line {
    position: absolute;
    top: 50%;
    left: 20rpx;
    right: 20rpx;
    height: 4rpx;
    border-radius: 4rpx;
    background: $brand-accent;
    animation: scan-move 2.4s ease-in-out infinite;
  }
}

@keyframes scan-move {
  0%, 100% { top: 20%; }
  50% { top: 76%; }
}

.input-card {
  margin: 24rpx;
  padding: 32rpx;

  &__title {
    font-size: 16px;
    font-weight: 700;
  }

  &__groups {
    display: flex;
    justify-content: space-between;
    margin: 28rpx 0;
  }

  &__seg {
    width: 140rpx;
    height: 96rpx;
    border: 1px solid $brand-line;
    border-radius: 16rpx;
    background: #ffffff;
    text-align: center;
    font-size: 20px;
    font-weight: 700;
    letter-spacing: 2px;
    text-transform: uppercase;
  }

  &__submit {
    width: 100%;
  }
}

.check-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}

.check-panel {
  width: 100%;
  border-radius: $radius-card $radius-card 0 0;
  padding: 32rpx 28rpx calc(40rpx + env(safe-area-inset-bottom));
}

.check-panel__title {
  display: block;
  text-align: center;
  font-size: 17px;
  font-weight: 700;
  margin-bottom: 20rpx;
}

.check-panel__row {
  display: flex;
  padding: 20rpx 0;
  border-bottom: 1px solid $brand-line;
}

.check-panel__label {
  width: 140rpx;
  color: $text-sub;
  font-size: 14px;
}

.check-panel__value {
  flex: 1;
  font-size: 15px;
  color: $text-main;

  &--accent {
    color: $brand-accent;
    font-weight: 700;
  }
}

.check-panel__confirm {
  margin-top: 28rpx;
  width: 100%;
}

.check-panel__cancel {
  margin-top: 20rpx;
  width: 100%;
}

.recent {
  margin: 0 24rpx;
  padding: 24rpx 28rpx;

  &__title {
    font-size: 15px;
    font-weight: 700;
  }

  &__item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 18rpx 0;
    border-bottom: 1px solid $brand-line;

    &:last-child {
      border-bottom: none;
    }
  }

  &__voucher {
    font-size: 13px;
    color: $text-main;
  }

  &__time {
    font-size: 12px;
    color: $text-sub;
  }
}
</style>
