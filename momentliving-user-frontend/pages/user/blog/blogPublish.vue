<template>
  <view class="blog-publish">
    <NavBar title="发布博客" class="page-nav" />
    <!-- 无可发布店铺：引导先去购买券（编辑模式不需要选店铺） -->
    <view v-if="loaded && shops.length === 0 && !editId" class="blog-publish__guide brand-card">
      <text class="blog-publish__guide-title">还不能发布博客</text>
      <text class="blog-publish__guide-text">只有购买过优惠券（已支付/已核销）的店铺才能发布真实探店博客，先去逛逛吧～</text>
      <button class="brand-btn blog-publish__guide-btn" @click="goHome">去逛逛</button>
    </view>

    <template v-else>
      <!-- 关联店铺（发布必选，仅限购买过的；编辑模式店铺不可改，隐藏） -->
      <view class="blog-publish__shop brand-card" v-if="!editId">
        <text class="blog-publish__shop-label">关联店铺（仅限购买过的）</text>
        <picker mode="selector" :range="shopNames" @change="onShopChange">
          <view class="blog-publish__shop-picker" :class="{ 'is-placeholder': shopIndex < 0 }">
            <text>{{ shopIndex < 0 ? '请选择要分享的店铺' : shopNames[shopIndex] }}</text>
            <text class="blog-publish__shop-arrow">›</text>
          </view>
        </picker>
      </view>

      <!-- 选图 -->
      <view class="blog-publish__images">
        <view class="blog-publish__img-item" v-for="(img, i) in images" :key="i">
          <image class="blog-publish__img" :src="img" mode="aspectFill" />
          <view class="blog-publish__img-del" @click="removeImage(i)">×</view>
        </view>
        <view class="blog-publish__img-add" v-if="images.length < 9" @click="chooseImage">
          <text class="blog-publish__img-add-icon">+</text>
          <text class="blog-publish__img-add-text">{{ uploading ? '上传中…' : '添加图片' }}</text>
        </view>
      </view>

      <!-- 文字 -->
      <view class="blog-publish__form brand-card">
        <input
          class="blog-publish__title"
          v-model="title"
          maxlength="30"
          placeholder="填写标题（最多 30 字）"
          placeholder-class="blog-publish__placeholder"
        />
        <textarea
          class="blog-publish__content"
          v-model="content"
          maxlength="2000"
          placeholder="分享你的探店体验…"
          placeholder-class="blog-publish__placeholder"
        />
      </view>

      <button class="brand-btn blog-publish__submit" :class="{ 'is-disabled': !canSubmit }" @click="handlePublish">
        {{ editId ? '保存修改' : '发布博客' }}
      </button>
    </template>
  </view>
</template>

<script>
import { uploadImage, deleteImage } from '@/api/file.js'
import { publishBlog, updateBlog, purchasableShops, getBlog } from '@/api/blog.js'

/**
 * 发布/编辑博客：
 * - 发布：选择购买过的店铺（后端强校验）→ 选图上传 OSS → 标题/正文 → POST
 * - 编辑：?id=xxx 进入，预填标题/正文/图片，店铺不可改，PUT 只更新标题/内容/图片
 */
