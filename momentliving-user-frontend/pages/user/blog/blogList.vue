<template>
  <view class="blog-list">
    <!-- 朋友圈式自定义导航：左=我的博客（头像） 右=发布（相机） -->
    <view class="blog-nav">
      <view class="blog-nav__status"></view>
      <view class="blog-nav__body">
        <view class="blog-nav__left" @click="goMyBlogs">
          <image class="blog-nav__avatar" :src="myAvatar" mode="aspectFill" />
        </view>
        <text class="blog-nav__title">博客</text>
        <view class="blog-nav__right" @click="goPublish">
          <text class="blog-nav__camera">📷</text>
        </view>
      </view>
      <!-- 内容源切换：热榜 / 好友动态 -->
      <view class="blog-tabs">
        <text class="blog-tabs__item" :class="{ 'is-active': mode === 'hot' }" @click="switchMode('hot')">
          热榜
        </text>
        <text class="blog-tabs__item" :class="{ 'is-active': mode === 'follow' }" @click="switchMode('follow')">
          好友动态
        </text>
      </view>
      <!-- 收藏/喜欢列表入口 -->
      <view class="blog-entries">
        <view class="blog-entries__btn" @click="goCollect('favorite')">
          <text class="blog-entries__icon">⭐</text>
          <text class="blog-entries__label">我的收藏</text>
        </view>
        <view class="blog-entries__btn" @click="goCollect('like')">
          <text class="blog-entries__icon">❤️</text>
          <text class="blog-entries__label">我的喜欢</text>
        </view>
      </view>
    </view>

    <BlogCard v-for="b in blogs" :key="b.id" :blog="b" @like="handleLike" @click="goDetail" @author="goAuthor" />
    <EmptyView
      v-if="!loading && blogs.length === 0"
      :text="mode === 'hot' ? '还没有博客，点右上角相机发布第一条吧' : '还没有关注的人发布动态，去热榜逛逛吧'"
    />
    <view class="blog-list__loading" v-if="loading">
      <text>加载中…</text>
    </view>
    <view class="blog-list__nomore" v-if="!loading && !hasMore && blogs.length > 0">
      <text>— 没有更多了 —</text>
    </view>
  </view>
</template>

<script>
import BlogCard from '@/components/BlogCard.vue'
import EmptyView from '@/components/EmptyView.vue'
import { hotBlogs, followBlogs, likeBlog } from '@/api/blog.js'
import { getToken } from '@/utils/request.js'

/**
 * Tab3 博客流：顶部切换"热榜 / 好友动态"两个内容源（消息页不再提供动态入口）
 * - 热榜：GET /blog/hot 分页（current）
 * - 好友动态：GET /blog/of/follow 推模式游标（lastMaxTime=上一页 minTime）
 */
