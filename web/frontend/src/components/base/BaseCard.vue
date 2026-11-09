<script setup>
/**
 * 通用卡片容器：等宽小写标题 + 右侧动作区插槽 + 内容插槽。
 * 全站统一面板观感（panel 底、line 边、card 圆角），页面不自行拼卡片。
 * fill 模式（桌面端 lg 起）：根节点纵向 flex、内容区撑满卡片剩余高度，
 * 供与同行更高卡片等高时内部区域拉伸/滚动（如样板卡格子区）；窄屏无效果。
 */
defineProps({
  /** 卡片标题；为空且无动作区时不渲染头部。 */
  title: { type: String, default: '' },
  /** 填充模式：内容区 flex 撑满卡片剩余高度（仅 lg 起生效）。 */
  fill: { type: Boolean, default: false },
})
</script>

<template>
  <section
    class="rounded-card border border-line bg-panel"
    :class="fill ? 'lg:flex lg:flex-col' : ''"
  >
    <header
      v-if="title || $slots.actions"
      class="flex min-h-11 shrink-0 items-center justify-between gap-3 border-b border-line px-4 py-2.5"
    >
      <h2 class="font-mono text-[11px] uppercase tracking-[0.18em] text-ink-faint">{{ title }}</h2>
      <slot name="actions" />
    </header>
    <div class="p-4" :class="fill ? 'lg:min-h-0 lg:flex-1 lg:flex lg:flex-col' : ''">
      <slot />
    </div>
  </section>
</template>
