<template>
  <div class="page-shell">
    <div class="panel" style="padding:20px">
      <h2 style="margin:0 0 12px">视频播放</h2>
      <p class="meta-line">{{ fileName }}</p>
      <div v-if="status && status !== 'FIN'" style="margin:12px 0">
        转码中：{{ status }}
      </div>
      <video
        v-if="ready"
        ref="videoRef"
        :src="src"
        controls
        style="width:100%;max-height:70vh;background:#000;border-radius:8px"
      />
      <div style="margin-top:16px">
        <el-button @click="$router.push('/')">返回主页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getTranscodeStatus, getVideo } from '@/api/files'
import { useAuthStore } from '@/stores/auth'
import { bindVideoVolume } from '@/utils/mediaVolume'

const route = useRoute()
const auth = useAuthStore()
const fileId = route.params.fileId as string
const fileName = ref('')
const status = ref('')
const ready = ref(false)
const src = ref('')
const videoRef = ref<HTMLVideoElement | null>(null)
let timer: number | undefined

async function poll() {
  const info = await getVideo(fileId)
  fileName.value = info.fileName
  if (!info.needTranscode) {
    ready.value = true
    src.value = `/api/preview/resource/${fileId}${auth.token ? '' : ''}`
    return
  }
  status.value = await getTranscodeStatus(fileId)
  if (status.value === 'FIN') {
    ready.value = true
    src.value = `/api/preview/resource/${fileId}`
    return
  }
  if (status.value === 'ERROR') {
    status.value = '转码失败（请确认已安装 ffmpeg）'
    return
  }
  timer = window.setTimeout(poll, 800)
}

watch(ready, async (v) => {
  if (!v) return
  await nextTick()
  bindVideoVolume(videoRef.value)
})

onMounted(poll)
onUnmounted(() => {
  if (timer) clearTimeout(timer)
})
</script>
