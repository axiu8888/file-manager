<template>
  <AppWindow
    :model-value="modelValue"
    :title="displayTitle"
    :z-index="zIndex"
    :activate-key="activateKey"
    :cascade="cascade"
    :show-mask="false"
    :fullscreen-target="fullscreenTarget"
    @update:model-value="onWindowVisible"
    @opened="onOpened"
    @closed="onClosed"
    @resized="onWindowResized"
    @activate="emit('activate')"
  >
    <template #title>
      <div class="preview-title-wrap">
        <div class="preview-title">{{ displayTitle }}{{ textDirty ? ' *' : '' }}</div>
        <div v-if="bookTitle && bookTitle !== displayTitle" class="preview-sub">{{ bookTitle }}</div>
      </div>
    </template>

    <template #default="{ immersive }">
      <div class="preview-body" :class="{ 'is-immersive': immersive, 'is-video': type === 'video' }">
        <div class="preview-with-list">
          <div class="preview-main-pane">
            <div v-if="loading" class="preview-status">加载中…</div>
            <div v-if="error" class="preview-status error">{{ error }}</div>

            <div v-if="type === 'pdf' && !error" class="preview-pdf">
          <div v-show="!immersive" class="epub-toolbar">
            <el-button size="small" @click="togglePdfToc">{{ pdfTocOpen ? '隐藏目录' : '目录' }}</el-button>
            <span v-if="pdfChapterLabel" class="epub-chapter">{{ pdfChapterLabel }}</span>
          </div>
          <div class="epub-main" :class="{ immersive }">
            <aside v-show="pdfTocOpen && !immersive" class="epub-toc">
              <div class="epub-toc-title">目录</div>
              <div v-if="!pdfTocItems.length" class="epub-toc-empty">暂无目录大纲</div>
              <button
                v-for="item in pdfTocItems"
                :key="item.id"
                type="button"
                class="epub-toc-item"
                :class="{ active: item.id === pdfActiveTocId, [`level-${item.level}`]: true }"
                :title="item.label"
                @click="goPdfToc(item)"
              >
                {{ item.label }}
              </button>
            </aside>
            <iframe
              v-if="pdfSrc"
              :key="pdfFrameKey"
              class="epub-viewer pdf-frame"
              :src="pdfSrc"
              title="pdf"
            />
          </div>
        </div>

        <div v-if="type === 'epub' && !error" class="preview-epub">
          <div v-show="!immersive" class="epub-toolbar">
            <el-button size="small" @click="toggleToc">{{ tocOpen ? '隐藏目录' : '目录' }}</el-button>
            <el-button size="small" :disabled="loading" title="也可滚轮 / 左右点击翻页" @click="epubPrev">上一页</el-button>
            <el-button size="small" :disabled="loading" title="也可滚轮 / 左右点击翻页" @click="epubNext">下一页</el-button>
            <span v-if="chapterLabel" class="epub-chapter">{{ chapterLabel }}</span>
          </div>
          <div class="epub-main" :class="{ immersive }">
            <aside v-show="tocOpen && !immersive" class="epub-toc">
              <div class="epub-toc-title">目录</div>
              <div v-if="!tocItems.length" class="epub-toc-empty">暂无目录</div>
              <button
                v-for="item in tocItems"
                :key="item.id"
                type="button"
                class="epub-toc-item"
                :class="{ active: item.id === activeTocId, [`level-${item.level}`]: true }"
                :title="item.label"
                @click="goToc(item)"
              >
                {{ item.label }}
              </button>
            </aside>
            <div ref="epubViewerRef" class="epub-viewer" />
          </div>
        </div>

        <div v-if="type === 'video' && !error" class="preview-video-layout">
          <div class="preview-video-stage">
            <div v-show="!immersive && !loading && videoSrc" class="video-rate-bar">
              <span class="video-rate-label">倍速</span>
              <button
                v-for="r in videoRates"
                :key="r"
                type="button"
                class="video-rate-btn"
                :class="{ active: playbackRate === r }"
                :title="`${r}x`"
                @click="setPlaybackRate(r)"
              >
                {{ formatRate(r) }}
              </button>
            </div>
            <video
              v-if="!loading && videoSrc"
              :key="`video-${currentFileId}`"
              ref="videoRef"
              class="preview-video"
              :src="videoSrc"
              controls
              playsinline
              preload="metadata"
              controlslist="nodownload"
              @loadedmetadata="applyPlaybackRate"
            />
          </div>
        </div>

        <div v-if="!loading && !error && type === 'excel'" class="preview-excel">
          <div v-if="!excelSheets.length" class="excel-empty">工作簿为空</div>
          <template v-else>
            <div class="excel-tabs">
              <button
                v-for="(sheet, i) in excelSheets"
                :key="`${sheet.name}-${i}`"
                type="button"
                class="excel-tab"
                :class="{ active: i === excelSheetIndex }"
                :title="sheet.name"
                @click="excelSheetIndex = i"
              >
                {{ sheet.name }}
              </button>
            </div>
            <div v-if="currentExcelSheet?.truncated" class="excel-hint">仅预览前 1000 行 × 50 列，完整内容请下载</div>
            <div class="excel-grid-wrap">
              <table v-if="currentExcelSheet && excelColCount" class="excel-grid">
                <thead>
                  <tr>
                    <th class="excel-corner"></th>
                    <th v-for="c in excelColCount" :key="`c-${c}`">{{ excelColLabel(c - 1) }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, r) in currentExcelSheet.rows" :key="`r-${r}`">
                    <th>{{ r + 1 }}</th>
                    <td v-for="c in excelColCount" :key="`r-${r}-c-${c}`">{{ row[c - 1] ?? '' }}</td>
                  </tr>
                </tbody>
              </table>
              <div v-else class="excel-empty">此工作表为空</div>
            </div>
          </template>
        </div>

        <div v-if="!loading && !error && type === 'ppt'" class="preview-ppt">
          <div v-if="!pptSlides.length" class="excel-empty">演示文稿为空</div>
          <template v-else>
            <div class="ppt-toolbar">
              <el-button size="small" :disabled="pptIndex <= 0" @click="pptPrev">上一页</el-button>
              <span class="ppt-page">{{ pptIndex + 1 }} / {{ pptSlides.length }}</span>
              <el-button size="small" :disabled="pptIndex >= pptSlides.length - 1" @click="pptNext">下一页</el-button>
              <span v-if="currentPptSlide?.title" class="ppt-slide-title">{{ currentPptSlide.title }}</span>
            </div>
            <div class="ppt-stage">
              <div v-if="pptImgLoading" class="preview-status">渲染中…</div>
              <div v-if="pptImgError" class="preview-status error">{{ pptImgError }}</div>
              <img
                v-if="currentPptSlide"
                :key="`ppt-${currentFileId}-${pptIndex}`"
                class="ppt-slide-img"
                :src="pptSlideSrc(pptIndex)"
                :alt="currentPptSlide.title"
                @load="pptImgLoading = false"
                @error="onPptImgError"
              />
            </div>
            <div v-if="pptSlides.length > 1" class="ppt-thumbs">
              <button
                v-for="s in pptSlides"
                :key="s.index"
                type="button"
                class="ppt-thumb"
                :class="{ active: s.index === pptIndex }"
                :title="s.title"
                @click="goPpt(s.index)"
              >
                {{ s.index + 1 }}
              </button>
            </div>
          </template>
        </div>

        <div v-if="!loading && !error && type === 'text'" class="preview-text">
          <div v-if="showTextToolbar" class="md-toolbar">
            <el-button-group v-if="textMode === 'markdown' || (textMode === 'html' && textEditing)">
              <el-button size="small" :type="paneLayout === 'source' ? 'primary' : 'default'" @click="paneLayout = 'source'">
                源码
              </el-button>
              <el-button size="small" :type="paneLayout === 'split' ? 'primary' : 'default'" @click="paneLayout = 'split'">
                并排
              </el-button>
              <el-button size="small" :type="paneLayout === 'preview' ? 'primary' : 'default'" @click="paneLayout = 'preview'">
                预览
              </el-button>
            </el-button-group>
            <div v-if="canEditText" class="text-edit-actions">
              <template v-if="textEditing">
                <span class="text-edit-status">{{ textDirty ? '未保存' : '已保存' }}</span>
                <el-button size="small" :disabled="textSaving" @click="cancelEdit">退出编辑</el-button>
                <el-button
                  size="small"
                  type="primary"
                  title="Ctrl+S"
                  :disabled="!textDirty || textSaving"
                  :loading="textSaving"
                  @click="saveText"
                >
                  保存
                </el-button>
              </template>
              <el-button v-else size="small" type="primary" @click="startEdit">编辑</el-button>
            </div>
          </div>

          <template v-if="textMode === 'html'">
            <div v-if="textEditing" class="md-panes" :class="`layout-${paneLayout}`">
              <div v-show="paneLayout !== 'preview'" class="md-source">
                <textarea v-model="textContent" class="text-editor" spellcheck="false" />
              </div>
              <div v-show="paneLayout === 'split'" class="md-split-line" aria-hidden="true" />
              <iframe
                v-show="paneLayout !== 'source'"
                class="text-html-frame"
                sandbox=""
                :srcdoc="textContent"
                title="html-preview"
              />
            </div>
            <iframe
              v-else
              class="text-html-frame"
              sandbox=""
              :srcdoc="textContent"
              title="html-preview"
            />
          </template>

          <template v-else-if="textMode === 'markdown'">
            <div class="md-panes" :class="`layout-${paneLayout}`">
              <div v-show="paneLayout !== 'preview'" class="md-source">
                <textarea v-if="textEditing" v-model="textContent" class="text-editor" spellcheck="false" />
                <pre v-else class="text-pre md-source-pre"><code>{{ textContent }}</code></pre>
              </div>
              <div v-show="paneLayout === 'split'" class="md-split-line" aria-hidden="true" />
              <div v-show="paneLayout !== 'source'" class="md-preview">
                <div class="text-md" v-html="renderedMarkdown" />
              </div>
            </div>
          </template>

          <textarea v-else-if="textEditing" v-model="textContent" class="text-editor" spellcheck="false" />
          <pre v-else class="text-pre"><code>{{ textContent }}</code></pre>
        </div>
          </div>
          <SiblingPlaylist
            v-model:open="listOpen"
            :items="siblings"
            :active-id="currentFileId"
            :immersive="immersive"
            :tone="type === 'video' ? 'dark' : 'light'"
            :title="listTitle"
            @select="openSibling"
          />
        </div>
      </div>
    </template>
  </AppWindow>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import ePub from 'epubjs'
