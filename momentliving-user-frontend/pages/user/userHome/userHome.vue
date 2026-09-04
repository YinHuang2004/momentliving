<template>
  <view class="user-home">
    <NavBar title="个人主页" class="page-nav" />
    <!-- 个人信息头部 -->
    <view class="user-home__head brand-card">
      <view class="user-home__info">
        <image class="user-home__avatar" :src="userInfo.images || '/static/logo.png'" mode="aspectFill" />
        <text class="user-home__nick">{{ userInfo.nickName || '加载中…' }}</text>
      </view>
      <button
        v-if="!isSelf"
        class="user-home__follow-btn"
        :class="{ 'is-followed': isFollowed }"
        @click="handleFollow"
      >
        {{ isFollowed ? '已关注' : '+ 关注' }}
      </button>
    </view>

    <!-- 关注 / 粉丝 / 博客 统计：关注/粉丝点进列表；共同关注仅他人主页展示（没有显示 0） -->
    <view class="user-home__stats brand-card">
      <view class="user-home__stat" @click="goFollowList(1)">
        <text class="user-home__stat-num">{{ count.followee }}</text>
        <text class="user-home__stat-label">关注</text>
      </view>
      <view class="user-home__stat" @click="goFollowList(2)">
        <text class="user-home__stat-num">{{ count.follower }}</text>
        <text class="user-home__stat-label">粉丝</text>
      </view>
      <view class="user-home__stat" v-if="!isSelf" @click="showCommon = true">
        <text class="user-home__stat-num">{{ commonFollows.length }}</text>
        <text class="user-home__stat-label">共同关注</text>
      </view>
      <view class="user-home__stat">
        <text class="user-home__stat-num">{{ count.blog }}</text>
        <text class="user-home__stat-label">博客</text>
      </view>
    </view>

    <!-- 共同关注列表（他人主页，点"共同关注"统计弹出） -->
    <view class="user-home__common-mask" v-if="showCommon" @click="showCommon = false">
      <view class="user-home__common-panel brand-card" @click.stop>
        <text class="user-home__common-title">共同关注（{{ commonFollows.length }}）</text>
        <scroll-view scroll-y class="user-home__common-list">
          <view class="user-home__common-item" v-for="u in commonFollows" :key="u.id" @click="goCommonUser(u)">
            <image class="user-home__common-avatar" :src="u.images || '/static/logo.png'" mode="aspectFill" />
            <text class="user-home__common-name ellipsis-1">{{ u.nickName || '用户' + u.id }}</text>
          </view>
          <view class="user-home__common-empty" v-if="commonFollows.length === 0">还没有共同关注的人</view>
        </scroll-view>
        <button class="user-home__common-close" @click="showCommon = false">关闭</button>
      </view>
    </view>

    <!-- 内容切换：博客 / 足迹 -->
    <view class="user-home__tabs">
      <text class="user-home__tab" :class="{ 'is-active': tab === 'blog' }" @click="switchTab('blog')">博客</text>
      <text class="user-home__tab" :class="{ 'is-active': tab === 'footprint' }" @click="switchTab('footprint')">足迹</text>
    </view>

    <!-- 博客 Tab -->
    <template v-if="tab === 'blog'">
      <BlogCard v-for="b in blogs" :key="b.id" :blog="b" @like="handleLike" @click="goDetail" @author="goAuthor" />
      <EmptyView v-if="!loading && blogs.length === 0" text="TA还没有发布过博客" />
      <view class="user-home__nomore" v-if="!loading && !hasMore && blogs.length > 0">
        <text>— 没有更多了 —</text>
      </view>
    </template>

    <!-- 足迹 Tab -->
    <template v-else>
      <!-- 本人：可见性开关 + 清空按钮 -->
      <view class="user-home__footprint-set brand-card" v-if="isSelf">
        <view class="user-home__set-row">
          <view class="user-home__set-text">
            <text class="user-home__set-title">足迹对他人可见</text>
            <text class="user-home__set-sub">关闭后，其他用户进入你的主页看不到足迹</text>
          </view>
          <switch :checked="footprintVisible" color="#6B8E5A" @change="onVisibleChange" />
        </view>
        <button class="user-home__clear-btn" @click="handleClearFootprint">清空足迹</button>
      </view>

      <!-- 他人：已隐藏足迹 -->
      <view class="user-home__footprint-lock brand-card" v-if="!isSelf && footprintHidden">
        <text class="user-home__lock-icon">🔒</text>
        <text class="user-home__lock-text">TA已隐藏自己的足迹</text>
      </view>

      <!-- 足迹列表（他人的由后端过滤：仅清空时间之后的已支付/已核销记录） -->
      <template v-if="!footprintHidden">
        <view class="user-home__fp-item brand-card" v-for="f in footprints" :key="f.orderId">
          <view class="user-home__fp-info">
            <text class="user-home__fp-title">{{ f.voucherTitle || `券 ${f.voucherId}` }}</text>
            <text class="user-home__fp-time">{{ formatTime(f.payTime || f.createTime) }}</text>
          </view>
          <text class="user-home__fp-status" :class="f.status === 2 ? 'is-used' : 'is-paid'">
            {{ f.status === 2 ? '已核销' : '已支付' }}
          </text>
        </view>
        <EmptyView v-if="!loading && footprints.length === 0" :text="isSelf ? '还没有购物足迹，去买张券吧' : 'TA还没有足迹'" />
        <view class="user-home__nomore" v-if="!loading && !fpHasMore && footprints.length > 0">
          <text>— 没有更多了 —</text>
        </view>
      </template>
    </template>
  </view>
