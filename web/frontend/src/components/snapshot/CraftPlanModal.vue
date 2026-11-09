<script setup>
/**
 * 计划合成弹窗：点击样板格子后弹出（z 轴更高 + 背景模糊）。
 * 展示目标产物、可修改的数量，并按「批次 = ⌈数量 / 单批产量⌉」换算各输入槽需求。
 * 「开始合成」提交 POST /craft：受理后展示命令回执（commandId），并通知父级刷新命令列表。
 * 提交失败的契约错误码（invalid_amount 等）直接作为文案展示。ESC 或点击遮罩关闭。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import BaseButton from '../base/BaseButton.vue'
import ItemSlot from '../base/ItemSlot.vue'
import { iconUrlOf, submitCraft } from '../../api/index.js'
import { formatAmount, formatTime, keyId } from '../../lib/format.js'
import { displayName } from '../../lib/itemName.js'

const props = defineProps({
  /** 目标网格 accessId：提交合成命令的路径参数。 */
  accessId: { type: String, required: true },
  /** 选中的样板对象（patterns[] 条目）。 */
  pattern: { type: Object, required: true },
})

const emit = defineEmits(['close', 'submitted'])

/** 期望合成数量：默认单批产量。 */
const amount = ref(props.pattern.primaryOutput.amount || 1)

/** 提交中标记：防止重复点击。 */
const submitting = ref(false)

/** 提交失败的契约错误码（如 invalid_amount / mod_unreachable）。 */
const submitError = ref('')

/** 受理成功后的 202 响应 {commandId, state, submittedAt}；非空表示已受理。 */
const accepted = ref(null)

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

/**
 * 提交合成命令：先做与后端/模组对齐的前端校验（正整数数量），
 * 通过后 POST /craft 并记录受理响应；契约错误码直接透出为文案。
 */
async function submit() {
  if (submitting.value || accepted.value) {
    return
  }
  if (!Number.isInteger(amount.value) || amount.value <= 0) {
    submitError.value = 'invalid_amount'
    return
  }
  submitting.value = true
  submitError.value = ''
  try {
    accepted.value = await submitCraft(props.accessId, props.pattern.primaryOutput.key, amount.value)
    emit('submitted')
  } catch (cause) {
    submitError.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    submitting.value = false
  }
}

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
              <ItemSlot :item="pattern.primaryOutput" :icon-url="iconUrlOf(pattern.primaryOutput.key)" />
            </div>
            <div class="min-w-0">
              <p class="truncate text-sm text-ink">{{ displayName(pattern.primaryOutput) }}</p>
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
              :disabled="submitting || !!accepted"
              class="w-40 rounded-md border border-line bg-panel-2 px-2.5 py-1.5 font-mono text-xs tabular-nums text-ink focus:border-accent/60 focus:outline-none disabled:opacity-50"
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
                  <ItemSlot :item="candidate" :icon-url="iconUrlOf(candidate.key)" />
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
          <p
            v-if="accepted"
            class="min-w-0 truncate font-mono text-[10px] text-ok"
            :title="accepted.commandId"
          >
            已受理 · 命令 {{ accepted.commandId.slice(0, 8) }}… · {{ formatTime(accepted.submittedAt) }}
          </p>
          <p v-else-if="submitError" class="min-w-0 truncate font-mono text-[10px] text-err">
            提交失败：{{ submitError }}
          </p>
          <p v-else class="font-mono text-[10px] text-ink-faint">数量为正整数 · 受理后由模组队列调度</p>
          <div class="flex shrink-0 gap-2">
            <BaseButton v-if="accepted" variant="primary" @click="emit('close')">完成</BaseButton>
            <template v-else>
              <BaseButton @click="emit('close')">取消</BaseButton>
              <BaseButton variant="primary" :disabled="submitting" @click="submit">
                {{ submitting ? '提交中…' : '开始合成' }}
              </BaseButton>
            </template>
          </div>
        </footer>
      </div>
    </div>
  </Teleport>
</template>
