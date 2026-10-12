import request from './request'

export function getVolumes(envId: number) {
  return request.get(`/env/${envId}/volumes`) as any
}

export function createVolume(envId: number, data: any) {
  return request.post(`/env/${envId}/volumes`, data) as any
}

export function removeVolume(envId: number, volumeName: string, force = false) {
  return request.delete(`/env/${envId}/volumes/${volumeName}`, { params: { force } }) as any
}
