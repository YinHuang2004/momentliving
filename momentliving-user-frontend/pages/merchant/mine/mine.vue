<template>
  <view class="m-mine">
    <NavBar title="我的店铺" :back="false" bgColor="#4A6B4D" color="#FAF7F0" />

    <!-- 个人信息卡（编辑走 PUT /merchant/me：头像/姓名/手机号，可选改密） -->
    <view class="me-card brand-card">
      <image class="me-card__avatar" :src="meAvatar" mode="aspectFill" />
      <view class="me-card__info">
        <text class="me-card__name">{{ me.name || me.username || '商家' }}</text>
        <text class="me-card__meta">{{ me.phone || '未绑定手机号' }} · @{{ me.username || '-' }}</text>
      </view>
      <button class="me-card__edit" @click="openProfileEdit">编辑资料</button>
    </view>

    <!-- 店铺信息卡 -->
    <view class="shop-card brand-card" v-if="shop">
      <image class="shop-card__img" :src="cover" mode="aspectFill" />
      <view class="shop-card__info">
        <text class="shop-card__name">{{ shop.name }}</text>
        <text class="shop-card__meta">类型 ID {{ shop.typeId }}<template v-if="scoreText"> · ★{{ scoreText }}</template></text>
        <text class="shop-card__addr ellipsis-1">{{ shop.area }} {{ shop.address }}</text>
      </view>
    </view>

    <!-- 营业状态开关 -->
    <view class="m-mine__row brand-card" v-if="shop">
      <view class="m-mine__row-info">
        <text class="m-mine__row-label">营业状态</text>
        <text class="m-mine__row-value" :class="open ? 'is-open' : 'is-closed'">
          {{ open ? '营业中' : '已打烊' }}
        </text>
      </view>
      <switch
        :checked="open"
        color="#6B8E5A"
        @change="handleOpenChange"
      />
    </view>

    <!-- 今日数据 -->
    <view class="m-mine__stats">
      <view class="m-mine__stat brand-card">
        <text class="m-mine__stat-num">{{ stats.today.verified }}</text>
        <text class="m-mine__stat-label">今日核销（单）</text>
      </view>
      <view class="m-mine__stat brand-card">
        <text class="m-mine__stat-num m-mine__stat-num--accent">{{ revenueText }}</text>
        <text class="m-mine__stat-label">今日营收</text>
      </view>
    </view>

    <!-- 操作按钮 -->
    <view class="m-mine__actions">
      <button class="brand-btn-plain m-mine__action" @click="handleEdit">编辑店铺信息</button>
      <button class="brand-btn-plain m-mine__action" @click="handleReviews">查看评价</button>
      <button class="brand-btn-plain m-mine__action" @click="handleContact">联系平台</button>
      <button class="brand-btn-plain m-mine__action m-mine__action--danger" @click="handleLogout">退出登录</button>
    </view>

    <!-- 编辑店铺弹层：店名 / 营业时间 / 地址 / 头图（PUT /shop） -->
    <view class="edit-mask" v-if="showEdit" @click="showEdit = false">
      <view class="edit-panel brand-card" @click.stop>
        <text class="edit-panel__title">编辑店铺信息</text>
        <view class="edit-panel__row">
          <text class="edit-panel__label">店名</text>
          <input class="edit-panel__input" v-model="form.name" placeholder="店铺名称" placeholder-class="edit-panel__placeholder" />
        </view>
        <view class="edit-panel__row">
          <text class="edit-panel__label">营业时间</text>
          <view class="edit-panel__hours">
            <picker class="edit-panel__hours-picker" mode="time" :value="form.openStart" @change="onStartChange">
              <view class="edit-panel__time" :class="{ 'is-placeholder': !form.openStart }">{{ form.openStart || '开始时间' }}</view>
            </picker>
            <text class="edit-panel__hours-sep">至</text>
            <picker class="edit-panel__hours-picker" mode="time" :value="form.openEnd" @change="onEndChange">
              <view class="edit-panel__time" :class="{ 'is-placeholder': !form.openEnd }">{{ form.openEnd || '结束时间' }}</view>
            </picker>
          </view>
        </view>
        <view class="edit-panel__row">
          <text class="edit-panel__label">地址</text>
          <input class="edit-panel__input" v-model="form.address" placeholder="店铺地址" placeholder-class="edit-panel__placeholder" />
        </view>
        <view class="edit-panel__row edit-panel__row--img">
          <text class="edit-panel__label">店铺头图</text>
          <view class="edit-panel__img-wrap">
            <image class="edit-panel__img" :src="form.cover" mode="aspectFill" @click="chooseCover" />
            <text class="edit-panel__img-tip">点击更换</text>
          </view>
        </view>
        <button class="edit-panel__submit" :disabled="saving" @click="saveShop">保存</button>
      </view>
    </view>

    <!-- 编辑个人信息弹层：头像 / 姓名 / 手机号 / 改密（PUT /merchant/me） -->
    <view class="edit-mask" v-if="showProfileEdit" @click="showProfileEdit = false">
      <view class="edit-panel brand-card" @click.stop>
        <text class="edit-panel__title">编辑个人信息</text>
        <view class="edit-panel__row edit-panel__row--img">
          <text class="edit-panel__label">头像</text>
          <view class="edit-panel__img-wrap">
            <image class="edit-panel__avatar" :src="profileForm.avatar || '/static/logo.png'" mode="aspectFill" @click="chooseAvatar" />
            <text class="edit-panel__img-tip">点击更换</text>
          </view>
        </view>
        <view class="edit-panel__row">
          <text class="edit-panel__label">姓名</text>
          <input class="edit-panel__input" v-model="profileForm.name" maxlength="20" placeholder="商家姓名/店长" placeholder-class="edit-panel__placeholder" />
        </view>
        <view class="edit-panel__row">
          <text class="edit-panel__label">手机号</text>
          <input class="edit-panel__input" v-model="profileForm.phone" type="number" maxlength="11" placeholder="11 位手机号" placeholder-class="edit-panel__placeholder" />
        </view>
        <view class="edit-panel__section">修改密码（不改请留空）</view>
        <view class="edit-panel__row">
          <text class="edit-panel__label">旧密码</text>
          <input class="edit-panel__input" v-model="profileForm.oldPassword" password placeholder="修改密码时必填" placeholder-class="edit-panel__placeholder" />
        </view>
        <view class="edit-panel__row">
          <text class="edit-panel__label">新密码</text>
          <input class="edit-panel__input" v-model="profileForm.newPassword" password placeholder="至少 6 位" placeholder-class="edit-panel__placeholder" />
        </view>
        <button class="edit-panel__submit" :disabled="savingProfile" @click="saveProfile">保存</button>
      </view>
    </view>

    <CustomTabBar :active="3" />
  </view>