import * as pdfjs from 'pdfjs-dist'
import { PDFDocument } from 'pdf-lib'
import { marked } from 'marked'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import AppWindow from '@/components/AppWindow.vue'
import SiblingPlaylist, { type SiblingItem } from '@/components/SiblingPlaylist.vue'
import { bindVideoVolume } from '@/utils/mediaVolume'
import { fetchPdfPreview, fetchPreviewResource, getExcel, getPpt, getSiblings, saveTextContent } from '@/api/files'
import { isPreviewResourceUrl, pptSlideUrl, previewResourceUrl, previewThumbUrl } from '@/api/urls'

// Vite: use bundled worker
pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url,
).toString()

marked.setOptions({
  gfm: true,
  breaks: true,
})

export type PreviewType = 'pdf' | 'epub' | 'video' | 'text' | 'excel' | 'ppt'

const props = defineProps<{
  modelValue: boolean
  title: string
  fileId: string
  type: PreviewType
  kind?: 'pdf' | 'txt' | 'office'
  zIndex?: number
  /** 递增可把已打开窗口还原并置顶 */
  activateKey?: number
  /** 多窗口错开 */
  cascade?: number
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  activate: []
}>()

const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const pdfSrc = ref('')
const videoSrc = ref('')
const videoRef = ref<HTMLVideoElement | null>(null)
const videoRates = [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2] as const
const playbackRate = ref(1)
const siblings = ref<SiblingItem[]>([])
const listOpen = ref(true)
const currentFileId = ref('')
const playingName = ref('')
const epubViewerRef = ref<HTMLElement | null>(null)
const textContent = ref('')
const textSaved = ref('')
const textSaving = ref(false)
const textEditing = ref(false)
const textMode = ref<'plain' | 'markdown' | 'html'>('plain')
const paneLayout = ref<'source' | 'preview' | 'split'>('split')
const excelSheets = ref<{ name: string; rows: string[][]; truncated: boolean }[]>([])
const excelSheetIndex = ref(0)
const pptSlides = ref<{ index: number; title: string }[]>([])
const pptIndex = ref(0)
const pptImgLoading = ref(false)
const pptImgError = ref('')

const currentExcelSheet = computed(() => excelSheets.value[excelSheetIndex.value] || null)
const excelColCount = computed(() => currentExcelSheet.value?.rows[0]?.length || 0)
const currentPptSlide = computed(() => pptSlides.value[pptIndex.value] || null)

const TEXT_MAX_BYTES = 5 * 1024 * 1024
const canEditText = computed(() => props.type === 'text' && auth.isLogin && auth.hasAuth('UPLOAD_FILES'))
const textDirty = computed(() => textEditing.value && textContent.value !== textSaved.value)
const showTextToolbar = computed(() => textMode.value === 'markdown' || canEditText.value)

const renderedMarkdown = computed(() => {
  try {
    return marked.parse(textContent.value || '', { async: false }) as string
  } catch {
    return '<p>Markdown 渲染失败</p>'
  }
})

