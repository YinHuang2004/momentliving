<template>
  <div class="page">
    <div class="page-toolbar">
      <el-select v-model="query.typeId" placeholder="全部分类" clearable style="width: 160px" @change="reload">
        <el-option v-for="t in types" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <!-- 🆕 搜索：名称/地址走 ES 中文分词 + 相关性排序，纯数字按店铺 ID 精确匹配 -->
      <el-input
        v-model="query.keyword"
        placeholder="按店铺名/地址搜索（ES），或输入店铺ID"
        clearable
        style="width: 280px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button :icon="Search" @click="reload">搜索</el-button>
      <!-- 业务规则：管理员不能新增店铺——店铺上线走商家开店申请+平台审核（入驻审核页/开店申请审核） -->
    </div>

    <el-table :data="list" v-loading="loading" class="brand-card" row-style="height:72px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="头图" width="90">
        <template #default="{ row }">
          <img class="cell-img" :src="coverOf(row)" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="店铺名" min-width="180" show-overflow-tooltip />
      <el-table-column label="分类" width="110">
        <template #default="{ row }">{{ typeName(row.typeId) }}</template>
      </el-table-column>
      <el-table-column label="人均" width="100">
        <template #default="{ row }">
          <span class="accent-num" v-if="row.avgPrice">¥{{ (row.avgPrice / 100).toFixed(0) }}</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="sold" label="已售" width="90" />
      <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">下架</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="prev, pager, next"
        :total="total"
        :page-size="10"
        :current-page="query.current"
        @current-change="onPage"
      />
    </div>

    <!-- 编辑抽屉（店铺只能由商家申请上线，这里只编辑已有店铺） -->
    <el-drawer v-model="drawerVisible" title="编辑店铺" size="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="店铺名" required>
          <el-input v-model="form.name" maxlength="32" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.typeId" placeholder="选择分类" style="width: 100%">
            <el-option v-for="t in types" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="头图">
          <ImageUpload v-model="form.images" dir="shops" />
        </el-form-item>
        <el-form-item label="商圈">
          <el-input v-model="form.area" maxlength="32" placeholder="如：朝阳区" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" maxlength="64" />
        </el-form-item>
        <el-form-item label="营业时间">
          <el-time-picker
            v-model="openHoursRange"
            is-range
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="HH:mm"
            value-format="HH:mm"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="人均(元)">
          <el-input-number v-model="form.avgPriceYuan" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="经纬度">
          <div style="display:flex; gap:8px; width:100%">
            <el-input-number v-model="form.x" :precision="6" :step="0.001" placeholder="经度" style="flex:1" />
            <el-input-number v-model="form.y" :precision="6" :step="0.001" placeholder="纬度" style="flex:1" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listShopTypes, listShops, searchShops, updateShop, deleteShop } from '../api/shop'
import ImageUpload from '../components/ImageUpload.vue'

const PAGE_SIZE = 10

const types = ref([])
const list = ref([])
const loading = ref(false)
const query = reactive({ typeId: null, keyword: '', current: 1 })
const total = ref(0)

const drawerVisible = ref(false)
const saving = ref(false)
const emptyForm = () => ({
  id: null, name: '', typeId: null, images: '', area: '', address: '',
  openHours: '', avgPriceYuan: 0, x: 116.397428, y: 39.90923
})
const form = reactive(emptyForm())

// 营业时间存 "HH:mm-HH:mm" 字符串，编辑时用区间时间选择器；
// 存量数据格式不规整时选择器留空，保存即以选择结果为准
const openHoursRange = computed({
  get() {
    const parts = (form.openHours || '').split('-').map((s) => s.trim())
    return parts.length === 2 && parts.every((t) => /^\d{1,2}:\d{2}$/.test(t)) ? parts : null
  },
  set(v) {
    form.openHours = Array.isArray(v) && v.length === 2 ? `${v[0]}-${v[1]}` : ''
  }
})

function typeName(typeId) {
  const t = types.value.find((x) => Number(x.id) === Number(typeId))
  return t ? t.name : typeId || '—'
}

function coverOf(row) {
  return (row.images || '').split(',').filter(Boolean)[0] || '/vite.svg'
}

async function load() {
  loading.value = true
  try {
    const kw = (query.keyword || '').trim()
    const res = kw
      ? await searchShops({ keyword: kw, typeId: query.typeId, current: query.current })
      : await listShops({ typeId: query.typeId, current: query.current })
    list.value = res || []
    // 后端 /shop/of/type 每页固定 10 条且不回传 total：满页再允许翻页
    total.value = query.current * PAGE_SIZE + (list.value.length === PAGE_SIZE ? PAGE_SIZE : 0)
  } catch (e) {
    // 已统一 toast
  } finally {
    loading.value = false
  }
}

function reload() {
  query.current = 1
  load()
}

function onPage(p) {
  query.current = p
  load()
}

function openForm(row) {
  Object.assign(form, emptyForm(), row || {}, {
    avgPriceYuan: row && row.avgPrice ? Math.round(row.avgPrice / 100) : 0
  })
  drawerVisible.value = true
}

async function save() {
  if (!form.name.trim()) return ElMessage.warning('请填写店铺名')
  if (!form.typeId) return ElMessage.warning('请选择分类')
  saving.value = true
  try {
    const payload = {
      id: form.id || undefined,
      name: form.name.trim(),
      typeId: form.typeId,
      images: form.images || '',
      area: form.area || '',
      address: form.address || '',
      openHours: form.openHours || '',
      x: form.x,
      y: form.y,
      avgPrice: Math.round(form.avgPriceYuan * 100),
      sold: form.sold ?? 0,
      comments: form.comments ?? 0,
      score: form.score ?? 0
    }
    if (!form.id) return ElMessage.warning('店铺只能由商家申请上线，不支持新增')
    await updateShop(payload)
    ElMessage.success('保存成功')
    drawerVisible.value = false
    load()
  } catch (e) {
    // 已统一 toast
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  const ok = await ElMessageBox.confirm(`确定下架店铺「${row.name}」吗？`, '下架确认', { type: 'warning' })
    .then(() => true).catch(() => false)
  if (!ok) return
  try {
    await deleteShop(row.id)
    ElMessage.success('已下架')
    load()
  } catch (e) {
    // 已统一 toast
  }
}

onMounted(async () => {
  try {
    types.value = (await listShopTypes()) || []
  } catch (e) { /* 已统一 toast */ }
  load()
})
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 16px 4px;
}
</style>
