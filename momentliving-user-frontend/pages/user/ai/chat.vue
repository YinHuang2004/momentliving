<template>
  <view class="ai-chat">
    <!-- 顶部：会话切换 -->
    <view class="ai-chat__top">
      <picker mode="selector" :range="conversationTitles" @change="onPickConversation">
        <view class="ai-chat__conv-btn">{{ currentTitle || '选择会话' }} ▾</view>
      </picker>
      <view class="ai-chat__new" @click="newConversation">＋ 新对话</view>
    </view>

    <!-- 消息列表 -->
    <scroll-view class="ai-chat__list" scroll-y :scroll-into-view="scrollInto">
      <view class="ai-chat__welcome" v-if="!messages.length">
        <text class="ai-chat__welcome-title">你好，我是 AI 助手「小评」🍜</text>
        <text class="ai-chat__welcome-sub">可以问我：附近有什么好吃的火锅？我买的券怎么退款？帮我写一篇探店笔记…</text>
      </view>
      <view v-for="(m, i) in messages" :key="i" :id="`msg-${i}`">
        <!-- 用户消息 -->
        <view class="ai-chat__row ai-chat__row--user" v-if="m.role === 'user'">
          <view class="ai-chat__bubble ai-chat__bubble--user">{{ m.content }}</view>
        </view>
        <!-- AI 消息 -->
        <view class="ai-chat__row" v-else>
          <view class="ai-chat__bubble ai-chat__bubble--ai">{{ m.content }}<text v-if="m.typing" class="ai-chat__cursor">▌</text></view>
        </view>
      </view>
      <view class="ai-chat__bottom-space"></view>
    </scroll-view>

    <!-- 输入区 -->
    <view class="ai-chat__input-bar">
      <input class="ai-chat__input" v-model="input" confirm-type="send"
             placeholder="问问小评：附近有什么好吃的？" @confirm="send" :disabled="sending" />
      <button class="ai-chat__send" :disabled="sending || !input.trim()" @click="send">发送</button>
    </view>
    <view class="ai-chat__disclaimer">AI 生成内容仅供参考</view>
  </view>
</template>

<script>
import { chatStream, conversations, conversationMessages } from '@/api/ai.js'

export default {
  data() {
    return {
      input: '',
      sending: false,
      messages: [],           // [{role:'user'|'assistant', content, typing?}]
      conversationId: null,
      conversationList: [],   // [{id,title,lastMessage,updatedAt}]
      currentTitle: '',
      scrollInto: ''
    }
  },
  computed: {
    conversationTitles() {
      return this.conversationList.map((c) => c.title || '新对话')
    }
  },
  onLoad() {
    this.loadConversations()
  },
  methods: {
    async loadConversations() {
      try {
        this.conversationList = (await conversations()) || []
      } catch (e) { /* 未登录等场景 request 层已 toast */ }
    },
    onPickConversation(e) {
      const conv = this.conversationList[e.detail.value]
      if (!conv) return
      this.openConversation(conv)
    },
    async openConversation(conv) {
      this.conversationId = conv.id
      this.currentTitle = conv.title
      this.messages = []
      try {
        const list = (await conversationMessages(conv.id)) || []
        this.messages = list
          .filter((m) => m.role !== 'system')
          .map((m) => ({ role: m.role, content: m.content }))
        this.scrollBottom()
      } catch (e) { /* toast 已提示 */ }
    },
    newConversation() {
      this.conversationId = null
      this.currentTitle = ''
      this.messages = []
    },
    send() {
      const text = this.input.trim()
      if (!text || this.sending) return
      this.sending = true
      this.input = ''
      this.messages.push({ role: 'user', content: text })
      const aiMsg = { role: 'assistant', content: '', typing: true }
      this.messages.push(aiMsg)
      this.scrollBottom()

      chatStream(text, this.conversationId, {
        onMeta: (conversationId) => {
          this.conversationId = conversationId
        },
        onChunk: (chunk) => {
          aiMsg.content += chunk
          this.scrollBottom()
        },
        onDone: () => {
          aiMsg.typing = false
          this.sending = false
          this.loadConversations()
        },
        onError: (msg) => {
          aiMsg.typing = false
          aiMsg.content = aiMsg.content || msg
          this.sending = false
        }
      }).catch(() => {
        aiMsg.typing = false
        this.sending = false
      })
    },
    scrollBottom() {
      this.$nextTick(() => {
        this.scrollInto = `msg-${this.messages.length - 1}`
      })
    }
  }
}
</script>

<style scoped>
.ai-chat {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f6f6f2;
}
.ai-chat__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx;
  background: #6b8e5a;
  color: #fff;
}
.ai-chat__conv-btn {
  font-size: 28rpx;
  max-width: 400rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ai-chat__new {
  font-size: 26rpx;
  background: #d4a574;
  color: #fff;
  padding: 8rpx 24rpx;
  border-radius: 999rpx;
}
.ai-chat__list {
  flex: 1;
  padding: 24rpx;
  box-sizing: border-box;
}
.ai-chat__welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 120rpx;
  gap: 16rpx;
}
.ai-chat__welcome-title { font-size: 34rpx; font-weight: 600; color: #3a4a34; }
.ai-chat__welcome-sub {
  font-size: 26rpx; color: #8a9a82; text-align: center;
  padding: 0 60rpx; line-height: 1.6;
}
.ai-chat__row { display: flex; margin-bottom: 24rpx; }
.ai-chat__row--user { justify-content: flex-end; }
.ai-chat__bubble {
  max-width: 78%;
  padding: 20rpx 26rpx;
  border-radius: 20rpx;
  font-size: 28rpx;
  line-height: 1.6;
  word-break: break-all;
  white-space: pre-wrap;
}
.ai-chat__bubble--user { background: #6b8e5a; color: #fff; border-top-right-radius: 6rpx; }
.ai-chat__bubble--ai { background: #fff; color: #333; border-top-left-radius: 6rpx; }
.ai-chat__cursor { color: #6b8e5a; animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0; } }
.ai-chat__bottom-space { height: 20rpx; }
.ai-chat__input-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  background: #fff;
  border-top: 1rpx solid #eee;
}
.ai-chat__input {
  flex: 1;
  background: #f2f2ec;
  border-radius: 999rpx;
  padding: 16rpx 28rpx;
  font-size: 28rpx;
}
.ai-chat__send {
  background: #6b8e5a;
  color: #fff;
  font-size: 26rpx;
  line-height: 2.2;
  padding: 0 30rpx;
  border-radius: 999rpx;
}
.ai-chat__send[disabled] { background: #c5cdbf; color: #fff; }
.ai-chat__disclaimer {
  text-align: center;
  font-size: 20rpx;
  color: #b0b8a8;
  padding-bottom: env(safe-area-inset-bottom);
}
</style>
