import request from './request'

export function getSystemDf(envId: number) {
  return request.get(`/env/${envId}/system/df`) as any
}

export function pruneContainers(envId: number) {
  return request.post(`/env/${envId}/system/prune/containers`) as any
}

export function pruneImages(envId: number, all = false) {
  return request.post(`/env/${envId}/system/prune/images`, null, { params: { all } }) as any
}

export function pruneVolumes(envId: number) {
  return request.post(`/env/${envId}/system/prune/volumes`) as any
}

export function pruneNetworks(envId: number) {
  return request.post(`/env/${envId}/system/prune/networks`) as any
}

export function pruneBuilder(envId: number) {
  return request.post(`/env/${envId}/system/prune/builder`) as any
}

export function pruneSystem(envId: number, all = false) {
  return request.post(`/env/${envId}/system/prune/system`, null, { params: { all } }) as any
}
