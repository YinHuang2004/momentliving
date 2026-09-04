import { get, post, put, del } from '../utils/request.js'

/** 当前用户可发布博客的店铺（购买过券的店铺，[{id, name}]），发布页选择用 */
export const purchasableShops = () => get('/api/blog/purchasable-shops')

/** 热门博客流（BlogVO：name=作者昵称/authorImages/isLike；liked=点赞数） */
export const hotBlogs = (current = 1) => get('/api/blog/hot', { current })

/**
 * 关注动态流（推模式 ScrollResult：{list, minTime, offset}）
 * 首次 lastMaxTime 传当前时间戳，翻页传上一次的 minTime
 */
export const followBlogs = ({ lastMaxTime, offset = 0 } = {}) =>
  get('/api/blog/of/follow', { lastId: lastMaxTime, offset })

/** 某用户的博客列表（当前登录用户） */
export const myBlogs = (userId, current = 1) => get(`/api/blog/of/user/${userId}`, { current })

/** 博客详情（BlogVO） */
export const getBlog = (id) => get(`/api/blog/${id}`)

/** 发布博客（BlogDTO：title/images(逗号分隔 OSS URL)/content/shopId） */
export const publishBlog = (data) => post('/api/blog', data)

/** 编辑博客（仅作者本人，后端只允许改 title/content/images） */
export const updateBlog = (id, data) => put(`/api/blog/${id}`, data)

/** 点赞/取消点赞（后端自动判断当前状态并取反） */
export const likeBlog = (id) => put(`/api/blog/like/${id}`)

/** 删除博客（单个传 [id]，批量传 [id1, id2...]，共用同一个后端接口） */
export const deleteBlogs = (ids) => del('/api/blog', ids)

/** 点过赞的用户列表（UserVO[]） */
export const blogLikes = (id) => get(`/api/blog/likes/${id}`)

/** 收藏/取消收藏博客（toggle，返回 true=操作后已收藏） */
export const favoriteBlog = (id) => put(`/api/blog/favorite/${id}`)

/** 是否已收藏博客 */
export const isBlogFavorite = (id) => get(`/api/blog/favorite/is-favorite/${id}`)

/** 我收藏的博客（BlogVO[]，收藏时间倒序） */
export const myFavoriteBlogs = () => get('/api/blog/of/favorite')

/** 我点赞过的博客（BlogVO[]，点赞时间倒序） */
export const myLikedBlogs = () => get('/api/blog/of/likes')

/** 博客评论列表 */
export const listComments = (blogId, current = 1) => get(`/api/blog-comments/list/${blogId}`, { current })

/** 发表评论（BlogComments：blogId/content/parentId/answerId） */
export const addComment = (data) => post('/api/blog-comments', data)

/** 删除评论 */
export const deleteComment = (id) => del(`/api/blog-comments/${id}`)
