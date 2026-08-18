import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearToken } from '@/utils/auth'
import type { Result } from '@/types/api'
import router from '@/router'

// axios 封装:统一 baseURL / token 头 / 错误处理 / 401 跳登录
const request = axios.create({
  baseURL: '/api',
  timeout: 60000, // 聊天接口 LLM 生成可能较慢
})

request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data as Result
    // 业务错误(Result.code !== 200)
    if (res && res.code !== 200) {
      if (res.code === 401) {
        clearToken()
        router.push('/login')
      }
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      clearToken()
      router.push('/login')
      ElMessage.error('登录已过期,请重新登录')
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时,请稍后再试')
    } else {
      ElMessage.error(error.response?.data?.message || '网络错误')
    }
    return Promise.reject(error)
  },
)

export default request