function fileExtOf(name: string) {
  const i = name.lastIndexOf('.')
  return i >= 0 ? name.slice(i + 1).toLowerCase() : ''
}

function detectTextMode(name: string): 'plain' | 'markdown' | 'html' {
  const ext = fileExtOf(name)
  if (ext === 'md' || ext === 'markdown') return 'markdown'
  if (ext === 'html' || ext === 'htm') return 'html'
  return 'plain'
}

function decodeTextBytes(buf: ArrayBuffer): string {
  const bytes = new Uint8Array(buf)
  const utf8 = new TextDecoder('utf-8', { fatal: false }).decode(bytes)
  const bad = (utf8.match(/\uFFFD/g) || []).length
  if (bad === 0 || bad / Math.max(utf8.length, 1) < 0.01) return utf8
  try {
    const gbk = new TextDecoder('gbk', { fatal: false }).decode(bytes)
    const gbkBad = (gbk.match(/\uFFFD/g) || []).length
    if (gbkBad < bad) return gbk
  } catch {
    /* ignore */
  }
  return utf8
}

function fullscreenTarget() {
  if (props.type === 'video') return videoRef.value
  return null
}
const bookTitle = ref('')
const chapterLabel = ref('')
const tocOpen = ref(true)
const tocItems = ref<TocItem[]>([])
const activeTocId = ref('')
const pdfTocOpen = ref(true)
const pdfTocItems = ref<PdfTocItem[]>([])
const pdfActiveTocId = ref('')
const pdfChapterLabel = ref('')
const pdfFrameKey = ref(0)
let pdfDoc: any = null
let pdfBlobBase = ''

interface TocItem {
  id: string
  label: string
  href: string
  level: number
}

interface PdfTocItem {
  id: string
  label: string
  page: number
  level: number
}

const displayTitle = computed(() => {
  const raw = playingName.value || props.title
  const name = fixMojibake((raw || '').trim())
  return name || '预览'
})

const listTitle = computed(() => {
  if (props.type === 'video') return '播放列表'
  if (props.type === 'epub') return '书籍列表'
  if (props.type === 'pdf') return props.kind === 'office' ? '文档列表' : 'PDF 列表'
  if (props.type === 'excel') return '表格列表'
  if (props.type === 'ppt') return '演示列表'
  return '文件列表'
})

function activeId() {
  return currentFileId.value || props.fileId
}

function fixMojibake(text: string): string {
  if (!text) return ''
  // 已是正常中文/日韩等
  if (/[\u4e00-\u9fff\u3040-\u30ff\uac00-\ud7af]/.test(text)) return text
  // UTF-8 被当成 Latin-1 的典型乱码
  try {
    const bytes = Uint8Array.from(text, (ch) => ch.charCodeAt(0) & 0xff)
    const utf8 = new TextDecoder('utf-8', { fatal: false }).decode(bytes)
    if (/[\u4e00-\u9fff]/.test(utf8) && !utf8.includes('\uFFFD')) return utf8
  } catch {
    /* ignore */
  }
  // GBK/GB18030 被当成 Latin-1
  try {
    const bytes = Uint8Array.from(text, (ch) => ch.charCodeAt(0) & 0xff)
    const gbk = new TextDecoder('gbk', { fatal: false }).decode(bytes)
    if (/[\u4e00-\u9fff]/.test(gbk) && !gbk.includes('\uFFFD')) return gbk
  } catch {
    /* ignore */
  }
  try {
    return decodeURIComponent(text)
  } catch {
    return text
  }
}

/** 覆盖 PDF 内嵌 Title，避免 Chrome PDF 工具栏显示乱码标题 */
async function pdfBytesWithTitle(buf: ArrayBuffer, title: string): Promise<Uint8Array> {
  const name = fixMojibake(title).trim() || 'preview.pdf'
  try {
    const doc = await PDFDocument.load(buf.slice(0), { ignoreEncryption: true, updateMetadata: true })
    doc.setTitle(name)
    doc.setLanguage('zh-CN')
    const saved = await doc.save({ useObjectStreams: false })
    return saved
  } catch {
    return new Uint8Array(buf)
  }
}

let objectUrl = ''
let book: ReturnType<typeof ePub> | null = null
let rendition: ReturnType<ReturnType<typeof ePub>['renderTo']> | null = null
let loadSeq = 0
let epubFlow: 'paginated' | 'scrolled-doc' = 'paginated'
let wheelAcc = 0
let wheelLock = false
let wheelUnlockTimer: ReturnType<typeof setTimeout> | null = null
const WHEEL_THRESHOLD = 60
const WHEEL_COOLDOWN_MS = 320

function stopVideo() {
  const v = videoRef.value
  if (v) {
    try {
      v.pause()
    } catch {
      /* ignore */
    }
  }
  // 兜底：停掉页面里误挂载的同资源媒体（例如旧的隐藏 iframe/幽灵 video）
  const id = activeId()
  if (!id) return
  document.querySelectorAll('video, audio').forEach((el) => {
    const media = el as HTMLMediaElement
    if (media === v) return
    const s = media.currentSrc || media.getAttribute('src') || ''
    if (!s.includes(id)) return
    try {
      media.pause()
      media.removeAttribute('src')
      media.load()
    } catch {
      /* ignore */
    }
  })
  document.querySelectorAll('iframe').forEach((frame) => {
    const s = frame.getAttribute('src') || ''
    if (!isPreviewResourceUrl(s, id)) return
    frame.removeAttribute('src')
  })
}

function resetViewer() {
  window.removeEventListener('keydown', onKey)
  stopVideo()
  const viewer = epubViewerRef.value
  if (viewer) {
    viewer.removeEventListener('wheel', onEpubWheel)
    viewer.removeEventListener('click', onEpubTapTurn)
  }
  if (wheelUnlockTimer) {
    clearTimeout(wheelUnlockTimer)
    wheelUnlockTimer = null
  }
  wheelAcc = 0
  wheelLock = false
  epubFlow = 'paginated'
  try {
    rendition?.destroy()
  } catch {
    /* ignore */
  }
  try {
    book?.destroy()
  } catch {
    /* ignore */
  }
  rendition = null
  book = null
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = ''
  }
  pdfSrc.value = ''
  videoSrc.value = ''
  bookTitle.value = ''
  chapterLabel.value = ''
  tocItems.value = []
  activeTocId.value = ''
  tocOpen.value = true
  pdfTocItems.value = []
  pdfActiveTocId.value = ''
  pdfChapterLabel.value = ''
  pdfTocOpen.value = true
  pdfFrameKey.value = 0
  pdfDoc = null
  pdfBlobBase = ''
  textContent.value = ''
  textSaved.value = ''
  textSaving.value = false
  textEditing.value = false
  textMode.value = 'plain'
  paneLayout.value = 'split'
  excelSheets.value = []
  excelSheetIndex.value = 0
  pptSlides.value = []
  pptIndex.value = 0
  pptImgLoading.value = false
  pptImgError.value = ''
  if (epubViewerRef.value) {
    epubViewerRef.value.innerHTML = ''
  }
}

