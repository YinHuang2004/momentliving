<template>
  <div class="page">
    <div class="page-toolbar">
      <el-select v-model="shopId" placeholder="选择店铺" filterable style="width: 280px" @change="reload">
        <el-option v-for="s in shops" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-select v-model="status" placeholder="核销状态" clearable style="width: 160px" @change="reload">
        <el-option label="未核销" :value="0" />
        <el-option label="已核销" :value="1" />
        <el-option label="已作废" :value="2" />
      </el-select>
    </div>

    <el-table :data="list" v-loading="loading" class="brand-card" row-style="height:64px">
      <el-table-column prop="orderId" label="订单ID" width="110" />
      <el-table-column prop="voucherTitle" label="券名" min-width="180" show-overflow-tooltip />
      <el-table-column label="买家" width="140">
        <template #default="{ row }">{{ row.nickName || ('用户' + row.userId) }}</template>
      </el-table-column>
      <el-table-column label="核销码" width="150">
        <template #default="{ row }">
          <el-text v-if="row.verifyCodeTail" type="info">**** **** **** {{ row.verifyCodeTail }}</el-text>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" effect="plain">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="核销时间" width="170">
        <template #default="{ row }">{{ fmt(row.verifyTime) }}</template>
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
import { listShops } from '../api/shop'
import { listVerifyRecords } from '../api/admin'

const PAGE_SIZE = 10

const shops = ref([])
const shopId = ref(null)
const status = ref(null)
const list = ref([])
const loading = ref(false)
const current = ref(1)
const total = ref(0)

function statusText(s) {
  return { 0: '未核销', 1: '已核销', 2: '已作废' }[s] || s
}
function statusType(s) {
  return { 0: 'warning', 1: 'success', 2: 'info' }[s] || 'info'
}
function fmt(t) {
  return (t || '').replace('T', ' ').slice(0, 16)
}

async function loadShops() {
  const acc = []
  for (let p = 1; p <= 5; p++) {
    const res = await listShops({ current: p })
    acc.push(...(res || []))
    if (!res || res.length < 10) break
  }
  shops.value = acc
}

async function load() {
  if (!shopId.value) return
  loading.value = true
  try {
    const res = await listVerifyRecords({
      shopId: shopId.value,
      status: status.value ?? undefined,
      current: current.value,
      pageSize: PAGE_SIZE
    })
    list.value = (res && res.list) || []
    total.value = (res && res.total) || 0
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

onMounted(async () => {
  try {
    await loadShops()
  } catch (e) { /* 已统一 toast */ }
})
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 16px 4px;
}
</style>
