<script setup>
/**
 * 网格详情页：状态·CPU·命令合并卡 / 样板 / 库存三类卡片。
 * 首行合并卡与样板卡横向对半分（合并卡内 CPU 在上、命令在下，两列表各最多 5 条滚动；
 * 样板格子区桌面端拉伸填满与合并卡等高、超出滚动）；库存卡整行、视口固定高度滚动。
 * 快照与命令列表各 5s 轮询；离线网格照常进入，展示最后快照并给出提示条。
 * 样板卡内提交合成后通过 submitted 事件立即刷新命令列表。
 * 锁定网格（契约 v0.5：快照返回 grid_locked）以解锁门面板替代内容，
 * 解锁成功后刷新快照与网格列表（unlocked 徽标随之更新）。
 */
import { computed, onMounted, onUnmounted } from 'vue'

import { USE_MOCK, deleteCommand } from '../api/index.js'
import { subscribeGridEvents } from '../api/events.js'
import { useCommands } from '../composables/useCommands.js'
import { useGrids } from '../composables/useGrids.js'
import { usePolling } from '../composables/usePolling.js'
import { useSnapshot } from '../composables/useSnapshot.js'
import UnlockGate from '../components/grid/UnlockGate.vue'
import InventoryCard from '../components/snapshot/InventoryCard.vue'
import PatternListCard from '../components/snapshot/PatternListCard.vue'
import StatusCpuCard from '../components/snapshot/StatusCpuCard.vue'

const props = defineProps({
  /** 路由参数：目标网格 accessId。 */
  accessId: { type: String, required: true },
})

const { snapshot, loading, error, refresh } = useSnapshot()
const { commands, error: commandsError, refresh: refreshCommands } = useCommands()
const { grids, refresh: refreshGrids } = useGrids()

// 快照与命令列表各 5s 轮询；网格列表仅在进入页面时拉一次，用于解析显示名与控制器位置
usePolling(() => refresh(props.accessId), 5_000).start()
usePolling(() => refreshCommands(props.accessId), 5_000).start()
onMounted(refreshGrids)

// 后端推送：列表级变化 → 刷新网格列表（显示名/锁定徽标同步）；本网格快照刷新 → 立即拉快照与命令
let unsubscribeEvents = null
onMounted(() => {
  unsubscribeEvents = subscribeGridEvents({
    onGridsUpdated: () => refreshGrids(),
    onGridSnapshotUpdated: (accessId) => {
      if (accessId === props.accessId) {
        refresh(props.accessId)
        refreshCommands(props.accessId)
      }
    },
  })
})
onUnmounted(() => unsubscribeEvents?.())

/** 命中的网格摘要（可能为 null：快照存在但列表未返回）。 */
const grid = computed(() => grids.value.find((item) => item.accessId === props.accessId) ?? null)

/** 标题优先用显示名，退化用 accessId 前 8 位。 */
const title = computed(
  () => grid.value?.displayName ?? (snapshot.value ? `网格 ${props.accessId.slice(0, 8)}` : '加载中…'),
)

/** 网格锁定态：快照端点返回 grid_locked（设有密码且当前账号未解锁）。 */
const locked = computed(() => error.value === 'grid_locked')

/** 解锁成功：刷新快照（恢复内容）与网格列表（同步 unlocked 徽标）。 */
function onUnlocked() {
  refresh(props.accessId)
  refreshGrids()
}

/**
 * 删除命令记录（契约 v0.9）：调 DELETE 端点后刷新命令列表。
 * command_not_found（重复点击/竞态下记录已消失）按已删除处理，静默通过；
 * 其余错误码不阻塞——刷新后列表仍显示该记录，删除按钮保持可用，真相由数据呈现。
 * @param command 目标命令条目（来自 CommandList 的 delete 事件）
 */
async function onDeleteCommand(command) {
  try {
    await deleteCommand(props.accessId, command.commandId)
  } catch (cause) {
    if (cause?.message !== 'command_not_found') {
      console.warn('[grid] 删除命令失败:', cause?.message ?? cause)
    }
  }
  await refreshCommands(props.accessId)
}
</script>

<template>
  <div>
    <RouterLink to="/" class="font-mono text-xs text-ink-faint transition-colors hover:text-ink">
      ← 返回网格列表
    </RouterLink>

    <div class="mb-6 mt-3 flex items-end justify-between gap-4">
      <h1 class="min-w-0 truncate text-lg font-medium text-ink">{{ title }}</h1>
      <!-- 仅 mock 模式显示徽标：实时数据是默认态，无需额外提示 -->
      <span v-if="USE_MOCK" class="shrink-0 font-mono text-[11px] text-ink-faint">MOCK 数据</span>
    </div>

    <UnlockGate
      v-if="locked"
      :access-id="accessId"
      :display-name="grid?.displayName ?? ''"
      @unlocked="onUnlocked"
    />

    <template v-else>
      <p
        v-if="error"
        class="mb-4 rounded-md border border-err/30 bg-err/10 px-3 py-2 font-mono text-xs text-err"
      >
        加载失败：{{ error }}
      </p>

      <div v-if="loading && !snapshot" class="space-y-4">
        <div class="h-44 animate-pulse rounded-card border border-line bg-panel"></div>
        <div class="h-72 animate-pulse rounded-card border border-line bg-panel"></div>
      </div>

      <template v-else-if="snapshot">
        <p
          v-if="!snapshot.online"
          class="mb-4 rounded-md border border-accent/30 bg-accent/10 px-3 py-2 font-mono text-xs text-accent"
        >
          网格离线：显示最后一份快照
        </p>

        <div class="grid gap-4 lg:grid-cols-2">
          <StatusCpuCard
            :snapshot="snapshot"
            :grid="grid"
            :commands="commands"
            :commands-error="commandsError"
            @delete="onDeleteCommand"
          />
          <PatternListCard
            :access-id="accessId"
            :patterns="snapshot.patterns"
            @submitted="refreshCommands(accessId)"
          />
          <InventoryCard class="lg:col-span-2" :items="snapshot.inventory" />
        </div>
      </template>
    </template>
  </div>
</template>
