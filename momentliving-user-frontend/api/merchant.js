import { get, post, put, setMerchantToken, clearMerchantToken } from '../utils/request.js'
import { getShop, updateShop } from './shop.js'

// 商家端复用店铺查询/编辑接口，重新导出保持 api 层统一入口
export { getShop as getMerchantShop, updateShop as updateMerchantShop }

/**
 * 商家登录：POST /api/merchant/login {username, password}
 * 返回 MerchantVO { id, username, name, phone, shopId, avatar, token }；
 * 商家 token 存独立命名空间 merchantToken，与用户端 userToken 互不顶号；
 * 账号体系已与平台管理员拆分（/admin/login 仅限平台管理员）
 */
export async function merchantLogin({ username, password }) {
  const res = await post('/api/merchant/login', { username, password })
  if (res && res.token) {
    setMerchantToken(res.token)
    uni.setStorageSync('merchantInfo', res)
  }
  return res
}

/**
 * 商家入驻申请（公开接口，无需登录）：POST /api/merchant/apply
 * 提交后等待平台管理员审核，审核通过即可用该账号登录商家端
 */
export const applyMerchant = (data) => post('/api/merchant/apply', data)

/** 退出登录：失效服务端登录态并清商家端 token（不动用户端登录态） */
export const merchantLogout = async () => {
  try {
    await post('/api/merchant/logout')
  } finally {
    clearMerchantToken()
  }
}

/** 当前商家信息（MerchantVO，含 shopId） */
export const getMerchantMe = () => get('/api/merchant/me')

/**
 * 修改商家个人信息：PUT /api/merchant/me
 * {name, phone, avatar} 提供才更新；改密需同时传 {oldPassword, newPassword}
 * 返回更新后的 MerchantVO（不含 token），调用方负责同步本地 merchantInfo 缓存
 */
export const updateMerchantMe = (data) => put('/api/merchant/me', data)

/** 核销：输入 16 位核销码（后端 @RequestParam code，需拼 query） */
export const verifyByCode = (code) => post(`/api/merchant/verify?code=${encodeURIComponent(code)}`)

/** 核销前核对订单（按订单 ID） */
export const getVerifyOrder = (orderId) => get(`/api/merchant/verify/order/${orderId}`)

/** 按核销码查订单预览（核销第一步"核对"，只读）：{orderId, voucherTitle, nickName, payValue, status, verifyStatus} */
export const getVerifyPreview = (code) => get(`/api/merchant/verify/preview/${encodeURIComponent(code)}`)

/**
 * 工作台统计：MerchantStatsVO
 * { today: {pendingVerify, verified, revenue}, recent: [{orderId, voucherTitle, nickName, verifyTime, status, verifyCodeTail}] }
 */
export const merchantStats = () => get('/api/merchant/verify/stats')

/**
 * 核销记录分页：VerifyRecordsVO { total, list }
 * status：0未核销 1已核销 2已作废，不传查全部
 */
export const verifyRecords = ({ shopId, status, current = 1, pageSize = 10 } = {}) =>
  get('/api/merchant/verify/records', { shopId, status, current, pageSize })

/**
 * 提交开店申请（商家登录后调用）：POST /api/merchant/shop/apply
 * {shopName, typeId, address, contactPhone}
 * 规则：管理员不能直接新增店铺——店铺只能由商家提交申请、平台审核通过后上线
 */
export const applyShopOpen = (data) => post('/api/merchant/shop/apply', data)

/** 我的开店申请列表：[{id, shopName, status(0待审核/1已通过/2已拒绝), rejectReason, shopId, createTime}] */
export const listMyShopApplies = () => get('/api/merchant/shop/apply/list')
