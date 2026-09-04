<template>
  <view v-if="visible" class="share-mask" @click="$emit('close')">
    <view class="share-panel brand-card" @click.stop>
      <text class="share-panel__title">分享笔记</text>

      <!-- 分享给好友：选择会话（单聊/群聊）→ 聊天发送博客卡片消息 -->
      <view class="share-panel__section">
        <text class="share-panel__section-title">分享给好友</text>
        <scroll-view scroll-y class="share-panel__sessions">
          <view
            class="share-panel__session"
            v-for="s in sessions"
            :key="s.id"
            @click="sendToSession(s)"
          >
            <image
              class="share-panel__avatar"
              :src="(s.type === 2 ? '' : (s.peerAvatar || '')) || '/static/logo.png'"
              mode="aspectFill"
            />
            <view class="share-panel__session-info">
              <text class="share-panel__name ellipsis-1">{{ s.type === 2 ? s.groupName : s.peerName }}</text>
              <text class="share-panel__type">{{ s.type === 2 ? '群聊' : '单聊' }}</text>
            </view>
            <text class="share-panel__send">发送</text>
          </view>
          <view class="share-panel__empty" v-if="!loadingSessions && sessions.length === 0">
            <text>还没有聊天会话，先去"消息"页找个好友吧</text>
          </view>
          <view class="share-panel__empty" v-if="loadingSessions"><text>加载会话中…</text></view>
        </scroll-view>
      </view>

      <!-- 其他渠道 -->
      <view class="share-panel__channels">
        <button class="share-panel__channel" @click="systemShare">
          <text>系统分享</text>
        </button>
        <button class="share-panel__channel" @click="copyLink">
          <text>复制链接</text>
        </button>
      </view>

      <button class="share-panel__cancel" @click="$emit('close')">取消</button>
    </view>
  </view>
</template>

<script>
import { getSessions } from '@/api/chat.js'
import { connectWS, sendWS, onWSMessage, isConnected } from '@/utils/websocket.js'

/**
 * 博客分享面板（blogDetail 的分享按钮弹出）：
 * 1. 分享给好友：拉会话列表 → 点选会话 → 走聊天 WS 发博客卡片消息（type=2，
 *    content={blogId,title,cover,author}），对方聊天里点卡片直达详情；
 * 2. 系统分享：小程序/ App 用 uni.share，H5 自动降级为复制链接；
 * 3. 复制链接：H5 uni.setClipboardData 复制详情页路径。
 * 只依赖 blog 对象（需 id/title/images/name），动作完成后 emit('close')。
 */
export default {
  name: 'SharePanel',
  props: {
    visible: { type: Boolean, default: false },
    blog: { type: Object, default: () => ({}) }
  },
  emits: ['close'],
  data() {
    return {
      sessions: [],
      loadingSessions: false
    }
  },
  watch: {
    visible(open) {
      if (open) this.loadSessions()
    }
  },
  methods: {
    cardOf() {
      const imgs = (this.blog.images || '').split(',').filter(Boolean)
      return {
        blogId: this.blog.id,
        title: this.blog.title || '一篇笔记',
        cover: imgs[0] || '',
        author: this.blog.name || ''
      }
    },
    async loadSessions() {
      this.loadingSessions = true
      try {
        this.sessions = (await getSessions()) || []
      } catch (e) {
        // toast 已统一处理
        this.sessions = []
      } finally {
        this.loadingSessions = false
      }
    },
    /** 等待 WS 就绪（分享页可能从未连过聊天），最多等 3 秒 */
    waitConnected(timeout = 3000) {
      connectWS()   // 幂等，已连接则跳过
      if (isConnected()) return Promise.resolve(true)
      return new Promise((resolve) => {
        const started = Date.now()
        const timer = setInterval(() => {
          if (isConnected() || Date.now() - started > timeout) {
            clearInterval(timer)
            resolve(isConnected())
          }
        }, 200)
      })
    },
    async sendToSession(session) {
      const clientMsgId = `share-${this.blog.id}-${Date.now()}-${Math.floor(Math.random() * 1e6)}`
      const payload = {
        op: 'send',
        clientMsgId,
        sessionId: session.id,
        type: 2,
        content: JSON.stringify(this.cardOf())
      }
      const ready = await this.waitConnected()
      if (!ready) {
        uni.showToast({ title: '网络未连接，请稍后再试', icon: 'none' })
        return
      }
      // 等 ack / reject 回帧确认发送结果
      const un = onWSMessage((frame) => {
        if (!frame || frame.clientMsgId !== clientMsgId) return
        if (frame.op === 'ack') {
          uni.showToast({ title: '已分享', icon: 'success' })
          un()
          this.$emit('close')
        } else if (frame.op === 'reject') {
          uni.showToast({ title: frame.msg || '分享失败', icon: 'none' })
          un()
        }
      })
      sendWS(payload)
      // 兜底：5 秒没回帧提示重试，避免面板卡死
      setTimeout(() => un(), 5000)
    },
    systemShare() {
      const path = `/pages/user/blog/blogDetail?id=${this.blog.id}`
      // #ifdef H5
      this.copyLink()
      // #endif
      // #ifndef H5
      uni.share({
        provider: 'weixin',
        scene: 'session',
        type: 0,
        title: this.cardOf().title,
        imageUrl: this.cardOf().cover,
        path,
        success: () => this.$emit('close'),
        fail: () => uni.showToast({ title: '分享未完成', icon: 'none' })
      })
      // #endif
    },
    copyLink() {
      uni.setClipboardData({
        data: `/pages/user/blog/blogDetail?id=${this.blog.id}`,
        success: () => {
          uni.showToast({ title: '链接已复制', icon: 'success' })
          this.$emit('close')
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.share-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}

.share-panel {
  width: 100%;
  border-radius: $radius-card $radius-card 0 0;
  padding: 32rpx 28rpx calc(32rpx + env(safe-area-inset-bottom));
}

.share-panel__title {
  display: block;
  text-align: center;
  font-size: 17px;
  font-weight: 700;
  margin-bottom: 24rpx;
}

.share-panel__section-title {
  display: block;
  color: $text-sub;
  font-size: 13px;
  margin-bottom: 12rpx;
}

.share-panel__sessions {
  max-height: 40vh;
}

.share-panel__session {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
}

.share-panel__avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: $brand-bg-2;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.share-panel__session-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.share-panel__name {
  font-size: 15px;
  font-weight: 600;
}

.share-panel__type {
  margin-top: 4rpx;
  color: $text-sub;
  font-size: 12px;
}

.share-panel__send {
  color: $brand-primary;
  font-size: 14px;
  font-weight: 600;
}

.share-panel__empty {
  padding: 32rpx 0;
  text-align: center;
  color: $text-sub;
  font-size: 13px;
}

.share-panel__channels {
  display: flex;
  gap: 20rpx;
  margin-top: 24rpx;
}

.share-panel__channel {
  flex: 1;
  height: 76rpx;
  line-height: 72rpx;
  border-radius: $radius-btn;
  background: $brand-bg-2;
  color: $text-main;
  font-size: 14px;

  &::after {
    border: none;
  }
}

.share-panel__cancel {
  margin-top: 20rpx;
  height: 76rpx;
  line-height: 72rpx;
  border-radius: $radius-btn;
  background: transparent;
  border: 1px solid $brand-line;
  color: $text-sub;
  font-size: 14px;

  &::after {
    border: none;
  }
}
</style>
