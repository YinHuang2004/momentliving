<template>
  <view class="my-blogs">
    <NavBar title="博客列表" class="page-nav" />
    <view class="my-blogs__head">
      <text class="my-blogs__title" v-if="headNick">{{ headNick }} 的博客</text>
      <text class="my-blogs__title" v-else>我的博客</text>
      <!-- 仅看自己的博客时显示管理入口 -->
      <text v-if="isOwner && blogs.length > 0" class="my-blogs__manage" @click="toggleManage">
        {{ managing ? '完成' : '管理' }}
      </text>
    </view>

    <view class="my-blogs__list">
      <view
        v-for="b in blogs"
        :key="b.id"
        class="my-blogs__item"
        @longpress="handleLongPress(b)"
      >
        <!-- 管理模式：遮罩拦截卡片点击，整卡变为选择 -->
        <view v-if="managing" class="my-blogs__mask" @click="toggleSelect(b.id)">
          <view class="my-blogs__checkbox" :class="{ 'is-checked': selectedIds.includes(b.id) }">
            <text v-if="selectedIds.includes(b.id)" class="my-blogs__checkmark">✓</text>
          </view>
        </view>
        <BlogCard :blog="b" @like="handleLike" @click="goDetail" @author="goAuthor" />
      </view>
    </view>

    <EmptyView v-if="!loading && blogs.length === 0" text="还没有发布过博客" />
    <view class="my-blogs__loading" v-if="loading"><text>加载中…</text></view>
    <view class="my-blogs__nomore" v-if="!loading && !hasMore && blogs.length > 0">
      <text>— 没有更多了 —</text>
    </view>

    <!-- 管理模式底部操作栏：全选 + 批量删除 -->
    <view class="my-blogs__toolbar" v-if="managing">
      <view class="my-blogs__select-all" @click="toggleSelectAll">
        <view class="my-blogs__checkbox" :class="{ 'is-checked': isAllSelected }">
          <text v-if="isAllSelected" class="my-blogs__checkmark">✓</text>
        </view>
        <text>全选</text>
      </view>
      <button
        class="my-blogs__delete-btn"
        :class="{ 'is-disabled': selectedIds.length === 0 }"
        :disabled="selectedIds.length === 0"
        @click="handleBatchDelete"
      >
        删除{{ selectedIds.length > 0 ? `(${selectedIds.length})` : '' }}
      </button>
    </view>
    <view style="height: 120rpx" v-if="managing"></view>
  </view>
</template>

<script>
import BlogCard from '@/components/BlogCard.vue'
import EmptyView from '@/components/EmptyView.vue'
import { myBlogs, likeBlog, deleteBlogs } from '@/api/blog.js'

/**
 * 用户博客列表：GET /blog/of/user/{userId}（不传 userId 默认看自己的）
 * 入口：我的页宫格、关注/粉丝列表页
 * 自己的博客支持删除：点"管理"进入勾选模式（单个=勾一条，批量=勾多条），长按卡片也可单删
 */
import NavBar from '@/components/NavBar.vue'
export default {
  components: { NavBar, BlogCard, EmptyView },
  data() {
    return {
      userId: null,
      headNick: '',
      blogs: [],
      current: 1,
      hasMore: true,
      loading: false,
      isOwner: false,
      managing: false,
      selectedIds: []
    }
  },
  computed: {
    isAllSelected() {
      return this.blogs.length > 0 && this.selectedIds.length === this.blogs.length
    }
  },
  onLoad(options) {
    this.userId = options.userId || uni.getStorageSync('userInfo').id
    this.isOwner = String(this.userId) === String(uni.getStorageSync('userInfo').id)
    if (options.nickName) {
      this.headNick = decodeURIComponent(options.nickName)
    }
  },
  onShow() {
    if (!this.userId) {
      uni.showToast({ title: '缺少用户参数', icon: 'none' })
      return
    }
    this.resetAndLoad()
  },
  onReachBottom() {
    this.loadBlogs()
  },
  methods: {
    resetAndLoad() {
      this.current = 1
      this.blogs = []
      this.hasMore = true
      this.loadBlogs()
    },
    async loadBlogs() {
      if (this.loading || !this.hasMore) return
      this.loading = true
      try {
        const list = await myBlogs(this.userId, this.current)
        this.blogs = this.current === 1 ? list || [] : this.blogs.concat(list || [])
        this.hasMore = (list || []).length >= 10
        this.current++
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
    },
    /** 进入/退出管理模式，退出时清空勾选 */
    toggleManage() {
      this.managing = !this.managing
      if (!this.managing) {
        this.selectedIds = []
      }
    },
    toggleSelect(id) {
      const idx = this.selectedIds.indexOf(id)
      if (idx >= 0) {
        this.selectedIds.splice(idx, 1)
      } else {
        this.selectedIds.push(id)
      }
    },
    toggleSelectAll() {
      this.selectedIds = this.isAllSelected ? [] : this.blogs.map((b) => b.id)
    },
    /** 长按卡片：单个删除（非管理模式） */
    handleLongPress(blog) {
      if (this.managing || !this.isOwner) return
      this.confirmDelete([blog.id])
    },
    handleBatchDelete() {
      if (this.selectedIds.length === 0) return
      this.confirmDelete([...this.selectedIds])
    },
    /** 统一删除入口：单个与批量共用同一个后端接口 */
    confirmDelete(ids) {
      uni.showModal({
        title: '删除博客',
        content: ids.length > 1 ? `确定删除选中的 ${ids.length} 篇博客吗？` : '确定删除这篇博客吗？',
        confirmColor: '#fa5151',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await deleteBlogs(ids)
            this.blogs = this.blogs.filter((b) => !ids.includes(b.id))
            this.selectedIds = this.selectedIds.filter((id) => !ids.includes(id))
            uni.showToast({ title: '删除成功', icon: 'success' })
            // 全部删完时退出管理模式
            if (this.blogs.length === 0) {
              this.managing = false
            }
          } catch (e) {
            // toast 已统一处理
          }
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.my-blogs {
  min-height: 100vh;
  padding: 24rpx;
}

.my-blogs__head {
  margin-bottom: 20rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.my-blogs__title {
  font-size: 18px;
  font-weight: 700;
}

.my-blogs__manage {
  font-size: 14px;
  color: #007aff;
}

.my-blogs__item {
  position: relative;
  margin-bottom: 20rpx;
}

.my-blogs__mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2;
  border-radius: 16rpx;
}

.my-blogs__checkbox {
  position: absolute;
  top: 20rpx;
  right: 20rpx;
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  border: 2rpx solid #c8c9cc;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;

  &.is-checked {
    border-color: #fa5151;
    background: #fa5151;
  }
}

.my-blogs__checkmark {
  color: #fff;
  font-size: 26rpx;
  line-height: 1;
}

.my-blogs__toolbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -4rpx 12rpx rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.my-blogs__select-all {
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 14px;
}

.my-blogs__select-all .my-blogs__checkbox {
  position: static;
}

.my-blogs__delete-btn {
  min-width: 200rpx;
  margin: 0;
  font-size: 14px;
  color: #fff;
  background: #fa5151;
  border-radius: 999rpx;

  &.is-disabled {
    opacity: 0.4;
  }
}

.my-blogs__loading,
.my-blogs__nomore {
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
