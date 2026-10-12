import request from './request'

export function getContainers(envId: number, showAll = true) {
  return request.get(`/env/${envId}/containers`, { params: { showAll } }) as any
}

export function inspectContainer(envId: number, containerId: string) {
  return request.get(`/env/${envId}/containers/${containerId}`) as any
}

export function createContainer(envId: number, data: any) {
  return request.post(`/env/${envId}/containers`, data) as any
}

export function containerAction(envId: number, containerId: string, action: string) {
  return request.post(`/env/${envId}/containers/${containerId}/${action}`) as any
}

export function removeContainer(envId: number, containerId: string, force = false) {
  return request.delete(`/env/${envId}/containers/${containerId}`, { params: { force } }) as any
}

/** docker update：热调资源限制，字段留空不修改 */
export function updateContainerResources(envId: number, containerId: string, data: {
  memoryMb?: number; memorySwapMb?: number; memoryReservationMb?: number
  cpuCores?: number; cpuShares?: number; cpusetCpus?: string
}) {
  return request.put(`/env/${envId}/containers/${containerId}/resources`, data) as any
}

export function getContainerLogs(envId: number, containerId: string, tail = 100, timestamps = true) {
  return request.get(`/env/${envId}/containers/${containerId}/logs`, { params: { tail, timestamps } }) as any
}

export function listContainerFiles(envId: number, containerId: string, path = '/') {
  return request.get(`/env/${envId}/containers/${containerId}/files`, { params: { path } }) as any
}

export function readContainerFile(envId: number, containerId: string, path: string) {
  return request.get(`/env/${envId}/containers/${containerId}/files/content`, { params: { path } }) as any
}

export function writeContainerFile(envId: number, containerId: string, path: string, content: string) {
  return request.put(`/env/${envId}/containers/${containerId}/files/content`, { content }, { params: { path } }) as any
}
