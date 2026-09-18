<template>
  <div class="page-shell">
    <div class="panel auth-card">
      <div class="brand-title">文件管理系统 <span class="brand-sub">KIFT</span></div>
      <p class="meta-line" style="margin: 12px 0 20px">登录后管理您的网盘文件</p>
      <el-form @submit.prevent="onSubmit" label-position="top">
        <el-form-item label="账号">
          <el-input v-model="account" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="验证码">
          <div style="display:flex;gap:8px;width:100%">
            <el-input v-model="captcha" autocomplete="off" />
            <img
              v-if="captchaImg"
              :src="'data:image/png;base64,' + captchaImg"
              alt="captcha"
              style="height:32px;cursor:pointer;border-radius:4px"
              @click="loadCaptcha"
            />
          </div>
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width:100%">登录</el-button>
      </el-form>
      <div style="margin-top:14px;display:flex;justify-content:space-between">
        <el-button link @click="$router.push('/')">返回主页</el-button>
        <el-button v-if="canSignup" link type="primary" @click="$router.push('/signup')">立即注册</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCaptcha, login, signupEnabled } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const account = ref('')
const password = ref('')
const captcha = ref('')
const captchaId = ref('')
const captchaImg = ref('')
const canSignup = ref(false)
const loading = ref(false)

async function loadCaptcha() {
  const c = await getCaptcha()
  captchaId.value = c.captchaId
  captchaImg.value = c.imageBase64
  captcha.value = ''
}

async function onSubmit() {
  if (!captcha.value.trim()) {
    ElMessage.warning('请输入验证码')
    return
  }
  loading.value = true
  try {
    const res = await login(account.value, password.value, captchaId.value, captcha.value)
    auth.setSession(res.token, res.account, res.auth)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.message || '登录失败')
    await loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  canSignup.value = await signupEnabled()
  await loadCaptcha()
})
</script>
