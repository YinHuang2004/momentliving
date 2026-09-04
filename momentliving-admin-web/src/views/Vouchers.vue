<template>
  <div class="page">
    <div class="page-toolbar">
      <div class="scope-legend">
        <el-tag effect="plain" type="success">全场通用</el-tag>
        <el-tag effect="plain">指定多店</el-tag>
        <el-tag effect="plain" type="info">单店</el-tag>
      </div>
      <div class="grow"></div>
      <el-button type="primary" :icon="Plus" @click="openForm()">发放优惠券</el-button>
    </div>

    <el-table :data="vouchers" v-loading="loading" class="brand-card" row-style="height:64px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="券名" min-width="170" show-overflow-tooltip />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.type === 1 ? 'warning' : 'info'" effect="plain">
            {{ row.type === 1 ? '秒杀券' : '普通券' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="适用范围" min-width="200">
        <template #default="{ row }">
          <template v-if="scopeCountOf(row) === 0">
            <el-tag effect="plain" type="success">全部店铺</el-tag>
          </template>
          <template v-else-if="scopeCountOf(row) === 1">
            <span>{{ shopName(row.shopIds[0]) }}</span>
          </template>
          <template v-else>
            <el-tooltip :content="shopNamesOf(row)" placement="top">
              <el-tag effect="plain">指定 {{ scopeCountOf(row) }} 家店铺</el-tag>
            </el-tooltip>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="售价" width="100">
        <template #default="{ row }">
          <span class="accent-num">¥{{ fen2yuan(row.payValue) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="面值" width="100">
        <template #default="{ row }">¥{{ fen2yuan(row.actualValue) }}</template>
      </el-table-column>
      <el-table-column label="库存" width="100">
        <template #default="{ row }">
          {{ row.type === 1 ? (row.stock ?? '—') : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="活动时间" min-width="190">
        <template #default="{ row }">
          <template v-if="row.type === 1 && row.beginTime">
            {{ fmtTime(row.beginTime) }} ~ {{ fmtTime(row.endTime) }}
          </template>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
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
        :page-size="pageSize"
        :current-page="current"
        @current-change="onPage"
      />
    </div>

    <!-- 发放/编辑抽屉（秒杀开关切换普通券/秒杀券；适用范围=全部/指定店铺） -->
    <el-drawer v-model="drawerVisible" :title="form.id ? '编辑优惠券' : '发放优惠券'" size="440px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="券名" required>
          <el-input v-model="form.title" maxlength="32" placeholder="如：满100减20券" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="form.subTitle" maxlength="64" />
        </el-form-item>
        <el-form-item label="使用规则">
          <el-input v-model="form.rules" type="textarea" :rows="3" maxlength="200" placeholder="如：除桌床类商品外全场可用" />
        </el-form-item>

        <!-- 🆕 适用范围：全部店铺 / 指定店铺（可多选=批量发放） -->
        <el-form-item label="适用范围" required>
          <el-radio-group v-model="form.scope">
            <el-radio value="all">全部店铺</el-radio>
            <el-radio value="part">指定店铺</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scope === 'part'" label="适用店铺" required>
          <el-select
            v-model="form.shopIds"
            multiple
            filterable
            placeholder="选择一家或多家的店铺（多选=批量发放）"
            style="width: 100%"
          >
            <el-option v-for="s in shops" :key="s.id" :label="`${s.name}（#${s.id}）`" :value="s.id" />
          </el-select>
          <div class="form-tip-line">选 1 家=单店券；选多家=批量发放到各店；全场券请选"全部店铺"</div>
        </el-form-item>

        <el-form-item label="售价(元)" required>
          <el-input-number v-model="form.payYuan" :min="0" :precision="2" :step="1" />
        </el-form-item>
        <el-form-item label="面值(元)" required>
          <el-input-number v-model="form.actualYuan" :min="0" :precision="2" :step="1" />
        </el-form-item>

        <el-form-item label="秒杀券">
          <el-switch v-model="form.seckill" :disabled="!!form.id" />
          <span class="form-tip">{{ form.id ? '创建后不可切换类型' : '开启后需填库存与活动时间' }}</span>
        </el-form-item>
        <template v-if="form.seckill">
          <el-form-item label="库存" required>
            <el-input-number v-model="form.stock" :min="1" :max="999999" />
          </el-form-item>
          <el-form-item label="开始时间" required>
            <el-date-picker v-model="form.beginTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
          </el-form-item>
          <el-form-item label="结束时间" required>
            <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { listShops } from '../api/shop'
import { listAllVouchers, addVoucher, addSeckillVoucher, updateVoucher, deleteVoucher } from '../api/voucher'

const PAGE_SIZE = 10

const shops = ref([])
const vouchers = ref([])
const loading = ref(false)
const current = ref(1)
const total = ref(0)

const drawerVisible = ref(false)
const saving = ref(false)
const emptyForm = () => ({
  id: null, title: '', subTitle: '', rules: '',
  scope: 'all',        // 'all' | 'part'
  shopIds: [],
  payYuan: 0, actualYuan: 0,
  seckill: false, stock: 100,
  beginTime: dayjs().format('YYYY-MM-DDTHH:mm:ss'),
  endTime: dayjs().add(3, 'day').format('YYYY-MM-DDTHH:mm:ss')
})
const form = reactive(emptyForm())

function fen2yuan(v) {
  const n = Number(v)
  return n ? (n / 100).toFixed(n % 100 === 0 ? 0 : 2) : '0'
}

function fmtTime(t) {
  return dayjs(t).format('MM-DD HH:mm')
}

/** 行的适用店铺数（后端统一回填 shopIds；0 = 全场通用，1 = 单店，N = 多店） */
function scopeCountOf(row) {
  return (row.shopIds || []).length
}

function shopName(id) {
  const s = shops.value.find((x) => Number(x.id) === Number(id))
  return s ? s.name : `店铺#${id}`
}

function shopNamesOf(row) {
  return (row.shopIds || []).map((id) => `#${id} ${shopName(id)}`).join('、')
}

async function loadShops() {
  // 店铺选择器：拉前几页足够筛选（数据量大可换成搜索选择器）
  const acc = []
  for (let p = 1; p <= 5; p++) {
    const res = await listShops({ current: p })
    acc.push(...(res || []))
    if (!res || res.length < 10) break
  }
  shops.value = acc
}

async function loadVouchers() {
  loading.value = true
  try {
    vouchers.value = (await listAllVouchers({ current: current.value, pageSize: PAGE_SIZE })) || []
    // 后端每页固定 pageSize：满页再允许翻页
    total.value = current.value * PAGE_SIZE + (vouchers.value.length === PAGE_SIZE ? PAGE_SIZE : 0)
  } catch (e) {
    // 已统一 toast
  } finally {
    loading.value = false
  }
}

function onPage(p) {
  current.value = p
  loadVouchers()
}

function openForm(row) {
  Object.assign(form, emptyForm())
  if (row) {
    Object.assign(form, {
      id: row.id,
      title: row.title,
      subTitle: row.subTitle || '',
      rules: row.rules || '',
      scope: scopeCountOf(row) === 0 ? 'all' : 'part',
      shopIds: [...(row.shopIds || [])],
      payYuan: Number(fen2yuan(row.payValue)),
      actualYuan: Number(fen2yuan(row.actualValue)),
      seckill: row.type === 1,
      stock: row.stock || 100
    })
  }
  drawerVisible.value = true
}

async function save() {
  if (!form.title.trim()) return ElMessage.warning('请填写券名')
  if (form.scope === 'part' && form.shopIds.length === 0) return ElMessage.warning('请至少选择一家店铺')
  if (form.payYuan <= 0 || form.actualYuan <= 0) return ElMessage.warning('售价/面值需大于 0')
  if (form.actualYuan < form.payYuan) return ElMessage.warning('面值不能低于售价')
  saving.value = true
  try {
    const base = {
      id: form.id || undefined,
      title: form.title.trim(),
      subTitle: form.subTitle || '',
      rules: form.rules || '',
      payValue: Math.round(form.payYuan * 100),
      actualValue: Math.round(form.actualYuan * 100),
      type: form.seckill ? 1 : 0,
      // 范围：全场 allShop=true；指定店铺 shopIds（选 1 家即单店）
      allShop: form.scope === 'all',
      shopIds: form.scope === 'part' ? form.shopIds : undefined
    }
    if (form.id) {
      await updateVoucher(base)
    } else if (form.seckill) {
      if (!form.beginTime || !form.endTime) return ElMessage.warning('请填写活动时间')
      await addSeckillVoucher({ ...base, stock: form.stock, beginTime: form.beginTime, endTime: form.endTime })
    } else {
      await addVoucher(base)
    }
    ElMessage.success('保存成功')
    drawerVisible.value = false
    loadVouchers()
  } catch (e) {
    // 已统一 toast
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  const ok = await ElMessageBox.confirm(`确定下架优惠券「${row.title}」吗？`, '下架确认', { type: 'warning' })
    .then(() => true).catch(() => false)
  if (!ok) return
  try {
    await deleteVoucher(row.id)
    ElMessage.success('已下架')
    loadVouchers()
  } catch (e) {
    // 已统一 toast
  }
}

onMounted(async () => {
  try {
    await loadShops()
  } catch (e) { /* 已统一 toast */ }
  loadVouchers()
})
</script>

<style scoped>
.form-tip {
  margin-left: 10px;
  color: var(--text-sub);
  font-size: 12px;
}

.form-tip-line {
  width: 100%;
  margin-top: 6px;
  color: var(--text-sub);
  font-size: 12px;
  line-height: 1.5;
}

.scope-legend {
  display: flex;
  gap: 8px;
}
</style>
