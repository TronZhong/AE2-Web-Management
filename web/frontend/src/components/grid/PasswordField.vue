<script setup>
import { ref } from 'vue'

/**
 * 访问密码输入（预留栏位）：本期仅 UI 占位，不接任何校验逻辑；
 * 语义待认证方案定稿后回填（全局账号密码 / 每网络访问令牌，见 docs/design.md 4.1）。
 */
const value = ref('')
</script>

<template>
  <div>
    <label class="mb-1 block font-mono text-[10px] uppercase tracking-widest text-ink-faint">访问密码</label>
    <input
      v-model="value"
      type="password"
      autocomplete="off"
      placeholder="••••••••"
      class="w-full rounded-md border border-line bg-panel-2 px-2.5 py-1.5 font-mono text-xs text-ink placeholder:text-ink-faint focus:border-accent/60 focus:outline-none"
    />
    <p class="mt-1 font-mono text-[10px] text-ink-faint">预留栏位 · 暂不校验</p>
  </div>
</template>
