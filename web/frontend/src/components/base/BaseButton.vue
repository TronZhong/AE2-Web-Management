<script setup>
/**
 * 通用按钮：primary（橙底黑字，主操作）与 ghost（描边灰字，次操作）两种变体。
 * 样式对齐终端风：等宽小字、方角略圆、hover 提亮。
 */
defineProps({
  /** 视觉变体。 */
  variant: {
    type: String,
    default: 'ghost',
    validator: (value) => ['primary', 'ghost'].includes(value),
  },
  /** 禁用态。 */
  disabled: { type: Boolean, default: false },
})
</script>

<template>
  <button
    :disabled="disabled"
    class="inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 font-mono text-xs transition-colors disabled:cursor-not-allowed disabled:opacity-40"
    :class="
      variant === 'primary'
        ? 'bg-accent font-medium text-black hover:bg-accent/85'
        : 'border border-line text-ink-dim hover:border-line-strong hover:text-ink'
    "
  >
    <slot />
  </button>
</template>
