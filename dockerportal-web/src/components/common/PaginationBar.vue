<template>
  <el-pagination
    v-if="total > 0"
    class="pagination-bar"
    background
    layout="total, sizes, prev, pager, next, jumper"
    :current-page="page"
    :page-size="pageSize"
    :page-sizes="PAGE_SIZES"
    :total="total"
    @current-change="(v: number) => emit('update:page', v)"
    @size-change="(v: number) => emit('update:pageSize', v)"
  />
</template>

<script setup lang="ts">
import { PAGE_SIZES } from '@/composables/usePagination'

defineProps<{ page: number; pageSize: number; total: number }>()
const emit = defineEmits<{
  (e: 'update:page', value: number): void
  (e: 'update:pageSize', value: number): void
}>()
</script>

<style scoped>
.pagination-bar {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
