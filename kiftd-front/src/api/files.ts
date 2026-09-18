import http, { getArrayBuffer, type ApiResponse } from './http'
import { paths } from './paths'
import { saveBlob, assertDownloadBlob } from '@/utils/saveBlob'
import { downloadUrl } from './urls'
import { useAuthStore } from '@/stores/auth'

export interface Folder {
  folderId: string
  folderName: string
  folderCreationDate: string
  folderCreator: string
  folderParent: string | null
  folderConstraint: number
}

export interface FileNode {
  fileId: string
  fileName: string
  fileSize: string
  fileParentFolder: string
  fileCreationDate: string
  fileCreator: string
  filePath: string
}

export interface FolderView {
  folder: Folder
  parentList: Folder[]
  folderList: Folder[]
  fileList: FileNode[]
  account: string | null
  authList: string
  publishTime: string
  allowDownload: boolean
  foldersOffset: number
  filesOffset: number
  selectStep: number
}

export async function getFolderView(fid = 'root') {
  const { data } = await http.get<ApiResponse<FolderView>>(paths.folders.view, { params: { fid } })
  return data.data
}

export async function getRemaining(fid: string, folderOffset: number, fileOffset: number) {
  const { data } = await http.get<ApiResponse<{ folderList: Folder[]; fileList: FileNode[] }>>(paths.folders.remaining, {
    params: { fid, folderOffset, fileOffset },
  })
  return data.data
}

export async function createFolder(parentId: string, folderName: string, constraint = 0) {
  const { data } = await http.post<ApiResponse<Folder>>(paths.folders.create, { parentId, folderName, constraint })
  return data.data
}

export async function renameFolder(folderId: string, newName: string, constraint?: number) {
  await http.put(paths.folders.one(folderId), { folderId, newName, constraint })
}

export async function deleteFolder(folderId: string) {
  await http.delete(paths.folders.one(folderId))
}

export async function countFolder(folderId: string) {
  const { data } = await http.get<ApiResponse<{ folderCount: number; fileCount: number; totalSize: string }>>(
    paths.folders.count(folderId),
  )
  return data.data
}

export async function searchAll(keyword: string) {
  const { data } = await http.get<ApiResponse<{ folders: Folder[]; files: FileNode[] }>>(paths.folders.search, {
    params: { keyword },
  })
  return data.data
}

export async function checkUpload(folderId: string, fileNames: string[]) {
  const { data } = await http.post<ApiResponse<{ ok: boolean; overlaps: string[]; uploadKey: string }>>(
    paths.files.checkUpload,
    { folderId, fileNames },
  )
  return data.data
}

export async function uploadFile(folderId: string, file: File, uploadKey: string, overwrite = false, onProgress?: (p: number) => void) {
  const form = new FormData()
  form.append('file', file)
  form.append('folderId', folderId)
  form.append('uploadKey', uploadKey)
  form.append('overwrite', String(overwrite))
  const { data } = await http.post<ApiResponse<FileNode>>(paths.files.upload, form, {
    onUploadProgress: (e) => {
      if (e.total && onProgress) onProgress(Math.round((e.loaded / e.total) * 100))
    },
  })
  return data.data
}

export async function renameFile(fileId: string, newName: string) {
  await http.put(paths.files.one(fileId), { fileId, newName })
}

export async function saveTextContent(fileId: string, content: string) {
  const { data } = await http.put<ApiResponse<FileNode>>(paths.files.content(fileId), { content })
  return data.data
}

export async function deleteFile(fileId: string) {
  await http.delete(paths.files.one(fileId))
}

export async function batchDelete(fileIds: string[], folderIds: string[]) {
  await http.post(paths.files.batchDelete, { fileIds, folderIds })
}

export async function downloadFile(fileId: string, fileName = '') {
  // 用 fetch 拉二进制，避开 axios 对 blob 的边角问题，并给出更明确的空内容诊断
  const auth = useAuthStore()
  const headers: HeadersInit = {}
  if (auth.token) headers.Authorization = `Bearer ${auth.token}`

  const res = await fetch(downloadUrl(fileId), { headers, credentials: 'same-origin' })
  if (!res.ok) {
    let detail = `HTTP ${res.status}`
    try {
      const text = await res.text()
      const json = JSON.parse(text) as { message?: string }
      if (json.message) detail = json.message
    } catch {
      /* ignore */
    }
    throw new Error(`下载失败：${detail}`)
  }

  const buf = await res.arrayBuffer()
  const contentLength = res.headers.get('content-length')
  if (buf.byteLength === 0) {
    if (contentLength === '0') {
      throw new Error('文件大小为 0 字节（可能上传时未写入内容）')
    }
    throw new Error(
      '下载内容为空（响应体 0 字节）。若刚停过迅雷服务请重试；仍失败请检查服务端文件块是否存在。',
    )
  }

  const blob = await assertDownloadBlob(new Blob([buf], { type: 'application/octet-stream' }))
  const fromHeader = parseContentDispositionFileName(res.headers.get('content-disposition'))
  const name = (fileName || fromHeader || 'download').trim() || 'download'
  return saveBlob(blob, name)
}

