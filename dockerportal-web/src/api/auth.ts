import request from './request'

export function login(username: string, password: string) {
  return request.post('/auth/login', { username, password }) as any
}

export function getMe() {
  return request.get('/auth/me') as any
}

export function changePassword(oldPassword: string, newPassword: string) {
  return request.post('/auth/change-password', { oldPassword, newPassword }) as any
}
