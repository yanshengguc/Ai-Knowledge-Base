import { defineStore } from 'pinia'
import { login as apiLogin, register as apiRegister } from '@/api/modules/user'
import { getToken, setToken, clearToken } from '@/utils/auth'
import type { LoginDTO, RegisterDTO } from '@/types/api'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    username: localStorage.getItem('akb_username') || '',
  }),
  actions: {
    async login(data: LoginDTO) {
      const res = await apiLogin(data)
      this.token = res.data
      this.username = data.username
      setToken(res.data)
      localStorage.setItem('akb_username', data.username)
    },
    async register(data: RegisterDTO) {
      await apiRegister(data)
    },
    logout() {
      this.token = ''
      this.username = ''
      clearToken()
      localStorage.removeItem('akb_username')
    },
  },
})
