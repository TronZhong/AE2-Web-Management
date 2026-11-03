<script setup>
/**
 * 通用卡片容器：等宽小写标题 + 右侧动作区插槽 + 内容插槽。
 * 全站统一面板观感（panel 底、line 边、card 圆角），页面不自行拼卡片。
 */
defineProps({
  /** 卡片标题；为空且无动作区时不渲染头部。 */
  title: { type: String, default: '' },
})
</script>

<template>
  <section class="rounded-card border border-line bg-panel">
    <header
      v-if="title || $slots.actions"
      class="flex min-h-11 items-center justify-between gap-3 border-b border-line px-4 py-2.5"
    >
      <h2 class="font-mono text-[11px] uppercase tracking-[0.18em] text-ink-faint">{{ title }}</h2>
      <slot name="actions" />
    </header>
    <div class="p-4">
      <slot />
    </div>
  </section>
</template>
