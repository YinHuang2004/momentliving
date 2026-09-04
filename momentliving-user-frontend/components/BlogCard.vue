<template>
  <view class="blog-card brand-card" @click="$emit('click', blog)">
    <view class="blog-card__author" @click.stop="$emit('author', blog)">
      <image class="blog-card__avatar" :src="blog.authorImages || '/static/logo.png'" mode="aspectFill" />
      <text class="blog-card__nick">{{ blog.name || '匿名用户' }}</text>
    </view>
    <image
      v-if="cover"
      class="blog-card__img"
      :src="cover"
      mode="aspectFill"
      :lazy-load="true"
    />
    <view class="blog-card__body">
      <text class="blog-card__title ellipsis-1" v-if="blog.title">{{ blog.title }}</text>
      <text class="blog-card__content ellipsis-2">{{ blog.content }}</text>
    </view>
    <view class="blog-card__footer">
      <view class="blog-card__actions" @click.stop="$emit('like', blog)">
        <text class="blog-card__like" :class="{ 'is-liked': blog.isLike }">{{ blog.isLike ? '♥' : '♡' }}</text>
        <text class="blog-card__count">{{ likeCountText }}</text>
      </view>
      <view class="blog-card__actions">
        <text class="blog-card__comment">💬</text>
        <text class="blog-card__count">{{ blog.comments || 0 }}</text>
      </view>
    </view>
  </view>
</template>

<script>
/**
 * 博客卡片（热门博客流 / 关注动态流通用）
 * blog 字段来自后端 BlogVO：title/images/content/name(作者昵称)/liked(点赞数)/comments/isLike(当前用户是否点赞)
 */
export default {
  name: 'BlogCard',
  props: {
    blog: { type: Object, required: true }
  },
  // 必须声明自定义事件：click 是原生事件名，不声明的话父组件的 @click
  // 会穿透成根元素的原生监听器，goDetail 将收到 MouseEvent 而不是 blog
  emits: ['click', 'like', 'author'],
  computed: {
    // images 为逗号分隔字符串，取第一张做封面
    cover() {
      const imgs = (this.blog.images || '').split(',').filter(Boolean)
      return imgs[0] || ''
    },
    likeCountText() {
      const n = Number(this.blog.liked) || 0
      return n >= 1000 ? `${(n / 1000).toFixed(1)}k` : String(n)
    }
  }
}
</script>

<style lang="scss" scoped>
.blog-card {
  padding: 24rpx;

  &__author {
    display: flex;
    align-items: center;
    margin-bottom: 16rpx;
  }

  &__avatar {
    width: 64rpx;
    height: 64rpx;
    border-radius: 50%;
    background: $brand-bg-2;
    margin-right: 16rpx;
  }

  &__nick {
    font-size: 14px;
    font-weight: 600;
    color: $text-main;
  }

  &__img {
    width: 100%;
    height: 360rpx;
    border-radius: 16rpx;
    background: $brand-bg-2;
  }

  &__body {
    margin-top: 16rpx;
  }

  &__title {
    font-size: 16px;
    font-weight: 600;
    color: $text-main;
  }

  &__content {
    color: $text-sub;
    font-size: 14px;
    line-height: 1.6;
  }

  &__footer {
    display: flex;
    align-items: center;
    gap: 48rpx;
    margin-top: 16rpx;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 8rpx;
  }

  &__like {
    color: $text-sub;
    font-size: 18px;

    &.is-liked {
      color: $brand-primary;
    }
  }

  &__comment {
    color: $text-sub;
    font-size: 15px;
  }

  &__count {
    color: $text-sub;
    font-size: 13px;
  }
}
</style>
