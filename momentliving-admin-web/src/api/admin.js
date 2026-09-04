import request from './request'

/**
 * 管理端（平台管理员）接口：/admin/**（网关白名单，admin-service 自行鉴权）
 */
export const login = (data) => request.post('/admin/login', data)
export const logout = () => request.post('/admin/logout')
export const getMe = () => request.get('/admin/me')

/** 数据概览：统计卡 + 分类分布（环形图）+ 近 7 日申请趋势（折线图） */
export const getDashboard = () => request.get('/admin/dashboard')

/** 入驻申请：status 0待审核 1已通过 2已拒绝，不传查全部 */
export const listApplies = ({ status, current = 1, pageSize = 10 } = {}) =>
  request.get('/admin/apply/list', { params: { status, current, pageSize } })

/** 入驻审核：{id, approved, reason?, drillFail?} */
export const auditApply = (data) => request.post('/admin/apply/audit', data)

/** 按店铺查看核销记录（核销记录页） */
export const listVerifyRecords = ({ shopId, status, current = 1, pageSize = 10 } = {}) =>
  request.get('/admin/verify/records', { params: { shopId, status, current, pageSize } })