function parseContentDispositionFileName(header: unknown): string {
  if (typeof header !== 'string' || !header) return ''
  // filename*=UTF-8''...
  const star = /filename\*\s*=\s*(?:UTF-8''|utf-8'')([^;]+)/i.exec(header)
  if (star?.[1]) {
    try {
      return decodeURIComponent(star[1].trim().replace(/^["']|["']$/g, ''))
    } catch {
      /* ignore */
    }
  }
  const plain = /filename\s*=\s*([^;]+)/i.exec(header)
  if (plain?.[1]) {
    return plain[1].trim().replace(/^["']|["']$/g, '')
  }
  return ''
}

export async function confirmMove(targetFolderId: string, fileIds: string[], folderIds: string[]) {
  const { data } = await http.post<ApiResponse<{ conflictFiles: string[]; conflictFolders: string[] }>>(
    paths.files.confirmMove,
    { targetFolderId, fileIds, folderIds, copy: false },
  )
  return data.data
}

export async function moveItems(payload: {
  targetFolderId: string
  fileIds: string[]
  folderIds: string[]
  copy: boolean
  conflictStrategy?: Record<string, string>
}) {
  await http.post(paths.files.move, payload)
}

export async function createChain(fileId: string) {
  const { data } = await http.post<ApiResponse<{ chainKey: string }>>(paths.links.chain, { fileId })
  return data.data.chainKey
}

export async function createDownloadKey(fileId: string) {
  const { data } = await http.post<ApiResponse<{ downloadKey: string }>>(paths.links.downloadKey, { fileId })
  return data.data.downloadKey
}

export async function getNotice() {
  const { data } = await http.get<ApiResponse<string>>(paths.system.notice)
  return data.data
}

export async function getOs() {
  const { data } = await http.get<ApiResponse<string>>(paths.system.os)
  return data.data
}

export async function getPictures(fileId: string) {
  const { data } = await http.get<ApiResponse<{ pictureViewList: { fileId: string; fileName: string; url: string }[]; index: number }>>(
    paths.preview.pictures,
    { params: { fileId } },
  )
  return data.data
}

export async function getAudios(folderId: string) {
  const { data } = await http.get<ApiResponse<{ fileId: string; fileName: string; url: string }[]>>(paths.preview.audios, {
    params: { folderId },
  })
  return data.data
}

export async function getVideo(fileId: string) {
  const { data } = await http.get<ApiResponse<{ fileId: string; fileName: string; needTranscode: boolean }>>(paths.preview.video, {
    params: { fileId },
  })
  return data.data
}

export async function getVideos(fileId: string) {
  const { data } = await http.get<ApiResponse<{ videoViewList: { fileId: string; fileName: string }[]; index: number }>>(
    paths.preview.videos,
    { params: { fileId } },
  )
  return data.data
}

export async function getSiblings(fileId: string) {
  const { data } = await http.get<ApiResponse<{ items: { fileId: string; fileName: string }[]; index: number; category: string }>>(
    paths.preview.siblings,
    { params: { fileId } },
  )
  return data.data
}

export async function getExcel(fileId: string) {
  const { data } = await http.get<
    ApiResponse<{ fileName: string; sheets: { name: string; rows: string[][]; truncated: boolean }[] }>
  >(paths.preview.excel, { params: { fileId } })
  return data.data
}

export async function getPpt(fileId: string) {
  const { data } = await http.get<ApiResponse<{ fileName: string; slides: { index: number; title: string }[] }>>(
    paths.preview.ppt,
    { params: { fileId } },
  )
  return data.data
}

export async function getTranscodeStatus(fileId: string) {
  const { data } = await http.get<ApiResponse<string>>(paths.preview.transcodeStatus, { params: { fileId } })
  return data.data
}

export async function fetchPreviewResource(fileId: string) {
  return getArrayBuffer(paths.preview.resource(fileId))
}

export async function fetchPdfPreview(fileId: string, kind = 'pdf') {
  const path =
    kind === 'txt' ? paths.preview.txtPdf(fileId) : kind === 'office' ? paths.preview.officePdf(fileId) : paths.preview.pdf(fileId)
  return getArrayBuffer(path)
}

export async function zipDownload(fileIds: string[]) {
  const res = await http.post(paths.files.zip, { fileIds }, { responseType: 'blob' })
  if (res.status < 200 || res.status >= 300) {
    throw new Error(`打包下载失败（HTTP ${res.status}）`)
  }
  const blob = await assertDownloadBlob(res.data)
  return saveBlob(blob, 'download.zip')
}
