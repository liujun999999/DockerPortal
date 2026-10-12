import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getEnvironments } from '@/api/environment'

export interface Environment {
  id: number
  name: string
  type: string
  endpoint: string
  status: string
  swarmMode: boolean
  dockerVersion: string
}

export const useEnvironmentStore = defineStore('environment', () => {
  const environments = ref<Environment[]>([])
  const currentEnvId = ref<number>(Number(localStorage.getItem('currentEnvId')) || 0)

  const currentEnv = computed(() =>
    environments.value.find((e) => e.id === currentEnvId.value)
  )

  async function loadEnvironments() {
    try {
      const res = await getEnvironments()
      environments.value = res
      if (!currentEnvId.value && res.length > 0) {
        currentEnvId.value = res[0].id
        localStorage.setItem('currentEnvId', String(res[0].id))
      }
    } catch (e) {
      console.error('Failed to load environments', e)
    }
  }

  function setCurrentEnv(id: number) {
    currentEnvId.value = id
    localStorage.setItem('currentEnvId', String(id))
  }

  return { environments, currentEnvId, currentEnv, loadEnvironments, setCurrentEnv }
})
