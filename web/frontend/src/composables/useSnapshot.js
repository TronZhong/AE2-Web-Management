import { ref } from 'vue'

import { fetchSnapshot } from '../api/index.js'

/**
 * 单网格快照状态：暴露 snapshot / loading / error 与 refresh(accessId)。
 * 轮询由页面通过 usePolling 接线；离线网格同样返回最后快照（online=false）。
 */
export function useSnapshot() {
  const snapshot = ref(null)
  const loading = ref(false)
  const error = ref('')

  // 首次加载显示骨架屏，之后轮询静默更新
  let firstLoad = true

  /** 拉取指定网格的快照；失败时保留旧快照并记录错误码。 */
  async function refresh(accessId) {
    if (firstLoad) {
      loading.value = true
    }
    try {
      snapshot.value = await fetchSnapshot(accessId)
      error.value = ''
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : String(cause)
    } finally {
      firstLoad = false
      loading.value = false
    }
  }

  return { snapshot, loading, error, refresh }
}
