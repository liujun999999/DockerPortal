import request from './request'

export function getDashboard(envId: number) {
  return request.get(`/env/${envId}/dashboard`) as any
}
