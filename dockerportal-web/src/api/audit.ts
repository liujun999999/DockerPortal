import request from './request'

export interface AuditQuery {
  page?: number
  size?: number
  level?: string
  action?: string
  username?: string
}

export const getAuditLogs = (params: AuditQuery = {}) =>
  request.get('/audit', { params })
