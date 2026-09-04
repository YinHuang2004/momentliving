<template>
  <view class="blog-detail" v-if="blog">
    <NavBar title="博客详情" back />

    <scroll-view scroll-y class="blog-detail__scroll">
      <!-- 作者信息 -->
      <view class="blog-detail__author">
        <view class="blog-detail__author-main" @click="goAuthor">
          <image class="blog-detail__avatar" :src="blog.authorImages || '/static/logo.png'" mode="aspectFill" />
          <text class="blog-detail__nick">{{ blog.name || '匿名用户' }}</text>
        </view>
        <button
          v-if="blog.userId && blog.userId !== myId"
          class="blog-detail__follow-btn"
          :class="{ 'is-followed': isFollowed }"
          @click="handleFollow"
        >
          {{ isFollowed ? '已关注' : '关注' }}
        </button>
        <button v-else-if="isAuthor" class="blog-detail__follow-btn" @click="handleEdit">
          编辑
        </button>
      </view>

      <!-- 大图 -->
      <swiper v-if="images.length > 0" class="blog-detail__swiper" indicator-dots circular>
        <swiper-item v-for="(img, i) in images" :key="i">
          <image class="blog-detail__img" :src="img" mode="aspectFill" />
        </swiper-item>
      </swiper>

      <!-- 标题与正文 -->
      <view class="blog-detail__body">
        <text class="blog-detail__title" v-if="blog.title">{{ blog.title }}</text>
        <text class="blog-detail__content">{{ blog.content }}</text>
        <text class="blog-detail__time">{{ formatTime(blog.createTime) }}</text>
      </view>

      <!-- 评论列表 -->
      <view class="blog-detail__comments">
        <text class="blog-detail__comments-title">评论 {{ comments.length }}</text>
        <view class="blog-detail__comment" v-for="c in comments" :key="c.id">
          <text class="blog-detail__comment-content">{{ c.content }}</text>
          <view class="blog-detail__comment-foot">
            <text class="blog-detail__comment-time">{{ formatTime(c.createTime) }}</text>
            <!-- 仅本人评论可删（后端同样校验归属） -->
            <text
              class="blog-detail__comment-del"
              v-if="Number(c.userId) === Number(myId)"
              @click="handleDeleteComment(c)"
            >删除</text>
          </view>
        </view>
        <EmptyView v-if="comments.length === 0" text="还没有评论，来抢沙发" />
      </view>
      <view style="height: 140rpx"></view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view class="blog-detail__footer">
      <input
        class="blog-detail__input"
        v-model="commentText"
        placeholder="说点什么…"
        placeholder-class="blog-detail__placeholder"
        confirm-type="send"
        @confirm="handleComment"
      />
      <view class="blog-detail__chat" v-if="blog.userId && blog.userId !== myId" @click="handleChat">
        <text class="blog-detail__chat-text">聊一聊</text>
      </view>
      <view class="blog-detail__fav" @click="handleFavorite">
        <text class="blog-detail__fav-icon" :class="{ 'is-fav': isFav }">{{ isFav ? '★' : '☆' }}</text>
        <text class="blog-detail__like-count">{{ isFav ? '已收藏' : '收藏' }}</text>
      </view>
      <view class="blog-detail__like" @click="handleLike">
        <text class="blog-detail__like-icon" :class="{ 'is-liked': blog.isLike }">{{ blog.isLike ? '♥' : '♡' }}</text>
        <text class="blog-detail__like-count">{{ likeCountText }}</text>
      </view>
      <view class="blog-detail__share" @click="showShare = true">
        <text class="blog-detail__share-icon">↗</text>
        <text class="blog-detail__like-count">分享</text>
      </view>
    </view>

    <!-- 分享面板：分享给好友(聊天博客卡片) / 系统分享 / 复制链接 -->
    <SharePanel :visible="showShare" :blog="blog || {}" @close="showShare = false" />
  </view>
</template>

<script>
import NavBar from '@/components/NavBar.vue'
import EmptyView from '@/components/EmptyView.vue'
import SharePanel from '@/components/SharePanel.vue'
import { getBlog, likeBlog, favoriteBlog, isBlogFavorite, blogLikes, listComments, addComment, deleteComment } from '@/api/blog.js'
import { isFollow, follow } from '@/api/user.js'
import { ensureSingle } from '@/api/chat.js'

/**
 * 博客详情：作者+关注、大图轮播、正文、点赞/评论、"聊一聊"进单聊、分享面板
 * 分享三通道：好友(聊天博客卡片，见 SharePanel)、小程序右上角转发(onShareAppMessage)、H5 复制链接
 */
