<template>
  <Teleport to="body">
    <div v-if="modelValue" class="aw-root" :style="{ zIndex }">
      <div class="aw-mask" @mousedown="onMaskDown" />
      <div
        ref="winRef"
        class="aw-window"
        :class="{
          'is-maximized': maximized,
          'is-browser-fs': browserFs,
          'is-content-fs': contentFs,
        }"
        :style="frameStyle"
        role="dialog"
        aria-modal="true"
      >
        <header
          v-show="!browserFs && !contentFs"
          class="aw-titlebar"
          @mousedown="startDrag"
          @dblclick.prevent="toggleMaximize"
        >
          <div class="aw-title">
            <slot name="title">
              <span class="aw-title-text">{{ title }}</span>
            </slot>
          </div>
          <div class="aw-controls" @mousedown.stop @dblclick.stop>
            <button type="button" class="aw-btn" title="全屏" @click="toggleBrowserFullscreen">
              <svg viewBox="0 0 12 12" aria-hidden="true">
                <path
                  d="M1 4V1h3M8 1h3v3M11 8v3H8M4 11H1V8"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linecap="square"
                />
              </svg>
            </button>
            <button
              type="button"
              class="aw-btn"
              :title="maximized ? '还原' : '最大化'"
              @click="toggleMaximize"
            >
              <svg v-if="!maximized" viewBox="0 0 12 12" aria-hidden="true">
                <rect x="1.5" y="1.5" width="9" height="9" fill="none" stroke="currentColor" stroke-width="1.3" />
              </svg>
              <svg v-else viewBox="0 0 12 12" aria-hidden="true">
                <rect x="3" y="1.5" width="7.5" height="7.5" fill="none" stroke="currentColor" stroke-width="1.2" />
                <path d="M1.5 3.5h7.5v7.5H1.5z" fill="none" stroke="currentColor" stroke-width="1.2" />
              </svg>
            </button>
            <button type="button" class="aw-btn aw-close" title="关闭" @click="close">
              <svg viewBox="0 0 12 12" aria-hidden="true">
                <path d="M2 2l8 8M10 2L2 10" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
              </svg>
            </button>
          </div>
        </header>

        <div class="aw-body" :class="{ 'is-immersive': browserFs || contentFs }">
          <slot :immersive="browserFs || contentFs" :browser-fs="browserFs" />
        </div>

        <template v-if="!maximized && !browserFs">
          <div
            v-for="dir in resizeDirs"
            :key="dir"
            class="aw-resize"
            :class="`aw-resize-${dir}`"
            @mousedown="startResize($event, dir)"
          />
        </template>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

type ResizeDir = 'n' | 's' | 'e' | 'w' | 'ne' | 'nw' | 'se' | 'sw'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    zIndex?: number
    /** 点击遮罩是否关闭 */
    closeOnMask?: boolean
    initialWidth?: number
    initialHeight?: number
    /** 全屏时优先使用的元素（如 video），可获得原生沉浸式全屏 */
    fullscreenTarget?: () => HTMLElement | null | undefined
  }>(),
  {
    title: '',
    zIndex: 3000,
    closeOnMask: false,
    initialWidth: 0,
    initialHeight: 0,
  },
)

const emit = defineEmits<{
  'update:modelValue': [boolean]
  opened: []
  closed: []
  resized: []
}>()

const winRef = ref<HTMLElement | null>(null)
const maximized = ref(false)
const browserFs = ref(false)
/** 内容元素（如 video）自身处于 Fullscreen API */
const contentFs = ref(false)
const left = ref(0)
const top = ref(0)
const width = ref(960)
const height = ref(640)

const resizeDirs: ResizeDir[] = ['n', 's', 'e', 'w', 'ne', 'nw', 'se', 'sw']

let restoreBox = { left: 0, top: 0, width: 960, height: 640 }
let drag: null | { ox: number; oy: number; sl: number; st: number } = null
let resize: null | {
  dir: ResizeDir
  ox: number
  oy: number
  sl: number
  st: number
  sw: number
  sh: number
} = null

