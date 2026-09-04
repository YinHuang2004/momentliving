<template>
  <view class="apply-page">
    <NavBar title="" class="page-nav" />
    <view class="apply-hero">
      <text class="apply-hero__title">商家入驻申请</text>
      <text class="apply-hero__sub">提交资料 → 平台管理员审核 → 审核通过自动开通账号与店铺</text>
    </view>

    <view class="apply-form">
      <view class="apply-form__item">
        <text class="apply-form__label">店铺名称 *</text>
        <input class="apply-form__input" v-model="form.shopName" placeholder="如：巷子咖啡"
               placeholder-class="apply-form__placeholder" />
      </view>
      <view class="apply-form__item">
        <text class="apply-form__label">店铺地址</text>
        <input class="apply-form__input" v-model="form.address" placeholder="如：朝阳区南锣鼓巷 12 号"
               placeholder-class="apply-form__placeholder" />
      </view>
      <view class="apply-form__item">
        <text class="apply-form__label">联系电话 *</text>
        <input class="apply-form__input" v-model="form.contactPhone" type="number" maxlength="11"
               placeholder="方便平台与你联系" placeholder-class="apply-form__placeholder" />
      </view>
      <view class="apply-form__item">
        <text class="apply-form__label">登录账号 *</text>
        <input class="apply-form__input" v-model="form.username" placeholder="4~32 位，审核通过即登录账号"
               placeholder-class="apply-form__placeholder" />
      </view>
      <view class="apply-form__item">
        <text class="apply-form__label">登录密码 *</text>
        <input class="apply-form__input" v-model="form.password" :password="true" placeholder="至少 6 位"
               placeholder-class="apply-form__placeholder" />
      </view>

      <button class="brand-btn apply-form__submit" :class="{ 'is-disabled': !canSubmit }" @click="handleSubmit">
        提交申请
      </button>
      <text class="apply-form__tip">提交后由平台管理员审核；被拒绝可修改后重新提交，审核通过即可用本账号登录商家端</text>
    </view>
  </view>
</template>

<script>
import { applyMerchant } from '@/api/merchant.js'

/**
 * 商家入驻申请页（公开页面，无需登录）
 * 提交到 POST /merchant/apply → merchant_apply 表（status=0 待审核）
 * 平台管理员审核通过后：自动创建 shop 记录 + merchant 商家账号(shop_id=新店铺)
 */
import NavBar from '@/components/NavBar.vue'
export default {
  components: { NavBar },
  data() {
    return {
      form: {
        shopName: '',
        address: '',
        contactPhone: '',
        username: '',
        password: ''
      },
      submitting: false
    }
  },
  computed: {
    canSubmit() {
      const f = this.form
      return !!f.shopName.trim() && /^1[3-9]\d{9}$/.test(f.contactPhone)
        && f.username.trim().length >= 4 && f.password.length >= 6
    }
  },
  methods: {
    async handleSubmit() {
      if (this.submitting) return
      if (!/^1[3-9]\d{9}$/.test(this.form.contactPhone)) {
        uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
        return
      }
      this.submitting = true
      try {
        await applyMerchant({
          shopName: this.form.shopName.trim(),
          address: this.form.address.trim(),
          contactPhone: this.form.contactPhone.trim(),
          username: this.form.username.trim(),
          password: this.form.password
        })
        uni.showToast({ title: '申请已提交，等待平台审核', icon: 'none' })
        setTimeout(() => uni.navigateBack(), 1200)
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.apply-page {
  min-height: 100vh;
  background: $brand-bg;
  padding: 24rpx;
}

.apply-hero {
  padding: 40rpx 24rpx;
  margin-bottom: 20rpx;

  &__title {
    display: block;
    font-size: 22px;
    font-weight: 700;
    color: $text-main;
  }

  &__sub {
    display: block;
    margin-top: 12rpx;
    color: $text-sub;
    font-size: 13px;
  }
}

.apply-form {
  background: #ffffff;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
  padding: 32rpx;

  &__item {
    margin-bottom: 28rpx;
  }

  &__label {
    display: block;
    margin-bottom: 12rpx;
    color: $text-main;
    font-size: 14px;
    font-weight: 600;
  }

  &__input {
    height: 88rpx;
    padding: 0 24rpx;
    border: 1px solid $brand-line;
    border-radius: 999rpx;
    font-size: 14px;
    background: $brand-bg;
  }

  &__submit {
    margin-top: 16rpx;
  }

  &__tip {
    display: block;
    margin-top: 24rpx;
    color: $text-sub;
    font-size: 12px;
    line-height: 1.6;
  }
}

.apply-form__placeholder {
  color: $text-sub;
}

/* NavBar 通栏：抵消根容器 padding */
.page-nav {
  margin: -24rpx -24rpx 0;
}
</style>
