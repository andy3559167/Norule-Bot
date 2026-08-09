<script setup lang="ts">
import { computed, useId } from 'vue'

const checked = defineModel<boolean>({ default: false })
const props = withDefaults(defineProps<{ id?: string; label: string; hint?: string; disabled?: boolean }>(), { hint: '', disabled: false })
const generatedId = useId()
const switchId = computed(() => props.id || generatedId)
</script>

<template>
  <label class="nr-switch" :class="{ 'nr-switch--disabled': disabled }" :for="switchId">
    <span><strong>{{ label }}</strong><small v-if="hint">{{ hint }}</small></span>
    <input :id="switchId" v-model="checked" type="checkbox" role="switch" :disabled="disabled">
    <span class="nr-switch__track" aria-hidden="true"><span /></span>
  </label>
</template>

<style scoped>
.nr-switch{display:flex;min-height:var(--nr-control-height);align-items:center;justify-content:space-between;gap:1rem;padding:.7rem .85rem;border:1px solid var(--nr-border);border-radius:var(--nr-radius-md);background:rgba(5,9,7,.45);cursor:pointer}.nr-switch>span:first-child{display:grid;gap:.1rem}.nr-switch strong{font-size:.88rem;font-weight:680}.nr-switch small{color:var(--nr-text-muted);font-size:.78rem;line-height:1.45}.nr-switch input{position:absolute;width:1px;height:1px;opacity:0}.nr-switch__track{display:flex;width:2.8rem;height:1.55rem;align-items:center;padding:.18rem;border-radius:999px;background:#29322d;transition:background 160ms ease}.nr-switch__track span{width:1.18rem;height:1.18rem;border-radius:50%;background:#bac4be;transition:transform 160ms ease,background 160ms ease}.nr-switch input:checked+.nr-switch__track{background:var(--nr-accent)}.nr-switch input:checked+.nr-switch__track span{transform:translateX(1.25rem);background:var(--nr-accent-ink)}.nr-switch input:focus-visible+.nr-switch__track{outline:3px solid color-mix(in srgb,var(--nr-focus) 78%,transparent);outline-offset:3px}.nr-switch--disabled{cursor:not-allowed;opacity:.55}
</style>