export default {
  components: { BlogCard, EmptyView },
  data() {
    return {
      mode: 'hot',
      // 热榜：页码分页
      hotList: [],
      hotCurrent: 1,
      hotHasMore: true,
      // 好友动态：时间戳游标
      followList: [],
      lastMaxTime: 0,
      followHasMore: true,
      loading: false,
      myAvatar: '/static/logo.png'
    }
  },
  computed: {
    blogs() {
      return this.mode === 'hot' ? this.hotList : this.followList
    },
    hasMore() {
      return this.mode === 'hot' ? this.hotHasMore : this.followHasMore
    }
  },
  onLoad() {
    // 详情页点赞后广播事件，这里同步两种内容源里对应卡片的状态
    uni.$on('blogLikeChanged', this.syncLike)
    // 编辑页保存后广播事件，同步卡片的标题/正文/图片
    uni.$on('blogEdited', this.syncEdit)
  },
  onUnload() {
    uni.$off('blogLikeChanged', this.syncLike)
    uni.$off('blogEdited', this.syncEdit)
  },
  onShow() {
    if (!getToken()) {
      uni.reLaunch({ url: '/pages/user/login/login' })
      return
    }
    // 头像用本地缓存的登录信息，进"我的博客"页后会拿到最新值
    const userInfo = uni.getStorageSync('userInfo')
    if (userInfo && userInfo.images) this.myAvatar = userInfo.images
    this.loadIfEmpty()
  },
  onReachBottom() {
    this.loadBlogs()
  },
  onPullDownRefresh() {
    this.resetActive()
    this.loadBlogs().finally(() => uni.stopPullDownRefresh())
  },
  methods: {
    loadIfEmpty() {
      const empty = this.mode === 'hot' ? this.hotList.length === 0 : this.followList.length === 0
      if (empty) {
        this.loadBlogs()
      }
    },
    switchMode(mode) {
      if (this.mode === mode) return
      this.mode = mode
      this.loadIfEmpty()
    },
    resetActive() {
      if (this.mode === 'hot') {
        this.hotCurrent = 1
        this.hotList = []
        this.hotHasMore = true
      } else {
        this.followList = []
        this.lastMaxTime = Date.now()
        this.followHasMore = true
      }
    },
    async loadBlogs() {
      if (this.loading || !this.hasMore) return
      this.loading = true
      try {
        if (this.mode === 'hot') {
          const list = await hotBlogs(this.hotCurrent)
          this.hotList = this.hotCurrent === 1 ? list || [] : this.hotList.concat(list || [])
          this.hotHasMore = (list || []).length >= 10
          this.hotCurrent++
        } else {
          if (this.lastMaxTime === 0) {
            this.lastMaxTime = Date.now()
          }
          const res = await followBlogs({ lastMaxTime: this.lastMaxTime })
          const list = (res && res.list) || []
          this.followList = this.followList.concat(list)
          // 后端 ScrollResult.minTime 为毫秒时间戳（Long），直接作为下一页 lastId 游标
          if (res && res.minTime) {
            this.lastMaxTime = res.minTime
          }
          this.followHasMore = list.length > 0
        }
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    /** 同步详情页广播的点赞状态到两种内容源的卡片 */
    syncLike(payload) {
      for (const list of [this.hotList, this.followList]) {
        const blog = list.find((b) => String(b.id) === String(payload.id))
        if (blog) {
          blog.isLike = payload.isLike
          blog.liked = payload.liked
        }
      }
    },
    /** 同步编辑页广播的最新内容到卡片 */
    syncEdit(payload) {
      for (const list of [this.hotList, this.followList]) {
        const blog = list.find((b) => String(b.id) === String(payload.id))
        if (blog && payload.title !== undefined) {
          blog.title = payload.title
          blog.content = payload.content
          blog.images = payload.images
        }
      }
    },
    async handleLike(blog) {
      try {
        await likeBlog(blog.id)
        // 本地取反，刷新点赞数
        blog.isLike = !blog.isLike
        blog.liked = Math.max((Number(blog.liked) || 0) + (blog.isLike ? 1 : -1), 0)
      } catch (e) {
        // toast 已统一处理
      }
    },
    goDetail(blog) {
      uni.navigateTo({ url: `/pages/user/blog/blogDetail?id=${blog.id}` })
    },
    /** 点卡片作者 → 个人主页 */
    goAuthor(blog) {
      uni.navigateTo({ url: `/pages/user/userHome/userHome?userId=${blog.userId}` })
    },
    goMyBlogs() {
      uni.navigateTo({ url: '/pages/user/blog/myBlogs' })
    },
    /** 我的收藏（收藏的博客）/ 我的喜欢（点赞过的博客）子页面 */
    goCollect(type) {
      uni.navigateTo({ url: `/pages/user/blog/blogCollect?type=${type}` })
    },
    goPublish() {
      uni.navigateTo({ url: '/pages/user/blog/blogPublish' })
    }
  }
}
</script>

<style lang="scss" scoped>
.blog-list {
  min-height: 100vh;
  padding: 0 24rpx 24rpx;
}

.blog-nav {
  background: $brand-bg;
  position: sticky;
  top: 0;
  z-index: 10;

  &__status {
    height: var(--status-bar-height);
  }

  &__body {
    height: 88rpx;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__left {
    width: 120rpx;
    display: flex;
  }

  &__avatar {
    width: 64rpx;
    height: 64rpx;
    border-radius: 16rpx;
    background: $brand-bg-2;
  }

  &__title {
    font-size: 18px;
    font-weight: 700;
    color: $text-main;
  }

  &__right {
    width: 120rpx;
    display: flex;
    justify-content: flex-end;
  }

  &__camera {
    font-size: 24px;
  }
}

.blog-tabs {
  display: flex;
  justify-content: center;
  gap: 48rpx;
  padding: 12rpx 0 16rpx;

  &__item {
    font-size: 15px;
    color: $text-sub;
    padding: 8rpx 8rpx;
    border-bottom: 4rpx solid transparent;

    &.is-active {
      color: $text-main;
      font-weight: 700;
      border-bottom-color: $brand-primary;
    }
  }
}

.blog-entries {
  display: flex;
  gap: 20rpx;
  padding-bottom: 16rpx;

  &__btn {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8rpx;
    height: 60rpx;
    border-radius: 999rpx;
    background: $brand-bg-2;
  }

  &__icon {
    font-size: 13px;
  }

  &__label {
    font-size: 13px;
    color: $text-main;
  }
}

.blog-list__loading,
.blog-list__nomore {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;
  color: $text-sub;
  font-size: 13px;
}
</style>
