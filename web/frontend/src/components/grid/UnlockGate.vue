<script setup>
/**
 * 网格解锁面板（契约 v0.3 网格访问控制）：
 * 网络在游戏内设置了密码且当前账号未解锁时，详情页以此面板替代快照内容。
 * 提交 POST /api/networks/{accessId}/unlock；invalid_password 显示「密码错误」，
 * 其余错误码原样透出；成功后向父级发 unlocked 事件（父级刷新快照与列表）。
 * MOCK 模式下显示演示密码提示（真实密码校验在模组端，明文不出模组）。
 */
import { ref } from 'vue'

import BaseButton from '../base/BaseButton.vue'
import { USE_MOCK, unlockNetwork } from '../../api/index.js'
import { NETWORK_PASSWORDS } from '../../api/mock/fixtures.js'

const props = defineProps({
  /** 目标网络 accessId。 */
  accessId: { type: String, required: true },
  /** 显示名（用于面板标题）。 */
  displayName: { type: String, default: '' },
})

const emit = defineEmits(['unlocked'])

const password = ref('')
const submitting = ref(false)
const error = ref('')

/** 契约错误码 → 面板文案；未收录的原样展示。 */
const ERROR_TEXT = {
  invalid_password: '密码错误，请重新输入',
  invalid_json: '请输入网络密码',
  network_not_found: '网络不存在（可能已被游戏内注销）',
  mod_unreachable: '无法连接游戏端（游戏未启动或数据口未开启）',
  mod_unavailable: '游戏端拒绝了后端共享密钥',
}

/** 提交解锁；成功后向父级发 unlocked 事件。 */
async function submit() {
  if (submitting.value) {
    return
  }
  if (!password.value) {
    error.value = 'invalid_json'
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await unlockNetwork(props.accessId, password.value)
    password.value = ''
    emit('unlocked')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="mx-auto mt-10 w-full max-w-sm rounded-card border border-line bg-panel p-6">
    <h2 class="text-base font-medium text-ink">🔒 网格已锁定</h2>
    <p class="mt-1.5 font-mono text-[11px] leading-5 text-ink-faint">
      「{{ displayName || accessId.slice(0, 8) }}」在游戏内设置了访问密码，
      输入密码解锁后即可查看库存与提交合成。
    </p>

    <form class="mt-6" @submit.prevent="submit">
      <label class="mb-1 block font-mono text-[10px] uppercase tracking-widest text-ink-faint" for="unlock-password">
        网格密码
      </label>
      <input
        id="unlock-password"
        v-model="password"
        type="password"
        autocomplete="off"
        class="w-full rounded-md border border-line bg-panel-2 px-2.5 py-1.5 font-mono text-xs text-ink placeholder:text-ink-faint focus:border-accent/60 focus:outline-none"
        placeholder="••••••••"
      />

      <p v-if="error" class="mt-4 rounded-md border border-err/30 bg-err/10 px-3 py-2 font-mono text-xs text-err">
        {{ ERROR_TEXT[error] ?? `解锁失败：${error}` }}
      </p>

      <BaseButton variant="primary" class="mt-6 w-full justify-center" :disabled="submitting" type="submit">
        {{ submitting ? '解锁中…' : '解锁网格' }}
      </BaseButton>
    </form>

    <p class="mt-4 font-mono text-[10px] leading-4 text-ink-faint">
      {{
        USE_MOCK && NETWORK_PASSWORDS[accessId]
          ? `MOCK 模式：演示密码为 ${NETWORK_PASSWORDS[accessId]}`
          : '密码由网络所有者在游戏内接入方块 GUI 设置；解锁状态按账号长期记忆'
      }}
    </p>
  </div>
</template>