const frameStyle = computed(() => {
  if (maximized.value || browserFs.value) {
    return {
      left: '0px',
      top: '0px',
      width: '100vw',
      height: '100vh',
    }
  }
  return {
    left: `${left.value}px`,
    top: `${top.value}px`,
    width: `${width.value}px`,
    height: `${height.value}px`,
  }
})

function clamp(n: number, min: number, max: number) {
  return Math.min(max, Math.max(min, n))
}

function defaultGeometry() {
  const vw = window.innerWidth
  const vh = window.innerHeight
  const w = props.initialWidth > 0 ? props.initialWidth : Math.round(Math.min(vw * 0.9, 1280))
  const h = props.initialHeight > 0 ? props.initialHeight : Math.round(Math.min(vh * 0.86, 820))
  width.value = w
  height.value = h
  left.value = Math.round((vw - w) / 2)
  top.value = Math.round(Math.max(12, (vh - h) / 2))
  restoreBox = { left: left.value, top: top.value, width: w, height: h }
}

function close() {
  emit('update:modelValue', false)
}

function onMaskDown() {
  if (props.closeOnMask) close()
}

function toggleMaximize() {
  if (browserFs.value) return
  if (!maximized.value) {
    restoreBox = { left: left.value, top: top.value, width: width.value, height: height.value }
    maximized.value = true
  } else {
    maximized.value = false
    left.value = restoreBox.left
    top.value = restoreBox.top
    width.value = restoreBox.width
    height.value = restoreBox.height
  }
  nextTick(() => emit('resized'))
}

async function toggleBrowserFullscreen() {
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
      return
    }
    const custom = props.fullscreenTarget?.()
    const el = custom || winRef.value
    if (!el) return
    maximized.value = false
    await el.requestFullscreen()
  } catch {
    /* 浏览器可能拒绝全屏 */
  }
  nextTick(() => emit('resized'))
}

function onFullscreenChange() {
  const fs = document.fullscreenElement
  const target = props.fullscreenTarget?.()
  browserFs.value = fs === winRef.value
  contentFs.value = !!target && fs === target
  nextTick(() => emit('resized'))
}

function startDrag(e: MouseEvent) {
  if (e.button !== 0) return
  if (maximized.value || browserFs.value) return
  const t = e.target as HTMLElement
  if (t.closest('.aw-controls')) return
  drag = { ox: e.clientX, oy: e.clientY, sl: left.value, st: top.value }
  window.addEventListener('mousemove', onPointerMove)
  window.addEventListener('mouseup', endPointer)
}

function startResize(e: MouseEvent, dir: ResizeDir) {
  if (e.button !== 0) return
  e.preventDefault()
  e.stopPropagation()
  resize = {
    dir,
    ox: e.clientX,
    oy: e.clientY,
    sl: left.value,
    st: top.value,
    sw: width.value,
    sh: height.value,
  }
  window.addEventListener('mousemove', onPointerMove)
  window.addEventListener('mouseup', endPointer)
}

function onPointerMove(e: MouseEvent) {
  const vw = window.innerWidth
  const vh = window.innerHeight
  const minW = 480
  const minH = 320

  if (drag) {
    const dx = e.clientX - drag.ox
    const dy = e.clientY - drag.oy
    left.value = clamp(drag.sl + dx, -width.value + 120, vw - 80)
    top.value = clamp(drag.st + dy, 0, vh - 40)
    return
  }

  if (!resize) return
  const dx = e.clientX - resize.ox
  const dy = e.clientY - resize.oy
  let l = resize.sl
  let t = resize.st
  let w = resize.sw
  let h = resize.sh
  const dir = resize.dir

  if (dir.includes('e')) w = clamp(resize.sw + dx, minW, vw - l)
  if (dir.includes('s')) h = clamp(resize.sh + dy, minH, vh - t)
  if (dir.includes('w')) {
    const nw = clamp(resize.sw - dx, minW, resize.sw + resize.sl)
    l = resize.sl + (resize.sw - nw)
    w = nw
  }
  if (dir.includes('n')) {
    const nh = clamp(resize.sh - dy, minH, resize.sh + resize.st)
    t = resize.st + (resize.sh - nh)
    h = nh
  }

  left.value = l
  top.value = t
  width.value = w
  height.value = h
}

