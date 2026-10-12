import request from './request'

export interface SetupStatus {
  needsSetup: boolean
  freshInstall: boolean
  setupEnabled: boolean
}

export interface SetupResult {
  accessToken: string
  username: string
}

export function getSetupStatus() {
  return request.get('/setup/status') as any
}

export function submitSetup(payload: { username: string; password: string }) {
  return request.post('/setup', payload) as any
}
