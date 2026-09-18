import http, { type ApiResponse } from './http'

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
  const { data } = await http.get<ApiResponse<FolderView>>('/folders/view', { params: { fid } })
  return data.data
}

export async function getRemaining(fid: string, folderOffset: number, fileOffset: number) {
  const { data } = await http.get<ApiResponse<{ folderList: Folder[]; fileList: FileNode[] }>>('/folders/remaining', {
    params: { fid, folderOffset, fileOffset },
  })
  return data.data
}

export async function createFolder(parentId: string, folderName: string, constraint = 0) {
  const { data } = await http.post<ApiResponse<Folder>>('/folders', { parentId, folderName, constraint })
  return data.data
}

export async function renameFolder(folderId: string, newName: string, constraint?: number) {
  await http.put(`/folders/${folderId}`, { folderId, newName, constraint })
}

export async function deleteFolder(folderId: string) {
  await http.delete(`/folders/${folderId}`)
}

export async function countFolder(folderId: string) {
  const { data } = await http.get<ApiResponse<{ folderCount: number; fileCount: number; totalSize: string }>>(
    `/folders/${folderId}/count`,
  )
  return data.data
}

export async function searchAll(keyword: string) {
  const { data } = await http.get<ApiResponse<{ folders: Folder[]; files: FileNode[] }>>('/folders/search', {
    params: { keyword },
  })
  return data.data
}

export async function checkUpload(folderId: string, fileNames: string[]) {
  const { data } = await http.post<ApiResponse<{ ok: boolean; overlaps: string[]; uploadKey: string }>>(
    '/files/check-upload',
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
  const { data } = await http.post<ApiResponse<FileNode>>('/files/upload', form, {
    onUploadProgress: (e) => {
      if (e.total && onProgress) onProgress(Math.round((e.loaded / e.total) * 100))
    },
  })
  return data.data
}

export async function renameFile(fileId: string, newName: string) {
  await http.put(`/files/${fileId}`, { fileId, newName })
}

export async function saveTextContent(fileId: string, content: string) {
  const { data } = await http.put<ApiResponse<FileNode>>(`/files/${fileId}/content`, { content })
  return data.data
}

export async function deleteFile(fileId: string) {
  await http.delete(`/files/${fileId}`)
}

export async function batchDelete(fileIds: string[], folderIds: string[]) {
  await http.post('/files/batch-delete', { fileIds, folderIds })
}

export function downloadUrl(fileId: string) {
  return `/api/files/${fileId}/download`
}

export async function confirmMove(targetFolderId: string, fileIds: string[], folderIds: string[]) {
  const { data } = await http.post<ApiResponse<{ conflictFiles: string[]; conflictFolders: string[] }>>(
    '/files/confirm-move',
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
  await http.post('/files/move', payload)
}

export async function createChain(fileId: string) {
  const { data } = await http.post<ApiResponse<{ chainKey: string }>>('/links/chain', { fileId })
  return data.data.chainKey
}

export async function createDownloadKey(fileId: string) {
  const { data } = await http.post<ApiResponse<{ downloadKey: string }>>('/links/download-key', { fileId })
  return data.data.downloadKey
}

export async function getNotice() {
  const { data } = await http.get<ApiResponse<string>>('/system/notice')
  return data.data
}

export async function getOs() {
  const { data } = await http.get<ApiResponse<string>>('/system/os')
  return data.data
}

export async function getPictures(fileId: string) {
  const { data } = await http.get<ApiResponse<{ pictureViewList: { fileId: string; fileName: string; url: string }[]; index: number }>>(
    '/preview/pictures',
    { params: { fileId } },
  )
  return data.data
}

export async function getAudios(folderId: string) {
  const { data } = await http.get<ApiResponse<{ fileId: string; fileName: string; url: string }[]>>('/preview/audios', {
    params: { folderId },
  })
  return data.data
}

export async function getVideo(fileId: string) {
  const { data } = await http.get<ApiResponse<{ fileId: string; fileName: string; needTranscode: boolean }>>('/preview/video', {
    params: { fileId },
  })
  return data.data
}

export async function getVideos(fileId: string) {
  const { data } = await http.get<ApiResponse<{ videoViewList: { fileId: string; fileName: string }[]; index: number }>>(
    '/preview/videos',
    { params: { fileId } },
  )
  return data.data
}

export async function getSiblings(fileId: string) {
  const { data } = await http.get<ApiResponse<{ items: { fileId: string; fileName: string }[]; index: number; category: string }>>(
    '/preview/siblings',
    { params: { fileId } },
  )
  return data.data
}

export async function getExcel(fileId: string) {
  const { data } = await http.get<
    ApiResponse<{ fileName: string; sheets: { name: string; rows: string[][]; truncated: boolean }[] }>
  >('/preview/excel', { params: { fileId } })
  return data.data
}

export async function getPpt(fileId: string) {
  const { data } = await http.get<ApiResponse<{ fileName: string; slides: { index: number; title: string }[] }>>(
    '/preview/ppt',
    { params: { fileId } },
  )
  return data.data
}

export async function getTranscodeStatus(fileId: string) {
  const { data } = await http.get<ApiResponse<string>>('/preview/transcode-status', { params: { fileId } })
  return data.data
}

export async function zipDownload(fileIds: string[]) {
  const res = await http.post('/files/zip', { fileIds }, { responseType: 'blob' })
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url
  a.download = 'download.zip'
  a.click()
  URL.revokeObjectURL(url)
}
