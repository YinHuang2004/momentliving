<template>
  <div>
    <el-upload
      :show-file-list="false"
      :http-request="doUpload"
      :multiple="multiple"
      accept="image/*"
    >
      <slot>
        <div class="upload-box">
          <img v-if="cover" :src="cover" class="upload-box__img" />
          <el-icon v-else class="upload-box__icon"><Plus /></el-icon>
        </div>
      </slot>
    </el-upload>
    <div class="upload-list" v-if="urls.length > 1 || (urls.length === 1 && multiple)">
      <div class="upload-list__item" v-for="(u, i) in urls" :key="i">
        <img :src="u" class="upload-list__img" />
        <el-icon class="upload-list__del" @click="removeAt(i)"><Close /></el-icon>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 图片上传（OSS，走 /api/file/upload?dir=xxx，带管理端 token）
 * v-model：逗号分隔的 URL 字符串（与后端 images 列存储一致）
 */
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadImage } from '../api/request'

const props = defineProps({
  modelValue: { type: String, default: '' },
  dir: { type: String, default: 'shops' },
  multiple: { type: Boolean, default: true }
})
const emit = defineEmits(['update:modelValue'])

const urls = computed(() => (props.modelValue || '').split(',').filter(Boolean))
const cover = computed(() => (props.multiple ? '' : (urls.value[0] || '')))

async function doUpload({ file }) {
  try {
    const url = await uploadImage(file, props.dir)
    const next = props.multiple ? [...urls.value, url] : url
    emit('update:modelValue', Array.isArray(next) ? next.join(',') : next)
  } catch (e) {
    ElMessage.error('上传失败，请重试')
  }
}

function removeAt(i) {
  const next = urls.value.slice()
  next.splice(i, 1)
  emit('update:modelValue', next.join(','))
}
</script>

<style scoped>
.upload-box {
  width: 96px;
  height: 96px;
  border-radius: 12px;
  border: 1px dashed var(--brand-line);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--brand-bg);
  cursor: pointer;
  overflow: hidden;
}
.upload-box__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.upload-box__icon {
  font-size: 22px;
  color: var(--text-sub);
}
.upload-list {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.upload-list__item {
  position: relative;
}
.upload-list__img {
  width: 72px;
  height: 72px;
  border-radius: 10px;
  object-fit: cover;
  display: block;
}
.upload-list__del {
  position: absolute;
  right: -6px;
  top: -6px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 12px;
}
</style>
