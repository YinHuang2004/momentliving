<template>
  <view class="message-page">
    <view class="message-page__head">
      <text class="message-page__title">消息</text>
      <view class="message-page__actions">
        <text class="message-page__action" @click="goSearch">搜人</text>
        <text class="message-page__action" @click="goGroupCreate">发群</text>
      </view>
    </view>

    <view
      class="session-card"
      v-for="s in sessions"
      :key="s.id"
      @click="enterSession(s)"
    >
      <!-- 头像：单聊用对方头像，群聊用默认群图标 -->
      <image
        class="session-card__avatar"
        :src="s.type === 1 ? (s.peerAvatar || '/static/logo.png') : '/static/logo.png'"
        mode="aspectFill"
      />
      <view class="session-card__body">
        <view class="session-card__top">
          <text class="session-card__name ellipsis-1">
            {{ s.type === 1 ? (s.peerName || '用户') : (s.groupName || '群聊') }}
          </text>
          <text class="session-card__time">{{ formatTime(s.lastMessageAt) }}</text>
        </view>
        <view class="session-card__bottom">
          <text class="session-card__preview ellipsis-1">{{ s.lastMessage || (s.type === 2 ? '群聊已创建，来说点什么吧' : '打个招呼吧') }}</text>
          <view class="session-card__badge" v-if="s.unreadCount > 0">
            <text class="session-card__badge-text">{{ s.unreadCount > 99 ? '99+' : s.unreadCount }}</text>
          </view>
        </view>
      </view>
    </view>

    <EmptyView v-if="!loading && sessions.length === 0" text="还没有会话，去博客页点「聊一聊」或搜索用户开聊吧" />
    <view class="message-page__loading" v-if="loading">
      <text>加载中…</text>
    </view>
  </view>
</template>

<script>
import EmptyView from '@/components/EmptyView.vue'
import { getSessions, unreadCount } from '@/api/chat.js'
import { getToken } from '@/utils/request.js'
import { connectWS, onWSMessage } from '@/utils/websocket.js'

/**
 * Tab4 消息 = 聊天会话列表（单聊+群聊）：未读红点 + 最后消息预览 + 时间
 * 专注 websocket 聊天；"好友动态"入口已移至 Tab3 博客页顶部的切换 Tab
 */
export default {
  components: { EmptyView },
  data() {
    return {
      sessions: [],
      loading: false,
      _unsub: null
    }
  },
  onShow() {
    if (!getToken()) {
      uni.reLaunch({ url: '/pages/user/login/login' })
      return
    }
    connectWS()   // 幂等：已连接则跳过；新消息到达时实时刷新列表
    this.loadSessions()
    this.refreshBadge()
    // 订阅 WS：收到新消息就刷新（会话列表量级小，全量刷新简单可靠）
    if (!this._unsub) {
      this._unsub = onWSMessage((msg) => {
        if (msg.op === 'new_msg') {
          this.loadSessions()
          this.refreshBadge()
        }
      })
    }
  },
  onHide() {
    if (this._unsub) {
      this._unsub()
      this._unsub = null
    }
  },
  onPullDownRefresh() {
    this.loadSessions().finally(() => uni.stopPullDownRefresh())
  },
  methods: {
    async loadSessions() {
      if (this.loading) return
      this.loading = true
      try {
        this.sessions = (await getSessions()) || []
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    /** TabBar 消息红点（Tab4 是第 4 个，index=3） */
    async refreshBadge() {
      try {
        const n = (await unreadCount()) || 0
        if (n > 0) {
          uni.setTabBarBadge({ index: 3, text: String(n > 99 ? '99+' : n) })
        } else {
          uni.removeTabBarBadge({ index: 3 })
        }
      } catch (e) {
        // 非 tabBar 环境可能报错，忽略
      }
    },
    enterSession(s) {
      const name = encodeURIComponent(s.type === 1 ? (s.peerName || '用户') : (s.groupName || '群聊'))
      uni.navigateTo({
        url: `/pages/user/chat/chatRoom?sessionId=${s.id}&type=${s.type}&groupId=${s.groupId || ''}&peerName=${name}`
      })
    },
    goSearch() {
      uni.navigateTo({ url: '/pages/user/chat/userSearch' })
    },
    goGroupCreate() {
      uni.navigateTo({ url: '/pages/user/chat/groupCreate' })
    },
    formatTime(t) {
      if (!t) return ''
      const s = String(t).replace('T', ' ')
      const today = new Date().toISOString().slice(0, 10)
      const datePart = s.slice(0, 10)
      // 今天的消息只显示时分，早于今天显示月-日
      return datePart === today ? s.slice(11, 16) : s.slice(5, 10)
    }
  }
}
</script>

<style lang="scss" scoped>
.message-page {
  min-height: 100vh;
  padding: 24rpx;
}

.message-page__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.message-page__title {
  font-size: 20px;
  font-weight: 700;
}

.message-page__actions {
  display: flex;
  gap: 28rpx;
}

.message-page__action {
  color: $brand-primary;
  font-size: 14px;
}

.session-card {
  display: flex;
  align-items: center;
  background: #ffffff;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
  padding: 24rpx;
  margin-bottom: 20rpx;
}

.session-card__avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: $brand-bg-2;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.session-card__body {
  flex: 1;
  min-width: 0;
}

.session-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}

.session-card__name {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  margin-right: 16rpx;
}

.session-card__time {
  color: $text-sub;
  font-size: 12px;
  flex-shrink: 0;
}

.session-card__bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.session-card__preview {
  flex: 1;
  min-width: 0;
  color: $text-sub;
  font-size: 13px;
  margin-right: 16rpx;
}

.session-card__badge {
  min-width: 32rpx;
  height: 32rpx;
  line-height: 32rpx;
  padding: 0 10rpx;
  border-radius: 999rpx;
  background: $brand-accent;
  text-align: center;
  flex-shrink: 0;
}

.session-card__badge-text {
  color: #ffffff;
  font-size: 11px;
}

.message-page__loading {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;
  color: $text-sub;
  font-size: 13px;
}
</style>