function cleanup() {
  resetViewer()
  error.value = ''
  loading.value = false
  siblings.value = []
  currentFileId.value = ''
  playingName.value = ''
  listOpen.value = true
}

function onClosed() {
  cleanup()
}

async function onWindowVisible(open: boolean) {
  if (!open) {
    const ok = await confirmDiscardIfDirty()
    if (!ok) return
  }
  emit('update:modelValue', open)
}

async function confirmDiscardIfDirty() {
  if (!textDirty.value) return true
  try {
    await ElMessageBox.confirm('内容尚未保存，确定放弃修改？', '未保存', {
      type: 'warning',
      confirmButtonText: '放弃',
      cancelButtonText: '继续编辑',
    })
    return true
  } catch {
    return false
  }
}

async function onOpened() {
  await load()
}

function onWindowResized() {
  nextTick(() => {
    try {
      rendition?.resize()
    } catch {
      /* ignore */
    }
  })
}

function toggleToc() {
  tocOpen.value = !tocOpen.value
  nextTick(() => {
    try {
      rendition?.resize()
    } catch {
      /* ignore */
    }
  })
}

function flattenToc(nodes: any[], level = 0, out: TocItem[] = []): TocItem[] {
  for (const n of nodes || []) {
    const label = fixMojibake(String(n.label || n.title || '').replace(/\s+/g, ' ').trim())
    const href = String(n.href || '')
    if (label && href) {
      out.push({
        id: `${level}-${out.length}-${href}`,
        label,
        href,
        level: Math.min(level, 3),
      })
    }
    if (n.subitems?.length) flattenToc(n.subitems, level + 1, out)
    else if (n.children?.length) flattenToc(n.children, level + 1, out)
  }
  return out
}

async function buildToc() {
  const items: TocItem[] = []
  try {
    const nav = await (book as any).loaded.navigation
    const toc = nav?.toc || (book as any).navigation?.toc || []
    flattenToc(toc, 0, items)
  } catch {
    /* ignore */
  }
  if (!items.length) {
    try {
      const spine = (book as any).spine
      const spineItems = spine?.items || spine?.spineItems || []
      spineItems.forEach((s: any, idx: number) => {
        const href = s.href || s.url || ''
        if (!href) return
        const label = s.idref || s.id || `第 ${idx + 1} 章`
        items.push({
          id: `spine-${idx}`,
          label: fixMojibake(String(label)),
          href,
          level: 0,
        })
      })
    } catch {
      /* ignore */
    }
  }
  tocItems.value = items
}

function matchTocByHref(href: string | undefined): TocItem | undefined {
  if (!href || !tocItems.value.length) return undefined
  const clean = href.split('#')[0]
  const list = tocItems.value
  let best: TocItem | undefined
  for (const item of list) {
    const ih = item.href.split('#')[0]
    if (!ih) continue
    if (clean === ih || clean.endsWith(ih) || ih.endsWith(clean) || clean.includes(ih) || ih.includes(clean)) {
      best = item
    }
  }
  return best
}

function syncActiveToc(href?: string) {
  const hit = matchTocByHref(href)
  if (hit) {
    activeTocId.value = hit.id
    chapterLabel.value = hit.label
  }
}

async function goToc(item: TocItem) {
  if (!rendition) return
  try {
    await rendition.display(item.href)
    activeTocId.value = item.id
    chapterLabel.value = item.label
  } catch (e) {
    console.warn('toc jump failed', e)
  }
}

async function waitForEpubEl(seq: number, tries = 30): Promise<HTMLElement> {
  for (let i = 0; i < tries; i++) {
    if (seq !== loadSeq) throw new Error('cancelled')
    const el = epubViewerRef.value
    if (el && el.clientWidth > 0 && el.clientHeight > 0) {
      return el
    }
    await nextTick()
    await new Promise((r) => requestAnimationFrame(() => r(null)))
  }
  if (epubViewerRef.value) return epubViewerRef.value
  throw new Error('阅读器容器未就绪，请关闭后重试')
}

function togglePdfToc() {
  pdfTocOpen.value = !pdfTocOpen.value
}

function flattenPdfOutline(nodes: any[], level = 0, out: PdfTocItem[] = []): PdfTocItem[] {
  for (const n of nodes || []) {
    const label = fixMojibake(String(n.title || '').replace(/\s+/g, ' ').trim())
    if (label) {
      out.push({
        id: `${level}-${out.length}-${label}`,
        label,
        page: 0,
        level: Math.min(level, 3),
      })
      // page filled later async
      ;(out[out.length - 1] as any)._dest = n.dest
    }
    if (n.items?.length) flattenPdfOutline(n.items, level + 1, out)
  }
  return out
}

async function resolvePdfOutlinePages(doc: any, items: PdfTocItem[]) {
  for (const item of items) {
    const dest = (item as any)._dest
    try {
      let explicit = dest
      if (typeof dest === 'string') {
        explicit = await doc.getDestination(dest)
      } else if (dest && typeof dest.then === 'function') {
        explicit = await dest
      }
      if (!Array.isArray(explicit) || explicit.length === 0) {
        item.page = 0
        continue
      }
      const ref = explicit[0]
      if (typeof ref === 'object' && ref !== null) {
        item.page = (await doc.getPageIndex(ref)) + 1
      } else if (Number.isInteger(ref)) {
        // 部分 PDF 直接用 0-based 页码
        item.page = (ref as number) + 1
      } else {
        item.page = 0
      }
    } catch {
      item.page = 0
    }
    delete (item as any)._dest
  }
}

async function goPdfToc(item: PdfTocItem) {
  if (!pdfBlobBase || !item.page) return
  pdfActiveTocId.value = item.id
  pdfChapterLabel.value = item.label
  // Chrome 内置 PDF 查看器对同一 blob 只改 hash 不会跳页，需重建 iframe
  pdfSrc.value = ''
  await nextTick()
  pdfFrameKey.value += 1
  pdfSrc.value = `${pdfBlobBase}#page=${item.page}&toolbar=1&navpanes=0`
}

