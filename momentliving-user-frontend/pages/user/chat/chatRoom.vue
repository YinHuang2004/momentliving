<template>
  <view class="chat-room">
    <NavBar title="聊天" />
    <!-- 首条限制提示条：发起方发出首条后、对方回复前锁定 -->
    <view class="chat-room__lock-tip" v-if="!canSend">
      <text>对方回复后即可继续发送</text>
    </view>

    <scroll-view
      class="chat-room__scroll"
      scroll-y
      :scroll-into-view="scrollInto"
      scroll-with-animation
      @scrolltoupper="loadMoreHistory"
      :upper-threshold="60"
    >
      <view class="chat-room__loading" v-if="loadingMore">
        <text>加载更早的消息…</text>
      </view>
      <view
        v-for="(m, idx) in messages"
        :key="m.clientMsgId"
        :id="`msg-${m.clientMsgId}`"
        class="chat-room__item"
        :class="{ 'is-mine': Number(m.senderId) === Number(myId) }"
      >
        <text class="chat-room__time" v-if="showTime(idx)">{{ formatTime(m.createTime) }}</text>
        <view class="chat-room__row">
          <image
            class="chat-room__avatar"
            :src="(m.senderAvatar || '/static/logo.png')"
            mode="aspectFill"
            @click="goUserHome(m)"
          />
          <view class="chat-room__bubble-wrap">
            <!-- 群聊显示发送者昵称 -->
            <text class="chat-room__sender" v-if="type === 2 && Number(m.senderId) !== Number(myId)">{{ m.senderName || '用户' }}</text>

            <!-- 文本 -->
            <view class="chat-room__bubble" :class="{ 'is-fail': m.status === 'fail' }" v-if="m.type === 0">
              <text>{{ m.content }}</text>
            </view>

            <!-- 图片 -->
            <image
              class="chat-room__img"
              :src="m.content"
              mode="widthFix"
              v-else-if="m.type === 1"
              @click="previewImage(m.content)"
            />

            <!-- 博客卡片 -->
            <view class="chat-room__blog-card" v-else-if="m.type === 2" @click="goBlogCard(m)">
              <image class="chat-room__blog-cover" :src="cardOf(m).cover || '/static/logo.png'" mode="aspectFill" />
              <view class="chat-room__blog-info">
                <text class="chat-room__blog-title ellipsis-2">{{ cardOf(m).title || '博客卡片' }}</text>
                <text class="chat-room__blog-author ellipsis-1">@{{ cardOf(m).author || '用户' }}</text>
                <text class="chat-room__blog-jump">查看笔记 ›</text>
              </view>
            </view>

            <text class="chat-room__status" v-if="Number(m.senderId) === Number(myId) && m.status === 'sending'">发送中…</text>
            <text class="chat-room__status is-fail" v-if="Number(m.senderId) === Number(myId) && m.status === 'fail'">发送失败</text>
          </view>
        </view>
      </view>
      <view style="height: 24rpx"></view>
    </scroll-view>

    <!-- 底部输入栏 -->
    <view class="chat-room__footer">
      <view class="chat-room__plus" @click="togglePlus">
        <text class="chat-room__plus-icon">+</text>
      </view>
      <input
        class="chat-room__input"
        v-model="inputText"
        :disabled="!canSend"
        :placeholder="canSend ? '说点什么…' : '对方回复后解锁'"
        placeholder-class="chat-room__placeholder"
        confirm-type="send"
        @confirm="sendText"
      />
      <view class="chat-room__send" :class="{ 'is-disabled': !inputText.trim() || !canSend }" @click="sendText">
        <text class="chat-room__send-text">发送</text>
      </view>
    </view>

    <!-- "+" 扩展面板：图片 / 博客卡片 -->
    <view class="chat-room__panel" v-if="showPlus">
      <view class="chat-room__panel-item" @click="sendImage">
        <text class="chat-room__panel-icon">🖼️</text>
        <text class="chat-room__panel-label">图片</text>
      </view>
      <view class="chat-room__panel-item" @click="pickBlogCard">
        <text class="chat-room__panel-icon">📝</text>
        <text class="chat-room__panel-label">博客卡片</text>
      </view>
    </view>
    <view class="chat-room__mask" v-if="showPlus" @click="showPlus = false"></view>

    <!-- 群聊右上角入口 -->
    <view class="chat-room__group-entry" v-if="type === 2 && groupId" @click="goGroupInfo">
      <text>群</text>
    </view>
  </view>
</template>

<script>
import { getMessages, markRead } from '@/api/chat.js'
import { myBlogs } from '@/api/blog.js'
import { uploadImage } from '@/api/file.js'
import { getMe } from '@/api/user.js'
import { getToken } from '@/utils/request.js'
import { connectWS, sendWS, onWSMessage } from '@/utils/websocket.js'