</template>

<script>
import BlogCard from '@/components/BlogCard.vue'
import EmptyView from '@/components/EmptyView.vue'
import { getUserInfo, follow, isFollow, followCount, commonFollow, footprintSettings, updateFootprintVisible, clearFootprint } from '@/api/user.js'
import { myBlogs, likeBlog } from '@/api/blog.js'
import { userFootprint } from '@/api/voucher.js'

/**
 * 个人主页（自己/他人通用）：
 * - 头部：头像/昵称 + 关注按钮（他人）；关注数/粉丝数/博客数
 * - 博客 Tab：该用户的博客列表（后端按 userId 查询，点赞状态为当前用户视角）
 * - 足迹 Tab：购物记录；本人可开关"对他人可见"并清空；他人查看受后端可见性校验
 */
import NavBar from '@/components/NavBar.vue'
export default {
  components: { NavBar, BlogCard, EmptyView },
  data() {
    return {
      userId: null,
      isSelf: false,
      commonFollows: [],
      showCommon: false,
      userInfo: {},
      count: { followee: 0, follower: 0, blog: 0 },
      isFollowed: false,
      tab: 'blog',
      // 博客分页
      blogs: [],
      blogCurrent: 1,
      hasMore: true,
      // 足迹分页
      footprints: [],
      fpCurrent: 1,
      fpHasMore: true,
      footprintVisible: true,
      footprintHidden: false,
      loading: false
    }
  },
  onLoad(options) {
    this.userId = options.userId || uni.getStorageSync('userInfo').id
    this.isSelf = String(this.userId) === String(uni.getStorageSync('userInfo').id)
  },
  onShow() {
    this.loadAll()
  },
  onReachBottom() {
    if (this.tab === 'blog') {
      this.loadBlogs()
    } else {
      this.loadFootprints()
    }
  },
  methods: {
    async loadAll() {
      this.loading = true
      try {
        // 个人信息 + 统计并行加载
        const infoP = getUserInfo(this.userId)
        const countP = followCount(this.userId)
        this.userInfo = (await infoP) || {}
        const c = (await countP) || {}
        this.count.followee = c.followee || 0
        this.count.follower = c.follower || 0

        if (!this.isSelf) {
          this.isFollowed = (await isFollow(this.userId)) || false
          // 共同关注（失败不阻断主页加载）
          try {
            this.commonFollows = (await commonFollow(this.userId)) || []
          } catch (e) {
            this.commonFollows = []
          }
        } else {
          // 本人：读取足迹可见性开关
          const s = (await footprintSettings()) || {}
          this.footprintVisible = s.visible !== false
        }
        await this.loadBlogs(true)
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    async loadBlogs(reset = false) {
      if (this.loading && !reset) return
      if (reset) {
        this.blogCurrent = 1
        this.hasMore = true
      }
      if (!this.hasMore) return
      this.loading = true
      try {
        const list = await myBlogs(this.userId, this.blogCurrent)
        this.blogs = this.blogCurrent === 1 ? list || [] : this.blogs.concat(list || [])
        this.hasMore = (list || []).length >= 10
        this.count.blog = this.blogs.length < 10 && this.blogCurrent === 1 ? this.blogs.length : this.count.blog
        this.blogCurrent++
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    async loadFootprints(reset = false) {
      if (this.loading && !reset) return
      if (reset) {
        this.fpCurrent = 1
        this.fpHasMore = true
      }
      if (!this.fpHasMore) return
      this.loading = true
      try {
        const list = await userFootprint(this.userId, this.fpCurrent)
        this.footprints = this.fpCurrent === 1 ? list || [] : this.footprints.concat(list || [])
        this.fpHasMore = (list || []).length >= 10
        this.fpCurrent++
      } catch (e) {
        // 后端"该用户已隐藏足迹"等业务异常在这里体现为请求失败
        if (!this.isSelf) {
          this.footprintHidden = true
        }
      } finally {
        this.loading = false
      }
    },
    switchTab(tab) {
      if (this.tab === tab) return
      this.tab = tab
      // 首次切到足迹时加载
      if (tab === 'footprint' && this.footprints.length === 0 && !this.footprintHidden) {
        this.loadFootprints(true)
      }
    },
    async handleFollow() {
      try {
        await follow(this.userId)
        this.isFollowed = !this.isFollowed
        // 关注数即时 +1/-1
        this.count.follower += this.isFollowed ? 1 : -1
        uni.showToast({ title: this.isFollowed ? '关注成功' : '已取消关注', icon: 'none' })
      } catch (e) {
        // toast 已统一处理
      }
    },
    async onVisibleChange(e) {
      const visible = e.detail.value
      try {
        await updateFootprintVisible(visible)
        this.footprintVisible = visible
        uni.showToast({ title: visible ? '足迹已对他人可见' : '足迹已隐藏', icon: 'none' })
      } catch (err) {
        // 失败时回滚开关（toast 已统一处理）
        this.$nextTick(() => { this.footprintVisible = !visible })
      }
    },
    handleClearFootprint() {
      uni.showModal({
        title: '清空足迹',
        content: '确定清空足迹吗？清空后其他人将看不到你之前的购物记录（订单本身不受影响）',
        confirmColor: '#fa5151',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await clearFootprint()
            this.loadFootprints(true)
            uni.showToast({ title: '已清空', icon: 'success' })
          } catch (e) {
            // toast 已统一处理
          }
        }
      })
    },
    async handleLike(blog) {
      try {
        await likeBlog(blog.id)
        blog.isLike = !blog.isLike
        blog.liked = Math.max((Number(blog.liked) || 0) + (blog.isLike ? 1 : -1), 0)
      } catch (e) {
        // toast 已统一处理
      }
    },
    goDetail(blog) {
      uni.navigateTo({ url: `/pages/user/blog/blogDetail?id=${blog.id}` })
    },
    goAuthor(blog) {
      uni.navigateTo({ url: `/pages/user/userHome/userHome?userId=${blog.userId}` })
    },
    goCommonUser(u) {
      uni.navigateTo({ url: `/pages/user/userHome/userHome?userId=${u.id}` })
    },
    // 点"关注/粉丝"统计进列表页（带上被查看的用户，TA 人也可看自己的）
    goFollowList(type) {
      uni.navigateTo({
        url: `/pages/user/follow/followList?type=${type}&userId=${this.userId}&nickName=${encodeURIComponent(this.userInfo.nickName || '')}`
      })
    },
    formatTime(t) {
      if (!t) return ''
      return String(t).replace('T', ' ').slice(0, 16)
    }
  }
}
</script>

<style lang="scss" scoped>
.user-home {
  min-height: 100vh;
  padding: 24rpx;
}

.user-home__head {
  padding: 32rpx 28rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-home__info {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.user-home__avatar {
  width: 110rpx;
  height: 110rpx;
  border-radius: 50%;
  background: $brand-bg-2;
}

.user-home__nick {
  font-size: 19px;
  font-weight: 700;
  color: $text-main;
}

.user-home__follow-btn {
  min-width: 170rpx;
  margin: 0;
  font-size: 14px;
  color: #fff;
  background: $brand-primary;
  border-radius: 999rpx;

  &.is-followed {
    color: $text-sub;
    background: $brand-bg-2;
  }
}

.user-home__stats {
  margin-top: 20rpx;
  padding: 24rpx 0;
  display: flex;
}

.user-home__stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.user-home__stat-num {
  font-size: 17px;
  font-weight: 700;
  color: $text-main;
}

.user-home__stat-label {
  font-size: 12px;
  color: $text-sub;
}

.user-home__common-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}

.user-home__common-panel {
  width: 100%;
  border-radius: $radius-card $radius-card 0 0;
  padding: 32rpx 28rpx calc(32rpx + env(safe-area-inset-bottom));
}

.user-home__common-title {
  display: block;
  text-align: center;
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 16rpx;
}

.user-home__common-list {
  max-height: 40vh;
}

.user-home__common-item {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
}

.user-home__common-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: $brand-bg-2;
  margin-right: 20rpx;
}

.user-home__common-name {
  flex: 1;
  font-size: 15px;
}

.user-home__common-empty {
  padding: 48rpx 0;
  text-align: center;
  color: $text-sub;
  font-size: 13px;
}

.user-home__common-close {
  margin-top: 20rpx;
  height: 72rpx;
  line-height: 68rpx;
  border-radius: $radius-btn;
  background: transparent;
  border: 1px solid $brand-line;
  color: $text-sub;
  font-size: 14px;

  &::after {
    border: none;
  }
}

.user-home__tabs {
  display: flex;
  justify-content: center;
  gap: 64rpx;
  padding: 20rpx 0;
}

.user-home__tab {
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

.user-home__footprint-set {
  padding: 24rpx 28rpx;
  margin-bottom: 20rpx;
}

.user-home__set-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-home__set-text {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.user-home__set-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-main;
}

.user-home__set-sub {
  font-size: 12px;
  color: $text-sub;
}

.user-home__clear-btn {
  margin: 24rpx 0 0;
  font-size: 14px;
  color: #fa5151;
  background: #ffffff;
  border: 1px solid #fa5151;
  border-radius: 999rpx;
}

.user-home__footprint-lock {
  padding: 64rpx 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
}

.user-home__lock-icon {
  font-size: 32px;
}

.user-home__lock-text {
  color: $text-sub;
  font-size: 14px;
}

.user-home__fp-item {
  padding: 26rpx 28rpx;
  margin-bottom: 16rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-home__fp-info {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.user-home__fp-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-main;
}

.user-home__fp-time {
  font-size: 12px;
  color: $text-sub;
}

.user-home__fp-status {
  font-size: 12px;
  padding: 6rpx 18rpx;
  border-radius: 999rpx;

  &.is-paid {
    color: $brand-primary;
    background: $brand-bg-2;
  }

  &.is-used {
    color: $text-sub;
    background: $brand-bg-2;
  }
}

.user-home__nomore {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;
  color: $text-sub;
  font-size: 13px;
}

/* NavBar 通栏：抵消根容器 padding */
.page-nav {
  margin: -24rpx -24rpx 0;
}
</style>
