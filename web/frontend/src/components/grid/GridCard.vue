<script setup>
/**
 * 网格卡片：选择页的单个网络条目。
 * 展示显示名、在线徽标、锁定徽标（v0.3：设有网格密码且未解锁）、
 * 控制器位置、快照摘要与「数据截至」时间；点击「进入网格」向父级发 enter 事件。
 * 账号登录承担服务器级访问控制，网格密码在详情页解锁门校验。
 */
import { computed } from 'vue'

import BaseButton from '../base/BaseButton.vue'
import StatusBadge from '../base/StatusBadge.vue'
import { formatTime } from '../../lib/format.js'

const props = defineProps({
  /** 网络摘要对象，形状见 contracts/web-api.md 的 GET /api/networks（v0.3 含 hasPassword/unlocked）。 */
  network: { type: Object, required: true },
})

defineEmits(['enter'])

/** 是否处于锁定态：设有网格密码且当前账号未解锁。 */
const locked = computed(() => Boolean(props.network.hasPassword) && !props.network.unlocked)
</script>

<template>
  <article
    class="flex flex-col gap-3 rounded-card border border-line bg-panel p-4 transition-colors hover:border-line-strong"
  >
    <header class="flex items-start justify-between gap-2">
      <h3 class="min-w-0 truncate text-sm font-medium text-ink" :title="network.displayName">
        {{ network.displayName }}
      </h3>
      <span class="flex shrink-0 items-center gap-1.5">
        <StatusBadge v-if="locked" label="🔒 锁定" variant="accent" />
        <StatusBadge :label="network.online ? '在线' : '离线'" :variant="network.online ? 'ok' : 'neutral'" />
      </span>
    </header>

    <dl class="space-y-1 font-mono text-[11px] leading-5 text-ink-dim">
      <div class="flex justify-between gap-2">
        <dt class="shrink-0 text-ink-faint">控制器</dt>
        <dd
          class="truncate"
          :title="`${network.dimension} @ (${network.controllerX}, ${network.controllerY}, ${network.controllerZ})`"
        >
          {{ network.dimension }} @ ({{ network.controllerX }}, {{ network.controllerY }},
          {{ network.controllerZ }})
        </dd>
      </div>
      <div class="flex justify-between gap-2">
        <dt class="shrink-0 text-ink-faint">库存 / CPU</dt>
        <dd>{{ network.inventoryEntries }} 项 / {{ network.cpuCount }} 个</dd>
      </div>
      <div class="flex justify-between gap-2">
        <dt class="shrink-0 text-ink-faint">数据截至</dt>
        <dd>{{ formatTime(network.capturedAt) }}</dd>
      </div>
    </dl>

    <footer>
      <BaseButton variant="primary" class="w-full justify-center" @click="$emit('enter')">
        {{ locked ? '输入密码解锁 →' : '进入网格 →' }}
      </BaseButton>
    </footer>
  </article>
</template>
