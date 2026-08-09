<script setup lang="ts">
withDefaults(defineProps<{
  type?: 'button' | 'submit' | 'reset'
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  loading?: boolean
  disabled?: boolean
  block?: boolean
}>(), {
  type: 'button',
  variant: 'primary',
  loading: false,
  disabled: false,
  block: false,
})
</script>

<template>
  <button
    class="nr-button"
    :class="[`nr-button--${variant}`, { 'nr-button--block': block }]"
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading"
  >
    <span v-if="loading" class="nr-button__spinner" aria-hidden="true" />
    <span><slot>{{ loading ? '處理中…' : '確認' }}</slot></span>
  </button>
</template>

<style scoped>
.nr-button{display:inline-flex;min-height:var(--nr-control-height);align-items:center;justify-content:center;gap:.65rem;padding:.72rem 1.2rem;border:1px solid transparent;border-radius:var(--nr-radius-md);font-weight:720;line-height:1.1;cursor:pointer;transition:transform 160ms ease,border-color 160ms ease,background 160ms ease,color 160ms ease,box-shadow 160ms ease}.nr-button:hover:not(:disabled){transform:translateY(-1px)}.nr-button:active:not(:disabled){transform:translateY(0)}.nr-button:disabled{cursor:not-allowed;opacity:.52}.nr-button--block{width:100%}.nr-button--primary{color:var(--nr-accent-ink);background:var(--nr-accent);box-shadow:0 10px 26px rgba(112,230,171,.14)}.nr-button--primary:hover:not(:disabled){background:var(--nr-accent-hover);box-shadow:0 12px 30px rgba(112,230,171,.21)}.nr-button--secondary{color:var(--nr-text);border-color:var(--nr-border-strong);background:rgba(112,230,171,.07)}.nr-button--ghost{color:var(--nr-text-muted);border-color:var(--nr-border);background:transparent}.nr-button--danger{color:#170909;border-color:transparent;background:var(--nr-danger)}.nr-button__spinner{width:1rem;height:1rem;border:2px solid currentColor;border-right-color:transparent;border-radius:50%;animation:nr-button-spin .7s linear infinite}@keyframes nr-button-spin{to{transform:rotate(360deg)}}
</style>
