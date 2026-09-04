import request from './request'

/**
 * AI 知识库管理接口：/ai/knowledge/**（ai-service）
 * 管理端 token 经网关"管理端模式"放行（AuthGlobalFilter 管理前缀已含 /ai）
 */

/** 文档列表（含切分块数与入库状态） */
export const listKnowledgeDocs = () => request.get('/ai/knowledge/list')

/** 上传文档：{title, sourceType(faq/help/rule), content} —— 含向量化，给长超时 */
export const uploadKnowledgeDoc = (data) =>
  request.post('/ai/knowledge/upload', data, { timeout: 120000 })

/** 删除文档（连同知识块） */
export const deleteKnowledgeDoc = (id) => request.delete(`/ai/knowledge/${id}`)

/** 检索测试：看某问题会命中哪些知识片段（调优知识库用） */
export const searchKnowledge = (query) => request.post('/ai/knowledge/search', { query })
