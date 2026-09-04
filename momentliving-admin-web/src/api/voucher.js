import request from './request'

/**
 * 优惠券管理接口：/voucher/**（voucher-service）
 * 金额单位统一为"分"（Long），表单层用"元"换算
 *
 * 🆕 适用范围模型（2026-08-30）：
 *   allShop=true            → 全场通用券（voucher.shop_id=0，无 voucher_shop 记录）
 *   shopIds=[...] 非空       → 指定多店券（voucher.shop_id=0，范围写 voucher_shop 表）
 *   shopId>0                → 单店券（老入参兼容）
 * 核销：单店券仅限本店、多店券须在名单内、全场通用任意店铺；
 *      全场/多店券核销后归属记到实际核销店铺（工作台统计可见）
 */
export const listVouchersOfShop = (shopId) => request.get(`/voucher/list/${shopId}`)

/** 🆕 管理端券分页（全量，按 id 倒序）：多店券回填 shopIds */
export const listAllVouchers = ({ current = 1, pageSize = 10 } = {}) =>
  request.get('/voucher/page', { params: { current, pageSize } })
export const getVoucher = (id) => request.get(`/voucher/${id}`)
/** 新增普通券（type=0） */
export const addVoucher = (data) => request.post('/voucher', data)
/** 新增秒杀券（type=1，携带 stock/beginTime/endTime，Redis 预扣库存 + MQ） */
export const addSeckillVoucher = (data) => request.post('/voucher/seckill', data)
/** 更新券（基础字段） */
export const updateVoucher = (data) => request.put('/voucher', data)
/** 下架即删除 */
export const deleteVoucher = (id) => request.delete(`/voucher/${id}`)
