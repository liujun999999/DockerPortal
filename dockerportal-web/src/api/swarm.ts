import request from './request'

export const getSwarmInfo = (envId: number) =>
  request.get(`/env/${envId}/swarm/info`)

export const getSwarmJoinInfo = (envId: number) =>
  request.get(`/env/${envId}/swarm/join-info`)

export const getSwarmTasks = (envId: number) =>
  request.get(`/env/${envId}/swarm/tasks`)

export const initSwarm = (envId: number, advertiseAddr?: string) =>
  request.post(`/env/${envId}/swarm/init`, null, { params: { advertiseAddr } })

export const leaveSwarm = (envId: number, force = false) =>
  request.post(`/env/${envId}/swarm/leave`, null, { params: { force } })
