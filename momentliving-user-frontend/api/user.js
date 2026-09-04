import { get, post, put, setUserToken } from '../utils/request.js'

/**
 * 发送验证码（email/phone 二选一）
 * 返回 CaptchaVO {code, expireSeconds}：
 * - 手机号：演示模式（未接短信），code 直接返回前端展示，expireSeconds 为有效期秒数（Redis TTL 保证过期）
 * - 邮箱：走 SMTP 发送，code 为 null
 */
export const sendCode = ({ email, phone }) => {
  const query = phone
    ? `phone=${encodeURIComponent(phone)}`
    : `email=${encodeURIComponent(email)}`
  return post(`/api/user/code?${query}`)
}

/**
 * 验证码登录：POST /api/user/login {email|phone, code}
 * 返回 LoginVO { token, refreshToken, userInfo: {id, nickName, images} }，
 * 用户端双 token 存独立命名空间（userToken/refreshToken），与商家端互不顶号
 */
export async function loginByCode({ email, phone, code }) {
  const res = await post('/api/user/login', { email, phone, code })
  if (res && res.token) {
    setUserToken(res.token)
    uni.setStorageSync('refreshToken', res.refreshToken || '')
    uni.setStorageSync('userInfo', res.userInfo || {})
  }
  return res
}

/** 刷新 accessToken：POST /api/user/refresh?refreshToken=，返回 {token, ...} */
export async function refreshToken(refreshToken) {
  const res = await post(`/api/user/refresh?refreshToken=${encodeURIComponent(refreshToken)}`)
  if (res && (res.token || res.accessToken)) {
    setUserToken(res.token || res.accessToken)
  }
  return res
}

/** 当前登录用户信息（脱敏 UserVO：id/nickName/images） */
export const getMe = () => get('/api/user/me')

/** 退出登录 */
export const logout = () => post('/api/user/logout')

/** 更新用户详细资料（nickName 存 user 表，city/introduce/gender/birthday 存 UserInfo 表） */
export const updateInfo = (data) => put('/api/user/info', data)

/** 修改头像（image 为 file-service 上传后的 URL；user.images 是全站头像来源） */
export const updateAvatar = (image) => put(`/api/user/avatar?image=${encodeURIComponent(image)}`)

/** 查询用户详细资料 */
export const getUserInfo = (userId) => get(`/api/user/info/${userId}`)

/** 每日签到 */
export const sign = () => post('/api/user/sign')

/** 本月签到天数 */
export const signCount = () => get('/api/user/sign/count')

// ========== 每日积分 ==========

/** 领取每日积分（每天 1 次共 10 分），返回累加后的总积分 */
export const claimCredits = () => post('/api/user/credits/claim')

/** 查询积分信息：{credits, claimedToday} */
export const getCredits = () => get('/api/user/credits')

/** 关注用户（toggle：已关注则取关、未关注则关注，无需先查状态） */
export const follow = (userId) => put(`/api/follow/${userId}`)

/** 是否已关注 */
export const isFollow = (userId) => get(`/api/follow/is-follow/${userId}`)

/** 关注/粉丝列表（type=1 关注 / 2 粉丝），返回 UserVO 数组 */
export const followList = (type) => get(`/api/follow/list/${type}`)

/** 某用户的关注/粉丝列表（他人主页统计入口）：type=1 TA关注的人 / 2 TA的粉丝 */
export const userFollowList = (userId, type) => get(`/api/follow/list/${userId}/${type}`)

/** 共同关注 */
export const commonFollow = (userId) => get(`/api/follow/commons/${userId}`)

/** 某用户的关注数/粉丝数：{followee, follower}（个人主页） */
export const followCount = (userId) => get(`/api/follow/count/${userId}`)

// ========== 个人主页足迹（购物记录） ==========

/** 当前登录用户的足迹设置：{visible, clearedTime} */
export const footprintSettings = () => get('/api/user/footprint/settings')

/** 设置足迹是否对他人可见 */
export const updateFootprintVisible = (visible) => put(`/api/user/footprint/visible?visible=${visible}`)

/** 清空足迹（早于清空时间点的购物记录对他人隐藏，订单本身保留） */
export const clearFootprint = () => post('/api/user/footprint/clear')