async function loadPdf() {
  const buf = await fetchPdfPreview(activeId(), props.kind || 'pdf')
  if (!buf.byteLength) throw new Error('PDF 内容为空')

  const title = displayTitle.value.endsWith('.pdf') ? displayTitle.value : `${displayTitle.value}`
  const fixed = await pdfBytesWithTitle(buf, title)
  // 拷到独立 ArrayBuffer，避免 Uint8Array<ArrayBufferLike> 与 BlobPart 不兼容
  const pdfBytes = new Uint8Array(fixed.byteLength)
  pdfBytes.set(fixed)
  // 使用带文件名的 File，部分浏览器会用其作为显示名
  const file = new File([pdfBytes], title.endsWith('.pdf') ? title : `${title}.pdf`, {
    type: 'application/pdf',
  })
  objectUrl = URL.createObjectURL(file)
  pdfBlobBase = objectUrl
  // navpanes=0 隐藏侧栏；标题已写入 PDF 元数据
  pdfSrc.value = `${objectUrl}#toolbar=1&navpanes=0`

  try {
    pdfDoc = await pdfjs.getDocument({ data: fixed }).promise
    const outline = await pdfDoc.getOutline()
    const items = flattenPdfOutline(outline || [])
    await resolvePdfOutlinePages(pdfDoc, items)
    pdfTocItems.value = items.filter((i) => i.page > 0)
    if (!pdfTocItems.value.length && pdfDoc.numPages > 0) {
      // 无大纲时用页码列表兜底
      pdfTocItems.value = Array.from({ length: Math.min(pdfDoc.numPages, 200) }, (_, i) => ({
        id: `page-${i + 1}`,
        label: `第 ${i + 1} 页`,
        page: i + 1,
        level: 0,
      }))
    }
  } catch (e) {
    console.warn('pdf outline failed', e)
    pdfTocItems.value = []
  }
}

async function loadVideo() {
  const id = activeId()
  // 与 PDF iframe 分离，避免隐藏 iframe 同时拉视频并出声
  videoSrc.value = previewResourceUrl(id, auth.token)
  window.addEventListener('keydown', onKey)
}

async function loadSiblings() {
  currentFileId.value = props.fileId
  playingName.value = props.title
  try {
    const data = await getSiblings(props.fileId)
    const cat = data.category || ''
    const withThumb = cat === 'image' || cat === 'video' || cat === 'pdf' || cat === 'ppt'
    siblings.value = (data.items || []).map((item) => ({
      fileId: item.fileId,
      fileName: item.fileName,
      thumb: withThumb ? previewThumbUrl(item.fileId, auth.token) : undefined,
    }))
    const current =
      siblings.value.find((v) => v.fileId === props.fileId) || siblings.value[data.index] || siblings.value[0]
    if (current) {
      currentFileId.value = current.fileId
      playingName.value = current.fileName
    }
  } catch {
    siblings.value = props.fileId ? [{ fileId: props.fileId, fileName: props.title }] : []
  }
  listOpen.value = siblings.value.length > 1
}

async function loadCurrentContent(seq: number) {
  if (props.type === 'pdf') await loadPdf()
  else if (props.type === 'video') await loadVideo()
  else if (props.type === 'epub') await loadEpub(seq)
  else if (props.type === 'text') await loadText()
  else if (props.type === 'excel') await loadExcel()
  else if (props.type === 'ppt') await loadPpt()
}

async function openSibling(item: SiblingItem) {
  if (!item?.fileId || item.fileId === currentFileId.value) return
  if (!(await confirmDiscardIfDirty())) return
  const seq = ++loadSeq
  resetViewer()
  currentFileId.value = item.fileId
  playingName.value = item.fileName
  loading.value = true
  error.value = ''
  try {
    await loadCurrentContent(seq)
    if (seq !== loadSeq) return
    loading.value = false
    if (props.type === 'video') await playVideoOnce()
  } catch (e: any) {
    if (e?.message === 'cancelled' || seq !== loadSeq) return
    loading.value = false
    error.value = e?.message || '打开失败'
  }
}

async function playVideoOnce() {
  await nextTick()
  const v = videoRef.value
  if (!v || props.type !== 'video') return
  bindVideoVolume(v)
  applyPlaybackRate()
  try {
    await v.play()
  } catch {
    /* 浏览器可能拦截自动播放，用户可手动点播放 */
  }
}

function formatRate(r: number) {
  return r === 1 ? '1x' : `${r}x`
}

function applyPlaybackRate() {
  const v = videoRef.value
  if (!v) return
  try {
    v.playbackRate = playbackRate.value
  } catch {
    /* ignore */
  }
}

function setPlaybackRate(rate: number) {
  playbackRate.value = rate
  applyPlaybackRate()
}

function seekVideoBy(deltaSec: number) {
  const v = videoRef.value
  if (!v || props.type !== 'video') return
  const duration = Number.isFinite(v.duration) ? v.duration : NaN
  let next = (v.currentTime || 0) + deltaSec
  if (Number.isFinite(duration) && duration > 0) {
    next = Math.min(Math.max(0, next), Math.max(0, duration - 0.05))
  } else {
    next = Math.max(0, next)
  }
  try {
    v.currentTime = next
  } catch {
    /* ignore */
  }
}

function changeVideoVolume(delta: number) {
  const v = videoRef.value
  if (!v || props.type !== 'video') return
  if (v.muted && delta > 0) {
    v.muted = false
    if (v.volume <= 0) v.volume = Math.min(1, delta)
    return
  }
  const next = Math.min(1, Math.max(0, v.volume + delta))
  v.volume = next
  v.muted = next === 0
}

async function loadEpub(seq: number) {
  const buf = await fetchPreviewResource(activeId())
  if (!buf || buf.byteLength < 100) {
    throw new Error('EPUB 文件无效或为空')
  }
  const head = new Uint8Array(buf.slice(0, 4))
  if (!(head[0] === 0x50 && head[1] === 0x4b)) {
    throw new Error('不是有效的 EPUB（ZIP）文件')
  }

  loading.value = false
  const el = await waitForEpubEl(seq)
  el.innerHTML = ''

  const openEpub = (ePub as unknown as { default?: typeof ePub }).default ?? ePub
  book = openEpub(buf)

  try {
    await Promise.race([
      book.ready,
      new Promise((_, reject) => setTimeout(() => reject(new Error('EPUB 解析超时，请确认文件未损坏')), 30000)),
    ])
  } catch (e: any) {
    throw new Error(e?.message || 'EPUB 解析失败')
  }
  if (seq !== loadSeq) return

  try {
    const meta = await book.loaded.metadata
    const t = (meta?.title || '').trim()
    if (t) bookTitle.value = fixMojibake(t)
  } catch {
    /* ignore metadata */
  }

  await buildToc()

  const render = async (flow: 'paginated' | 'scrolled-doc') => {
    epubFlow = flow
    rendition = book!.renderTo(el, {
      width: '100%',
      height: '100%',
      flow,
      allowScriptedContent: true,
    })
    rendition.on('relocated', (loc: any) => {
      syncActiveToc(loc?.start?.href)
    })
    bindEpubContentNav()
    await rendition.display()
    syncActiveToc((rendition as any)?.location?.start?.href)
  }

  try {
    await render('paginated')
  } catch (e1) {
    console.warn('paginated render failed, fallback scrolled', e1)
    try {
      rendition?.destroy()
    } catch {
      /* ignore */
    }
    el.innerHTML = ''
    await render('scrolled-doc')
  }

  el.addEventListener('wheel', onEpubWheel, { passive: false })
  el.addEventListener('click', onEpubTapTurn)
  window.addEventListener('keydown', onKey)
}

