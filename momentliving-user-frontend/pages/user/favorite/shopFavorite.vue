<template>
  <view class="shop-favorite">
    <NavBar title="收藏店铺" class="page-nav" />

    <view class="shop-favorite__list">
      <view class="shop-favorite__item brand-card" v-for="s in shops" :key="s.id" @click="goDetail(s)">
        <image class="shop-favorite__cover" :src="cover(s)" mode="aspectFill" />
        <view class="shop-favorite__info">
          <text class="shop-favorite__name ellipsis-1">{{ s.name }}</text>
          <text class="shop-favorite__addr ellipsis-1" v-if="s.address">
            {{ s.area ? s.area + ' · ' : '' }}{{ s.address }}
          </text>
          <view class="shop-favorite__meta">
            <text class="shop-favorite__price" v-if="avgPrice(s)">人均 ¥{{ avgPrice(s) }}</text>
            <text class="shop-favorite__sold" v-if="s.sold != null">已售 {{ s.sold }}</text>
          </view>
        </view>
        <text class="shop-favorite__arrow">›</text>
      </view>
    </view>

    <EmptyView v-if="!loading && shops.length === 0" text="还没有收藏店铺，去店铺详情点⭐收藏吧" />
    <view class="shop-favorite__loading" v-if="loading"><text>加载中…</text></view>
  </view>
</template>

<script>
import NavBar from '@/components/NavBar.vue'
import EmptyView from '@/components/EmptyView.vue'
import { myFavoriteShops } from '@/api/shop.js'

/**
 * 收藏店铺列表（我的页宫格"收藏店铺"入口）
 * 数据：GET /shop/favorite/list（ShopVO[]，收藏时间倒序，后端一次全量返回）
 */
export default {
  components: { NavBar, EmptyView },
  data() {
    return {
      shops: [],
      loading: false
    }
  },
  onShow() {
    // 每次回到本页都重新拉取：详情页取消收藏后列表要同步
    this.loadShops()
  },
  methods: {
    async loadShops() {
      this.loading = true
      try {
        this.shops = (await myFavoriteShops()) || []
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    cover(shop) {
      return (shop.images || '').split(',').filter(Boolean)[0] || ''
    },
    avgPrice(shop) {
      const p = Number(shop.avgPrice)
      return p ? (p / 100).toFixed(0) : ''
    },
    goDetail(shop) {
      uni.navigateTo({ url: `/pages/user/shop/shopDetail?id=${shop.id}` })
    }
  }
}
</script>

<style lang="scss" scoped>
.shop-favorite {
  min-height: 100vh;
  padding: 24rpx;
}

.shop-favorite__item {
  display: flex;
  align-items: center;
  padding: 24rpx;
  margin-bottom: 20rpx;
}

.shop-favorite__cover {
  width: 140rpx;
  height: 140rpx;
  border-radius: 16rpx;
  background: $brand-bg-2;
  flex-shrink: 0;
  margin-right: 24rpx;
}

.shop-favorite__info {
  flex: 1;
  min-width: 0;
}

.shop-favorite__name {
  display: block;
  font-size: 16px;
  font-weight: 700;
  color: $text-main;
}

.shop-favorite__addr {
  display: block;
  margin-top: 8rpx;
  font-size: 13px;
  color: $text-sub;
}

.shop-favorite__meta {
  display: flex;
  gap: 24rpx;
  margin-top: 10rpx;
}

.shop-favorite__price {
  color: $brand-accent;
  font-size: 13px;
  font-weight: 700;
}

.shop-favorite__sold {
  color: $text-sub;
  font-size: 13px;
}

.shop-favorite__arrow {
  margin-left: 12rpx;
  font-size: 22px;
  color: $text-sub;
}

.shop-favorite__loading {
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
