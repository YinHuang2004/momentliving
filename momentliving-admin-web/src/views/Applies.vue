<template>
  <div class="page">
    <el-tabs v-model="activeStatus" @tab-change="reload">
      <el-tab-pane label="待审核" name="0" />
      <el-tab-pane label="已通过" name="1" />
      <el-tab-pane label="已拒绝" name="2" />
      <el-tab-pane label="全部" name="all" />
    </el-tabs>

    <el-table :data="list" v-loading="loading" class="brand-card" row-style="height:64px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="shopName" label="店铺名" min-width="160" show-overflow-tooltip />
      <el-table-column prop="username" label="账号" width="140" />
      <el-table-column prop="contactPhone" label="联系电话" width="140" />
      <el-table-column prop="address" label="地址" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" effect="plain">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" width="160">
        <template #default="{ row }">{{ fmt(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button link type="primary" @click="audit(row, true)">通过</el-button>
            <el-button link type="danger" @click="audit(row, false)">拒绝</el-button>
          </template>
          <span v-else class="audit-meta">
            {{ row.rejectReason ? ('原因：' + row.rejectReason) : '已处理' }}
          </span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="current"
        @current-change="onPage"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listApplies, auditApply } from '../api/admin'

const PAGE_SIZE = 10

const activeStatus = ref('0')
const list = ref([])
const loading = ref(false)
const current = ref(1)
const total = ref(0)

function statusText(s) {
  return { 0: '待审核', 1: '已通过', 2: '已拒绝' }[s] || s
}
function statusType(s) {
  return { 0: 'warning', 1: 'success', 2: 'danger' }[s] || 'info'
}
function fmt(t) {
  return (t || '').replace('T', ' ').slice(0, 16)
}

async function load() {
  loading.value = true
  try {
    const params = { current: current.value, pageSize: PAGE_SIZE }
    if (activeStatus.value !== 'all') params.status = Number(activeStatus.value)
    const res = await listApplies(params)
    list.value = res || []
    // 后端返回分页 records（Page.getRecords），total 需另行估算：满页可继续翻页
    total.value = current.value * PAGE_SIZE + (list.value.length === PAGE_SIZE ? PAGE_SIZE : 0)
  } catch (e) {
    // 已统一 toast
  } finally {
    loading.value = false
  }
}

function reload() {
  current.value = 1
  load()
}

function onPage(p) {
  current.value = p
  load()
}

async function audit(row, approved) {
  if (approved) {
    const ok = await ElMessageBox.confirm(
      `通过「${row.shopName}」的入驻申请？\n将自动创建店铺与商家账号（${row.username}）。`,
      '审核确认',
      { type: 'warning' }
    ).then(() => true).catch(() => false)
    if (!ok) return
    await doAudit({ id: row.id, approved: true })
  } else {
    // 拒绝必须填原因
    const { value } = await ElMessageBox.prompt('请填写拒绝原因（商家可见）', '拒绝申请', {
      inputPlaceholder: '如：资质材料不齐全',
      inputValidator: (v) => (v && v.trim() ? true : '原因不能为空')
    }).catch(() => ({ value: null }))
    if (!value) return
    await doAudit({ id: row.id, approved: false, reason: value.trim() })
  }
}

async function doAudit(payload) {
  try {
    await auditApply(payload)
    ElMessage.success('已提交审核结果')
    load()
  } catch (e) {
    // 已统一 toast（账号冲突等）
  }
}

onMounted(load)
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 16px 4px;
}
.audit-meta {
  color: var(--text-sub);
  font-size: 12px;
}
</style>
