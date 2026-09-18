import { apiUrl } from './prefix'
import { paths } from './paths'

function withToken(path: string, token?: string | null) {
  return token ? `${apiUrl(path)}?token=${encodeURIComponent(token)}` : apiUrl(path)
}

/** 给 <img> / <video> / iframe 用的带前缀 URL（无法设 Authorization 时传 token） */
export function previewResourceUrl(fileId: string, token?: string | null) {
  return withToken(paths.preview.resource(fileId), token)
}

export function pdfPreviewUrl(fileId: string, kind: string = 'pdf') {
  if (kind === 'txt') return apiUrl(paths.preview.txtPdf(fileId))
  if (kind === 'office') return apiUrl(paths.preview.officePdf(fileId))
  return apiUrl(paths.preview.pdf(fileId))
}

export function pptSlideUrl(fileId: string, index: number, token?: string | null) {
  return withToken(paths.preview.pptSlide(fileId, index), token)
}

export function downloadUrl(fileId: string) {
  return apiUrl(paths.files.download(fileId))
}

export function chainShareUrl(key: string) {
  return `${location.origin}${apiUrl(paths.links.chainPublic(key))}`
}

export function downloadKeyShareUrl(key: string) {
  return `${location.origin}${apiUrl(paths.links.downloadPublic(key))}`
}

export function isPreviewResourceUrl(src: string, fileId: string) {
  return src.includes(apiUrl('/preview/resource/')) && src.includes(fileId)
}

export function mediaSrc(item: { fileId: string; url?: string }, token?: string | null) {
  const u = item.url || ''
  if (u.startsWith('http') || u.startsWith('blob:') || u.startsWith('data:')) return u
  if (u.startsWith('/')) return u
  return previewResourceUrl(item.fileId, token)
}
