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

      <el-progress v-if="uploadProgress >= 0" :percentage="uploadProgress" style="margin:8px 0" />

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
              <el-icon class="file-icon" :class="fileIconClass(row)">
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
            />
          </div>
        </div>
      </template>
    </AppWindow>

    <AppWindow
      v-model="audioVisible"
      title="音频播放"
      :initial-width="720"
      :initial-height="420"
      @closed="onAudioClosed"
    >
      <template #default="{ immersive }">
        <div class="audio-preview-body">
          <div class="audio-with-list">
            <div class="audio-main">
              <div class="audio-now">{{ currentAudio?.fileName || '音频播放' }}</div>
              <audio
                v-if="currentAudio"
                :key="currentAudio.fileId"
                ref="audioRef"
                :src="mediaSrc(currentAudio)"
                controls
                preload="metadata"
              />
            </div>
            <SiblingPlaylist
              v-model:open="audioListOpen"
              :items="audioSiblings"
              :active-id="currentAudio?.fileId || ''"
              :immersive="immersive"
              tone="light"
              title="播放列表"
              @select="onSelectAudio"
            />
          </div>
        </div>
      </template>
    </AppWindow>

    <PreviewDialog
      v-model="filePreviewVisible"
      :title="filePreviewTitle"
      :file-id="filePreviewId"
      :type="filePreviewType"
      :kind="filePreviewKind"
    />

    <el-dialog v-model="linkVisible" title="分享链接" width="560px">
      <el-input v-model="linkText" type="textarea" :rows="3" readonly />
      <template #footer>
        <el-button type="primary" @click="copyLink">复制</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
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
import { changePassword } from '@/api/auth'
import { chainShareUrl, downloadKeyShareUrl, mediaSrc } from '@/api/urls'
import { useAuthStore } from '@/stores/auth'
import { bindVideoVolume } from '@/utils/mediaVolume'
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
const dragOver = ref(false)
let dragDepth = 0
const openChangePwd = ref(false)
const oldPwd = ref('')
const newPwd = ref('')
const imgVisible = ref(false)
const imgIndex = ref(0)
const imgListOpen = ref(true)
const pictures = ref<{ fileId: string; fileName: string; url: string }[]>([])

const currentPicture = computed(() => pictures.value[imgIndex.value] || null)
const pictureSiblings = computed<SiblingItem[]>(() =>
  pictures.value.map((p) => ({ fileId: p.fileId, fileName: p.fileName, thumb: mediaSrc(p) })),
)

function onSelectPicture(item: SiblingItem) {
  const i = pictures.value.findIndex((p) => p.fileId === item.fileId)
  if (i >= 0) imgIndex.value = i
}

function onImgClosed() {
  imgIndex.value = 0
  pictures.value = []
  imgListOpen.value = true
}
const audioVisible = ref(false)
const audioIndex = ref(0)
const audioListOpen = ref(true)
const audioRef = ref<HTMLAudioElement | null>(null)
const audios = ref<{ fileId: string; fileName: string; url: string }[]>([])
const currentAudio = computed(() => audios.value[audioIndex.value] || null)
const audioSiblings = computed<SiblingItem[]>(() =>
  audios.value.map((a) => ({ fileId: a.fileId, fileName: a.fileName })),
)

function onSelectAudio(item: SiblingItem) {
  const i = audios.value.findIndex((a) => a.fileId === item.fileId)
  if (i >= 0) audioIndex.value = i
}

watch([currentAudio, audioVisible], async () => {
  if (!audioVisible.value || !currentAudio.value) return
  await nextTick()
  bindVideoVolume(audioRef.value)
  try {
    await audioRef.value?.play()
  } catch {
    /* autoplay may be blocked */
  }
})

function onAudioClosed() {
  audios.value = []
  audioIndex.value = 0
  audioListOpen.value = true
}
const linkVisible = ref(false)
const linkText = ref('')
const clipboard = ref<{ copy: boolean; fileIds: string[]; folderIds: string[] } | null>(null)
const filePreviewVisible = ref(false)
const filePreviewTitle = ref('')
const filePreviewId = ref('')
const filePreviewType = ref<PreviewType>('pdf')
const filePreviewKind = ref<'pdf' | 'txt' | 'office'>('pdf')

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
  return [...folders, ...files].sort((a, b) => sortByDate(b, a))
})

function sortByName(a: Row, b: Row) {
  return a.name.localeCompare(b.name, 'zh-CN', { numeric: true, sensitivity: 'base' })
}

function sortBySize(a: Row, b: Row) {
  return a.sizeBytes - b.sizeBytes
}

