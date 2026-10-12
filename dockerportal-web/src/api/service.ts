import request from './request'

export const getServices = (envId: number) =>
  request.get(`/env/${envId}/swarm/services`)

export const getService = (envId: number, serviceId: string) =>
  request.get(`/env/${envId}/swarm/services/${serviceId}`)

export const createService = (envId: number, data: any) =>
  request.post(`/env/${envId}/swarm/services`, data)

export const updateService = (envId: number, serviceId: string, data: any) =>
  request.put(`/env/${envId}/swarm/services/${serviceId}`, data)

export const scaleService = (envId: number, serviceId: string, replicas: number) =>
  request.post(`/env/${envId}/swarm/services/${serviceId}/scale`, null, { params: { replicas } })

export const removeService = (envId: number, serviceId: string) =>
  request.delete(`/env/${envId}/swarm/services/${serviceId}`)

export const getServiceTasks = (envId: number, serviceId: string) =>
  request.get(`/env/${envId}/swarm/services/${serviceId}/tasks`)

export const getServiceLogs = (envId: number, serviceId: string, tail = 100) =>
  request.get(`/env/${envId}/swarm/services/${serviceId}/logs`, { params: { tail } })

// 单个副本（task）日志：/tasks/{id}/logs，daemon 会定位到任务所在节点取日志
export const getTaskLogs = (envId: number, taskId: string, tail = 100) =>
  request.get(`/env/${envId}/swarm/tasks/${taskId}/logs`, { params: { tail } })

// 探测副本终端接入方式：{ containerId, nodeId, state, local, sshConfigured, nodeAddress }
export const getTaskExecTarget = (envId: number, taskId: string) =>
  request.get(`/env/${envId}/swarm/tasks/${taskId}/exec-target`)
