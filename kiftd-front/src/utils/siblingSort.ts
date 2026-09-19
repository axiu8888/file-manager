export type SiblingSortBy = 'name' | 'date' | 'size'
export type SiblingSortOrder = 'asc' | 'desc'

export interface SiblingSortState {
  by: SiblingSortBy
  order: SiblingSortOrder
}

export interface SortableFileMeta {
  fileName: string
  fileCreationDate?: string
  fileSize?: string | number
}

const STORAGE_KEY = 'kiftd.previewListSort'

const DEFAULT_SORT: SiblingSortState = { by: 'date', order: 'desc' }

export function loadSiblingSort(): SiblingSortState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { ...DEFAULT_SORT }
    const parsed = JSON.parse(raw) as Partial<SiblingSortState>
    const by =
      parsed.by === 'name' || parsed.by === 'date' || parsed.by === 'size' ? parsed.by : DEFAULT_SORT.by
    const order = parsed.order === 'asc' || parsed.order === 'desc' ? parsed.order : DEFAULT_SORT.order
    return { by, order }
  } catch {
    return { ...DEFAULT_SORT }
  }
}

export function saveSiblingSort(state: SiblingSortState) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch {
    /* ignore */
  }
}

function sizeBytes(v?: string | number) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

/** 与主文件列表一致的比较器；order=asc 时供 el-table sort-method 使用（表格会再乘方向）。 */
export function compareFileMeta(a: SortableFileMeta, b: SortableFileMeta, state: SiblingSortState): number {
  const mul = state.order === 'asc' ? 1 : -1
  let cmp = 0
  if (state.by === 'date') {
    cmp = String(a.fileCreationDate || '').localeCompare(String(b.fileCreationDate || ''))
  } else if (state.by === 'size') {
    cmp = sizeBytes(a.fileSize) - sizeBytes(b.fileSize)
  } else {
    cmp = a.fileName.localeCompare(b.fileName, 'zh-CN', { numeric: true, sensitivity: 'base' })
  }
  if (cmp !== 0) return cmp * mul
  // 时间/大小相同时按名称自然序，避免和主列表对不上
  return a.fileName.localeCompare(b.fileName, 'zh-CN', { numeric: true, sensitivity: 'base' })
}

export function sortSiblingItems<T extends SortableFileMeta>(
  items: T[],
  state: SiblingSortState = loadSiblingSort(),
): T[] {
  return [...items].sort((a, b) => compareFileMeta(a, b, state))
}
