<template>
  <view class="sapply-page">
    <NavBar title="开店申请" fallback="/pages/merchant/workbench/workbench" class="page-nav" />

    <!-- 开店说明 -->
    <view class="sapply-hero">
      <text class="sapply-hero__title">申请新开一家店铺</text>
      <text class="sapply-hero__sub">平台规则：商家提交开店申请 → 平台管理员审核 → 审核通过后店铺自动上线</text>
    </view>

    <!-- 申请表单 -->
    <view class="sapply-form">
      <view class="sapply-form__item">
        <text class="sapply-form__label">店铺名称 *</text>
        <input class="sapply-form__input" v-model="form.shopName" placeholder="如：巷子咖啡·二店"
               placeholder-class="sapply-form__placeholder" />
      </view>
      <view class="sapply-form__item">
        <text class="sapply-form__label">店铺分类 *</text>
        <picker :range="typeNames" @change="onTypeChange">
          <view class="sapply-form__input sapply-form__picker">
            <text :class="{ 'is-placeholder': typeIndex < 0 }">{{ typeIndex >= 0 ? typeNames[typeIndex] : '请选择分类' }}</text>
          </view>
        </picker>
      </view>
      <view class="sapply-form__item">
        <text class="sapply-form__label">店铺地址</text>
        <input class="sapply-form__input" v-model="form.address" placeholder="如：朝阳区南锣鼓巷 12 号"
               placeholder-class="sapply-form__placeholder" />
      </view>
      <view class="sapply-form__item">
        <text class="sapply-form__label">联系电话 *</text>
        <input class="sapply-form__input" v-model="form.contactPhone" type="number" maxlength="11"
               placeholder="方便平台与你联系" placeholder-class="sapply-form__placeholder" />
      </view>

      <button class="brand-btn sapply-form__submit" :class="{ 'is-disabled': !canSubmit || submitting }" @click="handleSubmit">
        {{ submitting ? '提交中…' : '提交开店申请' }}
      </button>
      <text class="sapply-form__tip">待审核期间不能重复提交；被拒绝会显示原因，可修改后再次申请</text>
    </view>

    <!-- 我的申请记录 -->
    <view class="sapply-list">
      <text class="sapply-list__title">我的开店申请</text>
      <view class="sapply-list__item brand-card" v-for="a in applies" :key="a.id">
        <view class="sapply-list__row">
          <text class="sapply-list__name">{{ a.shopName }}</text>
          <StatBadge :text="statusText(a.status)" :type="statusType(a.status)" />
        </view>
        <text class="sapply-list__meta">{{ (a.createTime || '').replace('T', ' ').slice(0, 16) }} · {{ a.address || '未填地址' }}</text>
        <text class="sapply-list__reject" v-if="a.status === 2 && a.rejectReason">拒绝原因：{{ a.rejectReason }}</text>
        <text class="sapply-list__ok" v-if="a.status === 1 && a.shopId">已上线，店铺ID：{{ a.shopId }}（可在"我的店铺"查看）</text>
      </view>
      <EmptyView v-if="!loading && applies.length === 0" text="还没有开店申请记录" />
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/NavBar.vue'
import StatBadge from '@/components/StatBadge.vue'
import EmptyView from '@/components/EmptyView.vue'
import { applyShopOpen, listMyShopApplies } from '@/api/merchant.js'
import { listShopType } from '@/api/shop.js'

/**
 * 开店申请页（商家端，需登录）
 * 规则：管理员不能直接新增店铺——店铺上线只有"商家提交申请 → 平台审核通过"一条路；
 * 提交 POST /merchant/shop/apply，记录 GET /merchant/shop/apply/list；
 * 审核通过时 admin-service 经 Seata 全局事务建店（审核通过 = 上线）。
 */
export default {
  components: { NavBar, StatBadge, EmptyView },
  data() {
    return {
      form: { shopName: '', address: '', contactPhone: '' },
      types: [],
      typeIndex: -1,
      applies: [],
      loading: true,
      submitting: false
    }
  },
  computed: {
    typeNames() {
      return this.types.map((t) => t.name)
    },
    canSubmit() {
      return !!this.form.shopName.trim() && this.typeIndex >= 0 && /^1[3-9]\d{9}$/.test(this.form.contactPhone)
    }
  },
  onShow() {
    this.loadTypes()
    this.loadApplies()
  },
  methods: {
    async loadTypes() {
      try {
        this.types = (await listShopType()) || []
      } catch (e) {
        // toast 已统一处理
      }
    },
    onTypeChange(e) {
      this.typeIndex = Number(e.detail.value)
    },
    async loadApplies() {
      this.loading = true
      try {
        this.applies = (await listMyShopApplies()) || []
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    async handleSubmit() {
      if (this.submitting) return
      if (!/^1[3-9]\d{9}$/.test(this.form.contactPhone)) {
        uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
        return
      }
      this.submitting = true
      try {
        await applyShopOpen({
          shopName: this.form.shopName.trim(),
          typeId: this.types[this.typeIndex] && this.types[this.typeIndex].id,
          address: this.form.address.trim(),
          contactPhone: this.form.contactPhone.trim()
        })
        uni.showToast({ title: '申请已提交，等待平台审核', icon: 'none' })
        this.form = { shopName: '', address: '', contactPhone: '' }
        this.typeIndex = -1
        this.loadApplies()
      } catch (e) {
        // toast 已统一处理（重复申请等提示由后端返回）
      } finally {
        this.submitting = false
      }
    },
    statusText(s) {
      return { 0: '待审核', 1: '已通过', 2: '已拒绝' }[s] || '未知'
    },
    statusType(s) {
      return s === 1 ? 'primary' : 'gray'
    }
  }
}
</script>

<style lang="scss" scoped>
.sapply-page {
  min-height: 100vh;
  background: $brand-bg;
  padding: 24rpx 24rpx 60rpx;
}

.sapply-hero {
  padding: 32rpx 16rpx;
  margin-bottom: 8rpx;

  &__title {
    display: block;
    font-size: 20px;
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

.sapply-form {
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

  &__picker {
    display: flex;
    align-items: center;
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

.sapply-form__placeholder,
.is-placeholder {
  color: $text-sub;
}

.sapply-list {
  margin-top: 36rpx;

  &__title {
    display: block;
    margin: 0 8rpx 20rpx;
    color: $text-main;
    font-size: 16px;
    font-weight: 700;
  }

  &__item {
    padding: 24rpx;
    margin-bottom: 20rpx;
  }

  &__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__name {
    color: $text-main;
    font-size: 15px;
    font-weight: 600;
  }

  &__meta {
    display: block;
    margin-top: 10rpx;
    color: $text-sub;
    font-size: 12px;
  }

  &__reject {
    display: block;
    margin-top: 10rpx;
    color: #c0504d;
    font-size: 12px;
  }

  &__ok {
    display: block;
    margin-top: 10rpx;
    color: $brand-primary;
    font-size: 12px;
  }
}

/* NavBar 通栏：抵消根容器 padding */
.page-nav {
  margin: -24rpx -24rpx 0;
}
</style>
