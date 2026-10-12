<template>
  <el-tag :type="tagType" size="small" :effect="effect">
    {{ label }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  status: string
  statusMap?: Record<string, { label: string; type: string }>
  effect?: 'light' | 'dark' | 'plain'
}>(), {
  effect: 'light',
})

const tagType = computed(() => {
  if (props.statusMap && props.statusMap[props.status]) {
    return props.statusMap[props.status].type
  }
  return 'info'
})

const label = computed(() => {
  if (props.statusMap && props.statusMap[props.status]) {
    return props.statusMap[props.status].label
  }
  return props.status
})
</script>
