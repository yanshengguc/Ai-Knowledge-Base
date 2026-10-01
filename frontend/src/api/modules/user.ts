import request from '@/api/request'
import type { LoginDTO, RegisterDTO, Result } from '@/types/api'

export interface UserProfile {
  id: number
  username: string
  /** 是否命中管理端白名单(决定 /admin 路由与导航是否可见) */
  admin: boolean
}

export function register(data: RegisterDTO) {
  return request.post<unknown, Result>('/user/register', data)
}

export function login(data: LoginDTO) {
  return request.post<unknown, Result<string>>('/user/login', data)
}

export function getMe() {
  return request.get<unknown, Result<UserProfile>>('/user/me')
}