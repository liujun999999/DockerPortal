import request from './request'

export const getAuditLogs = (page = 1, size = 20) =>
  request.get('/audit', { params: { page, size } })