function sortByDate(a: Row, b: Row) {
  return String(a.date || '').localeCompare(String(b.date || ''))
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
  await uploadFilesBatch(files)
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

async function onDrop(e: DragEvent) {
  e.preventDefault()
  dragDepth = 0
  dragOver.value = false
  if (!auth.isLogin) {
    ElMessage.warning('请先登录后再上传')
    return
  }
  if (!view.value || !e.dataTransfer) return
  try {
    const files = await collectDroppedFiles(e.dataTransfer)
    if (!files.length) {
      ElMessage.info('未识别到可上传的文件')
      return
    }
    await uploadDroppedFiles(files)
  } catch (err: any) {
    ElMessage.error(err?.message || '拖拽上传失败')
  }
}

/** Traverse FileSystemEntry tree (supports folder drop). */
async function collectDroppedFiles(dt: DataTransfer): Promise<Array<File & { webkitRelativePath?: string }>> {
  const out: Array<File & { webkitRelativePath?: string }> = []
  const items = dt.items
  if (items?.length) {
    const entries: FileSystemEntry[] = []
    for (let i = 0; i < items.length; i++) {
      const entry = items[i].webkitGetAsEntry?.()
      if (entry) entries.push(entry)
    }
    if (entries.length) {
      for (const entry of entries) {
        await walkFsEntry(entry, '', out)
      }
      return out
    }
  }
  // Fallback: flat file list (no directory metadata)
  return Array.from(dt.files || []) as Array<File & { webkitRelativePath?: string }>
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

async function uploadDroppedFiles(files: Array<File & { webkitRelativePath?: string }>) {
  const hasNested = files.some((f) => {
    const rel = f.webkitRelativePath || ''
    return rel.includes('/')
  })
  if (!hasNested) {
    await uploadFilesBatch(files)
    return
  }
  uploadProgress.value = 0
  let ok = 0
  try {
    for (let i = 0; i < files.length; i++) {
      await uploadOneWithRelativePath(files[i])
      ok++
      uploadProgress.value = Math.round(((i + 1) / files.length) * 100)
    }
    ElMessage.success(ok === 1 ? '上传成功' : `已上传 ${ok} 个文件`)
    refresh()
  } catch (e: any) {
    ElMessage.error(e.message || '上传失败')
    if (ok > 0) refresh()
  } finally {
    uploadProgress.value = -1
  }
}

async function uploadFilesBatch(files: File[]) {
  const folderId = view.value!.folder.folderId
  uploadProgress.value = 0
  let ok = 0
  let skip = 0
  try {
    const check = await checkUpload(
      folderId,
      files.map((f) => f.name),
    )
    let overwriteAll = false
    let asked = false
    for (let i = 0; i < files.length; i++) {
      const file = files[i]
      const overlap = check.overlaps.includes(file.name)
      let overwrite = false
      if (overlap) {
        if (!asked) {
          asked = true
          overwriteAll = await ElMessageBox.confirm(
            `有 ${check.overlaps.length} 个同名文件，是否全部覆盖？`,
            '提示',
            { confirmButtonText: '覆盖', cancelButtonText: '跳过同名' },
          )
            .then(() => true)
            .catch(() => false)
        }
        overwrite = overwriteAll
        if (!overwrite) {
          skip++
          uploadProgress.value = Math.round(((i + 1) / files.length) * 100)
          continue
        }
      }
      await uploadFile(folderId, file, check.uploadKey, overwrite, (p) => {
        const base = (i / files.length) * 100
        uploadProgress.value = Math.round(base + p / files.length)
      })
      ok++
    }
    if (ok > 0) {
      ElMessage.success(ok === 1 ? '上传成功' : `已上传 ${ok} 个文件` + (skip ? `，跳过 ${skip} 个` : ''))
      refresh()
    } else if (skip > 0) {
      ElMessage.info('已跳过全部同名文件')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '上传失败')
  } finally {
    uploadProgress.value = -1
  }
}

/** Shared across concurrent directory-upload requests to avoid duplicate createFolder races. */
const folderPathCache = new Map<string, string>()

async function uploadOneWithRelativePath(file: File & { webkitRelativePath?: string }) {
  const rel = file.webkitRelativePath || file.name
  const parts = rel.split('/')
  let parentId = view.value!.folder.folderId
  let pathKey = parentId
  for (let i = 0; i < parts.length - 1; i++) {
    const name = parts[i]
    pathKey = `${pathKey}/${name}`
    const cached = folderPathCache.get(pathKey)
    if (cached) {
      parentId = cached
      continue
    }
    const created = await createFolder(parentId, name, view.value!.folder.folderConstraint)
    parentId = created.folderId
    folderPathCache.set(pathKey, parentId)
  }
  const check = await checkUpload(parentId, [file.name])
  await uploadFile(parentId, file, check.uploadKey, true)
}

async function doUploadFolder(opt: UploadRequestOptions) {
  const file = opt.file as File & { webkitRelativePath?: string }
  await uploadOneWithRelativePath(file)
  refresh()
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

function openFilePreview(row: Row, type: PreviewType, kind: 'pdf' | 'txt' | 'office' = 'pdf') {
  filePreviewTitle.value = row.name
  filePreviewId.value = row.id
  filePreviewType.value = type
  filePreviewKind.value = kind
  filePreviewVisible.value = true
}

async function preview(row: Row) {
  if (row.kind !== 'file') return
  const name = row.name
  if (isImage(name)) {
    const p = await getPictures(row.id)
    pictures.value = p.pictureViewList || []
    imgIndex.value = Math.min(Math.max(p.index || 0, 0), Math.max(pictures.value.length - 1, 0))
    imgVisible.value = true
    imgListOpen.value = pictures.value.length > 1
    return
  }
  if (isAudio(name)) {
    audios.value = await getAudios(view.value!.folder.folderId)
    const idx = audios.value.findIndex((a) => a.fileId === row.id)
    audioIndex.value = idx >= 0 ? idx : 0
    audioListOpen.value = audios.value.length > 1
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
  osInfo.value = await getOs().catch(() => '获取失败')
  notice.value = await getNotice().catch(() => '')
  refresh()
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
  gap: 16px;
  padding: 28px 32px;
}
.audio-now {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.audio-main audio {
  width: 100%;
}
</style>
