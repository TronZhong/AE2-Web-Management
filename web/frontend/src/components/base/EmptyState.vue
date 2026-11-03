<script setup>
/**
 * 空状态占位：虚线框 + 等宽灰字，用于无数据、无匹配等场景，统一空态观感。
 */
defineProps({
  /** 提示文本。 */
  text: { type: String, required: true },
})
</script>

<template>
  <div class="flex h-24 items-center justify-center rounded-md border border-dashed border-line">
    <p class="font-mono text-xs text-ink-faint">{{ text }}</p>
  </div>
</template>
