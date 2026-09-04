import { get, post, put } from '../utils/request.js'

/** 首页分类宫格（ShopType：id/name/images/sort） */
export const listShopType = () => get('/api/shop-type/list')

/** 收藏/取消收藏店铺（toggle，返回 true=操作后已收藏） */
export const favoriteShop = (id) => put(`/api/shop/favorite/${id}`)

/** 是否已收藏店铺 */
export const isShopFavorite = (id) => get(`/api/shop/favorite/is-favorite/${id}`)

/** 我收藏的店铺（ShopVO[]，收藏时间倒序） */
export const myFavoriteShops = () => get('/api/shop/favorite/list')

/** 分类分页店铺（ShopVO = Shop + distance；typeId/current/x/y） */
export const listShopByType = ({ typeId, current = 1, x, y } = {}) =>
  get('/api/shop/of/type', { typeId, current, x, y })

/** 按店铺名称关键词搜索（分页，name 必填） */
export const searchShopByName = (name, current = 1) => get('/api/shop/of/name', { name, current })

/** 店铺详情 */
export const getShop = (id) => get(`/api/shop/${id}`)

/** 编辑店铺（商家端营业信息） */
export const updateShop = (data) => put('/api/shop', data)

/** 店铺评分汇总：{ avg: "4.8", cnt: "12" } */
export const getShopScore = (shopId) => get(`/api/review/score/${shopId}`)

/** 店铺评价分页（ReviewVO：nickName/avatar/rating/content/createTime） */
export const listReview = ({ shopId, current = 1 } = {}) => get(`/api/review/list/${shopId}`, { current })

/** 提交评价（ReviewDTO：shopId/orderId/rating/content/images[]） */
export const addReview = (data) => post('/api/review', data)