export default {
  components: { NavBar, EmptyView, SharePanel },
  data() {
    return {
      blog: null,
      isFollowed: false,
      isFav: false,
      comments: [],
      commentText: '',
      myId: 0,
      showShare: false
    }
  },
  computed: {
    isAuthor() {
      return !!(this.blog && this.blog.userId && Number(this.blog.userId) === Number(this.myId))
    },
    images() {
      return (this.blog && this.blog.images ? this.blog.images : '').split(',').filter(Boolean)
    },
    likeCountText() {
      const n = Number(this.blog && this.blog.liked) || 0
      return n >= 1000 ? `${(n / 1000).toFixed(1)}k` : String(n)
    }
  },
  // 小程序/ App 右上角转发（H5 不触发）；面板里的"分享给好友"走聊天卡片，见 SharePanel
  onShareAppMessage() {
    const b = this.blog || {}
    const cover = (b.images || '').split(',').filter(Boolean)[0] || ''
    return {
      title: b.title || '一刻生活 · 笔记分享',
      imageUrl: cover,
      path: `/pages/user/blog/blogDetail?id=${this.blogId}`
    }
  },
  async onLoad(options) {
    this.blogId = options.id
    // 参数缺失（如上游传了 undefined）时直接返回，避免发出必失败的请求
    if (!this.blogId || this.blogId === 'undefined') {
      uni.showToast({ title: '博客不存在', icon: 'none' })
      setTimeout(() => uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/index/index' }) }), 800)
      return
    }
    this.myId = (uni.getStorageSync('userInfo') || {}).id || 0
    // 编辑页保存后广播事件，这里重新拉取详情
    uni.$on('blogEdited', this.onBlogEdited)
    try {
      this.blog = await getBlog(this.blogId)
      // 点赞状态兜底：详情接口已带 isLike；字段缺失时（历史数据）用 blogLikes 回查当前用户是否点过赞
      if (this.blog && this.blog.isLike == null) {
        try {
          const likedUsers = (await blogLikes(this.blogId)) || []
          this.blog.isLike = likedUsers.some((u) => Number(u.id) === Number(this.myId))
        } catch (e) {
          this.blog.isLike = false
        }
      }
      // 是否已关注作者
      if (this.blog.userId) {
        this.isFollowed = (await isFollow(this.blog.userId)) || false
      }
      // 是否已收藏：详情接口带 isFavorite；历史数据字段缺失时单独回查
      if (this.blog.isFavorite == null) {
        try {
          this.isFav = (await isBlogFavorite(this.blogId)) || false
        } catch (e) {
          this.isFav = false
        }
      } else {
        this.isFav = this.blog.isFavorite
      }
      this.loadComments()
    } catch (e) {
      // toast 已统一处理
    }
  },
  onUnload() {
    uni.$off('blogEdited', this.onBlogEdited)
  },
  methods: {
    async onBlogEdited(payload) {
      if (String(payload.id) !== String(this.blogId)) return
      try {
        this.blog = await getBlog(this.blogId)
      } catch (e) {
        // toast 已统一处理
      }
    },
    handleEdit() {
      uni.navigateTo({ url: `/pages/user/blog/blogPublish?id=${this.blog.id}` })
    },
    /** 点作者头像/昵称 → 个人主页 */
    goAuthor() {
      uni.navigateTo({ url: `/pages/user/userHome/userHome?userId=${this.blog.userId}` })
    },
    async loadComments() {
      try {
        this.comments = (await listComments(this.blogId)) || []
      } catch (e) {
        // toast 已统一处理
      }
    },
    async handleDeleteComment(c) {
      const confirmed = await new Promise((resolve) => {
        uni.showModal({
          title: '删除评论',
          content: '确定删除这条评论吗？',
          success: (res) => resolve(res.confirm),
          fail: () => resolve(false)
        })
      })
      if (!confirmed) return
      try {
        await deleteComment(c.id)
        uni.showToast({ title: '已删除', icon: 'none' })
        this.loadComments()
      } catch (e) {
        // toast 已统一处理
      }
    },
    async handleFollow() {
      try {
        // PUT /follow/{id} 是 toggle：已关注则取关、未关注则关注
        await follow(this.blog.userId)
        this.isFollowed = !this.isFollowed
        uni.showToast({ title: this.isFollowed ? '关注成功' : '已取消关注', icon: 'none' })
      } catch (e) {
        // toast 已统一处理
      }
    },
    async handleLike() {
      try {
        await likeBlog(this.blog.id)
        this.blog.isLike = !this.blog.isLike
        this.blog.liked = Math.max((Number(this.blog.liked) || 0) + (this.blog.isLike ? 1 : -1), 0)
        // 通知所有列表页同步该博客的点赞状态（返回列表时卡片红心立即一致）
        uni.$emit('blogLikeChanged', {
          id: this.blog.id,
          isLike: this.blog.isLike,
          liked: this.blog.liked
        })
      } catch (e) {
        // toast 已统一处理
      }
    },
    /** 收藏/取消收藏（PUT /blog/favorite/{id} toggle，返回操作后的收藏状态） */
    async handleFavorite() {
      try {
        const fav = await favoriteBlog(this.blog.id)
        this.isFav = !!fav
        uni.showToast({ title: fav ? '已收藏' : '已取消收藏', icon: 'none' })
      } catch (e) {
        // toast 已统一处理
      }
    },
    /** 聊一聊：ensureSingle 创建/获取单聊会话（幂等），进聊天室发首条（发完锁定等对方回复） */
    async handleChat() {
      try {
        const res = await ensureSingle(this.blog.userId)
        const name = encodeURIComponent(res.peerName || this.blog.name || '用户')
        uni.navigateTo({
          url: `/pages/user/chat/chatRoom?sessionId=${res.id}&type=1&peerId=${this.blog.userId}&peerName=${name}`
        })
      } catch (e) {
        // toast 已统一处理
      }
    },
    async handleComment() {
      const content = (this.commentText || '').trim()
      if (!content) return
      try {
        await addComment({ blogId: this.blogId, content })
        this.commentText = ''
        uni.showToast({ title: '评论成功', icon: 'none' })
        this.loadComments()
      } catch (e) {
        // toast 已统一处理
      }
    },
    formatTime(t) {
      return (t || '').replace('T', ' ').slice(0, 16)
    }
  }
}
</script>

<style lang="scss" scoped>
.blog-detail {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.blog-detail__scroll {
  flex: 1;
  min-height: 0;
}

.blog-detail__author {
  display: flex;
  align-items: center;
  padding: 24rpx;
}

/* 头像+昵称整体可点，进作者个人主页 */
.blog-detail__author-main {
  display: flex;
  align-items: center;
  flex: 1;
}

.blog-detail__avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: $brand-bg-2;
  margin-right: 16rpx;
}

.blog-detail__nick {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
}

.blog-detail__follow-btn {
  height: 56rpx;
  line-height: 52rpx;
  padding: 0 32rpx;
  border-radius: $radius-btn;
  background: transparent;
  border: 1px solid $text-sub;
  color: $text-sub;
  font-size: 13px;

  &::after {
    border: none;
  }

  &.is-followed {
    border-color: $brand-line;
    color: $text-sub;
    opacity: 0.6;
  }
}

.blog-detail__swiper {
  height: 460rpx;
  margin: 0 24rpx;
  border-radius: $radius-card;
  overflow: hidden;
}

.blog-detail__img {
  width: 100%;
  height: 100%;
}

.blog-detail__body {
  padding: 28rpx 32rpx;
}

.blog-detail__title {
  display: block;
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 16rpx;
}

.blog-detail__content {
  display: block;
  font-size: 15px;
  line-height: 1.8;
  color: $text-main;
}

.blog-detail__time {
  display: block;
  margin-top: 20rpx;
  color: $text-sub;
  font-size: 12px;
}

.blog-detail__comments {
  padding: 0 32rpx 24rpx;
}

.blog-detail__comments-title {
  font-size: 16px;
  font-weight: 700;
}

.blog-detail__comment {
  padding: 20rpx 0;
  border-bottom: 1px solid $brand-line;
}

.blog-detail__comment-content {
  display: block;
  font-size: 14px;
  line-height: 1.6;
}

.blog-detail__comment-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8rpx;
}

