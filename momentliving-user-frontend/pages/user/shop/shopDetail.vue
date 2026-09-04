<template>
  <view class="shop-detail" v-if="shop">
    <!-- 自定义导航（透明渐变） -->
    <NavBar title="店铺详情" back :bgColor="navBg" />

    <!-- 图片轮播 -->
    <swiper class="shop-detail__swiper" indicator-dots circular autoplay>
      <swiper-item v-for="(img, i) in images" :key="i">
        <image class="shop-detail__swiper-img" :src="img" mode="aspectFill" />
      </swiper-item>
    </swiper>

    <!-- 店铺基础信息 -->
    <view class="shop-detail__head brand-card">
      <view class="shop-detail__name-row">
        <text class="shop-detail__name">{{ shop.name }}</text>
        <text class="shop-detail__score" v-if="scoreText">★{{ scoreText }}</text>
        <view class="shop-detail__fav" @click="handleFavorite">
          <text class="shop-detail__fav-icon" :class="{ 'is-fav': isFav }">{{ isFav ? '★' : '☆' }}</text>
          <text class="shop-detail__fav-text">{{ isFav ? '已收藏' : '收藏' }}</text>
        </view>
      </view>
      <view class="shop-detail__meta">
        <text class="shop-detail__price" v-if="avgPriceText">人均 ¥{{ avgPriceText }}</text>
        <text class="shop-detail__sub" v-if="shop.sold != null">已售 {{ shop.sold }}</text>
        <text class="shop-detail__sub" v-if="shop.openHours">营业 {{ shop.openHours }}</text>
      </view>
      <text class="shop-detail__addr" v-if="shop.address">{{ shop.area }} · {{ shop.address }}</text>
    </view>

    <!-- 优惠券横向滑动 -->
    <view class="shop-detail__section" v-if="vouchers.length > 0">
      <view class="shop-detail__section-head">
        <text class="shop-detail__section-title">优惠券</text>
      </view>
      <scroll-view scroll-x class="shop-detail__voucher-scroll">
        <view class="shop-detail__voucher-list">
          <view class="shop-detail__voucher-item" v-for="v in vouchers" :key="v.id">
            <VoucherCard :voucher="v" :mode="v.type === 1 ? 'seckill' : 'normal'">
              <template #action>
                <button class="shop-detail__get-btn" @click.stop="handleBuy(v)">领取</button>
              </template>
            </VoucherCard>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 分段 tabs：评价 | 详情 -->
    <view class="shop-detail__tabs">
      <view
        class="shop-detail__tab"
        :class="{ 'is-active': activeTab === 'review' }"
        @click="activeTab = 'review'"
      >
        <text>评价</text>
      </view>
      <view
        class="shop-detail__tab"
        :class="{ 'is-active': activeTab === 'info' }"
        @click="activeTab = 'info'"
      >
        <text>详情</text>
      </view>
    </view>

    <!-- 评价列表 -->
    <view v-if="activeTab === 'review'" class="shop-detail__reviews">
      <view class="shop-detail__review-summary brand-card" v-if="scoreSummary">
        <text class="shop-detail__review-avg">{{ scoreSummary.avg }}</text>
        <text class="shop-detail__review-cnt">{{ scoreSummary.cnt }} 条评价</text>
        <button class="shop-detail__review-add" @click="showReview = true">写评价</button>
      </view>
      <view class="shop-detail__review brand-card" v-for="r in reviews" :key="r.id">
        <view class="shop-detail__review-head">
          <image class="shop-detail__review-avatar" :src="r.avatar || '/static/logo.png'" mode="aspectFill" />
          <view class="shop-detail__review-user">
            <text class="shop-detail__review-nick">{{ r.nickName || '匿名用户' }}</text>
            <text class="shop-detail__review-stars">{{ '★'.repeat(r.rating || 5) }}</text>
          </view>
        </view>
        <text class="shop-detail__review-content">{{ r.content }}</text>
        <view class="shop-detail__review-imgs" v-if="reviewImages(r).length">
          <image
            class="shop-detail__review-img"
            v-for="(img, i) in reviewImages(r)"
            :key="i"
            :src="img"
            mode="aspectFill"
            @click="previewReviewImages(r)"
          />
        </view>
        <text class="shop-detail__review-time">{{ formatTime(r.createTime) }}</text>
      </view>
      <EmptyView v-if="!loading && reviews.length === 0" text="还没有评价" />
    </view>

    <!-- 店铺详情 -->
    <view v-else class="shop-detail__info brand-card">
      <view class="shop-detail__info-row">
        <text class="shop-detail__info-label">店铺名称</text>
        <text>{{ shop.name }}</text>
      </view>
      <view class="shop-detail__info-row">
        <text class="shop-detail__info-label">营业时间</text>
        <text>{{ shop.openHours || '暂无' }}</text>
      </view>
      <view class="shop-detail__info-row">
        <text class="shop-detail__info-label">店铺地址</text>
        <text>{{ shop.address || '暂无' }}</text>
      </view>
      <view class="shop-detail__info-row">
        <text class="shop-detail__info-label">商户电话</text>
        <text>暂无</text>
      </view>
    </view>

    <!-- 写评价弹层：星级 + 文字 + 图片（≤4 张，走 file-service dir=reviews） -->
    <view class="review-mask" v-if="showReview" @click="showReview = false">
      <view class="review-panel brand-card" @click.stop>
        <text class="review-panel__title">写评价</text>
        <view class="review-panel__stars">
          <text
            v-for="n in 5"
            :key="n"
            class="review-panel__star"
            :class="{ 'is-active': n <= form.rating }"
            @click="form.rating = n"
          >★</text>
        </view>
        <textarea
          class="review-panel__textarea"
          v-model="form.content"
          maxlength="500"
          placeholder="这家店怎么样？说说你的体验…"
          placeholder-class="review-panel__placeholder"
        />
        <view class="review-panel__imgs">
          <view class="review-panel__img-item" v-for="(img, i) in form.images" :key="i">
            <image class="review-panel__img" :src="img" mode="aspectFill" />
            <text class="review-panel__img-del" @click="removeReviewImage(i)">×</text>
          </view>
          <view class="review-panel__img-add" v-if="form.images.length < 4" @click="chooseReviewImage">
            <text>＋</text>
          </view>
        </view>
        <button class="review-panel__submit" :disabled="submitting" @click="submitReview">发布评价</button>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/NavBar.vue'
