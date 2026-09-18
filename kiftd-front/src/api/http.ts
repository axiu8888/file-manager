import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import { API_PREFIX } from './prefix'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

const http = axios.create({
  baseURL: API_PREFIX,
  timeout: 600000,
})

export async function getArrayBuffer(path: string) {
  const { data } = await http.get<ArrayBuffer>(path, { responseType: 'arraybuffer' })
  return data
}

export async function getBlob(path: string) {
  const { data } = await http.get<Blob>(path, { responseType: 'blob' })
  return data
}

/** Fetch blob and preserve response headers (e.g. Content-Disposition). */
export async function getBlobResponse(path: string) {
  return http.get<Blob>(path, { responseType: 'blob' })
}

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (res) => {
    const rt = res.config.responseType
    if (rt === 'blob' || rt === 'arraybuffer') {
      return res
    }
    const data = res.data
    if (data && typeof data === 'object' && 'code' in data && data.code !== 0) {
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return res
  },
  (err) => {
    if (err.response?.status === 401) {
      const auth = useAuthStore()
      auth.logout()
      router.push({ name: 'login' })
    }
    return Promise.reject(normalizeHttpError(err))
  },
)

/** 把 axios 的 Network Error / 413 等转成可读中文 */
export function normalizeHttpError(err: unknown): Error {
  const e = err as {
    message?: string
    code?: string
    response?: { status?: number; data?: unknown }
    config?: { url?: string }
  }
  const data = e.response?.data
  if (data && typeof data === 'object' && data !== null && 'message' in data) {
    const msg = String((data as { message?: unknown }).message || '').trim()
    if (msg) return new Error(msg)
  }
  const status = e.response?.status
  if (status === 413) {
    return new Error('上传文件过大，已超过服务器或网关限制')
  }
  if (status === 502 || status === 504) {
    return new Error('网关超时或不可用，请稍后重试')
  }
  const msg = e.message || ''
  if (!e.response && (msg === 'Network Error' || e.code === 'ERR_NETWORK')) {
    const url = e.config?.url || ''
    if (url.includes('/upload') || url.includes('/files')) {
      return new Error('上传中断（网络断开或文件过大被拒绝）。若文件很大，请确认服务端已提高上传限制并已重启。')
    }
    return new Error('网络异常，请检查网络后重试')
  }
  if (err instanceof Error) return err
  return new Error(msg || '请求失败')
}

export default http
