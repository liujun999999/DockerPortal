<template>
  <div class="json-viewer">
    <pre class="json-content"><code>{{ formattedJson }}</code></pre>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  data: any
  indent?: number
}>(), {
  indent: 2,
})

const formattedJson = computed(() => {
  try {
    if (typeof props.data === 'string') {
      return JSON.stringify(JSON.parse(props.data), null, props.indent)
    }
    return JSON.stringify(props.data, null, props.indent)
  } catch {
    return String(props.data)
  }
})
</script>

<style scoped>
.json-viewer {
  background: #1e1e1e;
  border-radius: 6px;
  padding: 16px;
  overflow: auto;
  max-height: 500px;
}
.json-content {
  color: #d4d4d4;
  font-family: 'Cascadia Code', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.5;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