function bindEpubContentNav() {
  if (!rendition) return
  // EPUB 内容在 iframe 内，需挂到章节文档上才能收到滚轮
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
}

function turnEpub(dir: 1 | -1) {
  if (!rendition || epubFlow !== 'paginated') return
  void (dir > 0 ? rendition.next() : rendition.prev())
}

function onEpubWheel(e: WheelEvent) {
  if (epubFlow !== 'paginated' || !rendition) return
  // 横向手势交给浏览器；纵向滚轮翻页
  if (Math.abs(e.deltaX) > Math.abs(e.deltaY)) return
  e.preventDefault()
  e.stopPropagation()
  if (wheelLock) return

  // deltaMode: 0=pixel, 1=line, 2=page
  let delta = e.deltaY
  if (e.deltaMode === 1) delta *= 16
  else if (e.deltaMode === 2) delta *= WHEEL_THRESHOLD

  wheelAcc += delta
  if (Math.abs(wheelAcc) < WHEEL_THRESHOLD) return

  const dir: 1 | -1 = wheelAcc > 0 ? 1 : -1
  wheelAcc = 0
  wheelLock = true
  turnEpub(dir)
  if (wheelUnlockTimer) clearTimeout(wheelUnlockTimer)
  wheelUnlockTimer = setTimeout(() => {
    wheelLock = false
    wheelAcc = 0
    wheelUnlockTimer = null
  }, WHEEL_COOLDOWN_MS)
}

function onEpubTapTurn(e: MouseEvent, win?: Window) {
  if (epubFlow !== 'paginated' || !rendition) return
  const target = e.target as HTMLElement | null
  if (!target) return
  if (target.closest?.('a, button, input, textarea, select, summary, label')) return
  const selection = (win || window).getSelection?.()
  if (selection && !selection.isCollapsed && String(selection).trim()) return

  const viewer = epubViewerRef.value
  if (!viewer) return
  const rect = viewer.getBoundingClientRect()
  // iframe 内坐标需换算到视口
  let clientX = e.clientX
  if (win && win !== window) {
    const frame = win.frameElement as HTMLElement | null
    if (frame) {
      const fr = frame.getBoundingClientRect()
      clientX = fr.left + e.clientX
    }
  }
  const ratio = (clientX - rect.left) / Math.max(rect.width, 1)
  if (ratio < 0.28) turnEpub(-1)
  else if (ratio > 0.72) turnEpub(1)
}

function epubPrev() {
  turnEpub(-1)
}
function epubNext() {
  turnEpub(1)
}
function onKey(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && (e.key === 's' || e.key === 'S')) {
    if (props.type === 'text' && textEditing.value && canEditText.value) {
      e.preventDefault()
      void saveText()
    }
    return
  }
  const tag = (e.target as HTMLElement | null)?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return
  if (props.type === 'video') {
    if (e.key === 'ArrowLeft') {
      e.preventDefault()
      seekVideoBy(e.shiftKey ? -30 : -5)
    } else if (e.key === 'ArrowRight') {
      e.preventDefault()
      seekVideoBy(e.shiftKey ? 30 : 5)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      changeVideoVolume(e.shiftKey ? 0.1 : 0.05)
    } else if (e.key === 'ArrowDown') {
      e.preventDefault()
      changeVideoVolume(e.shiftKey ? -0.1 : -0.05)
    } else if (e.key === ' ' || e.key === 'Spacebar') {
      e.preventDefault()
      const v = videoRef.value
      if (!v) return
      if (v.paused) void v.play().catch(() => undefined)
      else v.pause()
    }
    return
  }
  if (props.type === 'ppt') {
    if (e.key === 'ArrowLeft' || e.key === 'ArrowUp' || e.key === 'PageUp') {
      e.preventDefault()
      pptPrev()
    } else if (
      e.key === 'ArrowRight' ||
      e.key === 'ArrowDown' ||
      e.key === 'PageDown' ||
      e.key === ' ' ||
      e.key === 'Spacebar'
    ) {
      e.preventDefault()
      pptNext()
    }
    return
  }
  if (props.type !== 'epub' || epubFlow !== 'paginated') return
  if (e.key === 'ArrowLeft' || e.key === 'ArrowUp' || e.key === 'PageUp') {
    e.preventDefault()
    epubPrev()
  } else if (
    e.key === 'ArrowRight' ||
    e.key === 'ArrowDown' ||
    e.key === 'PageDown' ||
    e.key === ' ' ||
    e.key === 'Spacebar'
  ) {
    e.preventDefault()
    epubNext()
  }
}

async function loadText() {
  const buf = await fetchPreviewResource(activeId())
  if (buf.byteLength > TEXT_MAX_BYTES) {
    throw new Error('文件过大（超过 5MB），请下载后查看')
  }
  textMode.value = detectTextMode(playingName.value || props.title || '')
  paneLayout.value = 'split'
  textEditing.value = false
  textContent.value = buf.byteLength ? decodeTextBytes(buf) : ''
  textSaved.value = textContent.value
}

function startEdit() {
  if (!canEditText.value) return
  textEditing.value = true
  if (textMode.value === 'html' || textMode.value === 'markdown') {
    paneLayout.value = 'split'
  }
  nextTick(() => {
    const el = document.querySelector('.preview-text .text-editor') as HTMLTextAreaElement | null
    el?.focus()
  })
}

async function cancelEdit() {
  if (!(await confirmDiscardIfDirty())) return
  textContent.value = textSaved.value
  textEditing.value = false
  if (textMode.value === 'markdown' || textMode.value === 'html') {
    paneLayout.value = 'split'
  }
}

async function saveText() {
  if (!canEditText.value || !textEditing.value || textSaving.value || !textDirty.value) return
  const bytes = new TextEncoder().encode(textContent.value)
  if (bytes.length > TEXT_MAX_BYTES) {
    ElMessage.error('文件过大（超过 5MB），无法在线保存')
    return
  }
  textSaving.value = true
  try {
    await saveTextContent(activeId(), textContent.value)
    textSaved.value = textContent.value
    ElMessage.success('已保存')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    textSaving.value = false
  }
}

function excelColLabel(index: number) {
  let n = index
  let label = ''
  while (n >= 0) {
    label = String.fromCharCode((n % 26) + 65) + label
    n = Math.floor(n / 26) - 1
  }
  return label
}

