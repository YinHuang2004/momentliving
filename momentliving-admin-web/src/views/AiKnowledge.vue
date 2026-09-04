<template>
  <div class="page">
    <div class="page-toolbar">
      <el-button type="primary" :icon="Plus" @click="openUpload">上传文档</el-button>
      <el-button :icon="Search" @click="searchVisible = true">检索测试</el-button>
    </div>

    <el-table :data="list" v-loading="loading" class="brand-card" row-style="height:64px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="文档标题" min-width="180" />
      <el-table-column label="来源类型" width="110">
        <template #default="{ row }">
          <el-tag :type="sourceTagType(row.sourceType)" size="small">{{ sourceText(row.sourceType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="chunkCount" label="知识块数" width="100" />
      <el-table-column label="上传时间" width="180">
        <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadVisible" title="上传知识库文档" width="560px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="50" placeholder="如：退款规则" />
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="form.sourceType" style="width: 200px">
            <el-option label="常见问题 FAQ" value="faq" />
            <el-option label="帮助文档" value="help" />
            <el-option label="平台规则" value="rule" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="12"
            maxlength="20000"
            show-word-limit
            placeholder="粘贴纯文本或 Markdown；空行会作为段落切分边界"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">上传入库</el-button>
      </template>
    </el-dialog>

    <!-- 检索测试对话框 -->
    <el-dialog v-model="searchVisible" title="检索测试" width="560px">
      <el-input v-model="searchQuery" placeholder="输入用户可能的提问，如：秒杀券怎么用" @keyup.enter="doSearch" />
      <div v-if="searchResult" class="search-result">{{ searchResult }}</div>
      <div v-else-if="searched" class="search-result search-result--empty">没有命中任何知识片段</div>
      <template #footer>
        <el-button @click="searchVisible = false">关闭</el-button>
        <el-button type="primary" :loading="searching" @click="doSearch">检索</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listKnowledgeDocs, uploadKnowledgeDoc, deleteKnowledgeDoc, searchKnowledge } from '../api/ai'

const list = ref([])
const loading = ref(false)
const uploadVisible = ref(false)
const saving = ref(false)
const form = ref({ title: '', sourceType: 'faq', content: '' })

const searchVisible = ref(false)
const searchQuery = ref('')
const searchResult = ref('')
const searched = ref(false)
const searching = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    list.value = (await listKnowledgeDocs()) || []
  } finally {
    loading.value = false
  }
}

function openUpload() {
  form.value = { title: '', sourceType: 'faq', content: '' }
  uploadVisible.value = true
}

async function save() {
  if (!form.value.title.trim() || !form.value.content.trim()) {
    ElMessage.warning('标题与内容不能为空')
    return
  }
  saving.value = true
  try {
    const doc = await uploadKnowledgeDoc(form.value)
    ElMessage.success(doc && doc.status === 1 ? '入库成功' : '已保存（向量化未完成，将走关键词检索）')
    uploadVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`删除文档「${row.title}」及其全部知识块？`, '提示', { type: 'warning' })
  await deleteKnowledgeDoc(row.id)
  ElMessage.success('已删除')
  await load()
}

async function doSearch() {
  if (!searchQuery.value.trim()) return
  searching.value = true
  searched.value = false
  try {
    searchResult.value = (await searchKnowledge(searchQuery.value)) || ''
    searched.value = true
  } finally {
    searching.value = false
  }
}

function sourceText(t) {
  return { faq: '常见问题', help: '帮助文档', rule: '平台规则' }[t] || t
}
function sourceTagType(t) {
  return { faq: 'primary', help: 'success', rule: 'warning' }[t] || 'info'
}
function statusText(s) {
  return { 0: '处理中', 1: '已入库', 2: '部分失败' }[s] ?? '未知'
}
function statusTagType(s) {
  return { 0: 'info', 1: 'success', 2: 'warning' }[s] || 'info'
}
function fmt(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}
</script>

<style scoped>
.search-result {
  margin-top: 16px;
  padding: 12px 16px;
  background: #f6f6f2;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  max-height: 300px;
  overflow: auto;
}
.search-result--empty {
  color: #999;
}
</style>
