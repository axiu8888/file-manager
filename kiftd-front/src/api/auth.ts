import http, { type ApiResponse } from './http'
import JSEncrypt from 'jsencrypt'

export async function getPublicKey() {
  const { data } = await http.get<ApiResponse<{ publicKey: string; time: number }>>('/auth/public-key')
  return data.data
}

export async function getCaptcha() {
  const { data } = await http.get<ApiResponse<{ captchaId: string; imageBase64: string }>>('/auth/captcha')
  return data.data
}

export async function signupEnabled() {
  const { data } = await http.get<ApiResponse<{ enabled: boolean }>>('/auth/signup-enabled')
  return data.data.enabled
}

export async function encryptPassword(plain: string) {
  const pk = await getPublicKey()
  const enc = new JSEncrypt()
  enc.setPublicKey(`-----BEGIN PUBLIC KEY-----\n${pk.publicKey}\n-----END PUBLIC KEY-----`)
  const encrypted = enc.encrypt(plain)
  if (!encrypted) throw new Error('密码加密失败')
  return encrypted
}

export async function login(account: string, password: string, captchaId?: string, captcha?: string) {
  const encryptedPwd = await encryptPassword(password)
  const { data } = await http.post<ApiResponse<{ token: string; account: string; auth: string }>>('/auth/login', {
    account,
    encryptedPwd,
    captchaId,
    captcha,
  })
  return data.data
}

export async function signup(account: string, password: string, captchaId: string, captcha: string) {
  const encryptedPwd = await encryptPassword(password)
  const { data } = await http.post<ApiResponse<{ token: string; account: string; auth: string }>>('/auth/signup', {
    account,
    encryptedPwd,
    captchaId,
    captcha,
  })
  return data.data
}

export async function changePassword(oldPwd: string, newPwd: string, captchaId?: string, captcha?: string) {
  const encryptedOldPwd = await encryptPassword(oldPwd)
  const encryptedNewPwd = await encryptPassword(newPwd)
  await http.post('/auth/change-password', { encryptedOldPwd, encryptedNewPwd, captchaId, captcha })
}
