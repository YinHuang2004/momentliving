import request from './request'

/**
 * 店铺 / 分类管理接口：/shop/**、/shop-type/**（shop-service）
 * 管理端 token 经网关"管理端模式"放行（写操作同）
 */
export const listShopTypes = () => request.get('/shop-type/list')

export const addShopType = (data) => request.post('/shop-type', data)
export const updateShopType = (data) => request.put('/shop-type', data)
export const deleteShopType = (id) => request.delete(`/shop-type/${id}`)

/** 店铺分页：typeId 不传查全部，每页 10 条（后端固定） */
export const listShops = ({ typeId, current = 1 } = {}) =>
  request.get('/shop/of/type', { params: { typeId, current } })

export const getShop = (id) => request.get(`/shop/${id}`)
// 🆕 管理端不再提供新增店铺（店铺上线=商家开店申请+平台审核），仅保留编辑/下架
export const updateShop = (data) => request.put('/shop', data)

/**
 * 🆕 店铺搜索：按名称/地址关键词（后端 ES 优先：ik 中文分词 + 相关性排序，ES 不可用降级 MySQL like）
 * 数字关键词按店铺 ID 精确匹配
 */
export const searchShops = ({ keyword, typeId, current = 1 } = {}) =>
  request.get('/shop/of/name', { params: { name: keyword, typeId, current } })
/** 下架即删除（后端无独立上下架字段） */
export const deleteShop = (id) => request.delete(`/shop/${id}`)
