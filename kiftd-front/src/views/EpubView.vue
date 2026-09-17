<template>
  <div class="epub-page">
    <header class="epub-bar">
      <div class="epub-title">{{ fileName || 'EPUB 阅读' }}</div>
      <div class="epub-actions">
        <el-button title="也可滚轮 / 左右点击翻页" @click="prev">上一页</el-button>
        <el-button title="也可滚轮 / 左右点击翻页" @click="next">下一页</el-button>
        <el-button @click="$router.push('/')">返回</el-button>
      </div>
    </header>
    <div v-if="loading" class="epub-status">正在加载电子书…</div>
    <div v-else-if="error" class="epub-status error">{{ error }}</div>
    <div ref="viewerRef" class="epub-viewer" />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import ePub from 'epubjs'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()
const fileId = route.params.fileId as string
const viewerRef = ref<HTMLElement | null>(null)
const fileName = ref((route.query.name as string) || '')
const loading = ref(true)
const error = ref('')

let book: ReturnType<typeof ePub> | null = null
let rendition: ReturnType<ReturnType<typeof ePub>['renderTo']> | null = null
let objectUrl: string | null = null
let wheelAcc = 0
let wheelLock = false
let wheelUnlockTimer: ReturnType<typeof setTimeout> | null = null
const WHEEL_THRESHOLD = 60
const WHEEL_COOLDOWN_MS = 320

watch(fileName, (v) => {
  if (v) document.title = v
}, { immediate: true })

async function load() {
  loading.value = true
  error.value = ''
  try {
    const headers: HeadersInit = {}
    if (auth.token) {
      headers.Authorization = `Bearer ${auth.token}`
    }
    const res = await fetch(`/api/preview/resource/${fileId}`, { headers })
    if (!res.ok) {
      throw new Error('无法加载 EPUB 文件')
    }
    if (!fileName.value) {
      fileName.value = 'book.epub'
    }
    const blob = await res.blob()
    objectUrl = URL.createObjectURL(blob)
    book = ePub(objectUrl)
    await book.ready
    if (!viewerRef.value) return
    rendition = book.renderTo(viewerRef.value, {
      width: '100%',
      height: '100%',
      flow: 'paginated',
      allowScriptedContent: true,
    })
    rendition.hooks.content.register((contents: any) => {
      const doc: Document | undefined = contents?.document
      if (!doc) return
      const onWheel = (e: WheelEvent) => onEpubWheel(e)
      const onClick = (e: MouseEvent) => onEpubTapTurn(e, doc.defaultView || undefined)
      doc.addEventListener('wheel', onWheel, { passive: false })
      doc.addEventListener('click', onClick)
      contents.on?.('destroy', () => {
        doc.removeEventListener('wheel', onWheel)
        doc.removeEventListener('click', onClick)
      })
    })
    await rendition.display()
    viewerRef.value.addEventListener('wheel', onEpubWheel, { passive: false })
    viewerRef.value.addEventListener('click', onEpubTapTurn as EventListener)
    window.addEventListener('keydown', onKey)
  } catch (e: any) {
    error.value = e.message || 'EPUB 打开失败'
  } finally {
    loading.value = false
  }
}

function turn(dir: 1 | -1) {
  if (!rendition) return
  void (dir > 0 ? rendition.next() : rendition.prev())
}

function prev() {
  turn(-1)
}

function next() {
  turn(1)
}

function onEpubWheel(e: WheelEvent) {
  if (!rendition) return
  if (Math.abs(e.deltaX) > Math.abs(e.deltaY)) return
  e.preventDefault()
  e.stopPropagation()
  if (wheelLock) return

  let delta = e.deltaY
  if (e.deltaMode === 1) delta *= 16
  else if (e.deltaMode === 2) delta *= WHEEL_THRESHOLD

  wheelAcc += delta
  if (Math.abs(wheelAcc) < WHEEL_THRESHOLD) return

  const dir: 1 | -1 = wheelAcc > 0 ? 1 : -1
  wheelAcc = 0
  wheelLock = true
  turn(dir)
  if (wheelUnlockTimer) clearTimeout(wheelUnlockTimer)
  wheelUnlockTimer = setTimeout(() => {
    wheelLock = false
    wheelAcc = 0
    wheelUnlockTimer = null
  }, WHEEL_COOLDOWN_MS)
}

function onEpubTapTurn(e: MouseEvent, win?: Window) {
  if (!rendition) return
  const target = e.target as HTMLElement | null
  if (!target) return
  if (target.closest?.('a, button, input, textarea, select, summary, label')) return
  const selection = (win || window).getSelection?.()
  if (selection && !selection.isCollapsed && String(selection).trim()) return

  const viewer = viewerRef.value
  if (!viewer) return
  const rect = viewer.getBoundingClientRect()
  let clientX = e.clientX
  if (win && win !== window) {
    const frame = win.frameElement as HTMLElement | null
    if (frame) {
      const fr = frame.getBoundingClientRect()
      clientX = fr.left + e.clientX
    }
  }
  const ratio = (clientX - rect.left) / Math.max(rect.width, 1)
  if (ratio < 0.28) turn(-1)
  else if (ratio > 0.72) turn(1)
}

function onKey(e: KeyboardEvent) {
  const tag = (e.target as HTMLElement | null)?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return
  if (e.key === 'ArrowLeft' || e.key === 'ArrowUp' || e.key === 'PageUp') {
    e.preventDefault()
    prev()
  } else if (
    e.key === 'ArrowRight' ||
    e.key === 'ArrowDown' ||
    e.key === 'PageDown' ||
    e.key === ' ' ||
    e.key === 'Spacebar'
  ) {
    e.preventDefault()
    next()
  }
}

onMounted(load)
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  viewerRef.value?.removeEventListener('wheel', onEpubWheel)
  viewerRef.value?.removeEventListener('click', onEpubTapTurn as EventListener)
  if (wheelUnlockTimer) clearTimeout(wheelUnlockTimer)
  rendition?.destroy()
  book?.destroy()
  if (objectUrl) URL.revokeObjectURL(objectUrl)
  document.title = '文件管理系统'
})
</script>

<style scoped>
.epub-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f3f5f8;
}
.epub-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 18px;
  background: #fff;
  border-bottom: 1px solid #dbe3ec;
}
.epub-title {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.epub-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.epub-viewer {
  flex: 1;
  min-height: 0;
  margin: 12px;
  background: #fff;
  border: 1px solid #dbe3ec;
  border-radius: 10px;
  overflow: hidden;
}
.epub-status {
  padding: 24px;
  text-align: center;
  color: #6b7280;
}
.epub-status.error {
  color: #b91c1c;
}
</style>
