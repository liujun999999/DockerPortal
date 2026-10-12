<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    :width="width"
    @update:model-value="$emit('update:visible', $event)"
    @close="$emit('cancel')"
  >
    <p>{{ message }}</p>
    <template #footer>
      <el-button @click="$emit('cancel')">取消</el-button>
      <el-button :type="type" :loading="loading" @click="$emit('confirm')">
        {{ confirmText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  visible: boolean
  title?: string
  message: string
  type?: 'primary' | 'danger' | 'warning' | 'success'
  confirmText?: string
  width?: string
  loading?: boolean
}>(), {
  title: '确认操作',
  type: 'danger',
  confirmText: '确认',
  width: '420px',
  loading: false,
})

defineEmits(['update:visible', 'confirm', 'cancel'])
</script>