async function loadExcel() {
  const data = await getExcel(activeId())
  excelSheets.value = data?.sheets || []
  excelSheetIndex.value = 0
}

function pptSlideSrc(index: number) {
  return pptSlideUrl(activeId(), index, auth.token)
}

function goPpt(index: number) {
  if (index < 0 || index >= pptSlides.value.length || index === pptIndex.value) return
  pptImgLoading.value = true
  pptImgError.value = ''
  pptIndex.value = index
}

function pptPrev() {
  goPpt(pptIndex.value - 1)
}

function pptNext() {
  goPpt(pptIndex.value + 1)
}

function onPptImgError() {
  pptImgLoading.value = false
  pptImgError.value = '幻灯片渲染失败'
}

async function loadPpt() {
  const data = await getPpt(activeId())
  pptSlides.value = data?.slides || []
  pptIndex.value = 0
  pptImgError.value = ''
  pptImgLoading.value = pptSlides.value.length > 0
  window.addEventListener('keydown', onKey)
}

async function load() {
  const seq = ++loadSeq
  cleanup()
  if (!props.modelValue || !props.fileId) return
  loading.value = true
  error.value = ''
  try {
    await loadSiblings()
    await loadCurrentContent(seq)
  } catch (e: any) {
    if (e?.message === 'cancelled' || seq !== loadSeq) return
    console.error('preview failed', e)
    error.value = e?.message || '打开失败'
  } finally {
    if (seq === loadSeq) loading.value = false
  }
  if (seq === loadSeq && props.type === 'video' && !error.value) {
    await playVideoOnce()
  }
}
</script>