</template>

<script>
import NavBar from '@/components/NavBar.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { getMerchantShop, updateMerchantShop, merchantStats, merchantLogout, getMerchantMe, updateMerchantMe } from '@/api/merchant.js'
import { getToken, clearMerchantToken } from '@/utils/request.js'
import { uploadImage } from '@/api/file.js'

/**
 * 商家端"我的店铺"：个人信息卡 + 店铺信息卡 + 营业状态开关 + 今日数据 + 编辑弹层 + 退出登录
 * 个人信息（头像/姓名/手机号，可选改密）走 PUT /merchant/me，保存后同步本地 merchantInfo；
 * 编辑店铺（店名/营业时间/地址/头图）走 PUT /shop，营业时间用起止 time picker 选择
 */
export default {
  components: { NavBar, CustomTabBar },
  data() {
    return {
      me: {},
      shop: null,
      open: true,
      stats: { today: { verified: 0, revenue: 0 } },
      showEdit: false,
      saving: false,
      form: { name: '', openStart: '', openEnd: '', address: '', cover: '' },
      showProfileEdit: false,
      savingProfile: false,
      profileForm: { name: '', phone: '', avatar: '', oldPassword: '', newPassword: '' }
    }
  },
  computed: {
    meAvatar() {
      return this.me.avatar || '/static/logo.png'
    },
    cover() {
      if (!this.shop || !this.shop.images) return '/static/logo.png'
      return this.shop.images.split(',')[0] || '/static/logo.png'
    },
    scoreText() {
      const s = Number(this.shop && this.shop.score)
      if (!s) return ''
      return s > 5 ? (s / 10).toFixed(1) : String(s)
    },
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
    this.loadMe()
    this.loadShop()
    this.loadStats()
  },
  methods: {
    async loadMe() {
      try {
        const me = await getMerchantMe()
        if (me) {
          this.me = me
          // 同步本地缓存（保留原 token 字段），工作台问候语等处即时生效
          const cached = uni.getStorageSync('merchantInfo') || {}
          uni.setStorageSync('merchantInfo', { ...cached, ...me, token: me.token || cached.token })
        }
      } catch (e) {
        // 拉取失败时退回本地缓存
        this.me = uni.getStorageSync('merchantInfo') || {}
      }
    },
    openProfileEdit() {
      this.profileForm = {
        name: this.me.name || '',
        phone: this.me.phone || '',
        avatar: this.me.avatar || '',
        oldPassword: '',
        newPassword: ''
      }
      this.showProfileEdit = true
    },
    chooseAvatar() {
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        success: async (res) => {
          const path = res.tempFilePaths && res.tempFilePaths[0]
          if (!path) return
          uni.showLoading({ title: '上传中…' })
          try {
            this.profileForm.avatar = await uploadImage(path, 'avatars')
          } catch (e) {
            // toast 已统一处理
          } finally {
            uni.hideLoading()
          }
        }
      })
    },
    async saveProfile() {
      const name = (this.profileForm.name || '').trim()
      const phone = (this.profileForm.phone || '').trim()
      if (!name) {
        uni.showToast({ title: '姓名不能为空', icon: 'none' })
        return
      }
      if (!/^1[3-9]\d{9}$/.test(phone)) {
        uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
        return
      }
      const payload = { name, phone, avatar: this.profileForm.avatar || '' }
      if (this.profileForm.newPassword) {
        if (!this.profileForm.oldPassword) {
          uni.showToast({ title: '请输入旧密码', icon: 'none' })
          return
        }
        if (this.profileForm.newPassword.length < 6) {
          uni.showToast({ title: '新密码至少 6 位', icon: 'none' })
          return
        }
        payload.oldPassword = this.profileForm.oldPassword
        payload.newPassword = this.profileForm.newPassword
      }
      this.savingProfile = true
      try {
        const me = await updateMerchantMe(payload)
        if (me) {
          this.me = me
          const cached = uni.getStorageSync('merchantInfo') || {}
          uni.setStorageSync('merchantInfo', { ...cached, ...me, token: me.token || cached.token })
        }
        uni.showToast({ title: '保存成功', icon: 'success' })
        this.showProfileEdit = false
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.savingProfile = false
      }
    },
    async loadShop() {
      try {
        const me = uni.getStorageSync('merchantInfo')
        const shopId = me && me.shopId
        if (!shopId) {
          uni.showToast({ title: '当前账号未绑定店铺，请联系平台', icon: 'none' })
          return
        }
        this.shop = await getMerchantShop(shopId)
      } catch (e) {
        // toast 已统一处理
      }
    },
    async loadStats() {
      try {
        const res = await merchantStats()
        this.stats = { today: (res && res.today) || { verified: 0, revenue: 0 } }
      } catch (e) {
        // toast 已统一处理
      }
    },
    handleOpenChange(e) {
      const next = e.detail.value
      this.open = next
      // Shop 暂无营业状态字段，仅本地记录；后续后端补充字段后接入 PUT /api/shop
      uni.showToast({ title: next ? '已切换为营业中' : '已切换为已打烊', icon: 'none' })
    },
    handleEdit() {
      if (!this.shop) return
      const { start, end } = this.parseOpenHours(this.shop.openHours)
      this.form = {
        name: this.shop.name || '',
        openStart: start,
        openEnd: end,
        address: this.shop.address || '',
        cover: (this.shop.images || '').split(',').filter(Boolean)[0] || ''
      }
      this.showEdit = true
    },
    // "09:00-22:00" → {start, end}；存量数据格式不规整时留空，让商家用选择器重选
    parseOpenHours(openHours) {
      const parts = (openHours || '').split('-').map((s) => s.trim())
      const isTime = (t) => /^\d{1,2}:\d{2}$/.test(t)
      if (parts.length === 2 && isTime(parts[0]) && isTime(parts[1])) {
        return {
          start: parts[0].split(':').map((x, i) => (i === 0 ? x.padStart(2, '0') : x)).join(':'),
          end: parts[1].split(':').map((x, i) => (i === 0 ? x.padStart(2, '0') : x)).join(':')
        }
      }
      return { start: '', end: '' }
    },
    onStartChange(e) {
      this.form.openStart = e.detail.value
    },
    onEndChange(e) {
      this.form.openEnd = e.detail.value
    },
    chooseCover() {
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        success: async (res) => {
          const path = res.tempFilePaths && res.tempFilePaths[0]
          if (!path) return
          uni.showLoading({ title: '上传中…' })
          try {
            this.form.cover = await uploadImage(path, 'shops')
          } catch (e) {
            // toast 已统一处理
          } finally {
            uni.hideLoading()
          }
        }
      })
    },
    async saveShop() {
      const name = (this.form.name || '').trim()
      if (!name) {
        uni.showToast({ title: '店名不能为空', icon: 'none' })
        return
      }
      if ((this.form.openStart && !this.form.openEnd) || (!this.form.openStart && this.form.openEnd)) {
        uni.showToast({ title: '请选择完整的营业时间', icon: 'none' })
        return
      }
      const openHours = this.form.openStart && this.form.openEnd
        ? `${this.form.openStart}-${this.form.openEnd}`
        : ''
      this.saving = true
      try {
        await updateMerchantShop({
          id: this.shop.id,
          name,
          openHours,
          address: (this.form.address || '').trim(),
          images: this.form.cover || ''
        })
        uni.showToast({ title: '保存成功', icon: 'success' })
        this.showEdit = false
        this.loadShop()
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.saving = false
      }
    },
    async handleLogout() {
      const confirmed = await new Promise((resolve) => {
        uni.showModal({
          title: '退出登录',
          content: '确定退出商家工作台吗？',
          success: (res) => resolve(res.confirm),
          fail: () => resolve(false)
        })
      })
      if (!confirmed) return
      try {
        await merchantLogout()
      } catch (e) {
        // 无论服务端是否成功，本地登录态都清掉
        clearMerchantToken()
      }
      uni.reLaunch({ url: '/pages/merchant/login/login' })
    },
    handleReviews() {
      uni.showToast({ title: '评价管理开发中', icon: 'none' })
    },
    handleContact() {
      uni.showToast({ title: '平台客服：400-000-0000', icon: 'none' })
    }
  }
}
</script>

