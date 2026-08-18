import request from '@/api/request'
import type { LoginDTO, RegisterDTO, Result } from '@/types/api'

export function register(data: RegisterDTO) {
  return request.post<unknown, Result>('/user/register', data)
}

export function login(data: LoginDTO) {
  return request.post<unknown, Result<string>>('/user/login', data)
}