import VoucherCard from '@/components/VoucherCard.vue'
import EmptyView from '@/components/EmptyView.vue'
import { getShop, getShopScore, listReview, addReview, favoriteShop, isShopFavorite } from '@/api/shop.js'
import { listShopVoucher, createSeckillOrder, buyVoucher } from '@/api/voucher.js'
import { uploadImage } from '@/api/file.js'

/**
 * 店铺详情：轮播 + 店铺信息 + 优惠券（横滑）+ 评价/详情 tabs
 * 领券走下单接口（POST /voucher-order/seckill/{id}，一人限购 3 单），成功后跳支付确认页
 */
export default {
  components: { NavBar, VoucherCard, EmptyView },
  data() {
    return {
      shop: null,
      vouchers: [],
      reviews: [],
      scoreSummary: null,
      activeTab: 'review',
      loading: false,
      navBg: 'transparent',
      isFav: false,
      showReview: false,
      submitting: false,
      form: { rating: 0, content: '', images: [] }
    }
  },
  computed: {
    images() {
      return (this.shop && this.shop.images ? this.shop.images : '').split(',').filter(Boolean)
    },
    scoreText() {
      if (!this.scoreSummary) {
        const s = Number(this.shop && this.shop.score)
        if (!s) return ''
        return s > 5 ? (s / 10).toFixed(1) : String(s)
      }
      return this.scoreSummary.avg
    },
    avgPriceText() {
      const p = Number(this.shop && this.shop.avgPrice)
      return p ? (p / 100).toFixed(0) : ''
    }
  },
  async onLoad(options) {
    const id = options.id
    // "undefined"/"null" 字符串也是无效参数（上游把 undefined 拼进了 URL）
    if (!id || id === 'undefined' || id === 'null') {
      uni.showToast({ title: '缺少店铺参数', icon: 'none' })
      return
    }
    this.shopId = id
    await this.loadAll()
  },
  onPageScroll(e) {
    // 滚动后给导航加米白底
    this.navBg = e.scrollTop > 60 ? '#FAF7F0' : 'transparent'
  },
  onReachBottom() {
    this.loadReviews()
  },
  methods: {
    async loadAll() {
      this.loading = true
      try {
        // 店铺详情 + 券列表并行；评分与评价单独容错
        const [shop, vouchers] = await Promise.all([getShop(this.shopId), listShopVoucher(this.shopId)])
        this.shop = shop
        this.vouchers = vouchers || []
        this.loadScore()
        this.loadReviews(true)
        // 收藏状态单独容错（失败只影响按钮初始态）
        isShopFavorite(this.shopId)
          .then((fav) => { this.isFav = !!fav })
          .catch(() => {})
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    /** 收藏/取消收藏店铺（PUT /shop/favorite/{id} toggle，返回操作后的收藏状态） */
    async handleFavorite() {
      try {
        const fav = await favoriteShop(this.shopId)
        this.isFav = !!fav
        uni.showToast({ title: fav ? '已收藏' : '已取消收藏', icon: 'none' })
      } catch (e) {
        // toast 已统一处理
      }
    },
    async loadScore() {
      try {
        this.scoreSummary = await getShopScore(this.shopId)
      } catch (e) {
        this.scoreSummary = null
      }
    },
    async loadReviews(reset = false) {
      if (this.loading) return
      this.loading = true
      try {
        if (reset) this.reviewCurrent = 1
        const list = await listReview({ shopId: this.shopId, current: this.reviewCurrent || 1 })
        this.reviews = reset ? list || [] : this.reviews.concat(list || [])
        this.reviewCurrent = (this.reviewCurrent || 1) + 1
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.loading = false
      }
    },
    async handleBuy(voucher) {
      try {
        // 秒杀券(type=1)走抢购（MQ 异步落库），普通券直接下单（同步落库）
        const orderId = voucher.type === 1
          ? await createSeckillOrder(voucher.id)
          : await buyVoucher(voucher.id)
        uni.navigateTo({ url: `/pages/user/pay/payConfirm?orderId=${orderId}` })
      } catch (e) {
        // toast 已统一处理（库存不足/限购等）
      }
    },
    formatTime(t) {
      return (t || '').replace('T', ' ').slice(0, 16)
    },

    // ==================== 写评价 ====================

    reviewImages(r) {
      return (r.images || '').split(',').filter(Boolean)
    },
    previewReviewImages(r) {
      const urls = this.reviewImages(r)
      if (urls.length) uni.previewImage({ urls })
    },
    chooseReviewImage() {
      uni.chooseImage({
        count: 4 - this.form.images.length,
        sizeType: ['compressed'],
        success: async (res) => {
          const paths = res.tempFilePaths || []
          uni.showLoading({ title: '上传中…' })
          try {
            for (const path of paths) {
              const url = await uploadImage(path, 'reviews')
              this.form.images.push(url)
            }
          } catch (e) {
            // toast 已统一处理
          } finally {
            uni.hideLoading()
          }
        }
      })
    },
    removeReviewImage(i) {
      this.form.images.splice(i, 1)
    },
    async submitReview() {
      if (!this.form.rating) {
        uni.showToast({ title: '请先选择星级', icon: 'none' })
        return
      }
      const content = (this.form.content || '').trim()
      if (!content) {
        uni.showToast({ title: '写点什么吧', icon: 'none' })
        return
      }
      this.submitting = true
      try {
        await addReview({
          shopId: this.shopId,
          rating: this.form.rating,
          content,
          images: this.form.images
        })
        uni.showToast({ title: '评价成功', icon: 'success' })
        this.showReview = false
        this.form = { rating: 0, content: '', images: [] }
        // 评价列表回到第一页 + 评分聚合刷新
        this.reviewCurrent = 1
        await this.loadReviews(true)
        this.loadScore()
      } catch (e) {
        // toast 已统一处理
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.shop-detail {
  min-height: 100vh;
  padding-bottom: 60rpx;
}

.shop-detail__swiper {
  height: 420rpx;
  margin: 0 24rpx;
  border-radius: $radius-card;
  overflow: hidden;

  &-img {
    width: 100%;
    height: 100%;
  }
}

.shop-detail__head {
  margin: 24rpx;
  padding: 28rpx;

  &__name-row {
    display: flex;
    align-items: baseline;
  }
}

.shop-detail__name {
  font-size: 20px;
  font-weight: 700;
  color: $text-main;
}

.shop-detail__fav {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4rpx;
  padding: 6rpx 20rpx;
  border-radius: 999rpx;
  background: $brand-bg-2;
}

.shop-detail__fav-icon {
  font-size: 14px;
  color: $text-sub;

  &.is-fav {
    color: $brand-accent;
  }
}

.shop-detail__fav-text {
  font-size: 12px;
  color: $text-main;
}

.shop-detail__score {
  margin-left: 16rpx;
  color: $brand-accent;
  font-weight: 700;
}

.shop-detail__meta {
  display: flex;
  gap: 24rpx;
  margin-top: 12rpx;
  align-items: baseline;
}

.shop-detail__price {
  color: $brand-accent;
  font-size: 17px;
  font-weight: 700;
}

.shop-detail__sub {
  color: $text-sub;
  font-size: 13px;
}

.shop-detail__addr {
  display: block;
  margin-top: 12rpx;
  color: $text-sub;
  font-size: 13px;
}

.shop-detail__section {
  margin: 0 24rpx 24rpx;

  &-head {
    margin-bottom: 16rpx;
  }

  &-title {
    font-size: 18px;
    font-weight: 700;
  }
}

.shop-detail__voucher-scroll {
  white-space: nowrap;
}

.shop-detail__voucher-list {
  display: flex;
  gap: 20rpx;
  padding-bottom: 8rpx;
}

.shop-detail__voucher-item {
  width: 560rpx;
  flex-shrink: 0;
}

.shop-detail__get-btn {
  height: 56rpx;
  line-height: 52rpx;
  padding: 0 28rpx;
  border-radius: $radius-btn;
  background: transparent;
  border: 1px solid $brand-primary;
  color: $brand-primary;
  font-size: 13px;

  &::after {
    border: none;
  }
}

.shop-detail__tabs {
  display: flex;
  margin: 0 24rpx 20rpx;
  border-bottom: 1px solid $brand-line;

  &__tab.is-active {
    color: $brand-primary;
  }
}

.shop-detail__tab {
  padding: 20rpx 32rpx;
  font-size: 16px;
  color: $text-sub;
  position: relative;

  &.is-active {
    color: $brand-primary;
    font-weight: 700;

    &::after {
      content: '';
      position: absolute;
      left: 50%;
      transform: translateX(-50%);
      bottom: -1px;
      width: 48rpx;
      height: 4rpx;
      border-radius: 4rpx;
      background: $brand-primary;
    }
  }
}

.shop-detail__review-summary {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  padding: 24rpx 28rpx;
  margin: 0 24rpx 20rpx;
}

.shop-detail__review-avg {
  font-size: 32px;
  font-weight: 800;
  color: $brand-accent;
}

.shop-detail__review-cnt {
  color: $text-sub;
  font-size: 13px;
}

.shop-detail__review {
  margin: 0 24rpx 20rpx;
  padding: 24rpx 28rpx;
}

.shop-detail__review-head {
  display: flex;
  align-items: center;
}

.shop-detail__review-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: $brand-bg-2;
  margin-right: 16rpx;
}

.shop-detail__review-user {
  display: flex;
  flex-direction: column;
}

.shop-detail__review-nick {
  font-size: 14px;
  font-weight: 600;
}

.shop-detail__review-stars {
  color: $brand-accent;
  font-size: 12px;
}

.shop-detail__review-content {
  display: block;
  margin-top: 16rpx;
  font-size: 14px;
  color: $text-main;
  line-height: 1.6;
}

.shop-detail__review-time {
  display: block;
  margin-top: 12rpx;
  color: $text-sub;
  font-size: 12px;
}

.shop-detail__info {
  margin: 0 24rpx;
  padding: 12rpx 28rpx;
}

.shop-detail__info-row {
  display: flex;
  padding: 24rpx 0;
  border-bottom: 1px solid $brand-line;
  font-size: 14px;

  &:last-child {
    border-bottom: none;
  }
}

.shop-detail__info-label {
  width: 160rpx;
  color: $text-sub;
}

/* ========== 评价提交弹层 ========== */

.shop-detail__review-add {
  margin-left: auto;
  height: 56rpx;
  line-height: 52rpx;
  padding: 0 28rpx;
  border-radius: $radius-btn;
  background: $brand-primary;
  color: #ffffff;
  font-size: 13px;

  &::after {
    border: none;
  }
}

.shop-detail__review-imgs {
  display: flex;
  gap: 12rpx;
  margin-top: 16rpx;
  flex-wrap: wrap;
}

.shop-detail__review-img {
  width: 150rpx;
  height: 150rpx;
  border-radius: 12rpx;
  background: $brand-bg-2;
}

.review-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}

.review-panel {
  width: 100%;
  border-radius: $radius-card $radius-card 0 0;
  padding: 32rpx 28rpx calc(32rpx + env(safe-area-inset-bottom));
}

.review-panel__title {
  display: block;
  text-align: center;
  font-size: 17px;
  font-weight: 700;
  margin-bottom: 20rpx;
}

.review-panel__stars {
  display: flex;
  justify-content: center;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.review-panel__star {
  font-size: 32px;
  color: $brand-line;

  &.is-active {
    color: $brand-accent;
  }
}

.review-panel__textarea {
  width: 100%;
  box-sizing: border-box;
  min-height: 160rpx;
  padding: 20rpx;
  border-radius: 12rpx;
  background: $brand-bg-2;
  font-size: 14px;
  line-height: 1.6;
}

.review-panel__placeholder {
  color: $text-sub;
}

.review-panel__imgs {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
  flex-wrap: wrap;
}

.review-panel__img-item {
  position: relative;
}

.review-panel__img {
  width: 140rpx;
  height: 140rpx;
  border-radius: 12rpx;
  background: $brand-bg-2;
}

.review-panel__img-del {
  position: absolute;
  right: -10rpx;
  top: -10rpx;
  width: 40rpx;
  height: 40rpx;
  line-height: 36rpx;
  text-align: center;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #ffffff;
  font-size: 16px;
}

.review-panel__img-add {
  width: 140rpx;
  height: 140rpx;
  border-radius: 12rpx;
  border: 1px dashed $brand-line;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $text-sub;
  font-size: 28px;
}

.review-panel__submit {
  margin-top: 28rpx;
  height: 76rpx;
  line-height: 72rpx;
  border-radius: $radius-btn;
  background: $brand-primary;
  color: #ffffff;
  font-size: 15px;

  &::after {
    border: none;
  }
}
</style>
