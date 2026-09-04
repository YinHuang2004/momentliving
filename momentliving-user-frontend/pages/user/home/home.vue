<template>
  <view class="home-page">
    <!-- 自定义导航：搜索框 -->
    <view class="home-nav">
      <view class="home-nav__status"></view>
      <view class="home-nav__search-row">
        <view class="home-nav__search">
          <text class="home-nav__search-icon">⌕</text>
          <input
            class="home-nav__search-input"
            v-model="searchKey"
            confirm-type="search"
            placeholder="搜索店铺、美食、咖啡…"
            placeholder-class="home-nav__placeholder"
            @confirm="handleSearch"
          />
          <text class="home-nav__search-clear" v-if="searchKey" @click="clearSearchKey">×</text>
        </view>
        <text class="home-nav__cancel" v-if="isSearchMode" @click="exitSearch">取消</text>
      </view>
    </view>

    <!-- 分类宫格（shop-type 接口） -->
    <view class="home-grid brand-card">
      <view
        v-for="item in shopTypes"
        :key="item.id"
        class="home-grid__item"
        :class="{ 'is-active': currentTypeId === item.id }"
        @click="handleSelectType(item)"
      >
        <view class="home-grid__icon">
          <text class="home-grid__icon-text">{{ item.name.charAt(0) }}</text>
        </view>
        <text class="home-grid__label">{{ item.name }}</text>
      </view>
    </view>

    <!-- 附近店铺 / 搜索结果列表 -->
    <view class="home-section-title" v-if="!isSearchMode">
      <text class="home-section-title__text">{{ currentTypeId ? '分类店铺' : '附近店铺' }}</text>
    </view>
    <view class="home-section-title" v-else>
      <text class="home-section-title__text">“{{ searchedKey }}” 的搜索结果</text>
    </view>
    <ShopCard v-for="shop in shops" :key="shop.id" :shop="shop" @click="goDetail" />
    <EmptyView v-if="!loading && shops.length === 0" text="附近暂无店铺" />

    <view class="home-loading" v-if="loading">
      <text class="home-loading__text">加载中…</text>
    </view>
    <view class="home-nomore" v-if="!loading && !hasMore && shops.length > 0">
      <text class="home-nomore__text">— 没有更多了 —</text>
    </view>
  </view>
</template>

<script>
import ShopCard from '@/components/ShopCard.vue'
import EmptyView from '@/components/EmptyView.vue'
import { listShopType, listShopByType, searchShopByName } from '@/api/shop.js'
import { getToken } from '@/utils/request.js'

/**
 * Tab1 首页：搜索框 + 分类宫格 + 附近店铺列表（上拉分页）
 * 搜索走 GET /shop/of/name（名称模糊匹配），搜索模式下隐藏分类宫格入口逻辑
 */
