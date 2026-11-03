<script setup>
/**
 * 计划合成弹窗：点击样板格子后弹出（z 轴更高 + 背景模糊）。
 * 展示目标产物、可修改的数量，并按「批次 = ⌈数量 / 单批产量⌉」换算各输入槽需求。
 * 「开始合成」在本期禁用——提交接口 POST /craft 由后端转发后于下一迭代接通。
 * ESC 或点击遮罩关闭。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import BaseButton from '../base/BaseButton.vue'
import ItemSlot from '../base/ItemSlot.vue'
import { formatAmount, friendlyName, keyId } from '../../lib/format.js'

const props = defineProps({
  /** 选中的样板对象（patterns[] 条目）。 */
  pattern: { type: Object, required: true },
})

const emit = defineEmits(['close'])

/** 期望合成数量：默认单批产量。 */
const amount = ref(props.pattern.primaryOutput.amount || 1)

/** 批次数 = ⌈数量 / 单批产量⌉，至少 1。 */
const times = computed(() => {
  const outputAmount = props.pattern.primaryOutput.amount || 1
  return Math.max(1, Math.ceil((Number(amount.value) || 0) / outputAmount))
})

/** 各输入槽：候选项换算为「本次需求」（候选 × 倍率 × 批次），供格子角标与悬浮显示。 */
const inputLines = computed(() =>
  props.pattern.inputs.map((input, index) => ({
    slot: index + 1,
    multiplier: input.multiplier,
    candidates: input.possibleInputs.map((candidate) => ({
      key: candidate.key,
      amount: candidate.amount * input.multiplier * times.value,
    })),
  })),
)

/** ESC 关闭；打开期间锁定页面滚动。 */
function onKeyDown(event) {
  if (event.key === 'Escape') {
    emit('close')
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeyDown)
  document.body.style.overflow = 'hidden'
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeyDown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <div class="fixed inset-0 z-40 flex items-center justify-center p-4">
      <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="emit('close')"></div>

      <div class="relative w-full max-w-md rounded-card border border-line bg-panel shadow-2xl shadow-black/70">
        <header class="flex min-h-11 items-center justify-between border-b border-line px-4 py-2.5">
          <h2 class="font-mono text-[11px] uppercase tracking-[0.18em] text-ink-faint">计划合成</h2>
          <button
            type="button"
            class="font-mono text-sm text-ink-faint transition-colors hover:text-ink"
            aria-label="关闭"
            @click="emit('close')"
          >
            [×]
          </button>
        </header>

        <div class="space-y-4 p-4">
          <div class="flex items-center gap-3">
            <div class="size-12 shrink-0">
              <ItemSlot :item="pattern.primaryOutput" />
            </div>
            <div class="min-w-0">
              <p class="truncate text-sm text-ink">{{ friendlyName(pattern.primaryOutput.key) }}</p>
              <p class="truncate font-mono text-[11px] text-ink-faint">{{ keyId(pattern.primaryOutput.key) }}</p>
              <p class="mt-0.5 font-mono text-[11px] text-ink-dim">单批产量 ×{{ formatAmount(pattern.primaryOutput.amount) }}</p>
            </div>
          </div>

          <div>
            <label class="mb-1 block font-mono text-[10px] uppercase tracking-widest text-ink-faint" for="craft-amount">
              合成数量
            </label>
            <input
              id="craft-amount"
              v-model.number="amount"
              type="number"
              min="1"
              class="w-40 rounded-md border border-line bg-panel-2 px-2.5 py-1.5 font-mono text-xs tabular-nums text-ink focus:border-accent/60 focus:outline-none"
            />
            <p class="mt-1 font-mono text-[11px] text-ink-faint">共 {{ times }} 批</p>
          </div>

          <div v-if="inputLines.length" class="space-y-2">
            <p class="text-[10px] uppercase tracking-widest text-ink-faint">输入需求</p>
            <div v-for="line in inputLines" :key="line.slot" class="flex items-center gap-2">
              <span
                class="w-10 shrink-0 font-mono text-[10px] leading-4 text-ink-faint"
                :title="`输入槽 ${line.slot} · 倍率 ×${line.multiplier}`"
              >
                槽{{ line.slot }}<br />×{{ line.multiplier }}
              </span>
              <div class="flex flex-wrap gap-1.5">
                <div v-for="candidate in line.candidates" :key="keyId(candidate.key)" class="size-12">
                  <ItemSlot :item="candidate" />
                </div>
              </div>
            </div>
            <p class="font-mono text-[10px] text-ink-faint">
              角标 = 本次需求（候选 × 倍率 × 批次）· 悬浮查看详情
            </p>
          </div>
          <p v-else class="font-mono text-[11px] text-ink-faint">该样板没有输入槽（如合成台空配方）</p>
        </div>

        <footer class="flex items-center justify-between gap-3 border-t border-line px-4 py-3">
          <p class="font-mono text-[10px] text-ink-faint">提交接口待后端 /craft 接通后开放</p>
          <div class="flex gap-2">
            <BaseButton @click="emit('close')">取消</BaseButton>
            <BaseButton variant="primary" disabled>开始合成</BaseButton>
          </div>
        </footer>
      </div>
    </div>
  </Teleport>
</template>
