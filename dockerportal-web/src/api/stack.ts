import request from './request'

export const getStacks = (envId: number) =>
  request.get(`/env/${envId}/swarm/stacks`)

export const getStack = (envId: number, stackName: string) =>
  request.get(`/env/${envId}/swarm/stacks/${stackName}`)

/** 部署时落库的 compose 原文；stored=false 表示该 Stack 不是由本平台部署的，没有原文可回填 */
export const getStackCompose = (envId: number, stackName: string) =>
  request.get(`/env/${envId}/swarm/stacks/${stackName}/compose`)

export const deployStack = (envId: number, data: { name: string; composeContent: string }) =>
  request.post(`/env/${envId}/swarm/stacks`, data)

export const removeStack = (envId: number, stackName: string) =>
  request.delete(`/env/${envId}/swarm/stacks/${stackName}`)
