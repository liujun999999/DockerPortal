import request from './request'

export const getNodes = (envId: number) =>
  request.get(`/env/${envId}/swarm/nodes`) as any

export const getNode = (envId: number, nodeId: string) =>
  request.get(`/env/${envId}/swarm/nodes/${nodeId}`)

/** 本节点上的任务（含已结束、被拒绝的历史任务） */
export const getNodeTasks = (envId: number, nodeId: string) =>
  request.get(`/env/${envId}/swarm/nodes/${nodeId}/tasks`)

/**
 * data 形如 { labels, availability }，字段可省略。labels 是整体替换：
 * 想保留的原有条目必须一并带上，没带上的 key 会被删掉。
 */
export const updateNode = (envId: number, nodeId: string, data: any) =>
  request.put(`/env/${envId}/swarm/nodes/${nodeId}`, data)

export const removeNode = (envId: number, nodeId: string, force = false) =>
  request.delete(`/env/${envId}/swarm/nodes/${nodeId}`, { params: { force } })
