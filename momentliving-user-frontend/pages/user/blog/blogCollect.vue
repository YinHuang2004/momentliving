<template>
  <view class="blog-collect">
    <NavBar :title="type === 'favorite' ? '我的收藏' : '我的喜欢'" class="page-nav" />

    <view class="blog-collect__list">
      <view v-for="b in blogs" :key="b.id" class="blog-collect__item">
        <BlogCard :blog="b" @like="handleLike" @click="goDetail" @author="goAuthor" />
      </view>
    </view>

    <EmptyView v-if="!loading && blogs.length === 0" :text="emptyText" />
    <view class="blog-collect__loading" v-if="loading"><text>加载中…</text></view>
  </view>
</template>

<script>
import NavBar from '@/components/NavBar.vue'
import BlogCard from '@/components/BlogCard.vue'
import EmptyView from '@/components/EmptyView.vue'
import { myFavoriteBlogs, myLikedBlogs, likeBlog } from '@/api/blog.js'

/**
 * 我的收藏（?type=favorite）/ 我的喜欢（?type=like）博客列表
 * 入口：博客 Tab 顶部"我的收藏 / 我的喜欢"按钮
 * 数据：GET /blog/of/favorite、GET /blog/of/likes（收藏/点赞规模小，后端一次全量返回）
 */
export default {
  components: { NavBar, BlogCard, EmptyView },
  data() {
    return {
      type: 'favorite',
      blogs: [],
      loading: false
    }
  },
  computed: {
    emptyText() {
      return this.type === 'favorite'
        ? '还没有收藏的博客，去博客详情点⭐收藏吧'
        : '还没有喜欢的博客，去博客详情点♥喜欢吧'
    }
  },
  onLoad(options) {
    this.type = options.type === 'like' ? 'like' : 'favorite'
  },
  onShow() {
    // 每次回到本页都重新拉取：详情页里取消收藏/取消点赞后列表要同步
    this.loadBlogs()
  },
  methods: {
    async loadBlogs() {
      this.loading = true
      try {
        const list = this.type === 'favorite' ? await myFavoriteBlogs() : await myLikedBlogs()
        this.blogs = list || []
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
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
    /** 点卡片作者 → 个人主页 */
    goAuthor(blog) {
      uni.navigateTo({ url: `/pages/user/userHome/userHome?userId=${blog.userId}` })
    }
  }
}
</script>

<style lang="scss" scoped>
.blog-collect {
  min-height: 100vh;
  padding: 24rpx;
}

.blog-collect__item {
  margin-bottom: 20rpx;
}

.blog-collect__loading {
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
