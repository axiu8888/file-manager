import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('kiftd_token') || '')
  const account = ref(localStorage.getItem('kiftd_account') || '')
  const auth = ref(localStorage.getItem('kiftd_auth') || '')

  const isLogin = computed(() => !!token.value)

  function setSession(t: string, a: string, au: string) {
    token.value = t
    account.value = a
    auth.value = au
    localStorage.setItem('kiftd_token', t)
    localStorage.setItem('kiftd_account', a)
    localStorage.setItem('kiftd_auth', au)
  }

  function logout() {
    token.value = ''
    account.value = ''
    auth.value = ''
    localStorage.removeItem('kiftd_token')
    localStorage.removeItem('kiftd_account')
    localStorage.removeItem('kiftd_auth')
  }

  function hasAuth(name: string) {
    return auth.value.split(',').map((s) => s.trim()).includes(name)
  }

  return { token, account, auth, isLogin, setSession, logout, hasAuth }
})
