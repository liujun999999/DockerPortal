import request from './request'

export function getNetworks(envId: number) {
  return request.get(`/env/${envId}/networks`) as any
}

export function createNetwork(envId: number, data: any) {
  return request.post(`/env/${envId}/networks`, data) as any
}

export function removeNetwork(envId: number, networkId: string) {
  return request.delete(`/env/${envId}/networks/${networkId}`) as any
}
