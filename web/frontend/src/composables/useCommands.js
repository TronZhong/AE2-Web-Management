import { ref } from 'vue'

import { fetchCommands } from '../api/index.js'

/**
 * 单网格合成命令列表状态：暴露 commands / loading / error 与 refresh(accessId)。
 * 轮询由页面通过 usePolling 接线；提交合成命令后可手动 refresh 立即反映新命令。
 * 数据为该网格全量命令历史（PENDING / SUBMITTED / FAILED），展示层自行排序。
 */
export function useCommands() {
  const commands = ref([])
  const loading = ref(false)
  const error = ref('')

  // 首次加载显示加载态，之后轮询静默更新
  let firstLoad = true

  /** 拉取指定网格的命令列表；失败时保留旧数据并记录错误码。 */
  async function refresh(accessId) {
    if (firstLoad) {
      loading.value = true
    }
    try {
      const data = await fetchCommands(accessId)
      commands.value = data.commands
      error.value = ''
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : String(cause)
    } finally {
      firstLoad = false
      loading.value = false
    }
  }

  return { commands, loading, error, refresh }
}
