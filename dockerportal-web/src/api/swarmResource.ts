import request from './request'

// ==================== Secrets ====================

export const listSecrets = (envId: number): Promise<any[]> =>
  request.get(`/env/${envId}/swarm/secrets`)

export const createSecret = (envId: number, data: { name: string; data: string; labels?: Record<string, string> }) =>
  request.post(`/env/${envId}/swarm/secrets`, data)

export const deleteSecret = (envId: number, secretId: string) =>
  request.delete(`/env/${envId}/swarm/secrets/${secretId}`)

// ==================== Configs ====================

export const listConfigs = (envId: number): Promise<any[]> =>
  request.get(`/env/${envId}/swarm/configs`)

export const getConfig = (envId: number, configId: string): Promise<any> =>
  request.get(`/env/${envId}/swarm/configs/${configId}`)

export const createConfig = (envId: number, data: { name: string; data: string; labels?: Record<string, string>; templatable?: boolean }) =>
  request.post(`/env/${envId}/swarm/configs`, data)

export const deleteConfig = (envId: number, configId: string) =>
  request.delete(`/env/${envId}/swarm/configs/${configId}`)
