<script setup>
/**
 * 状态徽标：圆点 + 等宽小字，用于在线/离线、忙碌/空闲等状态。
 * variant 决定配色：ok 绿、err 红、accent 橙、neutral 灰。
 */
defineProps({
  /** 徽标文本。 */
  label: { type: String, required: true },
  /** 配色变体。 */
  variant: {
    type: String,
    default: 'neutral',
    validator: (value) => ['ok', 'err', 'accent', 'neutral'].includes(value),
  },
})

/** 各变体的文字/边框/底色与圆点颜色。 */
const VARIANTS = {
  ok: 'border-ok/30 bg-ok/10 text-ok',
  err: 'border-err/30 bg-err/10 text-err',
  accent: 'border-accent/30 bg-accent/10 text-accent',
  neutral: 'border-line bg-panel-2 text-ink-dim',
}
const DOTS = {
  ok: 'bg-ok',
  err: 'bg-err',
  accent: 'bg-accent',
  neutral: 'bg-ink-faint',
}
</script>

<template>
  <span
    class="inline-flex shrink-0 items-center gap-1.5 rounded-full border px-2 py-0.5 font-mono text-[11px] leading-4"
    :class="VARIANTS[variant]"
  >
    <span class="size-1.5 rounded-full" :class="DOTS[variant]"></span>
    {{ label }}
  </span>
</template>
