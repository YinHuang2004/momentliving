<template>
  <view class="follow-page">
    <NavBar :title="pageTitle" />
    <!-- tabs：关注 / 粉丝 -->
    <view class="follow-tabs">
      <view
        v-for="t in tabs"
        :key="t.type"
        class="follow-tabs__item"
        :class="{ 'is-active': type === t.type }"
        @click="switchType(t.type)"
      >
        <text>{{ t.label }}</text>
      </view>
    </view>

    <view class="follow-item brand-card" v-for="u in users" :key="u.id">
      <!-- 头像/昵称可点，进 TA 的主页 -->
      <view class="follow-item__main" @click="goUserHome(u)">
        <image class="follow-item__avatar" :src="u.images || '/static/logo.png'" mode="aspectFill" />
        <view class="follow-item__info">
          <text class="follow-item__nick">{{ u.nickName || '用户' + u.id }}</text>
          <text class="follow-item__id">ID: {{ u.id }}</text>
        </view>
      </view>
      <button class="follow-item__btn" @click="goBlogs(u)">看博客</button>
      <!-- 自己的关注列表：取关走 toggle（PUT /follow/{id}） -->
      <button
        v-if="isSelfView && type === 1"
        class="follow-item__btn is-unfollow"
        @click="handleUnfollow(u)"
      >取关</button>
      <!-- 他人主页进入：关注/已关注（列表里出现自己时不显示） -->
      <button
        v-if="!isSelfView && String(u.id) !== myId"
        class="follow-item__btn is-toggle"
        :class="{ 'is-gray': u.followed }"
        @click="handleToggle(u)"
      >{{ u.followed ? '已关注' : '+ 关注' }}</button>
    </view>
    <EmptyView v-if="!loading && users.length === 0" :text="emptyText" />
    <view class="follow-page__loading" v-if="loading"><text>加载中…</text></view>
  </view>
</template>

<script>
import EmptyView from '@/components/EmptyView.vue'
import { followList, userFollowList, follow, isFollow } from '@/api/user.js'

/**
 * 关注/粉丝列表：type=1 关注的人 / 2 粉丝
 * - 不带 userId：看自己的列表 GET /follow/list/{type}，关注 Tab 带"取关"（toggle）
 * - 带 userId：从他人主页统计进入，GET /follow/list/{userId}/{type} 看 TA 的列表，
 *   每人带"关注/已关注"按钮（isFollow 并行查状态 + toggle），点头像/昵称进 TA 的主页
 */
import NavBar from '@/components/NavBar.vue'
export default {
  components: { NavBar, EmptyView },
  data() {
    return {
      tabs: [
        { type: 1, label: '关注' },
        { type: 2, label: '粉丝' }
      ],
      type: 1,
      users: [],
      loading: false,
      viewUserId: null,
      viewNick: '',
      myId: ''
    }
  },
  computed: {
    isSelfView() {
      return !this.viewUserId || String(this.viewUserId) === this.myId
    },
    pageTitle() {
      return this.isSelfView ? '关注与粉丝' : `${this.viewNick || 'TA'}的关注与粉丝`
    },
    emptyText() {
      return this.isSelfView
        ? (this.type === 1 ? '还没有关注的人' : '还没有粉丝')
        : (this.type === 1 ? 'TA还没有关注的人' : 'TA还没有粉丝')
    }
  },
  onLoad(options) {
    this.type = Number(options.type) === 2 ? 2 : 1
    this.viewUserId = options.userId || null
    this.viewNick = options.nickName ? decodeURIComponent(options.nickName) : ''
    this.myId = String((uni.getStorageSync('userInfo') || {}).id || '')
  },
  onShow() {
    this.loadUsers()
  },
  methods: {
    switchType(type) {
      if (type === this.type) return
      this.type = type
      this.loadUsers()
    },
    async loadUsers() {
      this.loading = true
      try {
        let users = this.isSelfView
          ? (await followList(this.type)) || []
          : (await userFollowList(this.viewUserId, this.type)) || []
        // 他人视角：并行查我对每个人的关注状态，供"关注/已关注"按钮展示
        if (!this.isSelfView && users.length > 0) {
          const states = await Promise.all(users.map((u) => isFollow(u.id).catch(() => false)))
          users = users.map((u, i) => ({ ...u, followed: !!states[i] }))
        }
        this.users = users
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    goUserHome(user) {
      uni.navigateTo({ url: `/pages/user/userHome/userHome?userId=${user.id}` })
    },
    goBlogs(user) {
      uni.navigateTo({ url: `/pages/user/blog/myBlogs?userId=${user.id}&nickName=${encodeURIComponent(user.nickName || '')}` })
    },
    async handleUnfollow(user) {
      try {
        await follow(user.id)   // toggle：已关注 → 取关
        uni.showToast({ title: '已取消关注', icon: 'none' })
        this.loadUsers()
      } catch (e) {
        // toast 已统一处理
      }
    },
    async handleToggle(user) {
      try {
        await follow(user.id)   // toggle：关注⇄取关，成功后本地翻转状态
        user.followed = !user.followed
        uni.showToast({ title: user.followed ? '关注成功' : '已取消关注', icon: 'none' })
      } catch (e) {
        // toast 已统一处理
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.follow-page {
  min-height: 100vh;
  padding-bottom: 40rpx;
}

.follow-tabs {
  display: flex;
  border-bottom: 1px solid $brand-line;
  background: $brand-bg;
  position: sticky;
  top: 0;
  z-index: 10;

  &__item {
    flex: 1;
    text-align: center;
    padding: 24rpx 0;
    color: $text-sub;
    font-size: 15px;
    position: relative;

    &.is-active {
      color: $brand-primary;
      font-weight: 700;

      &::after {
        content: '';
        position: absolute;
        left: 50%;
        transform: translateX(-50%);
        bottom: 0;
        width: 48rpx;
        height: 4rpx;
        border-radius: 4rpx;
        background: $brand-primary;
      }
    }
  }
}

.follow-item {
  display: flex;
  align-items: center;
  margin: 20rpx 24rpx;
  padding: 24rpx 28rpx;
}

.follow-item__main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}

.follow-item__avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: $brand-bg-2;
  margin-right: 20rpx;
}

.follow-item__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.follow-item__nick {
  font-size: 15px;
  font-weight: 600;
}

.follow-item__id {
  margin-top: 6rpx;
  color: $text-sub;
  font-size: 12px;
}

.follow-item__btn {
  height: 56rpx;
  line-height: 52rpx;
  padding: 0 28rpx;
  border-radius: $radius-btn;
  background: transparent;
  border: 1px solid $brand-primary;
  color: $brand-primary;
  font-size: 13px;

  &::after {
    border: none;
  }

  &.is-toggle {
    margin-left: 16rpx;
  }

  &.is-gray {
    border-color: $brand-line;
    color: $text-sub;
    background: $brand-bg-2;
  }

  &.is-unfollow {
    margin-left: 16rpx;
    border-color: $brand-line;
    color: $text-sub;
  }
}

.follow-page__loading {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;
  color: $text-sub;
  font-size: 13px;
}
</style>
