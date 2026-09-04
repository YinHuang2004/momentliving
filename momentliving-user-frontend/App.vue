<script>
export default {
  onLaunch() {
    // 全局启动逻辑：登录态校验在页面 onShow 内按需做（见各页面）
    // 登录态存储已按角色隔离（userToken / merchantToken），旧版共用 key 'token' 一次作废，
    // 保留它可能导致角色错乱的 401，这里统一清除，用户重新登录一次即可
    if (uni.getStorageSync('token')) {
      uni.removeStorageSync('token')
    }
  },
  onShow() {},
  onHide() {}
}
</script>

<style lang="scss">
@import 'uview-plus/index.scss';

/* ========== 全局基础样式（配色见 uni.scss 设计 token） ========== */
page {
  background: $brand-bg;
  color: $text-main;
  font-size: $font-main;
  /* 苹方 / 思源黑体风格无衬线 */
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue',
    'Source Han Sans SC', 'Noto Sans SC', 'Microsoft YaHei', sans-serif;
}

/* 统一卡片：圆角 20px + 柔和阴影 */
.brand-card {
  background: $brand-bg;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
}

/* 统一主按钮：胶囊、抹茶绿底白字、高度 ≥ 48px */
.brand-btn {
  height: 48px;
  line-height: 48px;
  border-radius: $radius-btn;
  background: $brand-primary;
  color: #fff;
  font-size: $font-btn;
  text-align: center;
  border: none;

  &::after {
    border: none;
  }

  &.is-disabled {
    opacity: 0.5;
  }
}

/* 描边按钮 */
.brand-btn-plain {
  height: 48px;
  line-height: 46px;
  border-radius: $radius-btn;
  background: transparent;
  color: $brand-primary;
  font-size: $font-btn;
  text-align: center;
  border: 1px solid $brand-primary;
}

/* 一行省略 */
.ellipsis-1 {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ellipsis-2 {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
</style>
