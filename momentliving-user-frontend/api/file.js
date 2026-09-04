import { transformUrl } from '../utils/urlTransform.js'
import { del } from '../utils/request.js'

/**
 * 上传图片到 OSS（uni.uploadFile + Authorization 头）
 * @param {string} filePath 本地临时文件路径（uni.chooseImage 返回）
 * @param {string} dir 上传目录：blogs/shops/avatars/icons/vouchers/reviews
 * @returns {Promise<string>} OSS URL
 */
export function uploadImage(filePath, dir = 'blogs') {
  return new Promise((resolve, reject) => {
    // 上传统一用用户端 token（商家端暂无上传场景）
    const token = uni.getStorageSync('userToken')
    uni.uploadFile({
      url: transformUrl(`/api/file/upload?dir=${dir}`),
      filePath,
      name: 'file',
      header: token ? { Authorization: token } : {},
      success: (res) => {
        try {
          const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
          // 后端 Result：code=1 成功（见 utils/request.js 说明）
          if (res.statusCode === 200 && data && (data.code === 1 || data.success === true)) {
            resolve(data.data)
            return
          }
          uni.showToast({ title: data.msg || '图片上传失败', icon: 'none' })
          reject(new Error(data.msg || '图片上传失败'))
        } catch (e) {
          uni.showToast({ title: '图片上传失败', icon: 'none' })
          reject(e)
        }
      },
      fail: () => {
        uni.showToast({ title: '图片上传失败，请检查网络', icon: 'none' })
        reject(new Error('图片上传失败'))
      }
    })
  })
}

/** 删除 OSS 图片 */
export const deleteImage = (url) => del(`/api/file/delete?url=${encodeURIComponent(url)}`)