<style lang="scss" scoped>
.m-mine {
  min-height: 100vh;
  background: $brand-bg;
  padding-bottom: 160rpx;
}

/* ========== 个人信息卡 ========== */

.me-card {
  display: flex;
  align-items: center;
  margin: 24rpx 24rpx 0;
  padding: 24rpx;
}

.me-card__avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: $brand-bg-2;
  flex-shrink: 0;
}

.me-card__info {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.me-card__name {
  font-size: 17px;
  font-weight: 700;
}

.me-card__meta {
  color: $text-sub;
  font-size: 12px;
}

.me-card__edit {
  margin: 0;
  padding: 0 24rpx;
  height: 56rpx;
  line-height: 52rpx;
  font-size: 13px;
  border-radius: $radius-btn;
  border: 1px solid $brand-primary;
  color: $brand-primary;
  background: transparent;
  flex-shrink: 0;

  &::after {
    border: none;
  }
}

.shop-card {
  display: flex;
  margin: 24rpx;
  padding: 24rpx;
}

.shop-card__img {
  width: 150rpx;
  height: 150rpx;
  border-radius: 16rpx;
  background: $brand-bg-2;
  flex-shrink: 0;
}

.shop-card__info {
  flex: 1;
  min-width: 0;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.shop-card__name {
  font-size: 18px;
  font-weight: 700;
}

.shop-card__meta {
  color: $brand-accent;
  font-size: 13px;
}

.shop-card__addr {
  color: $text-sub;
  font-size: 13px;
}

.m-mine__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 24rpx 24rpx;
  padding: 24rpx 28rpx;
}

.m-mine__row-info {
  display: flex;
  flex-direction: column;
}

.m-mine__row-label {
  font-size: 15px;
  font-weight: 600;
}

.m-mine__row-value {
  margin-top: 6rpx;
  font-size: 13px;

  &.is-open {
    color: $brand-primary;
  }

  &.is-closed {
    color: $text-sub;
  }
}

.m-mine__stats {
  display: flex;
  gap: 20rpx;
  margin: 0 24rpx 24rpx;
}

.m-mine__stat {
  flex: 1;
  padding: 28rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.m-mine__stat-num {
  font-size: 26px;
  font-weight: 800;

  &--accent {
    color: $brand-accent;
  }
}

.m-mine__stat-label {
  margin-top: 8rpx;
  color: $text-sub;
  font-size: 12px;
}

.m-mine__actions {
  margin: 0 24rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.m-mine__action {
  height: 44px;
  line-height: 42px;

  &--danger {
    border-color: rgba(196, 74, 58, 0.4);
    color: #C44A3A;
  }
}

/* ========== 编辑店铺弹层 ========== */

.edit-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}

.edit-panel {
  width: 100%;
  border-radius: $radius-card $radius-card 0 0;
  padding: 32rpx 28rpx calc(48rpx + env(safe-area-inset-bottom));
}

.edit-panel__title {
  display: block;
  text-align: center;
  font-size: 17px;
  font-weight: 700;
  margin-bottom: 24rpx;
}

.edit-panel__row {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1px solid $brand-line;

  &--img {
    align-items: flex-start;
  }
}

.edit-panel__label {
  width: 160rpx;
  color: $text-sub;
  font-size: 14px;
}

.edit-panel__input {
  flex: 1;
  font-size: 15px;
}

/* 营业时间起止选择 */
.edit-panel__hours {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.edit-panel__hours-picker {
  flex: 1;
}

.edit-panel__time {
  height: 64rpx;
  line-height: 64rpx;
  text-align: center;
  border: 1px solid $brand-line;
  border-radius: $radius-btn;
  font-size: 14px;
  color: $text-main;

  &.is-placeholder {
    color: $text-sub;
  }
}

.edit-panel__hours-sep {
  color: $text-sub;
  font-size: 13px;
  flex-shrink: 0;
}

.edit-panel__section {
  padding: 24rpx 0 4rpx;
  color: $text-sub;
  font-size: 12px;
}

.edit-panel__avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: $brand-bg-2;
}

.edit-panel__img-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.edit-panel__img {
  width: 160rpx;
  height: 120rpx;
  border-radius: 12rpx;
  background: $brand-bg-2;
}

.edit-panel__img-tip {
  margin-top: 8rpx;
  color: $text-sub;
  font-size: 11px;
}

.edit-panel__placeholder {
  color: $text-sub;
}

.edit-panel__submit {
  margin-top: 28rpx;
  height: 76rpx;
  line-height: 72rpx;
  border-radius: $radius-btn;
  background: $brand-primary;
  color: #ffffff;
  font-size: 15px;

  &::after {
    border: none;
  }
}
</style>