/**
 * 聊天窗口（单聊/群聊复用）
 *
 * - 通道：发送走 WS {op:'send'}，服务端回 {op:'ack'} 才把本地"发送中"置"已发送"；
 *   收到 {op:'reject'}（如 WAIT_REPLY 首条限制）标失败并锁定输入框
 * - 首条限制：服务端在 /chat/messages 响应里算好 canSend，这里只执行——
 *   canSend=false 锁输入框；收到对方 new_msg 后解锁
 * - 博客卡片：type=2，content 为 {blogId,title,cover,author} JSON，点击跳博客详情
 * - 历史：游标分页（cursor=已加载最旧一条 id），scroll-to-top 触发更早一页
 */
import NavBar from '@/components/NavBar.vue'
export default {
  components: { NavBar },
  data() {
    return {
      sessionId: 0,
      type: 1,
      groupId: '',
      myId: 0,
      messages: [],
      canSend: true,
      inputText: '',
      hasMore: false,
      loadingMore: false,
      showPlus: false,
      scrollInto: '',
      _unsub: null
    }
  },
  onLoad(options) {
    if (!getToken()) {
      uni.reLaunch({ url: '/pages/user/login/login' })
      return
    }
    this.sessionId = Number(options.sessionId)
    this.type = Number(options.type || 1)
    this.groupId = options.groupId || ''
    const title = decodeURIComponent(options.peerName || (this.type === 2 ? '群聊' : '聊天'))
    uni.setNavigationBarTitle({ title })

    const userInfo = uni.getStorageSync('userInfo') || {}
    this.myId = userInfo.id || 0
    // 身份以服务端为准：本地 userInfo 缓存若因换账号没刷新而与 token 不一致，
    // 消息左右判断会整体错位。用 /user/me 的 id 归属气泡，顺带校正本地缓存
    getMe().then((me) => {
      if (me && me.id) {
        this.myId = me.id
        if (String(userInfo.id) !== String(me.id)) {
          uni.setStorageSync('userInfo', { ...userInfo, id: me.id, nickName: me.nickName, images: me.images })
        }
      }
    }).catch(() => {})

    connectWS()   // 幂等
    this.loadHistory()
    this._unsub = onWSMessage(this.handleFrame)
  },
  onUnload() {
    if (this._unsub) {
      this._unsub()
      this._unsub = null
    }
  },
  methods: {
    // ==================== 数据加载 ====================

    async loadHistory() {
      try {
        const res = await getMessages({ sessionId: this.sessionId, size: 20 })
        // 后端返回按 id 倒序（新→旧），展示需要正序
        this.messages = ((res && res.list) || []).slice().reverse()
        this.canSend = !res || res.canSend !== false
        this.hasMore = (res && res.list && res.list.length === 20) || false
        this.markAllRead()
        this.scrollBottom()
      } catch (e) {
        // toast 已统一处理
      }
    },
    /** 触顶加载更早：cursor=当前最旧一条的服务端 id */
    async loadMoreHistory() {
      if (this.loadingMore || !this.messages.length) return
      const oldest = this.messages.find((m) => m.id > 0)
      if (!oldest || !this.hasMore) return
      this.loadingMore = true
      try {
        const res = await getMessages({ sessionId: this.sessionId, cursor: oldest.id, size: 20 })
        const earlier = ((res && res.list) || []).slice().reverse()
        this.messages = earlier.concat(this.messages)
        this.hasMore = (res && res.list && res.list.length === 20) || false
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loadingMore = false
      }
    },
    async markAllRead() {
      try {
        await markRead(this.sessionId)
      } catch (e) {
        // 忽略
      }
    },

    // ==================== WS 帧处理 ====================

    handleFrame(msg) {
      switch (msg.op) {
        case 'new_msg':
          if (Number(msg.msg.sessionId) === Number(this.sessionId)) {
            this.messages.push({ ...msg.msg, clientMsgId: `srv-${msg.msg.id}`, status: 'sent' })
            this.scrollBottom()
            if (Number(msg.msg.senderId) !== Number(this.myId)) {
              // 对方有回复 → 首条限制解除；同时清掉本会话未读
              this.canSend = true
              this.markAllRead()
            }
          }
          break
        case 'ack':
          this.updateLocal(msg.clientMsgId, { id: msg.msgId, status: 'sent' })
          // 服务端随 ack 下发最新 canSend：发起方发完首条立即锁定输入框，不用等第二条被拒
          if (typeof msg.canSend === 'boolean') {
            this.canSend = msg.canSend
          }
          break
        case 'reject':
          this.updateLocal(msg.clientMsgId, { status: 'fail' })
          uni.showToast({ title: msg.msg || '发送失败', icon: 'none' })
          if (msg.code === 'WAIT_REPLY') {
            this.canSend = false
          }
          break
        default:
          break
      }
    },
    updateLocal(clientMsgId, patch) {
      const target = this.messages.find((m) => m.clientMsgId === clientMsgId)
      if (target) {
        Object.assign(target, patch)
      }
    },

    // ==================== 发送 ====================

    sendText() {
      const content = (this.inputText || '').trim()
      if (!content || !this.canSend) return
      this.inputText = ''
      this.showPlus = false
      this.send(content, 0)
    },
    sendImage() {
      this.showPlus = false
      if (!this.canSend) return
      uni.chooseImage({
        count: 1,
        success: async (res) => {
          try {
            const url = await uploadImage(res.tempFilePaths[0], 'chat')
            this.send(url, 1)
          } catch (e) {
            // toast 已统一处理
          }
        }
      })
    },
    /** 选我的一篇笔记 → 生成博客卡片（type=2，content 为卡片 JSON） */
    async pickBlogCard() {
      this.showPlus = false
      if (!this.canSend) return
      try {
        const blogs = (await myBlogs(this.myId)) || []
        if (!blogs.length) {
          uni.showToast({ title: '你还没有发布过笔记', icon: 'none' })
          return
        }
        const titles = blogs.slice(0, 6).map((b) => b.title || b.content || '无标题笔记')
        uni.showActionSheet({
          itemList: titles,
          success: (res) => {
            const b = blogs[res.tapIndex]
            const firstImage = (b.images || '').split(',').filter(Boolean)[0] || ''
            const card = {
              blogId: b.id,
              title: b.title || '无标题笔记',
              cover: firstImage,
              author: b.name || (uni.getStorageSync('userInfo') || {}).nickName || '我'
            }
            this.send(JSON.stringify(card), 2)
          }
        })
      } catch (e) {
        // toast 已统一处理
      }
    },
    /** 本地乐观插入 + WS 发出；等 ack 才算"已发送" */
    send(content, type) {
      const clientMsgId = `${this.myId}-${Date.now()}-${Math.floor(Math.random() * 1e6)}`
      this.messages.push({
        id: 0,
        clientMsgId,
        sessionId: this.sessionId,
        senderId: this.myId,
        senderName: (uni.getStorageSync('userInfo') || {}).nickName,
        senderAvatar: (uni.getStorageSync('userInfo') || {}).images,
        type,
        content,
        createTime: new Date().toISOString(),
        status: 'sending'
      })
      this.scrollBottom()
      connectWS()   // 若恰好断线，先补一针
      sendWS({ op: 'send', clientMsgId, sessionId: this.sessionId, type, content })
    },

    // ==================== 展示辅助 ====================

    togglePlus() {
      this.showPlus = !this.showPlus
    },
    cardOf(m) {
      try {
        return JSON.parse(m.content)
      } catch (e) {
        return {}
      }
    },
    goBlogCard(m) {
      const card = this.cardOf(m)
      if (card.blogId) {
        uni.navigateTo({ url: `/pages/user/blog/blogDetail?id=${card.blogId}` })
      }
    },
    /** 点击消息头像 → 该发送者的用户主页（自己的头像也能进自己主页） */
    goUserHome(m) {
      if (m.senderId) {
        uni.navigateTo({ url: `/pages/user/userHome/userHome?userId=${m.senderId}` })
      }
    },
    goGroupInfo() {
      uni.navigateTo({ url: `/pages/user/chat/groupInfo?groupId=${this.groupId}` })
    },
    previewImage(url) {
      uni.previewImage({ urls: [url] })
    },
    /** 消息间隔超过 5 分钟（或第一条）才显示时间，避免满屏时间戳 */
    showTime(idx) {
      if (idx === 0) return true
      const prev = new Date(this.messages[idx - 1].createTime).getTime()
      const cur = new Date(this.messages[idx].createTime).getTime()
      return isNaN(cur) || isNaN(prev) || cur - prev > 5 * 60 * 1000
    },
    formatTime(t) {
      return String(t || '').replace('T', ' ').slice(0, 16)
    },
    scrollBottom() {
      this.$nextTick(() => {
        const last = this.messages[this.messages.length - 1]
        if (last) {
          this.scrollInto = `msg-${last.clientMsgId}`
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.chat-room {
  height: 100vh;
  display: flex;
  flex-direction: column;
  position: relative;
}

.chat-room__lock-tip {
  background: rgba(212, 165, 116, 0.15);
  color: $brand-accent;
  font-size: 12px;
  text-align: center;
  padding: 10rpx 0;
}

.chat-room__scroll {
  flex: 1;
  min-height: 0;
  padding: 24rpx;
  box-sizing: border-box;
}

.chat-room__loading {
  text-align: center;
  color: $text-sub;
  font-size: 12px;
  padding: 12rpx 0;
}

.chat-room__item {
  margin-bottom: 28rpx;
}

.chat-room__time {
  display: block;
  text-align: center;
  color: $text-sub;
  font-size: 11px;
  margin-bottom: 16rpx;
}

.chat-room__row {
  display: flex;
  align-items: flex-start;
}

// 自己的消息：头像在右、气泡在左换成绿色。
// 注意 is-mine 绑在 __item 上（此处样式必须挂在 __item 层级，
// 挂在 __row 下写成 .chat-room__row.is-mine 会永远匹配不上 → 全部消息靠左的根因）
.chat-room__item.is-mine {
  .chat-room__row {
    flex-direction: row-reverse;
  }

  .chat-room__bubble {
    background: $brand-primary;
    color: #ffffff;
  }

  .chat-room__sender {
    text-align: right;
  }
}

.chat-room__avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: $brand-bg-2;
  flex-shrink: 0;
}

.chat-room__bubble-wrap {
  max-width: 70%;
  margin: 0 16rpx;
}

.chat-room__sender {
  display: block;
  color: $text-sub;
  font-size: 11px;
  margin-bottom: 6rpx;
}

.chat-room__bubble {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 18rpx 24rpx;
  font-size: 15px;
  line-height: 1.6;
  word-break: break-all;
  box-shadow: $shadow-card;

  &.is-fail {
    opacity: 0.5;
    border: 1px dashed $brand-accent;
  }
}

.chat-room__img {
  width: 320rpx;
  border-radius: 20rpx;
  background: $brand-bg-2;
}

.chat-room__blog-card {
  display: flex;
  background: #ffffff;
  border-radius: 20rpx;
  padding: 16rpx;
  box-shadow: $shadow-card;
}

.chat-room__blog-cover {
  width: 120rpx;
  height: 120rpx;
  border-radius: 12rpx;
  background: $brand-bg-2;
  margin-right: 16rpx;
  flex-shrink: 0;
}

.chat-room__blog-info {
  flex: 1;
  min-width: 0;
}

.chat-room__blog-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
}

.chat-room__blog-author {
  display: block;
  color: $text-sub;
  font-size: 12px;
  margin-top: 6rpx;
}

.chat-room__blog-jump {
  display: block;
  color: $brand-primary;
  font-size: 12px;
  margin-top: 6rpx;
}

.chat-room__status {
  display: block;
  color: $text-sub;
  font-size: 11px;
  margin-top: 6rpx;

  &.is-fail {
    color: $brand-accent;
  }
}

.chat-room__footer {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: $brand-bg;
  border-top: 1px solid $brand-line;
}

.chat-room__plus {
  width: 64rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  border: 1px solid $brand-line;
  border-radius: $radius-btn;
}

.chat-room__plus-icon {
  font-size: 22px;
  color: $text-sub;
}

.chat-room__input {
  flex: 1;
  height: 72rpx;
  background: #ffffff;
  border: 1px solid $brand-line;
  border-radius: $radius-btn;
  padding: 0 28rpx;
  font-size: 14px;
}

.chat-room__placeholder {
  color: $text-sub;
}

.chat-room__send {
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 32rpx;
  border-radius: $radius-btn;
  background: $brand-primary;

  &.is-disabled {
    opacity: 0.4;
  }
}

.chat-room__send-text {
  color: #ffffff;
  font-size: 14px;
}

.chat-room__panel {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(120rpx + env(safe-area-inset-bottom));
  display: flex;
  gap: 40rpx;
  padding: 32rpx;
  background: $brand-bg;
  border-top: 1px solid $brand-line;
  z-index: 11;
}

.chat-room__panel-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.chat-room__panel-icon {
  width: 96rpx;
  height: 96rpx;
  line-height: 96rpx;
  text-align: center;
  font-size: 40rpx;
  background: #ffffff;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
}

.chat-room__panel-label {
  color: $text-sub;
  font-size: 12px;
}

.chat-room__mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 10;
}

.chat-room__group-entry {
  position: fixed;
  top: 120rpx;
  right: 24rpx;
  width: 64rpx;
  height: 64rpx;
  line-height: 64rpx;
  text-align: center;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.3);
  color: #ffffff;
  font-size: 13px;
  z-index: 9;
}
</style>
