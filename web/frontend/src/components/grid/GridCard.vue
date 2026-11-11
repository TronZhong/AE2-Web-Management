<script setup>
/**
 * 网格卡片：选择页的单个网格条目。
 * 展示显示名、在线徽标、锁定徽标（v0.3：设有网格密码且未解锁）、创建者
 * （v0.11：ownerName，旧后端/无主网格省略）、控制器位置、快照摘要与时间行
 * （在线「数据截至」/ 离线「最后在线」，契约 v0.10）；点击「进入网格」向父级发 enter 事件。
 * 账号登录承担服务器级访问控制，网格密码在详情页解锁门校验（owner 免密由后端判定）。
 */
import { computed } from 'vue'

import BaseButton from '../base/BaseButton.vue'
import StatusBadge from '../base/StatusBadge.vue'
import { formatTime } from '../../lib/format.js'

const props = defineProps({
  /** 网格摘要对象，形状见 contracts/web-api.md 的 GET /api/grids（v0.11 含 ownerUuid/ownerName）。 */
  grid: { type: Object, required: true },
})

defineEmits(['enter'])

/** 是否处于锁定态：设有网格密码且当前账号未解锁（owner 免密由后端折算进 unlocked）。 */
const locked = computed(() => Boolean(props.grid.hasPassword) && !props.grid.unlocked)

/**
 * 时间行（契约 v0.10）：在线网格显示「数据截至」（capturedAt）；
 * 离线网格同一行直接显示「最后在线」（lastSeenAt，旧后端缺字段时回退 capturedAt），不额外占一行。
 */
const timeRow = computed(() =>
  props.grid.online
    ? { label: '数据截至', value: props.grid.capturedAt }
    : { label: '最后在线', value: props.grid.lastSeenAt ?? props.grid.capturedAt },
)
</script>

<template>
  <article
    class="flex flex-col gap-3 rounded-card border border-line bg-panel p-4 transition-colors hover:border-line-strong"
  >
    <header class="flex items-start justify-between gap-2">
      <h3 class="min-w-0 truncate text-sm font-medium text-ink" :title="grid.displayName">
        {{ grid.displayName }}
      </h3>
      <span class="flex shrink-0 items-center gap-1.5">
        <StatusBadge v-if="locked" label="🔒 锁定" variant="accent" />
        <StatusBadge :label="grid.online ? '在线' : '离线'" :variant="grid.online ? 'ok' : 'neutral'" />
      </span>
    </header>

    <dl class="space-y-1 font-mono text-[11px] leading-5 text-ink-dim">
      <!-- 创建者（契约 v0.11）：ownerName 缺省（旧后端 / 无主网格）时整行省略 -->
      <div v-if="grid.ownerName" class="flex justify-between gap-2">
        <dt class="shrink-0 text-ink-faint">创建者</dt>
        <dd class="truncate" :title="grid.ownerName">{{ grid.ownerName }}</dd>
      </div>
      <div class="flex justify-between gap-2">
        <dt class="shrink-0 text-ink-faint">控制器</dt>
        <dd
          class="truncate"
          :title="`${grid.dimension} @ (${grid.controllerX}, ${grid.controllerY}, ${grid.controllerZ})`"
        >
          {{ grid.dimension }} @ ({{ grid.controllerX }}, {{ grid.controllerY }},
          {{ grid.controllerZ }})
        </dd>
      </div>
      <div class="flex justify-between gap-2">
        <dt class="shrink-0 text-ink-faint">库存 / CPU</dt>
        <dd>{{ grid.inventoryEntries }} 项 / {{ grid.cpuCount }} 个</dd>
      </div>
      <!-- 时间行：在线「数据截至」；离线同位替换为「最后在线」（契约 v0.10），单行不重复 -->
      <div class="flex justify-between gap-2">
        <dt class="shrink-0 text-ink-faint">{{ timeRow.label }}</dt>
        <dd>{{ formatTime(timeRow.value) }}</dd>
      </div>
    </dl>

    <footer>
      <BaseButton variant="primary" class="w-full justify-center" @click="$emit('enter')">
        {{ locked ? '输入密码解锁 →' : '进入网格 →' }}
      </BaseButton>
    </footer>
  </article>
</template>
