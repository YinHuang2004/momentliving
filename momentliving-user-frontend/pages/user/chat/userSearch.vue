<template>
  <view class="user-search">
    <NavBar title="搜索用户" class="page-nav" />
    <view class="user-search__bar">
      <input
        class="user-search__input"
        v-model="keyword"
        placeholder="输入昵称（模糊）或手机号（精确）"
        placeholder-class="user-search__placeholder"
        confirm-type="search"
        @confirm="doSearch"
      />
      <view class="user-search__btn" @click="doSearch">
        <text class="user-search__btn-text">搜索</text>
      </view>
    </view>

    <view class="user-search__tip" v-if="searched && users.length === 0 && !loading">
      <text>没有找到相关用户</text>
    </view>

    <view class="user-item" v-for="u in users" :key="u.id" @click="chatWith(u)">
      <image class="user-item__avatar" :src="u.images || '/static/logo.png'" mode="aspectFill" />
      <view class="user-item__body">
        <text class="user-item__name ellipsis-1">{{ u.nickName || `用户${u.id}` }}</text>
        <text class="user-item__sub">打个招呼认识一下</text>
      </view>
      <text class="user-item__action">聊一聊</text>
    </view>

    <view class="user-search__loading" v-if="loading">
      <text>搜索中…</text>
    </view>
  </view>
</template>

<script>
import { searchUsers, ensureSingle } from '@/api/chat.js'
import { getToken } from '@/utils/request.js'

/**
 * 搜用户（昵称模糊 / 手机号精确）→ 点击进单聊（ensureSingle 幂等）
 * 搜索到的陌生人会话同样受"首条限制"约束：我发出首条后锁定，对方回复解锁
 */
import NavBar from '@/components/NavBar.vue'
export default {
  components: { NavBar },
  data() {
    return {
      keyword: '',
      users: [],
      loading: false,
      searched: false
    }
  },
  onShow() {
    if (!getToken()) {
      uni.reLaunch({ url: '/pages/user/login/login' })
    }
  },
  methods: {
    async doSearch() {
      const kw = (this.keyword || '').trim()
      if (!kw) return
      this.loading = true
      try {
        this.users = (await searchUsers(kw)) || []
        this.searched = true
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    /** 进单聊：ensureSingle 返回既有/新建会话（幂等），带对方昵称进聊天室 */
    async chatWith(u) {
      try {
        const res = await ensureSingle(u.id)
        const name = encodeURIComponent(res.peerName || u.nickName || `用户${u.id}`)
        uni.navigateTo({
          url: `/pages/user/chat/chatRoom?sessionId=${res.id}&type=1&peerName=${name}`
        })
      } catch (e) {
        // toast 已统一处理
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.user-search {
  min-height: 100vh;
  padding: 24rpx;
}

.user-search__bar {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.user-search__input {
  flex: 1;
  height: 76rpx;
  background: #ffffff;
  border: 1px solid $brand-line;
  border-radius: $radius-btn;
  padding: 0 28rpx;
  font-size: 14px;
}

.user-search__placeholder {
  color: $text-sub;
}

.user-search__btn {
  height: 76rpx;
  line-height: 76rpx;
  padding: 0 32rpx;
  border-radius: $radius-btn;
  background: $brand-primary;
}

.user-search__btn-text {
  color: #ffffff;
  font-size: 14px;
}

.user-search__tip,
.user-search__loading {
  text-align: center;
  color: $text-sub;
  font-size: 13px;
  padding: 48rpx 0;
}

.user-item {
  display: flex;
  align-items: center;
  background: #ffffff;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
  padding: 24rpx;
  margin-bottom: 20rpx;
}

.user-item__avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: $brand-bg-2;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.user-item__body {
  flex: 1;
  min-width: 0;
}

.user-item__name {
  display: block;
  font-size: 15px;
  font-weight: 600;
}

.user-item__sub {
  display: block;
  color: $text-sub;
  font-size: 12px;
  margin-top: 6rpx;
}

.user-item__action {
  color: $brand-primary;
  font-size: 14px;
  flex-shrink: 0;
}

/* NavBar 通栏：抵消根容器 padding */
.page-nav {
  margin: -24rpx -24rpx 0;
}
</style>
