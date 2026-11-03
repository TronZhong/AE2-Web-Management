import { ref } from 'vue'

import { fetchNetworks } from '../api/index.js'

/**
 * 网络列表状态：暴露 networks / loading / error 与 refresh()。
 * 轮询由页面通过 usePolling(refresh) 接线，本组合式函数不持有定时器。
 */
export function useNetworks() {
  const networks = ref([])
  const loading = ref(false)
  const error = ref('')

  // 首次加载显示骨架屏，之后轮询静默刷新，避免列表闪烁
  let firstLoad = true

  /** 拉取网络列表并更新状态；失败时保留旧数据并记录错误码。 */
  async function refresh() {
    if (firstLoad) {
      loading.value = true
    }
    try {
      const data = await fetchNetworks()
      networks.value = data.networks
      error.value = ''
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : String(cause)
    } finally {
      firstLoad = false
      loading.value = false
    }
  }

  return { networks, loading, error, refresh }
}
