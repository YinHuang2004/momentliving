<template>
  <view class="ai-merchant">
    <!-- 功能切换 -->
    <view class="ai-merchant__tabs">
      <view class="ai-merchant__tab" :class="{ active: tab === 'analysis' }" @click="tab = 'analysis'">📊 经营分析</view>
      <view class="ai-merchant__tab" :class="{ active: tab === 'copy' }" @click="tab = 'copy'">✍️ 营销文案</view>
      <view class="ai-merchant__tab" :class="{ active: tab === 'intro' }" @click="tab = 'intro'">🏪 店铺介绍</view>
    </view>

    <scroll-view class="ai-merchant__body" scroll-y>
      <!-- 经营分析 -->
      <view v-if="tab === 'analysis'" class="ai-merchant__card">
        <view class="ai-merchant__desc">基于今日核销、营收、店铺评分等真实数据，AI 生成经营分析报告与改进建议。</view>
        <button class="ai-merchant__btn" :disabled="loading" @click="runAnalysis">{{ loading ? '分析中…' : '生成经营分析' }}</button>
      </view>

      <!-- 营销文案 -->
      <view v-if="tab === 'copy'" class="ai-merchant__card">
        <view class="ai-merchant__label">券信息（必填）</view>
        <input class="ai-merchant__input" v-model="copy.voucherDesc" placeholder="如：满100减30团购券" />
        <view class="ai-merchant__label">核心卖点（选填）</view>
        <input class="ai-merchant__input" v-model="copy.sellingPoint" placeholder="如：招牌双人套餐" />
        <button class="ai-merchant__btn" :disabled="loading" @click="runCopywriting">{{ loading ? '生成中…' : '生成文案' }}</button>
      </view>

      <!-- 店铺介绍 -->
      <view v-if="tab === 'intro'" class="ai-merchant__card">
        <view class="ai-merchant__desc">根据店铺名称、区域、人均、评分，AI 生成 100-150 字的店铺介绍文案。</view>
        <button class="ai-merchant__btn" :disabled="loading" @click="runIntro">{{ loading ? '生成中…' : '生成店铺介绍' }}</button>
      </view>

      <!-- 结果展示 -->
      <view class="ai-merchant__result" v-if="result">
        <view class="ai-merchant__result-title">AI 结果</view>
        <text class="ai-merchant__result-text">{{ result }}</text>
        <view class="ai-merchant__copy" @click="copyResult">复制全文</view>
      </view>
      <view class="ai-merchant__disclaimer">AI 生成内容仅供参考，请核对后再使用</view>
    </scroll-view>
  </view>
</template>

<script>
import { merchantAnalysis, merchantCopywriting, merchantShopIntro } from '@/api/ai.js'

export default {
  data() {
    return {
      tab: 'analysis',
      loading: false,
      result: '',
      copy: { voucherDesc: '', sellingPoint: '' }
    }
  },
  methods: {
    switchTab(t) {
      this.tab = t
      this.result = ''
    },
    async runAnalysis() {
      this.loading = true
      this.result = ''
      try {
        this.result = await merchantAnalysis()
      } catch (e) { /* toast 已统一处理 */ }
      this.loading = false
    },
    async runCopywriting() {
      if (!this.copy.voucherDesc.trim()) {
        uni.showToast({ title: '请填写券信息', icon: 'none' })
        return
      }
      this.loading = true
      this.result = ''
      try {
        this.result = await merchantCopywriting(this.copy.voucherDesc, this.copy.sellingPoint)
      } catch (e) { /* toast 已统一处理 */ }
      this.loading = false
    },
    async runIntro() {
      this.loading = true
      this.result = ''
      try {
        this.result = await merchantShopIntro()
      } catch (e) { /* toast 已统一处理 */ }
      this.loading = false
    },
    copyResult() {
      if (!this.result) return
      uni.setClipboardData({
        data: this.result,
        success: () => uni.showToast({ title: '已复制', icon: 'success' })
      })
    }
  }
}
</script>

<style scoped>
.ai-merchant { display: flex; flex-direction: column; height: 100vh; background: #f6f6f2; }
.ai-merchant__tabs { display: flex; background: #6b8e5a; padding: 12rpx 16rpx; gap: 12rpx; }
.ai-merchant__tab {
  flex: 1; text-align: center; color: #e8efe2; font-size: 26rpx;
  padding: 12rpx 0; border-radius: 12rpx;
}
.ai-merchant__tab.active { background: #fff; color: #3a4a34; font-weight: 600; }
.ai-merchant__body { flex: 1; padding: 24rpx; box-sizing: border-box; }
.ai-merchant__card {
  background: #fff; border-radius: 20rpx; padding: 30rpx;
  display: flex; flex-direction: column; gap: 20rpx;
}
.ai-merchant__desc { font-size: 26rpx; color: #6a7563; line-height: 1.6; }
.ai-merchant__label { font-size: 26rpx; color: #3a4a34; }
.ai-merchant__input {
  background: #f2f2ec; border-radius: 12rpx; padding: 18rpx 24rpx; font-size: 28rpx;
}
.ai-merchant__btn {
  background: #6b8e5a; color: #fff; font-size: 28rpx; border-radius: 999rpx;
}
.ai-merchant__btn[disabled] { background: #c5cdbf; color: #fff; }
.ai-merchant__result {
  background: #fff; border-radius: 20rpx; padding: 30rpx; margin-top: 24rpx;
}
.ai-merchant__result-title { font-size: 28rpx; font-weight: 600; color: #3a4a34; margin-bottom: 16rpx; }
.ai-merchant__result-text { font-size: 27rpx; color: #444; line-height: 1.7; white-space: pre-wrap; }
.ai-merchant__copy {
  margin-top: 24rpx; text-align: center; color: #6b8e5a; font-size: 26rpx;
  border-top: 1rpx solid #eee; padding-top: 20rpx;
}
.ai-merchant__disclaimer { text-align: center; font-size: 20rpx; color: #b0b8a8; padding: 30rpx 0; }
</style>
