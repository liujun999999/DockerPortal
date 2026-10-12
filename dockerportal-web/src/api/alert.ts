import request from './request'

// 告警规则
export const getAlertRules = (envId?: number) =>
  request.get('/alerts/rules', { params: envId ? { envId } : {} })

export const getAlertRule = (id: number) =>
  request.get(`/alerts/rules/${id}`)

export const createAlertRule = (data: any) =>
  request.post('/alerts/rules', data)

export const updateAlertRule = (id: number, data: any) =>
  request.put(`/alerts/rules/${id}`, data)

export const deleteAlertRule = (id: number) =>
  request.delete(`/alerts/rules/${id}`)

export const toggleAlertRule = (id: number) =>
  request.post(`/alerts/rules/${id}/toggle`)

// 告警事件
export const getAlertEvents = (params?: { envId?: number; page?: number; size?: number }) =>
  request.get('/alerts/events', { params })

export const getEventsByRule = (ruleId: number) =>
  request.get(`/alerts/events/rule/${ruleId}`)