<style scoped>
.preview-title-wrap {
  min-width: 0;
  flex: 1;
}
.preview-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.25;
}
.preview-sub {
  margin-top: 1px;
  font-size: 11px;
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preview-body {
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  position: relative;
  padding: 10px 12px 12px;
}
.preview-with-list {
  flex: 1;
  min-width: 0;
  min-height: 0;
  width: 100%;
  height: 100%;
  display: flex;
  position: relative;
}
.preview-main-pane {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
}
.preview-body.is-immersive {
  padding: 0;
  background: #000;
}
.preview-body.is-immersive.is-video {
  background: #000;
}
.preview-body.is-video {
  padding: 0;
  background: #000;
}
.preview-body.is-video .preview-with-list,
.preview-body.is-video .preview-main-pane {
  background: #000;
}
.preview-body.is-video .preview-status {
  background: rgba(0, 0, 0, 0.55);
  color: #e5e7eb;
}
.preview-body.is-immersive .epub-main {
  border: 0;
  border-radius: 0;
}
.preview-body.is-immersive .preview-video {
  width: 100%;
  height: 100%;
  max-height: none;
}
.preview-frame,
.preview-video {
  flex: 1;
  width: 100%;
  min-height: 0;
  border: 0;
  background: #525659;
}
.preview-video-layout {
  flex: 1;
  min-width: 0;
  min-height: 0;
  width: 100%;
  height: 100%;
  display: flex;
  position: relative;
  background: #000;
}
.preview-video-stage {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
  background: #000;
}
.preview-video-stage .preview-status {
  background: rgba(0, 0, 0, 0.55);
  color: #e5e7eb;
}
.video-rate-bar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: rgba(15, 23, 42, 0.92);
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  z-index: 2;
}
.video-rate-label {
  font-size: 12px;
  color: #94a3b8;
  margin-right: 2px;
  user-select: none;
}
.video-rate-btn {
  appearance: none;
  border: 1px solid transparent;
  background: transparent;
  color: #cbd5e1;
  font-size: 12px;
  line-height: 1;
  padding: 5px 9px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.12s ease, color 0.12s ease, border-color 0.12s ease;
}
.video-rate-btn:hover {
  background: rgba(148, 163, 184, 0.18);
  color: #f8fafc;
}
.video-rate-btn.active {
  background: rgba(59, 130, 246, 0.28);
  border-color: rgba(96, 165, 250, 0.55);
  color: #93c5fd;
  font-weight: 600;
}
.preview-video {
  background: #000;
  object-fit: contain;
}
.preview-pdf,
.preview-epub {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.epub-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}
.epub-chapter {
  color: #6b7280;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.epub-main {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 0;
  border: 1px solid #dbe3ec;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}
.epub-toc {
  width: 220px;
  flex-shrink: 0;
  border-right: 1px solid #e5e7eb;
  background: #f8fafc;
  overflow: auto;
  padding: 8px 0;
}
.epub-toc-title {
  padding: 4px 14px 10px;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}
.epub-toc-empty {
  padding: 8px 14px;
  color: #9ca3af;
  font-size: 12px;
}
.epub-toc-item {
  display: block;
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 8px 14px;
  font-size: 13px;
  line-height: 1.4;
  color: #4b5563;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.epub-toc-item:hover {
  background: #eef2f7;
  color: #111827;
}
.epub-toc-item.active {
  background: #e6f4f1;
  color: #0f766e;
  font-weight: 600;
}
.epub-toc-item.level-1 {
  padding-left: 24px;
}
.epub-toc-item.level-2 {
  padding-left: 34px;
}
.epub-toc-item.level-3 {
  padding-left: 44px;
}
.epub-viewer {
  flex: 1;
  min-width: 0;
  min-height: 0;
  background: #fff;
  overflow: hidden;
}
.pdf-frame {
  border: 0;
}
.preview-excel {
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
}
.excel-tabs {
  flex: 0 0 auto;
  display: flex;
  gap: 2px;
  overflow-x: auto;
  padding: 6px 8px 0;
  background: #f3f4f6;
  border-bottom: 1px solid #d1d5db;
}
.excel-tab {
  flex: 0 0 auto;
  border: 1px solid transparent;
  border-bottom: 0;
  border-radius: 6px 6px 0 0;
  background: transparent;
  padding: 6px 12px;
  font-size: 12px;
  color: #4b5563;
  cursor: pointer;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.excel-tab:hover {
  background: #e5e7eb;
  color: #111827;
}
.excel-tab.active {
  background: #fff;
  border-color: #d1d5db;
  color: #111827;
  font-weight: 600;
}
.excel-hint {
  flex: 0 0 auto;
  padding: 6px 12px;
  font-size: 12px;
  color: #b45309;
  background: #fffbeb;
  border-bottom: 1px solid #fde68a;
}
.excel-grid-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: #fff;
}
.excel-empty {
  padding: 40px 16px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}
.excel-grid {
  border-collapse: collapse;
  font-size: 12px;
  line-height: 1.35;
  color: #111827;
  min-width: 100%;
}
.excel-grid th,
.excel-grid td {
  border: 1px solid #d1d5db;
  padding: 4px 8px;
  white-space: nowrap;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: top;
}
.excel-grid thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: #f3f4f6;
  font-weight: 600;
  text-align: center;
  color: #4b5563;
  min-width: 72px;
}
.excel-grid tbody th,
.excel-grid .excel-corner {
  position: sticky;
  left: 0;
  z-index: 1;
  background: #f3f4f6;
  font-weight: 600;
  text-align: center;
  color: #6b7280;
  min-width: 40px;
  max-width: 48px;
}
.excel-grid .excel-corner {
  z-index: 3;
  left: 0;
  top: 0;
}
.excel-grid tbody tr:nth-child(even) td {
  background: #fafafa;
}
.preview-ppt {
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #111827;
}
.ppt-toolbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  background: #1f2937;
  color: #e5e7eb;
}
.ppt-page {
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  color: #d1d5db;
  min-width: 64px;
  text-align: center;
}
.ppt-slide-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: #9ca3af;
}
.ppt-stage {
  flex: 1;
  min-height: 0;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 20px;
  background: #111827;
}
.ppt-stage .preview-status {
  background: rgba(17, 24, 39, 0.55);
  color: #e5e7eb;
}
.ppt-stage .preview-status.error {
  background: #111827;
  color: #fca5a5;
}
.ppt-slide-img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.45);
  background: #fff;
}
.ppt-thumbs {
  flex: 0 0 auto;
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding: 8px 10px 10px;
  background: #1f2937;
}
.ppt-thumb {
  flex: 0 0 auto;
  min-width: 36px;
  height: 28px;
  border: 1px solid #374151;
  border-radius: 4px;
  background: #111827;
  color: #9ca3af;
  font-size: 12px;
  cursor: pointer;
}
.ppt-thumb:hover {
  border-color: #6b7280;
  color: #e5e7eb;
}
.ppt-thumb.active {
  background: #134e4a;
  border-color: #14b8a6;
  color: #99f6e4;
  font-weight: 600;
}
.preview-status {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
  background: rgba(255, 255, 255, 0.72);
  color: #6b7280;
  pointer-events: none;
}
.preview-status.error {
  color: #b91c1c;
  pointer-events: auto;
  background: #fff;
}
.preview-text {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}
.md-toolbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 1px solid #e5e7eb;
  background: #f8fafc;
}
.text-edit-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
}
.text-edit-status {
  font-size: 12px;
  color: #64748b;
}
.md-panes {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}
.md-panes.layout-source .md-source,
.md-panes.layout-preview .md-preview,
.md-panes.layout-preview .text-html-frame {
  flex: 1;
}
.md-panes.layout-split .md-source,
.md-panes.layout-split .md-preview,
.md-panes.layout-split .text-html-frame {
  flex: 1;
  min-width: 0;
  width: 50%;
}
.md-source,
.md-preview {
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
}
.md-source {
  background: #fafbfc;
}
.md-source-pre {
  color: #24292f;
  background: transparent;
}
.md-split-line {
  flex: 0 0 1px;
  background: #e5e7eb;
}
.md-preview {
  background: #fff;
}
.text-pre {
  margin: 0;
  padding: 14px 16px;
  flex: 1;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  line-height: 1.55;
  color: #1f2937;
  white-space: pre-wrap;
  word-break: break-word;
}
.text-pre code {
  font-family: inherit;
}
.text-editor {
  margin: 0;
  padding: 14px 16px;
  flex: 1;
  width: 100%;
  min-height: 0;
  border: 0;
  resize: none;
  outline: none;
  box-sizing: border-box;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  line-height: 1.55;
  color: #1f2937;
  background: transparent;
  white-space: pre-wrap;
  word-break: break-word;
}
.text-html-frame {
  flex: 1;
  width: 100%;
  min-height: 0;
  border: 0;
  background: #fff;
}
.text-md {
  flex: 1;
  overflow: auto;
  padding: 20px 28px 32px;
  font-size: 15px;
  line-height: 1.7;
  color: #24292f;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans', Helvetica, Arial, sans-serif;
}
.text-md :deep(h1),
.text-md :deep(h2),
.text-md :deep(h3),
.text-md :deep(h4),
.text-md :deep(h5),
.text-md :deep(h6) {
  margin: 1.25em 0 0.6em;
  font-weight: 600;
  line-height: 1.25;
}
.text-md :deep(h1) {
  font-size: 2em;
  padding-bottom: 0.3em;
  border-bottom: 1px solid #d0d7de;
}
.text-md :deep(h2) {
  font-size: 1.5em;
  padding-bottom: 0.3em;
  border-bottom: 1px solid #d0d7de;
}
.text-md :deep(h3) { font-size: 1.25em; }
.text-md :deep(h4) { font-size: 1em; }
.text-md :deep(p) { margin: 0.8em 0; }
.text-md :deep(ul),
.text-md :deep(ol) {
  margin: 0.6em 0;
  padding-left: 2em;
}
.text-md :deep(li) { margin: 0.25em 0; }
.text-md :deep(li + li) { margin-top: 0.25em; }
.text-md :deep(blockquote) {
  margin: 0.8em 0;
  padding: 0 1em;
  border-left: 0.25em solid #d0d7de;
  color: #656d76;
}
.text-md :deep(hr) {
  height: 0.25em;
  margin: 1.5em 0;
  background: #d0d7de;
  border: 0;
}
.text-md :deep(table) {
  border-collapse: collapse;
  margin: 1em 0;
  width: 100%;
  overflow: auto;
  display: block;
}
.text-md :deep(th),
.text-md :deep(td) {
  border: 1px solid #d0d7de;
  padding: 6px 13px;
}
.text-md :deep(th) {
  background: #f6f8fa;
  font-weight: 600;
}
.text-md :deep(tr:nth-child(2n)) {
  background: #f6f8fa;
}
.text-md :deep(img) {
  max-width: 100%;
  height: auto;
}
.text-md :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.85em;
  background: rgba(175, 184, 193, 0.2);
  padding: 0.2em 0.4em;
  border-radius: 6px;
}
.text-md :deep(pre) {
  margin: 1em 0;
  padding: 14px 16px;
  overflow: auto;
  background: #f6f8fa;
  border-radius: 8px;
  line-height: 1.45;
}
.text-md :deep(pre code) {
  background: transparent;
  padding: 0;
  font-size: 0.9em;
  color: inherit;
}
.text-md :deep(a) {
  color: #0969da;
  text-decoration: none;
}
.text-md :deep(a:hover) {
  text-decoration: underline;
}
@media (max-width: 800px) {
  .md-panes.layout-split {
    flex-direction: column;
  }
  .md-panes.layout-split .md-source,
  .md-panes.layout-split .md-preview {
    width: 100%;
    flex: 1;
    max-height: 50%;
  }
  .md-split-line {
    height: 1px;
    width: 100%;
  }
}
</style>

<style>
.preview-body .epub-viewer iframe {
  border: 0;
}
</style>

