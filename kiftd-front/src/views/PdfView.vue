<template>
  <div class="pdf-page">
    <header class="pdf-bar">
      <div class="pdf-title">{{ title }}</div>
      <el-button @click="$router.push('/')">返回</el-button>
    </header>
    <iframe v-if="src" class="pdf-frame" :src="src" title="pdf" />
    <div v-else-if="error" class="pdf-status error">{{ error }}</div>
    <div v-else class="pdf-status">正在加载 PDF…</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()
const fileId = computed(() => route.params.fileId as string)
const kind = computed(() => (route.query.kind as string) || 'pdf')
const title = ref((route.query.name as string) || 'PDF 预览')
const src = ref('')
const error = ref('')
let objectUrl = ''

async function load() {
  error.value = ''
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = ''
  }
  src.value = ''
  document.title = title.value
  try {
    const path =
      kind.value === 'txt'
        ? `/api/preview/txt-pdf/${fileId.value}`
        : kind.value === 'office'
          ? `/api/preview/office-pdf/${fileId.value}`
          : `/api/preview/pdf/${fileId.value}`
    const headers: HeadersInit = {}
    if (auth.token) {
      headers.Authorization = `Bearer ${auth.token}`
    }
    const res = await fetch(path, { headers })
    if (!res.ok) {
      throw new Error('无法加载 PDF')
    }
    const blob = await res.blob()
    objectUrl = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }))
    src.value = objectUrl
  } catch (e: any) {
    error.value = e.message || 'PDF 打开失败'
  }
}

watch(title, (v) => {
  document.title = v
})

onMounted(load)
onBeforeUnmount(() => {
  if (objectUrl) URL.revokeObjectURL(objectUrl)
  document.title = '文件管理系统'
})
</script>

<style scoped>
.pdf-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #eef2f6;
}
.pdf-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 18px;
  background: #fff;
  border-bottom: 1px solid #dbe3ec;
}
.pdf-title {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pdf-frame {
  flex: 1;
  width: 100%;
  min-height: 0;
  border: 0;
  background: #525659;
}
.pdf-status {
  padding: 24px;
  text-align: center;
  color: #6b7280;
}
.pdf-status.error {
  color: #b91c1c;
}
</style>
