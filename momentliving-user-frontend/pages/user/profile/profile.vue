<template>
  <view class="profile-page">
    <NavBar title="编辑资料" class="page-nav" />
    <!-- 头像可点击修改（PUT /user/avatar，头像存 user.images） -->
    <view class="profile-row brand-card profile-row--user">
      <view class="profile-row__avatar-wrap" @click="changeAvatar">
        <image class="profile-row__avatar" :src="userInfo.images || '/static/logo.png'" mode="aspectFill" />
        <view class="profile-row__avatar-edit">✎</view>
      </view>
      <text class="profile-row__nick">{{ userInfo.nickName || '--' }}</text>
    </view>

    <!-- 昵称 + 详细资料（昵称存 user 表，其余存 UserInfo 表，随 PUT /user/info 一起保存） -->
    <view class="profile-form brand-card">
      <view class="profile-form__row">
        <text class="profile-form__label">昵称</text>
        <input class="profile-form__input" v-model="form.nickName" maxlength="16" placeholder="给自己起个名字" placeholder-class="profile-form__placeholder" />
      </view>
      <view class="profile-form__row">
        <text class="profile-form__label">城市</text>
        <input class="profile-form__input" v-model="form.city" placeholder="如：北京" placeholder-class="profile-form__placeholder" />
      </view>
      <view class="profile-form__row">
        <text class="profile-form__label">性别</text>
        <view class="profile-form__genders">
          <view
            class="profile-form__gender"
            :class="{ 'is-active': form.gender === false }"
            @click="form.gender = false"
          >女</view>
          <view
            class="profile-form__gender"
            :class="{ 'is-active': form.gender === true }"
            @click="form.gender = true"
          >男</view>
        </view>
      </view>
      <view class="profile-form__row">
        <text class="profile-form__label">生日</text>
        <picker mode="date" :value="birthdayText" @change="onBirthdayChange">
          <text class="profile-form__picker">{{ birthdayText || '请选择生日' }}</text>
        </picker>
      </view>
      <view class="profile-form__row profile-form__row--area">
        <text class="profile-form__label">个性签名</text>
        <textarea
          class="profile-form__textarea"
          v-model="form.introduce"
          maxlength="100"
          placeholder="一句话介绍自己"
          placeholder-class="profile-form__placeholder"
        />
      </view>
    </view>

    <button class="brand-btn profile-page__save" @click="handleSave">保存资料</button>
  </view>
</template>

<script>
import { getMe, getUserInfo, updateInfo, updateAvatar } from '@/api/user.js'
import { uploadImage } from '@/api/file.js'

/**
 * 编辑资料：昵称/城市/性别/生日/签名 → PUT /user/info（昵称存 user 表，其余存 UserInfo 表）；
 * 头像 → 点击头像上传 → PUT /user/avatar（存 user.images）
 */
import NavBar from '@/components/NavBar.vue'
export default {
  components: { NavBar },
  data() {
    return {
      userInfo: {},
      form: {
        nickName: '',
        city: '',
        gender: null,
        birthday: null,
        introduce: ''
      }
    }
  },
  computed: {
    birthdayText() {
      return this.form.birthday || ''
    }
  },
  async onLoad() {
    try {
      this.userInfo = (await getMe()) || {}
      this.form.nickName = this.userInfo.nickName || ''
      const info = await getUserInfo(this.userInfo.id)
      if (info) {
        this.form.city = info.city || ''
        this.form.gender = info.gender == null ? null : !!info.gender
        this.form.birthday = info.birthday || null
        this.form.introduce = info.introduce || ''
      }
    } catch (e) {
      // toast 已统一处理
    }
  },
  methods: {
    changeAvatar() {
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        success: async (res) => {
          const path = res.tempFilePaths && res.tempFilePaths[0]
          if (!path) return
          uni.showLoading({ title: '上传中…' })
          try {
            const url = await uploadImage(path, 'avatars')
            await updateAvatar(url)
            this.userInfo.images = url
            // 同步本地缓存，博客导航头像/我的页即时生效
            const cached = uni.getStorageSync('userInfo') || {}
            cached.images = url
            uni.setStorageSync('userInfo', cached)
            uni.showToast({ title: '头像已更新', icon: 'success' })
          } catch (e) {
            // toast 已统一处理
          } finally {
            uni.hideLoading()
          }
        }
      })
    },
    onBirthdayChange(e) {
      this.form.birthday = e.detail.value
    },
    async handleSave() {
      try {
        await updateInfo({
          nickName: this.form.nickName,
          city: this.form.city,
          gender: this.form.gender,
          birthday: this.form.birthday,
          introduce: this.form.introduce
        })
        // 同步本地缓存，"我的"页/博客导航昵称即时生效
        this.userInfo.nickName = this.form.nickName
        const cached = uni.getStorageSync('userInfo') || {}
        cached.nickName = this.form.nickName
        uni.setStorageSync('userInfo', cached)
        uni.showToast({ title: '保存成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 600)
      } catch (e) {
        // toast 已统一处理
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.profile-page {
  min-height: 100vh;
  padding: 24rpx;
}

.profile-row {
  display: flex;
  align-items: center;
  padding: 28rpx;
  margin-bottom: 24rpx;

  &--user {
    display: flex;
  }
}

.profile-row__avatar-wrap {
  position: relative;
  margin-right: 20rpx;
}

.profile-row__avatar {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: $brand-bg-2;
}

.profile-row__avatar-edit {
  position: absolute;
  right: -4rpx;
  bottom: -4rpx;
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: $brand-primary;
  color: #ffffff;
  font-size: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2rpx solid #ffffff;
}

.profile-row__nick {
  font-size: 18px;
  font-weight: 700;
}

.profile-form {
  padding: 12rpx 28rpx;
}

.profile-form__row {
  display: flex;
  align-items: center;
  padding: 26rpx 0;
  border-bottom: 1px solid $brand-line;

  &:last-child {
    border-bottom: none;
  }

  &--area {
    align-items: flex-start;
  }
}

.profile-form__label {
  width: 160rpx;
  color: $text-sub;
  font-size: 14px;
}

.profile-form__input {
  flex: 1;
  font-size: 15px;
}

.profile-form__genders {
  display: flex;
  gap: 20rpx;
}

.profile-form__gender {
  padding: 6rpx 36rpx;
  border-radius: $radius-btn;
  border: 1px solid $brand-line;
  color: $text-sub;
  font-size: 14px;

  &.is-active {
    border-color: $brand-primary;
    color: $brand-primary;
    background: rgba(107, 142, 90, 0.08);
  }
}

.profile-form__picker {
  font-size: 15px;
  color: $text-main;
}

.profile-form__textarea {
  flex: 1;
  min-height: 120rpx;
  font-size: 15px;
  line-height: 1.6;
}

.profile-page__save {
  margin-top: 40rpx;
}

.profile-form__placeholder {
  color: $text-sub;
}

/* NavBar 通栏：抵消根容器 padding */
.page-nav {
  margin: -24rpx -24rpx 0;
}
</style>