.blog-detail__comment-del {
  color: $text-sub;
  font-size: 12px;
}

.blog-detail__comment-time {
  display: block;
  margin-top: 8rpx;
  color: $text-sub;
  font-size: 12px;
}

.blog-detail__footer {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: $brand-bg;
  border-top: 1px solid $brand-line;
  gap: 24rpx;
}

.blog-detail__input {
  flex: 1;
  height: 72rpx;
  background: #ffffff;
  border: 1px solid $brand-line;
  border-radius: $radius-btn;
  padding: 0 28rpx;
  font-size: 14px;
}

.blog-detail__chat {
  flex-shrink: 0;
  padding: 0 20rpx;
  height: 72rpx;
  line-height: 68rpx;
  border: 1px solid $brand-primary;
  border-radius: $radius-btn;
}

.blog-detail__chat-text {
  color: $brand-primary;
  font-size: 13px;
}

.blog-detail__like,
.blog-detail__share,
.blog-detail__fav {
  display: flex;
  align-items: center;
  gap: 6rpx;
  flex-shrink: 0;
}

.blog-detail__fav-icon {
  font-size: 20px;
  color: $text-sub;

  &.is-fav {
    color: $brand-accent;
  }
}

.blog-detail__like-icon {
  font-size: 22px;
  color: $text-sub;

  &.is-liked {
    color: $brand-primary;
  }
}

.blog-detail__share-icon {
  font-size: 20px;
  color: $text-sub;
}

.blog-detail__like-count {
  color: $text-sub;
  font-size: 13px;
}

.blog-detail__placeholder {
  color: $text-sub;
}
</style>
