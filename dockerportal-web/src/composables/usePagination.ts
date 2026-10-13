import { computed, ref, watch, type Ref } from 'vue'

/** 全站列表页统一的每页条数选项 */
export const PAGE_SIZES = [10, 50, 100]

/**
 * 前端分页：把一份完整数组（ref 或 computed）切成当前页要渲染的片段。
 *
 * 适用于「按单环境一次性拉取、量有界」的资源列表（容器/镜像/服务/任务等），
 * 无需后端改造分页接口。数据源变化时自动收敛页码：
 * - 换每页条数 → 回到第 1 页（避免停留在按旧尺寸算出的页）；
 * - 数据变少导致当前页越界 → 收敛到最后一页，而不是显示空白页。
 *
 * 用法：
 *   const { page, pageSize, total, pageData } = usePagination(source)
 *   <el-table :data="pageData"> ... <PaginationBar v-model:page="page" v-model:pageSize="pageSize" :total="total" />
 */
export function usePagination<T>(source: Ref<T[]>) {
  const page = ref(1)
  const pageSize = ref(PAGE_SIZES[0])

  const total = computed(() => source.value?.length ?? 0)
  const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
  const pageData = computed(() => {
    const start = (page.value - 1) * pageSize.value
    return (source.value ?? []).slice(start, start + pageSize.value)
  })

  watch(pageSize, () => { page.value = 1 })
  watch([total, pageCount], () => {
    if (page.value > pageCount.value) page.value = pageCount.value
  })

  /** 主动重置到第 1 页（如切换环境、重新加载数据时调用） */
  function reset() { page.value = 1 }

  return { page, pageSize, total, pageData, reset }
}
