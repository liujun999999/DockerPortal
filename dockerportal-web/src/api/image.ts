import request from './request'

export function getImages(envId: number) {
  return request.get(`/env/${envId}/images`) as any
}

export function pullImage(envId: number, imageName: string, tag = 'latest', registryId?: number) {
  const body: any = { imageName, tag }
  if (registryId !== undefined && registryId !== null) body.registryId = registryId
  return request.post(`/env/${envId}/images/pull`, body) as any
}

/** 没有 force 参数：使用中的镜像（含被已停止容器、Swarm 服务引用）后端一律拒删 */
export function removeImage(envId: number, imageId: string) {
  return request.delete(`/env/${envId}/images/${imageId}`) as any
}

export function inspectImage(envId: number, imageId: string) {
  return request.get(`/env/${envId}/images/${imageId}`) as any
}

/** 推送镜像：后端自动先打目标 tag 再 push，registryId 缺省时按 repo 主机前缀自动匹配凭据 */
export function pushImage(envId: number, imageId: string, repo: string, tag: string, registryId?: number) {
  const params: any = { repo, tag }
  if (registryId !== undefined && registryId !== null) params.registryId = registryId
  return request.post(`/env/${envId}/images/${imageId}/push`, null, { params, timeout: 600000 }) as any
}

export function getImageHistory(envId: number, imageId: string) {
  return request.get(`/env/${envId}/images/${imageId}/history`) as any
}
