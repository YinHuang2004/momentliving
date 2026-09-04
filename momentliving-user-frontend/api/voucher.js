import { get, post } from '../utils/request.js'

/** 店铺优惠券列表（VoucherVO = Voucher + stock/beginTime/endTime；金额单位为分） */
export const listShopVoucher = (shopId) => get(`/api/voucher/list/${shopId}`)

/** 全量秒杀券列表（附带库存与活动时间），供秒杀 Tab 展示 */
export const listSeckillVouchers = () => get('/api/voucher/seckill/list')

/**
 * 🆕 平台券列表（优惠券中心双子页）：type=0 普通券 / 1 秒杀券
 * VoucherVO 附带 shopIds（适用店铺ID，空=全场通用）与 scopeShops（适用店铺精简信息）
 */
export const listPlatformVouchers = (type) => get('/api/voucher/all', { type })

/** 券详情 */
export const getVoucher = (id) => get(`/api/voucher/${id}`)

/**
 * 抢购/下单（一人限购 3 单，异步落库）：POST /api/voucher-order/seckill/{id}
 * 返回订单 id；订单落库由 MQ 异步完成，随后页面查询订单需短暂重试
 */
export const createSeckillOrder = (id) => post(`/api/voucher-order/seckill/${id}`)

/** 普通券下单（type=0，同步落库，无库存/限购限制）：POST /api/voucher-order/buy/{id}，返回订单 id */
export const buyVoucher = (id) => post(`/api/voucher-order/buy/${id}`)

/** 订单详情（VoucherOrder + verifyCode 核销码） */
export const getOrder = (id) => get(`/api/voucher-order/${id}`)

/** 我的订单分页（status：0待支付 1已支付 2已核销 3已退款 4已关闭，不传查全部） */
export const listMyOrders = ({ current = 1, pageSize = 10, status } = {}) =>
  get('/api/voucher-order/my', { current, pageSize, status })

/** 某用户的足迹（购物记录，个人主页）：他人查看受足迹开关限制，只含已支付/已核销 */
export const userFootprint = (userId, current = 1, pageSize = 10) =>
  get(`/api/voucher-order/of/user/${userId}`, { current, pageSize })

/** 发起支付（payType：1微信未开通 2支付宝 3模拟），返回 PayOrderVO */
export const createPay = (orderId, payType) =>
  post(`/api/pay/create/${orderId}?payType=${payType}`)

/** 轮询支付状态：{orderId, orderStatus, payStatus} */
export const payStatus = (orderId) => get(`/api/pay/status/${orderId}`)

/** 申请退款（仅限已支付未核销订单） */
export const refund = (orderId) => post(`/api/pay/refund/${orderId}`)
