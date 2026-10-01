import { defineStore } from 'pinia'
import { login as apiLogin, register as apiRegister, getMe } from '@/api/modules/user'
import { getToken, setToken, clearToken } from '@/utils/auth'
import type { LoginDTO, RegisterDTO } from '@/types/api'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    username: localStorage.getItem('akb_username') || '',
    // 管理端可见性:由后端白名单判定,前端只持有展示用的布尔值(真正的拦截在后端)
    isAdmin: false,
  }),
  actions: {
    async login(data: LoginDTO) {
      const res = await apiLogin(data)
      this.token = res.data
      this.username = data.username
      setToken(res.data)
      localStorage.setItem('akb_username', data.username)
      await this.ensureProfile()
    },
    async register(data: RegisterDTO) {
      await apiRegister(data)
    },
    /** 拉取当前用户档案;失败时降级为非管理员,不阻塞页面 */
    async ensureProfile() {
      if (!this.token) {
        this.isAdmin = false
        return
      }
      try {
        const res = await getMe()
        this.isAdmin = !!res.data?.admin
        if (res.data?.username) {
          this.username = res.data.username
          localStorage.setItem('akb_username', res.data.username)
        }
      } catch {
        this.isAdmin = false
      }
    },
    logout() {
      this.token = ''
      this.username = ''
      this.isAdmin = false
      clearToken()
      localStorage.removeItem('akb_username')
    },
  },
})