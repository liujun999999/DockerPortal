import request from './request'

export function getEnvironments() {
  return request.get('/environments') as any
}

export function getEnvironment(id: number) {
  return request.get(`/environments/${id}`) as any
}

export function createEnvironment(data: any) {
  return request.post('/environments', data) as any
}

export function updateEnvironment(id: number, data: any) {
  return request.put(`/environments/${id}`, data) as any
}

export function deleteEnvironment(id: number) {
  return request.delete(`/environments/${id}`) as any
}

export function connectEnvironment(id: number) {
  return request.post(`/environments/${id}/connect`) as any
}

export function disconnectEnvironment(id: number) {
  return request.post(`/environments/${id}/disconnect`) as any
}

/** 测试未保存的连接草稿，返回引擎概况（dockerVersion/apiVersion/os/arch/ncpu/memGB/swarmState） */
export function testEnvironmentDraft(data: any): Promise<any> {
  return request.post('/environments/test', data)
}

/** 测试已保存环境的连通性 */
export function testEnvironment(id: number): Promise<any> {
  return request.post(`/environments/${id}/test`)
}
