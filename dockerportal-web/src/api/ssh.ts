import request from './request'

export const getSshConfigs = (envId: number) =>
  request.get(`/env/${envId}/ssh/configs`) as any

export const getSshConfig = (envId: number, id: number) =>
  request.get(`/env/${envId}/ssh/configs/${id}`)

export const createSshConfig = (envId: number, data: any) =>
  request.post(`/env/${envId}/ssh/configs`, data)

export const testSshConfig = (envId: number, data: any) =>
  request.post(`/env/${envId}/ssh/configs/test`, data) as any

export const updateSshConfig = (envId: number, id: number, data: any) =>
  request.put(`/env/${envId}/ssh/configs/${id}`, data)

export const deleteSshConfig = (envId: number, id: number) =>
  request.delete(`/env/${envId}/ssh/configs/${id}`)