export default {
  components: { ShopCard, EmptyView },
  data() {
    return {
      shopTypes: [],
      currentTypeId: null,
      shops: [],
      current: 1,
      hasMore: true,
      loading: false,
      searchKey: '',
      searchedKey: '',
      isSearchMode: false
    }
  },
  onShow() {
    if (!getToken()) {
      uni.reLaunch({ url: '/pages/user/login/login' })
      return
    }
    if (this.shopTypes.length === 0) {
      this.loadShopTypes()
    }
  },
  onReachBottom() {
    this.loadShops()
  },
  onPullDownRefresh() {
    this.current = 1
    this.shops = []
    this.hasMore = true
    this.loadShops().finally(() => uni.stopPullDownRefresh())
  },
  methods: {
    async loadShopTypes() {
      try {
        const list = await listShopType()
        this.shopTypes = (list || []).slice(0, 8)
      } catch (e) {
        // toast 已统一处理
      }
      this.loadShops()
    },
    async loadShops() {
      if (this.loading || !this.hasMore) return
      this.loading = true
      try {
        const list = this.isSearchMode
          ? await searchShopByName(this.searchedKey, this.current)
          : await listShopByType({ typeId: this.currentTypeId || undefined, current: this.current })
        this.shops = this.current === 1 ? list || [] : this.shops.concat(list || [])
        // 一页 10 条，不足一页视为到底
        this.hasMore = (list || []).length >= 10
        this.current++
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    handleSearch() {
      const key = (this.searchKey || '').trim()
      if (!key) {
        uni.showToast({ title: '请输入搜索关键词', icon: 'none' })
        return
      }
      this.isSearchMode = true
      this.searchedKey = key
      this.resetAndLoad()
    },
    exitSearch() {
      this.isSearchMode = false
      this.searchKey = ''
      this.searchedKey = ''
      this.resetAndLoad()
    },
    clearSearchKey() {
      this.searchKey = ''
    },
    resetAndLoad() {
      this.current = 1
      this.shops = []
      this.hasMore = true
      this.loadShops()
    },
    handleSelectType(item) {
      // 搜索模式下点分类：先退出搜索回到浏览模式
      if (this.isSearchMode) {
        this.isSearchMode = false
        this.searchKey = ''
        this.searchedKey = ''
      }
      // 再点一次取消分类，回到全部
      this.currentTypeId = this.currentTypeId === item.id ? null : item.id
      this.resetAndLoad()
    },
    goDetail(shop) {
      // 防御：事件参数异常（如误收到原生 MouseEvent）时不导航，避免拼出 ?id=undefined
      if (!shop || !shop.id) return
      uni.navigateTo({ url: `/pages/user/shop/shopDetail?id=${shop.id}` })
    }
  }
}
</script>

<style lang="scss" scoped>
.home-page {
  min-height: 100vh;
  padding-bottom: 40rpx;
}

.home-nav {
  background: $brand-bg;

  &__status {
    height: var(--status-bar-height);
  }

  &__search-row {
    display: flex;
    align-items: center;
    margin: 20rpx 24rpx;
    gap: 20rpx;
  }

  &__search {
    flex: 1;
    height: 72rpx;
    border-radius: $radius-btn;
    background: #ffffff;
    border: 1px solid $brand-line;
    display: flex;
    align-items: center;
    padding: 0 28rpx;
  }

  &__search-icon {
    font-size: 20px;
    color: $text-sub;
    margin-right: 12rpx;
  }

  &__search-input {
    flex: 1;
    font-size: 14px;
    color: $text-main;
  }

  &__search-clear {
    width: 40rpx;
    height: 40rpx;
    line-height: 36rpx;
    text-align: center;
    border-radius: 50%;
    background: $brand-bg-2;
    color: $text-sub;
    font-size: 14px;
  }

  &__cancel {
    color: $brand-primary;
    font-size: 14px;
    flex-shrink: 0;
  }
}

.home-nav__placeholder {
  color: $text-sub;
}

.home-grid {
  margin: 0 24rpx 24rpx;
  padding: 32rpx 12rpx 12rpx;
  display: flex;
  flex-wrap: wrap;

  &__item {
    width: 25%;
    display: flex;
    flex-direction: column;
    align-items: center;
    margin-bottom: 24rpx;
  }

  &__icon {
    width: 88rpx;
    height: 88rpx;
    border-radius: 50%;
    background: rgba(107, 142, 90, 0.12);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__icon-text {
    color: $brand-primary;
    font-size: 20px;
    font-weight: 700;
  }

  &__item.is-active .home-grid__icon {
    background: $brand-primary;
  }

  &__item.is-active .home-grid__icon-text {
    color: #ffffff;
  }

  &__label {
    margin-top: 10rpx;
    font-size: 13px;
    color: $text-main;
  }
}

.home-section-title {
  margin: 8rpx 32rpx 20rpx;

  &__text {
    font-size: 18px;
    font-weight: 700;
    color: $text-main;
  }
}

.home-loading,
.home-nomore {
  display: flex;
  justify-content: center;
  padding: 24rpx 0;

  &__text {
    color: $text-sub;
    font-size: 13px;
  }
}
</style>
