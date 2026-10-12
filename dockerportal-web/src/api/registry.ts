import request from './request'

export function getRegistries() {
  return request.get('/registries') as any
}

export function createRegistry(data: any) {
  return request.post('/registries', data) as any
}

export function updateRegistry(id: number, data: any) {
  return request.put(`/registries/${id}`, data) as any
}

export function deleteRegistry(id: number) {
  return request.delete(`/registries/${id}`) as any
}

export function testRegistryById(id: number) {
  return request.post(`/registries/${id}/test`) as any
}

export function testRegistryWith(data: { url: string; username: string; password: string }) {
  return request.post('/registries/test', data) as any
}