import NavBar from '@/components/NavBar.vue'
export default {
  components: { NavBar },
  data() {
    return {
      editId: null, // 有值 = 编辑模式
      shops: [], // 可发布店铺 [{id, name}]
      shopIndex: -1,
      loaded: false,
      images: [], // 已上传完成的 OSS URL
      title: '',
      content: '',
      uploading: false
    }
  },
  computed: {
    shopNames() {
      return this.shops.map((s) => s.name)
    },
    canSubmit() {
      if (!this.title || !this.content || this.uploading) return false
      // 编辑模式不需要选店铺，但至少要有一张图（与发布一致）
      return this.editId ? this.images.length > 0 : this.shopIndex >= 0 && this.images.length > 0
    }
  },
  onLoad(options) {
    if (options.id) {
      this.editId = options.id
      uni.setNavigationBarTitle({ title: '编辑博客' })
      this.loadBlogForEdit()
    } else {
      this.loadShops()
    }
  },
  methods: {
    async loadBlogForEdit() {
      try {
        const blog = await getBlog(this.editId)
        if (!blog) {
          uni.showToast({ title: '博客不存在', icon: 'none' })
          setTimeout(() => uni.navigateBack(), 800)
          return
        }
        this.title = blog.title || ''
        this.content = blog.content || ''
        this.images = (blog.images || '').split(',').filter(Boolean)
        this.loaded = true
      } catch (e) {
        // toast 已统一处理
        this.loaded = true
      }
    },
    async loadShops() {
      try {
        this.shops = (await purchasableShops()) || []
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loaded = true
      }
    },
    onShopChange(e) {
      this.shopIndex = Number(e.detail.value)
    },
    goHome() {
      uni.switchTab({ url: '/pages/user/home/home' })
    },
    chooseImage() {
      if (this.uploading) return
      uni.chooseImage({
        count: 9 - this.images.length,
        sizeType: ['compressed'],
        success: async (res) => {
          this.uploading = true
          try {
            for (const path of res.tempFilePaths) {
              // 逐张上传，失败的跳过并提示
              const url = await uploadImage(path, 'blogs')
              this.images.push(url)
            }
          } catch (e) {
            // toast 已统一处理
          } finally {
            this.uploading = false
          }
        }
      })
    },
    removeImage(index) {
      // 已上传的图同步清理 OSS，避免僵尸文件；本地未上传的忽略
      const url = this.images[index]
      if (url && /^https?:\/\//.test(url)) {
        deleteImage(url).catch(() => {})
      }
      this.images.splice(index, 1)
    },
    async handlePublish() {
      if (!this.editId && this.shopIndex < 0) {
        uni.showToast({ title: '请选择要分享的店铺', icon: 'none' })
        return
      }
      if (!this.canSubmit) {
        uni.showToast({ title: '请填写标题、正文并至少上传一张图片', icon: 'none' })
        return
      }
      try {
        if (this.editId) {
          // 编辑：后端只允许改标题/内容/图片
          await updateBlog(this.editId, {
            title: this.title,
            content: this.content,
            images: this.images.join(',')
          })
          // 通知详情页/列表页同步最新内容
          uni.$emit('blogEdited', {
            id: this.editId,
            title: this.title,
            content: this.content,
            images: this.images.join(',')
          })
          uni.showToast({ title: '保存成功', icon: 'none' })
        } else {
          await publishBlog({
            shopId: this.shops[this.shopIndex].id,
            title: this.title,
            content: this.content,
            images: this.images.join(',')
          })
          uni.showToast({ title: '发布成功', icon: 'none' })
        }
        setTimeout(() => uni.navigateBack(), 600)
      } catch (e) {
        // toast 已统一处理
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.blog-publish {
  min-height: 100vh;
  padding: 24rpx;
}

.blog-publish__guide {
  padding: 64rpx 48rpx;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.blog-publish__guide-title {
  font-size: 18px;
  font-weight: 700;
}

.blog-publish__guide-text {
  margin: 20rpx 0 32rpx;
  color: $text-sub;
  font-size: 14px;
  line-height: 1.7;
}

.blog-publish__guide-btn {
  width: 50%;
}

.blog-publish__shop {
  padding: 24rpx 28rpx;
  margin-bottom: 24rpx;
}

.blog-publish__shop-label {
  color: $text-sub;
  font-size: 13px;
}

.blog-publish__shop-picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
  height: 80rpx;
  background: #ffffff;
  border: 1px solid $brand-line;
  border-radius: 16rpx;
  padding: 0 24rpx;
  font-size: 15px;
  color: $text-main;

  &.is-placeholder {
    color: $text-sub;
  }
}

.blog-publish__shop-arrow {
  color: $text-sub;
  font-size: 18px;
}

.blog-publish__images {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.blog-publish__img-item {
  position: relative;
  width: 200rpx;
  height: 200rpx;
}

.blog-publish__img {
  width: 100%;
  height: 100%;
  border-radius: 16rpx;
  background: $brand-bg-2;
}

.blog-publish__img-del {
  position: absolute;
  top: -12rpx;
  right: -12rpx;
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: rgba(31, 36, 33, 0.7);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.blog-publish__img-add {
  width: 200rpx;
  height: 200rpx;
  border-radius: 16rpx;
  border: 2rpx dashed $brand-line;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.blog-publish__img-add-icon {
  font-size: 32px;
  color: $text-sub;
}

.blog-publish__img-add-text {
  color: $text-sub;
  font-size: 12px;
}

.blog-publish__form {
  padding: 28rpx;
}

.blog-publish__title {
  font-size: 17px;
  font-weight: 600;
  padding-bottom: 20rpx;
  border-bottom: 1px solid $brand-line;
}

.blog-publish__content {
  width: 100%;
  min-height: 300rpx;
  margin-top: 20rpx;
  font-size: 15px;
  line-height: 1.7;
}

.blog-publish__submit {
  margin-top: 32rpx;
}

.blog-publish__placeholder {
  color: $text-sub;
}

/* NavBar 通栏：抵消根容器 padding */
.page-nav {
  margin: -24rpx -24rpx 0;
}
</style>
