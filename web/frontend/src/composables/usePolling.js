import { onUnmounted } from 'vue'

/**
 * 定时轮询：每 interval 毫秒执行一次 task；页面不可见时暂停，恢复可见立即补一次。
 * 组件卸载时自动停止。返回 { start, stop }，通常在 setup 中直接 start()。
 */
export function usePolling(task, interval = 5000) {
  let timer = null

  /** 单次执行：吞掉异常并告警，避免轮询因一次失败中断。 */
  async function tick() {
    try {
      await task()
    } catch (error) {
      console.warn('[usePolling] 轮询任务失败:', error)
    }
  }

  /** 页面重新可见时立即刷新一次，避免长时间后台后数据滞后。 */
  function onVisibilityChange() {
    if (document.visibilityState === 'visible') {
      tick()
    }
  }

  /** 启动轮询（重复调用会先停止旧定时器）。 */
  function start() {
    stop()
    tick()
    timer = window.setInterval(tick, interval)
    document.addEventListener('visibilitychange', onVisibilityChange)
  }

  /** 停止轮询并移除可见性监听。 */
  function stop() {
    if (timer !== null) {
      window.clearInterval(timer)
      timer = null
    }
    document.removeEventListener('visibilitychange', onVisibilityChange)
  }

  onUnmounted(stop)
  return { start, stop }
}
