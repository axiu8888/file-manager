<template>
  <div class="page-shell">
    <div class="panel auth-card">
      <div class="brand-title">注册账号</div>
      <p class="meta-line" style="margin: 12px 0 20px">创建后即可上传与管理文件</p>
      <el-form @submit.prevent="onSubmit" label-position="top">
        <el-form-item label="账号">
          <el-input v-model="account" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password />
        </el-form-item>
        <el-form-item label="验证码">
          <div style="display:flex;gap:8px;width:100%">
            <el-input v-model="captcha" />
            <img
              v-if="captchaImg"
              :src="'data:image/png;base64,' + captchaImg"
              alt="captcha"
              style="height:32px;cursor:pointer;border-radius:4px"
              @click="loadCaptcha"
            />
          </div>
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width:100%">注册</el-button>
      </el-form>
      <div style="margin-top:14px">
        <el-button link @click="$router.push('/login')">已有账号？去登录</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCaptcha, signup } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const account = ref('')
const password = ref('')
const captcha = ref('')
const captchaId = ref('')
const captchaImg = ref('')
const loading = ref(false)

async function loadCaptcha() {
  const c = await getCaptcha()
  captchaId.value = c.captchaId
  captchaImg.value = c.imageBase64
}

async function onSubmit() {
  loading.value = true
  try {
    const res = await signup(account.value, password.value, captchaId.value, captcha.value)
    auth.setSession(res.token, res.account, res.auth)
    ElMessage.success('注册成功')
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.message || '注册失败')
    await loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(loadCaptcha)
</script>
