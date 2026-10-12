import request from './request'

export const getNodeInfo = (envId: number) =>
  request.get(`/env/${envId}/monitor/node`)

export const getAllContainerStats = (envId: number) =>
  request.get(`/env/${envId}/monitor/containers`)

export const getContainerStat = (envId: number, containerId: string) =>
  request.get(`/env/${envId}/monitor/containers/${containerId}`)
