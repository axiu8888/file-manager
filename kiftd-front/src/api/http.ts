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
    return Promise.reject(err)
  },
)

export default http