function endPointer() {
  const wasResize = !!resize
  drag = null
  resize = null
  window.removeEventListener('mousemove', onPointerMove)
  window.removeEventListener('mouseup', endPointer)
  if (wasResize) emit('resized')
}

function onKey(e: KeyboardEvent) {
  if (!props.modelValue) return
  if (e.key === 'Escape') {
    if (document.fullscreenElement) {
      void document.exitFullscreen()
      e.preventDefault()
      return
    }
    if (maximized.value) {
      toggleMaximize()
      e.preventDefault()
      return
    }
    close()
  }
  if (e.key === 'F11') {
    e.preventDefault()
    void toggleBrowserFullscreen()
  }
}

watch(
  () => props.modelValue,
  async (open) => {
    if (open) {
      maximized.value = false
      browserFs.value = false
      defaultGeometry()
      window.addEventListener('keydown', onKey)
      document.addEventListener('fullscreenchange', onFullscreenChange)
      await nextTick()
      emit('opened')
      emit('resized')
    } else {
      window.removeEventListener('keydown', onKey)
      document.removeEventListener('fullscreenchange', onFullscreenChange)
      if (document.fullscreenElement) {
        try {
          await document.exitFullscreen()
        } catch {
          /* ignore */
        }
      }
      maximized.value = false
      browserFs.value = false
      contentFs.value = false
      emit('closed')
    }
  },
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  window.removeEventListener('mousemove', onPointerMove)
  window.removeEventListener('mouseup', endPointer)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
</script>

<style scoped>
.aw-root {
  position: fixed;
  inset: 0;
  pointer-events: none;
}
.aw-mask {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.38);
  pointer-events: auto;
}
.aw-window {
  position: absolute;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #c5ced9;
  border-radius: 10px;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.28);
  overflow: hidden;
  pointer-events: auto;
  min-width: 480px;
  min-height: 320px;
}
.aw-window.is-maximized,
.aw-window.is-browser-fs,
.aw-window.is-content-fs {
  border-radius: 0;
  border-width: 0;
  box-shadow: none;
}
.aw-body.is-immersive {
  background: transparent;
}
.aw-titlebar {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 4px 6px 4px 14px;
  background: linear-gradient(180deg, #f8fafc 0%, #eef2f7 100%);
  border-bottom: 1px solid #dbe3ec;
  user-select: none;
  cursor: default;
  flex-shrink: 0;
}
.aw-title {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.aw-title-text {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.aw-controls {
  display: flex;
  align-items: stretch;
  align-self: stretch;
  margin-left: auto;
}
.aw-btn {
  width: 46px;
  min-height: 32px;
  border: 0;
  background: transparent;
  color: #374151;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
}
.aw-btn svg {
  width: 12px;
  height: 12px;
}
.aw-btn:hover {
  background: rgba(15, 23, 42, 0.06);
}
.aw-btn.aw-close:hover {
  background: #e81123;
  color: #fff;
}
.aw-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  overflow: hidden;
}
.aw-resize {
  position: absolute;
  z-index: 5;
}
.aw-resize-n,
.aw-resize-s {
  left: 8px;
  right: 8px;
  height: 6px;
  cursor: ns-resize;
}
.aw-resize-e,
.aw-resize-w {
  top: 8px;
  bottom: 8px;
  width: 6px;
  cursor: ew-resize;
}
.aw-resize-n { top: -2px; }
.aw-resize-s { bottom: -2px; }
.aw-resize-e { right: -2px; }
.aw-resize-w { left: -2px; }
.aw-resize-ne,
.aw-resize-nw,
.aw-resize-se,
.aw-resize-sw {
  width: 12px;
  height: 12px;
}
.aw-resize-ne { top: -2px; right: -2px; cursor: nesw-resize; }
.aw-resize-nw { top: -2px; left: -2px; cursor: nwse-resize; }
.aw-resize-se { bottom: -2px; right: -2px; cursor: nwse-resize; }
.aw-resize-sw { bottom: -2px; left: -2px; cursor: nesw-resize; }
</style>
