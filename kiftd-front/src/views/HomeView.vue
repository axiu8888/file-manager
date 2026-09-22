<template>
  <div class="page-shell">
    <header class="head">
      <div>
        <div class="brand-title">文件管理系统 <span class="brand-sub">KIFT</span></div>
        <p class="meta-line">文件同步时间：{{ view?.publishTime || '-' }}</p>
        <p class="meta-line">OS：{{ osInfo }}</p>
      </div>
      <div class="head-actions">
        <template v-if="auth.isLogin">
          <span class="meta-line">{{ auth.account }}</span>
          <el-button @click="openChangePwd = true">修改密码</el-button>
          <el-button @click="doLogout">退出</el-button>
        </template>
        <template v-else>
          <el-button type="primary" @click="$router.push('/login')">登录</el-button>
          <el-button @click="$router.push('/signup')">注册</el-button>
        </template>
        <el-button @click="refresh">刷新</el-button>
      </div>
    </header>

    <el-alert v-if="notice" :title="notice" type="info" show-icon :closable="false" style="margin-bottom:14px" />

    <div
      class="panel drop-zone"
      :class="{ 'is-dragover': dragOver }"
      style="padding:12px 14px"
      @dragenter="onDragEnter"
      @dragover="onDragOver"
      @dragleave="onDragLeave"
      @drop="onDrop"
    >
      <div v-if="dragOver" class="drop-overlay">
        <div class="drop-overlay-inner">
          <el-icon :size="36"><Upload /></el-icon>
          <p>{{ auth.isLogin ? '松开即可上传文件或文件夹' : '请先登录后再上传' }}</p>
        </div>
      </div>
      <div class="toolbar">
        <div class="path-row">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="p in view?.parentList || []" :key="p.folderId">
              <a href="javascript:;" @click="openFolder(p.folderId)">{{ p.folderName }}</a>
            </el-breadcrumb-item>
          </el-breadcrumb>
          <span v-if="folderStats && !keyword.trim()" class="folder-stats">
            · 共 {{ folderStats.folderCount }} 个文件夹，{{ folderStats.fileCount }} 个文件，占用 {{ folderStats.totalSize }}
          </span>
        </div>
        <div class="toolbar-btns">
          <el-input
            v-model="keyword"
            class="toolbar-search"
            placeholder="输入即搜索文件/文件夹"
            clearable
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>

          <div class="toolbar-group">
            <el-button class="tb-btn tb-folder" :disabled="!auth.isLogin" @click="promptNewFolder">
              <el-icon><FolderAdd /></el-icon>新建文件夹
            </el-button>
            <input
              ref="fileInputRef"
              type="file"
              multiple
              class="hidden-file-input"
              :disabled="!auth.isLogin"
              @change="onFilesPicked"
            />
            <el-button class="tb-btn tb-upload" :disabled="!auth.isLogin" @click="pickFiles">
              <el-icon><Upload /></el-icon>上传文件
            </el-button>
            <el-upload
              :show-file-list="false"
              :http-request="doUploadFolder"
              multiple
              :disabled="!auth.isLogin"
              webkitdirectory
              directory
            >
              <el-button class="tb-btn tb-upload-folder" :disabled="!auth.isLogin">
                <el-icon><FolderOpened /></el-icon>上传文件夹
              </el-button>
            </el-upload>
          </div>

          <div class="toolbar-divider" aria-hidden="true" />

          <div class="toolbar-group">
            <el-button class="tb-btn tb-delete" :disabled="!selected.length" @click="doBatchDelete">
              <el-icon><Delete /></el-icon>删除选中
            </el-button>
            <el-button class="tb-btn tb-zip" :disabled="!selectedFiles.length" @click="doZip">
              <el-icon><Download /></el-icon>打包下载
            </el-button>
          </div>

          <div class="toolbar-divider" aria-hidden="true" />

          <div class="toolbar-group">
            <el-button class="tb-btn tb-cut" :disabled="!selected.length" @click="startMove(false)">
              <el-icon><Scissor /></el-icon>剪切
            </el-button>
            <el-button class="tb-btn tb-copy" :disabled="!selected.length" @click="startMove(true)">
              <el-icon><CopyDocument /></el-icon>复制
            </el-button>
            <el-button class="tb-btn tb-paste" :disabled="!clipboard" @click="doPaste">
              <el-icon><DocumentChecked /></el-icon>粘贴
            </el-button>
          </div>
        </div>
      </div>

      <el-progress v-if="uploadProgress >= 0 && !uploadDialogVisible" :percentage="uploadProgress" style="margin:8px 0" />

      <el-table
        class="file-table"
        :data="rows"
        v-loading="loading"
        :default-sort="{ prop: 'date', order: 'descending' }"
        @selection-change="onSelect"
        @row-dblclick="onDblClick"
        style="width:100%"
      >
        <el-table-column type="selection" width="48" header-align="center" align="center" />
        <el-table-column label="名称" min-width="280" header-align="center" sortable :sort-method="sortByName">
          <template #default="{ row }">
            <span class="name-cell" :class="{ 'is-folder': row.kind === 'folder' }" @click="onOpen(row)">
              <span
                v-if="showThumb(row)"
                class="file-thumb-wrap"
                :class="fileIconClass(row)"
                @mouseenter="onThumbEnter($event, row)"
                @mouseleave="onThumbLeave"
              >
                <img
                  class="file-thumb"
                  :src="thumbSrc(row)"
                  :alt="row.name"
                  loading="lazy"
                  @error="onThumbError(row.id)"
                />
                <span v-if="isVideo(row.name)" class="file-thumb-play" aria-hidden="true" />
              </span>
              <el-icon v-else class="file-icon" :class="fileIconClass(row)">
                <component :is="fileIconName(row)" />
              </el-icon>
              <span class="name-text" :title="row.name">{{ row.name }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="size" label="大小" width="120" header-align="center" align="center" sortable :sort-method="sortBySize">
          <template #default="{ row }">
            <span class="cell-size" :class="{ muted: row.kind === 'folder' }">{{ row.size }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="creator" label="创建者" width="120" header-align="center" align="center">
          <template #default="{ row }">
            <span class="cell-creator">{{ row.creator || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="date" label="创建日期" width="180" header-align="center" align="center" sortable :sort-method="sortByDate">
          <template #default="{ row }">
            <span class="cell-date">{{ row.date || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right" header-align="center" align="center">
          <template #default="{ row }">
            <div class="op-btns">
              <el-button link type="primary" class="op-preview" @click="onOpen(row)">
                {{ row.kind === 'folder' ? '打开' : '预览' }}
              </el-button>
              <el-button v-if="row.kind === 'file'" link type="success" @click="download(row)">下载</el-button>
              <el-button link type="warning" @click="renameRow(row)">重命名</el-button>
              <el-button link type="danger" @click="removeRow(row)">删除</el-button>
              <el-dropdown v-if="row.kind === 'file'" trigger="click">
                <el-button link type="info">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="shareChain(row)">获取直链</el-dropdown-item>
                    <el-dropdown-item @click="shareKey(row)">外链下载</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="openChangePwd" title="修改密码" width="420px">
      <el-form label-position="top">
        <el-form-item label="原密码"><el-input v-model="oldPwd" type="password" show-password /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="newPwd" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="openChangePwd = false">取消</el-button>
        <el-button type="primary" @click="submitChangePwd">确定</el-button>
      </template>
    </el-dialog>

    <AppWindow
      v-model="imgVisible"
      :title="currentPicture?.fileName || '图片预览'"
      :show-mask="false"
      :z-index="imgWinZ"
      @activate="onImgActivate"
      @closed="onImgClosed"
    >
      <template #title>
        <span class="preview-title">{{ currentPicture?.fileName || '图片预览' }}</span>
      </template>
      <template #default="{ immersive }">
        <div class="img-preview-body" :class="{ immersive }">
          <div class="img-with-list">
            <div class="img-viewer">
              <el-button class="img-nav prev" circle :disabled="imgIndex <= 0" @click="imgIndex--">
                <el-icon><ArrowLeft /></el-icon>
              </el-button>
              <div class="img-stage">
                <img v-if="currentPicture" :src="mediaSrc(currentPicture)" :alt="currentPicture.fileName" />
              </div>
              <el-button class="img-nav next" circle :disabled="imgIndex >= pictures.length - 1" @click="imgIndex++">
                <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
            <SiblingPlaylist
              v-model:open="imgListOpen"
              :items="pictureSiblings"
              :active-id="currentPicture?.fileId || ''"
              :immersive="immersive"
              tone="dark"
              title="图片列表"
              @select="onSelectPicture"
              @sort-change="onPictureSortChange"
            />
          </div>
        </div>
      </template>
    </AppWindow>

    <AppWindow
      v-model="audioVisible"
      :title="currentAudio?.fileName || '音频播放'"
      :show-mask="false"
      :initial-width="860"
      :initial-height="520"
      :z-index="audioWinZ"
      @activate="onAudioActivate"
      @closed="onAudioClosed"
    >
      <template #title>
        <span class="preview-title">{{ currentAudio?.fileName || '音频播放' }}</span>
      </template>
      <template #default="{ immersive }">
        <div class="audio-preview-body" :class="{ immersive }">
          <div class="audio-with-list">
            <div class="audio-main">
              <div class="audio-stage">
                <div class="audio-disc" :class="{ playing: audioPlaying }">
                  <div class="audio-disc-ring" aria-hidden="true" />
                  <div class="audio-disc-core" aria-hidden="true">
                    <svg viewBox="0 0 24 24" width="28" height="28" fill="currentColor">
                      <path
                        d="M12 3v10.55A4 4 0 1 0 14 17V7h4V3h-6zm-2 14a2 2 0 1 1 0-.01V17z"
                      />
                    </svg>
                  </div>
                </div>
                <div class="audio-meta">
                  <div class="audio-now" :title="currentAudio?.fileName">
                    {{ audioDisplayName }}
                  </div>
                  <div class="audio-sub">
                    {{ audioTrackLabel }}
                    <span v-if="audioExt" class="audio-ext">{{ audioExt }}</span>
                  </div>
                </div>
              </div>

              <div class="audio-controls">
                <div class="audio-time-row">
                  <span>{{ formatAudioTime(audioCurrent) }}</span>
                  <input
                    class="audio-seek"
                    type="range"
                    min="0"
                    :max="audioDuration || 0"
                    step="0.1"
                    :value="audioCurrent"
                    :disabled="!currentAudio"
                    @input="onAudioSeek"
                  />
                  <span>{{ formatAudioTime(audioDuration) }}</span>
                </div>
                <div class="audio-btn-row">
                  <button
                    type="button"
                    class="audio-btn"
                    title="上一首"
                    :disabled="audioIndex <= 0"
                    @click="audioPrev"
                  >
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
                      <path d="M6 6h2v12H6V6zm3.5 6 8.5 6V6l-8.5 6z" />
                    </svg>
                  </button>
                  <button
                    type="button"
                    class="audio-btn audio-btn-main"
                    :title="audioPlaying ? '暂停' : '播放'"
                    :disabled="!currentAudio"
                    @click="toggleAudioPlay"
                  >
                    <svg v-if="!audioPlaying" viewBox="0 0 24 24" width="26" height="26" fill="currentColor">
                      <path d="M8 5v14l11-7L8 5z" />
                    </svg>
                    <svg v-else viewBox="0 0 24 24" width="26" height="26" fill="currentColor">
                      <path d="M6 5h4v14H6V5zm8 0h4v14h-4V5z" />
                    </svg>
                  </button>
                  <button
                    type="button"
                    class="audio-btn"
                    title="下一首"
                    :disabled="audioIndex >= audios.length - 1"
                    @click="audioNext"
                  >
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
                      <path d="M16 6h2v12h-2V6zM6 18l8.5-6L6 6v12z" />
                    </svg>
                  </button>
                </div>
                <div class="audio-vol-row">
                  <button
                    type="button"
                    class="audio-btn audio-btn-vol"
                    :title="audioMuted || audioVolume <= 0 ? '取消静音' : '静音'"
                    :disabled="!currentAudio"
                    @click="toggleAudioMute"
                  >
                    <svg
                      v-if="audioMuted || audioVolume <= 0"
                      viewBox="0 0 24 24"
                      width="18"
                      height="18"
                      fill="currentColor"
                    >
                      <path
                        d="M16.5 12a4.5 4.5 0 0 0-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51A8.8 8.8 0 0 0 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3 3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06a8.99 8.99 0 0 0 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4 9.91 6.09 12 8.18V4z"
                      />
                    </svg>
                    <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
                      <path
                        d="M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"
                      />
                    </svg>
                  </button>
                  <input
                    class="audio-seek audio-vol"
                    type="range"
                    min="0"
                    max="1"
                    step="0.01"
                    :value="audioMuted ? 0 : audioVolume"
                    :disabled="!currentAudio"
                    :title="`音量 ${Math.round((audioMuted ? 0 : audioVolume) * 100)}%`"
                    @input="onAudioVolumeInput"
                  />
                  <span class="audio-vol-label">{{ Math.round((audioMuted ? 0 : audioVolume) * 100) }}%</span>
                </div>
              </div>

              <audio
                v-if="currentAudio"
                :key="currentAudio.fileId"
                ref="audioRef"
                class="audio-hidden"
                :src="mediaSrc(currentAudio)"
                preload="metadata"
                @timeupdate="onAudioTimeUpdate"
                @loadedmetadata="onAudioMeta"
                @play="onAudioPlay"
                @pause="onAudioPause"
                @ended="onAudioEnded"
                @volumechange="syncAudioVolumeUi"
              />
            </div>
            <SiblingPlaylist
              v-model:open="audioListOpen"
              :items="audioSiblings"
              :active-id="currentAudio?.fileId || ''"
              :immersive="immersive"
              tone="dark"
              title="播放列表"
              @select="onSelectAudio"
              @sort-change="onAudioSortChange"
            />
          </div>
        </div>
      </template>
    </AppWindow>

    <PreviewDialog
      v-for="(p, idx) in previewSessions"
      :key="p.id"
      :model-value="p.visible"
      :title="p.title"
      :file-id="p.fileId"
      :type="p.type"
      :kind="p.kind"
      :z-index="p.zIndex"
      :activate-key="p.activateKey"
      :cascade="idx"
      @update:model-value="(v) => onPreviewVisible(p.id, v)"
      @activate="raisePreview(p.id)"
    />

    <el-dialog v-model="linkVisible" title="分享链接" width="560px">
      <el-input v-model="linkText" type="textarea" :rows="3" readonly />
      <template #footer>
        <el-button type="primary" @click="copyLink">复制</el-button>
      </template>
    </el-dialog>

    <Teleport to="body">
      <div
        v-if="thumbHover"
        class="thumb-pop"
        :style="{ left: `${thumbHover.left}px`, top: `${thumbHover.top}px` }"
      >
        <img class="thumb-pop-img" :src="thumbHover.src" :alt="thumbHover.name" />
        <span v-if="thumbHover.video" class="thumb-pop-badge">视频</span>
        <div class="thumb-pop-name" :title="thumbHover.name">{{ thumbHover.name }}</div>
      </div>
    </Teleport>

    <el-dialog
      v-model="uploadDialogVisible"
      title="正在上传"
      width="480px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      append-to-body
      align-center
      class="upload-progress-dialog"
    >
      <div class="upload-dialog-body">
        <div class="upload-dialog-meta">
          <span>{{ uploadState.done }} / {{ uploadState.total }}</span>
          <span>{{ uploadProgress >= 0 ? uploadProgress : 0 }}%</span>
        </div>
        <el-progress
          :percentage="uploadProgress >= 0 ? uploadProgress : 0"
          :stroke-width="14"
          striped
          striped-flow
        />
        <div class="upload-dialog-current" :title="uploadState.current">
          <span class="upload-dialog-label">当前</span>
          <span class="upload-dialog-file">{{ uploadState.current || '准备中…' }}</span>
        </div>
        <div v-if="uploadState.detail" class="upload-dialog-detail">{{ uploadState.detail }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import PreviewDialog, { type PreviewType } from '@/components/PreviewDialog.vue'
import AppWindow from '@/components/AppWindow.vue'
import SiblingPlaylist, { type SiblingItem } from '@/components/SiblingPlaylist.vue'
import {
  batchDelete,
  checkUpload,
  countFolder,
  createChain,
  createDownloadKey,
  createFolder,
  deleteFile,
  deleteFolder,
  downloadFile,
  getAudios,
  getFolderView,
  getNotice,
  getOs,
  getPictures,
  getRemaining,
  moveItems,
  renameFile,
  renameFolder,
  searchAll,
  uploadFile,
  zipDownload,
  type FileNode,
  type Folder,
  type FolderView,
} from '@/api/files'
import { changePassword, fetchMe } from '@/api/auth'
import { chainShareUrl, downloadKeyShareUrl, mediaSrc, previewThumbUrl } from '@/api/urls'
import { useAuthStore } from '@/stores/auth'
import { bindVideoVolume, getCachedMuted, getCachedVideoVolume, setCachedVideoVolume } from '@/utils/mediaVolume'
import { compareFileMeta, loadSiblingSort, sortSiblingItems, type SiblingSortState } from '@/utils/siblingSort'
import { isTopmostWindow } from '@/utils/windowStack'
import {
  Search,
  FolderAdd,
  Upload,
  FolderOpened,
  Delete,
  Download,
  Scissor,
  CopyDocument,
  DocumentChecked,
} from '@element-plus/icons-vue'

interface Row {
  id: string
  kind: 'folder' | 'file'
  name: string
  size: string
  sizeBytes: number
  creator: string
  date: string
  raw: Folder | FileNode
}

const auth = useAuthStore()
const router = useRouter()
const view = ref<FolderView | null>(null)
const loading = ref(false)
const osInfo = ref('加载中...')
const notice = ref('')
const keyword = ref('')
const folderStats = ref<{ folderCount: number; fileCount: number; totalSize: string } | null>(null)
const selected = ref<Row[]>([])
const uploadProgress = ref(-1)
const uploadDialogVisible = ref(false)
const uploadState = ref({
  total: 0,
  done: 0,
  current: '',
  detail: '',
})
const dragOver = ref(false)
let dragDepth = 0
/** 防止拖拽/连点导致并发上传（并发时 Tomcat swallow 易连环报 size exceeded） */
let uploadBusy = false

function beginUploadProgress(total: number, detail = '') {
  uploadState.value = { total, done: 0, current: '', detail }
  uploadProgress.value = 0
  uploadDialogVisible.value = true
}

function setUploadCurrent(name: string, done: number, detail = '') {
  uploadState.value = {
    ...uploadState.value,
    current: name,
    done,
    detail: detail || uploadState.value.detail,
  }
}

function endUploadProgress() {
  uploadProgress.value = -1
  uploadDialogVisible.value = false
  uploadState.value = { total: 0, done: 0, current: '', detail: '' }
}
const openChangePwd = ref(false)
const oldPwd = ref('')
const newPwd = ref('')
const imgVisible = ref(false)
const imgIndex = ref(0)
const imgListOpen = ref(true)
const imgWinZ = ref(3000)
const pictures = ref<
  { fileId: string; fileName: string; url: string; fileCreationDate?: string; fileSize?: string }[]
>([])

const currentPicture = computed(() => pictures.value[imgIndex.value] || null)
const pictureSiblings = computed<SiblingItem[]>(() =>
  pictures.value.map((p) => ({
    fileId: p.fileId,
    fileName: p.fileName,
    fileCreationDate: p.fileCreationDate,
    fileSize: p.fileSize,
    thumb: previewThumbUrl(p.fileId, auth.token),
  })),
)

function onSelectPicture(item: SiblingItem) {
  const i = pictures.value.findIndex((p) => p.fileId === item.fileId)
  if (i >= 0) imgIndex.value = i
}

function onPictureSortChange(state: SiblingSortState) {
  const id = currentPicture.value?.fileId
  pictures.value = sortSiblingItems(pictures.value, state)
  if (id) {
    const i = pictures.value.findIndex((p) => p.fileId === id)
    if (i >= 0) imgIndex.value = i
  }
}

function onImgActivate() {
  imgWinZ.value = ++previewZ
}

function onImgClosed() {
  imgIndex.value = 0
  pictures.value = []
  imgListOpen.value = true
}
const audioVisible = ref(false)
const audioIndex = ref(0)
const audioListOpen = ref(true)
const audioWinZ = ref(3000)
const audioRef = ref<HTMLAudioElement | null>(null)
const audioPlaying = ref(false)
const audioCurrent = ref(0)
const audioDuration = ref(0)
const audioVolume = ref(getCachedVideoVolume())
const audioMuted = ref(getCachedMuted())
const audios = ref<
  { fileId: string; fileName: string; url: string; fileCreationDate?: string; fileSize?: string }[]
>([])
const currentAudio = computed(() => audios.value[audioIndex.value] || null)
const audioSiblings = computed<SiblingItem[]>(() =>
  audios.value.map((a) => ({
    fileId: a.fileId,
    fileName: a.fileName,
    fileCreationDate: a.fileCreationDate,
    fileSize: a.fileSize,
  })),
)
const audioDisplayName = computed(() => {
  const name = currentAudio.value?.fileName || '未选择音频'
  const i = name.lastIndexOf('.')
  return i > 0 ? name.slice(0, i) : name
})
const audioExt = computed(() => {
  const name = currentAudio.value?.fileName || ''
  const i = name.lastIndexOf('.')
  return i >= 0 ? name.slice(i + 1).toUpperCase() : ''
})
const audioTrackLabel = computed(() => {
  if (!audios.value.length) return '播放列表为空'
  return `第 ${audioIndex.value + 1} / ${audios.value.length} 首`
})

function formatAudioTime(sec: number) {
  if (!Number.isFinite(sec) || sec < 0) return '0:00'
  const s = Math.floor(sec)
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${m}:${r.toString().padStart(2, '0')}`
}

function onSelectAudio(item: SiblingItem) {
  const i = audios.value.findIndex((a) => a.fileId === item.fileId)
  if (i >= 0) audioIndex.value = i
}

function onAudioSortChange(state: SiblingSortState) {
  const id = currentAudio.value?.fileId
  audios.value = sortSiblingItems(audios.value, state)
  if (id) {
    const i = audios.value.findIndex((a) => a.fileId === id)
    if (i >= 0) audioIndex.value = i
  }
}

function onAudioTimeUpdate() {
  const el = audioRef.value
  if (!el) return
  audioCurrent.value = el.currentTime || 0
}

function onAudioMeta() {
  const el = audioRef.value
  if (!el) return
  audioDuration.value = Number.isFinite(el.duration) ? el.duration : 0
  audioCurrent.value = el.currentTime || 0
}

function onAudioPlay(e: Event) {
  // 忽略旧 audio 节点卸载时的滞后事件
  if (e.target !== audioRef.value) return
  audioPlaying.value = true
}

function onAudioPause(e: Event) {
  if (e.target !== audioRef.value) return
  audioPlaying.value = false
}

function onAudioSeek(e: Event) {
  const el = audioRef.value
  if (!el) return
  const v = Number((e.target as HTMLInputElement).value)
  if (!Number.isFinite(v)) return
  el.currentTime = v
  audioCurrent.value = v
}

function syncAudioVolumeUi() {
  const el = audioRef.value
  if (!el) {
    audioVolume.value = getCachedVideoVolume()
    audioMuted.value = getCachedMuted()
    return
  }
  audioVolume.value = el.volume
  audioMuted.value = el.muted
}

function onAudioVolumeInput(e: Event) {
  const el = audioRef.value
  const v = Number((e.target as HTMLInputElement).value)
  if (!Number.isFinite(v)) return
  const next = Math.min(1, Math.max(0, v))
  audioVolume.value = next
  audioMuted.value = next <= 0
  if (el) {
    el.volume = next
    el.muted = next <= 0
  }
  setCachedVideoVolume(next, next <= 0)
}

function toggleAudioMute() {
  const el = audioRef.value
  if (!el) return
  if (el.muted || el.volume <= 0) {
    const restore = audioVolume.value > 0 ? audioVolume.value : getCachedVideoVolume() || 0.5
    el.muted = false
    el.volume = restore
    audioMuted.value = false
    audioVolume.value = restore
    setCachedVideoVolume(restore, false)
  } else {
    el.muted = true
    audioMuted.value = true
    setCachedVideoVolume(el.volume, true)
  }
}

function seekAudioBy(deltaSec: number) {
  const el = audioRef.value
  if (!el) return
  const duration = Number.isFinite(el.duration) ? el.duration : NaN
  let next = (el.currentTime || 0) + deltaSec
  if (Number.isFinite(duration) && duration > 0) {
    next = Math.min(Math.max(0, next), Math.max(0, duration - 0.05))
  } else {
    next = Math.max(0, next)
  }
  try {
    el.currentTime = next
    audioCurrent.value = next
  } catch {
    /* ignore */
  }
}

function changeAudioVolume(delta: number) {
  const el = audioRef.value
  if (!el) return
  if (el.muted && delta > 0) {
    el.muted = false
    if (el.volume <= 0) el.volume = Math.min(1, delta)
    syncAudioVolumeUi()
    return
  }
  const next = Math.min(1, Math.max(0, el.volume + delta))
  el.volume = next
  el.muted = next === 0
  syncAudioVolumeUi()
}

function onAudioActivate() {
  audioWinZ.value = ++previewZ
}

function onMediaHotkey(e: KeyboardEvent) {
  const tag = (e.target as HTMLElement | null)?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return

  if (audioVisible.value && isTopmostWindow(audioWinZ.value)) {
    if (e.key === 'ArrowLeft') {
      e.preventDefault()
      seekAudioBy(e.shiftKey ? -30 : -5)
    } else if (e.key === 'ArrowRight') {
      e.preventDefault()
      seekAudioBy(e.shiftKey ? 30 : 5)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      changeAudioVolume(e.shiftKey ? 0.1 : 0.05)
    } else if (e.key === 'ArrowDown') {
      e.preventDefault()
      changeAudioVolume(e.shiftKey ? -0.1 : -0.05)
    } else if (e.key === ' ' || e.key === 'Spacebar') {
      e.preventDefault()
      void toggleAudioPlay()
    }
    return
  }

  if (imgVisible.value && isTopmostWindow(imgWinZ.value)) {
    if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
      e.preventDefault()
      if (imgIndex.value > 0) imgIndex.value -= 1
    } else if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
      e.preventDefault()
      if (imgIndex.value < pictures.value.length - 1) imgIndex.value += 1
    }
  }
}

async function toggleAudioPlay() {
  const el = audioRef.value
  if (!el) return
  if (el.paused) {
    try {
      await el.play()
      audioPlaying.value = true
    } catch {
      /* ignore */
    }
  } else {
    el.pause()
    audioPlaying.value = false
  }
}

function audioPrev() {
  if (audioIndex.value > 0) audioIndex.value -= 1
}

function audioNext() {
  if (audioIndex.value < audios.value.length - 1) audioIndex.value += 1
}

function onAudioEnded() {
  if (audioIndex.value < audios.value.length - 1) {
    audioIndex.value += 1
    return
  }
  audioPlaying.value = false
  audioCurrent.value = 0
}

function stopAudio() {
  const el = audioRef.value
  if (!el) return
  try {
    el.pause()
    el.removeAttribute('src')
    el.load()
  } catch {
    /* ignore */
  }
  audioPlaying.value = false
  audioCurrent.value = 0
  audioDuration.value = 0
}

watch(
  [currentAudio, audioVisible],
  async () => {
    if (!audioVisible.value || !currentAudio.value) return
    audioCurrent.value = 0
    audioDuration.value = 0
    await nextTick()
    const el = audioRef.value
    if (!el) return
  bindVideoVolume(el)
  syncAudioVolumeUi()
  try {
    await el.play()
    // 显式同步按钮状态，避免旧节点 pause 事件覆盖
    if (audioRef.value === el && !el.paused) {
      audioPlaying.value = true
    }
  } catch {
    if (audioRef.value === el) audioPlaying.value = false
  }
  },
  { flush: 'post' },
)

function onAudioClosed() {
  stopAudio()
  audios.value = []
  audioIndex.value = 0
  audioListOpen.value = true
}
const linkVisible = ref(false)
const linkText = ref('')
const clipboard = ref<{ copy: boolean; fileIds: string[]; folderIds: string[] } | null>(null)

interface PreviewSession {
  id: string
  fileId: string
  title: string
  type: PreviewType
  kind: 'pdf' | 'txt' | 'office'
  visible: boolean
  zIndex: number
  activateKey: number
}

const previewSessions = ref<PreviewSession[]>([])
let previewSeq = 0
let previewZ = 3200
let previewActivateSeq = 0
const MAX_PREVIEW_WINDOWS = 8

function onPreviewVisible(sessionId: string, open: boolean) {
  const idx = previewSessions.value.findIndex((p) => p.id === sessionId)
  if (idx < 0) return
  if (open) {
    previewSessions.value[idx].visible = true
    return
  }
  // 先置 false 触发子组件清理，再移除会话，避免视频仍在后台播放
  previewSessions.value[idx].visible = false
  nextTick(() => {
    const i = previewSessions.value.findIndex((p) => p.id === sessionId)
    if (i >= 0) previewSessions.value.splice(i, 1)
  })
}

function raisePreview(sessionId: string) {
  const s = previewSessions.value.find((p) => p.id === sessionId)
  if (!s || !s.visible) return
  if (s.zIndex >= previewZ) return
  s.zIndex = ++previewZ
}

function openFilePreview(row: Row, type: PreviewType, kind: 'pdf' | 'txt' | 'office' = 'pdf') {
  const existing = previewSessions.value.find((p) => p.fileId === row.id)
  if (existing) {
    existing.visible = true
    existing.zIndex = ++previewZ
    existing.activateKey = ++previewActivateSeq
    ElMessage.info('该文件已在预览中，已切换到对应窗口')
    return
  }
  const openCount = previewSessions.value.filter((p) => p.visible).length
  if (openCount >= MAX_PREVIEW_WINDOWS) {
    ElMessage.warning(`最多同时打开 ${MAX_PREVIEW_WINDOWS} 个预览窗口，请先关闭一些`)
    return
  }
  previewSessions.value.push({
    id: `pv-${++previewSeq}`,
    fileId: row.id,
    title: row.name,
    type,
    kind,
    visible: true,
    zIndex: ++previewZ,
    activateKey: ++previewActivateSeq,
  })
}

const rows = computed<Row[]>(() => {
  if (!view.value) return []
  const folders = view.value.folderList.map((f) => ({
    id: f.folderId,
    kind: 'folder' as const,
    name: f.folderName,
    size: '--',
    sizeBytes: -1,
    creator: f.folderCreator,
    date: f.folderCreationDate,
    raw: f,
  }))
  const files = view.value.fileList.map((f) => ({
    id: f.fileId,
    kind: 'file' as const,
    name: f.fileName,
    size: formatSize(f.fileSize),
    sizeBytes: Number(f.fileSize) || 0,
    creator: f.fileCreator,
    date: f.fileCreationDate,
    raw: f,
  }))
  // 与播放列表默认规则一致：时间倒序，同时间名称自然序
  return [...folders, ...files].sort((a, b) =>
    compareFileMeta(
      { fileName: a.name, fileCreationDate: a.date, fileSize: a.sizeBytes },
      { fileName: b.name, fileCreationDate: b.date, fileSize: b.sizeBytes },
      { by: 'date', order: 'desc' },
    ),
  )
})

function sortByName(a: Row, b: Row) {
  return compareFileMeta(
    { fileName: a.name },
    { fileName: b.name },
    { by: 'name', order: 'asc' },
  )
}

function sortBySize(a: Row, b: Row) {
  return compareFileMeta(
    { fileName: a.name, fileSize: a.sizeBytes },
    { fileName: b.name, fileSize: b.sizeBytes },
    { by: 'size', order: 'asc' },
  )
}

function sortByDate(a: Row, b: Row) {
  // el-table 会按 ascending/descending 再乘系数，这里始终返回正序比较结果
  return compareFileMeta(
    { fileName: a.name, fileCreationDate: a.date },
    { fileName: b.name, fileCreationDate: b.date },
    { by: 'date', order: 'asc' },
  )
}

const selectedFiles = computed(() => selected.value.filter((r) => r.kind === 'file'))

function formatSize(s: string) {
  const n = Number(s)
  if (Number.isNaN(n)) return s
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(2) + ' KB'
  if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(2) + ' MB'
  return (n / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

function onSelect(val: Row[]) {
  selected.value = val
}

async function refresh(fid?: string) {
  loading.value = true
  try {
    const remembered = localStorage.getItem('folder_id') || 'root'
    let id = fid || view.value?.folder.folderId || remembered
    let v
    try {
      v = await getFolderView(id)
    } catch (e: any) {
      // 记忆路径失效（换库/删除后）时回到根目录，避免一刷新就报错
      if (id !== 'root' && (e.message === '文件夹不存在' || e.message === 'NOT_FOUND' || e.message === 'notAccess')) {
        localStorage.removeItem('folder_id')
        id = 'root'
        v = await getFolderView('root')
      } else {
        throw e
      }
    }
    view.value = v
    localStorage.setItem('folder_id', v.folder.folderId)
    // 同步服务端识别到的账号权限；不因 account 为空就强制登出（浏览接口本身允许匿名）
    if (v.account) {
      auth.setSession(auth.token || localStorage.getItem('kiftd_token') || '', v.account, v.authList || auth.auth)
    }
    thumbFailed.value = new Set()
    if (v.foldersOffset > v.selectStep || v.filesOffset > v.selectStep) {
      const rem = await getRemaining(v.folder.folderId, Math.min(v.selectStep, v.folderList.length), Math.min(v.selectStep, v.fileList.length))
      view.value.folderList = [...v.folderList, ...rem.folderList.filter((f) => !v.folderList.some((x) => x.folderId === f.folderId))]
      view.value.fileList = [...v.fileList, ...rem.fileList.filter((f) => !v.fileList.some((x) => x.fileId === f.fileId))]
    }
    try {
      folderStats.value = await countFolder(v.folder.folderId)
    } catch {
      folderStats.value = null
    }
  } catch (e: any) {
    if (e.message === 'mustLogin') {
      router.push('/login')
      return
    }
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openFolder(id: string) {
  if (searchTimer) clearTimeout(searchTimer)
  searchSeq++
  if (keyword.value) {
    suppressKeywordWatch = true
    keyword.value = ''
    suppressKeywordWatch = false
  }
  refresh(id)
}

function onOpen(row: Row) {
  if (row.kind === 'folder') openFolder(row.id)
  else preview(row)
}

function onDblClick(row: Row) {
  onOpen(row)
}

async function promptNewFolder() {
  const { value } = await ElMessageBox.prompt('请输入文件夹名称', '新建文件夹')
  await createFolder(view.value!.folder.folderId, value, view.value!.folder.folderConstraint)
  ElMessage.success('已创建')
  refresh()
}

const fileInputRef = ref<HTMLInputElement | null>(null)

function pickFiles() {
  if (!auth.isLogin) return
  const input = fileInputRef.value
  if (!input) return
  input.value = ''
  input.click()
}

async function onFilesPicked(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length || !view.value) return
  try {
    await ensureUploadSession()
    await uploadFilesBatch(files)
  } catch (err: any) {
    ElMessage.error(err?.message || '上传失败')
  }
}

function isFileDrag(e: DragEvent) {
  return Array.from(e.dataTransfer?.types || []).includes('Files')
}

function onDragEnter(e: DragEvent) {
  if (!isFileDrag(e)) return
  e.preventDefault()
  dragDepth++
  dragOver.value = true
}

function onDragOver(e: DragEvent) {
  if (!isFileDrag(e)) return
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = auth.isLogin ? 'copy' : 'none'
}

function onDragLeave(e: DragEvent) {
  if (!isFileDrag(e)) return
  e.preventDefault()
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) dragOver.value = false
}

async function ensureUploadSession() {
  if (!auth.isLogin) {
    throw new Error('请先登录后再上传')
  }
  try {
    const me = await fetchMe()
    if (me?.account) {
      auth.setSession(auth.token, me.account, me.auth || auth.auth)
    }
  } catch (e: any) {
    throw new Error(e?.message || '登录状态无效，请重新登录后再上传')
  }
  if (!auth.hasAuth('UPLOAD_FILES')) {
    throw new Error('当前账号没有上传权限')
  }
}

async function onDrop(e: DragEvent) {
  e.preventDefault()
  dragDepth = 0
  dragOver.value = false
  if (!auth.isLogin) {
    ElMessage.warning('请先登录后再上传')
    return
  }
  if (!view.value || !e.dataTransfer) return
  if (uploadBusy) {
    ElMessage.warning('正在上传中，请稍候再拖入')
    return
  }
  // 必须先同步取出拖拽文件：任何 await 之后 DataTransfer 会被浏览器清空
  let files: Array<File & { webkitRelativePath?: string }> = []
  try {
    files = await collectDroppedFiles(e.dataTransfer)
  } catch (err: any) {
    ElMessage.error(err?.message || '读取拖拽文件失败')
    return
  }
  if (!files.length) {
    ElMessage.info('未识别到可上传的文件（空文件夹无法上传）')
    return
  }
  try {
    await ensureUploadSession()
    await uploadDroppedFiles(files)
  } catch (err: any) {
    ElMessage.error(err?.message || '拖拽上传失败')
  }
}

/** Traverse FileSystemEntry tree (supports folder drop). */
async function collectDroppedFiles(dt: DataTransfer): Promise<Array<File & { webkitRelativePath?: string }>> {
  const out: Array<File & { webkitRelativePath?: string }> = []
  // 先同步快照 items/files，避免后续 await 后 DataTransfer 被清空
  const itemList = dt.items ? Array.from(dt.items) : []
  const flatFiles = Array.from(dt.files || []) as Array<File & { webkitRelativePath?: string }>

  if (itemList.length) {
    const entries: FileSystemEntry[] = []
    for (const item of itemList) {
      if (item.kind !== 'file') continue
      const entry = item.webkitGetAsEntry?.()
      if (entry) entries.push(entry)
    }
    if (entries.length) {
      for (const entry of entries) {
        await walkFsEntry(entry, '', out)
      }
      if (out.length) return out
    }
  }
  // Fallback: flat file list（不支持目录结构）
  return flatFiles
}

function readDirectoryEntries(reader: FileSystemDirectoryReader): Promise<FileSystemEntry[]> {
  return new Promise((resolve, reject) => {
    const all: FileSystemEntry[] = []
    const readBatch = () => {
      reader.readEntries((batch) => {
        if (!batch.length) {
          resolve(all)
          return
        }
        all.push(...batch)
        readBatch()
      }, reject)
    }
    readBatch()
  })
}

async function walkFsEntry(
  entry: FileSystemEntry,
  parentPath: string,
  out: Array<File & { webkitRelativePath?: string }>,
) {
  if (entry.isFile) {
    const file = await new Promise<File>((resolve, reject) => {
      ;(entry as FileSystemFileEntry).file(resolve, reject)
    })
    const rel = parentPath ? `${parentPath}/${file.name}` : file.name
    Object.defineProperty(file, 'webkitRelativePath', { value: rel, configurable: true })
    out.push(file as File & { webkitRelativePath?: string })
    return
  }
  if (entry.isDirectory) {
    const dirPath = parentPath ? `${parentPath}/${entry.name}` : entry.name
    const reader = (entry as FileSystemDirectoryEntry).createReader()
    const children = await readDirectoryEntries(reader)
    for (const child of children) {
      await walkFsEntry(child, dirPath, out)
    }
  }
}

function normalizeRelPath(rel: string) {
  return (rel || '').replace(/\\/g, '/').replace(/^\/+/, '')
}

function fileRelPath(file: File & { webkitRelativePath?: string }) {
  return normalizeRelPath(file.webkitRelativePath || file.name)
}

function hasNestedPath(files: Array<File & { webkitRelativePath?: string }>) {
  return files.some((f) => fileRelPath(f).includes('/'))
}

/** Shared across concurrent directory-upload requests to avoid duplicate createFolder races. */
const folderPathCache = new Map<string, string>()

async function ensureFolderByRelPath(relDir: string): Promise<string> {
  const rootId = view.value!.folder.folderId
  if (!relDir) return rootId
  const parts = relDir.split('/').filter(Boolean)
  let parentId = rootId
  let pathKey = rootId
  for (const name of parts) {
    pathKey = `${pathKey}/${name}`
    const cached = folderPathCache.get(pathKey)
    if (cached) {
      parentId = cached
      continue
    }
    const created = await createFolder(parentId, name, view.value!.folder.folderConstraint, true)
    parentId = created.folderId
    folderPathCache.set(pathKey, parentId)
  }
  return parentId
}

async function uploadDroppedFiles(files: Array<File & { webkitRelativePath?: string }>) {
  if (!hasNestedPath(files)) {
    await uploadFilesBatch(files)
    return
  }
  await uploadTreeFiles(files)
}

/** 带相对路径的批量上传（拖文件夹 / 上传文件夹按钮） */
async function uploadTreeFiles(files: Array<File & { webkitRelativePath?: string }>) {
  if (!files.length) return
  if (uploadBusy) {
    ElMessage.warning('正在上传中，请稍候')
    return
  }
  await ensureUploadSession()
  uploadBusy = true
  beginUploadProgress(files.length, '正在创建目录…')
  let ok = 0
  let skip = 0
  let fail = 0
  const errors: string[] = []
  try {
    // 1) 先建好所有目录
    const dirSet = new Set<string>()
    for (const f of files) {
      const rel = fileRelPath(f)
      const parts = rel.split('/')
      if (parts.length > 1) {
        dirSet.add(parts.slice(0, -1).join('/'))
      }
    }
    const dirs = [...dirSet].sort((a, b) => a.localeCompare(b))
    for (let di = 0; di < dirs.length; di++) {
      setUploadCurrent(dirs[di], 0, `正在创建目录… (${di + 1}/${dirs.length})`)
      await ensureFolderByRelPath(dirs[di])
    }

    // 2) 按目标文件夹分组
    setUploadCurrent('', 0, '正在检查同名文件…')
    const groups = new Map<string, Array<File & { webkitRelativePath?: string }>>()
    for (const f of files) {
      const rel = fileRelPath(f)
      const parts = rel.split('/')
      const dir = parts.length > 1 ? parts.slice(0, -1).join('/') : ''
      const parentId = await ensureFolderByRelPath(dir)
      const list = groups.get(parentId) || []
      list.push(f)
      groups.set(parentId, list)
    }

    // 3) 预先汇总同名，只询问一次
    let overwriteAll = false
    let asked = false
    let totalOverlap = 0
    const overlapByFolder = new Map<string, Set<string>>()
    for (const [parentId, list] of groups) {
      const names = list.map((f) => {
        const parts = fileRelPath(f).split('/')
        return parts[parts.length - 1]
      })
      const check = await checkUpload(parentId, names)
      const set = new Set(check.overlaps || [])
      overlapByFolder.set(parentId, set)
      totalOverlap += set.size
    }
    if (totalOverlap > 0) {
      asked = true
      uploadDialogVisible.value = false
      overwriteAll = await ElMessageBox.confirm(
        `有 ${totalOverlap} 个同名文件，是否全部覆盖？`,
        '提示',
        { confirmButtonText: '覆盖', cancelButtonText: '跳过同名', closeOnClickModal: false },
      )
        .then(() => true)
        .catch(() => false)
      uploadDialogVisible.value = true
    }

    // 4) 逐组上传；单文件失败不中断整批
    let done = 0
    const total = files.length
    setUploadCurrent('', 0, '正在上传文件…')
    for (const [parentId, list] of groups) {
      const names = list.map((f) => {
        const parts = fileRelPath(f).split('/')
        return parts[parts.length - 1]
      })
      const check = await checkUpload(parentId, names)
      const overlaps = overlapByFolder.get(parentId) || new Set(check.overlaps || [])
      for (let i = 0; i < list.length; i++) {
        const file = list[i]
        const rel = fileRelPath(file)
        const parts = rel.split('/')
        const name = parts[parts.length - 1]
        const overlap = overlaps.has(name)
        if (overlap && asked && !overwriteAll) {
          skip++
          done++
          setUploadCurrent(rel, done, `已跳过同名 · 成功 ${ok} / 失败 ${fail}`)
          uploadProgress.value = Math.round((done / total) * 100)
          continue
        }
        setUploadCurrent(rel, done, `上传中 · 成功 ${ok} / 失败 ${fail}`)
        try {
          await uploadFile(parentId, file, check.uploadKey, overlap && overwriteAll, (p) => {
            const base = (done / total) * 100
            uploadProgress.value = Math.round(base + p / total)
          })
          ok++
        } catch (e: any) {
          fail++
          const msg = e?.message || '上传失败'
          if (errors.length < 3) errors.push(`${name}: ${msg}`)
        }
        done++
        setUploadCurrent(rel, done, `上传中 · 成功 ${ok} / 失败 ${fail}`)
        uploadProgress.value = Math.round((done / total) * 100)
      }
    }

    if (ok && (skip || fail)) {
      ElMessage.success(`已上传 ${ok} 个` + (skip ? `，跳过 ${skip} 个` : '') + (fail ? `，失败 ${fail} 个` : ''))
    } else if (ok) {
      ElMessage.success(ok === 1 ? '上传成功' : `已上传 ${ok} 个文件`)
    } else if (skip && !fail) {
      ElMessage.info('已跳过全部同名文件')
    } else if (fail) {
      ElMessage.error(errors[0] || '上传失败')
    }
    if (ok > 0) refresh()
  } catch (e: any) {
    ElMessage.error(e.message || '上传失败')
    if (ok > 0) refresh()
  } finally {
    endUploadProgress()
    uploadBusy = false
  }
}

async function uploadFilesBatch(files: File[]) {
  if (uploadBusy) {
    ElMessage.warning('正在上传中，请稍候')
    return
  }
  await ensureUploadSession()
  uploadBusy = true
  const folderId = view.value!.folder.folderId
  beginUploadProgress(files.length, '正在检查同名文件…')
  let ok = 0
  let skip = 0
  let fail = 0
  const errors: string[] = []
  try {
    const check = await checkUpload(
      folderId,
      files.map((f) => f.name),
    )
    let overwriteAll = false
    let asked = false
    const overlaps = new Set(check.overlaps || [])
    if (overlaps.size > 0) {
      asked = true
      uploadDialogVisible.value = false
      overwriteAll = await ElMessageBox.confirm(
        `有 ${overlaps.size} 个同名文件，是否全部覆盖？`,
        '提示',
        { confirmButtonText: '覆盖', cancelButtonText: '跳过同名', closeOnClickModal: false },
      )
        .then(() => true)
        .catch(() => false)
      uploadDialogVisible.value = true
    }
    setUploadCurrent('', 0, '正在上传文件…')
    for (let i = 0; i < files.length; i++) {
      const file = files[i]
      const overlap = overlaps.has(file.name)
      if (overlap && asked && !overwriteAll) {
        skip++
        setUploadCurrent(file.name, i + 1, `已跳过同名 · 成功 ${ok} / 失败 ${fail}`)
        uploadProgress.value = Math.round(((i + 1) / files.length) * 100)
        continue
      }
      setUploadCurrent(file.name, i, `上传中 · 成功 ${ok} / 失败 ${fail}`)
      try {
        await uploadFile(folderId, file, check.uploadKey, overlap && overwriteAll, (p) => {
          const base = (i / files.length) * 100
          uploadProgress.value = Math.round(base + p / files.length)
        })
        ok++
      } catch (e: any) {
        fail++
        const msg = e?.message || '上传失败'
        if (errors.length < 3) errors.push(`${file.name}: ${msg}`)
      }
      setUploadCurrent(file.name, i + 1, `上传中 · 成功 ${ok} / 失败 ${fail}`)
      uploadProgress.value = Math.round(((i + 1) / files.length) * 100)
    }
    if (ok > 0) {
      ElMessage.success(
        (ok === 1 ? '上传成功' : `已上传 ${ok} 个文件`) +
          (skip ? `，跳过 ${skip} 个` : '') +
          (fail ? `，失败 ${fail} 个` : ''),
      )
      refresh()
    } else if (skip > 0 && !fail) {
      ElMessage.info('已跳过全部同名文件')
    } else if (fail) {
      ElMessage.error(errors[0] || '上传失败')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '上传失败')
  } finally {
    endUploadProgress()
    uploadBusy = false
  }
}

/** el-upload webkitdirectory 会并发回调，先攒齐再统一上传 */
const folderUploadBuffer: Array<File & { webkitRelativePath?: string }> = []
let folderUploadFlushTimer: ReturnType<typeof setTimeout> | null = null

function doUploadFolder(opt: UploadRequestOptions) {
  const file = opt.file as File & { webkitRelativePath?: string }
  folderUploadBuffer.push(file)
  if (folderUploadFlushTimer) clearTimeout(folderUploadFlushTimer)
  folderUploadFlushTimer = setTimeout(() => {
    folderUploadFlushTimer = null
    const batch = folderUploadBuffer.splice(0)
    void uploadTreeFiles(batch)
  }, 200)
}

async function download(row: Row) {
  try {
    const ok = await downloadFile(row.id, row.name)
    if (ok) ElMessage.success('已开始下载')
  } catch (e: any) {
    ElMessage.error(e?.message || '下载失败')
  }
}

async function renameRow(row: Row) {
  const { value } = await ElMessageBox.prompt('新名称', '重命名', { inputValue: row.name })
  if (row.kind === 'folder') await renameFolder(row.id, value)
  else await renameFile(row.id, value)
  refresh()
}

async function removeRow(row: Row) {
  await ElMessageBox.confirm(`确认删除 ${row.name}？`)
  if (row.kind === 'folder') await deleteFolder(row.id)
  else await deleteFile(row.id)
  refresh()
}

async function doBatchDelete() {
  await ElMessageBox.confirm(`确认删除选中的 ${selected.value.length} 项？`)
  await batchDelete(
    selected.value.filter((r) => r.kind === 'file').map((r) => r.id),
    selected.value.filter((r) => r.kind === 'folder').map((r) => r.id),
  )
  refresh()
}

async function doZip() {
  try {
    const ok = await zipDownload(selectedFiles.value.map((r) => r.id))
    if (ok) ElMessage.success('已开始打包下载')
  } catch (e: any) {
    ElMessage.error(e?.message || '打包下载失败')
  }
}

function startMove(copy: boolean) {
  clipboard.value = {
    copy,
    fileIds: selected.value.filter((r) => r.kind === 'file').map((r) => r.id),
    folderIds: selected.value.filter((r) => r.kind === 'folder').map((r) => r.id),
  }
  ElMessage.success(copy ? '已复制' : '已剪切')
}

async function doPaste() {
  if (!clipboard.value || !view.value) return
  await moveItems({
    targetFolderId: view.value.folder.folderId,
    fileIds: clipboard.value.fileIds,
    folderIds: clipboard.value.folderIds,
    copy: clipboard.value.copy,
  })
  if (!clipboard.value.copy) clipboard.value = null
  ElMessage.success('完成')
  refresh()
}

async function doSearch() {
  const q = keyword.value.trim()
  if (!q) {
    await refresh()
    return
  }
  const seq = ++searchSeq
  try {
    const res = await searchAll(q)
    if (seq !== searchSeq) return
    view.value = {
      ...(view.value as FolderView),
      folderList: res.folders,
      fileList: res.files,
    }
  } catch (e: any) {
    if (seq !== searchSeq) return
    ElMessage.error(e?.message || '搜索失败')
  }
}

let searchTimer: ReturnType<typeof setTimeout> | null = null
let searchSeq = 0
let suppressKeywordWatch = false
watch(keyword, (val) => {
  if (suppressKeywordWatch) return
  if (searchTimer) clearTimeout(searchTimer)
  const q = val.trim()
  if (!q) {
    searchTimer = setTimeout(() => {
      void refresh()
    }, 200)
    return
  }
  searchTimer = setTimeout(() => {
    void doSearch()
  }, 300)
})

function fileExt(name: string) {
  const i = name.lastIndexOf('.')
  return i >= 0 ? name.slice(i + 1).toLowerCase() : ''
}

function fileIconName(row: Row) {
  if (row.kind === 'folder') return 'Folder'
  const ext = fileExt(row.name)
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg', 'ico'].includes(ext)) return 'Picture'
  if (['mp4', 'mkv', 'avi', 'mov', 'webm', 'flv', 'wmv', 'm4v'].includes(ext)) return 'VideoCamera'
  if (['mp3', 'flac', 'wav', 'ogg', 'm4a', 'aac', 'wma'].includes(ext)) return 'Headset'
  if (['pdf'].includes(ext)) return 'Document'
  if (['epub', 'mobi', 'azw', 'azw3'].includes(ext)) return 'Reading'
  if (['doc', 'docx', 'rtf', 'odt'].includes(ext)) return 'DocumentChecked'
  if (['ppt', 'pptx', 'key'].includes(ext)) return 'DataBoard'
  if (['xls', 'xlsx', 'csv', 'numbers'].includes(ext)) return 'Grid'
  if (['txt', 'md', 'log', 'json', 'xml', 'yml', 'yaml', 'ini', 'conf'].includes(ext)) return 'Memo'
  if (['zip', 'rar', '7z', 'tar', 'gz', 'bz2'].includes(ext)) return 'Box'
  if (['html', 'htm', 'css', 'js', 'ts', 'vue', 'java', 'py', 'go', 'c', 'cpp', 'h'].includes(ext)) return 'Monitor'
  if (['exe', 'msi', 'dmg', 'apk', 'iso'].includes(ext)) return 'Cpu'
  return 'Document'
}

function fileIconClass(row: Row) {
  if (row.kind === 'folder') return 'icon-folder'
  const ext = fileExt(row.name)
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg', 'ico'].includes(ext)) return 'icon-image'
  if (['mp4', 'mkv', 'avi', 'mov', 'webm', 'flv', 'wmv', 'm4v'].includes(ext)) return 'icon-video'
  if (['mp3', 'flac', 'wav', 'ogg', 'm4a', 'aac', 'wma'].includes(ext)) return 'icon-audio'
  if (ext === 'pdf') return 'icon-pdf'
  if (['epub', 'mobi', 'azw', 'azw3'].includes(ext)) return 'icon-ebook'
  if (['doc', 'docx', 'rtf', 'odt'].includes(ext)) return 'icon-word'
  if (['ppt', 'pptx', 'key'].includes(ext)) return 'icon-ppt'
  if (['xls', 'xlsx', 'csv', 'numbers'].includes(ext)) return 'icon-excel'
  if (['txt', 'md', 'log', 'json', 'xml', 'yml', 'yaml', 'ini', 'conf'].includes(ext)) return 'icon-text'
  if (['zip', 'rar', '7z', 'tar', 'gz', 'bz2'].includes(ext)) return 'icon-archive'
  if (['html', 'htm', 'css', 'js', 'ts', 'vue', 'java', 'py', 'go', 'c', 'cpp', 'h'].includes(ext)) return 'icon-code'
  if (['exe', 'msi', 'dmg', 'apk', 'iso'].includes(ext)) return 'icon-app'
  return 'icon-file'
}

function isImage(name: string) {
  return /\.(png|jpe?g|gif|bmp|webp)$/i.test(name)
}
function isAudio(name: string) {
  return /\.(mp3|flac|wav|ogg|m4a)$/i.test(name)
}
function isVideo(name: string) {
  return /\.(mp4|mkv|avi|mov|webm)$/i.test(name)
}
function isPdf(name: string) {
  return /\.pdf$/i.test(name)
}
function isTxt(name: string) {
  return /\.(txt|md|markdown|log|html?|css|js|mjs|cjs|ts|tsx|jsx|vue|json|xml|ya?ml|ini|conf|cfg|properties|env|sql|sh|bash|bat|cmd|ps1|py|java|go|rs|c|cpp|h|hpp|cs|php|rb|swift|kt|scala|r|lua|toml|csv|tsv|srt|vtt|diff|patch|gitignore|dockerfile|makefile)$/i.test(
    name,
  ) || /^(dockerfile|makefile|license|readme)$/i.test(name)
}
function isOffice(name: string) {
  return /\.(docx?)$/i.test(name)
}
function isExcel(name: string) {
  return /\.(xlsx|xls)$/i.test(name)
}
function isPpt(name: string) {
  return /\.(pptx|ppt)$/i.test(name)
}
function isEpub(name: string) {
  return /\.epub$/i.test(name)
}

const thumbFailed = ref(new Set<string>())

function supportsThumb(name: string) {
  return isImage(name) || isVideo(name) || isPdf(name) || isPpt(name)
}

function showThumb(row: Row) {
  return row.kind === 'file' && supportsThumb(row.name) && !thumbFailed.value.has(row.id)
}

function thumbSrc(row: Row) {
  return previewThumbUrl(row.id, auth.token)
}

function onThumbError(fileId: string) {
  if (thumbFailed.value.has(fileId)) return
  const next = new Set(thumbFailed.value)
  next.add(fileId)
  thumbFailed.value = next
  if (thumbHover.value?.id === fileId) thumbHover.value = null
}

const thumbHover = ref<{ id: string; src: string; name: string; video: boolean; left: number; top: number } | null>(
  null,
)

function onThumbEnter(e: MouseEvent, row: Row) {
  const el = e.currentTarget as HTMLElement | null
  if (!el) return
  const rect = el.getBoundingClientRect()
  const popW = 320
  const popH = 280
  let left = rect.right + 12
  let top = rect.top - 4
  if (left + popW > window.innerWidth - 8) left = Math.max(8, rect.left - popW - 12)
  if (top + popH > window.innerHeight - 8) top = Math.max(8, window.innerHeight - popH - 8)
  thumbHover.value = {
    id: row.id,
    src: thumbSrc(row),
    name: row.name,
    video: isVideo(row.name),
    left,
    top,
  }
  window.addEventListener('scroll', onThumbLeave, true)
}

function onThumbLeave() {
  thumbHover.value = null
  window.removeEventListener('scroll', onThumbLeave, true)
}

async function preview(row: Row) {
  if (row.kind !== 'file') return
  const name = row.name
  if (isImage(name)) {
    // 优先用主列表同一份 fileList，保证时间/大小字段与排序一致
    let list = (view.value?.fileList || [])
      .filter((f) => isImage(f.fileName))
      .map((f) => ({
        fileId: f.fileId,
        fileName: f.fileName,
        url: '',
        fileCreationDate: f.fileCreationDate || '',
        fileSize: f.fileSize || '',
      }))
    if (!list.some((x) => x.fileId === row.id)) {
      const p = await getPictures(row.id)
      list = (p.pictureViewList || []).map((f) => ({
        fileId: f.fileId,
        fileName: f.fileName,
        url: f.url || '',
        fileCreationDate: f.fileCreationDate || '',
        fileSize: f.fileSize || '',
      }))
    }
    pictures.value = sortSiblingItems(list, loadSiblingSort())
    const idx = pictures.value.findIndex((x) => x.fileId === row.id)
    imgIndex.value = idx >= 0 ? idx : 0
    imgWinZ.value = ++previewZ
    imgVisible.value = true
    imgListOpen.value = pictures.value.length > 1
    return
  }
  if (isAudio(name)) {
    let list = (view.value?.fileList || [])
      .filter((f) => isAudio(f.fileName))
      .map((f) => ({
        fileId: f.fileId,
        fileName: f.fileName,
        url: '',
        fileCreationDate: f.fileCreationDate || '',
        fileSize: f.fileSize || '',
      }))
    if (!list.some((x) => x.fileId === row.id)) {
      list = (await getAudios(view.value!.folder.folderId)).map((f) => ({
        fileId: f.fileId,
        fileName: f.fileName,
        url: f.url || '',
        fileCreationDate: f.fileCreationDate || '',
        fileSize: f.fileSize || '',
      }))
    }
    audios.value = sortSiblingItems(list, loadSiblingSort())
    const idx = audios.value.findIndex((a) => a.fileId === row.id)
    audioIndex.value = idx >= 0 ? idx : 0
    audioListOpen.value = audios.value.length > 1
    audioWinZ.value = ++previewZ
    audioVisible.value = true
    return
  }
  if (isVideo(name)) {
    audioVisible.value = false
    openFilePreview(row, 'video')
    return
  }
  if (isEpub(name)) {
    openFilePreview(row, 'epub')
    return
  }
  if (isPdf(name)) {
    openFilePreview(row, 'pdf', 'pdf')
    return
  }
  if (isTxt(name)) {
    openFilePreview(row, 'text')
    return
  }
  if (isExcel(name)) {
    openFilePreview(row, 'excel')
    return
  }
  if (isPpt(name)) {
    openFilePreview(row, 'ppt')
    return
  }
  if (isOffice(name)) {
    openFilePreview(row, 'pdf', 'office')
    return
  }
  ElMessage.info('该文件类型暂不支持在线打开，请使用「下载」')
}

async function shareChain(row: Row) {
  const key = await createChain(row.id)
  linkText.value = chainShareUrl(key)
  linkVisible.value = true
}

async function shareKey(row: Row) {
  const key = await createDownloadKey(row.id)
  linkText.value = downloadKeyShareUrl(key)
  linkVisible.value = true
}

async function copyLink() {
  await navigator.clipboard.writeText(linkText.value)
  ElMessage.success('已复制')
}

function doLogout() {
  auth.logout()
  ElMessage.success('已退出')
  refresh()
}

async function submitChangePwd() {
  await changePassword(oldPwd.value, newPwd.value)
  ElMessage.success('密码已修改')
  openChangePwd.value = false
}

onMounted(async () => {
  window.addEventListener('keydown', onMediaHotkey)
  osInfo.value = await getOs().catch(() => '获取失败')
  notice.value = await getNotice().catch(() => '')
  refresh()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onMediaHotkey)
})
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}
.head-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.drop-zone {
  position: relative;
}
.drop-zone.is-dragover {
  outline: 2px dashed var(--accent, #0f766e);
  outline-offset: -2px;
}
.drop-overlay {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 118, 110, 0.08);
  backdrop-filter: blur(1px);
  pointer-events: none;
  border-radius: inherit;
}
.drop-overlay-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 28px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
  color: #0f766e;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
}
.drop-overlay-inner p {
  margin: 0;
}
.toolbar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 10px;
}
.path-row {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 0 4px;
  min-width: 0;
  line-height: 22px;
}
.path-row :deep(.el-breadcrumb) {
  display: inline-flex;
  flex: 0 1 auto;
  flex-wrap: wrap;
  min-width: 0;
  font-size: 14px;
  line-height: 22px;
}
.path-row :deep(.el-breadcrumb__item) {
  display: inline-flex;
  align-items: center;
}
.folder-stats {
  flex: 0 0 auto;
  font-size: 13px;
  color: #64748b;
  white-space: nowrap;
  line-height: 22px;
}
.toolbar-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
.hidden-file-input {
  display: none;
}
.toolbar-search {
  width: 240px;
}
.toolbar-search :deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #dbe3ec inset;
  background: #f8fafc;
  transition: box-shadow 0.15s ease, background 0.15s ease;
}
.toolbar-search :deep(.el-input__wrapper:hover),
.toolbar-search :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow: 0 0 0 1px var(--accent) inset;
}
.toolbar-search :deep(.el-input__prefix) {
  color: #94a3b8;
}
.toolbar-group {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.toolbar-divider {
  width: 1px;
  height: 22px;
  background: #e2e8f0;
  flex-shrink: 0;
}
.tb-btn {
  --el-button-border-color: transparent;
  --el-button-hover-border-color: transparent;
  --el-button-active-border-color: transparent;
  border-radius: 8px !important;
  font-weight: 500;
  padding: 8px 12px;
}
.tb-btn .el-icon {
  margin-right: 4px;
  font-size: 15px;
}
.tb-folder {
  --el-button-bg-color: #fff7ed;
  --el-button-text-color: #c2410c;
  --el-button-hover-bg-color: #ffedd5;
  --el-button-hover-text-color: #9a3412;
  --el-button-disabled-bg-color: #f8fafc;
  --el-button-disabled-text-color: #cbd5e1;
}
.tb-upload {
  --el-button-bg-color: #0f766e;
  --el-button-text-color: #fff;
  --el-button-hover-bg-color: #115e59;
  --el-button-hover-text-color: #fff;
  --el-button-disabled-bg-color: #99f6e4;
  --el-button-disabled-text-color: #fff;
}
.tb-upload-folder {
  --el-button-bg-color: #ecfdf5;
  --el-button-text-color: #047857;
  --el-button-hover-bg-color: #d1fae5;
  --el-button-hover-text-color: #065f46;
  --el-button-disabled-bg-color: #f8fafc;
  --el-button-disabled-text-color: #cbd5e1;
}
.tb-delete {
  --el-button-bg-color: #fef2f2;
  --el-button-text-color: #dc2626;
  --el-button-hover-bg-color: #fee2e2;
  --el-button-hover-text-color: #b91c1c;
  --el-button-disabled-bg-color: #f8fafc;
  --el-button-disabled-text-color: #cbd5e1;
}
.tb-zip {
  --el-button-bg-color: #eff6ff;
  --el-button-text-color: #2563eb;
  --el-button-hover-bg-color: #dbeafe;
  --el-button-hover-text-color: #1d4ed8;
  --el-button-disabled-bg-color: #f8fafc;
  --el-button-disabled-text-color: #cbd5e1;
}
.tb-cut {
  --el-button-bg-color: #fffbeb;
  --el-button-text-color: #d97706;
  --el-button-hover-bg-color: #fef3c7;
  --el-button-hover-text-color: #b45309;
  --el-button-disabled-bg-color: #f8fafc;
  --el-button-disabled-text-color: #cbd5e1;
}
.tb-copy {
  --el-button-bg-color: #f5f3ff;
  --el-button-text-color: #7c3aed;
  --el-button-hover-bg-color: #ede9fe;
  --el-button-hover-text-color: #6d28d9;
  --el-button-disabled-bg-color: #f8fafc;
  --el-button-disabled-text-color: #cbd5e1;
}
.tb-paste {
  --el-button-bg-color: #f0fdfa;
  --el-button-text-color: #0f766e;
  --el-button-hover-bg-color: #ccfbf1;
  --el-button-hover-text-color: #115e59;
  --el-button-disabled-bg-color: #f8fafc;
  --el-button-disabled-text-color: #cbd5e1;
}
@media (max-width: 720px) {
  .toolbar-divider {
    display: none;
  }
  .toolbar-search {
    width: 100%;
  }
}
.name-cell {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  max-width: 100%;
  min-width: 0;
  padding: 2px 0;
  color: #1f2937;
}
.name-cell.is-folder .name-text {
  font-weight: 600;
}
.name-cell:hover {
  color: var(--accent);
}
.name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-icon {
  margin-right: 8px;
  font-size: 18px;
  flex-shrink: 0;
}
.file-thumb-wrap {
  position: relative;
  width: 36px;
  height: 28px;
  margin-right: 8px;
  flex-shrink: 0;
  border-radius: 4px;
  overflow: hidden;
  background: #e5e7eb;
  border: 1px solid #d1d5db;
  cursor: zoom-in;
}
.file-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.file-thumb-play {
  position: absolute;
  right: 2px;
  bottom: 2px;
  width: 0;
  height: 0;
  border-style: solid;
  border-width: 4px 0 4px 7px;
  border-color: transparent transparent transparent rgba(255, 255, 255, 0.92);
  filter: drop-shadow(0 0 1px rgba(0, 0, 0, 0.55));
  pointer-events: none;
}
.thumb-pop {
  position: fixed;
  z-index: 5000;
  width: 320px;
  padding: 8px;
  background: #fff;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.28);
  pointer-events: none;
}
.thumb-pop-img {
  width: 100%;
  height: 220px;
  object-fit: contain;
  display: block;
  background: #111827;
  border-radius: 6px;
}
.thumb-pop-badge {
  position: absolute;
  left: 16px;
  top: 16px;
  font-size: 12px;
  line-height: 1;
  color: #fff;
  background: rgba(15, 23, 42, 0.72);
  border-radius: 999px;
  padding: 4px 8px;
}
.thumb-pop-name {
  margin-top: 6px;
  font-size: 12px;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.upload-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 2px 8px;
}
.upload-dialog-meta {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #4b5563;
  font-variant-numeric: tabular-nums;
}
.upload-dialog-current {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
  font-size: 13px;
}
.upload-dialog-label {
  flex-shrink: 0;
  color: #6b7280;
}
.upload-dialog-file {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #111827;
  font-weight: 500;
}
.upload-dialog-detail {
  font-size: 12px;
  color: #6b7280;
}
.icon-folder { color: #e6a23c; }
.icon-image { color: #67c23a; }
.icon-video { color: #409eff; }
.icon-audio { color: #9b59b6; }
.icon-pdf { color: #f56c6c; }
.icon-ebook { color: #0f766e; }
.icon-word { color: #2b579a; }
.icon-ppt { color: #c43e1c; }
.icon-excel { color: #217346; }
.icon-text { color: #909399; }
.icon-archive { color: #d48806; }
.icon-code { color: #528bff; }
.icon-app { color: #606266; }
.icon-file { color: #909399; }

.cell-size {
  font-variant-numeric: tabular-nums;
  font-family: ui-monospace, "Cascadia Mono", "Segoe UI Mono", Consolas, monospace;
  font-size: 13px;
  color: #374151;
}
.cell-size.muted {
  color: #9ca3af;
  font-family: inherit;
}
.cell-creator {
  display: inline-block;
  max-width: 100%;
  padding: 2px 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cell-date {
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  color: #64748b;
}

.op-btns {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.op-btns :deep(.el-button.is-link) {
  font-weight: 500;
  padding: 0 6px;
  margin: 0;
}
.op-btns :deep(.el-button--primary.is-link) {
  color: #0f766e;
}
.op-btns :deep(.el-button--primary.is-link:hover) {
  color: #115e59;
}
.op-btns :deep(.el-button--success.is-link) {
  color: #16a34a;
}
.op-btns :deep(.el-button--success.is-link:hover) {
  color: #15803d;
}
.op-btns :deep(.el-button--warning.is-link) {
  color: #d97706;
}
.op-btns :deep(.el-button--warning.is-link:hover) {
  color: #b45309;
}
.op-btns :deep(.el-button--danger.is-link) {
  color: #dc2626;
}
.op-btns :deep(.el-button--danger.is-link:hover) {
  color: #b91c1c;
}
.op-btns :deep(.el-button--info.is-link) {
  color: #64748b;
}
.op-btns :deep(.el-button--info.is-link:hover) {
  color: #334155;
}

.file-table :deep(.el-table__header th.el-table__cell) {
  background: #f8fafc;
  color: #334155;
  font-weight: 600;
  font-size: 13px;
}
.file-table :deep(.el-table__row:hover > td.el-table__cell) {
  background: #f0fdfa !important;
}
.file-table :deep(.el-table__cell) {
  padding: 10px 0;
}
.img-preview-body {
  flex: 1;
  min-height: 0;
  display: flex;
  padding: 0;
}
.img-with-list {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  position: relative;
}
.img-preview-body.immersive {
  background: #000;
}
.img-preview-body.immersive .img-viewer {
  border-radius: 0;
}
.img-viewer {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
  min-height: 0;
  background: #111827;
  overflow: hidden;
}
.img-stage {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 48px;
}
.img-stage img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 4px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
}
.img-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 2;
}
.img-nav.prev { left: 12px; }
.img-nav.next { right: 12px; }
.preview-title {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.audio-preview-body {
  flex: 1;
  min-height: 0;
  display: flex;
  background:
    radial-gradient(1200px 420px at 20% -10%, rgba(15, 118, 110, 0.35), transparent 55%),
    radial-gradient(900px 380px at 90% 110%, rgba(30, 64, 175, 0.22), transparent 50%),
    linear-gradient(160deg, #0f172a 0%, #111827 48%, #0b1220 100%);
}
.audio-preview-body.immersive {
  background: #000;
}
.audio-with-list {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  position: relative;
}
.audio-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 28px;
  padding: 32px 36px;
  color: #e5e7eb;
}
.audio-stage {
  display: flex;
  align-items: center;
  gap: 22px;
  min-width: 0;
}
.audio-disc {
  position: relative;
  width: 132px;
  height: 132px;
  flex-shrink: 0;
  border-radius: 50%;
  background:
    radial-gradient(circle at 35% 30%, #334155 0%, #0f172a 42%, #020617 70%),
    conic-gradient(from 210deg, #134e4a, #1e3a8a, #0f766e, #134e4a);
  box-shadow:
    0 18px 40px rgba(2, 6, 23, 0.55),
    inset 0 0 0 1px rgba(148, 163, 184, 0.25);
}
.audio-disc.playing {
  animation: audio-spin 8s linear infinite;
}
.audio-disc-ring {
  position: absolute;
  inset: 14px;
  border-radius: 50%;
  border: 1px solid rgba(148, 163, 184, 0.28);
  box-shadow: inset 0 0 0 10px rgba(15, 23, 42, 0.35);
}
.audio-disc-core {
  position: absolute;
  inset: 44px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #99f6e4;
  background: radial-gradient(circle at 40% 35%, #1f2937, #020617 70%);
  box-shadow: 0 0 0 2px rgba(45, 212, 191, 0.35);
}
.audio-meta {
  min-width: 0;
  flex: 1;
}
.audio-now {
  font-size: 22px;
  line-height: 1.3;
  font-weight: 650;
  letter-spacing: 0.01em;
  color: #f8fafc;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.audio-sub {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #94a3b8;
}
.audio-ext {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  color: #cbd5e1;
  font-size: 11px;
  letter-spacing: 0.04em;
}
.audio-controls {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.audio-time-row {
  display: grid;
  grid-template-columns: 42px 1fr 42px;
  gap: 10px;
  align-items: center;
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}
.audio-seek {
  -webkit-appearance: none;
  appearance: none;
  width: 100%;
  height: 6px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.28);
  outline: none;
  cursor: pointer;
}
.audio-seek::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #5eead4;
  box-shadow: 0 0 0 4px rgba(45, 212, 191, 0.18);
  cursor: pointer;
}
.audio-seek::-moz-range-thumb {
  width: 14px;
  height: 14px;
  border: 0;
  border-radius: 50%;
  background: #5eead4;
  cursor: pointer;
}
.audio-btn-row {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 18px;
}
.audio-btn {
  width: 46px;
  height: 46px;
  border: 0;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #e2e8f0;
  background: rgba(148, 163, 184, 0.14);
  cursor: pointer;
  transition: background 0.15s ease, transform 0.15s ease, color 0.15s ease;
}
.audio-btn:hover:not(:disabled) {
  background: rgba(148, 163, 184, 0.24);
  color: #fff;
}
.audio-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.audio-btn-main {
  width: 64px;
  height: 64px;
  color: #042f2e;
  background: linear-gradient(160deg, #5eead4, #14b8a6 55%, #0f766e);
  box-shadow: 0 12px 28px rgba(15, 118, 110, 0.35);
}
.audio-btn-main:hover:not(:disabled) {
  color: #022c22;
  background: linear-gradient(160deg, #99f6e4, #2dd4bf 55%, #0d9488);
  transform: translateY(-1px);
}
.audio-vol-row {
  display: grid;
  grid-template-columns: 40px 1fr 42px;
  gap: 10px;
  align-items: center;
}
.audio-btn-vol {
  width: 40px;
  height: 40px;
}
.audio-vol-label {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
  text-align: right;
}
.audio-hidden {
  position: absolute;
  width: 0;
  height: 0;
  opacity: 0;
  pointer-events: none;
}
@keyframes audio-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
