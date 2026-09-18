<template>
  <aside v-show="open && items.length > 1 && !immersive" class="sib" :class="tone">
    <div class="sib-head">
      <span>{{ title }} · {{ items.length }}</span>
      <button type="button" class="sib-toggle" title="收起" @click="open = false">收起</button>
    </div>
    <div ref="bodyRef" class="sib-body">
      <button
        v-for="(item, i) in items"
        :key="item.fileId"
        type="button"
        class="sib-item"
        :class="{ active: item.fileId === activeId }"
        :title="item.fileName"
        @click="emit('select', item)"
      >
        <span class="sib-index">{{ i + 1 }}</span>
        <img v-if="item.thumb" class="sib-thumb" :src="item.thumb" :alt="item.fileName" />
        <span class="sib-name">{{ item.fileName }}</span>
      </button>
    </div>
  </aside>
  <button
    v-if="!open && items.length > 1 && !immersive"
    type="button"
    class="sib-tab"
    :class="tone"
    title="展开列表"
    @click="open = true"
  >
    列表
  </button>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

export interface SiblingItem {
  fileId: string
  fileName: string
  thumb?: string
}

const open = defineModel<boolean>('open', { default: true })

const props = withDefaults(
  defineProps<{
    items: SiblingItem[]
    activeId: string
    immersive?: boolean
    tone?: 'light' | 'dark'
    title?: string
  }>(),
  {
    immersive: false,
    tone: 'light',
    title: '文件列表',
  },
)

const emit = defineEmits<{
  select: [item: SiblingItem]
}>()

const bodyRef = ref<HTMLElement | null>(null)

function scrollActive() {
  nextTick(() => {
    const active = bodyRef.value?.querySelector('.sib-item.active') as HTMLElement | null
    active?.scrollIntoView({ block: 'nearest' })
  })
}

watch(() => [props.activeId, open.value, props.items.length], scrollActive)
</script>

<style scoped>
.sib {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.sib.light {
  background: #f8fafc;
  border-left: 1px solid #e5e7eb;
  color: #374151;
}
.sib.dark {
  background: #111827;
  border-left: 1px solid #1f2937;
  color: #e5e7eb;
}
.sib-head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
}
.sib.light .sib-head {
  color: #374151;
  border-bottom: 1px solid #e5e7eb;
}
.sib.dark .sib-head {
  color: #f3f4f6;
  border-bottom: 1px solid #1f2937;
}
.sib-toggle {
  border: 0;
  background: transparent;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 4px;
}
.sib.light .sib-toggle { color: #6b7280; }
.sib.light .sib-toggle:hover { color: #111827; }
.sib.dark .sib-toggle { color: #9ca3af; }
.sib.dark .sib-toggle:hover { color: #e5e7eb; }
.sib-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 6px 0;
}
.sib-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 9px 12px;
  cursor: pointer;
  font-size: 13px;
  line-height: 1.35;
}
.sib.light .sib-item { color: #4b5563; }
.sib.light .sib-item:hover { background: #eef2f7; color: #111827; }
.sib.light .sib-item.active {
  background: #e6f4f1;
  color: #0f766e;
  font-weight: 600;
}
.sib.dark .sib-item { color: #d1d5db; }
.sib.dark .sib-item:hover { background: #1f2937; color: #fff; }
.sib.dark .sib-item.active {
  background: #134e4a;
  color: #99f6e4;
}
.sib-index {
  flex: 0 0 22px;
  font-size: 12px;
  text-align: right;
}
.sib.light .sib-index { color: #9ca3af; }
.sib.light .sib-item.active .sib-index { color: #0f766e; }
.sib.dark .sib-index { color: #6b7280; }
.sib.dark .sib-item.active .sib-index { color: #5eead4; }
.sib-thumb {
  width: 36px;
  height: 36px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
  background: #111827;
}
.sib-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sib-tab {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  z-index: 3;
  width: 28px;
  padding: 16px 0;
  border: 0;
  border-radius: 8px 0 0 8px;
  font-size: 12px;
  letter-spacing: 2px;
  writing-mode: vertical-rl;
  cursor: pointer;
}
.sib-tab.light {
  background: #f8fafc;
  color: #374151;
  box-shadow: -4px 0 12px rgba(15, 23, 42, 0.12);
}
.sib-tab.light:hover { background: #eef2f7; }
.sib-tab.dark {
  background: #111827;
  color: #e5e7eb;
  box-shadow: -4px 0 12px rgba(0, 0, 0, 0.35);
}
.sib-tab.dark:hover { background: #1f2937; color: #fff; }
@media (max-width: 800px) {
  .sib {
    position: absolute;
    right: 0;
    top: 0;
    bottom: 0;
    z-index: 4;
    box-shadow: -8px 0 24px rgba(0, 0, 0, 0.18);
  }
}
</style>
