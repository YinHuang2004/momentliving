<template>
  <div class="page">
    <div class="page-toolbar">
      <el-button type="primary" :icon="Plus" @click="openForm()">新增分类</el-button>
    </div>

    <el-table :data="list" v-loading="loading" class="brand-card" row-style="height:64px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="图标" width="90">
        <template #default="{ row }">
          <img class="cell-img" :src="(row.images || '').split(',').filter(Boolean)[0] || '/vite.svg'" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="分类名" min-width="160" />
      <el-table-column prop="sort" label="排序" width="100" />
      <el-table-column prop="createTime" label="创建时间" width="180">
        <template #default="{ row }">{{ fmt(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑分类' : '新增分类'" width="440px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="分类名" required>
          <el-input v-model="form.name" maxlength="16" placeholder="如：美食" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="图标">
          <ImageUpload v-model="form.images" :multiple="false" dir="icons" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listShopTypes, addShopType, updateShopType, deleteShopType } from '../api/shop'
import ImageUpload from '../components/ImageUpload.vue'

const list = ref([])
const loading = ref(false)
const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ id: null, name: '', sort: 0, images: '' })

async function load() {
  loading.value = true
  try {
    list.value = (await listShopTypes()) || []
  } catch (e) {
    // 已统一 toast
  } finally {
    loading.value = false
  }
}

function openForm(row) {
  Object.assign(form, { id: null, name: '', sort: 0, images: '' }, row || {})
  formVisible.value = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写分类名')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateShopType({ ...form })
    } else {
      await addShopType({ name: form.name, sort: form.sort, images: form.images })
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    load()
  } catch (e) {
    // 已统一 toast
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  const ok = await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？`, '删除确认', { type: 'warning' })
    .then(() => true).catch(() => false)
  if (!ok) return
  try {
    await deleteShopType(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    // 已统一 toast（分类下有店铺时后端会拒绝）
  }
}

function fmt(t) {
  return (t || '').replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>
