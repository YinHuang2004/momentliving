// uni-app 官方 Promise 化适配器（照官方模板保留）
// 说明：本工程内 uni API 均按回调式调用（request.js 的 success/fail），此适配器仅保证
// 部分平台下 api 返回值（task/uniIdWrap）形态一致，不影响同步 Storage 等 api 的行为。
uni.addInterceptor('returnValue', {
  success(res) {
    if (!!(res && res.task && res.task.uniIdWrap)) {
      return res.task.uniIdWrap
    }
    return res
  }
})
